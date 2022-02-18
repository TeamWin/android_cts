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

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothStatusCodes;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Activity for verifying the handoff of RFCOMM sockets from the bluetooth manager to the app
 * holding the car projection role. This test expects a second device to run the {@link
 * BackgroundRfcommTestClientActivity}.
 */
public class BackgroundRfcommTestActivity extends PassFailButtons.Activity {
    private static final String TAG = "BT.CarProjectionTest";
    private static final String ACTION = "BT_BACKGROUND_RFCOMM_TEST_ACTION";

    private BluetoothAdapter mBluetoothAdapter;
    private UUID mUuid;
    private String mTestMessage;
    private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bt_background_rfcomm);
        getPassButton().setEnabled(false);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mUuid = UUID.fromString(getString(R.string.bt_background_rfcomm_test_uuid));
        mTestMessage = getString(R.string.bt_background_rfcomm_test_message);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                writeAndReadRfcommData();
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION);

        registerReceiver(mReceiver, intentFilter);

        Intent intent = new Intent(ACTION);
        intent.putExtra(BluetoothAdapter.EXTRA_RFCOMM_LISTENER_ID, mUuid.toString());

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        mBluetoothAdapter.stopRfcommServer(mUuid);

        if (mBluetoothAdapter.startRfcommServer("TestBackgroundRfcomm", mUuid, pendingIntent)
                != BluetoothStatusCodes.SUCCESS) {
            Log.e(TAG, "Failed to start RFCOMM listener");
            setTestResultAndFinish(false);
        }
    }

    @Override
    protected void onDestroy() {
        mBluetoothAdapter.stopRfcommServer(mUuid);
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        super.onDestroy();
    }

    private void writeAndReadRfcommData() {
        BluetoothSocket bluetoothSocket = mBluetoothAdapter.retrieveConnectedRfcommSocket(mUuid);

        if (bluetoothSocket == null) {
            Log.e(TAG, "Failed to retrieve incoming RFCOMM socket connection");
            setTestResultAndFinish(false);
            return;
        }

        updateInstructions(R.string.bt_background_rfcomm_test_socket_received);

        try {
            updateInstructions(R.string.bt_background_rfcomm_test_sending_message);
            bluetoothSocket.getOutputStream().write(mTestMessage.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "Failed to write test message to RFCOMM socket", e);
            setTestResultAndFinish(false);
            return;
        }

        int offset = 0;
        int length = mTestMessage.length();
        ByteBuffer buf = ByteBuffer.allocate(length);

        try {
            updateInstructions(R.string.bt_background_rfcomm_test_waiting_for_message);
            while (length > 0) {
                int numRead = bluetoothSocket.getInputStream().read(buf.array(), offset, length);
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

        if (receivedMessage.equals(mTestMessage)) {
            setTestResultAndFinish(true);
        } else {
            Log.e(TAG, "Incorrect RFCOMM message received from client");
            setTestResultAndFinish(false);
        }
    }


    private void updateInstructions(int id) {
        TextView textView = findViewById(R.id.bt_background_rfcomm_text);
        textView.setText(id);
    }
}
