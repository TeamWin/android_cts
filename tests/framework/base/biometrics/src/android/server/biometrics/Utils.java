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

package android.server.biometrics;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.ComponentName;
import android.hardware.biometrics.BiometricPrompt;
import android.os.ParcelFileDescriptor;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class Utils {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

    /**
     * Runs a shell command, similar to running "adb shell ..." from the command line.
     * @param cmd A command, without the preceding "adb shell" portion. For example,
     *            passing in "dumpsys fingerprint" would be the equivalent of running
     *            "adb shell dumpsys fingerprint" from the command line.
     * @return The result of the command.
     */
    public static byte[] executeShellCommand(String cmd) {
        try {
            ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                    .executeShellCommand(cmd);
            byte[] buf = new byte[512];
            int bytesRead;
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                stdout.write(buf, 0, bytesRead);
            }
            fis.close();
            return stdout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void forceStopActivity(ComponentName componentName) {
        executeShellCommand("am force-stop " + componentName.getPackageName()
                + " " + componentName.getShortClassName().replaceAll("\\.", ""));
    }

    public static int numberOfSpecifiedOperations(@NonNull BiometricServiceState state,
            int sensorId, int operation) {
        int count = 0;
        final List<Integer> recentOps = state.mSensorStates.sensorStates.get(sensorId)
                .getSchedulerState().getRecentOperations();
        for (Integer i : recentOps) {
            if (i == operation) {
                count++;
            }
        }
        return count;
    }

    public static void generateBiometricBoundKey(String keyName) throws Exception {
        final KeyStore keystore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keystore.load(null);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);

        KeyGenerator keyGenerator = KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        keyGenerator.init(builder.build());

        // Generates and stores the key in Android KeyStore under the keystoreAlias (keyName)
        // specified in the builder.
        keyGenerator.generateKey();
    }

    public static BiometricPrompt.CryptoObject initializeCryptoObject(String keyName)
            throws Exception {
        final KeyStore keystore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keystore.load(null);
        final SecretKey secretKey = (SecretKey) keystore.getKey(
                keyName, null /* password */);
        final Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        final BiometricPrompt.CryptoObject cryptoObject =
                new BiometricPrompt.CryptoObject(cipher);
        return cryptoObject;
    }
}
