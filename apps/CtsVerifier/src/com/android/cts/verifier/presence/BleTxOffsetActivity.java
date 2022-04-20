/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.presence;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.widget.EditText;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/** Tests that the devices' Rx offset results in a median RSSI within a specified range */
public class BleTxOffsetActivity extends PassFailButtons.Activity {
    private static final String TAG = BleTxOffsetActivity.class.getName();

    // Report log schema
    private static final String KEY_MEDIAN_RSSI = "rssi_range";
    private static final String KEY_REFERENCE_DEVICE = "reference_device";

    // Thresholds
    private static final int MEDIAN_RSSI_UPPER_BOUND = -57;
    private static final int MEDIAN_RSSI_LOWER_BOUND = -63;

    private EditText reportMedianRssiEditText;
    private EditText reportReferenceDeviceEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_tx_offset);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        DeviceFeatureChecker.checkFeatureSupported(this, getPassButton(),
                PackageManager.FEATURE_BLUETOOTH_LE);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (!adapter.isEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.ble_bluetooth_disable_title)
                    .setMessage(R.string.ble_bluetooth_disable_message)
                    .setOnCancelListener(dialog -> finish())
                    .create().show();
        }

        reportMedianRssiEditText = findViewById(R.id.report_ble_rssi_median);
        reportReferenceDeviceEditText = findViewById(R.id.report_reference_device);

        reportMedianRssiEditText.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        reportReferenceDeviceEditText.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
    }

    private void checkTestInputs() {
        getPassButton().setEnabled(checkMedianRssiInput() && checkReferenceDeviceInput());
    }

    private boolean checkMedianRssiInput() {
        String medianRssiInput = reportMedianRssiEditText.getText().toString();

        if (!medianRssiInput.isEmpty()) {
            int rssiRange;
            try {
                rssiRange = Integer.parseInt(medianRssiInput);
            } catch (NumberFormatException e) {
                return false;
            }
            // Median RSSI must be inputted and within acceptable range before test can be passed
            return rssiRange <= MEDIAN_RSSI_UPPER_BOUND && rssiRange >= MEDIAN_RSSI_LOWER_BOUND;
        }
        return false;
    }

    private boolean checkReferenceDeviceInput() {
        // Reference device must be inputted before test can be passed
        return !reportReferenceDeviceEditText.getText().toString().isEmpty();
    }

    @Override
    public void recordTestResults() {
        String medianRssi = reportMedianRssiEditText.getText().toString();
        String referenceDevice = reportReferenceDeviceEditText.getText().toString();

        if (!medianRssi.isEmpty()) {
            Log.i(TAG, "BLE Median RSSI (dBm): " + medianRssi);
            getReportLog().addValue(KEY_MEDIAN_RSSI, Integer.parseInt(medianRssi),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }

        if (!referenceDevice.isEmpty()) {
            Log.i(TAG, "BLE Reference Device: " + referenceDevice);
            getReportLog().addValue(KEY_REFERENCE_DEVICE, referenceDevice,
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }
        getReportLog().submit();
    }
}
