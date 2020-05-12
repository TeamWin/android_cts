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

package android.hdmicec.cts.audio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.CecDevice;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.HdmiCecClientWrapper;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.RequiredPropertyRule;
import android.hdmicec.cts.RequiredFeatureRule;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/** HDMI CEC test to verify that device ignores invalid messages (Section 12) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecInvalidMessagesTest extends BaseHostJUnit4Test {
    private static final CecDevice AUDIO_DEVICE = CecDevice.AUDIO_SYSTEM;
    private static final String PROPERTY_LOCALE = "persist.sys.locale";

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";

    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecKeyEventCapture";

    /** The command to launch the main activity. */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    private static final int WAIT_TIME = 10;

    public HdmiCecClientWrapper hdmiCecClient = new HdmiCecClientWrapper(AUDIO_DEVICE);

    @Rule
    public RuleChain ruleChain =
        RuleChain
            .outerRule(new RequiredFeatureRule(this, CecDevice.HDMI_CEC_FEATURE))
            .around(new RequiredFeatureRule(this, CecDevice.LEANBACK_FEATURE))
            .around(RequiredPropertyRule.asCsvContainsValue(
                this,
                CecDevice.HDMI_DEVICE_TYPE_PROPERTY,
                AUDIO_DEVICE.getDeviceType()))
            .around(hdmiCecClient);

    private String getSystemLocale() throws Exception {
        ITestDevice device = getDevice();
        return device.executeShellCommand("getprop " + PROPERTY_LOCALE).trim();
    }

    private void setSystemLocale(String locale) throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("setprop " + PROPERTY_LOCALE + " " + locale);
    }

    private boolean isLanguageEditable() throws Exception {
        String val = getDevice().executeShellCommand("getprop ro.hdmi.set_menu_language");
        return val.trim().equals("true") ? true : false;
    }

    private static String extractLanguage(String locale) {
        return locale.split("[^a-zA-Z]")[0];
    }

    private void checkArcIsInitiated(){
        try {
            hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.REQUEST_ARC_INITIATION);
            hdmiCecClient.checkExpectedOutput(CecDevice.TV, CecMessage.INITIATE_ARC);
        } catch(Exception e) {
            assumeNoException(e);
        }
    }

    private void logShouldNotContain(String expectedOut) throws Exception {
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
        assertThat(testString).doesNotContain(expectedOut);
    }

    /**
     * Test 12-1
     * Tests that the device ignores every broadcast only message that is received as
     * directly addressed.
     */
    @Test
    public void cect_12_1_BroadcastReceivedAsDirectlyAddressed() throws Exception {
        /* <Set Menu Language> */
        assumeTrue(isLanguageEditable());
        final String locale = getSystemLocale();
        final String originalLanguage = extractLanguage(locale);
        final String language = originalLanguage.equals("spa") ? "eng" : "spa";
        try {
            hdmiCecClient.sendCecMessage(
                    CecDevice.TV,
                    AUDIO_DEVICE,
                    CecMessage.SET_MENU_LANGUAGE,
                    hdmiCecClient.convertStringToHexParams(language));
            assertThat(originalLanguage).isEqualTo(extractLanguage(getSystemLocale()));
        } finally {
            // If the language was incorrectly changed during the test, restore it.
            setSystemLocale(locale);
        }
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GET_CEC_VERSION> if received as
     * a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_getCecVersion() throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.GET_CEC_VERSION);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.TV,
                CecMessage.CEC_VERSION);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_PHYSICAL_ADDRESS> if received
     * as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_givePhysicalAddress()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.GIVE_PHYSICAL_ADDRESS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.BROADCAST,
                CecMessage.REPORT_PHYSICAL_ADDRESS);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_AUDIO_STATUS> if received as
     * a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveAudioStatus() throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.GIVE_AUDIO_STATUS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.TV,
                CecMessage.REPORT_AUDIO_STATUS);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_POWER_STATUS> if received as
     * a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_givePowerStatus() throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.GIVE_POWER_STATUS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.TV,
                CecMessage.REPORT_POWER_STATUS);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_DEVICE_VENDOR_ID> if received
     * as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveDeviceVendorId()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.GIVE_DEVICE_VENDOR_ID);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.BROADCAST,
                CecMessage.DEVICE_VENDOR_ID);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_OSD_NAME> if received as
     * a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveOsdName() throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.GIVE_OSD_NAME);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.TV,
                CecMessage.SET_OSD_NAME);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <GIVE_SYSTEM_AUDIO_MODE_STATUS> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_giveSystemAudioModeStatus()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.GIVE_SYSTEM_AUDIO_MODE_STATUS);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.TV,
                CecMessage.SYSTEM_AUDIO_MODE_STATUS);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <REQUEST_SHORT_AUDIO_DESCRIPTOR> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_requestShortAudioDescriptor()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.REQUEST_SHORT_AUDIO_DESCRIPTOR,
                hdmiCecClient.formatParams("01020304"));
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.TV,
                CecMessage.REPORT_SHORT_AUDIO_DESCRIPTOR);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <SYSTEM_AUDIO_MODE_REQUEST> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_systemAudioModeRequest()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.SYSTEM_AUDIO_MODE_REQUEST);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.BROADCAST,
                CecMessage.SET_SYSTEM_AUDIO_MODE);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <REQUEST_ARC_INITIATION> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_requestArcInitiation()
        throws Exception {
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.REQUEST_ARC_INITIATION);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.BROADCAST,
                CecMessage.INITIATE_ARC);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <REQUEST_ARC_TERMINATION> if
     * received as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_requestArcTermination()
        throws Exception {
        checkArcIsInitiated();
        hdmiCecClient.sendCecMessage(
                CecDevice.TV,
                CecDevice.BROADCAST,
                CecMessage.REQUEST_ARC_TERMINATION);
        hdmiCecClient.checkOutputDoesNotContainMessage(
                CecDevice.BROADCAST,
                CecMessage.TERMINATE_ARC);
    }

    /**
     * Test 12-2
     * Tests that the device ignores directly addressed message <USER_CONTROL_PRESSED> if received
     * as a broadcast message
     */
    @Test
    public void cect_12_2_DirectlyAddressedReceivedAsBroadcast_userControlPressed()
        throws Exception {
        ITestDevice device = getDevice();
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPressAndRelease(
                CecDevice.TV,
                CecDevice.BROADCAST,
                HdmiCecConstants.CEC_CONTROL_UP,
                false);
        logShouldNotContain("Short press KEYCODE_DPAD_UP");
    }
}
