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
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.widget.EditText;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Arrays;
import java.util.List;

/**
 * Activity for testing that NAN measurements are within the acceptable range and fits a trend line
 * that has a bias and slope within the expected range
 * range.
 */
public class NanPrecisionAndBiasTestActivity extends PassFailButtons.Activity {
    private static final String TAG = NanPrecisionAndBiasTestActivity.class.getName();

    // Report log schema
    private static final String KEY_MEASUREMENT_RANGE_1M = "measurement_range_1m";
    private static final String KEY_MEASUREMENT_RANGE_3M = "measurement_range_3m";
    private static final String KEY_MEASUREMENT_RANGE_5M = "measurement_range_5m";
    private static final String KEY_BIAS_METERS = "bias_meters";
    private static final String KEY_SLOPE_METERS = "slope_meters";
    private static final String KEY_REFERENCE_DEVICE = "reference_device";

    // Thresholds
    private static final int MAX_DISTANCE_RANGE_METERS = 2;
    private static final double MIN_BIAS_METERS = -0.25;
    private static final double MAX_BIAS_METERS = 0.25;
    private static final double MIN_SLOPE = 0.95;
    private static final double MAX_SLOPE = 1.05;

    private EditText mMeasurementRange1mGt;
    private EditText mMeasurementRange3mGt;
    private EditText mMeasurementRange5mGt;
    private EditText mBiasInput;
    private EditText mSlopeInput;
    private EditText mReferenceDeviceInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nan_precision_and_bias);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        DeviceFeatureChecker.checkFeatureSupported(this, getPassButton(),
                PackageManager.FEATURE_WIFI_AWARE);

        mMeasurementRange1mGt = (EditText) findViewById(R.id.distance_range_1m_gt);
        mMeasurementRange3mGt = (EditText) findViewById(R.id.distance_range_3m_gt);
        mMeasurementRange5mGt = (EditText) findViewById(R.id.distance_range_5m_gt);
        mBiasInput = (EditText) findViewById(R.id.bias_meters);
        mSlopeInput = (EditText) findViewById(R.id.slope);
        mReferenceDeviceInput = (EditText) findViewById(R.id.reference_device);

        mMeasurementRange1mGt.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        mMeasurementRange3mGt.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        mMeasurementRange5mGt.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        mBiasInput.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        mSlopeInput.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        mReferenceDeviceInput.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
    }

    private void checkTestInputs() {
        getPassButton().setEnabled(checkMeasurementRangeInput()
                && checkBiasInput() && checkSlopeInput()
                && checkReferenceDeviceInput());
    }

    private boolean checkMeasurementRangeInput() {
        String measurementRangeInput1mGt = mMeasurementRange1mGt.getText().toString();
        String measurementRangeInput3mGt = mMeasurementRange3mGt.getText().toString();
        String measurementRangeInput5mGt = mMeasurementRange1mGt.getText().toString();
        List<String> measurementRangeList = Arrays.asList(measurementRangeInput1mGt,
                measurementRangeInput3mGt, measurementRangeInput5mGt);

        for (String input : measurementRangeList) {
            if (input.isEmpty()) {
                // Distance range must be inputted for all fields so fail early otherwise
                return false;
            }
            int distanceRange = Integer.parseInt(input);
            if (distanceRange > MAX_DISTANCE_RANGE_METERS) {
                // All inputs must be in acceptable range so fail early otherwise
                return false;
            }
        }
        return true;
    }

    private boolean checkBiasInput() {
        String biasInput = mBiasInput.getText().toString();

        if (!biasInput.isEmpty()) {
            double bias = Double.parseDouble(biasInput);
            // Bias must be inputted and within acceptable range before test can be passed.
            return bias >= MIN_BIAS_METERS && bias <= MAX_BIAS_METERS;
        }
        return false;
    }

    private boolean checkSlopeInput() {
        String slopeInput = mSlopeInput.getText().toString();

        if (!slopeInput.isEmpty()) {
            double slope = Double.parseDouble(slopeInput);
            // Slope must be inputted and within acceptable range before test can be passed.
            return slope >= MIN_SLOPE && slope <= MAX_SLOPE;
        }
        return false;
    }

    private boolean checkReferenceDeviceInput() {
        // Reference device used must be inputted before test can be passed.
        return !mReferenceDeviceInput.getText().toString().isEmpty();
    }

    @Override
    public void recordTestResults() {
        String measurementRange1mGt = mMeasurementRange1mGt.getText().toString();
        String measurementRange3mGt = mMeasurementRange3mGt.getText().toString();
        String measurementRange5mGt = mMeasurementRange5mGt.getText().toString();
        String bias = mBiasInput.getText().toString();
        String slope = mSlopeInput.getText().toString();
        String referenceDevice = mReferenceDeviceInput.getText().toString();

        if (!measurementRange1mGt.isEmpty()) {
            Log.i(TAG, "NAN Measurement Range at 1m: " + measurementRange1mGt);
            getReportLog().addValue(KEY_MEASUREMENT_RANGE_1M,
                    Integer.parseInt(measurementRange1mGt),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }

        if (!measurementRange3mGt.isEmpty()) {
            Log.i(TAG, "NAN Measurement Range at 3m: " + measurementRange3mGt);
            getReportLog().addValue(KEY_MEASUREMENT_RANGE_3M,
                    Integer.parseInt(measurementRange1mGt),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }

        if (!measurementRange5mGt.isEmpty()) {
            Log.i(TAG, "NAN Measurement Range at 5m: " + measurementRange5mGt);
            getReportLog().addValue(KEY_MEASUREMENT_RANGE_5M,
                    Integer.parseInt(measurementRange1mGt),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }

        if (!bias.isEmpty()) {
            Log.i(TAG, "NAN bias: " + bias);
            getReportLog().addValue(KEY_BIAS_METERS, Double.parseDouble(bias),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }

        if (!slope.isEmpty()) {
            Log.i(TAG, "NAN slope: " + slope);
            getReportLog().addValue(KEY_SLOPE_METERS, Double.parseDouble(slope),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }

        if (!referenceDevice.isEmpty()) {
            Log.i(TAG, "NAN Reference Device: " + referenceDevice);
            getReportLog().addValue(KEY_REFERENCE_DEVICE, referenceDevice,
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }
        getReportLog().submit();
    }
}
