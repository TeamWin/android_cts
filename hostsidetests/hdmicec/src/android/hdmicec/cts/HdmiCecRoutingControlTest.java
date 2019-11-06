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

/** HDMI CEC test to test routing control (Section 11.2.2) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRoutingControlTest extends BaseHostJUnit4Test {

    private static final int PHYSICAL_ADDRESS = 0x1000;

    @Rule
    public HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, this);

    /**
     * Test 11.2.2-2
     * Tests that the device broadcasts a <ACTIVE_SOURCE> in response to a <REQUEST_ACTIVE_SOURCE>.
     * This test depends on One Touch Play, and will pass only if One Touch Play passes.
     */
    @Test
    public void cect_11_2_2_2_RequestActiveSource() throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("input keyevent KEYCODE_HOME");
        hdmiCecUtils.sendCecMessage(CecDevice.TV, CecDevice.BROADCAST,
            CecMessage.REQUEST_ACTIVE_SOURCE);
        String message = hdmiCecUtils.checkExpectedOutput(CecMessage.ACTIVE_SOURCE);
        assertEquals(PHYSICAL_ADDRESS, hdmiCecUtils.getParamsFromMessage(message));
    }

    /**
     * Test 11.2.2-4
     * Tests that the device sends a <INACTIVE_SOURCE> message when put on standby.
     * This test depends on One Touch Play, and will pass only if One Touch Play passes.
     */
    @Test
    public void cect_11_2_2_4_InactiveSourceOnStandby() throws Exception {
        ITestDevice device = getDevice();
        try {
            device.executeShellCommand("input keyevent KEYCODE_HOME");
            device.executeShellCommand("input keyevent KEYCODE_SLEEP");
            hdmiCecUtils.checkExpectedOutput(CecMessage.INACTIVE_SOURCE);
        } finally {
            /* Wake up the device */
            device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        }
    }
}
