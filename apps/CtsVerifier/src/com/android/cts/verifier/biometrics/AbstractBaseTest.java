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

import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.concurrent.Executor;

/**
 * Abstract base class for tests in this directory.
 */
public abstract class AbstractBaseTest extends PassFailButtons.Activity {

    private static final int REQUEST_ENROLL = 1;

    abstract protected String getTag();

    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    protected final Executor mExecutor = mHandler::post;

    BiometricManager mBiometricManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBiometricManager = getSystemService(BiometricManager.class);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENROLL) {
            onBiometricEnrollFinished();
        }
    }

    void showToastAndLog(String s) {
        Log.d(getTag(), s);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    protected void onBiometricEnrollFinished() {
    }

    void checkAndEnroll(Button enrollButton, int requestedStrength,
            int[] acceptableConfigStrengths) {
        // Check that no biometrics (of any strength) are enrolled
        int result = mBiometricManager.canAuthenticate(requestedStrength);
        if (result == BiometricManager.BIOMETRIC_SUCCESS) {
            showToastAndLog("Please ensure that all biometrics are removed before starting"
                    + " this test");
            return;
        }

        result = mBiometricManager.canAuthenticate(requestedStrength);
        if (result == BiometricManager.BIOMETRIC_SUCCESS) {
            showToastAndLog("Please ensure that all biometrics are removed before starting"
                    + " this test");
        } else if (result == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            boolean configContainsRequestedStrength = false;
            for (int strength : acceptableConfigStrengths) {
                if (Utils.deviceConfigContains(this, strength)) {
                    configContainsRequestedStrength = true;
                    break;
                }
            }

            if (configContainsRequestedStrength) {
                showToastAndLog("Your configuration contains the requested biometric strength, but"
                        + " is inconsistent with BiometricManager.");
            } else {
                showToastAndLog("This device does not have a sensor meeting the requested strength,"
                        + " you may pass this test");
                enrollButton.setEnabled(false);
                getPassButton().setEnabled(true);
            }
        } else if (result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            final Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
            enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_MINIMUM_STRENGTH_REQUIRED,
                    requestedStrength);

            startActivityForResult(enrollIntent, REQUEST_ENROLL);
        } else {
            showToastAndLog("Unexpected result: " + result + ". Please ensure you have removed"
                    + "all biometric enrollments.");
        }
    }

    void testBiometricUI(Utils.VerifyRandomContents contents, int allowedAuthenticators) {
        Utils.showInstructionDialog(this,
                R.string.biometric_test_ui_instruction_dialog_title,
                R.string.biometric_test_ui_instruction_dialog_contents,
                R.string.biometric_test_ui_instruction_dialog_continue,
                (dialog, which) -> {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                // Create the BiometricPrompt with the above random numbers
                final BiometricPrompt.Builder builder =
                        new BiometricPrompt.Builder(this);
                builder.setAllowedAuthenticators(allowedAuthenticators);
                builder.setTitle("Title: " + contents.mRandomTitle);
                builder.setSubtitle("Subtitle: " + contents.mRandomSubtitle);
                builder.setDescription("Description: " + contents.mRandomDescription);
                builder.setNegativeButton("Negative Button: "
                                + contents.mRandomNegativeButtonText, mExecutor,
                        (dialog1, which1) -> {
                            // Ignore
                        });
                final BiometricPrompt prompt = builder.build();

                // When authentication succeeds, check that the values entered by the
                // tester match the generated values.
                prompt.authenticate(new CancellationSignal(), mExecutor,
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(
                                    BiometricPrompt.AuthenticationResult result) {
                                final int authenticationType = result.getAuthenticationType();
                                if (authenticationType !=
                                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                                    showToastAndLog("Unexpected authenticationType: "
                                            + authenticationType);
                                    return;
                                }

                                Utils.showUIVerificationDialog(AbstractBaseTest.this,
                                        R.string.biometric_test_ui_verification_dialog_title,
                                        R.string.biometric_test_ui_verification_dialog_check,
                                        contents);
                            }
                        });
            }
        });
    }
}
