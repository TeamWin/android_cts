/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.biometrics;

import static org.junit.Assert.assertThrows;

import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import org.junit.Test;

import java.security.InvalidAlgorithmParameterException;

/**
 * Tests for cryptographic/keystore related functionality.
 */
@Presubmit
public class BiometricCryptoTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Crypto";

    @Test
    public void testGenerateKeyWithoutDeviceCredential_throwsException() {
        assertThrows("Key shouldn't be generatable before device credentials are enrolled",
                Exception.class,
                () -> Utils.generateBiometricBoundKey("keyBeforeCredentialEnrolled"));
    }

    @Test
    public void testGenerateKeyWithoutBiometricEnrolled_throwsInvalidAlgorithmParameterException()
            throws Exception {
        try (CredentialSession session = new CredentialSession()){
            session.setCredential();
            assertThrows("Key shouldn't be generatable before biometrics are enrolled",
                    InvalidAlgorithmParameterException.class,
                    () -> Utils.generateBiometricBoundKey("keyBeforeBiometricEnrolled"));
        }
    }

    @Test
    public void testGenerateKeyWhenCredentialAndBiometricEnrolled() throws Exception {
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            for (SensorProperties prop : mSensorProperties) {
                final String keyName = "key" + prop.getSensorId();
                Log.d(TAG, "Testing sensor: " + prop + ", key name: " + keyName);

                try (BiometricTestSession session =
                             mBiometricManager.createTestSession(prop.getSensorId())) {
                    waitForAllUnenrolled();
                    enrollForSensor(session, prop.getSensorId());
                    if (prop.getSensorStrength() == SensorProperties.STRENGTH_STRONG) {
                        Utils.generateBiometricBoundKey(keyName);
                        // We can test initializing the key, which in this case is a Cipher.
                        // However, authenticating it and using it is not testable, since that
                        // requires a real authentication from the TEE or equivalent.
                        BiometricPrompt.CryptoObject crypto = Utils.initializeCryptoObject(keyName);
                    } else {
                        assertThrows("Key shouldn't be generatable with non-strong biometrics",
                                InvalidAlgorithmParameterException.class,
                                () -> Utils.generateBiometricBoundKey(keyName));
                    }
                }
            }
        }
    }
}
