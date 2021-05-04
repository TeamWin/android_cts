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

import android.car.cts.powerpolicy.CpmsFrameworkLayerStateInfo;
import android.car.cts.powerpolicy.CpmsSystemLayerStateInfo;
import android.car.cts.powerpolicy.PowerPolicyConstants;
import android.car.cts.powerpolicy.PowerPolicyDef;
import android.car.cts.powerpolicy.PowerPolicyTestHelper;
import android.car.cts.powerpolicy.SilentModeInfo;
import android.car.cts.powerpolicy.SystemInfoParser;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class PowerPolicyHostTest extends CarHostJUnit4TestCase {
    private static final String ANDROID_CLIENT_PKG = "android.car.cts.app";
    private static final String ANDROID_CLIENT_ACTIVITY = ANDROID_CLIENT_PKG
            + "/.PowerPolicyTestActivity";
    private static final String POWER_POLICY_TEST_RESULT_HEADER = "PowerPolicyTestClientResult";

    @Before
    public void checkPrecondition() throws Exception {
        checkDefaultPowerPolicySet("pre-condition");
    }

    @Test
    public void testPowerPolicySilentMode() throws Exception {
        String testcase = "testPowerPolicySilentModeFull:";
        int expectedTotalPolicies = PowerPolicyDef.PolicySet.TOTAL_DEFAULT_REGISTERED_POLICIES;
        String teststep;
        PowerPolicyTestHelper testHelper;

        teststep = "1. reboot to forced silent";
        rebootForcedSilent();
        testHelper = new PowerPolicyTestHelper(testcase, teststep, getCpmsFrameworkLayerStateInfo(),
                getCpmsSystemLayerStateInfo(), getSilentModeInfo());
        testHelper.checkCurrentState(PowerPolicyConstants.CarPowerState.ON);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.NO_USER_INTERACTION);
        testHelper.checkSilentModeStatus(true);
        testHelper.checkSilentModeFull(SilentModeInfo.FORCED_SILENT);

        teststep = "2. restore to normal mode";
        restoreFromForcedSilentMode();
        testHelper = new PowerPolicyTestHelper(testcase, teststep, getCpmsFrameworkLayerStateInfo(),
                getCpmsSystemLayerStateInfo(), getSilentModeInfo());
        testHelper.checkCurrentState(PowerPolicyConstants.CarPowerState.ON);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);
        testHelper.checkSilentModeStatus(false);
        testHelper.checkSilentModeFull(SilentModeInfo.NO_SILENT);
    }

    public String fetchActivityDumpsys() throws Exception {
        return executeCommand("shell dumpsys activity %s | grep %s",
                ANDROID_CLIENT_ACTIVITY, POWER_POLICY_TEST_RESULT_HEADER);
    }

    private SilentModeInfo getSilentModeInfo() throws Exception {
        return executeAndParseCommand(
                new SystemInfoParser<SilentModeInfo>(SilentModeInfo.class),
                SilentModeInfo.COMMAND);
    }

    private CpmsFrameworkLayerStateInfo getCpmsFrameworkLayerStateInfo() throws Exception {
        return executeAndParseCommand(new SystemInfoParser<CpmsFrameworkLayerStateInfo>(
                CpmsFrameworkLayerStateInfo.class), CpmsFrameworkLayerStateInfo.COMMAND);
    }

    private CpmsSystemLayerStateInfo getCpmsSystemLayerStateInfo() throws Exception {
        return executeAndParseCommand(new SystemInfoParser<CpmsSystemLayerStateInfo>(
                CpmsSystemLayerStateInfo.class), CpmsSystemLayerStateInfo.COMMAND);
    }

    private void rebootDevice() throws Exception {
        executeCommand("svc power reboot");
        waitForDeviceAvailable();
    }

    private void rebootForcedSilent() throws Exception {
        executeCommand("reboot forcedsilent");
        waitForDeviceAvailable();
    }

    private void restoreFromForcedSilentMode() throws Exception {
        executeCommand("cmd car_service silent-mode non-forced-silent-mode");
    }

    private void definePowerPolicy(String policyStr) throws Exception {
        executeCommand("cmd car_service define-power-policy %s", policyStr);
    }

    private void waitForDeviceAvailable() throws Exception {
        try {
            getDevice().waitForDeviceAvailable();
        } catch (Exception e) {
            CLog.w("device is not available, trying one more time");
            getDevice().waitForDeviceAvailable();
        }
    }

    private void checkDefaultPowerPolicySet(String testcase) throws Exception {
        String teststep = "check if the car power is on the ON state";
        PowerPolicyTestHelper testHelper = new PowerPolicyTestHelper(testcase, teststep,
                getCpmsFrameworkLayerStateInfo(), getCpmsSystemLayerStateInfo(), null);
        testHelper.checkCurrentState(PowerPolicyConstants.CarPowerState.ON);
        testHelper.checkRegisteredPolicy(PowerPolicyDef.PolicySet.INITIAL_ALL_ON);
        testHelper.checkRegisteredPolicy(PowerPolicyDef.PolicySet.DEFAULT_ALL_ON);
        testHelper.checkCurrentPolicy(PowerPolicyDef.IdSet.DEFAULT_ALL_ON);
    }
}
