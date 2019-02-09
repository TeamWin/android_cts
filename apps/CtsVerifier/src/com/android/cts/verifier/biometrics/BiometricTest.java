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
import android.app.KeyguardManager;
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
 *    BIOMETRIC_ERROR_NONE_ENROLLED errors).
 * 2) When biometrics are enrolled, BiometricManager returns BIOMETRIC_SUCCESS and BiometricPrompt
 *    authentication can be successfully completed.
 */
public class BiometricTest extends PassFailButtons.Activity {

    private static final String TAG = "BiometricTest";
    private static final String BIOMETRIC_ENROLL = "android.settings.BIOMETRIC_ENROLL";
    private static final int BIOMETRIC_PERMISSION_REQUEST_CODE = 0;

    // Test that BiometricPrompt setAllowDeviceCredentials returns ERROR_NO_DEVICE_CREDENTIAL when
    // pin, pattern, password is not set.
    private static final int TEST_NOT_SECURED = 1;
    // Test that BiometricPrompt returns BIOMETRIC_ERROR_NO_BIOMETRICS when BiometricManager
    // states BIOMETRIC_ERROR_NONE_ENROLLED.
    private static final int TEST_NONE_ENROLLED = 2;
    // Test that BiometricPrompt setAllowDeviceCredentials can authenticate when no biometrics are
    // enrolled.
    private static final int TEST_DEVICE_CREDENTIAL = 3;
    // Test that authentication can succeed when biometrics are enrolled.
    private static final int TEST_AUTHENTICATE = 4;

    private BiometricManager mBiometricManager;
    private KeyguardManager mKeyguardManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private CancellationSignal mCancellationSignal;
    private int mCurrentTest;

    private Button mButtonEnroll;
    private Button mButtonTestNotSecured;
    private Button mButtonTestNoneEnrolled;
    private Button mButtonTestCredential;
    private Button mButtonTestAuthenticate;

    private Executor mExecutor = (runnable) -> {
        mHandler.post(runnable);
    };

