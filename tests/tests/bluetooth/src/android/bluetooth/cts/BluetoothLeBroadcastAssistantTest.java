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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
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
public class BluetoothLeBroadcastAssistantTest {
    private static final String TAG = BluetoothLeBroadcastAssistantTest.class.getSimpleName();


    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect
    private static final String PROFILE_SUPPORTED_LE_BROADCAST_ASSISTANT = "profile_supported_bass";
    private static final int LE_AUDIO_PROFILE_CONSTANT = 22;

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothLeBroadcastAssistant mBluetoothLeBroadcastAssistant;
    private boolean mIsLeBroadcastAssistantSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }
        mHasBluetooth = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        if (!mHasBluetooth) {
            return;
        }
        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectedlock = new ReentrantLock();
        mConditionProfileIsConnected = mProfileConnectedlock.newCondition();
        mIsProfileReady = false;
        mBluetoothLeBroadcastAssistant = null;

        Resources bluetoothResources = null;
        try {
            bluetoothResources = mContext.getPackageManager().getResourcesForApplication(
                    "com.android.bluetooth");
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        int leBroadcastAssistantSupportId = bluetoothResources.getIdentifier(
                PROFILE_SUPPORTED_LE_BROADCAST_ASSISTANT, "bool", "com.android.bluetooth");
        if (leBroadcastAssistantSupportId == 0) {
            return;
        }
        mIsLeBroadcastAssistantSupported = bluetoothResources.getBoolean(
                leBroadcastAssistantSupportId);
        if (!mIsLeBroadcastAssistantSupported) {
            return;
        }

        mAdapter.getProfileProxy(mContext, new ServiceListener(),
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
    }

    @After
    public void tearDown() {
        if (mHasBluetooth) {
            if (mAdapter != null && mBluetoothLeBroadcastAssistant != null) {
                mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT,
                        mBluetoothLeBroadcastAssistant);
                mBluetoothLeBroadcastAssistant = null;
                mIsProfileReady = false;
            }
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            mAdapter = null;
        }
    }

    @Test
    public void testGetConnectedDevices() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothLeBroadcastAssistant.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void testGetDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothLeBroadcastAssistant.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void testGetConnectionState() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothLeBroadcastAssistant.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothLeBroadcastAssistant.getConnectionState(testDevice));
    }

    @Test
    public void testProfileSupportLogic() {
        if (!mHasBluetooth) {
            return;
        }
        if (mAdapter.isLeAudioBroadcastAssistantSupported()
                == BluetoothStatusCodes.ERROR_FEATURE_NOT_SUPPORTED) {
            assertFalse(mIsLeBroadcastAssistantSupported);
            return;
        }
        assertTrue(mIsLeBroadcastAssistantSupported);
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

    private final class ServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectedlock.lock();
            mBluetoothLeBroadcastAssistant = (BluetoothLeBroadcastAssistant) proxy;
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
