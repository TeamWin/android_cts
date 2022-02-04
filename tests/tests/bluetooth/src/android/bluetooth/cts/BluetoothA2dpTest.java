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

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothA2dpTest extends AndroidTestCase {
    private static final String TAG = BluetoothA2dpTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect
    private static final String PROFILE_SUPPORTED_A2DP = "profile_supported_a2dp";

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothA2dp mBluetoothA2dp;
    private boolean mIsA2dpSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        if (!mHasBluetooth) return;
        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectedlock = new ReentrantLock();
        mConditionProfileIsConnected = mProfileConnectedlock.newCondition();
        mIsProfileReady = false;
        mBluetoothA2dp = null;

        Resources bluetoothResources = mContext.getPackageManager().getResourcesForApplication(
                "com.android.bluetooth");
        int a2dpSupportId = bluetoothResources.getIdentifier(
                PROFILE_SUPPORTED_A2DP, "bool", "com.android.bluetooth");
        assertTrue("resource profile_supported_a2dp not found", a2dpSupportId != 0);
        mIsA2dpSupported = bluetoothResources.getBoolean(a2dpSupportId);
        if (!mIsA2dpSupported) return;

        mAdapter.getProfileProxy(getContext(), new BluetoothA2dpServiceListener(),
                BluetoothProfile.A2DP);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            if (mAdapter != null && mBluetoothA2dp != null) {
                mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
                mBluetoothA2dp = null;
                mIsProfileReady = false;
            }
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            mAdapter = null;
        }
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertEquals(mBluetoothA2dp.getConnectedDevices(),
                new ArrayList<BluetoothDevice>());
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertEquals(mBluetoothA2dp.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>());
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothA2dp.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    public void test_isA2dpPlaying() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothA2dp.isA2dpPlaying(testDevice));
    }

    public void test_getCodecStatus() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertNull(mBluetoothA2dp.getCodecStatus(testDevice));
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.getCodecStatus(null);
        });
    }

    public void test_setCodecConfigPreference() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        BluetoothCodecConfig codecConfig = new BluetoothCodecConfig.Builder()
                .setCodecType(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC)
                .setCodecPriority(0)
                .build();
        mBluetoothA2dp.setCodecConfigPreference(testDevice, codecConfig);
        assertNull(mBluetoothA2dp.getCodecStatus(testDevice));
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.setCodecConfigPreference(null, null);
        });
    }

    private static <T extends Exception> void assertThrows(Class<T> clazz, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            if (!clazz.isAssignableFrom(e.getClass())) {
                throw e;
            }
        }
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

    private final class BluetoothA2dpServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectedlock.lock();
            mBluetoothA2dp = (BluetoothA2dp) proxy;
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
