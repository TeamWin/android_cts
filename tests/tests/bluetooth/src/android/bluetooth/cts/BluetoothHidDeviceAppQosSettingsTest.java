/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.bluetooth.cts;

import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.test.AndroidTestCase;

public class BluetoothHidDeviceAppQosSettingsTest extends AndroidTestCase {
    private BluetoothHidDeviceAppQosSettings mBluetoothHidDeviceAppQosSettings;

    @Override
    public void setUp() throws Exception {
        mBluetoothHidDeviceAppQosSettings = new BluetoothHidDeviceAppQosSettings(BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, 0, 0, 0, 0, 0);
    }

    @Override
    public void tearDown() throws Exception {
        mBluetoothHidDeviceAppQosSettings = null;
    }

    public void test_BluetoothHidDeviceAppQosSettings() {
        mBluetoothHidDeviceAppQosSettings = new BluetoothHidDeviceAppQosSettings(BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, 0, 0, 0, 0, 0);
    }

    public void test_getServiceType() {
        assertEquals(mBluetoothHidDeviceAppQosSettings.getServiceType(), BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT);
    }

    public void test_getLatency() {
        assertEquals(mBluetoothHidDeviceAppQosSettings.getLatency(), 0);
    }

    public void test_getPeakBandwidth() {
        assertEquals(mBluetoothHidDeviceAppQosSettings.getPeakBandwidth(), 0);
    }

    public void test_getTokenBucketSize() {
        assertEquals(mBluetoothHidDeviceAppQosSettings.getTokenBucketSize(), 0);
    }

    public void test_getTokenRate() {
        assertEquals(mBluetoothHidDeviceAppQosSettings.getTokenRate(), 0);
    }

}
