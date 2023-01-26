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

package com.google.snippet.bluetooth;

import static android.bluetooth.BluetoothDevice.PHY_LE_1M_MASK;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;

public final class BluetoothGattMultiDevicesClient {

    private static final String TAG = "BluetoothGattMultiDevicesClient";

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private CountDownLatch mServerFoundBlocker = null;

    private UiAutomation mUiAutomation;

    private static final int CALLBACK_TIMEOUT_SEC = 60;

    private String mServerName;
    private BluetoothDevice mServer;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, "Device discovered: " + device.getName()
                        + " Server name: " + mServerName);
                if (mServerName.equals(device.getName())) {
                    Log.i(TAG, "Server discovered");
                    mServer = device;
                    mServerFoundBlocker.countDown();
                }
            }
        }
    };

    public BluetoothGattMultiDevicesClient(Context context, BluetoothManager manager) {
        mContext = context;
        mBluetoothAdapter = manager.getAdapter();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mReceiver, filter);

    }

    public void clear() {
        mContext.unregisterReceiver(mReceiver);
        mUiAutomation.dropShellPermissionIdentity();
    }

    private boolean startDiscovery() throws InterruptedException {
        mServerFoundBlocker = new CountDownLatch(1);
        mBluetoothAdapter.startDiscovery();
        boolean timeout = !mServerFoundBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS);
        mBluetoothAdapter.cancelDiscovery();
        if (timeout) {
            Log.e(TAG, "Did not discover server");
            return false;
        }
        return true;
    }

    public boolean discoverServer(String deviceName) {
        try {
            mServerName = deviceName;
            return startDiscovery();
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
        return false;
    }

    public void connect(int priority) {
        mBluetoothGatt = mServer.connectGatt(mContext, false,
                TRANSPORT_LE, PHY_LE_1M_MASK, priority, null, new BluetoothGattCallback(){});
    }

    public void disconnect() {
        mBluetoothGatt.disconnect();
    }

    public void updatePriority(int priority) {
        mBluetoothGatt.requestConnectionPriority(priority);
    }
}
