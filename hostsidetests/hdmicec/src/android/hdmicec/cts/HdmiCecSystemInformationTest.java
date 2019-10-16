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

import com.android.tradefed.testtype.DeviceTestCase;

/** HDMI CEC system information tests (Section 11.2.6) */
public final class HdmiCecSystemInformationTest extends DeviceTestCase {

    /**
     * Test 11.2.6-1
     * Tests for Ack <Polling Message> message.
     */
    public void testAck() throws Exception {
        String command = "poll " + CecDevice.PLAYBACK_1;
        String expectedOutput = "Playback 1 (" + CecDevice.PLAYBACK_1 + "): device " +
            "status changed into 'present'";

        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        if (hdmiCecUtils.init()) {
            try {
                hdmiCecUtils.sendConsoleMessage(command);
                if (!hdmiCecUtils.checkConsoleOutput(expectedOutput)) {
                    throw new Exception("Could not find " + expectedOutput);
                }
            } finally {
                hdmiCecUtils.killCecProcess();
            }
        }
    }

    /**
     * Test 11.2.6-2
     * Tests that the device sends a <REPORT_PHYSICAL_ADDRESS> in response to a
     * <GIVE_PHYSICAL_ADDRESS>
     */
    public void testGivePhysicalAddress() throws Exception {
        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        if (hdmiCecUtils.init()) {
            try {
                hdmiCecUtils.sendCecMessage(CecMessage.GIVE_PHYSICAL_ADDRESS);
                String message = hdmiCecUtils.checkExpectedOutput
                    (CecMessage.REPORT_PHYSICAL_ADDRESS);
            } finally {
                hdmiCecUtils.killCecProcess();
            }
        }
    }

}
