/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.cts.backup;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import com.android.cts.migration.MigrationHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

/**
 * Base class for CTS backup/restore hostside tests
 */
public abstract class BaseBackupHostSideTest extends DeviceTestCase implements IBuildReceiver {

    /** Value of PackageManager.FEATURE_BACKUP */
    private static final String FEATURE_BACKUP = "android.software.backup";

    private static final String LOCAL_TRANSPORT =
            "android/com.android.internal.backup.LocalTransport";

    protected ITestDevice mDevice;
    protected boolean mIsBackupSupported;

    private boolean mWasBackupEnabled;
    private String mOldTransport;
    private HashSet<String> mAvailableFeatures;
    private IBuildInfo mCtsBuildInfo;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuildInfo = buildInfo;
    }

    @Override
    public void setUp() throws DeviceNotAvailableException, Exception {
        mDevice = getDevice();
        mIsBackupSupported = hasDeviceFeature(FEATURE_BACKUP);
        if (!mIsBackupSupported) {
            CLog.i("android.software.backup feature is not supported on this device");
            return;
        }
        // Enable backup and select local backup transport
        assertTrue("LocalTransport should be available.", hasBackupTransport(LOCAL_TRANSPORT));
        mWasBackupEnabled = enableBackup(true);
        mOldTransport = setBackupTransport(LOCAL_TRANSPORT);
        assertNotNull(mCtsBuildInfo);
    }

    @Override
    public void tearDown() throws Exception {
        if (mIsBackupSupported) {
            setBackupTransport(mOldTransport);
            enableBackup(mWasBackupEnabled);
        }
    }

    /**
     * Execute shell command "bmgr backupnow <packageName>" and return output from this command.
     */
    protected String backupNow(String packageName) throws DeviceNotAvailableException {
        return mDevice.executeShellCommand("bmgr backupnow " + packageName);
    }

    /**
     * Execute shell command "bmgr restore <packageName>" and return output from this command.
     */
    protected String restore(String packageName) throws DeviceNotAvailableException {
        return mDevice.executeShellCommand("bmgr restore " + packageName);
    }

    /**
     * Copied from com.android.cts.net.HostsideNetworkTestCase.
     */
    protected void runDeviceTest(String packageName, String className, String testName)
            throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                "android.support.test.runner.AndroidJUnitRunner", mDevice.getIDevice());

        if (className != null) {
            if (testName != null) {
                testRunner.setMethodName(className, testName);
            } else {
                testRunner.setClassName(className);
            }
        }

        final CollectingTestListener listener = new CollectingTestListener();
        mDevice.runInstrumentationTests(testRunner, listener);

        final TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new AssertionError("Failed to successfully run device tests for "
                    + result.getName() + ": " + result.getRunFailureMessage());
        }
        if (result.getNumTests() == 0) {
            throw new AssertionError("No tests were run on the device");
        }
        if (result.hasFailedTests()) {
            // build a meaningful error message
            StringBuilder errorBuilder = new StringBuilder("on-device tests failed:\n");
            for (Map.Entry<TestIdentifier, TestResult> resultEntry :
                result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }
    }

    /**
     * Parsing the output of "bmgr backupnow" command and checking that the package under test
     * was backed up successfully.
     *
     * Expected format: "Package <packageName> with result: Success"
     */
    protected void assertBackupIsSuccessful(String packageName, String backupnowOutput) {
        // Assert backup was successful.
        Scanner in = new Scanner(backupnowOutput);
        boolean success = false;
        while (in.hasNextLine()) {
            String line = in.nextLine();

            if (line.contains(packageName)) {
                String result = line.split(":")[1].trim();
                if ("Success".equals(result)) {
                    success = true;
                }
            }
        }
        in.close();
        assertTrue(success);
    }

    protected String installPackage(String apkName)
            throws DeviceNotAvailableException, FileNotFoundException {
        return mDevice.installPackage(MigrationHelper.getTestFile(mCtsBuildInfo, apkName), true);
    }

    /**
     * Parsing the output of "bmgr restore" command and checking that the package under test
     * was restored successfully.
     *
     * Expected format: "restoreFinished: 0"
     */
    protected void assertRestoreIsSuccessful(String restoreOutput) {
        assertTrue("Restore not successful", restoreOutput.contains("restoreFinished: 0"));
    }

    private boolean hasDeviceFeature(String requiredFeature) throws DeviceNotAvailableException {
        if (mAvailableFeatures == null) {
            String command = "pm list features";
            String commandOutput = getDevice().executeShellCommand(command);
            CLog.i("Output for command " + command + ": " + commandOutput);

            // Extract the id of the new user.
            mAvailableFeatures = new HashSet<>();
            for (String feature: commandOutput.split("\\s+")) {
                // Each line in the output of the command has the format "feature:{FEATURE_VALUE}".
                String[] tokens = feature.split(":");
                assertTrue("\"" + feature + "\" expected to have format feature:{FEATURE_VALUE}",
                        tokens.length > 1);
                assertEquals(feature, "feature", tokens[0]);
                mAvailableFeatures.add(tokens[1]);
            }
        }
        boolean result = mAvailableFeatures.contains(requiredFeature);
        if (!result) {
            CLog.d("Device doesn't have required feature "
            + requiredFeature + ". Test won't run.");
        }
        return result;
    }

    // Copied over from BackupQuotaTest
    private boolean enableBackup(boolean enable) throws Exception {
        boolean previouslyEnabled;
        String output = mDevice.executeShellCommand("bmgr enabled");
        Pattern pattern = Pattern.compile("^Backup Manager currently (enabled|disabled)$");
        Matcher matcher = pattern.matcher(output.trim());
        if (matcher.find()) {
            previouslyEnabled = "enabled".equals(matcher.group(1));
        } else {
            throw new RuntimeException("non-parsable output setting bmgr enabled: " + output);
        }

        mDevice.executeShellCommand("bmgr enable " + enable);
        return previouslyEnabled;
    }

    // Copied over from BackupQuotaTest
    private String setBackupTransport(String transport) throws Exception {
        String output = mDevice.executeShellCommand("bmgr transport " + transport);
        Pattern pattern = Pattern.compile("\\(formerly (.*)\\)$");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("non-parsable output setting bmgr transport: " + output);
        }
    }

    // Copied over from BackupQuotaTest
    private boolean hasBackupTransport(String transport) throws Exception {
        String output = mDevice.executeShellCommand("bmgr list transports");
        for (String t : output.split(" ")) {
            if (transport.equals(t.trim())) {
                return true;
            }
        }
        return false;
    }
}
