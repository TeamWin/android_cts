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
import android.widget.Button;
import android.widget.Toast;


public class MainActivity extends Activity implements ContextProvider {
    private static final int REQUEST_CODE_CDM = 2;
    private static final int REQUEST_CODE_PERMISSIONS = 3;
    private static final int REQUEST_CODE_BLUETOOTH_DISCOVERY = 4;
    private static final int REQUEST_CODE_BLUETOOTH_PERMISSIONS = 5;
    private static final int BLUETOOTH_DISCOVERY_DURATION_SECONDS = 100;

    private CDMController mCDMController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mCDMController = new CDMController(this);

        Button associateButton = findViewById(R.id.associateButton);
        Button disassociateButton = findViewById(R.id.disassociateButton);
        Button permissionRequestButton = findViewById(R.id.permissionsRequestButton);
        Button transferDataButton = findViewById(R.id.transferDataButton);
        Button startAdvertisingButton = findViewById(R.id.startAdvertisingButton);

        associateButton.setOnClickListener(v -> mCDMController.associate());
        disassociateButton.setOnClickListener(v -> mCDMController.disassociate());
        permissionRequestButton.setOnClickListener(v -> mCDMController.requestUserConsentForTransfer());
        transferDataButton.setOnClickListener(v -> mCDMController.beginDataTransfer());
        startAdvertisingButton.setOnClickListener(v -> checkBluetoothPermissions());
    }

    private void checkBluetoothPermissions() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PERMISSION_GRANTED) {
            Toast.makeText(this, "permission already granted, advertising", Toast.LENGTH_SHORT).show();
            advertise();
        } else {
            requestPermissions(new String[] { Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_BLUETOOTH_PERMISSIONS);
        }
    }

    private void advertise() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(
                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BLUETOOTH_DISCOVERY_DURATION_SECONDS);
        startActivityForResult(discoverableIntent, REQUEST_CODE_BLUETOOTH_DISCOVERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Toast.makeText(this, "onActivityResult: " + requestCode + " , " + resultCode, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_BLUETOOTH_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                    Toast.makeText(this, "permission granted, advertising", Toast.LENGTH_SHORT).show();
                    advertise();
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
}
