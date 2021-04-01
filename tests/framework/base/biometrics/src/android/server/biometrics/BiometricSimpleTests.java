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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.platform.test.annotations.Presubmit;

import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.Test;

/**
 * Simple tests.
 */
@Presubmit
public class BiometricSimpleTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Simple";

    @Test
    public void testEnroll() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId())){
                enrollForSensor(session, prop.getSensorId());
            }
        }
    }

    @Test
    public void testSensorPropertiesAndDumpsysMatch() throws Exception {
        final BiometricServiceState state = getCurrentState();

        assertEquals(mSensorProperties.size(), state.mSensorStates.sensorStates.size());
        for (SensorProperties prop : mSensorProperties) {
            assertTrue(state.mSensorStates.sensorStates.containsKey(prop.getSensorId()));
        }
    }

    @Test
    public void testPackageManagerAndDumpsysMatch() throws Exception {
        final BiometricServiceState state = getCurrentState();

        final PackageManager pm = mContext.getPackageManager();

        assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                state.mSensorStates.containsModality(SensorStateProto.FINGERPRINT));
        assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FACE),
                state.mSensorStates.containsModality(SensorStateProto.FACE));
        assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_IRIS),
                state.mSensorStates.containsModality(SensorStateProto.IRIS));
    }

    /**
     * When device credential is not enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2) BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 3) @deprecated BiometricPrompt#setDeviceCredentialAllowed(true)
     */
    @Test
    public void testWhenCredentialNotEnrolled() throws Exception {
        // First case above
        final int result = mBiometricManager.canAuthenticate(BiometricManager
                .Authenticators.DEVICE_CREDENTIAL);
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, result);

        // Second case above
        BiometricPrompt.AuthenticationCallback callback =
                mock(BiometricPrompt.AuthenticationCallback.class);
        showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL),
                any());

        // Third case above. Since the deprecated API is intended to allow credential in addition
        // to biometrics, we should be receiving BIOMETRIC_ERROR_NO_BIOMETRICS.
        callback = mock(BiometricPrompt.AuthenticationCallback.class);
        showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS),
                any());
    }

    /**
     * When device credential is enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2a) Successfully authenticating BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 2b) Cancelling authentication for the above
     * 3a) @deprecated BiometricPrompt#setDeviceCredentialALlowed(true)
     * 3b) Cancelling authentication for the above
     * 4) Cancelling auth for options 2) and 3)
     */
    @Test
    public void testWhenCredentialEnrolled() throws Exception {
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            // First case above
            final int result = mBiometricManager.canAuthenticate(BiometricManager
                    .Authenticators.DEVICE_CREDENTIAL);
            assertEquals(BiometricManager.BIOMETRIC_SUCCESS, result);

            // 2a above
            BiometricPrompt.AuthenticationCallback callback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 2b above
            CancellationSignal cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());

            // 3a above
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 3b above
            cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());
        }
    }
}
