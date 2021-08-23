/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.VirtualPath;
import android.signature.cts.VirtualPath.LocalFilePath;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

/**
 * Verifies that any shared library provided by this device and for which this test has a
 * corresponding API specific file provides the expected API.
 *
 * <pre>This test relies on the AndroidManifest.xml file for the APK in which this is run having a
 * {@code <uses-library>} entry for every shared library that provides an API that is contained
 * within the shared-libs-all.api.zip supplied to this test.
 */
public class SignatureMultiLibsTest extends SignatureTest {

    private static final String TAG = SignatureMultiLibsTest.class.getSimpleName();

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    public void testSignature() {
        runWithTestResultObserver(mResultObserver -> {

            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseApiResourcesAsStream(apiDocumentParser,
                    Stream.concat(Arrays.stream(expectedApiFiles), Arrays.stream(previousApiFiles))
                    .toArray(String[]::new))
                    .forEach(complianceChecker::checkSignatureCompliance);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }

    /**
     * Get all the shared libraries available on the device.
     *
     * @return a stream of available shared library names.
     */
    private Stream<String> getLibraries() {
        try {
            String result = runShellCommand(getInstrumentation(), "cmd package list libraries");
            return Arrays.stream(result.split("\n")).map(line -> line.split(":")[1]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check to see if the supplied name is an API file for a shared library that is available on
     * this device.
     *
     * @param name the name of the possible API file for a shared library.
     * @return true if it is, false otherwise.
     */
    private boolean checkLibrary (String name) {
        String libraryName = name.substring(name.lastIndexOf('/') + 1).split("-")[0];
        return getLibraries().anyMatch(libraryName::equals);
    }

    /**
     * Override the method that gets the files from a supplied zip file to filter out any file that
     * does not correspond to a shared library available on the device.
     *
     * @param path the path to the zip file.
     * @return a stream of paths in the zip file that contain APIs that should be available to this
     * tests.
     * @throws IOException if there was an issue reading the zip file.
     */
    @Override
    protected Stream<VirtualPath> getZipEntryFiles(LocalFilePath path) throws IOException {
        // Only return entries corresponding to shared libraries.
        return super.getZipEntryFiles(path).filter(p -> checkLibrary(p.toString()));
    }
}
