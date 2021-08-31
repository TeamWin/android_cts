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

package android.devicepolicy.cts;

import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.security.KeyChain;
import android.security.KeyChainException;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PositivePolicyTest;
import com.android.bedstead.harrier.policies.KeyManagement;
import com.android.compatibility.common.util.BlockingCallback;
import com.android.compatibility.common.util.FakeKeys;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(BedsteadJUnit4.class)
public class KeyManagementTest {

    private static final String RSA = "RSA";
    private static final String RSA_ALIAS = "com.android.test.valid-rsa-key-1";
    private static final PrivateKey PRIVATE_KEY =
            generatePrivateKey(FakeKeys.FAKE_RSA_1.privateKey, RSA);
    private static final Certificate CERTIFICATE =
            generateCertificate(FakeKeys.FAKE_RSA_1.caCertificate);

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void installKeyPair_validRsaKeyPair_success() throws Exception {
        try {
            // Install keypair
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(DPC_COMPONENT_NAME, PRIVATE_KEY, CERTIFICATE,
                            RSA_ALIAS)).isTrue();
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager().removeKeyPair(DPC_COMPONENT_NAME, RSA_ALIAS);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void removeKeyPair_validRsaKeyPair_success() throws Exception {
        try {
            // Install keypair
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(DPC_COMPONENT_NAME, PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);
        } finally {
            // Remove keypair
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(DPC_COMPONENT_NAME, RSA_ALIAS)).isTrue();
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = KeyManagement.class)
    public void choosePrivateKeyAlias_aliasIsSelectedByAdmin_returnAlias() throws Exception {
        try {
            // Install keypair
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(DPC_COMPONENT_NAME, PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);
            KeyChainAliasCallback callback = new KeyChainAliasCallback();

            choosePrivateKeyAlias(callback, RSA_ALIAS);

            assertThat(callback.await()).isEqualTo(RSA_ALIAS);
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager().removeKeyPair(DPC_COMPONENT_NAME, RSA_ALIAS);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = KeyManagement.class)
    public void getPrivateKey_aliasIsGranted_returnPrivateKey() throws Exception {
        try {
            // Install keypair
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(DPC_COMPONENT_NAME, PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);
            // Grant alias via {@code KeyChain.choosePrivateKeyAlias}
            KeyChainAliasCallback callback = new KeyChainAliasCallback();
            choosePrivateKeyAlias(callback, RSA_ALIAS);
            callback.await();

            // TODO(b/198297904): Allow runWithContext to run off the main thread
            ActivityContext.runWithContext((activity) -> mExecutor.execute(() -> {
                // Get private key for the granted alias
                final PrivateKey privateKey = getPrivateKey(activity, RSA_ALIAS);

                mHandler.post(() -> {
                    assertThat(privateKey).isNotNull();
                    assertThat(privateKey.getAlgorithm()).isEqualTo(RSA);
                });
            }));
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager().removeKeyPair(DPC_COMPONENT_NAME, RSA_ALIAS);
        }
    }

    private static class KeyChainAliasCallback extends BlockingCallback<String> implements
            android.security.KeyChainAliasCallback {

        @Override
        public void alias(final String chosenAlias) {
            callbackTriggered(chosenAlias);
        }
    }

    private static Uri getUri(String alias) {
        try {
            return Uri.parse("https://example.org/?alias=" + URLEncoder.encode(alias, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("Unable to parse URI." + e);
        }
    }

    private static void choosePrivateKeyAlias(KeyChainAliasCallback callback, String alias) {
        /* Pass the alias as a GET to an imaginary server instead of explicitly asking for it,
         * to make sure the DPC actually has to do some work to grant the cert.
         */
        try {
            ActivityContext.runWithContext(
                    (activity) -> KeyChain.choosePrivateKeyAlias(activity, callback, /* keyTypes= */
                            null, /* issuers= */ null, getUri(alias), /* alias = */ null)
            );
        } catch (InterruptedException e) {
            throw new AssertionError("Unable to choose private key alias." + e);
        }
    }

    private static PrivateKey getPrivateKey(Context context, String alias) {
        try {
            return KeyChain.getPrivateKey(context, alias);
        } catch (KeyChainException | InterruptedException e) {
            throw new AssertionError("Failed to get private key." + e);
        }
    }

    private static PrivateKey generatePrivateKey(final byte[] key, String type) {
        try {
            return KeyFactory.getInstance(type).generatePrivate(
                    new PKCS8EncodedKeySpec(key));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new AssertionError("Unable to get private key." + e);
        }
    }

    private static Certificate generateCertificate(byte[] cert) {
        try {
            return CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(cert));
        } catch (CertificateException e) {
            throw new AssertionError("Unable to get certificate." + e);
        }
    }
}
