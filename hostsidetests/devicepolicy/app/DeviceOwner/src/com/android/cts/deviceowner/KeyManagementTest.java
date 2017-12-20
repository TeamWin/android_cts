/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.deviceowner;

import static com.android.compatibility.common.util.FakeKeys.FAKE_RSA_1;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.security.AttestedKeyPair;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.test.ActivityInstrumentationTestCase2;

import com.android.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.org.bouncycastle.asn1.x500.X500Name;
import com.android.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import com.android.org.bouncycastle.cert.X509CertificateHolder;
import com.android.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

public class KeyManagementTest extends ActivityInstrumentationTestCase2<KeyManagementActivity> {

    private static final long KEYCHAIN_TIMEOUT_MINS = 6;
    private DevicePolicyManager mDevicePolicyManager;

    public KeyManagementTest() {
        super(KeyManagementActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Confirm our DeviceOwner is set up
        mDevicePolicyManager = (DevicePolicyManager)
                getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
        assertDeviceOwner(mDevicePolicyManager);

        // Hostside test has set a device lockscreen in order to enable credential storage
    }

    @Override
    protected void tearDown() throws Exception {
        // Hostside test will clear device lockscreen which in turn will clear the keystore.
        super.tearDown();
    }

    public void testCanInstallAndRemoveValidRsaKeypair() throws Exception {
        final String alias = "com.android.test.valid-rsa-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey , "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);

        // Install keypair.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, cert, alias));
        try {
            // Request and retrieve using the alias.
            assertGranted(alias, false);
            assertEquals(alias, new KeyChainAliasFuture(alias).get());
            assertGranted(alias, true);

            // Verify key is at least something like the one we put in.
            assertEquals(KeyChain.getPrivateKey(getActivity(), alias).getAlgorithm(), "RSA");
        } finally {
            // Delete regardless of whether the test succeeded.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
        // Verify alias is actually deleted.
        assertGranted(alias, false);
    }

    public void testCanInstallWithAutomaticAccess() throws Exception {
        final String grant = "com.android.test.autogrant-key-1";
        final String withhold = "com.android.test.nongrant-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey , "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);

        // Install keypairs.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                grant, true));
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                withhold, false));
        try {
            // Verify only the requested key was actually granted.
            assertGranted(grant, true);
            assertGranted(withhold, false);

            // Verify the granted key is actually obtainable in PrivateKey form.
            assertEquals(KeyChain.getPrivateKey(getActivity(), grant).getAlgorithm(), "RSA");
        } finally {
            // Delete both keypairs.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), grant));
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), withhold));
        }
        // Verify they're actually gone.
        assertGranted(grant, false);
        assertGranted(withhold, false);
    }

    private List<Certificate> loadCertificateChain(String assetName) throws Exception {
        final Collection<Certificate> certs = loadCertificatesFromAsset(assetName);
        final ArrayList<Certificate> certChain = new ArrayList(certs);
        // Some sanity check on the cert chain
        assertTrue(certs.size() > 1);
        for (int i = 1; i < certChain.size(); i++) {
            certChain.get(i - 1).verify(certChain.get(i).getPublicKey());
        }
        return certChain;
    }

    public void testCanInstallCertChain() throws Exception {
        // Use assets/generate-client-cert-chain.sh to regenerate the client cert chain.
        final PrivateKey privKey = loadPrivateKeyFromAsset("user-cert-chain.key");
        final Certificate[] certChain = loadCertificateChain("user-cert-chain.crt")
                .toArray(new Certificate[0]);
        final String alias = "com.android.test.clientkeychain";

        // Install keypairs.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, certChain, alias, true));
        try {
            // Verify only the requested key was actually granted.
            assertGranted(alias, true);

            // Verify the granted key is actually obtainable in PrivateKey form.
            assertEquals(KeyChain.getPrivateKey(getActivity(), alias).getAlgorithm(), "RSA");

            // Verify the certificate chain is correct
            X509Certificate[] returnedCerts = KeyChain.getCertificateChain(getActivity(), alias);
            assertTrue(Arrays.equals(certChain, returnedCerts));
        } finally {
            // Delete both keypairs.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
        // Verify they're actually gone.
        assertGranted(alias, false);
    }

    public void testGrantsDoNotPersistBetweenInstallations() throws Exception {
        final String alias = "com.android.test.persistent-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey , "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);

        // Install keypair.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                alias, true));
        try {
            assertGranted(alias, true);
        } finally {
            // Delete and verify.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
        assertGranted(alias, false);

        // Install again.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                alias, false));
        try {
            assertGranted(alias, false);
        } finally {
            // Delete.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    public void testNullKeyParamsFailPredictably() throws Exception {
        final String alias = "com.android.test.null-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey, "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);
        try {
            mDevicePolicyManager.installKeyPair(getWho(), null, cert, alias);
            fail("Exception should have been thrown for null PrivateKey");
        } catch (NullPointerException expected) {
        }
        try {
            mDevicePolicyManager.installKeyPair(getWho(), privKey, null, alias);
            fail("Exception should have been thrown for null Certificate");
        } catch (NullPointerException expected) {
        }
    }

    public void testNullAdminComponentIsDenied() throws Exception {
        final String alias = "com.android.test.null-admin-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey, "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);
        try {
            mDevicePolicyManager.installKeyPair(null, privKey, cert, alias);
            fail("Exception should have been thrown for null ComponentName");
        } catch (SecurityException expected) {
        }
    }

    public void testNotUserSelectableAliasCanBeChosenViaPolicy() throws Exception {
        final String alias = "com.android.test.not-selectable-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey , "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);

        // Install keypair.
        assertTrue(mDevicePolicyManager.installKeyPair(
            getWho(), privKey, new Certificate[] {cert}, alias, false, false));
        try {
            // Request and retrieve using the alias.
            assertGranted(alias, false);
            assertEquals(alias, new KeyChainAliasFuture(alias).get());
            assertGranted(alias, true);
        } finally {
            // Delete regardless of whether the test succeeded.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    byte[] signDataWithKey(String algoIdentifier, PrivateKey privateKey) throws Exception {
        byte[] data = new String("hello").getBytes();
        Signature sign = Signature.getInstance(algoIdentifier);
        sign.initSign(privateKey);
        sign.update(data);
        return sign.sign();
    }

    void verifySignature(String algoIdentifier, PublicKey publicKey, byte[] signature)
            throws Exception {
        byte[] data = new String("hello").getBytes();
        Signature verify = Signature.getInstance(algoIdentifier);
        verify.initVerify(publicKey);
        verify.update(data);
        assertTrue(verify.verify(signature));
    }

    void verifySignatureOverData(String algoIdentifier, KeyPair keyPair) throws Exception {
        verifySignature(algoIdentifier, keyPair.getPublic(),
                signDataWithKey(algoIdentifier, keyPair.getPrivate()));
    }

    public void testCanGenerateRSAKeyPair() throws Exception {
        final String alias = "com.android.test.generated-rsa-1";
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build();

            AttestedKeyPair generated = mDevicePolicyManager.generateKeyPair(
                    getWho(), "RSA", spec);
            assertNotNull(generated);
            verifySignatureOverData("SHA256withRSA", generated.getKeyPair());
        } finally {
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    public void testCanGenerateECKeyPair() throws Exception {
        final String alias = "com.android.test.generated-ec-1";
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build();

            AttestedKeyPair generated = mDevicePolicyManager.generateKeyPair(
                    getWho(), "EC", spec);
            assertNotNull(generated);
            verifySignatureOverData("SHA256withECDSA", generated.getKeyPair());
        } finally {
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    public void testCanGenerateECKeyPairWithKeyAttestation() throws Exception {
        final String alias = "com.android.test.attested-ec-1";
        byte[] attestationChallenge = new byte[] {0x01, 0x02, 0x03};
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge(attestationChallenge)
                    .build();
            AttestedKeyPair generated = mDevicePolicyManager.generateKeyPair(
                    getWho(), "EC", spec);
            final KeyPair keyPair = generated.getKeyPair();
            final String algorithmIdentifier = "SHA256withECDSA";
            assertNotNull(generated);
            verifySignatureOverData(algorithmIdentifier, keyPair);
            List<Certificate> attestation = generated.getAttestationRecord();
            assertNotNull(attestation);
            assertTrue(attestation.size() >= 2);
            X509Certificate leaf = (X509Certificate) attestation.get(0);
            final String attestationExtensionOID = "1.3.6.1.4.1.11129.2.1.17";
            Set<String> extensions = leaf.getNonCriticalExtensionOIDs();
            assertTrue(extensions.contains(attestationExtensionOID));
            PublicKey keyFromCert = leaf.getPublicKey();
            // The public key from the certificate doesn't have to be equal to the public key
            // from the pair, but must be usable for verifying signatures produced with
            // corresponding private key.
            verifySignature(algorithmIdentifier, keyFromCert,
                    signDataWithKey(algorithmIdentifier, keyPair.getPrivate()));
            // Check that the certificate chain is valid.
            for (int i = 1; i < attestation.size(); i++) {
                X509Certificate intermediate = (X509Certificate) attestation.get(i);
                PublicKey intermediateKey = intermediate.getPublicKey();
                leaf.verify(intermediateKey);
                leaf = intermediate;
            }

            // leaf is now the root, verify the root is self-signed.
            PublicKey rootKey = leaf.getPublicKey();
            leaf.verify(rootKey);
        } finally {
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    /**
     * Creates a self-signed X.509 certificate, given a key pair, subject and issuer.
     */
    private static X509Certificate createCertificate(
            KeyPair keyPair,
            X500Principal subject,
            X500Principal issuer) throws Exception {
        // Make the certificate valid for two days.
        long millisPerDay = 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        Date start = new Date(now - millisPerDay);
        Date end = new Date(now + millisPerDay);

        // Assign a random serial number.
        byte[] serialBytes = new byte[16];
        new SecureRandom().nextBytes(serialBytes);
        BigInteger serialNumber = new BigInteger(1, serialBytes);

        // Create the certificate builder
        X509v3CertificateBuilder x509cg = new X509v3CertificateBuilder(
                X500Name.getInstance(issuer.getEncoded()), serialNumber, start, end,
                X500Name.getInstance(subject.getEncoded()),
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));

        // Choose a signature algorithm matching the key format.
        String keyAlgorithm = keyPair.getPrivate().getAlgorithm();
        String signatureAlgorithm;
        if (keyAlgorithm.equals("RSA")) {
            signatureAlgorithm = "SHA256withRSA";
        } else if (keyAlgorithm.equals("EC")) {
            signatureAlgorithm = "SHA256withECDSA";
        } else {
            throw new IllegalArgumentException("Unknown key algorithm " + keyAlgorithm);
        }

        // Sign the certificate and generate it.
        X509CertificateHolder x509holder = x509cg.build(
                new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate()));
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate x509c = (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(x509holder.getEncoded()));
        return x509c;
    }

    public void testCanSetKeyPairCert() throws Exception {
        final String alias = "com.android.test.set-ec-1";
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build();

            AttestedKeyPair generated = mDevicePolicyManager.generateKeyPair(
                    getWho(), "EC", spec);
            assertNotNull(generated);
            // Create a self-signed cert to go with it.
            X500Principal issuer = new X500Principal("CN=SelfSigned, O=Android, C=US");
            X500Principal subject = new X500Principal("CN=Subject, O=Android, C=US");
            X509Certificate cert = createCertificate(generated.getKeyPair(), subject, issuer);
            // Set the certificate chain
            List<Certificate> certs = new ArrayList<Certificate>();
            certs.add(cert);
            mDevicePolicyManager.setKeyPairCertificate(getWho(), alias, certs, true);
            // Make sure that the alias can now be obtained.
            assertEquals(alias, new KeyChainAliasFuture(alias).get());
            // And can be retrieved from KeyChain
            X509Certificate[] fetchedCerts = KeyChain.getCertificateChain(getActivity(), alias);
            assertEquals(fetchedCerts.length, certs.size());
            assertTrue(Arrays.equals(fetchedCerts[0].getEncoded(), certs.get(0).getEncoded()));
        } finally {
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    public void testCanSetKeyPairCertChain() throws Exception {
        final String alias = "com.android.test.set-ec-2";
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build();

            AttestedKeyPair generated = mDevicePolicyManager.generateKeyPair(
                    getWho(), "EC", spec);
            assertNotNull(generated);
            List<Certificate> chain = loadCertificateChain("user-cert-chain.crt");
            mDevicePolicyManager.setKeyPairCertificate(getWho(), alias, chain, true);
            // Make sure that the alias can now be obtained.
            assertEquals(alias, new KeyChainAliasFuture(alias).get());
            // And can be retrieved from KeyChain
            X509Certificate[] fetchedCerts = KeyChain.getCertificateChain(getActivity(), alias);
            assertEquals(fetchedCerts.length, chain.size());
            for (int i = 0; i < chain.size(); i++) {
                assertTrue(Arrays.equals(fetchedCerts[i].getEncoded(), chain.get(i).getEncoded()));
            }
        } finally {
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    private void assertGranted(String alias, boolean expected) throws InterruptedException {
        boolean granted = false;
        try {
            granted = (KeyChain.getPrivateKey(getActivity(), alias) != null);
        } catch (KeyChainException e) {
            if (expected) {
                e.printStackTrace();
            }
        }
        assertEquals("Grant for alias: \"" + alias + "\"", expected, granted);
    }

    private static PrivateKey getPrivateKey(final byte[] key, String type)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance(type).generatePrivate(
                new PKCS8EncodedKeySpec(key));
    }

    private static Certificate getCertificate(byte[] cert) throws CertificateException {
        return CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(cert));
    }

    private Collection<Certificate> loadCertificatesFromAsset(String assetName) {
        try {
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            AssetManager am = getActivity().getAssets();
            InputStream is = am.open(assetName);
            return (Collection<Certificate>) certFactory.generateCertificates(is);
        } catch (IOException | CertificateException e) {
            e.printStackTrace();
        }
        return null;
    }

    private PrivateKey loadPrivateKeyFromAsset(String assetName) {
        try {
            AssetManager am = getActivity().getAssets();
            InputStream is = am.open(assetName);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int length;
            byte[] buffer = new byte[4096];
            while ((length = is.read(buffer, 0, buffer.length)) != -1) {
              output.write(buffer, 0, length);
            }
            return getPrivateKey(output.toByteArray(), "RSA");
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class KeyChainAliasFuture implements KeyChainAliasCallback {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private String mChosenAlias = null;

        @Override
        public void alias(final String chosenAlias) {
            mChosenAlias = chosenAlias;
            mLatch.countDown();
        }

        public KeyChainAliasFuture(String alias) throws UnsupportedEncodingException {
            /* Pass the alias as a GET to an imaginary server instead of explicitly asking for it,
             * to make sure the DPC actually has to do some work to grant the cert.
             */
            final Uri uri =
                    Uri.parse("https://example.org/?alias=" + URLEncoder.encode(alias, "UTF-8"));
            KeyChain.choosePrivateKeyAlias(getActivity(), this,
                    null /* keyTypes */, null /* issuers */, uri, null /* alias */);
        }

        public String get() throws InterruptedException {
            assertTrue("Chooser timeout", mLatch.await(KEYCHAIN_TIMEOUT_MINS, TimeUnit.MINUTES));
            return mChosenAlias;
        }
    }

    private void assertDeviceOwner(DevicePolicyManager devicePolicyManager) {
        assertNotNull(devicePolicyManager);
        assertTrue(devicePolicyManager.isAdminActive(getWho()));
        assertTrue(devicePolicyManager.isDeviceOwnerApp(getActivity().getPackageName()));
        assertFalse(devicePolicyManager.isManagedProfile(getWho()));
    }

    private ComponentName getWho() {
        return BasicAdminReceiver.getComponentName(getActivity());
    }
}
