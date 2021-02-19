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

package android.net.wifi.cts;

import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.PatternMatcher;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;

import androidx.core.os.BuildCompat;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

/**
 * Tests the entire connection flow using {@link WifiNetworkSpecifier} embedded in a
 * {@link NetworkRequest} & passed into {@link ConnectivityManager#requestNetwork(NetworkRequest,
 * ConnectivityManager.NetworkCallback)}.
 *
 * Assumes that all the saved networks is either open/WPA1/WPA2/WPA3 authenticated network.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiNetworkSpecifierTest extends WifiJUnit4TestBase {
    private static final String TAG = "WifiNetworkSpecifierTest";

    private static final String CA_SUITE_B_ECDSA_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIICTzCCAdSgAwIBAgIUdnLttwNPnQzFufplGOr9bTrGCqMwCgYIKoZIzj0EAwMw\n"
                    + "XjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMQwwCgYDVQQHDANNVFYxEDAOBgNV\n"
                    + "BAoMB0FuZHJvaWQxDjAMBgNVBAsMBVdpLUZpMRIwEAYDVQQDDAl1bml0ZXN0Q0Ew\n"
                    + "HhcNMjAwNzIxMDIyNDA1WhcNMzAwNTMwMDIyNDA1WjBeMQswCQYDVQQGEwJVUzEL\n"
                    + "MAkGA1UECAwCQ0ExDDAKBgNVBAcMA01UVjEQMA4GA1UECgwHQW5kcm9pZDEOMAwG\n"
                    + "A1UECwwFV2ktRmkxEjAQBgNVBAMMCXVuaXRlc3RDQTB2MBAGByqGSM49AgEGBSuB\n"
                    + "BAAiA2IABFmntXwk9icqhDQFUP1xy04WyEpaGW4q6Q+8pujlSl/X3iotPZ++GZfp\n"
                    + "Mfv3YDHDBl6sELPQ2BEjyPXmpsKjOUdiUe69e88oGEdeqT2xXiQ6uzpTfJD4170i\n"
                    + "O/TwLrQGKKNTMFEwHQYDVR0OBBYEFCjptsX3g4g5W0L4oEP6N3gfyiZXMB8GA1Ud\n"
                    + "IwQYMBaAFCjptsX3g4g5W0L4oEP6N3gfyiZXMA8GA1UdEwEB/wQFMAMBAf8wCgYI\n"
                    + "KoZIzj0EAwMDaQAwZgIxAK61brUYRbLmQKiaEboZgrHtnPAcGo7Yzx3MwHecx3Dm\n"
                    + "5soIeLVYc8bPYN1pbhXW1gIxALdEe2sh03nBHyQH4adYoZungoCwt8mp/7sJFxou\n"
                    + "9UnRegyBgGzf74ROWdpZHzh+Pg==\n"
                    + "-----END CERTIFICATE-----\n";
    public static final X509Certificate CA_SUITE_B_ECDSA_CERT =
            loadCertificate(CA_SUITE_B_ECDSA_CERT_STRING);

    private static final String CLIENT_SUITE_B_ECDSA_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIB9zCCAX4CFDpfSZh3AH07BEfGWuMDa7Ynz6y+MAoGCCqGSM49BAMDMF4xCzAJ\n"
                    + "BgNVBAYTAlVTMQswCQYDVQQIDAJDQTEMMAoGA1UEBwwDTVRWMRAwDgYDVQQKDAdB\n"
                    + "bmRyb2lkMQ4wDAYDVQQLDAVXaS1GaTESMBAGA1UEAwwJdW5pdGVzdENBMB4XDTIw\n"
                    + "MDcyMTAyMjk1MFoXDTMwMDUzMDAyMjk1MFowYjELMAkGA1UEBhMCVVMxCzAJBgNV\n"
                    + "BAgMAkNBMQwwCgYDVQQHDANNVFYxEDAOBgNVBAoMB0FuZHJvaWQxDjAMBgNVBAsM\n"
                    + "BVdpLUZpMRYwFAYDVQQDDA11bml0ZXN0Q2xpZW50MHYwEAYHKoZIzj0CAQYFK4EE\n"
                    + "ACIDYgAEhxhVJ7dcSqrto0X+dgRxtd8BWG8cWmPjBji3MIxDLfpcMDoIB84ae1Ew\n"
                    + "gJn4YUYHrWsUDiVNihv8j7a/Ol1qcIY2ybH7tbezefLmagqA4vXEUXZXoUyL4ZNC\n"
                    + "DWcdw6LrMAoGCCqGSM49BAMDA2cAMGQCMH4aP73HrriRUJRguiuRic+X4Cqj/7YQ\n"
                    + "ueJmP87KF92/thhoQ9OrRo8uJITPmNDswwIwP2Q1AZCSL4BI9dYrqu07Ar+pSkXE\n"
                    + "R7oOqGdZR+d/MvXcFSrbIaLKEoHXmQamIHLe\n"
                    + "-----END CERTIFICATE-----\n";
    public static final X509Certificate CLIENT_SUITE_B_ECDSA_CERT =
            loadCertificate(CLIENT_SUITE_B_ECDSA_CERT_STRING);

    private static final byte[] CLIENT_SUITE_B_ECC_KEY_DATA = new byte[]{
            (byte) 0x30, (byte) 0x81, (byte) 0xb6, (byte) 0x02, (byte) 0x01, (byte) 0x00,
            (byte) 0x30, (byte) 0x10, (byte) 0x06, (byte) 0x07, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0xce, (byte) 0x3d, (byte) 0x02, (byte) 0x01, (byte) 0x06,
            (byte) 0x05, (byte) 0x2b, (byte) 0x81, (byte) 0x04, (byte) 0x00, (byte) 0x22,
            (byte) 0x04, (byte) 0x81, (byte) 0x9e, (byte) 0x30, (byte) 0x81, (byte) 0x9b,
            (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x04, (byte) 0x30, (byte) 0xea,
            (byte) 0x6c, (byte) 0x4b, (byte) 0x6d, (byte) 0x43, (byte) 0xf9, (byte) 0x6c,
            (byte) 0x91, (byte) 0xdc, (byte) 0x2d, (byte) 0x6e, (byte) 0x87, (byte) 0x4f,
            (byte) 0x0a, (byte) 0x0b, (byte) 0x97, (byte) 0x25, (byte) 0x1c, (byte) 0x79,
            (byte) 0xa2, (byte) 0x07, (byte) 0xdc, (byte) 0x94, (byte) 0xc2, (byte) 0xee,
            (byte) 0x64, (byte) 0x51, (byte) 0x6d, (byte) 0x4e, (byte) 0x35, (byte) 0x1c,
            (byte) 0x22, (byte) 0x2f, (byte) 0xc0, (byte) 0xea, (byte) 0x09, (byte) 0x47,
            (byte) 0x3e, (byte) 0xb9, (byte) 0xb6, (byte) 0xb8, (byte) 0x83, (byte) 0x9e,
            (byte) 0xed, (byte) 0x59, (byte) 0xe5, (byte) 0xe7, (byte) 0x0f, (byte) 0xa1,
            (byte) 0x64, (byte) 0x03, (byte) 0x62, (byte) 0x00, (byte) 0x04, (byte) 0x87,
            (byte) 0x18, (byte) 0x55, (byte) 0x27, (byte) 0xb7, (byte) 0x5c, (byte) 0x4a,
            (byte) 0xaa, (byte) 0xed, (byte) 0xa3, (byte) 0x45, (byte) 0xfe, (byte) 0x76,
            (byte) 0x04, (byte) 0x71, (byte) 0xb5, (byte) 0xdf, (byte) 0x01, (byte) 0x58,
            (byte) 0x6f, (byte) 0x1c, (byte) 0x5a, (byte) 0x63, (byte) 0xe3, (byte) 0x06,
            (byte) 0x38, (byte) 0xb7, (byte) 0x30, (byte) 0x8c, (byte) 0x43, (byte) 0x2d,
            (byte) 0xfa, (byte) 0x5c, (byte) 0x30, (byte) 0x3a, (byte) 0x08, (byte) 0x07,
            (byte) 0xce, (byte) 0x1a, (byte) 0x7b, (byte) 0x51, (byte) 0x30, (byte) 0x80,
            (byte) 0x99, (byte) 0xf8, (byte) 0x61, (byte) 0x46, (byte) 0x07, (byte) 0xad,
            (byte) 0x6b, (byte) 0x14, (byte) 0x0e, (byte) 0x25, (byte) 0x4d, (byte) 0x8a,
            (byte) 0x1b, (byte) 0xfc, (byte) 0x8f, (byte) 0xb6, (byte) 0xbf, (byte) 0x3a,
            (byte) 0x5d, (byte) 0x6a, (byte) 0x70, (byte) 0x86, (byte) 0x36, (byte) 0xc9,
            (byte) 0xb1, (byte) 0xfb, (byte) 0xb5, (byte) 0xb7, (byte) 0xb3, (byte) 0x79,
            (byte) 0xf2, (byte) 0xe6, (byte) 0x6a, (byte) 0x0a, (byte) 0x80, (byte) 0xe2,
            (byte) 0xf5, (byte) 0xc4, (byte) 0x51, (byte) 0x76, (byte) 0x57, (byte) 0xa1,
            (byte) 0x4c, (byte) 0x8b, (byte) 0xe1, (byte) 0x93, (byte) 0x42, (byte) 0x0d,
            (byte) 0x67, (byte) 0x1d, (byte) 0xc3, (byte) 0xa2, (byte) 0xeb
    };
    public static final PrivateKey CLIENT_SUITE_B_ECC_KEY =
            loadPrivateKey("EC", CLIENT_SUITE_B_ECC_KEY_DATA);

    private static X509Certificate loadCertificate(String blob) {
        try {
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            InputStream stream = new ByteArrayInputStream(blob.getBytes(StandardCharsets.UTF_8));

            return (X509Certificate) certFactory.generateCertificate(stream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static PrivateKey loadPrivateKey(String algorithm, byte[] fakeKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(fakeKey));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;

    private Context mContext;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private UiDevice mUiDevice;
    private WifiConfiguration mTestNetwork;
    private ConnectivityManager.NetworkCallback mNrNetworkCallback;
    private TestHelper mTestHelper;

    private static final int DURATION = 10_000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported
        if (!WifiFeature.isWifiSupported(context)) return;

        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertThat(wifiManager).isNotNull();

        // turn on verbose logging for tests
        sWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setVerboseLoggingEnabled(true));
        // Disable scan throttling for tests.
        sWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setScanThrottleEnabled(false));

        // enable Wifi
        if (!wifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> wifiManager.setWifiEnabled(true));
        }
        PollingCheck.check("Wifi not enabled", DURATION, () -> wifiManager.isWifiEnabled());

        // check we have >= 1 saved network
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.getPrivilegedConfiguredNetworks());
        assertWithMessage("Need at least one saved network")
                .that(savedNetworks.isEmpty()).isFalse();

        // Disconnect & disable auto-join on the saved network to prevent auto-connect from
        // interfering with the test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : savedNetworks) {
                        wifiManager.disableNetwork(savedNetwork.networkId);
                    }
                });
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(context)) return;

        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertThat(wifiManager).isNotNull();

        if (!wifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> wifiManager.setWifiEnabled(true));
        }

        // Re-enable networks.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : wifiManager.getConfiguredNetworks()) {
                        wifiManager.enableNetwork(savedNetwork.networkId, false);
                    }
                });
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTestHelper = new TestHelper(mContext, mUiDevice);

        assumeTrue(WifiFeature.isWifiSupported(mContext));

        // turn screen on
        mTestHelper.turnScreenOn();

        // Clear any existing app state before each test.
        if (BuildCompat.isAtLeastS()) {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));
        }

        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.getPrivilegedConfiguredNetworks());
        // Pick any network in range.
        mTestNetwork = TestHelper.findMatchingSavedNetworksWithBssid(mWifiManager, savedNetworks)
                .get(0);

        // Wait for Wifi to be disconnected.
        PollingCheck.check(
                "Wifi not disconnected",
                20000,
                () -> mWifiManager.getConnectionInfo().getNetworkId() == -1);
    }

    @After
    public void tearDown() throws Exception {
        // If there is failure, ensure we unregister the previous request.
        if (mNrNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNrNetworkCallback);
        }
        // Clear any existing app state after each test.
        if (BuildCompat.isAtLeastS()) {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));
        }
        mTestHelper.turnScreenOff();
    }

    private void testSuccessfulConnectionWithSpecifier(WifiNetworkSpecifier specifier) {
        mNrNetworkCallback = mTestHelper.testConnectionFlowWithSpecifier(
                mTestNetwork, specifier, false);
    }

    private void testUserRejectionWithSpecifier(WifiNetworkSpecifier specifier) {
        mNrNetworkCallback = mTestHelper.testConnectionFlowWithSpecifier(
                mTestNetwork, specifier, true);
    }

    /**
     * Tests the entire connection flow using a specific SSID in the specifier.
     */
    @Test
    public void testConnectionWithSpecificSsid() {
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(
                        mTestNetwork)
                .build();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a SSID pattern in the specifier.
     */
    @Test
    public void testConnectionWithSsidPattern() {
        // Creates a ssid pattern by dropping the last char in the saved network & pass that
        // as a prefix match pattern in the request.
        String ssidUnquoted = WifiInfo.sanitizeSsid(mTestNetwork.SSID);
        assertThat(ssidUnquoted.length()).isAtLeast(2);
        String ssidPrefix = ssidUnquoted.substring(0, ssidUnquoted.length() - 1);
        // Note: The match may return more than 1 network in this case since we use a prefix match,
        // But, we will still ensure that the UI interactions in the test still selects the
        // saved network for connection.
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(mTestNetwork)
                .setSsidPattern(new PatternMatcher(ssidPrefix, PatternMatcher.PATTERN_PREFIX))
                .build();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a specific BSSID in the specifier.
     */
    @Test
    public void testConnectionWithSpecificBssid() {
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetwork)
                        .build();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a BSSID pattern in the specifier.
     */
    @Test
    public void testConnectionWithBssidPattern() {
        // Note: The match may return more than 1 network in this case since we use a prefix match,
        // But, we will still ensure that the UI interactions in the test still selects the
        // saved network for connection.
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetwork)
                        .setBssidPattern(MacAddress.fromString(mTestNetwork.BSSID),
                                MacAddress.fromString("ff:ff:ff:00:00:00"))
                        .build();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a BSSID pattern in the specifier.
     */
    @Test
    public void testUserRejectionWithSpecificSsid() {
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(
                        mTestNetwork)
                        .build();
        testUserRejectionWithSpecifier(specifier);
    }

    /**
     * Tests the builder for WPA2 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa2Enterprise() {
        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa2EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa2EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }

    /**
     * Tests the builder for WPA3 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa3Enterprise() {
        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa3EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa3EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }

    /**
     * Tests the builder for WPA3 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa3EnterpriseWithStandardApi() {
        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa3EnterpriseStandardModeConfig(new WifiEnterpriseConfig())
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa3EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }

    /**
     * Tests the builder for WPA3 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa3Enterprise192bit() {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");

        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(mTestNetwork.SSID))
                .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }
}
