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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

/** HDMI CEC test to verify physical address after device reboot (Section 10.1.2) */
public final class HdmiCecPhysicalAddressTest extends DeviceTestCase {
    private static final int REBOOT_TIMEOUT = 60000;

    public static final String PHY_ADDRESS = "1000";

    /**
     * Test 10.1.2-1
     * Tests that the device broadcasts a <REPORT_PHYSICAL_ADDRESS> after a reboot and that the
     * device has taken the physical address 1.0.0.0.
     */
    public void testRebootPhysicalAddress() throws Exception {
        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        if (!HdmiCecUtils.isHdmiCecFeatureSupported(device)) {
            CLog.v("No HDMI CEC feature running, should skip test.");
            return;
        }

        try {
            hdmiCecUtils.init();
            device.executeShellCommand("reboot");
            device.waitForBootComplete(REBOOT_TIMEOUT);
            String message = hdmiCecUtils.checkExpectedOutput
                (CecMessage.REPORT_PHYSICAL_ADDRESS);
            assertEquals(PHY_ADDRESS,
                            hdmiCecUtils.getParamsFromMessage(message, PHY_ADDRESS.length()));
        } finally {
            hdmiCecUtils.killCecProcess();
        }
    }
}
