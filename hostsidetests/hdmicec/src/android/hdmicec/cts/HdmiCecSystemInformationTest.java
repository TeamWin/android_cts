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

import static org.junit.Assert.assertEquals;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;

/** HDMI CEC system information tests (Section 11.2.6) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemInformationTest extends BaseHostJUnit4Test {

    /** The version number 0x05 refers to CEC v1.4 */
    private static final int CEC_VERSION_NUMBER = 0x05;

    @Rule
    public HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, this);

    /**
     * Test 11.2.6-1
     * Tests for Ack <Polling Message> message.
     */
    @Test
    public void cect_11_2_6_1_Ack() throws Exception {
        String command = CecClientMessage.POLL + " " + CecDevice.PLAYBACK_1;
        String expectedOutput = "POLL sent";
        hdmiCecUtils.sendConsoleMessage(command);
        if (!hdmiCecUtils.checkConsoleOutput(expectedOutput)) {
            throw new Exception("Could not find " + expectedOutput);
        }
    }

    /**
     * Test 11.2.6-2
     * Tests that the device sends a <REPORT_PHYSICAL_ADDRESS> in response to a
     * <GIVE_PHYSICAL_ADDRESS>
     */
    @Test
    public void cect_11_2_6_2_GivePhysicalAddress() throws Exception {
        hdmiCecUtils.sendCecMessage(CecMessage.GIVE_PHYSICAL_ADDRESS);
        String message = hdmiCecUtils.checkExpectedOutput(CecMessage.REPORT_PHYSICAL_ADDRESS);
        /* The checkExpectedOutput has already verified the first 4 nibbles of the message. We
            * have to verify the last 6 nibbles */
        int receivedParams = hdmiCecUtils.getParamsFromMessage(message);
        assertEquals(HdmiCecConstants.PHYSICAL_ADDRESS, receivedParams >> 8);
        assertEquals(HdmiCecConstants.PLAYBACK_DEVICE_TYPE, receivedParams & 0xFF);
    }

    /**
     * Test 11.2.6-6
     * Tests that the device sends a <CEC_VERSION> in response to a <GET_CEC_VERSION>
     */
    @Test
    public void cect_11_2_6_6_GiveCecVersion() throws Exception {
        hdmiCecUtils.sendCecMessage(CecDevice.TV, CecMessage.GET_CEC_VERSION);
        String message = hdmiCecUtils.checkExpectedOutput(CecDevice.TV,
                                                            CecMessage.CEC_VERSION);

        assertEquals(CEC_VERSION_NUMBER, hdmiCecUtils.getParamsFromMessage(message));
    }

    /**
     * Test 11.2.6-7
     * Tests that the device sends a <FEATURE_ABORT> in response to a <GET_MENU_LANGUAGE>
     */
    @Test
    public void cect_11_2_6_7_GetMenuLanguage() throws Exception {
        hdmiCecUtils.sendCecMessage(CecDevice.TV, CecMessage.GET_MENU_LANGUAGE);
        String message = hdmiCecUtils.checkExpectedOutput(CecDevice.TV,
                                                            CecMessage.FEATURE_ABORT);
        int abortedOpcode = hdmiCecUtils.getParamsFromMessage(message,
            CecMessage.GET_MENU_LANGUAGE.toString().length());
        assertEquals(CecMessage.getMessage(abortedOpcode), CecMessage.GET_MENU_LANGUAGE);
    }

}
