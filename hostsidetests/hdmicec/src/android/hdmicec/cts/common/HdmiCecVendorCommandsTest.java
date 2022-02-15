/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.LogHelper;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.Test;

/** HDMI CEC test to verify device vendor specific commands (Section 11.2.9) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecVendorCommandsTest extends BaseHdmiCecCtsTest {

    private static final int INCORRECT_VENDOR_ID = 0x0;

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";
    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecVendorCommandListener";
    /** The command to launch the main activity. */
    private static final String START_COMMAND =
            String.format("am start -W -n %s/%s.%s  ", PACKAGE, PACKAGE, CLASS);

    private static final String VENDOR_LISTENER_WITH_ID =
            "-a android.hdmicec.app.VENDOR_LISTENER_WITH_ID";
    private static final String VENDOR_LISTENER_WITHOUT_ID =
            "-a android.hdmicec.app.VENDOR_LISTENER_WITHOUT_ID";
    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    // This has to be the same as the vendor ID used in the HdmiCecVendorCommandListener
    private static final int VENDOR_ID = 0xBADDAD;

    @Rule
    public RuleChain ruleChain =
        RuleChain
            .outerRule(CecRules.requiresCec(this))
            .around(CecRules.requiresLeanback(this))
            .around(hdmiCecClient);

    /**
     * Test 11.2.9-1
     * <p>Tests that the device responds to a {@code <GIVE_DEVICE_VENDOR_ID>} from various source
     * devices with a {@code <DEVICE_VENDOR_ID>}.
     */
    @Test
    public void cect_11_2_9_1_GiveDeviceVendorId() throws Exception {
        for (LogicalAddress logicalAddress : LogicalAddress.values()) {
            // Skip the logical address of this device
            if (hasLogicalAddress(logicalAddress)) {
                continue;
            }
            hdmiCecClient.sendCecMessage(logicalAddress, CecOperand.GIVE_DEVICE_VENDOR_ID);
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.DEVICE_VENDOR_ID);
            assertThat(CecMessage.getParams(message)).isNotEqualTo(INCORRECT_VENDOR_ID);
        }
    }

    /**
     * Tests that the device responds to a {@code <GIVE_DEVICE_VENDOR_ID>} when in standby.
     */
    @Test
    public void cectGiveDeviceVendorIdDuringStandby() throws Exception {
        ITestDevice device = getDevice();
        try {
            sendDeviceToSleepAndValidate();
            cect_11_2_9_1_GiveDeviceVendorId();
        } finally {
            wakeUpDevice();
        }
    }

    /**
     * Test 11.2.9-2
     * <p>Tests that the device broadcasts a {@code <DEVICE_VENDOR_ID>} message after successful
     * initialisation and address allocation.
     */
    @Test
    public void cect_11_2_9_2_DeviceVendorIdOnInit() throws Exception {
        ITestDevice device = getDevice();
        device.reboot();
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.DEVICE_VENDOR_ID);
        assertThat(CecMessage.getParams(message)).isNotEqualTo(INCORRECT_VENDOR_ID);
    }

    @Test
    public void cecVendorCommandListenerWithVendorIdTest() throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + VENDOR_LISTENER_WITH_ID);

        String params = CecMessage.formatParams(VENDOR_ID);
        params += CecMessage.formatParams("010203");
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.VENDOR_COMMAND_WITH_ID, params);

        LogHelper.assertLog(device, CLASS, "Received vendor command with correct vendor ID");
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
    }

    @Test
    public void cecVendorCommandListenerReceivesVendorCommandWithoutId() throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + VENDOR_LISTENER_WITH_ID);

        String params = CecMessage.formatParams("010203");
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.VENDOR_COMMAND, params);

        LogHelper.assertLog(device, CLASS, "Received vendor command without vendor ID");
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
    }

    @Test
    public void cecVendorCommandListenerWithoutVendorIdTest() throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + VENDOR_LISTENER_WITHOUT_ID);

        String params = CecMessage.formatParams("010203");
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.VENDOR_COMMAND, params);

        LogHelper.assertLog(device, CLASS, "Received vendor command without vendor ID");
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
    }

    @Test
    public void cecVendorCommandListenerWithoutVendorIdDoesNotReceiveTest() throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + VENDOR_LISTENER_WITHOUT_ID);

        String params = CecMessage.formatParams(VENDOR_ID);
        params += CecMessage.formatParams("010203");
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, CecOperand.VENDOR_COMMAND_WITH_ID, params);

        LogHelper.assertLogDoesNotContain(
                device, CLASS, "Received vendor command with correct vendor ID");
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
    }
}
