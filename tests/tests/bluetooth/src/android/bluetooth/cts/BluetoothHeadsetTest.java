/*
 * Copyright 2022 The Android Open Source Project
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

import android.app.UiAutomation;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothHeadsetTest extends AndroidTestCase {
    private static final String TAG = BluetoothHeadsetTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect
    private static final String PROFILE_SUPPORTED_HEADSET = "profile_supported_hs_hfp";

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    private BluetoothHeadset mBluetoothHeadset;
    private boolean mIsHeadsetSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        if (!mHasBluetooth) return;
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectedlock = new ReentrantLock();
        mConditionProfileIsConnected = mProfileConnectedlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHeadset = null;

        Resources bluetoothResources = mContext.getPackageManager().getResourcesForApplication(
                "com.android.bluetooth");
        int headsetSupportId = bluetoothResources.getIdentifier(
                PROFILE_SUPPORTED_HEADSET, "bool", "com.android.bluetooth");
        assertTrue("resource profile_supported_hs_hfp not found", headsetSupportId != 0);
        mIsHeadsetSupported = bluetoothResources.getBoolean(headsetSupportId);
        if (!mIsHeadsetSupported) return;

        mAdapter.getProfileProxy(getContext(), new BluetoothHeadsetServiceListener(),
                BluetoothProfile.HEADSET);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            if (mAdapter != null && mBluetoothHeadset != null) {
                mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
                mBluetoothHeadset = null;
                mIsProfileReady = false;
            }
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            mAdapter = null;
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsHeadsetSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertEquals(mBluetoothHeadset.getConnectedDevices(),
                new ArrayList<BluetoothDevice>());
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsHeadsetSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertEquals(mBluetoothHeadset.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>());
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsHeadsetSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothHeadset.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    public void test_isAudioConnected() {
        if (!(mHasBluetooth && mIsHeadsetSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHeadset.isAudioConnected(testDevice));
        assertFalse(mBluetoothHeadset.isAudioConnected(null));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.isAudioConnected(testDevice));
    }

    public void test_isNoiseReductionSupported() {
        if (!(mHasBluetooth && mIsHeadsetSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHeadset.isNoiseReductionSupported(testDevice));
        assertFalse(mBluetoothHeadset.isNoiseReductionSupported(null));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.isNoiseReductionSupported(testDevice));
    }

    public void test_isVoiceRecognitionSupported() {
        if (!(mHasBluetooth && mIsHeadsetSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHeadset.isVoiceRecognitionSupported(testDevice));
        assertFalse(mBluetoothHeadset.isVoiceRecognitionSupported(null));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.isVoiceRecognitionSupported(testDevice));
    }

    public void test_sendVendorSpecificResultCode() {
        if (!(mHasBluetooth && mIsHeadsetSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        try {
            mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, null, null);
            fail("sendVendorSpecificResultCode did not throw an IllegalArgumentException when the "
                    + "command was null");
        } catch (IllegalArgumentException ignored) {
        }

        assertFalse(mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, "", ""));
        assertFalse(mBluetoothHeadset.sendVendorSpecificResultCode(null, "", ""));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, "", ""));
    }

    private boolean waitForProfileConnect() {
        mProfileConnectedlock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (!mIsProfileReady) {
                if (!mConditionProfileIsConnected.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Connect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrrupted");
        } finally {
            mProfileConnectedlock.unlock();
        }
        return mIsProfileReady;
    }

    private final class BluetoothHeadsetServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectedlock.lock();
            mBluetoothHeadset = (BluetoothHeadset) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileIsConnected.signal();
            } finally {
                mProfileConnectedlock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
        }
    }
}
