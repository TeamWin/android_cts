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
package android.content.pm.cts.shortcuthost;

import com.android.cts.migration.MigrationHelper;
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

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

abstract public class BaseShortcutManagerHostTest extends DeviceTestCase implements IBuildReceiver {

    private static final String RUNNER = "android.support.test.runner.AndroidJUnitRunner";

    private IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertNotNull(mCtsBuild);  // ensure build has been set before test is run.
    }

    protected void installAppAsUser(String appFileName, int userId) throws FileNotFoundException,
            DeviceNotAvailableException {
        CLog.i("Installing app " + appFileName + " for user " + userId);
        String result = getDevice().installPackageForUser(
                MigrationHelper.getTestFile(mCtsBuild, appFileName), true, true,
                userId, "-t");
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    protected int getPrimaryUserId() throws DeviceNotAvailableException {
        return getDevice().getPrimaryUserId();
    }

    /** Returns true if the specified tests passed. Tests are run as given user. */
    protected boolean runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, int userId)
            throws DeviceNotAvailableException {
        return runDeviceTestsAsUser(pkgName, testClassName, null /*testMethodName*/, userId);
    }

    /** Returns true if the specified tests passed. Tests are run as given user. */
    protected boolean runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, String testMethodName, int userId)
            throws DeviceNotAvailableException {
        Map<String, String> params = Collections.emptyMap();
        return runDeviceTestsAsUser(pkgName, testClassName, testMethodName, userId, params);
    }

    protected boolean runDeviceTestsAsUser(String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName, int userId,
            Map<String, String> params) throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(
                pkgName, RUNNER, getDevice().getIDevice());
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        for (Map.Entry<String, String> param : params.entrySet()) {
            testRunner.addInstrumentationArg(param.getKey(), param.getValue());
        }

        CollectingTestListener listener = new CollectingTestListener();
        assertTrue(getDevice().runInstrumentationTestsAsUser(testRunner, userId, listener));

        TestRunResult runResult = listener.getCurrentRunResults();
        printTestResult(runResult);
        return !runResult.hasFailedTests() && runResult.getNumTestsInState(TestStatus.PASSED) > 0;
    }

    private void printTestResult(TestRunResult runResult) {
        if (runResult.getTestResults().size() == 0) {
            CLog.e("No tests have been executed.");
            return;
        }
        for (Map.Entry<TestIdentifier, TestResult> testEntry :
                runResult.getTestResults().entrySet()) {
            TestResult testResult = testEntry.getValue();

            final String message = "Test " + testEntry.getKey() + ": " + testResult.getStatus();
            if (testResult.getStatus() == TestStatus.PASSED) {
                CLog.i(message);
            } else {
                CLog.e(message);
                CLog.e(testResult.getStackTrace());
            }
        }
    }
}
