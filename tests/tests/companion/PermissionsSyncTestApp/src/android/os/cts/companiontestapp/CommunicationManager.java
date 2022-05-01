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

package android.os.cts.companiontestapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.net.MacAddress;
import android.os.Handler;
import android.widget.Toast;

/**
 * A class that handles the communication with the Bluetooth stack
 */
public class CommunicationManager {

    private static CommunicationManager sCommunicationManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothCommunicationService mBluetoothCommunicationService;
    private Context mContext;

    public static CommunicationManager createInstance(Context context, Handler handler) {
        sCommunicationManager = new CommunicationManager(context, handler);
        return sCommunicationManager;
    }

    // TODO consider making it a service to avoid the singleton pattern
    public static CommunicationManager getInstance() {
        return sCommunicationManager;
    }

    private CommunicationManager(Context context, Handler handler) {
        mContext = context;

        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mBluetoothCommunicationService = new BluetoothCommunicationService(mBluetoothAdapter, handler);
    }

    public void onStart() {
        if (mBluetoothCommunicationService.getState() == BluetoothCommunicationService.STATE_NONE) {
            mBluetoothCommunicationService.start();
        }
    }

    public void onStop() {
        mBluetoothCommunicationService.stop();
    }

    public void sendData(byte[] message) {
        mBluetoothCommunicationService.write(message);
    }

    public void onRemoteDeviceAssociated(MacAddress macAddress) {
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress.toString().toUpperCase());
        if (mBluetoothCommunicationService.getState() != BluetoothCommunicationService.STATE_CONNECTED) {
            Toast.makeText(mContext, "mBluetoothDevice: " + mBluetoothDevice.getAddress(), Toast.LENGTH_SHORT).show();
            mBluetoothCommunicationService.connect(mBluetoothDevice);
        } else {
            Toast.makeText(mContext, "mBluetoothDevice: " + mBluetoothDevice.getAddress() + " already connected", Toast.LENGTH_SHORT).show();
        }
    }
}
