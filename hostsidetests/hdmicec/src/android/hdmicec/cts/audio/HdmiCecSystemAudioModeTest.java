/*
 * Copyright 2020 The Android Open Source Project
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

package android.hdmicec.cts.audio;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.Range;

import android.hdmicec.cts.CecDevice;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.HdmiCecClientWrapper;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/** HDMI CEC test to test system audio mode (Section 11.2.15) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemAudioModeTest extends BaseHostJUnit4Test {

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";

    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecAudioManager";

    /** The command to launch the main activity. */
    private static final String START_COMMAND = String.format(
            "am start -n %s/%s.%s -a ", PACKAGE, PACKAGE, CLASS);

    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    private static final int WAIT_TIME = 10;
    private static final CecDevice AUDIO_DEVICE = CecDevice.AUDIO_SYSTEM;
    private static final int ON = 0x1;
    private static final int OFF = 0x0;

    @Rule
    public HdmiCecClientWrapper hdmiCecClient = new HdmiCecClientWrapper(AUDIO_DEVICE, this);

    private void lookForLogFromHdmiCecAudioManager(String expectedOut) throws Exception {
        ITestDevice device = getDevice();
        TimeUnit.SECONDS.sleep(WAIT_TIME);
        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
        // Search for string.
        String testString = "";
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if(line.startsWith("I/" + CLASS)) {
                testString = line.split(":")[1].trim();
                break;
            }
        }
        device.executeAdbCommand("logcat", "-c");
        assertThat(testString).isEqualTo(expectedOut);
    }

    private void muteDevice() throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.MUTE");
    }

    private void unmuteDevice() throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.UNMUTE");
    }

    public boolean isDeviceMuted() throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.REPORT_VOLUME");
        try {
            lookForLogFromHdmiCecAudioManager("Device muted.");
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public void setDeviceVolume(int percentVolume) throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.SET_VOLUME --ei " +
                "\"volumePercent\" " + percentVolume);
    }

    public void sendSystemAudioModeTermination() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST);
    }

    public void sendSystemAudioModeInitiation() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
    }

    private int getDutAudioStatus() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE, CecMessage.GIVE_AUDIO_STATUS);
        String message = hdmiCecClient.checkExpectedOutput(CecDevice.TV,
                CecMessage.REPORT_AUDIO_STATUS);
        return hdmiCecClient.getParamsFromMessage(message);
    }

    private void reportAudioStatus_0_unmuted() throws  Exception {
        unmuteDevice();
        setDeviceVolume(0);
        int reportedVolume = getDutAudioStatus();
        /* Allow for a range of volume, since the actual volume set will depend on the device's
        volume resolution. */
        assertThat(reportedVolume).isEqualTo(0);
    }

    private void reportAudioStatus_50_unmuted() throws  Exception {
        unmuteDevice();
        setDeviceVolume(50);
        int reportedVolume = (getDutAudioStatus() * 100) / 127;
        /* Allow for a range of volume, since the actual volume set will depend on the device's
        volume resolution. */
        assertThat(reportedVolume).isIn(Range.closed(40, 60));
    }

    private void reportAudioStatus_100_unmuted() throws  Exception {
        unmuteDevice();
        setDeviceVolume(100);
        int reportedVolume = getDutAudioStatus();
        /* Allow for a range of volume, since the actual volume set will depend on the device's
        volume resolution. */
        assertThat(reportedVolume).isEqualTo(100);
    }

    private void reportAudioStatusMuted() throws  Exception {
        muteDevice();
        int reportedVolume = getDutAudioStatus();
        /* If device is muted, the 8th bit of CEC message parameters is set and the volume will
        be greater than 127. */
        assertWithMessage("Device not muted").that(reportedVolume).isGreaterThan(127);
    }

    @After
    public void resetVolume() throws Exception {
        setDeviceVolume(20);
    }

    /**
     * Test 11.2.15-1
     * Tests that the device handles <System Audio Mode Request> messages from various logical
     * addresses correctly as a follower.
     */
    @Test
    public void cect_11_2_15_1_SystemAudioModeRequestAsFollower() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS));
        String message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);

        /* Repeat test for device 0x3 (TUNER_1) */
        hdmiCecClient.sendCecMessage(CecDevice.TUNER_1, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS));
        message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);
    }

    /**
     * Test 11.2.15-4
     * Tests that the device responds correctly to a <Give System Audio Status>
     * message when System Audio Mode is "On".
     */
    @Test
    public void cect_11_2_15_4_SystemAudioModeStatusOn() throws Exception {
        sendSystemAudioModeInitiation();
        String message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.GIVE_SYSTEM_AUDIO_MODE_STATUS);
        message = hdmiCecClient.checkExpectedOutput(CecDevice.TV,
                CecMessage.SYSTEM_AUDIO_MODE_STATUS);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);
    }

    /**
     * Test 11.2.15-5
     * Tests that the device sends a <Set System Audio Mode> ["Off"]
     * message when a <System Audio Mode Request> is received with no operands
     */
    @Test
    public void cect_11_2_15_5_SetSystemAudioModeOff() throws Exception {
        sendSystemAudioModeInitiation();
        String message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);
        sendSystemAudioModeTermination();
        message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(OFF);
    }

    /**
     * Test 11.2.15-6
     * Tests that the device sends a <Set System Audio Mode> ["Off"]
     * message before going into standby when System Audio Mode is on.
     */
    @Test
    public void cect_11_2_15_6_SystemAudioModeOffBeforeStandby() throws Exception {
        try {
            getDevice().executeShellCommand("input keyevent KEYCODE_WAKEUP");
            sendSystemAudioModeInitiation();
            String message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
            assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);
            getDevice().executeShellCommand("input keyevent KEYCODE_SLEEP");
            message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
            assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(OFF);
        } finally {
            getDevice().executeShellCommand("input keyevent KEYCODE_WAKEUP");
        }
    }

    /**
    * Test 11.2.15-7
    * Tests that the device responds correctly to a <Give System Audio Mode Status>
    * message when the System Audio Mode is "Off".
    */
   @Test
    public void cect_11_2_15_7_SystemAudioModeStatusOff() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SET_SYSTEM_AUDIO_MODE, hdmiCecClient.formatParams(OFF));
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.GIVE_SYSTEM_AUDIO_MODE_STATUS);
        String message = hdmiCecClient.checkExpectedOutput(CecDevice.TV,
                CecMessage.SYSTEM_AUDIO_MODE_STATUS);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(OFF);
    }

    /**
     * Test 11.2.15-8
     * Tests that the device handles <User Controlled Pressed> ["Mute"]
     * correctly when System Audio Mode is "On".
     */
    @Test
    public void cect_11_2_15_8_HandleUcpMute() throws Exception {
        unmuteDevice();
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS));
        hdmiCecClient.sendUserControlPressAndRelease(CecDevice.TV, AUDIO_DEVICE,
                HdmiCecConstants.CEC_CONTROL_MUTE, false);
        assertWithMessage("Device is not muted").that(isDeviceMuted()).isTrue();
    }

    /**
     * Test 11.2.15-9
     * Tests that the device responds with a <Report Audio Status> message to a
     * <Give Audio Status> message.
     */
    @Test
    public void cect_11_2_15_9_ReportAudioStatus() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        /** Set volume to 0 % and check the <Report Audio Status> */
        reportAudioStatus_0_unmuted();

        /** Set volume to 50 % and check the <Report Audio Status> */
        reportAudioStatus_50_unmuted();

        /** Set volume to 100 % and check the <Report Audio Status> */
        reportAudioStatus_100_unmuted();

        /** Mute volume and check the <Report Audio Status> */
        reportAudioStatusMuted();
    }

    /**
     * Test 11.2.15-16
     * Tests that the device unmute its volume when it broadcasts a
     * <Set System Audio Mode> ["On"] message
     */
    @Test
    public void cect_11_2_15_16_UnmuteForSystemAudioRequestOn() throws Exception {
        muteDevice();
        sendSystemAudioModeTermination();
        String message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(OFF);
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);
        assertWithMessage("Device muted").that(isDeviceMuted()).isFalse();
    }

    /**
     * Test 11.2.15-17
     * Tests that the device mute its volume when it broadcasts a
     * <Set System Audio Mode> ["Off"] message
     */
    @Test
    public void cect_11_2_15_17_MuteForSystemAudioRequestOff() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        String message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(ON);
        sendSystemAudioModeTermination();
        message = hdmiCecClient.checkExpectedOutput(CecMessage.SET_SYSTEM_AUDIO_MODE);
        assertThat(hdmiCecClient.getParamsFromMessage(message)).isEqualTo(OFF);
        assertWithMessage("Device not muted").that(isDeviceMuted()).isTrue();
    }
}
