/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hdmicec.cts.common;

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.CecVersionHelper;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * HDMI CEC tests verifying power status related messages of the device (CEC 2.0 CTS Section 7.6)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecPowerStatusTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain mRuleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(hdmiCecClient);

    /**
     * Test HF4-6-20
     *
     * Verifies that {@code <Report Power Status>} message is broadcast when the device transitions
     * from standby to on.
     */
    @Test
    public void cect_hf4_6_20_broadcastsWhenTurningOn() throws Exception {
        ITestDevice device = getDevice();
        CecVersionHelper.setCec20(device);

        // Move device to standby
        device.executeShellCommand("input keyevent KEYCODE_SLEEP");

        // Turn device on
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");

        String reportPowerStatus = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                CecOperand.REPORT_POWER_STATUS);
        assertThat(CecMessage.getParams(reportPowerStatus)).isEqualTo(
                HdmiCecConstants.CEC_POWER_STATUS_ON);
    }

    /**
     * Test HF4-6-21
     *
     * Verifies that {@code <Report Power Status>} message is broadcast when the device transitions
     * from on to standby.
     */
    @Test
    public void cect_hf4_6_21_broadcastsWhenGoingToStandby() throws Exception {
        ITestDevice device = getDevice();
        CecVersionHelper.setCec20(device);

        // Turn device on
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");

        // Move device to standby
        device.executeShellCommand("input keyevent KEYCODE_SLEEP");

        String reportPowerStatus = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                CecOperand.REPORT_POWER_STATUS);
        assertThat(CecMessage.getParams(reportPowerStatus)).isEqualTo(
                HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
    }

}
