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

package android.hardware.biometrics.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Basic test cases for BiometricManager. See the manual biometric tests in CtsVerifier for a more
 * comprehensive test suite.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class BiometricManagerTest {
    private Context mContext;
    private BiometricManager mBiometricManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mBiometricManager = mContext.getSystemService(BiometricManager.class);
    }

    @Test
    public void test_canAuthenticate() {

        assertNotEquals("Device should not have any biometrics enrolled",
                mBiometricManager.canAuthenticate(), BiometricManager.BIOMETRIC_SUCCESS);

        assertNotEquals("Device should not have any biometrics enrolled",
                mBiometricManager.canAuthenticate(
                        Authenticators.DEVICE_CREDENTIAL | Authenticators.BIOMETRIC_WEAK),
                BiometricManager.BIOMETRIC_SUCCESS);
    }

    @Test
    public void test_getButtonLabel_isDifferentForBiometricAndCredential() {
        // Ensure labels for biometrics and credential are different (if non-empty).
        final CharSequence biometricLabel =
                mBiometricManager.getButtonLabel(Authenticators.BIOMETRIC_WEAK);
        final CharSequence credentialLabel =
                mBiometricManager.getButtonLabel(Authenticators.DEVICE_CREDENTIAL);
        if (!TextUtils.isEmpty(biometricLabel) || !TextUtils.isEmpty(credentialLabel)) {
            assertFalse("Biometric and credential button labels should not match",
                    TextUtils.equals(biometricLabel, credentialLabel));
        }
    }

    @Test
    public void test_getButtonLabel_isNonEmptyIfPresentForSubAuthType() {
        // Ensure label for biometrics|credential is non-empty if one for biometrics or credential
        // (or both) is non-empty.
        final CharSequence biometricOrCredentialLabel =
                mBiometricManager.getButtonLabel(
                        Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL);
        final CharSequence biometricLabel =
                mBiometricManager.getButtonLabel(Authenticators.BIOMETRIC_WEAK);
        final CharSequence credentialLabel =
                mBiometricManager.getButtonLabel(Authenticators.DEVICE_CREDENTIAL);
        final boolean isLabelPresentForSubAuthType =
                !TextUtils.isEmpty(biometricLabel) || !TextUtils.isEmpty(credentialLabel);
        assertFalse("Label should not be empty if one for an authenticator sub-type is non-empty",
                TextUtils.isEmpty(biometricOrCredentialLabel) && isLabelPresentForSubAuthType);
    }

    @Test
    public void test_getPromptMessage_isDifferentForDifferentAuthenticators() {
        // Ensure messages for biometrics, credential, and biometrics|credential are different.
        final CharSequence biometricMessage =
                mBiometricManager.getPromptMessage(Authenticators.BIOMETRIC_WEAK);
        final CharSequence credentialMessage =
                mBiometricManager.getPromptMessage(Authenticators.DEVICE_CREDENTIAL);
        final CharSequence biometricOrCredentialMessage =
                mBiometricManager.getPromptMessage(
                        Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL);
        if (!TextUtils.isEmpty(biometricMessage)
                || !TextUtils.isEmpty(credentialMessage)
                || !TextUtils.isEmpty(biometricOrCredentialMessage)) {
            assertFalse("Biometric and credential prompt messages should not match",
                    TextUtils.equals(biometricMessage, credentialMessage));
            assertFalse("Biometric and biometric|credential prompt messages should not match",
                    TextUtils.equals(biometricMessage, biometricOrCredentialMessage));
            assertFalse("Credential and biometric|credential prompt messages should not match",
                    TextUtils.equals(credentialMessage, biometricOrCredentialMessage));
        }
    }

    @Test
    public void test_getPromptMessage_isNonEmptyIfPresentForSubAuthType() {
        // Ensure message for biometrics|credential is non-empty if one for biometrics or credential
        // (or both) is non-empty.
        final CharSequence biometricOrCredentialMessage =
                mBiometricManager.getPromptMessage(
                        Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL);
        final CharSequence biometricMessage =
                mBiometricManager.getPromptMessage(Authenticators.BIOMETRIC_WEAK);
        final CharSequence credentialMessage =
                mBiometricManager.getPromptMessage(Authenticators.DEVICE_CREDENTIAL);
        final boolean isMessagePresentForSubAuthType =
                !TextUtils.isEmpty(biometricMessage) || !TextUtils.isEmpty(credentialMessage);
        assertFalse("Message should not be empty if one for an authenticator sub-type is non-empty",
                TextUtils.isEmpty(biometricOrCredentialMessage) && isMessagePresentForSubAuthType);
    }

    @Test
    public void test_getSettingName_forBiometrics() {
        final PackageManager pm = mContext.getPackageManager();
        final boolean hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        final boolean hasIris = pm.hasSystemFeature(PackageManager.FEATURE_IRIS);
        final boolean hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);
        assumeTrue("Test requires biometric hardware", hasFingerprint || hasIris || hasFace);

        // Ensure biometric setting name is non-empty if device supports biometrics.
        assertFalse("Name should be non-empty if device supports biometric authentication",
                TextUtils.isEmpty(mBiometricManager.getSettingName(Authenticators.BIOMETRIC_WEAK)));
        assertFalse("Name should be non-empty if device supports biometric authentication",
                TextUtils.isEmpty(mBiometricManager.getSettingName(
                        Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL)));
    }

    @Test
    public void test_getSettingName_forCredential() {
        final KeyguardManager km = mContext.getSystemService(KeyguardManager.class);
        assumeTrue("Test requires KeyguardManager", km != null);

        // Ensure credential setting name is non-empty if device supports PIN/pattern/password.
        assertFalse("Name should be non-empty if device supports PIN/pattern/password",
                TextUtils.isEmpty(mBiometricManager.getSettingName(
                        Authenticators.DEVICE_CREDENTIAL)));
        assertFalse("Name should be non-empty if device supports PIN/pattern/password",
                TextUtils.isEmpty(mBiometricManager.getSettingName(
                        Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL)));
    }
}
