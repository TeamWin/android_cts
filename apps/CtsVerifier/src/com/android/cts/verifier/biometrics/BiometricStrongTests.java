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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricPrompt.CryptoObject;
import android.hardware.biometrics.BiometricPrompt.AuthenticationCallback;
import android.hardware.biometrics.BiometricPrompt.AuthenticationResult;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

/**
 * On devices without a strong biometric, ensure that the
 * {@link BiometricManager#canAuthenticate(int)} returns
 * {@link BiometricManager#BIOMETRIC_ERROR_NO_HARDWARE}
 *
 * Ensure that this result is consistent with the configuration in core/res/res/values/config.xml
 *
 * Ensure that invoking {@link Settings.ACTION_BIOMETRIC_ENROLL} with its corresponding
 * {@link Settings.EXTRA_BIOMETRIC_MINIMUM_STRENGTH_REQUIRED} enrolls a
 * {@link BiometricManager.Authenticators.BIOMETRIC_STRONG} authenticator. This can be done by
 * authenticating a {@link BiometricPrompt.CryptoObject}.
 */
public class BiometricStrongTests extends PassFailButtons.Activity {
    private static final String TAG = "BiometricStrongEnrollmentTest";
    private static final int REQUEST_ENROLL = 1;
    private static final String KEY_NAME_STRONGBOX = "key_using_strongbox";
    private static final String KEY_NAME_NO_STRONGBOX = "key_without_strongbox";
    private static final byte[] SECRET_BYTE_ARRAY = new byte[] {1, 2, 3, 4, 5, 6};

    private boolean mHasStrongBox;
    private BiometricManager mBiometricManager;
    private Button mCheckAndEnrollButton;
    private Button mAuthenticateWithoutStrongBoxButton;
    private Button mAuthenticateWithStrongBoxButton;
    private Button mAuthenticateUIButton;
    private Button mKeyInvalidatedButton;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Executor mExecutor = mHandler::post;

