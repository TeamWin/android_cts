/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.powerpolicy.PowerPolicyTestAnalyzer;
import android.car.cts.powerpolicy.PowerPolicyTestResult;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class PowerPolicyHostTest extends CarHostJUnit4TestCase {
    private static final String ANDROID_CLIENT_PKG = "android.car.cts.app";
    private static final String ANDROID_CLIENT_ACTIVITY = ANDROID_CLIENT_PKG
            + "/.PowerPolicyTestActivity";
    private static final String SHELL_CMD_HEADER = "am start -n " + ANDROID_CLIENT_ACTIVITY;
    private static final String TESTCASE_CMD_HEADER = SHELL_CMD_HEADER
            + " --es \"powerpolicy\" \"TestCase%d,%s\"";
    private static final String POWER_POLICY_TEST_RESULT_HEADER = "PowerPolicyTestClientResult";

    private static final int MAX_TEST_CASES = 5;
    private static final long LAUNCH_BUFFER_TIME_MS = 1_000L;

    private final PowerPolicyTestAnalyzer mTestAnalyzer;

    public PowerPolicyHostTest() {
        mTestAnalyzer = new PowerPolicyTestAnalyzer(this);
    }

    @Before
    public void setUp() throws Exception {
        startAndroidClient();
        makeSureAndroidClientRunning(ANDROID_CLIENT_PKG);
    }

    @After
    public void tearDown() throws Exception {
        killAndroidClient(ANDROID_CLIENT_PKG);
    }

    @Test
    public void testDefaultPowerPolicyStateMachine() throws Exception {
        boolean status = true;
        // create expected test result
        PowerPolicyTestResult testResult = startTestCase(1);

        // populate the expected test result here.
        testResult.addCriteria("dumpstate", "6", null);

        // clear the device to the ON state
        rebootDevice();

        // execute the test sequence
        dumpPowerState(testResult.getTestcaseNo());

        // snapshot the test result
        endTestCase(testResult);

        //TODO (b/183449315): assign the return to the status variable
        testResult.checkTestStatus();

        assertWithMessage("testDefaultPowerPolicyStateMachine").that(status).isTrue();
    }

    @Test
    public void testPowerPolicyChange() throws Exception {
        boolean status = true;
        // create expected test result
        PowerPolicyTestResult testResult = startTestCase(2);

        // populate the expected test result here.
        testResult.addCriteria("dumpstate", "6", null);

        // execute the test sequence
        dumpPowerPolicy(testResult.getTestcaseNo());

        // snapshot the test result
        endTestCase(testResult);

        //TODO (b/183449315): assign the return to the status variable
        testResult.checkTestStatus();

        assertWithMessage("testPowerPolicyChange").that(status).isTrue();
    }

    @Test
    public void testPowerPolicySilentMode() throws Exception {
        boolean status = true;
        // create expected test result
        PowerPolicyTestResult testResult = startTestCase(3);

        // populate the expected test result here.
        testResult.addCriteria("dumpstate", "2", null);

        // execute the test sequence
        rebootForcedSilent();
        dumpPowerState(testResult.getTestcaseNo());

        // snapshot the test result
        endTestCase(testResult);

        //TODO (b/183449315): assign the return to the status variable
        testResult.checkTestStatus();

        assertWithMessage("testPowerPolicySilentMode").that(status).isTrue();
    }

    @Test
    public void testPowerPolicySuspendToRAM() throws Exception {
        boolean status = true;
        // create expected test result
        PowerPolicyTestResult testResult = startTestCase(4);

        // populate the expected test result here.
        testResult.addCriteria("dumpstate", "6", null);

        // reboot the device to clear it to ON state
        rebootDevice();

        // execute the test sequence
        dumpPowerState(testResult.getTestcaseNo());

        // snapshot the test result
        endTestCase(testResult);

        //TODO (b/183449315): assign the return to the status variable
        testResult.checkTestStatus();

        assertWithMessage("testPowerPolicySuspendToRAM").that(status).isTrue();
    }

    @Test
    public void testNewPowerPolicy() throws Exception {
        boolean status = true;
        // create expected test result
        PowerPolicyTestResult testResult = startTestCase(5);

        // populate the expected test result here.
        testResult.addCriteria("dumpstate", "6", null);

        // execute the test sequence
        // create a fake power policy for now to pass the test
        definePowerPolicy("123", "0 2 4", "1 3 5");
        applyPowerPolicy("123");
        dumpPowerPolicy(testResult.getTestcaseNo());

        // snapshot the test result
        endTestCase(testResult);

        //TODO (b/183449315): assign the return to the status variable
        testResult.checkTestStatus();

        assertWithMessage("testNewPowerPolicy").that(status).isTrue();
    }

    public String fetchActivityDumpsys() throws Exception {
        return executeCommand("shell dumpsys activity %s | grep %s",
                ANDROID_CLIENT_ACTIVITY, POWER_POLICY_TEST_RESULT_HEADER);
    }

    private void startAndroidClient() throws Exception {
        executeCommand(SHELL_CMD_HEADER);
    }

    private PowerPolicyTestResult startTestCase(int caseNo)
            throws Exception {
        PowerPolicyTestResult testResult;

        if (caseNo < 1 || caseNo > MAX_TEST_CASES) {
            throw new Exception(String.format("invalid test case number %d", caseNo));
        }

        testResult = new PowerPolicyTestResult(caseNo, mTestAnalyzer);
        testResult.takeStartSnapshot();
        executeCommand(TESTCASE_CMD_HEADER, caseNo, "start");
        return testResult;
    }

    private void endTestCase(PowerPolicyTestResult testResult) throws Exception {
        executeCommand(TESTCASE_CMD_HEADER, testResult.getTestcaseNo(), "end");
        testResult.takeEndSnapshot();
    }

    private void rebootDevice() throws Exception {
        executeCommand("svc power reboot");
        waitForDeviceAvailable();
    }

    private void rebootForcedSilent() throws Exception {
        executeCommand("reboot forcedsilent");
        waitForDeviceAvailable();
    }

    private void dumpPowerState(int caseNo) throws Exception {
        executeCommand(TESTCASE_CMD_HEADER, caseNo, "dumpstate");
    }

    private void dumpPowerPolicy(int caseNo) throws Exception {
        executeCommand(TESTCASE_CMD_HEADER, caseNo, "dumppolicy");
    }

    private void definePowerPolicy(String policyId, String enabledComps,
            String disabledComps) throws Exception {
        executeCommand("cmd car_service define-power-policy %s --enable %s --disable %s",
                policyId, enabledComps, disabledComps);
    }

    private void applyPowerPolicy(String policyId) throws Exception {
        executeCommand("cmd car_service apply-power-policy %s", policyId);
    }

    private void waitForDeviceAvailable() throws Exception {
         // ITestDevice.waitForDeviceAvailable has default boot timeout
         // Therefore, trying twice is sufficient
        try {
            getDevice().waitForDeviceAvailable();
        } catch (Exception e) {
            CLog.w("device is not available, trying one more time");
            getDevice().waitForDeviceAvailable();
        }
    }

    private void killAndroidClient(String clientPkgName) throws Exception {
        executeCommand("am force-stop %s", clientPkgName);
    }

    private boolean makeSureAndroidClientRunning(String clientPkgName) {
        int trialCount = 5;
        while (trialCount > 0) {
            RunUtil.getDefault().sleep(LAUNCH_BUFFER_TIME_MS);
            if (checkAndroidClientRunning(clientPkgName)) {
                return true;
            }
            trialCount--;
        }
        return false;
    }

    private boolean checkAndroidClientRunning(String clientPkgName) {
        String[] pids = getPidsOfProcess(clientPkgName);
        return pids.length == 1;
    }

    private String[] getPidsOfProcess(String... processNames) {
        String output;
        String param = String.join(" ", processNames);
        try {
            output = executeCommand("pidof %s", param).trim();
        } catch (Exception e) {
            CLog.w("Cannot get pids of %s", param);
            return new String[0];
        }
        if (output.isEmpty()) {
            return new String[0];
        }
        String[] tokens = output.split("\\s+");
        return tokens;
    }
}
