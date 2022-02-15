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

package com.android.cts.verifier.bluetooth;

import static android.bluetooth.BluetoothDevice.ACTION_UUID;
import static android.bluetooth.BluetoothDevice.EXTRA_UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Activity which connects to an RFCOMM server which is advertising a sample UUID which is known for
 * car projection. This test should be run in conjunction with another device which is bonded to
 * this one and is running the {@link BackgroundRfcommTestActivity}.
 */
public class BackgroundRfcommTestClientActivity extends PassFailButtons.Activity {
    private static final String TAG = "BT.CarProjectionClient";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mBluetoothSocket;
    private BroadcastReceiver mSdpReceiver;
    private String mTestMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bt_background_rfcomm_client);
        getPassButton().setEnabled(false);

        mBluetoothAdapter = getSystemService(BluetoothManager.class).getAdapter();
        mTestMessage = getString(R.string.bt_background_rfcomm_test_message);

        UUID uuid = UUID.fromString(getString(R.string.bt_background_rfcomm_test_uuid));
        List<BluetoothDevice> connectedDevices =
                mBluetoothAdapter.getMostRecentlyConnectedDevices();

        if (connectedDevices.isEmpty()) {
            Log.e(TAG, "No bluetooth devices connected");
            setTestResultAndFinish(false);
            return;
        }

        BluetoothDevice connectedDevice = connectedDevices.get(0);

        mSdpReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(EXTRA_UUID)) {
                    Parcelable[] uuids = intent.getParcelableArrayExtra(EXTRA_UUID);

                    for (Parcelable parcelUuid : uuids) {
                        if (((ParcelUuid) parcelUuid).getUuid().equals(uuid)) {
                            try {
                                updateInstructions(
                                        R.string.bt_background_rfcomm_test_connecting_to_server);
                                mBluetoothSocket =
                                        connectedDevice.createRfcommSocketToServiceRecord(uuid);
                            } catch (IOException e) {
                                Log.e(TAG, "Failed to create RFCOMM socket connection", e);
                            }

                            readAndWriteRfcommData();
                            return;
                        }
                    }
                }

                Log.e(TAG, "Expected UUID not found for device.");
                setTestResultAndFinish(false);
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UUID);

        registerReceiver(mSdpReceiver, intentFilter);

        updateInstructions(R.string.bt_background_rfcomm_test_doing_sdp);
        connectedDevice.fetchUuidsWithSdp();
    }

    @Override
    protected void onDestroy() {
        if (mSdpReceiver != null) {
            unregisterReceiver(mSdpReceiver);
        }

        if (mBluetoothSocket != null && mBluetoothSocket.isConnected()) {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close RFCOMM socket connection", e);
            }
        }
        super.onDestroy();
    }

    private void readAndWriteRfcommData() {
        if (mBluetoothSocket == null) {
            setTestResultAndFinish(false);
            return;
        }

        int offset = 0;
        int length = mTestMessage.length();
        ByteBuffer buf = ByteBuffer.allocate(length);

        try {
            updateInstructions(R.string.bt_background_rfcomm_test_waiting_for_message);
            mBluetoothSocket.connect();
            while (length > 0) {
                int numRead = mBluetoothSocket.getInputStream().read(buf.array(), offset, length);
                if (numRead == -1) {
                    break;
                }

                offset += numRead;
                length -= numRead;
            }
        } catch (IOException e) {
            Log.e(TAG, "RFCOMM read failed", e);
            setTestResultAndFinish(false);
            return;
        }

        String receivedMessage = new String(buf.array());

        boolean success = receivedMessage.equals(mTestMessage);

        if (success) {
            try {
                updateInstructions(R.string.bt_background_rfcomm_test_sending_message);
                mBluetoothSocket.getOutputStream().write(
                        mTestMessage.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                success = false;
                Log.e(TAG, "RFCOMM write failed", e);
            }
        } else {
            Log.e(TAG, "Incorrect RFCOMM message received from server");
        }

        setTestResultAndFinish(success);
    }

    private void updateInstructions(int id) {
        TextView textView = findViewById(R.id.bt_background_rfcomm_client_text);
        textView.setText(id);
    }
}
