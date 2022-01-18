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
import static com.google.common.truth.Truth.assertWithMessage;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.WakeLockHelper;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HDMI CEC tests verifying power status related messages of the device (CEC 2.0 CTS Section 7.6)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecPowerStatusTest extends BaseHdmiCecCtsTest {

    private static final int ON = 0x0;
    private static final int OFF = 0x1;
    private static final int IN_TRANSITION_TO_STANDBY = 0x3;

    private static final int SLEEP_TIMESTEP_SECONDS = 1;

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
        setCec20();

        // Move device to standby
        sendDeviceToSleep();

        // Turn device on
        wakeUpDevice();

        String reportPowerStatus = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                CecOperand.REPORT_POWER_STATUS);

        if (CecMessage.getParams(reportPowerStatus) == HdmiCecConstants.CEC_POWER_STATUS_STANDBY) {
            // Received the "turning off" broadcast, check for the next broadcast message
            reportPowerStatus = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                    CecOperand.REPORT_POWER_STATUS);
        }

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
        setCec20();

        try {
            // Turn device on
            wakeUpDevice();

            // Move device to standby
            sendDeviceToSleep();

            String reportPowerStatus =
                    hdmiCecClient.checkExpectedOutput(
                            LogicalAddress.BROADCAST, CecOperand.REPORT_POWER_STATUS);

            if (CecMessage.getParams(reportPowerStatus) == HdmiCecConstants.CEC_POWER_STATUS_ON) {
                // Received the "wake up" broadcast, check for the next broadcast message
                reportPowerStatus =
                        hdmiCecClient.checkExpectedOutput(
                                LogicalAddress.BROADCAST, CecOperand.REPORT_POWER_STATUS);
            }

            assertThat(CecMessage.getParams(reportPowerStatus))
                    .isEqualTo(HdmiCecConstants.CEC_POWER_STATUS_STANDBY);
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test 11.1.14-1, 11.2.14-1
     *
     * <p>Tests that the device sends a {@code <REPORT_POWER_STATUS>} with params 0x0 when the
     * device is powered on.
     */
    @Test
    public void cect_PowerStatusWhenOn() throws Exception {
        ITestDevice device = getDevice();
        /* Make sure the device is not booting up/in standby */
        device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
        wakeUpDevice();

        LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
        hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.GIVE_POWER_STATUS);
        String message =
                hdmiCecClient.checkExpectedOutput(cecClientDevice, CecOperand.REPORT_POWER_STATUS);
        assertThat(CecMessage.getParams(message)).isEqualTo(ON);
    }

    /**
     * Test 11.2.14-1, 11.2.14-2
     *
     * <p>Tests that the device sends a {@code <REPORT_POWER_STATUS>} with params 0x1 when the
     * device is powered off.
     */
    @Test
    public void cect_PowerStatusWhenOff() throws Exception {
        ITestDevice device = getDevice();
        try {
            /* Make sure the device is not booting up/in standby */
            device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
            /* The sleep below could send some devices into a deep suspend state. */
            sendDeviceToSleep();
            int waitTimeSeconds = HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS;
            int powerStatus;
            LogicalAddress cecClientDevice = hdmiCecClient.getSelfDevice();
            do {
                TimeUnit.SECONDS.sleep(SLEEP_TIMESTEP_SECONDS);
                waitTimeSeconds += SLEEP_TIMESTEP_SECONDS;
                hdmiCecClient.sendCecMessage(cecClientDevice, CecOperand.GIVE_POWER_STATUS);
                powerStatus =
                        CecMessage.getParams(
                                hdmiCecClient.checkExpectedOutput(
                                        cecClientDevice, CecOperand.REPORT_POWER_STATUS));
            } while (powerStatus == IN_TRANSITION_TO_STANDBY &&
                    waitTimeSeconds <= HdmiCecConstants.MAX_SLEEP_TIME_SECONDS);
            assertThat(powerStatus).isEqualTo(OFF);
        } finally {
            /* Wake up the device */
            wakeUpDevice();
        }
    }

    /**
     * Test HF4-6-8
     *
     * Tests that a device comes out of the standby state when it receives a {@code <User Control
     * Pressed>} message with power related operands.
     */
    @Test
    public void cect_hf4_6_8_userControlPressed_powerOn() throws Exception {
        ITestDevice device = getDevice();
        List<Integer> powerControlOperands = Arrays.asList(HdmiCecConstants.CEC_KEYCODE_POWER,
                HdmiCecConstants.CEC_KEYCODE_POWER_ON_FUNCTION,
                HdmiCecConstants.CEC_KEYCODE_POWER_TOGGLE_FUNCTION);

        LogicalAddress source = hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV)
                ? LogicalAddress.PLAYBACK_1
                : LogicalAddress.TV;

        for (Integer operand : powerControlOperands) {
            try {
                sendDeviceToSleep();
                String wakeStateBefore = device.executeShellCommand(
                        "dumpsys power | grep mWakefulness=");
                assertThat(wakeStateBefore.trim()).isEqualTo("mWakefulness=Asleep");

                hdmiCecClient.sendUserControlPressAndRelease(source, operand, false);

                TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
                String wakeStateAfter = device.executeShellCommand(
                        "dumpsys power | grep mWakefulness=");
                assertWithMessage("Device should wake up on <User Control Pressed> %s", operand)
                        .that(wakeStateAfter.trim()).isEqualTo("mWakefulness=Awake");
            } finally {
                wakeUpDevice();
            }
        }
    }

    /**
     * Test HF4-6-10
     *
     * Tests that a device comes enters the standby state when it receives a {@code <User Control
     * Pressed>} message with power related operands.
     */
    @Test
    public void cect_hf4_6_10_userControlPressed_powerOff() throws Exception {
        ITestDevice device = getDevice();
        List<Integer> powerControlOperands = Arrays.asList(
                HdmiCecConstants.CEC_KEYCODE_POWER_OFF_FUNCTION,
                HdmiCecConstants.CEC_KEYCODE_POWER_TOGGLE_FUNCTION);

        LogicalAddress source = hasDeviceType(HdmiCecConstants.CEC_DEVICE_TYPE_TV)
                ? LogicalAddress.PLAYBACK_1
                : LogicalAddress.TV;

        for (Integer operand : powerControlOperands) {
            try {
                wakeUpDevice();
                String wakeStateBefore = device.executeShellCommand(
                        "dumpsys power | grep mWakefulness=");
                assertThat(wakeStateBefore.trim()).isEqualTo("mWakefulness=Awake");

                WakeLockHelper.acquirePartialWakeLock(device);
                hdmiCecClient.sendUserControlPressAndRelease(source, operand, false);

                TimeUnit.SECONDS.sleep(HdmiCecConstants.DEVICE_WAIT_TIME_SECONDS);
                String wakeStateAfter = device.executeShellCommand(
                        "dumpsys power | grep mWakefulness=");
                assertWithMessage("Device should go to standby on <User Control Pressed> %s",
                        operand)
                        .that(wakeStateAfter.trim()).isEqualTo("mWakefulness=Asleep");
            } finally {
                wakeUpDevice();
            }
        }
    }
}
