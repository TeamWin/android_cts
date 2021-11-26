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

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothStatusCodes;
import android.content.AttributionSource;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;

public class BluetoothDeviceTest extends AndroidTestCase {

    private boolean mHasBluetooth;
    private boolean mHasCompanionDevice;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        mHasCompanionDevice = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_COMPANION_DEVICE_SETUP);

        if (mHasBluetooth && mHasCompanionDevice) {
            BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
            mAdapter = manager.getAdapter();
            mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth && mHasCompanionDevice) {
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            mAdapter = null;
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    public void test_setAlias_getAlias() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();
        String deviceAddress = "00:11:22:AA:BB:CC";

        AttributionSource source = AttributionSource.myAttributionSource();
        assertEquals("android.bluetooth.cts", source.getPackageName());

        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        // Verifies that when there is no alias, we return the device name
        assertNull(device.getAlias());

        assertThrows(IllegalArgumentException.class, () -> device.setAlias(""));

        String testDeviceAlias = "Test Device Alias";

        // This should throw a SecurityException because there is no CDM association
        assertThrows("BluetoothDevice.setAlias without"
                + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                SecurityException.class, () -> device.setAlias(testDeviceAlias));

        runShellCommand(String.format(
                "cmd companiondevice associate %d %s %s", userId, packageName, deviceAddress));
        String output = runShellCommand("dumpsys companiondevice");
        assertTrue("Package name missing from output", output.contains(packageName));
        assertTrue("Device address missing from output",
                output.toLowerCase().contains(deviceAddress.toLowerCase()));

        // Takes time to update the CDM cache, so sleep to ensure the association is cached
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Device properties don't exist for non-existent BluetoothDevice, so calling setAlias with
         * permissions should return false
         */
        assertEquals(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED, device.setAlias(testDeviceAlias));
        runShellCommand(String.format(
                "cmd companiondevice disassociate %d %s %s", userId, packageName, deviceAddress));
    }

    public void test_getAnonymizedAddress() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        assertEquals(device.getAnonymizedAddress(), "XX:XX:XX:AA:BB:CC");
    }

    public void test_getBatteryLevel() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        assertEquals(BluetoothDevice.BATTERY_LEVEL_UNKNOWN, device.getBatteryLevel());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> device.getBatteryLevel());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    public void test_getMessageAccessPermission() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        assertEquals(BluetoothDevice.ACCESS_UNKNOWN, device.getMessageAccessPermission());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> device.getMessageAccessPermission());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    public void test_getPhonebookAccessPermission() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        assertEquals(BluetoothDevice.ACCESS_UNKNOWN, device.getPhonebookAccessPermission());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> device.getPhonebookAccessPermission());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    public void test_isBondingInitiatedLocally() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        assertFalse(device.isBondingInitiatedLocally());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> device.isBondingInitiatedLocally());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    public void test_prepareToEnterProcess() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);
        device.prepareToEnterProcess(null);
    }

    public void test_setPin() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        assertFalse(device.setPin((String) null));
        assertFalse(device.setPin("12345678901234567")); // check PIN too big

        assertFalse(device.setPin("123456")); //device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> device.setPin("123456"));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    public void test_connect_disconnect() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> device.connect());
        assertThrows(SecurityException.class, () -> device.disconnect());
    }
}
