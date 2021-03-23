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

package android.bluetooth.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

public class BluetoothDeviceTest extends AndroidTestCase {

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        if (mHasBluetooth) {
            BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
            mAdapter = manager.getAdapter();
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            mAdapter = null;
        }
    }

    public void test_setAlias_getAlias() {
        if (!mHasBluetooth) {
            // Skip the test if bluetooth is not present.
            return;
        }

        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();
        String deviceAddress = "00:11:22:AA:BB:CC";

        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        // Verifies that when there is no alias, we return the device name
        assertNull(device.getAlias());

        String testDeviceAlias = "Test Device Alias";

        // This should throw a SecurityException because there is no CDM association
        try {
            device.setAlias(testDeviceAlias);
            fail("BluetoothDevice alias was able to be set without a CDM association without having"
                    + "BLUETOOTH_PRIVILEGED permission");
        } catch (SecurityException ex) {
            assertNull(device.getAlias());
        }

        runShellCommand(String.format(
                "cmd companiondevice associate %d %s %s", userId, packageName, deviceAddress));
        String output = runShellCommand("dumpsys companiondevice");
        assertTrue("Package name missing from output", output.contains(packageName));
        assertTrue("Device address missing from output", output.contains(deviceAddress));
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Device properties don't exist for non-existent BluetoothDevice, so calling setAlias with
         * permissions should return false
         */
        assertFalse(device.setAlias(testDeviceAlias));
        runShellCommand(String.format(
                "cmd companiondevice disassociate %d %s %s", userId, packageName, deviceAddress));
    }
}
