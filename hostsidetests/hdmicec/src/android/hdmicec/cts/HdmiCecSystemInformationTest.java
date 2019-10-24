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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

/** HDMI CEC system information tests (Section 11.2.6) */
public final class HdmiCecSystemInformationTest extends DeviceTestCase {

    /** The version number 0x05 refers to CEC v1.4 */
    public static final String CEC_VERSION_NUMBER = "05";

    /**
     * Test 11.2.6-1
     * Tests for Ack <Polling Message> message.
     */
    public void testAck() throws Exception {

        if (!HdmiCecUtils.isHdmiCecFeatureSupported(getDevice())) {
            CLog.v("No HDMI CEC feature running, should skip test.");
            return;
        }

        String command = "poll " + CecDevice.PLAYBACK_1;
        String expectedOutput = "Playback 1 (" + CecDevice.PLAYBACK_1 + "): device " +
            "status changed into 'present'";

        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");


        try {
            hdmiCecUtils.init();
            hdmiCecUtils.sendConsoleMessage(command);
            if (!hdmiCecUtils.checkConsoleOutput(expectedOutput)) {
                throw new Exception("Could not find " + expectedOutput);
            }
        } finally {
            hdmiCecUtils.killCecProcess();
        }
    }

    /**
     * Test 11.2.6-2
     * Tests that the device sends a <REPORT_PHYSICAL_ADDRESS> in response to a
     * <GIVE_PHYSICAL_ADDRESS>
     */
    public void testGivePhysicalAddress() throws Exception {

        if (!HdmiCecUtils.isHdmiCecFeatureSupported(getDevice())) {
            CLog.v("No HDMI CEC feature running, should skip test.");
            return;
        }

        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        try {
            hdmiCecUtils.init();
            hdmiCecUtils.sendCecMessage(CecMessage.GIVE_PHYSICAL_ADDRESS);
            String message = hdmiCecUtils.checkExpectedOutput(CecMessage.REPORT_PHYSICAL_ADDRESS);
        } finally {
            hdmiCecUtils.killCecProcess();
        }
    }

    /**
     * Test 11.2.6-6
     * Tests that the device sends a <CEC_VERSION> in response to a <GET_CEC_VERSION>
     */
    public void testGiveCecVersion() throws Exception {

        if (!HdmiCecUtils.isHdmiCecFeatureSupported(getDevice())) {
            CLog.v("No HDMI CEC feature running, should skip test.");
            return;
        }

        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        try {
            hdmiCecUtils.init();
            hdmiCecUtils.sendCecMessage(CecDevice.TV, CecMessage.GET_CEC_VERSION);
            String message = hdmiCecUtils.checkExpectedOutput(CecDevice.TV,
                                                              CecMessage.CEC_VERSION);

            assertEquals(CEC_VERSION_NUMBER, hdmiCecUtils.getParamsFromMessage(message));
        } finally {
            hdmiCecUtils.killCecProcess();
        }
    }

    /**
     * Test 11.2.6-7
     * Tests that the device sends a <FEATURE_ABORT> in response to a <GET_MENU_LANGUAGE>
     */
    public void testGetMenuLanguage() throws Exception {
        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        if (!HdmiCecUtils.isHdmiCecFeatureSupported(getDevice())) {
            CLog.v("No HDMI CEC feature running, should skip test.");
            return;
        }

        try {
            hdmiCecUtils.init();
            hdmiCecUtils.sendCecMessage(CecDevice.TV, CecMessage.GET_MENU_LANGUAGE);
            String message = hdmiCecUtils.checkExpectedOutput(CecDevice.TV,
                                                              CecMessage.FEATURE_ABORT);
            String params = hdmiCecUtils.getParamsFromMessage(message,
                CecMessage.GET_MENU_LANGUAGE.toString().length());
            assertEquals(params, CecMessage.GET_MENU_LANGUAGE.toString());
        } finally {
            hdmiCecUtils.killCecProcess();
        }
    }

}
