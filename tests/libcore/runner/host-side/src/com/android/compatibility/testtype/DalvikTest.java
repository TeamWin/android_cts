/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.testtype;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.AbiUtils;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.TimeVal;

import vogar.ExpectationStore;
import vogar.ModeId;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper to run tests against Dalvik.
 */
public class DalvikTest implements IAbiReceiver, IBuildReceiver, IDeviceTest, IRemoteTest,
        IRuntimeHintProvider, IShardableTest, ITestFilterReceiver {

    private static final String TAG = DalvikTest.class.getSimpleName();
    private static final String LIBCORE_PACKAGE = "android.core.tests.libcore.package.%s";
    private static final Set<String> PACKAGES = new HashSet<>();
    static {
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "com"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "conscrypt"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "dalvik"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "jsr166"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "libcore"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "okhttp"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "org"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "sun"));// Has 0 tests
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "tests"));
        PACKAGES.add(String.format(LIBCORE_PACKAGE, "tzdata"));// Has 0 tests
        PACKAGES.add("com.android.org.apache.harmony.beans");// Has 0 tests
        PACKAGES.add("com.android.org.apache.harmony.logging");// Has 0 tests
        PACKAGES.add("com.android.org.apache.harmony.prefs");// Has 0 tests
        PACKAGES.add("com.android.org.apache.harmony.sql");// Has 0 tests
        PACKAGES.add("org.apache.harmony.annotation.tests");// Has 0 tests
        PACKAGES.add("org.apache.harmony.crypto.tests");// Has 0 tests
        PACKAGES.add("org.apache.harmony.luni.tests");// Has 0 tests
        PACKAGES.add("org.apache.harmony.nio.tests");// Has 0 tests
        PACKAGES.add("org.apache.harmony.regex.tests");// Has 0 tests
        PACKAGES.add("org.apache.harmony.security.tests");// Has 0 tests
        PACKAGES.add("org.apache.harmony.tests.internal.net.www.protocol");// Has 0 tests
        PACKAGES.add("org.apache.harmony.tests.java.io");
        PACKAGES.add("org.apache.harmony.tests.java.lang");
        PACKAGES.add("org.apache.harmony.tests.java.math");
        PACKAGES.add("org.apache.harmony.tests.java.net");
        PACKAGES.add("org.apache.harmony.tests.java.nio");
        PACKAGES.add("org.apache.harmony.tests.java.text");
        PACKAGES.add("org.apache.harmony.tests.java.util");
        PACKAGES.add("org.apache.harmony.tests.javax.net");
        PACKAGES.add("org.apache.harmony.tests.javax.security");
        PACKAGES.add("org.json");
        PACKAGES.add("org.w3c.domts");
    }

    private static final String EXPECTATIONS_EXT = ".expectations";
    // Command to run the VM, args are bitness, classpath, dalvik-args, abi, runner-args,
    // include and exclude filters, and exclude filters file.
    private static final String COMMAND = "dalvikvm%s -classpath %s %s "
            + "com.android.compatibility.dalvik.DalvikTestRunner --abi=%s %s %s %s %s %s";
    private static final String INCLUDE_FILE = "/data/local/tmp/ctslibcore/includes";
    private static final String EXCLUDE_FILE = "/data/local/tmp/ctslibcore/excludes";
    private static String START_RUN = "start-run";
    private static String END_RUN = "end-run";
    private static String START_TEST = "start-test";
    private static String END_TEST = "end-test";
    private static String FAILURE = "failure";

    @Option(name = "run-name", description = "The name to use when reporting results")
    private String mRunName;

    @Option(name = "classpath", description = "Holds the paths to search when loading tests")
    private List<String> mClasspath = new ArrayList<>();

    @Option(name = "dalvik-arg", description = "Holds arguments to pass to Dalvik")
    private List<String> mDalvikArgs = new ArrayList<>();

    @Option(name = "runner-arg",
            description = "Holds arguments to pass to the device-side test runner")
    private List<String> mRunnerArgs = new ArrayList<>();

    @Option(name = "include-filter",
            description = "The include filters of the test name to run.")
    private List<String> mIncludeFilters = new ArrayList<>();

    @Option(name = "exclude-filter",
            description = "The exclude filters of the test name to run.")
    private List<String> mExcludeFilters = new ArrayList<>();

    @Option(name = "runtime-hint",
            isTimeVal = true,
            description="The hint about the test's runtime.")
    private long mRuntimeHint = 60000;// 1 minute

    private IAbi mAbi;
    private CompatibilityBuildHelper mBuildHelper;
    private ITestDevice mDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo build) {
        mBuildHelper = new CompatibilityBuildHelper(build);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(List<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(List<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRuntimeHint() {
        return mRuntimeHint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(final ITestInvocationListener listener) throws DeviceNotAvailableException {
        String abiName = mAbi.getName();
        String bitness = AbiUtils.getBitness(abiName);

        File temp = null;
        PrintWriter out = null;
        try {
            Set<File> expectationFiles = new HashSet<>();
            for (File f : mBuildHelper.getTestsDir().listFiles(
                    new ExpectationFileFilter(mRunName))) {
                expectationFiles.add(f);
            }
            ExpectationStore store = ExpectationStore.parse(expectationFiles, ModeId.DEVICE);

            // Work around because there are to many expectations to pass via command line
            temp = File.createTempFile("excludes", "txt");
            out = new PrintWriter(temp);
            for (String exclude : store.getAllFailures().keySet()) {
                out.println(exclude);
            }
            for (String exclude : store.getAllOutComes().keySet()) {
                out.println(exclude);
            }
            out.flush();
            if (!mDevice.pushFile(temp, EXCLUDE_FILE)) {
                Log.logAndDisplay(LogLevel.ERROR, TAG, "Couldn't push file: " + temp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
            temp.delete();
        }

        // Create command
        mDalvikArgs.add("-Duser.name=shell");
        mDalvikArgs.add("-Duser.language=en");
        mDalvikArgs.add("-Duser.region=US");
        mDalvikArgs.add("-Xcheck:jni");
        mDalvikArgs.add("-Xjnigreflimit:2000");
        String dalvikArgs = ArrayUtil.join(" ", mDalvikArgs);

        String runnerArgs = ArrayUtil.join(" ", mRunnerArgs);
        // Filters
        StringBuilder includeFilters = new StringBuilder();
        if (!mIncludeFilters.isEmpty()) {
            includeFilters.append("--include-filter=");
            includeFilters.append(ArrayUtil.join(",", mIncludeFilters));
        }
        StringBuilder excludeFilters = new StringBuilder();
        if (!mExcludeFilters.isEmpty()) {
            excludeFilters.append("--exclude-filter=");
            excludeFilters.append(ArrayUtil.join(",", mExcludeFilters));
        }
        // Filter files
        String includeFile = ""; // String.format("--include-filter-file=%s", INCLUDE);
        String excludeFile = String.format("--exclude-filter-file=%s", EXCLUDE_FILE);
        final String command = String.format(COMMAND, bitness,
                ArrayUtil.join(File.pathSeparator, mClasspath),
                dalvikArgs, abiName, runnerArgs,
                includeFilters, excludeFilters, includeFile, excludeFile);
        IShellOutputReceiver receiver = new MultiLineReceiver() {
            private TestIdentifier test;

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void processNewLines(String[] lines) {
                for (String line : lines) {
                    String[] parts = line.split(":");
                    String tag = parts[0];
                    if (tag.equals(START_RUN)) {
                        listener.testRunStarted(mRunName, Integer.parseInt(parts[1]));
                        Log.logAndDisplay(LogLevel.INFO, TAG, command);
                        Log.logAndDisplay(LogLevel.INFO, TAG, line);
                    } else if (tag.equals(END_RUN)) {
                        listener.testRunEnded(Integer.parseInt(parts[1]),
                                new HashMap<String, String>());
                        Log.logAndDisplay(LogLevel.INFO, TAG, line);
                    } else if (tag.equals(START_TEST)) {
                        test = getTestIdentifier(parts[1]);
                        listener.testStarted(test);
                    } else if (tag.equals(FAILURE)) {
                        listener.testFailed(test, parts[1]);
                    } else if (tag.equals(END_TEST)) {
                        listener.testEnded(getTestIdentifier(parts[1]),
                                new HashMap<String, String>());
                    } else {
                        Log.logAndDisplay(LogLevel.INFO, TAG, line);
                    }
                }
            }

            private TestIdentifier getTestIdentifier(String name) {
                String[] parts = name.split("#");
                String className = parts[0];
                String testName = "";
                if (parts.length > 1) {
                    testName = parts[1];
                }
                return new TestIdentifier(className, testName);
            }

        };
        mDevice.executeShellCommand(command, receiver, 1, TimeUnit.HOURS, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        List<IRemoteTest> shards = new ArrayList<>();
        // Test to catch all packages that aren't in PACKAGES
        DalvikTest catchAll = new DalvikTest();
        OptionCopier.copyOptionsNoThrow(this, catchAll);
        shards.add(catchAll);
        long runtimeHint = mRuntimeHint / PACKAGES.size();
        catchAll.mRuntimeHint = runtimeHint;
        for (String entry: PACKAGES) {
            catchAll.addExcludeFilter(entry);
            DalvikTest test = new DalvikTest();
            OptionCopier.copyOptionsNoThrow(this, test);
            test.addIncludeFilter(entry);
            test.mRuntimeHint = runtimeHint;
            shards.add(test);
        }
        return shards;
    }

    /**
     * A {@link FilenameFilter} to find all the expectation files in a directory.
     */
    public static class ExpectationFileFilter implements FilenameFilter {

        private String mName;

        public ExpectationFileFilter(String name) {
            mName = name;
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith(mName) && name.endsWith(EXPECTATIONS_EXT);
        }
    }
}
