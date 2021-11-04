/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.sample.cts;

import static org.junit.Assert.assertNotNull;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that interacts with multiple devices.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class SampleMultiDeviceTest extends BaseHostJUnit4Test {

    /**
     * Sample tests that showcase that we are receiving the multiple devices in the test side.
     */
    @Test
    public void testMultiDeviceTest() throws Exception {
        // Validate we are actually multi-devices
        Truth.assertThat(getListDevices().size()).isGreaterThan(1);
        for (ITestDevice device : getListDevices()) {
            CLog.i("device '%s' is available.", device.getSerialNumber());
            String buildId = device.getProperty("ro.vendor.build.id");
            assertNotNull(buildId);
        }
    }

}