    private BiometricPrompt.AuthenticationCallback mAuthenticationCallback =
            new BiometricPrompt.AuthenticationCallback() {
        @Override
        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            if (mCurrentTest == TEST_NOT_SECURED) {
                showToastAndLog("This should be impossible, please capture a bug report "
                        + mCurrentTest);
            } else if (mCurrentTest == TEST_NONE_ENROLLED) {
                showToastAndLog("This should be impossible, please capture a bug report"
                        + mCurrentTest);
            } else if (mCurrentTest == TEST_DEVICE_CREDENTIAL) {
                showToastAndLog("Please enroll a biometric and start the next test");
                mButtonTestCredential.setEnabled(false);
                mButtonEnroll.setVisibility(View.VISIBLE);
                mButtonTestAuthenticate.setVisibility(View.VISIBLE);
            } else if (mCurrentTest == TEST_AUTHENTICATE) {
                showToastAndLog("You have passed the test!");
                mButtonTestAuthenticate.setEnabled(false);
                getPassButton().setEnabled(true);
            }
        }

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            if (mCurrentTest == TEST_NOT_SECURED) {
                if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) {
                    showToastAndLog("Please start the next test");
                    mButtonTestNotSecured.setEnabled(false);
                    mButtonTestNoneEnrolled.setVisibility(View.VISIBLE);
                } else {
                    showToastAndLog("Error: " + errorCode + " " + errString);
                }
            } else if (mCurrentTest == TEST_NONE_ENROLLED) {
                if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS) {
                    mButtonTestNoneEnrolled.setEnabled(false);
                    mButtonTestCredential.setVisibility(View.VISIBLE);
                    showToastAndLog("Please start the next test");
                } else {
                    showToastAndLog("Error: " + errorCode + " " + errString);
                }
            } else if (mCurrentTest == TEST_DEVICE_CREDENTIAL) {
                showToastAndLog(errString.toString() + " Please try again");
            } else if (mCurrentTest == TEST_AUTHENTICATE) {
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
        mKeyguardManager = getApplicationContext().getSystemService(KeyguardManager.class);
        mButtonEnroll = findViewById(R.id.biometric_enroll_button);
        mButtonTestNoneEnrolled = findViewById(R.id.biometric_start_test_none_enrolled);
        mButtonTestNotSecured = findViewById(R.id.biometric_start_test_not_secured);
        mButtonTestAuthenticate = findViewById(R.id.biometric_start_test_authenticate_button);
        mButtonTestCredential = findViewById(R.id.biometric_start_test_credential_button);

        PackageManager pm = getApplicationContext().getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                || pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
                || pm.hasSystemFeature(PackageManager.FEATURE_FACE)) {
            requestPermissions(new String[]{Manifest.permission.USE_BIOMETRIC},
                    BIOMETRIC_PERMISSION_REQUEST_CODE);

            mButtonTestNotSecured.setEnabled(false);
            mButtonTestNotSecured.setOnClickListener((view) -> {
                startTest(TEST_NOT_SECURED);
            });
            mButtonTestNoneEnrolled.setOnClickListener((view) -> {
                startTest(TEST_NONE_ENROLLED);
            });
            mButtonTestAuthenticate.setOnClickListener((view) -> {
                startTest(TEST_AUTHENTICATE);
            });
            mButtonEnroll.setOnClickListener((view) -> {
                final Intent intent = new Intent();
                intent.setAction(BIOMETRIC_ENROLL);
                startActivity(intent);
            });
            mButtonTestCredential.setOnClickListener((view) -> {
                startTest(TEST_DEVICE_CREDENTIAL);
            });
        } else {
            // NO biometrics available
            mButtonTestNoneEnrolled.setEnabled(false);
            getPassButton().setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] state) {
        if (requestCode == BIOMETRIC_PERMISSION_REQUEST_CODE &&
                state[0] == PackageManager.PERMISSION_GRANTED) {
            mButtonTestNotSecured.setEnabled(true);
        }
    }

    private void startTest(int testType) {
        mCurrentTest = testType;
        int result = mBiometricManager.canAuthenticate();

        if (testType == TEST_NOT_SECURED) {
            if (mKeyguardManager.isDeviceSecure()) {
                showToastAndLog("Please remove your pin/pattern/password and try again");
            } else {
                showBiometricPrompt(true /* allowCredential */);
            }
        } else if (testType == TEST_NONE_ENROLLED) {
            if (result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                showBiometricPrompt(false /* allowCredential */);
            } else {
                showToastAndLog("Error: " + result + " Please remove all biometrics and try again");
            }
        } else if (testType == TEST_DEVICE_CREDENTIAL) {
            if (!mKeyguardManager.isDeviceSecure()) {
                showToastAndLog("Please set up a pin, pattern, or password and try again");
            } else if (result != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                showToastAndLog("Error: " + result + " Please remove all biometrics and try again");
            } else {
                showBiometricPrompt(true /* allowCredential */);
            }
        } else if (testType == TEST_AUTHENTICATE) {
            if (result == BiometricManager.BIOMETRIC_SUCCESS) {
                showBiometricPrompt(false /* allowCredential */);
            } else {
                showToastAndLog("Error: " + result +
                        " Please ensure at least one biometric is enrolled and try again");
            }
        } else {
            showToastAndLog("Unknown test type: " + testType);
        }
    }

    private void showBiometricPrompt(boolean allowCredential) {
        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(getApplicationContext())
            .setTitle("Please authenticate");
        if (allowCredential) {
            builder.setAllowDeviceCredential(true);
        } else {
            builder.setNegativeButton("Cancel", mExecutor, mBiometricPromptButtonListener);
        }
        BiometricPrompt bp = builder.build();
        mCancellationSignal = new CancellationSignal();
        bp.authenticate(mCancellationSignal, mExecutor, mAuthenticationCallback);
    }

    private void showToastAndLog(String string) {
        Toast.makeText(getApplicationContext(), string, Toast.LENGTH_SHORT).show();
        Log.v(TAG, string);
    }
}
