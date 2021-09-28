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

package android.hdmicec.cts;

import com.android.tradefed.device.ITestDevice;

/** Helper class with methods to test the remote control passthrough functionality */
public final class RemoteControlPassthrough {

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";
    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecKeyEventCapture";
    /** The command to launch the main activity. */
    private static final String START_COMMAND =
            String.format(
                    "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
                    PACKAGE, PACKAGE, CLASS);
    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    /**
     * Tests that the device responds correctly to a {@code <USER_CONTROL_PRESSED>} message followed
     * immediately by a {@code <USER_CONTROL_RELEASED>} message.
     */
    public static void checkUserControlPressAndRelease(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_UP, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_DOWN, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_LEFT, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_RIGHT, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_SELECT, false);
        LogHelper.assertLog(
                device, CLASS, "Short press KEYCODE_DPAD_CENTER", "Short press KEYCODE_ENTER");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_BACK, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_BACK");
    }

    /**
     * Tests that the device responds correctly to a {@code <USER_CONTROL_PRESSED>} message for
     * press and hold operations.
     */
    public static void checkUserControlPressAndHold(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_UP, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_DOWN, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_LEFT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_RIGHT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_SELECT, true);
        LogHelper.assertLog(
                device, CLASS, "Long press KEYCODE_DPAD_CENTER", "Long press KEYCODE_ENTER");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_BACK, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_BACK");
    }

    /**
     * Tests that the device responds correctly to a {@code <User Control Pressed>} message for
     * press and hold operations when no {@code <User Control Released>} is sent.
     */
    public static void checkUserControlPressAndHoldWithNoRelease(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_UP, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_DOWN, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_LEFT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_RIGHT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_SELECT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_CENTER");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_CONTROL_BACK, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_BACK");
    }

    /**
     * Tests that the device responds correctly to a {@code <User Control Pressed> [firstKeycode]}
     * press and hold operation when interrupted by a {@code <User Control Pressed> [secondKeycode]}
     * before a {@code <User Control Released> [firstKeycode]} is sent.
     */
    public static void checkUserControlInterruptedPressAndHoldWithNoRelease(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_CONTROL_UP,
                HdmiCecConstants.CEC_CONTROL_BACK,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_CONTROL_DOWN,
                HdmiCecConstants.CEC_CONTROL_UP,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_CONTROL_LEFT,
                HdmiCecConstants.CEC_CONTROL_DOWN,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_CONTROL_RIGHT,
                HdmiCecConstants.CEC_CONTROL_LEFT,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_CONTROL_SELECT,
                HdmiCecConstants.CEC_CONTROL_RIGHT,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_CENTER");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_CONTROL_BACK,
                HdmiCecConstants.CEC_CONTROL_SELECT,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_BACK");
    }
}
