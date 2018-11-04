/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.verifier.biometrics;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.concurrent.Executor;

/**
 * Manual test for BiometricManager and BiometricPrompt. This tests two things currently.
 * 1) When no biometrics are enrolled, BiometricManager and BiometricPrompt both return consistent
 *    BIOMETRIC_ERROR_NO_BIOMETRICS errors).
 * 2) When biometrics are enrolled, BiometricManager returns BIOMETRIC_SUCCESS and BiometricPrompt
 *    authentication can be successfully completed.
 */
public class BiometricTest extends PassFailButtons.Activity {

    private static final String TAG = "BiometricTest";
    private static final String BIOMETRIC_ENROLL = "android.settings.BIOMETRIC_ENROLL";
    private static final int BIOMETRIC_PERMISSION_REQUEST_CODE = 0;

    private static final int TEST_NONE_ENROLLED = 1;
    private static final int TEST_ENROLLED = 2;

    private BiometricManager mBiometricManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private CancellationSignal mCancellationSignal;
    private int mExpectedError;
    private int mCurrentTest;
    private Button mButtonEnroll;
    private Button mButtonTest1;
    private Button mButtonTest2;

    private Executor mExecutor = (runnable) -> {
        mHandler.post(runnable);
    };

    private BiometricPrompt.AuthenticationCallback mAuthenticationCallback =
            new BiometricPrompt.AuthenticationCallback() {
        @Override
        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            if (mCurrentTest == TEST_NONE_ENROLLED) {
                showToastAndLog("This should be impossible, please capture a bug report");
            } else if (mCurrentTest == TEST_ENROLLED) {
                showToastAndLog("Authenticated. You passed the test.");
                getPassButton().setEnabled(true);
            }
        }

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            if (mCurrentTest == TEST_NONE_ENROLLED) {
                if (errorCode == mExpectedError) {
                    mButtonTest1.setVisibility(View.INVISIBLE);
                    mButtonTest2.setVisibility(View.VISIBLE);
                    mButtonEnroll.setVisibility(View.VISIBLE);
                    showToastAndLog("Please enroll a biometric and start the next test");
                } else {
                    showToastAndLog("Expected: " + mExpectedError +
                            " Actual: " + errorCode + " " + errString);
                }
            } else if (mCurrentTest == TEST_ENROLLED) {
                showToastAndLog(errString.toString() + " Please try again");
            }
        }
    };

    private DialogInterface.OnClickListener mBiometricPromptButtonListener = (dialog, which) -> {
        showToastAndLog("Authentication canceled");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.biometric_test_main);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.biometric_test, R.string.biometric_test_info, -1);
        getPassButton().setEnabled(false);

        mBiometricManager = getApplicationContext().getSystemService(BiometricManager.class);
        mButtonEnroll = findViewById(R.id.biometric_enroll_button);
        mButtonEnroll.setVisibility(View.INVISIBLE);
        mButtonTest1 = findViewById(R.id.biometric_start_test1_button);
        mButtonTest2 = findViewById(R.id.biometric_start_test2_button);
        mButtonTest2.setVisibility(View.INVISIBLE);

        PackageManager pm = getApplicationContext().getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                || pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
                || pm.hasSystemFeature(PackageManager.FEATURE_FACE)) {
            requestPermissions(new String[]{Manifest.permission.USE_BIOMETRIC},
                    BIOMETRIC_PERMISSION_REQUEST_CODE);
            mButtonTest1.setEnabled(false);
            mButtonTest1.setOnClickListener((view) -> {
                startTest(TEST_NONE_ENROLLED);
            });
            mButtonTest2.setOnClickListener((view) -> {
                startTest(TEST_ENROLLED);
            });
            mButtonEnroll.setOnClickListener((view) -> {
                final Intent intent = new Intent();
                intent.setAction(BIOMETRIC_ENROLL);
                startActivity(intent);
            });
        } else {
            // NO biometrics available
            mButtonTest1.setEnabled(false);
            getPassButton().setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] state) {
        if (requestCode == BIOMETRIC_PERMISSION_REQUEST_CODE &&
                state[0] == PackageManager.PERMISSION_GRANTED) {
            mButtonTest1.setEnabled(true);
        }
    }

    private void startTest(int testType) {
        mCurrentTest = testType;
        int result = mBiometricManager.canAuthenticate();

        if (testType == TEST_NONE_ENROLLED) {
            if (result == BiometricManager.BIOMETRIC_ERROR_NO_BIOMETRICS) {
                mExpectedError = BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS;
                showBiometricPrompt();
            } else {
                showToastAndLog("Error: " + result + " Please remove all biometrics and try again");
            }
        } else if (testType == TEST_ENROLLED) {
            if (result == BiometricManager.BIOMETRIC_SUCCESS) {
                mExpectedError = 0;
                showBiometricPrompt();
            } else {
                showToastAndLog("Error: " + result +
                        " Please ensure at least one biometric is enrolled and try again");
            }
        } else {
            showToastAndLog("Unknown test type: " + testType);
        }
    }

    private void showBiometricPrompt() {
        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(getApplicationContext())
            .setTitle("Please authenticate")
            .setNegativeButton("Cancel", mExecutor, mBiometricPromptButtonListener);
        BiometricPrompt bp = builder.build();
        mCancellationSignal = new CancellationSignal();
        bp.authenticate(mCancellationSignal, mExecutor, mAuthenticationCallback);
    }

    private void showToastAndLog(String string) {
        Toast.makeText(getApplicationContext(), string, Toast.LENGTH_SHORT).show();
        Log.v(TAG, string);
    }
}
