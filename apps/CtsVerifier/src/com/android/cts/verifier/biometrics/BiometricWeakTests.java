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

import static android.hardware.biometrics.BiometricManager.Authenticators;

import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricPrompt.AuthenticationCallback;
import android.hardware.biometrics.BiometricPrompt.CryptoObject;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;

import com.android.cts.verifier.R;

import javax.crypto.Cipher;

/**
 * On devices without a weak biometric, ensure that the
 * {@link BiometricManager#canAuthenticate(int)} returns
 * {@link BiometricManager#BIOMETRIC_ERROR_NO_HARDWARE}
 *
 * Ensure that this result is consistent with the configuration in core/res/res/values/config.xml
 *
 * Ensure that invoking {@link Settings.ACTION_BIOMETRIC_ENROLL} with its corresponding
 * {@link Settings.EXTRA_BIOMETRIC_MINIMUM_STRENGTH_REQUIRED} enrolls a biometric that meets or
 * exceeds {@link BiometricManager.Authenticators.BIOMETRIC_WEAK}.
 *
 * Ensure that the BiometricPrompt UI displays all fields in the public API surface.
 */

public class BiometricWeakTests extends AbstractBaseTest {
    private static final String TAG = "BiometricWeakTests";

    private Button mEnrollButton;
    private Button mAuthenticateButton;

    private boolean mAuthenticatePassed;

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.biometric_test_weak_tests);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mEnrollButton = findViewById(R.id.biometric_test_weak_enroll_button);
        mAuthenticateButton = findViewById(R.id.biometric_test_weak_authenticate_button);

        mAuthenticateButton.setEnabled(false);

        mEnrollButton.setOnClickListener((view) -> {
            checkAndEnroll(mEnrollButton, Authenticators.BIOMETRIC_WEAK,
                    new int[]{Authenticators.BIOMETRIC_WEAK, Authenticators.BIOMETRIC_STRONG});
        });

        // Note: This button is running multiple sub-tests. This is to prevent misleading results
        // that could be caused by switching biometric sensors between tests.
        mAuthenticateButton.setOnClickListener((view) -> {
            // Note: Since enrollment request with Authenticators.BIOMETRIC_WEAK requests enrollment
            // for Weak "or stronger", it's possible that the user was asked to enroll a Strong
            // biometric. Thus, generation of keys may or may not pass - both are valid outcomes.

            // Check that requesting authentication with WEAK + CryptoObject throws
            // IllegalArgumentException. Note that we're using a CryptoObject without an actual
            // MAC/Signature/Cipher due to the above.
            final BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this);
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK);
            builder.setTitle("This UI should never get shown");
            builder.setNegativeButton("Cancel", mExecutor, (dialog, which) -> {
                // Ignore
            });
            final CryptoObject dummyCrypto = new CryptoObject((Cipher) null);
            final BiometricPrompt prompt = builder.build();

            boolean exceptionCaught = false;
            try {
                prompt.authenticate(dummyCrypto, new CancellationSignal(), mExecutor,
                        new AuthenticationCallback() {
                            // Ignore
                        });
            } catch (IllegalArgumentException e) {
                // Expected
                exceptionCaught = true;
                Log.d(TAG, "IllegalArgumentException: " + e);
            }

            if (!exceptionCaught) {
                showToastAndLog("Authenticating with BIOMETRIC_WEAK and Crypto is not a valid"
                        + " combination");
                return;
            }

            // Check that requesting authentication with WEAK works, and that the UI presents the
            // fields set through its public APIs
            final Utils.VerifyRandomContents contents = new Utils.VerifyRandomContents(this) {
                @Override
                void onVerificationSucceeded() {
                    mAuthenticatePassed = true;
                    mAuthenticateButton.setEnabled(false);
                    updatePassButton();
                }
            };
            testBiometricUI(contents, Authenticators.BIOMETRIC_WEAK);
        });
    }

    private void updatePassButton() {
        if (mAuthenticatePassed) {
            showToastAndLog("All tests passed");
            getPassButton().setEnabled(true);
        }
    }

    @Override
    protected void onBiometricEnrollFinished() {
        final int biometricStatus =
                mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK);
        if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            showToastAndLog("Successfully enrolled, please press the authenticate button");
            mEnrollButton.setEnabled(false);
            mAuthenticateButton.setEnabled(true);
        } else {
            showToastAndLog("Unexpected result after enrollment: " + biometricStatus);
        }
    }

}
