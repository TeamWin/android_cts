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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
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
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT);
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
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void test_addSource() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // TODO When implemented
        assertThrows(UnsupportedOperationException.class, () -> mBluetoothLeBroadcastAssistant
                .addSource(null, null, true));

        mBluetoothLeBroadcastAssistant.removeSource(null, 0);
    }

    @Test
    public void test_getAllSources() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // TODO When implemented

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        // Verify returns empty list if bluetooth is not enabled
        assertTrue(mBluetoothLeBroadcastAssistant.getAllSources(null).isEmpty());
    }

    @Test
    public void test_setConnectionPolicy() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // TODO When implemented
        assertFalse(mBluetoothLeBroadcastAssistant.setConnectionPolicy(null,
                    BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
        assertEquals(mBluetoothLeBroadcastAssistant.getConnectionPolicy(null),
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void test_getMaximumSourceCapacity() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // TODO When implemented
        assertEquals(mBluetoothLeBroadcastAssistant.getMaximumSourceCapacity(null), 0);
    }

    @Test
    public void test_isSearchInProgress() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // TODO When implemented
        assertFalse(mBluetoothLeBroadcastAssistant.isSearchInProgress());
    }

    @Test
    public void test_modifySource() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // TODO When implemented
        assertThrows(UnsupportedOperationException.class, () -> mBluetoothLeBroadcastAssistant
                .modifySource(null, 0, null));
    }

    @Test
    public void test_registerCallback() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        Executor executor = mContext.getMainExecutor();
        BluetoothLeBroadcastAssistant.Callback callback =
                new BluetoothLeBroadcastAssistant.Callback() {
                    @Override
                    public void onSearchStarted(int reason) {}
                    @Override
                    public void onSearchStartFailed(int reason) {}
                    @Override
                    public void onSearchStopped(int reason) {}
                    @Override
                    public void onSearchStopFailed(int reason) {}
                    @Override
                    public void onSourceFound(BluetoothLeBroadcastMetadata source) {}
                    @Override
                    public void onSourceAdded(BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceAddFailed(BluetoothDevice sink,
                            BluetoothLeBroadcastMetadata source, int reason) {}
                    @Override
                    public void onSourceModified(BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceModifyFailed(
                            BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceRemoved(BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceRemoveFailed(
                            BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onReceiveStateChanged(BluetoothDevice sink, int sourceId,
                            BluetoothLeBroadcastReceiveState state) {}
                };
        // empty calls to callback override
        callback.onSearchStarted(0);
        callback.onSearchStartFailed(0);
        callback.onSearchStopped(0);
        callback.onSearchStopFailed(0);
        callback.onSourceFound(null);
        callback.onSourceAdded(null, 0, 0);
        callback.onSourceAddFailed(null, null, 0);
        callback.onSourceModified(null, 0, 0);
        callback.onSourceModifyFailed(null, 0, 0);
        callback.onSourceRemoved(null, 0, 0);
        callback.onSourceRemoveFailed(null, 0, 0);
        callback.onReceiveStateChanged(null, 0, null);

        // Verify parameter
        assertThrows(IllegalArgumentException.class, () -> mBluetoothLeBroadcastAssistant
                .registerCallback(null, callback));
        assertThrows(IllegalArgumentException.class, () -> mBluetoothLeBroadcastAssistant
                .registerCallback(executor, null));
        assertThrows(IllegalArgumentException.class, () -> mBluetoothLeBroadcastAssistant
                .unregisterCallback(null));


        // TODO When implemented
        assertThrows(UnsupportedOperationException.class, () -> mBluetoothLeBroadcastAssistant
                .registerCallback(executor, callback));
        assertThrows(UnsupportedOperationException.class, () -> mBluetoothLeBroadcastAssistant
                .unregisterCallback(callback));
    }

    @Test
    public void test_startSearchingForSources() {
        if (!(mHasBluetooth && mIsLeBroadcastAssistantSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // Verify parameter
        assertThrows(IllegalArgumentException.class, () -> mBluetoothLeBroadcastAssistant
                .startSearchingForSources(null));

        // TODO When implemented
        assertThrows(UnsupportedOperationException.class, () -> mBluetoothLeBroadcastAssistant
                .startSearchingForSources(new ArrayList<>()));
        assertThrows(UnsupportedOperationException.class, () -> mBluetoothLeBroadcastAssistant
                .stopSearchingForSources());
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
                == BluetoothStatusCodes.FEATURE_NOT_SUPPORTED) {
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
