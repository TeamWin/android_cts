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

/** HDMI CEC test to check if the device reports power status correctly (Section 11.2.14) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecPowerStatusTest implements IDeviceTest {

    private static final int ON = 0x0;
    private static final int OFF = 0x1;

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
     * Test 11.2.14-1
     * Tests that the device broadcasts a <REPORT_POWER_STATUS> with params 0x0 when the device is
     * powered on.
     */
    @Test
    public void cect_11_2_14_1_PowerStatusWhenOn() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        try {
            hdmiCecUtils.init();
            /* Make sure the device is not booting up/in standby */
            device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
            hdmiCecUtils.sendCecMessage(CecDevice.TV, CecMessage.GIVE_POWER_STATUS);
            String message = hdmiCecUtils.checkExpectedOutput(CecDevice.TV,
                                                              CecMessage.REPORT_POWER_STATUS);
            assertEquals(ON, hdmiCecUtils.getParamsFromMessage(message));
        } finally {
            hdmiCecUtils.killCecProcess();
        }
    }

    /**
     * Test 11.2.14-2
     * Tests that the device broadcasts a <REPORT_POWER_STATUS> with params 0x1 when the device is
     * powered on.
     */
    @Test
    public void cect_11_2_14_2_PowerStatusWhenOff() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        HdmiCecUtils hdmiCecUtils = new HdmiCecUtils(CecDevice.PLAYBACK_1, "1.0.0.0");

        try {
            hdmiCecUtils.init();
            /* Make sure the device is not booting up/in standby */
            device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
            device.executeShellCommand("input keyevent KEYCODE_POWER");
            hdmiCecUtils.sendCecMessage(CecDevice.TV, CecMessage.GIVE_POWER_STATUS);
            String message = hdmiCecUtils.checkExpectedOutput(CecDevice.TV,
                                                              CecMessage.REPORT_POWER_STATUS);
            assertEquals(OFF, hdmiCecUtils.getParamsFromMessage(message));
        } finally {
            /* Wake up the device again */
            device.executeShellCommand("input keyevent KEYCODE_POWER");
            hdmiCecUtils.killCecProcess();
        }
    }
}
