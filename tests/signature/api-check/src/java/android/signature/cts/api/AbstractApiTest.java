/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.signature.cts.api;

import android.app.Instrumentation;
import android.os.Bundle;
import android.provider.Settings;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.ClassProvider;
import android.signature.cts.ExcludingClassProvider;
import android.signature.cts.ExpectedFailuresFilter;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import android.signature.cts.VirtualPath;
import android.signature.cts.VirtualPath.LocalFilePath;
import android.signature.cts.VirtualPath.ResourcePath;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.DynamicConfigDeviceSide;

import com.google.common.base.Suppliers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Base class for the signature tests.
 */
@RunWith(AndroidJUnit4.class)
public abstract class AbstractApiTest {

    /**
     * The name of the optional instrumentation option that contains the name of the dynamic config
     * data set that contains the expected failures.
     */
    private static final String DYNAMIC_CONFIG_NAME_OPTION = "dynamic-config-name";

    private static final String TAG = "SignatureTest";

    private TestResultObserver mResultObserver;

    ClassProvider mClassProvider;

    /**
     * The list of expected failures.
     */
    private Collection<String> expectedFailures = Collections.emptyList();

    public Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    protected String getGlobalExemptions() {
        return Settings.Global.getString(
                getInstrumentation().getContext().getContentResolver(),
                Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS);
    }

    protected String getGlobalHiddenApiPolicy() {
        return Settings.Global.getString(
                getInstrumentation().getContext().getContentResolver(),
                Settings.Global.HIDDEN_API_POLICY);
    }

    @Before
    public void setUp() throws Exception {
        mResultObserver = new TestResultObserver();

        // Get the arguments passed to the instrumentation.
        Bundle instrumentationArgs = InstrumentationRegistry.getArguments();

        // Check that the device is in the correct state for running this test.
        assertEquals(
                String.format("Device in bad state: %s is not as expected",
                        Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS),
                getExpectedBlocklistExemptions(),
                getGlobalExemptions());
        assertNull(
                String.format("Device in bad state: %s is not as expected",
                        Settings.Global.HIDDEN_API_POLICY),
                getGlobalHiddenApiPolicy());


        // Prepare for a class provider that loads classes from bootclasspath but filters
        // out known inaccessible classes.
        // Note that com.android.internal.R.* inner classes are also excluded as they are
        // not part of API though exist in the runtime.
        mClassProvider = new ExcludingClassProvider(
                new BootClassPathClassesProvider(),
                name -> name != null && name.startsWith("com.android.internal.R."));

        String dynamicConfigName = instrumentationArgs.getString(DYNAMIC_CONFIG_NAME_OPTION);
        if (dynamicConfigName != null) {
            // Get the DynamicConfig.xml contents and extract the expected failures list.
            DynamicConfigDeviceSide dcds = new DynamicConfigDeviceSide(dynamicConfigName);
            Collection<String> expectedFailures = dcds.getValues("expected_failures");
            initExpectedFailures(expectedFailures);
        }

        initializeFromArgs(instrumentationArgs);
    }

    /**
     * Initialize the expected failures.
     *
     * <p>Call from with {@link #setUp()}</p>
     *
     * @param expectedFailures the expected failures.
     */
    private void initExpectedFailures(Collection<String> expectedFailures) {
        this.expectedFailures = expectedFailures;
        String tag = getClass().getName();
        Log.d(tag, "Expected failure count: " + expectedFailures.size());
        for (String failure: expectedFailures) {
            Log.d(tag, "Expected failure: \"" + failure + "\"");
        }
    }

    protected String getExpectedBlocklistExemptions() {
        return null;
    }

    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {
    }

    protected interface RunnableWithResultObserver {
        void run(ResultObserver observer) throws Exception;
    }

    void runWithTestResultObserver(RunnableWithResultObserver runnable) {
        runWithTestResultObserver(expectedFailures, runnable);
    }

