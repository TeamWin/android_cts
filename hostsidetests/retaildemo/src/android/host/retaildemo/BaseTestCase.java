/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.host.retaildemo;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;

public class BaseTestCase extends DeviceTestCase implements IBuildReceiver {
    protected static final String RETAIL_DEMO_TEST_APK = "CtsRetailDemoApp.apk";

    private static final String RETAIL_DEMO_TEST_PKG = "com.android.cts.retaildemo";

    private static final String RUNNER = "android.support.test.runner.AndroidJUnitRunner";

    protected static final int USER_SYSTEM = 0; // From the UserHandle class.

    private static final int FLAG_DEMO = 0x00000200; // From the UserInfo class.

    private IBuildInfo mBuildInfo;
    private CompatibilityBuildHelper mBuildHelper;
    protected boolean mSupportsMultiUser;
    private ArrayList<Integer> mFixedUsers;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
        mBuildHelper = new CompatibilityBuildHelper(mBuildInfo);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertNotNull(mBuildInfo); // ensure build has been set before test is run.

        mSupportsMultiUser = getDevice().isMultiUserSupported();
        mFixedUsers = new ArrayList<>();
        final int primaryUserId = getDevice().getPrimaryUserId();
        mFixedUsers.add(primaryUserId);
        if (primaryUserId != USER_SYSTEM) {
            mFixedUsers.add(USER_SYSTEM);
        }
        getDevice().switchUser(primaryUserId);
        removeTestUsers();
    }

    @Override
    protected void tearDown() throws Exception {
        removeTestUsers();
        super.tearDown();
    }

    private void removeTestUsers() throws DeviceNotAvailableException {
        for (int userId : getDevice().listUsers()) {
            if (!mFixedUsers.contains(userId)) {
                getDevice().removeUser(userId);
            }
        }
    }

    // TODO: Update TestDevice class to include this functionality.
    protected int createDemoUser() throws DeviceNotAvailableException, IllegalStateException {
        final String command = "pm create-user --ephemeral --demo "
                + "TestUser_" + System.currentTimeMillis();
        CLog.d("Starting command: " + command);
        final String output = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + output);

        if (output.startsWith("Success")) {
            try {
                int userId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
                return userId;
            } catch (NumberFormatException e) {
                CLog.e("Failed to parse result: %s", output);
            }
        } else {
            CLog.e("Failed to create demo user: %s", output);
        }
        throw new IllegalStateException();
    }

    protected void enableDemoMode(boolean enabled) throws DeviceNotAvailableException {
        getDevice().executeShellCommand("settings put global device_demo_mode "
                + (enabled ? 1 : 0));
    }

    protected void setUserInactivityTimeoutMs(long timeoutMs) throws DeviceNotAvailableException {
        updateTimeoutValue("user_inactivity_timeout_ms", timeoutMs);
    }

    protected void setWarningDialogTimeoutMs(long timeoutMs) throws DeviceNotAvailableException {
        updateTimeoutValue("warning_dialog_timeout_ms", timeoutMs);
    }

    /**
     * Adds a new timeout constant, if it does not already exist, or updates the existing constant.
     */
    private void updateTimeoutValue(String constant, long timeoutMs)
            throws DeviceNotAvailableException {
        final String newConstant = constant + "=" + timeoutMs;
        final String currentConstants = getRetailDemoModeConstants();
        if (currentConstants == null) {
            setRetailDemoModeConstants(newConstant);
            return;
        }

        if (currentConstants.contains(constant)) {
            final String[] constants = currentConstants.split(",");
            for (int i = 0; i < constants.length; ++i) {
                if (constants[i].startsWith(constant)) {
                    constants[i] = newConstant;
                    break;
                }
            }
            setRetailDemoModeConstants(String.join(",", constants));
        } else {
            setRetailDemoModeConstants(currentConstants + "," + newConstant);
        }
    }

    protected void setRetailDemoModeConstants(String constants)
            throws DeviceNotAvailableException {
        getDevice().executeShellCommand("settings put global retail_demo_mode_constants " +
                constants);
    }

    protected String getRetailDemoModeConstants() throws DeviceNotAvailableException {
        final String rawOutput = getDevice().executeShellCommand(
                "settings get global retail_demo_mode_constants");
        if (rawOutput != null) {
            final String existingConstants = rawOutput.trim();
            return existingConstants.equals("null") ? null : existingConstants;
        }
        return null;
    }

    protected Integer getNewDemoUserId(ArrayList<Integer> existingDemoUsers)
            throws DeviceNotAvailableException {
        ArrayList<String[]> users = tokenizeListUsers();
        if (users == null) {
            return null;
        }
        for (String[] user : users) {
            final int flag = Integer.parseInt(user[3], 16);
            final Integer userId = Integer.parseInt(user[1]);
            if ((flag & FLAG_DEMO) != 0 && !existingDemoUsers.contains(userId)) {
                return userId;
            }
        }
        return null;
    }

    protected boolean isUserUnlocked(int userId) throws DeviceNotAvailableException {
        final String state = getDevice().executeShellCommand(
                "cmd activity get-started-user-state " + userId);
        return state != null && state.startsWith("RUNNING_UNLOCKED");
    }

    // TODO: Add getDemoUserIds() to TestDevice class to avoid this code duplication.
    private ArrayList<String[]> tokenizeListUsers() throws DeviceNotAvailableException {
        String command = "pm list users";
        String commandOutput = getDevice().executeShellCommand(command);
        // Extract the id of all existing users.
        String[] lines = commandOutput.split("\\r?\\n");
        if (lines.length < 1) {
            CLog.e("%s should contain at least one line", commandOutput);
            return null;
        }
        if (!lines[0].equals("Users:")) {
            CLog.e("%s in not a valid output for 'pm list users'", commandOutput);
            return null;
        }
        ArrayList<String[]> users = new ArrayList<>(lines.length - 1);
        for (int i = 1; i < lines.length; i++) {
            // Individual user is printed out like this:
            // \tUserInfo{$id$:$name$:$Integer.toHexString(flags)$} [running]
            String[] tokens = lines[i].split("\\{|\\}|:");
            if (tokens.length != 4 && tokens.length != 5) {
                CLog.e("%s doesn't contain 4 or 5 tokens", lines[i]);
                return null;
            }
            users.add(tokens);
        }
        return users;
    }

    protected void installAppAsUser(String appFileName, int userId)
            throws FileNotFoundException, DeviceNotAvailableException {
        CLog.d("Installing app " + appFileName + " for user " + userId);
        File apkFile = new File(mBuildHelper.getTestsDir(), appFileName);
        final String result = getDevice().installPackageForUser(
                apkFile, true, true, userId, "-t -r");
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    protected boolean runDeviceTestsAsUser(String testClassName, String testMethodName, int userId)
            throws Exception {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = RETAIL_DEMO_TEST_PKG + testClassName;
        }

        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(
                RETAIL_DEMO_TEST_PKG, RUNNER, getDevice().getIDevice());
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        CollectingTestListener listener = new CollectingTestListener();
        assertTrue(getDevice().runInstrumentationTestsAsUser(testRunner, userId, listener));

        TestRunResult runResult = listener.getCurrentRunResults();
        printTestResult(runResult);
        return !runResult.hasFailedTests() && runResult.getNumTestsInState(TestStatus.PASSED) > 0;
    }

    private void printTestResult(TestRunResult runResult) {
        for (Map.Entry<TestIdentifier, TestResult> testEntry :
                runResult.getTestResults().entrySet()) {
            TestResult testResult = testEntry.getValue();
            CLog.d("Test " + testEntry.getKey() + ": " + testResult.getStatus());
            if (testResult.getStatus() != TestStatus.PASSED) {
                CLog.d(testResult.getStackTrace());
            }
        }
    }
}
