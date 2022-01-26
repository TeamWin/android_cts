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
package android.uidmigration.cts;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class DataProvider extends ContentProvider {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String RESULT_KEY = "result";

    private SharedPreferences mPrefs;

    @Override
    public boolean onCreate() {
        mPrefs = getContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return true;
    }

    // The first time this method is called, it returns a new random UUID and stores it in prefs.
    // After an upgrade, if data migration works properly, it returns the previously generated UUID.
    // The tester app asserts that the UUIDs returned before/after the upgrade to be the same.
    private Bundle checkData() {
        final String prefsKey = "uuid";

        Bundle data = new Bundle();
        String uuid = mPrefs.getString(prefsKey, null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            mPrefs.edit().putString(prefsKey, uuid).commit();
        }
        data.putString(RESULT_KEY, uuid);
        return data;
    }

    // Generate new AES secret key and encrypt arg.
    private Bundle encryptAES(String arg) {
        final String keyAlias = "aes";

        Bundle result = new Bundle();

        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    keyAlias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setKeySize(256)
                    .build();
            keyGen.init(spec);
            SecretKey key = keyGen.generateKey();

            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(arg.getBytes(UTF_8));
            byte[] iv = cipher.getIV();
            result.putByteArray(RESULT_KEY, enc);
            result.putByteArray("iv", iv);
        } catch (Exception e) {
            Log.e("DataTestApp", "Crypto error", e);
        }
        return result;
    }

    // Decrypt provided data with iv + key in keystore.
    private Bundle decryptAES(Bundle extra) {
        final String keyAlias = "aes";

        Bundle result = new Bundle();

        byte[] enc = extra.getByteArray(RESULT_KEY);
        byte[] iv = extra.getByteArray("iv");
        if (enc == null || iv == null) return result;

        try {
            final KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);

            // Fetch the existing key in keystore.
            KeyStore.SecretKeyEntry entry =
                    (KeyStore.SecretKeyEntry) ks.getEntry(keyAlias, null);
            SecretKey key = entry.getSecretKey();

            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            final IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
            byte[] dec = cipher.doFinal(enc);
            result.putString(RESULT_KEY, new String(dec, UTF_8));
        } catch (Exception e) {
            Log.e("DataTestApp", "Crypto error", e);
        }

        return result;
    }

    private byte[] ecCertChainDigest(KeyStore ks) throws GeneralSecurityException {
        final String keyAlias = "ec";

        final Certificate[] certs = ks.getCertificateChain(keyAlias);
        if (certs == null) return null;
        MessageDigest digest = MessageDigest.getInstance("SHA256");
        for (Certificate cert : certs) {
            digest.update(cert.getEncoded());
        }
        return digest.digest();
    }

    // Generates a new EC keypair in keystore, and return the signed signature data.
    private Bundle signEC(String args) {
        final String keyAlias = "ec";

        Bundle result = new Bundle();
        byte[] data = args.getBytes(UTF_8);

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    keyAlias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge(UUID.randomUUID().toString().getBytes(UTF_8))
                    .build();
            kpg.initialize(spec);
            KeyPair kp = kpg.generateKeyPair();
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(kp.getPrivate());
            s.update(data);
            result.putByteArray(RESULT_KEY, s.sign());

            final KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            result.putByteArray("certChain", ecCertChainDigest(ks));
        } catch (Exception e) {
            Log.e("DataTestApp", "Crypto error", e);
        }

        return result;
    }

    // Fetch the previously generated EC keypair and returns the certificate.
    private Bundle getECCert() {
        final String keyAlias = "ec";
        Bundle result = new Bundle();

        try {
            final KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);

            // Fetch the existing key in keystore.
            KeyStore.PrivateKeyEntry entry =
                    (KeyStore.PrivateKeyEntry) ks.getEntry(keyAlias, null);
            if (entry != null) {
                result.putByteArray(RESULT_KEY, entry.getCertificate().getEncoded());
                result.putByteArray("certChain", ecCertChainDigest(ks));
            }
        } catch (Exception e) {
            Log.e("DataTestApp", "Crypto error", e);
        }
        return result;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        switch (method) {
            case "data":
                return checkData();
            case "encryptAES":
                return encryptAES(arg);
            case "decryptAES":
                return decryptAES(extras);
            case "signEC":
                return signEC(arg);
            case "getECCert":
                return getECCert();
            default:
                return new Bundle();
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
