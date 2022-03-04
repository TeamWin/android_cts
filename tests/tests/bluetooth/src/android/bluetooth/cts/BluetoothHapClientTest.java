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

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothHapClientTest {
    private static final String TAG = BluetoothHapClientTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect
    private static final String PROFILE_SUPPORTED_HAP_CLIENT = "profile_supported_hap_client";

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothHapClient mBluetoothHapClient;
    private boolean mIsHapClientSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }
        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) {
            return;
        }
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));


        mProfileConnectedlock = new ReentrantLock();
        mConditionProfileIsConnected  = mProfileConnectedlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHapClient = null;

        mIsHapClientSupported = TestUtils.getProfileConfigValueOrDie(BluetoothProfile.HAP_CLIENT);
        if (!mIsHapClientSupported) {
            return;
        }

        mAdapter.getProfileProxy(mContext, new BluetoothHapClientServiceListener(),
                BluetoothProfile.HAP_CLIENT);
    }

    @After
    public void tearDown() throws Exception {
        if (mHasBluetooth) {
            if (mAdapter != null && mBluetoothHapClient != null) {
                mBluetoothHapClient.close();
                mBluetoothHapClient = null;
                mIsProfileReady = false;
            }
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            mAdapter = null;
            TestUtils.dropPermissionAsShellUid();
        }
    }

    @Test
    public void testGetConnectedDevices() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothHapClient.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void testGetDevicesMatchingConnectionStates() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothHapClient.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void testGetConnectionState() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns STATE_DISCONNECTED when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHapClient.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns STATE_DISCONNECTED if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHapClient.getConnectionState(testDevice));
    }

    @Test
    public void testGetActivePresetIndex() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns null if bluetooth is not enabled
        assertNull(mBluetoothHapClient.getActivePresetInfo(testDevice));
    }

    @Test
    public void testSelectPreset() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.selectPreset(testDevice, 1);
    }

    @Test
    public void testSelectPresetForGroup() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.selectPresetForGroup(1, 1);
    }

    @Test
    public void testGetAllPresetInfo() {
        if (shouldSkipTest()) {
            return;
        }

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothHapPresetInfo> presets = mBluetoothHapClient.getAllPresetInfo(testDevice);
        assertTrue(presets.isEmpty());
    }

    @Test
    public void testSetPresetName() {
        if (shouldSkipTest()) {
            return;
        }

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.setPresetName(testDevice, 1 , "New Name");
    }

    @Test
    public void testSetPresetNameForGroup() {
        if (shouldSkipTest()) {
            return;
        }

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.setPresetNameForGroup(1, 1 , "New Name");
    }

    private boolean shouldSkipTest() {
        return !(mHasBluetooth && mIsHapClientSupported);
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
            Log.e(TAG, "waitForProfileConnect: interrupted");
        } finally {
            mProfileConnectedlock.unlock();
        }
        return mIsProfileReady;
    }

    private final class BluetoothHapClientServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectedlock.lock();
            mBluetoothHapClient = (BluetoothHapClient) proxy;
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
