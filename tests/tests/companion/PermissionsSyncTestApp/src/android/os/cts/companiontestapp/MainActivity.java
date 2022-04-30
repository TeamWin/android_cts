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


import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;

import android.companion.cts.permissionssynctestapp.R;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;


public class MainActivity extends Activity implements ContextProvider {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE_CDM = 2;
    private static final int REQUEST_CODE_PERMISSIONS = 3;
    private static final int REQUEST_CODE_BLUETOOTH_DISCOVERY = 4;
    private static final int REQUEST_CODE_BLUETOOTH_PERMISSIONS = 5;
    private static final int BLUETOOTH_DISCOVERY_DURATION_SECONDS = 100;

    private CommunicationManager mCommunicationManager;
    private CDMController mCDMController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mCommunicationManager = new CommunicationManager(this, mHandler);
        mCDMController = new CDMController(this, mCommunicationManager);

        Button associateButton = findViewById(R.id.associateButton);
        Button disassociateButton = findViewById(R.id.disassociateButton);
        Button permissionRequestButton = findViewById(R.id.permissionsRequestButton);
        Button transferDataButton = findViewById(R.id.transferDataButton);
        Button startAdvertisingButton = findViewById(R.id.startAdvertisingButton);

        associateButton.setOnClickListener(v -> mCDMController.associate());
        disassociateButton.setOnClickListener(v -> mCDMController.disassociate());
        permissionRequestButton.setOnClickListener(v -> mCDMController.requestUserConsentForTransfer());
        transferDataButton.setOnClickListener(v -> transferData());
        startAdvertisingButton.setOnClickListener(v -> startAdvertisingDevice());
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean permissionGranted = checkBluetoothPermissions();
        if (permissionGranted) {
            mCommunicationManager.onStart();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCommunicationManager.onStop();
    }

    private void transferData() {
        // TODO (b/230383170): transfer permission data between devices
        // mCDMController.beginDataTransfer();
        // Currently we are sending a test message
        String message = "test message";
        mCommunicationManager.sendMessage(message);
    }

    /**
     * Checks if the {@link Manifest.permission#BLUETOOTH_CONNECT} permission has been granted. If not,
     * requests the permission from the user
     *
     * @return true if the permission has been granted, false otherwise
     */
    private boolean checkBluetoothPermissions() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PERMISSION_GRANTED) {
            Toast.makeText(this, "permission already granted", Toast.LENGTH_SHORT).show();
            return true;
        } else {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_BLUETOOTH_PERMISSIONS);
            return false;
        }
    }

    private void startAdvertisingDevice() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(
                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BLUETOOTH_DISCOVERY_DURATION_SECONDS);
        startActivityForResult(discoverableIntent, REQUEST_CODE_BLUETOOTH_DISCOVERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult called with requestCode: " + requestCode + " and resultCode: " + resultCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_BLUETOOTH_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                    Toast.makeText(this, "permission granted", Toast.LENGTH_SHORT).show();
                    mCommunicationManager.onStart();
                }  else {
                    Toast.makeText(this, "permission denied", Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void processAssociationIntentSender(IntentSender intentSender) {
        try {
            startIntentSenderForResult(intentSender, REQUEST_CODE_CDM, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Toast.makeText(this, "IntentSender exception", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void processPermissionsSyncUserConsentIntentSender(IntentSender intentSender) {
        try {
            startIntentSenderForResult(intentSender, REQUEST_CODE_PERMISSIONS, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Toast.makeText(this, "IntentSender exception", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * The Handler that gets information back from the BluetoothCommunicationService
     */
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = MainActivity.this;
            switch (msg.what) {
                case BluetoothCommunicationService.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothCommunicationService.STATE_CONNECTED:
                            Toast.makeText(activity, "state: connected", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothCommunicationService.STATE_CONNECTING:
                            Toast.makeText(activity, "state: connecting", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothCommunicationService.STATE_LISTEN:
                            Toast.makeText(activity, "state: listen", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothCommunicationService.STATE_NONE:
                            Toast.makeText(activity, "state: none", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case BluetoothCommunicationService.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // nothing to do
                    break;
                case BluetoothCommunicationService.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Toast.makeText(activity, "Message Received:  "
                            + readMessage, Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothCommunicationService.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    String deviceName = msg.getData().getString(BluetoothCommunicationService.DEVICE_NAME);
                    Toast.makeText(activity, "Connected to "
                            + deviceName, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
}
