/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.keystore.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.spec.ECGenParameterSpec;

@RunWith(AndroidJUnit4.class)
public class Curve25519Test {
    private void deleteEntry(String entry) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.deleteEntry(entry);
        } catch (KeyStoreException e) {
            // Skipped
        }
    }

    @Test
    public void x25519KeyAgreementTest() throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        final String alias = "x25519-alias";
        deleteEntry(alias);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias,
                        KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("x25519")).build();
        kpg.initialize(keySpec);

        //TODO(b/214203951): Remove this try/catch once Conscrypt class are available.
        try {
            kpg.generateKeyPair();
            fail("Should not be supported yet");
        } catch (ProviderException e) {
            assertThat(e.getMessage()).isEqualTo("Curve XDH not supported yet");
        }
    }

    @Test
    public void ed25519KeyGenerationAndSigningTest()
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        final String alias = "ed25519-alias";
        deleteEntry(alias);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("ed25519")).build();
        kpg.initialize(keySpec);

        //TODO(b/214203951): Remove this try/catch once Conscrypt class are available.
        try {
            kpg.generateKeyPair();
            fail("Should not be supported yet");
        } catch (ProviderException e) {
            assertThat(e.getMessage()).isEqualTo("Curve 1.3.101.112 not supported yet");
        }
    }

    @Test
    public void testX25519CannotBeUsedForSigning()
            throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        final String alias = "x25519-baduse-alias";
        deleteEntry(alias);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("x25519")).build();

        assertThrows(InvalidAlgorithmParameterException.class, () -> kpg.initialize(keySpec));
    }

    @Test
    public void testEd25519CannotBeUsedForKeyExchange() throws NoSuchAlgorithmException,
            NoSuchProviderException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        final String alias = "ed25519-baduse-alias";
        deleteEntry(alias);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("ed25519")).build();

        assertThrows(InvalidAlgorithmParameterException.class, () -> kpg.initialize(keySpec));
    }
}