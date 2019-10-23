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
import com.android.tradefed.testtype.DeviceTestCase;

/** HDMI CEC tests for One Touch Play (Section 11.2.1) */
public final class HdmiCecOneTouchPlayTest extends DeviceTestCase {

    private static final String PHYSICAL_ADDRESS = "1000";

    /**
     * Test 11.2.1-1
     * Tests that the device sends a <TEXT_VIEW_ON> when the home key is pressed on device, followed
     * by a <ACTIVE_SOURCE> message.
     */
    public void testOneTouchPlay() throws Exception {
        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        if (hdmiCecUtils.init()) {
            try {
                ITestDevice device = getDevice();
                assertNotNull("Device not set", device);
                device.executeShellCommand("input keyevent KEYCODE_HOME");
                hdmiCecUtils.checkExpectedOutput(CecDevice.TV, CecMessage.TEXT_VIEW_ON);
                String message = hdmiCecUtils.checkExpectedOutput(CecMessage.ACTIVE_SOURCE);
                assertEquals(PHYSICAL_ADDRESS, hdmiCecUtils.getParamsFromMessage(message));
            } finally {
                hdmiCecUtils.killCecProcess();
            }
        }
    }
}
