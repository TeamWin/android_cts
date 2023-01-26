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

import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class BluetoothGattMultiDevicesServer {

    private static final String TAG = "BluetoothGattMultiDevicesServer";

    private Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    private int mConnectionPriority = -1;
    private CountDownLatch mPriorityBlocker = null;
    private CountDownLatch mConnectionBlocker = null;

    private static final int CALLBACK_TIMEOUT_SEC = 60;

    private BluetoothGattServerCallback mServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.i(TAG, "onConnectionStateChange: newState=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED && mConnectionBlocker != null) {
                mConnectionBlocker.countDown();
            }
        }

        @Override
        public void onPriorityChanged(BluetoothDevice device, int priority) {
            Log.i(TAG, "onPriorityChanged: priority=" + priority);
            if (priority == 0) {
                return;
            }
            mConnectionPriority = priority;
            if (mPriorityBlocker != null) {
                mPriorityBlocker.countDown();
            }
        }

    };

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        }

        @Override
        public void onStartFailure(int errorCode) {
        }
    };

    public BluetoothGattMultiDevicesServer(Context context, BluetoothManager manager) {
        mContext = context;
        mBluetoothManager = manager;
        mBluetoothAdapter = manager.getAdapter();
    }

    public String createServer() {
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(UUID.fromString(
                        "0000fffa-0000-1000-8000-00805f9b34fb")))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);

        mBluetoothGattServer = mBluetoothManager.openGattServer(mContext, mServerCallback);

        BluetoothGattService service = new BluetoothGattService(UUID.fromString(
                "0000fffa-0000-1000-8000-00805f9b34fb"), SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(new BluetoothGattCharacteristic(UUID.fromString(
                "0000fffb-0000-1000-8000-00805f9b34fb"), 0x02, 0x01));
        mBluetoothGattServer.addService(service);
        return mBluetoothAdapter.getName();
    }

    private boolean waitConnectCallback() throws InterruptedException {
        mConnectionBlocker = new CountDownLatch(1);
        return mConnectionBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS);
    }

    public boolean waitConnection() {
        if (mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER).size() > 0) {
            return true;
        }
        try {
            return waitConnectCallback();
        } catch (InterruptedException e) {
            Log.e(TAG, "", e);
        }
        return false;
    }

    public void destroyServer() {
        mBluetoothGattServer.close();
    }

    private int waitForPriority() throws InterruptedException {
        mConnectionPriority = -1;
        mPriorityBlocker = new CountDownLatch(1);
        boolean timeout = !mPriorityBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS);
        if (timeout) {
            Log.e(TAG, "Did not receive priority update");
        }
        return mConnectionPriority;
    }

    public int receivePriority() {
        try {
            return waitForPriority();
        } catch (InterruptedException e) {
            Log.e(TAG, "", e);
        }
        return -1;
    }
}
