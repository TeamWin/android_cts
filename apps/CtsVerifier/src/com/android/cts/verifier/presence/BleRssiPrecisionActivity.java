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

package com.android.cts.verifier.presence;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.presence.ble.BleAdvertisingPacket;
import com.android.cts.verifier.presence.ble.BleScanner;
import com.android.cts.verifier.presence.ble.BleAdvertiser;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

public class BleRssiPrecisionActivity extends PassFailButtons.Activity {
    private static final String TAG = BleRssiPrecisionActivity.class.getName();
    private static final String DEVICE_NAME = Build.MODEL;

    // Report log schema
    private static final String KEY_REFERENCE_DEVICE = "reference_device";

    // Thresholds
    private static final int MAX_RSSI_RANGE_DBM = 18;

    private boolean isReferenceDevice;
    private BleScanner mBleScanner;
    private BleAdvertiser mBleAdvertiser;
    private HashMap<String, ArrayList<Integer>> mRssiResultMap;
    private Button mStartTestButton;
    private Button mStopTestButton;
    private Button mStartAdvertisingButton;
    private Button mStopAdvertisingButton;
    private LinearLayout mDutModeLayout;
    private LinearLayout mRefModeLayout;
    private TextView mDeviceIdInfoTextView;
    private TextView mDeviceFoundTextView;
    private EditText mReferenceDeviceIdInput;
    private String mReferenceDeviceName;
    private boolean mTestPassed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_rssi_precision);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mStartTestButton = findViewById(R.id.start_test);
        mStopTestButton = findViewById(R.id.stop_test);
        mStartAdvertisingButton = findViewById(R.id.start_advertising);
        mStopAdvertisingButton = findViewById(R.id.stop_advertising);
        mDeviceIdInfoTextView = findViewById(R.id.device_id_info);
        mDeviceFoundTextView = findViewById(R.id.device_found_info);
        mReferenceDeviceIdInput = findViewById(R.id.ref_device_id_input);
        CheckBox isReferenceDeviceCheckbox = findViewById(R.id.is_reference_device);
        mDutModeLayout = findViewById(R.id.dut_mode_layout);
        mRefModeLayout = findViewById(R.id.ref_mode_layout);
        DeviceFeatureChecker.checkFeatureSupported(this, getPassButton(),
                PackageManager.FEATURE_BLUETOOTH_LE);
        mBleAdvertiser = new BleAdvertiser();
        mBleScanner = new BleScanner();
        mRssiResultMap = new HashMap<>();
        mStopTestButton.setEnabled(false);
        mStopAdvertisingButton.setEnabled(false);
        mDeviceIdInfoTextView.setVisibility(View.GONE);
        mDeviceFoundTextView.setVisibility(View.GONE);
        isReferenceDevice = isReferenceDeviceCheckbox.isChecked();
        checkUiMode();
        isReferenceDeviceCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReferenceDevice = isChecked;
            checkUiMode();
        });
        mStartTestButton.setOnClickListener(v -> startTest());
        mStopTestButton.setOnClickListener(v -> stopTest());
        mStartAdvertisingButton.setOnClickListener(v -> startAdvertising());
        mStopAdvertisingButton.setOnClickListener(v -> stopAdvertising());
    }

    private void startTest() {
        if (mReferenceDeviceIdInput.getText().toString().isEmpty()) {
            makeToast("Input the device ID shown on the advertising device before commencing test");
            return;
        }
        mStartTestButton.setEnabled(false);
        mStopTestButton.setEnabled(true);
        mBleScanner.startScanning((uuids,
                macAddress,
                deviceName,
                referenceDeviceName,
                deviceId,
                rawRssi) -> {

            mRssiResultMap.computeIfAbsent(deviceName, k -> new ArrayList<>());
            ArrayList<Integer> resultList = mRssiResultMap.get(deviceName);
            resultList.add(rawRssi);
            mDeviceFoundTextView.setVisibility(View.VISIBLE);
            mReferenceDeviceName = referenceDeviceName;
            String deviceFoundText = getString(R.string.device_found_presence,
                    resultList.size());
            mDeviceFoundTextView.setText(deviceFoundText);
            if (resultList.size() >= 1000) {
                Log.i(TAG, "Data collection complete");
                mBleScanner.stopScanning();
                mStartTestButton.setEnabled(true);
                mStopTestButton.setEnabled(false);
                computeTestResults(resultList);
            }
        });
    }

    private void computeTestResults(ArrayList<Integer> data) {
        Collections.sort(data);
        // Calculate range at 95th percentile
        int rssiRange = data.get(975) - data.get(25);
        if (rssiRange <= MAX_RSSI_RANGE_DBM) {
            getPassButton().performClick();
            makeToast("Test passed! Rssi range is: " + rssiRange);
            mTestPassed = true;
        } else {
            makeToast("Test failed! Rssi range is: " + rssiRange);
        }
        data.clear();
    }

    private void stopTest() {
        mBleScanner.stopScanning();
        mStopTestButton.setEnabled(false);
        mStartTestButton.setEnabled(true);
    }

    private void startAdvertising() {
        byte randomAdvertiserDeviceId = getRandomDeviceId();
        String deviceIdInfoText = getString(R.string.device_id_info_presence,
                randomAdvertiserDeviceId);
        mDeviceIdInfoTextView.setText(deviceIdInfoText);
        mDeviceIdInfoTextView.setVisibility(View.VISIBLE);
        String packetDeviceName = DEVICE_NAME.substring(0,
                BleAdvertisingPacket.MAX_REFERENCE_DEVICE_NAME_LENGTH - 1);
        mBleAdvertiser.startAdvertising(
                new BleAdvertisingPacket(packetDeviceName, randomAdvertiserDeviceId).toBytes());
        mStartAdvertisingButton.setEnabled(false);
        mStopAdvertisingButton.setEnabled(true);
    }

    private void stopAdvertising() {
        mBleAdvertiser.stopAdvertising();
        mStopAdvertisingButton.setEnabled(false);
        mStartAdvertisingButton.setEnabled(true);
        mDeviceIdInfoTextView.setVisibility(View.GONE);
    }

    private void checkUiMode() {
        if (isReferenceDevice) {
            mDutModeLayout.setVisibility(View.GONE);
            mRefModeLayout.setVisibility(View.VISIBLE);
        } else {
            mRefModeLayout.setVisibility(View.GONE);
            mDutModeLayout.setVisibility(View.VISIBLE);
        }
    }

    private static byte getRandomDeviceId() {
        Random random = new Random();
        byte[] randomDeviceIdArray = new byte[1];
        random.nextBytes(randomDeviceIdArray);
        return randomDeviceIdArray[0];
    }

    private void makeToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void recordTestResults() {
        if (mTestPassed) {
            getReportLog().addValue(KEY_REFERENCE_DEVICE, mReferenceDeviceName,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            getReportLog().submit();
        }
    }
}