    private void runWithTestResultObserver(
            Collection<String> expectedFailures, RunnableWithResultObserver runnable) {
        try {
            ResultObserver observer = mResultObserver;
            if (!expectedFailures.isEmpty()) {
                observer = new ExpectedFailuresFilter(observer, expectedFailures);
            }
            runnable.run(observer);
        } catch (Error|Exception e) {
            mResultObserver.notifyFailure(
                    FailureType.CAUGHT_EXCEPTION,
                    e.getClass().getName(),
                    "Uncaught exception thrown by test",
                    e);
        }
        mResultObserver.onTestComplete(); // Will throw is there are failures
    }

    static Supplier<String[]> getSupplierOfAnOptionalCommaSeparatedListArgument(String key) {
        return Suppliers.memoize(() -> {
            Bundle arguments = InstrumentationRegistry.getArguments();
            return getCommaSeparatedListOptional(arguments, key);
        })::get;
    }

    static String[] getCommaSeparatedListOptional(Bundle instrumentationArgs, String key) {
        String argument = instrumentationArgs.getString(key);
        if (argument == null) {
            return new String[0];
        }
        return argument.split(",");
    }

    static String[] getCommaSeparatedListRequired(Bundle instrumentationArgs, String key) {
        String argument = instrumentationArgs.getString(key);
        if (argument == null) {
            throw new IllegalStateException("Could not find required argument '" + key + "'");
        }
        return argument.split(",");
    }

    private static Stream<VirtualPath> readResource(ClassLoader classLoader, String resourceName) {
        try {
            ResourcePath resourcePath =
                    VirtualPath.get(classLoader, resourceName);
            if (resourceName.endsWith(".zip")) {
                // Extract to a temporary file and read from there.
                Path file = extractResourceToFile(resourceName, resourcePath.newInputStream());
                return flattenPaths(VirtualPath.get(file.toString()));
            } else {
                return Stream.of(resourcePath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path extractResourceToFile(String resourceName, InputStream is) throws IOException {
        Path tempDirectory = Files.createTempDirectory("signature");
        Path file = tempDirectory.resolve(resourceName);
        Log.i(TAG, "extractResourceToFile: extracting " + resourceName + " to " + file);
        Files.copy(is, file);
        is.close();
        return file;
    }

    /**
     * Given a path in the local file system (possibly of a zip file) flatten it into a stream of
     * virtual paths.
     */
    private static Stream<VirtualPath> flattenPaths(LocalFilePath path) {
        try {
            if (path.toString().endsWith(".zip")) {
                return getZipEntryFiles(path);
            } else {
                return Stream.of(path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a stream of {@link JDiffClassDescription} by parsing a set of API resource files.
     *
     * @param apiDocumentParser the parser to use.
     * @param apiResources the list of API resource files.
     *
     * @return the stream of {@link JDiffClassDescription}.
     */
    Stream<JDiffClassDescription> parseApiResourcesAsStream(
            ApiDocumentParser apiDocumentParser, String[] apiResources) {
        return retrieveApiResourcesAsStream(getClass().getClassLoader(), apiResources)
                .flatMap(apiDocumentParser::parseAsStream);
    }

    /**
     * Retrieve a stream of {@link VirtualPath} from a list of API resource files.
     *
     * <p>Any zip files are flattened, i.e. if a resource name ends with {@code .zip} then it is
     * unpacked into a temporary directory and the paths to the unpacked files are returned instead
     * of the path to the zip file.</p>
     *
     * @param classLoader the {@link ClassLoader} from which the resources will be loaded.
     * @param apiResources the list of API resource files.
     *
     * @return the stream of {@link VirtualPath}.
     */
    static Stream<VirtualPath> retrieveApiResourcesAsStream(
            ClassLoader classLoader,
            String[] apiResources) {
        return Stream.of(apiResources)
                .flatMap(resourceName -> readResource(classLoader, resourceName));
    }

    /**
     * Get the zip entries that are files.
     *
     * @param path the path to the zip file.
     * @return paths to zip entries
     */
    private static Stream<VirtualPath> getZipEntryFiles(LocalFilePath path) throws IOException {
        @SuppressWarnings("resource")
        ZipFile zip = new ZipFile(path.toFile());
        return zip.stream().map(entry -> VirtualPath.get(zip, entry));
    }
}
