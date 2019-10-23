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

/** HDMI CEC test to test routing control (Section 11.2.2) */
public final class HdmiCecRoutingControlTest extends DeviceTestCase {

    private static final String PHYSICAL_ADDRESS = "1000";

    /**
     * Test 11.2.2-2
     * Tests that the device broadcasts a <ACTIVE_SOURCE> in response to a <REQUEST_ACTIVE_SOURCE>.
     * This test depends on One Touch Play, and will pass only if One Touch Play passes.
     */
    public void testRequestActiveSource() throws Exception {
        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        if (hdmiCecUtils.init()) {
            try {
                ITestDevice device = getDevice();
                assertNotNull("Device not set", device);
                device.executeShellCommand("input keyevent KEYCODE_HOME");
                hdmiCecUtils.sendCecMessage(CecDevice.TV, CecDevice.BROADCAST,
                    CecMessage.REQUEST_ACTIVE_SOURCE);
                String message = hdmiCecUtils.checkExpectedOutput(CecMessage.ACTIVE_SOURCE);
                assertEquals(PHYSICAL_ADDRESS, hdmiCecUtils.getParamsFromMessage(message));
            } finally {
                hdmiCecUtils.killCecProcess();
            }
        }
    }

    /**
     * Test 11.2.2-4
     * Tests that the device sends a <INACTIVE_SOURCE> message when put on standby.
     * This test depends on One Touch Play, and will pass only if One Touch Play passes.
     */
    public void testInactiveSourceOnStandby() throws Exception {
        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        if (hdmiCecUtils.init()) {
            ITestDevice device = null;
            try {
                device = getDevice();
                assertNotNull("Device not set", device);
                device.executeShellCommand("input keyevent KEYCODE_HOME");
                device.executeShellCommand("input keyevent KEYCODE_POWER");
                hdmiCecUtils.checkExpectedOutput(CecMessage.INACTIVE_SOURCE);
            } finally {
                /* Wake up the device again */
                if (device != null) {
                    device.executeShellCommand("input keyevent KEYCODE_POWER");
                }
                hdmiCecUtils.killCecProcess();
            }
        }
    }
}
