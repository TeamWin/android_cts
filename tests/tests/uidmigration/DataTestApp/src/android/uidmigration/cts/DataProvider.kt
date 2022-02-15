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
package android.uidmigration.cts

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

class DataProvider : BaseProvider() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RESULT_KEY = "result"
    }

    private lateinit var mPrefs: SharedPreferences

    override fun onCreate(): Boolean {
        mPrefs = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return true
    }

    // The first time this method is called, it returns a new random UUID and stores it in prefs.
    // After an upgrade, if data migration works properly, it returns the previously generated UUID.
    // The tester app asserts that the UUIDs returned before/after the upgrade to be the same.
    private fun checkData(): Bundle {
        val prefsKey = "uuid"
        val data = Bundle()
        val uuid = mPrefs.getString(prefsKey, null) ?: UUID.randomUUID().toString().also {
            mPrefs.edit().putString(prefsKey, it).commit()
        }
        data.putString(RESULT_KEY, uuid)
        return data
    }

    // Generate new AES secret key and encrypt arg.
    private fun encryptAES(arg: String): Bundle {
        val keyAlias = "aes"
        val result = Bundle()
        try {
            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            val key = keyGen.generateKey()
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val enc = cipher.doFinal(arg.toByteArray(UTF_8))
            val iv = cipher.iv
            result.putByteArray(RESULT_KEY, enc)
            result.putByteArray("iv", iv)
        } catch (e: Exception) {
            Log.e("DataTestApp", "Crypto error", e)
        }
        return result
    }

    // Decrypt provided data with iv + key in keystore.
    private fun decryptAES(extra: Bundle): Bundle {
        val keyAlias = "aes"
        val result = Bundle()
        val enc = extra.getByteArray(RESULT_KEY)
        val iv = extra.getByteArray("iv")
        if (enc == null || iv == null) return result
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)

            // Fetch the existing key in keystore.
            val entry = ks.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
            val key = entry.secretKey
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            val dec = cipher.doFinal(enc)
            result.putString(RESULT_KEY, String(dec, UTF_8))
        } catch (e: Exception) {
            Log.e("DataTestApp", "Crypto error", e)
        }
        return result
    }

    @Throws(GeneralSecurityException::class)
    private fun ecCertChainDigest(ks: KeyStore): ByteArray? {
        val keyAlias = "ec"
        val certs = ks.getCertificateChain(keyAlias) ?: return null
        val digest = MessageDigest.getInstance("SHA256")
        for (cert in certs) {
            digest.update(cert.encoded)
        }
        return digest.digest()
    }

    // Generates a new EC keypair in keystore, and return the signed signature data.
    private fun signEC(args: String): Bundle {
        val keyAlias = "ec"
        val result = Bundle()
        val data = args.toByteArray(UTF_8)
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(UUID.randomUUID().toString().toByteArray(UTF_8))
                .build()
            kpg.initialize(spec)
            val kp = kpg.generateKeyPair()
            val s = Signature.getInstance("SHA256withECDSA")
            s.initSign(kp.private)
            s.update(data)
            result.putByteArray(RESULT_KEY, s.sign())
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            result.putByteArray("certChain", ecCertChainDigest(ks))
        } catch (e: Exception) {
            Log.e("DataTestApp", "Crypto error", e)
        }
        return result
    }

    // Fetch the previously generated EC keypair and returns the certificate.
    private fun getEcCert(): Bundle {
        val keyAlias = "ec"
        val result = Bundle()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)

            // Fetch the existing key in keystore.
            val entry = ks.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
            result.putByteArray(RESULT_KEY, entry.certificate.encoded)
            result.putByteArray("certChain", ecCertChainDigest(ks))
        } catch (e: Exception) {
            Log.e("DataTestApp", "Crypto error", e)
        }
        return result
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return when (method) {
            "data" -> checkData()
            "encryptAES" -> encryptAES(arg!!)
            "decryptAES" -> decryptAES(extras!!)
            "signEC" -> signEC(arg!!)
            "getECCert" -> getEcCert()
            else -> Bundle()
        }
    }
}