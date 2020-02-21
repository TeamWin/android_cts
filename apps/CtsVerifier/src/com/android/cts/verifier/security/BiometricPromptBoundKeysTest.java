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
 * limitations under the License
 */

package com.android.cts.verifier.security;

import android.content.DialogInterface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricPrompt.CryptoObject;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.Button;

import com.android.cts.verifier.R;

import java.lang.Exception;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Test for {@link BiometricPrompt}. This test runs twice, once with confirmation required,
 * once without. Both tests use crypto objects.
 */
public class BiometricPromptBoundKeysTest extends FingerprintBoundKeysTest {

    private static final String TAG = "BiometricPromptBoundKeysTest";

    private static final int STATE_TEST_REQUIRE_CONFIRMATION = 1; // confirmation required
    private static final int STATE_TEST_NO_CONFIRMATION = 2; // no confirmation required

    private DialogCallback mDialogCallback;
    private BiometricPrompt mBiometricPrompt;
    private CancellationSignal mCancellationSignal;
    private BiometricManager mBiometricManager;
    private int mState = STATE_TEST_REQUIRE_CONFIRMATION;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Executor mExecutor = (runnable) -> {
        mHandler.post(runnable);
    };

    private final Runnable mNegativeButtonRunnable = () -> {
        showToast("Authentication canceled by user");
    };

    private class DialogCallback extends
            BiometricPrompt.AuthenticationCallback {
        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            showToast(errString.toString());
        }