    private boolean mAuthenticateWithoutStrongBoxPassed;
    private boolean mAuthenticateWithStrongBoxPassed;
    private boolean mAuthenticateUIPassed;
    private boolean mKeyInvalidatedStrongboxPassed;
    private boolean mKeyInvalidatedNoStrongboxPassed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.biometric_test_strong_tests);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mBiometricManager = getSystemService(BiometricManager.class);

        mCheckAndEnrollButton = findViewById(R.id.check_and_enroll_button);
        mAuthenticateWithoutStrongBoxButton = findViewById(R.id.authenticate_no_strongbox_button);
        mAuthenticateWithStrongBoxButton = findViewById(R.id.authenticate_strongbox_button);
        mAuthenticateUIButton = findViewById(R.id.authenticate_ui_button);
        mKeyInvalidatedButton = findViewById(R.id.authenticate_key_invalidated_button);

        mHasStrongBox = getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);
        if (!mHasStrongBox) {
            Log.d(TAG, "Device does not support StrongBox");
            mAuthenticateWithStrongBoxButton.setVisibility(View.GONE);
            mAuthenticateWithStrongBoxPassed = true;
            mKeyInvalidatedStrongboxPassed = true;
        }

        mCheckAndEnrollButton.setOnClickListener((view) -> {
            // Check that no biometrics (of any strength) are enrolled
            int result = mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK);
            if (result == BiometricManager.BIOMETRIC_SUCCESS) {
                showToastAndLog("Please ensure that all biometrics are removed before starting"
                        + " this test");
                return;
            }

            result = mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG);
            if (result == BiometricManager.BIOMETRIC_SUCCESS) {
                showToastAndLog("Please ensure that all biometrics are removed before starting"
                        + " this test");
                return;
            } else if (result == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                boolean configHasStrong = false;
                final String config[] = getResources()
                        .getStringArray(com.android.internal.R.array.config_biometric_sensors);
                for (String s : config) {
                    String[] elems = s.split(":");
                    final int strength = Integer.parseInt(elems[2]);
                    if (strength == Authenticators.BIOMETRIC_STRONG) {
                        configHasStrong = true;
                        break;
                    }
                }

                if (configHasStrong) {
                    showToastAndLog("Your configuration contains a strong biometric, but"
                            + " is inconsistent with BiometricManager. Config: " + config);
                } else {
                    showToastAndLog("This device does not have a strong biometric sensor,"
                            + " you may pass this test");
                    getPassButton().setEnabled(true);
                }

                return;
            }

            final Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
            enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_MINIMUM_STRENGTH_REQUIRED,
                    Authenticators.BIOMETRIC_STRONG);

            startActivityForResult(enrollIntent, REQUEST_ENROLL);
        });

        mAuthenticateWithoutStrongBoxButton.setOnClickListener((view) -> {
            testBiometricBoundEncryption(KEY_NAME_NO_STRONGBOX, SECRET_BYTE_ARRAY,
                    false /* useStrongBox */);
        });

        mAuthenticateWithStrongBoxButton.setOnClickListener((view) -> {
            testBiometricBoundEncryption(KEY_NAME_STRONGBOX, SECRET_BYTE_ARRAY,
                    true /* useStrongBox */);
        });

        mAuthenticateUIButton.setOnClickListener((view) -> {
            Utils.showInstructionDialog(this,
                    R.string.biometric_test_ui_instruction_dialog_title,
                    R.string.biometric_test_ui_instruction_dialog_contents,
                    R.string.biometric_test_ui_instruction_dialog_continue,
                    (dialog, which) -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Generate random numbers to ensure that the implementation supports all the
                    // fields settable by the public API
                    Utils.VerifyRandomContents contents = new Utils.VerifyRandomContents(this) {
                        @Override
                        void onVerificationSucceeded() {
                            mAuthenticateUIPassed = true;
                            mAuthenticateUIButton.setEnabled(false);
                            updatePassButton();
                        }
                    };


                    // Create the BiometricPrompt with the above random numbers
                    final BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this);
                    builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG);
                    builder.setTitle("Title: " + contents.mRandomTitle);
                    builder.setSubtitle("Subtitle: " + contents.mRandomSubtitle);
                    builder.setDescription("Description: " + contents.mRandomDescription);
                    builder.setNegativeButton("Negative Button: "
                                    + contents.mRandomNegativeButtonText, mExecutor,
                            (dialog1, which1) -> {
                                // Ignore
                            });
                    final BiometricPrompt prompt = builder.build();

                    // When authentication succeeds, check that the values entered by the tester
                    // match the generated values.
                    prompt.authenticate(new CancellationSignal(), mExecutor,
                            new AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(AuthenticationResult result) {
                            final int authenticationType = result.getAuthenticationType();
                            if (authenticationType
                                    != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                                showToastAndLog("Unexpected authenticationType: "
                                        + authenticationType);
                                return;
                            }

                            Utils.showUIVerificationDialog(BiometricStrongTests.this,
                                    R.string.biometric_test_ui_verification_dialog_title,
                                    R.string.biometric_test_ui_verification_dialog_check,
                                    contents);
                        }
                    });
                }
            });
        });

        mKeyInvalidatedButton.setOnClickListener((view) -> {
            Utils.showInstructionDialog(this,
                    R.string.biometric_test_strong_authenticate_invalidated_instruction_title,
                    R.string.biometric_test_strong_authenticate_invalidated_instruction_contents,
                    R.string.biometric_test_strong_authenticate_invalidated_instruction_continue,
                    (dialog, which) -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // If the device supports StrongBox, check that this key is invalidated.
                    if (mHasStrongBox)
                        if (isKeyInvalidated(KEY_NAME_STRONGBOX)) {
                            mKeyInvalidatedStrongboxPassed = true;
                        } else {
                            showToastAndLog("StrongBox key not invalidated");
                            return;
                        }
                    }

                // Always check that non-StrongBox keys are invalidated.
                if (isKeyInvalidated(KEY_NAME_NO_STRONGBOX)) {
                    mKeyInvalidatedNoStrongboxPassed = true;
                } else {
                    showToastAndLog("Key not invalidated");
                    return;
                }

                mKeyInvalidatedButton.setEnabled(false);
                updatePassButton();
            });
        });
    }

    private boolean isKeyInvalidated(String keyName) {
        try {
            Utils.initCipher(KEY_NAME_STRONGBOX);
        } catch (KeyPermanentlyInvalidatedException e) {
            return true;
        } catch (Exception e) {
            showToastAndLog("Unexpected exception: " + e);
        }
        return false;
    }

    private void testBiometricBoundEncryption(String keyName, byte[] secret, boolean useStrongBox) {
        try {
            // Create the biometric-bound key
            Utils.createBiometricBoundKey(keyName, useStrongBox);

            // Initialize a cipher and try to use it before a biometric has been authenticated
            Cipher tryUseBeforeAuthCipher = Utils.initCipher(keyName);

            try {
                byte[] encrypted = Utils.doEncrypt(tryUseBeforeAuthCipher, secret);
                showToastAndLog("Should not be able to encrypt prior to authenticating: "
                        + encrypted);
                return;
            } catch (BadPaddingException | IllegalBlockSizeException
                    | UserNotAuthenticatedException | KeyPermanentlyInvalidatedException e) {
                // Normal, user has not authenticated yet
                Log.d(TAG, "Execption before authentication has occurred: " + e);
            }

            // Initialize a cipher and try to use it after a biometric has been authenticated
            final Cipher tryUseAfterAuthCipher = Utils.initCipher(keyName);
            CryptoObject crypto = new CryptoObject(tryUseAfterAuthCipher);

            final BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this);
            builder.setTitle("Please authenticate");
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG);
            builder.setNegativeButton("Cancel", mExecutor, (dialog, which) -> {
                // Do nothing
            });
            final BiometricPrompt prompt = builder.build();
            prompt.authenticate(crypto, new CancellationSignal(), mExecutor,
                    new AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(AuthenticationResult result) {
                            try {
                                final int authenticationType = result.getAuthenticationType();
                                if (authenticationType
                                        != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                                    showToastAndLog("Unexpected authenticationType: "
                                            + authenticationType);
                                    return;
                                }

                                byte[] encrypted = Utils.doEncrypt(tryUseAfterAuthCipher,
                                        secret);
                                showToastAndLog("Encrypted payload: " + encrypted
                                        + ", please run the next test");
                                if (useStrongBox) {
                                    mAuthenticateWithStrongBoxPassed = true;
                                    mAuthenticateWithStrongBoxButton.setEnabled(false);
                                } else {
                                    mAuthenticateWithoutStrongBoxPassed = true;
                                    mAuthenticateWithoutStrongBoxButton.setEnabled(false);
                                }
                                updatePassButton();
                            } catch (Exception e) {
                                showToastAndLog("Failed to encrypt after biometric was"
                                        + "authenticated: " + e);
                            }
                        }
                    });
        } catch (Exception e) {
            showToastAndLog("Failed during Crypto test: " + e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENROLL) {
            final int biometricStatus = mBiometricManager
                    .canAuthenticate(Authenticators.BIOMETRIC_STRONG);
            if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                showToastAndLog("Successfully enrolled, please press the authenticate button");
                mCheckAndEnrollButton.setEnabled(false);
                mAuthenticateWithoutStrongBoxButton.setEnabled(true);
                mAuthenticateWithStrongBoxButton.setEnabled(true);
                mAuthenticateUIButton.setEnabled(true);
            } else {
                showToastAndLog("Unexpected result after enrollment: " + biometricStatus);
            }
        }
    }

    private void updatePassButton() {
        if (mAuthenticateWithoutStrongBoxPassed && mAuthenticateWithStrongBoxPassed
                && mAuthenticateUIPassed) {
            if (!mKeyInvalidatedStrongboxPassed || !mKeyInvalidatedNoStrongboxPassed) {
                mKeyInvalidatedButton.setEnabled(true);
            }

            if (mKeyInvalidatedStrongboxPassed && mKeyInvalidatedNoStrongboxPassed) {
                showToastAndLog("All tests passed");
                // All tests passed
                getPassButton().setEnabled(true);
            }
        }
    }

    private void showToastAndLog(String s) {
        Log.d(TAG, s);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
