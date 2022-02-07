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

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSap;
import android.content.pm.PackageManager;
import android.sysprop.BluetoothProperties;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothSapTest extends AndroidTestCase {
    private static final String TAG = BluetoothLeAudioTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothSap mBluetoothSap;
    private boolean mIsProfileReady;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;

    private boolean mIsSapSupported;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        if (!mHasBluetooth) return;

        mIsSapSupported = BluetoothProperties.isProfileSapServerEnabled().orElse(false);
        if (!mIsSapSupported) return;

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT);

        mAdapter = getContext().getSystemService(BluetoothManager.class).getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectedlock = new ReentrantLock();
        mConditionProfileIsConnected  = mProfileConnectedlock.newCondition();
        mIsProfileReady = false;
        mBluetoothSap = null;

        mAdapter.getProfileProxy(getContext(), new BluetoothSapServiceListener(),
                BluetoothProfile.SAP);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth && mIsSapSupported) {
            if (mAdapter != null && mBluetoothSap != null) {
                mBluetoothSap.close();
                mBluetoothSap = null;
                mIsProfileReady = false;
            }
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
            mAdapter = null;
        }
    }

    public void test_setgetConnectionPolicy() {
        if (!mHasBluetooth || !mIsSapSupported) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothSap);

        assertThrows(NullPointerException.class, () -> mBluetoothSap.setConnectionPolicy(null, 0));
        assertThrows(NullPointerException.class, () -> mBluetoothSap.getConnectionPolicy(null));
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

    private final class BluetoothSapServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectedlock.lock();
            mBluetoothSap = (BluetoothSap) proxy;
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