        @Override
        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            if (tryEncrypt()) {
                if (mState == STATE_TEST_REQUIRE_CONFIRMATION) {
                    mState = STATE_TEST_NO_CONFIRMATION;
                    showToast("First test passed, run again to start the second test");
                    Button startButton = findViewById(R.id.sec_start_test_button);
                    startButton.setText("Run second test");
                } else if (mState == STATE_TEST_NO_CONFIRMATION) {
                    showToast("Test passed.");
                    getPassButton().setEnabled(true);
                }
            } else {
                showToast("Test failed. Key not accessible after auth");
            }
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    private CryptoObject generateDummyCrypto() {
        final String dummyKeyName = "dummy_key";

        try {
            KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
            keystore.load(null);
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");

            // This key should, or rather must not be biometric-bound, since we depend on the
            // operationId being non-zero to invoke the "STRONG" path of
            // BiometricManager#canAuthenticate(CryptoObject). See the comments below, where
            // the reflection is being checked/invoked for more details.
            keyGenerator.init(new KeyGenParameterSpec.Builder(dummyKeyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build());
            keyGenerator.generateKey();

            SecretKey secretKey = (SecretKey) keystore.getKey(dummyKeyName, null);
            Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return new CryptoObject(cipher);
        } catch (Exception e) {
            showToast("Exception while generating dummy key: " + e);
        }

        return null;
    }

    private Method getHiddenCanAuthenticateApiIfExists() {
        try {
            return BiometricManager.class.getMethod(
                    "canAuthenticate",
                    BiometricPrompt.CryptoObject.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private int invokeHiddenCanAuthenticateApi(BiometricManager manager, Method method,
            CryptoObject crypto) {
        try {
            final Object result = method.invoke(manager, crypto);
            if (result instanceof Integer) {
                return (int) result;
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            // Do nothing, assume the API does not exist since something is wrong.
        }
        return -1;
    }

    @Override
    protected void onPermissionsGranted() {
        Button startTestButton = findViewById(R.id.sec_start_test_button);
        mBiometricManager = getSystemService(BiometricManager.class);

        // The Android 10 CDD allows for integration of Strong, Weak, and Convenience
        // biometrics on the device. A subset (Strong, Weak) are allowed for application
        // integration (e.g. with the public APIs). However, the Android 10 API surface is not
        // wide enough to support this.
        //
        // Although this was addressed in Android 11 with Authenticators.BIOMETRIC_STRONG,
        // Authenticators.BIOMETRIC_WEAK, and the canAuthenticate(int) APIs etc, we still need
        // to patch the hole in Android 10. The POR is as follows.
        //
        // Devices with weak biometrics should implement a @hide API with the following
        // signature: `int BiometricManager#canAuthenticate(BiometricPrompt.CryptoObject)`.
        //
        // If invoked with a CryptoObject that's initialized with a proper KeyStore key (containing
        // a non-zero operationId), the returned result will be interpreted as "the current state
        // of the strong biometric sensor on the device. e.g.
        // 1) BIOMETRIC_SUCCESS will mean that a strong biometric sensor is available and enrolled,
        // 2) BIOMETRIC_ERROR_HW_UNAVAILABLE will mean that there is a problem with the strong
        //    biometric sensor (e.g. HAL crashed)
        // 3) BIOMETRIC_ERROR_NONE_ENROLLED will mean that a strong biometric sensor exists, but
        //    currently does not have any enrollments
        // 4) BIOMETRIC_ERROR_NO_HARDWARE will mean that the device does not have a strong biometric
        //    sensor.
        // In other words, on devices with only a WEAK biometric sensor, the method, when invoked
        // with a valid CryptoObject should return BIOMETRIC_ERROR_NO_HARDWARE.
        //
        // If invoked with a null CryptoObject, or a CryptoObject whos operationId is 0, the result
        // will be interpreted as "the current state of the weak biometric sensor on the device".
        // This is currently not needed, and not being tested. Since, the Android 11 API, e.g.
        // invoking BiometricManager#canAuthenticate(Authenticators.BIOMETRIC_WEAK) means "at
        // least one biometric sensor that meets or exceeds BIOMETRIC_WEAK is available+enrolled".
        // That information in Android 10's case can be derived from
        // BiometricManager#canAuthenticate() returning BIOMETRIC_SUCCESS and
        // BiometricManager#canAuthenticate(CryptoObject) existing/returning something (which is
        // what we're enforcing in this test here).
        //
        // To make this workaround available to developers, the androidx.biometric library will
        // bring Android11-like functionality to developers by similarly checking for the
        // existence/result of this @hide API.

        final int result = mBiometricManager.canAuthenticate();
        // At least oneof(weak OR strong) biometrics are available. We don't know exactly which
        // until we check for the hidden API
        if (result == BiometricManager.BIOMETRIC_SUCCESS) {
            Method canAuthenticateCrypto = getHiddenCanAuthenticateApiIfExists();
            if (canAuthenticateCrypto != null) {
                showToast("canAuthenticate(CryptoObject) detected, testing @hide API");
                // If the hidden API exists, we should use it (instead of
                // BiometricManager#canAuthenticate() to check for availability+enrolled state of
                // strong biometrics. In this case, BiometricManager#canAuthenticate() represents
                // WEAK (or stronger).

                // Override the behavior of the start button.
                startTestButton.setOnClickListener(view -> {
                    final int hiddenResult = invokeHiddenCanAuthenticateApi(mBiometricManager,
                            canAuthenticateCrypto, generateDummyCrypto());
                    boolean shouldStartCryptoTest = false;
                    switch (hiddenResult) {
                        case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                            showToast("Strong biometrics are available but not enrolled. Please"
                                    + " enroll a strong biometric");
                            break;

                        case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                            showToast("The device has WEAK biometrics but no STRONG biometrics."
                                    + " You can skip the keystore integration test.");
                            startTestButton.setEnabled(false);
                            getPassButton().setEnabled(true);
                            break;

                        case BiometricManager.BIOMETRIC_SUCCESS:
                            // Device reports that a Strong authenticator is available and enrolled.
                            // Test for Keystore integration in this case.
                            shouldStartCryptoTest = true;

                        case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                            // HAL/Hardware has an issue. This is definitely not expected. In this
                            // case, we should assume it should be in the enrolled/available state.
                            // Test for Keystore integration in this case.

                        default:
                            showToast("canAuthenticate(CryptoObject) result: " + hiddenResult
                                    + " Please proceed with the keystore integration test.");
                            break;
                    }

                    if (shouldStartCryptoTest) {
                        startTest();
                    }
                });
            } else {
                // Hidden API doesn't exist. BiometricManager#canAuthenticate() in this case
                // represents only STRONG biometrics. Start button has existing behavior inherited
                // from FingerprintBoundKeysTest.
            }
        } else if (result == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            showToast("No biometric features, test passed.");
            getPassButton().setEnabled(true);
        } else if (result == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            showToast("Biometric unavailable, something is wrong with your device");
        } else if (result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            showToast("Error: " + result + " Please ensure you have a biometric enrolled");
        } else {
            showToast("Unknown result: " + result);
        }
    }

    @Override
    protected void showAuthenticationScreen() {
        mCancellationSignal = new CancellationSignal();
        mDialogCallback = new DialogCallback();
        final boolean requireConfirmation = mState == STATE_TEST_REQUIRE_CONFIRMATION
                ? true : false;
        mBiometricPrompt = new BiometricPrompt.Builder(getApplicationContext())
                .setTitle("Authenticate with biometric")
                .setNegativeButton("Cancel", mExecutor,
                        (DialogInterface dialogInterface, int which) -> {
                            if (which == DialogInterface.BUTTON_NEGATIVE) {
                                mHandler.post(mNegativeButtonRunnable);
                            }
                        })
                .setConfirmationRequired(requireConfirmation)
                .build();
        mBiometricPrompt.authenticate(
                new BiometricPrompt
                .CryptoObject(getCipher()),
                mCancellationSignal, mExecutor, mDialogCallback);
    }

    @Override
    protected int getTitleRes() {
        return R.string.sec_biometric_prompt_bound_key_test;
    }

    @Override
    protected int getDescriptionRes() {
        return R.string.sec_biometric_prompt_bound_key_test_info;
    }
}
