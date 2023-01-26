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

import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

public class BluetoothGattMultiDevicesSnippet implements Snippet {

    private static final String TAG = "BluetoothGattMultiDevicesSnippet";

    private BluetoothGattMultiDevicesServer mGattServer;
    private BluetoothGattMultiDevicesClient mGattClient;

    private Context mContext;
    private BluetoothManager mBluetoothManager;

    public BluetoothGattMultiDevicesSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        mGattServer = new BluetoothGattMultiDevicesServer(mContext, mBluetoothManager);
        mGattClient = new BluetoothGattMultiDevicesClient(mContext, mBluetoothManager);
    }

    @Rpc(description = "Creates Bluetooth GATT server and returns device address.")
    public String createServer() {
        return mGattServer.createServer();
    }

    @Rpc(description = "Waits for a connection to be made btween client and server")
    public boolean waitConnection() {
        return mGattServer.waitConnection();
    }

    @Rpc(description = "Destroys Bluetooth GATT server.")
    public void destroyServer() {
        mGattServer.destroyServer();
    }

    @Rpc(description = "Destroys Bluetooth GATT server.")
    public int receivePriority() {
        return mGattServer.receivePriority();
    }

    @Rpc(description = "Try to discover Bluetooth GATT server")
    public boolean discoverServer(String name) {
        return mGattClient.discoverServer(name);
    }

    @Rpc(description = "Creates Bluetooth GATT server and returns device address")
    public void connectGatt(int priority) {
        mGattClient.connect(priority);
    }

    @Rpc(description = "Creates Bluetooth GATT server and returns device address")
    public void disconnectGatt() {
        mGattClient.disconnect();
    }

    @Rpc(description = "Creates Bluetooth GATT server and returns device address")
    public void updatePriority(int priority) {
        mGattClient.updatePriority(priority);
    }

    @Rpc(description = "Checks Bluetooth state")
    public boolean isBluetoothOn() {
        return mBluetoothManager.getAdapter().isEnabled();
    }

    @Rpc(description = "Clears the receivers and permissions")
    public void clearClient() {
        mGattClient.clear();
    }
}
