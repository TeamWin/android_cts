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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

/** HDMI CEC test to verify physical address after device reboot (Section 10.2.3) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecLogicalAddressTest implements IDeviceTest {
    private static final CecDevice PLAYBACK_DEVICE = CecDevice.PLAYBACK_1;

    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Before public void testHdmiCecAvailability() throws Exception {
        assumeTrue(HdmiCecUtils.isHdmiCecFeatureSupported(getDevice()));
    }

    /**
     * Test 10.2.3-1
     * Tests that the device broadcasts a <REPORT_PHYSICAL_ADDRESS> after a reboot and that the
     * device has taken the logical address "4".
     */
    @Test
    public void cect_10_2_3_1_RebootLogicalAddress() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        try {
            hdmiCecUtils.init();
            device.executeShellCommand("reboot");
            device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
            String message = hdmiCecUtils.checkExpectedOutput(CecMessage.REPORT_PHYSICAL_ADDRESS);
            assertEquals(PLAYBACK_DEVICE, hdmiCecUtils.getSourceFromMessage(message));
        } finally {
            hdmiCecUtils.killCecProcess();
        }
    }
}
