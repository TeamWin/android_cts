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
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothA2dpTest extends AndroidTestCase {
    private static final String TAG = BluetoothA2dpTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    private BluetoothA2dp mBluetoothA2dp;
    private boolean mIsA2dpSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) return;

        mIsA2dpSupported = TestUtils.isProfileEnabled(BluetoothProfile.A2DP_SINK);
        if (!mIsA2dpSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectedlock = new ReentrantLock();
        mConditionProfileIsConnected = mProfileConnectedlock.newCondition();
        mIsProfileReady = false;
        mBluetoothA2dp = null;

        mAdapter.getProfileProxy(getContext(), new BluetoothA2dpServiceListener(),
                BluetoothProfile.A2DP);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (!(mHasBluetooth && mIsA2dpSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothA2dp != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
            mBluetoothA2dp = null;
            mIsProfileReady = false;
        }
        if (mAdapter != null) {
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        }
        mAdapter = null;
        mUiAutomation.dropShellPermissionIdentity();
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

        assertThrows(SecurityException.class, () -> mBluetoothA2dp.getCodecStatus(testDevice));
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.getCodecStatus(null);
        });
    }

    public void test_setCodecConfigPreference() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.setCodecConfigPreference(null, null);
        });
    }

    public void test_setOptionalCodecsEnabled() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertThrows(IllegalArgumentException.class,
                () -> mBluetoothA2dp.setOptionalCodecsEnabled(null, 0));
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN));
        assertThrows(SecurityException.class, () -> mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED));
        assertThrows(SecurityException.class, () -> mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
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
