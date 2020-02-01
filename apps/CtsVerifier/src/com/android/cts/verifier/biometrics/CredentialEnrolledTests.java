/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.concurrent.Executor;

/**
 * This test checks that when a credential is enrolled, and biometrics are not enrolled,
 * BiometricManager and BiometricPrompt receive the correct results.
 */
public class CredentialEnrolledTests extends PassFailButtons.Activity {
    private static final String TAG = "CredentialEnrolledTests";

    boolean mBiometricManagerPass;
    boolean mBiometricPromptSetAllowedAuthenticatorsPass;
    boolean mBiometricPromptSetDeviceCredentialAllowedPass;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Executor mExecutor = mHandler::post;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.biometric_test_credential_enrolled_tests);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        // Test BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
        final Button bmButton = findViewById(R.id.bm_button);
        bmButton.setOnClickListener((view) -> {
            final BiometricManager bm = getSystemService(BiometricManager.class);

            final int biometricResult = bm.canAuthenticate(Authenticators.BIOMETRIC_WEAK);
            if (biometricResult != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                showToastAndLog("Unexpected result: " + biometricResult +
                        ". Please make sure the device does not have a biometric enrolled");
                return;
            }

            final int credentialResult = bm.canAuthenticate(Authenticators.DEVICE_CREDENTIAL);
            if (credentialResult == BiometricManager.BIOMETRIC_SUCCESS) {
                bmButton.setEnabled(false);
                mBiometricManagerPass = true;
                updatePassButton();
            } else {
                showToastAndLog("Unexpected result: " + credentialResult
                        + ". Please make sure the device"
                        + " has a PIN/Pattern/Password set");
            }
        });

        // Test setAllowedAuthenticators(DEVICE_CREDENTIAL)
        final Button bpSetAllowedAuthenticatorsButton =
                findViewById(R.id.setAllowedAuthenticators_button);
        bpSetAllowedAuthenticatorsButton.setOnClickListener((view) -> {
            BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this);
            builder.setTitle("Title");
            builder.setSubtitle("Subtitle");
            builder.setDescription("Description");
            builder.setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL);
            BiometricPrompt bp = builder.build();
            bp.authenticate(new CancellationSignal(), mExecutor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            final int authenticator = result.getAuthenticationType();
                            if (authenticator ==
                                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) {
                                bpSetAllowedAuthenticatorsButton.setEnabled(false);
                                mBiometricPromptSetAllowedAuthenticatorsPass = true;
                                updatePassButton();
                            } else {
                                showToastAndLog("Unexpected authenticator: " + authenticator);
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            showToastAndLog("Unexpected error: " + errorCode + ", " + errString);
                        }
                    });
        });

        // Test setDeviceCredentialAllowed(true)
        final Button bpSetDeviceCredentialAllowedButton =
                findViewById(R.id.setDeviceCredentialAllowed_button);
        bpSetDeviceCredentialAllowedButton.setOnClickListener((view) -> {
            BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this);
            builder.setTitle("Title");
            builder.setSubtitle("Subtitle");
            builder.setDescription("Description");
            builder.setDeviceCredentialAllowed(true);
            BiometricPrompt bp = builder.build();
            bp.authenticate(new CancellationSignal(), mExecutor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            final int authenticator = result.getAuthenticationType();
                            if (authenticator ==
                                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) {
                                bpSetDeviceCredentialAllowedButton.setEnabled(false);
                                mBiometricPromptSetDeviceCredentialAllowedPass = true;
                                updatePassButton();
                            } else {
                                showToastAndLog("Unexpected authenticator: " + authenticator
                                        + ". Please ensure the device does not have a biometric"
                                        + " enrolled.");
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            showToastAndLog("Unexpected error: " + errorCode + ", " + errString);
                        }
                    });
        });
    }

    private void showToastAndLog(String s) {
        Log.d(TAG, s);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void updatePassButton() {
        if (mBiometricManagerPass && mBiometricPromptSetAllowedAuthenticatorsPass
                && mBiometricPromptSetDeviceCredentialAllowedPass) {
            showToastAndLog("All tests passed");
            getPassButton().setEnabled(true);
        }
    }
}
