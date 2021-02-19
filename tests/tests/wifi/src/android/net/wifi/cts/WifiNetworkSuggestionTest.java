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

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.wifi.WifiEnterpriseConfig.Eap.AKA;
import static android.net.wifi.WifiEnterpriseConfig.Eap.WAPI_CERT;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.os.BuildCompat;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiNetworkSuggestionTest extends WifiJUnit4TestBase {
    private static final String TAG = "WifiNetworkSuggestionTest";

    private static final String TEST_SSID = "testSsid";
    private static final String TEST_BSSID = "00:df:aa:bc:12:23";
    private static final String TEST_PASSPHRASE = "testPassword";
    private static final int TEST_PRIORITY = 5;
    private static final int TEST_PRIORITY_GROUP = 1;
    private static final int TEST_SUB_ID = 1;

    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static boolean sWasWifiEnabled;

    private Context mContext;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private UiDevice mUiDevice;
    private WifiConfiguration mTestNetwork;
    private TestNetworkCallback mNsNetworkCallback;
    private ScheduledExecutorService mExecutorService;

    private static final int DURATION_MILLIS = 10_000;
    private static final int DURATION_NETWORK_CONNECTION_MILLIS = 60_000;
    private static final int DURATION_SCREEN_TOGGLE_MILLIS = 2000;


    @BeforeClass
    public static void setUpClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported
        // Don't use assumeTrue in @BeforeClass
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
        sWasWifiEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isWifiEnabled());
        if (!wifiManager.isWifiEnabled()) setWifiEnabled(true);
        PollingCheck.check("Wifi not enabled", DURATION_MILLIS, () -> wifiManager.isWifiEnabled());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(context)) return;

        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertThat(wifiManager).isNotNull();

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setWifiEnabled(sWasWifiEnabled));
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mExecutorService = Executors.newSingleThreadScheduledExecutor();

        // skip the test if WiFi is not supported
        assumeTrue(WifiFeature.isWifiSupported(mContext));
        // skip the test if location is not supported
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION));

        assertWithMessage("Please enable location for this test!").that(
                mContext.getSystemService(LocationManager.class).isLocationEnabled()).isTrue();

        // turn screen on
        turnScreenOn();

        // Clear any existing app state before each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));

        // check we have >= 1 saved network
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.getPrivilegedConfiguredNetworks());
        assertFalse("Need at least one saved network", savedNetworks.isEmpty());
        // Pick the last saved network on the device (assumes that it is in range)
        mTestNetwork = savedNetworks.get(savedNetworks.size()  - 1);

        // Disconnect & disable auto-join on the saved network to prevent auto-connect from
        // interfering with the test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : savedNetworks) {
                        mWifiManager.disableNetwork(savedNetwork.networkId);
                    }
                    mWifiManager.disconnect();
                });

        // Wait for Wifi to be disconnected.
        PollingCheck.check(
                "Wifi not disconnected",
                20_000,
                () -> mWifiManager.getConnectionInfo().getNetworkId() == -1);
    }

    @After
    public void tearDown() throws Exception {
        // Re-enable networks.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : mWifiManager.getConfiguredNetworks()) {
                        mWifiManager.enableNetwork(savedNetwork.networkId, false);
                    }
                });
        // Release the requests after the test.
        if (mNsNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNsNetworkCallback);
        }
        mExecutorService.shutdownNow();
        // Clear any existing app state after each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));
        turnScreenOff();
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        // now trigger the change using shell commands.
        SystemUtil.runShellCommand("svc wifi " + (enable ? "enable" : "disable"));
    }

    private void turnScreenOn() throws Exception {
        mUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE_MILLIS);
    }

    private void turnScreenOff() throws Exception {
        mUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE_MILLIS);
    }

    private static final String CA_SUITE_B_RSA3072_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIEnTCCAwWgAwIBAgIUD87Y8fFLzLr1HQ/64aEnjNq2R/4wDQYJKoZIhvcNAQEM\n"
                    + "BQAwXjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMQwwCgYDVQQHDANNVFYxEDAO\n"
                    + "BgNVBAoMB0FuZHJvaWQxDjAMBgNVBAsMBVdpLUZpMRIwEAYDVQQDDAl1bml0ZXN0\n"
                    + "Q0EwHhcNMjAwNzIxMDIxNzU0WhcNMzAwNTMwMDIxNzU0WjBeMQswCQYDVQQGEwJV\n"
                    + "UzELMAkGA1UECAwCQ0ExDDAKBgNVBAcMA01UVjEQMA4GA1UECgwHQW5kcm9pZDEO\n"
                    + "MAwGA1UECwwFV2ktRmkxEjAQBgNVBAMMCXVuaXRlc3RDQTCCAaIwDQYJKoZIhvcN\n"
                    + "AQEBBQADggGPADCCAYoCggGBAMtrsT0otlxh0QS079KpRRbU1PQjCihSoltXnrxF\n"
                    + "sTWZs2weVEeYVyYU5LaauCDDgISCMtjtfbfylMBeYjpWB5hYzYQOiTzo0anWhMyb\n"
                    + "Ngb7gpMVZuIl6lwMYRyVRKwHWnTo2EUg1ZzW5rGe5fs/KHj6//hoNFm+3Oju0TQd\n"
                    + "nraQULpoERPF5B7p85Cssk8uNbviBfZXvtCuJ4N6w7PNceOY/9bbwc1mC+pPZmzV\n"
                    + "SOAg0vvbIQRzChm63C3jBC3xmxSOOZVrKN4zKDG2s8P0oCNGt0NlgRMrgbPRekzg\n"
                    + "4avkbA0vTuc2AyriTEYkdea/Mt4EpRg9XuOb43U/GJ/d/vQv2/9fsxhXmsZrn8kr\n"
                    + "Qo5MMHJFUd96GgHmvYSU3Mf/5r8gF626lvqHioGuTAuHUSnr02ri1WUxZ15LDRgY\n"
                    + "quMjDCFZfucjJPDAdtiHcFSej/4SLJlN39z8oKKNPn3aL9Gv49oAKs9S8tfDVzMk\n"
                    + "fDLROQFHFuW715GnnMgEAoOpRwIDAQABo1MwUTAdBgNVHQ4EFgQUeVuGmSVN4ARs\n"
                    + "mesUMWSJ2qWLbxUwHwYDVR0jBBgwFoAUeVuGmSVN4ARsmesUMWSJ2qWLbxUwDwYD\n"
                    + "VR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQwFAAOCAYEAit1Lo/hegZpPuT9dlWZJ\n"
                    + "bC8JvAf95O8lnn6LFb69pgYOHCLgCIlvYXu9rdBUJgZo+V1MzJJljiO6RxWRfKbQ\n"
                    + "8WBYkoqR1EqriR3Kn8q/SjIZCdFSaznTyU1wQMveBQ6RJWXSUhYVfE9RjyFTp7B4\n"
                    + "UyH2uCluR/0T06HQNGfH5XpIYQqCk1Zgng5lmEmheLDPoJpa92lKeQFJMC6eYz9g\n"
                    + "lF1GHxPxkPfbMJ6ZDp5X6Yopu6Q6uEXhVKM/iQVcgzRkx9rid+xTYl+nOKyK/XfC\n"
                    + "z8P0/TFIoPTW02DLge5wKagdoCpy1B7HdrAXyUjoH4B8MsUkq3kYPFSjPzScuTtV\n"
                    + "kUuDw5ipCNeXCRnhbYqRDk6PX5GUu2cmN9jtaH3tbgm3fKNOsd/BO1fLIl7qjXlR\n"
                    + "27HHbC0JXjNvlm2DLp23v4NTxS7WZGYsxyUj5DZrxBxqCsTXu/01w1BrQKWKh9FM\n"
                    + "aVrlA8omfVODK2CSuw+KhEMHepRv/AUgsLl4L4+RMoa+\n"
                    + "-----END CERTIFICATE-----\n";
    private static final X509Certificate CA_SUITE_B_RSA3072_CERT =
            loadCertificate(CA_SUITE_B_RSA3072_CERT_STRING);

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
    private static final X509Certificate CA_SUITE_B_ECDSA_CERT =
            loadCertificate(CA_SUITE_B_ECDSA_CERT_STRING);

    private static final String CLIENT_SUITE_B_RSA3072_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIERzCCAq8CFDopjyNgaj+c2TN2k06h7okEWpHJMA0GCSqGSIb3DQEBDAUAMF4x\n"
                    + "CzAJBgNVBAYTAlVTMQswCQYDVQQIDAJDQTEMMAoGA1UEBwwDTVRWMRAwDgYDVQQK\n"
                    + "DAdBbmRyb2lkMQ4wDAYDVQQLDAVXaS1GaTESMBAGA1UEAwwJdW5pdGVzdENBMB4X\n"
                    + "DTIwMDcyMTAyMjkxMVoXDTMwMDUzMDAyMjkxMVowYjELMAkGA1UEBhMCVVMxCzAJ\n"
                    + "BgNVBAgMAkNBMQwwCgYDVQQHDANNVFYxEDAOBgNVBAoMB0FuZHJvaWQxDjAMBgNV\n"
                    + "BAsMBVdpLUZpMRYwFAYDVQQDDA11bml0ZXN0Q2xpZW50MIIBojANBgkqhkiG9w0B\n"
                    + "AQEFAAOCAY8AMIIBigKCAYEAwSK3C5K5udtCKTnE14e8z2cZvwmB4Xe+a8+7QLud\n"
                    + "Hooc/lQzClgK4MbVUC0D3FE+U32C78SxKoTaRWtvPmNm+UaFT8KkwyUno/dv+2XD\n"
                    + "pd/zARQ+3FwAfWopAhEyCVSxwsCa+slQ4juRIMIuUC1Mm0NaptZyM3Tj/ICQEfpk\n"
                    + "o9qVIbiK6eoJMTkY8EWfAn7RTFdfR1OLuO0mVOjgLW9/+upYv6hZ19nAMAxw4QTJ\n"
                    + "x7lLwALX7B+tDYNEZHDqYL2zyvQWAj2HClere8QYILxkvktgBg2crEJJe4XbDH7L\n"
                    + "A3rrXmsiqf1ZbfFFEzK9NFqovL+qGh+zIP+588ShJFO9H/RDnDpiTnAFTWXQdTwg\n"
                    + "szSS0Vw2PB+JqEABAa9DeMvXT1Oy+NY3ItPHyy63nQZVI2rXANw4NhwS0Z6DF+Qs\n"
                    + "TNrj+GU7e4SG/EGR8SvldjYfQTWFLg1l/UT1hOOkQZwdsaW1zgKyeuiFB2KdMmbA\n"
                    + "Sq+Ux1L1KICo0IglwWcB/8nnAgMBAAEwDQYJKoZIhvcNAQEMBQADggGBAMYwJkNw\n"
                    + "BaCviKFmReDTMwWPRy4AMNViEeqAXgERwDEKwM7efjsaj5gctWfKsxX6UdLzkhgg\n"
                    + "6S/T6PxVWKzJ6l7SoOuTa6tMQOZp+h3R1mdfEQbw8B5cXBxZ+batzAai6Fiy1FKS\n"
                    + "/ka3INbcGfYuIYghfTrb4/NJKN06ZaQ1bpPwq0e4gN7800T2nbawvSf7r+8ZLcG3\n"
                    + "6bGCjRMwDSIipNvOwoj3TG315XC7TccX5difQ4sKOY+d2MkVJ3RiO0Ciw2ZbEW8d\n"
                    + "1FH5vUQJWnBUfSFznosGzLwH3iWfqlP+27jNE+qB2igEwCRFgVAouURx5ou43xuX\n"
                    + "qf6JkdI3HTJGLIWxkp7gOeln4dEaYzKjYw+P0VqJvKVqQ0IXiLjHgE0J9p0vgyD6\n"
                    + "HVVcP7U8RgqrbIjL1QgHU4KBhGi+WSUh/mRplUCNvHgcYdcHi/gHpj/j6ubwqIGV\n"
                    + "z4iSolAHYTmBWcLyE0NgpzE6ntp+53r2KaUJA99l2iGVzbWTwqPSm0XAVw==\n"
                    + "-----END CERTIFICATE-----\n";
    private static final X509Certificate CLIENT_SUITE_B_RSA3072_CERT =
            loadCertificate(CLIENT_SUITE_B_RSA3072_CERT_STRING);

    private static final byte[] CLIENT_SUITE_B_RSA3072_KEY_DATA = new byte[]{
            (byte) 0x30, (byte) 0x82, (byte) 0x06, (byte) 0xfe, (byte) 0x02, (byte) 0x01,
            (byte) 0x00, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a,
            (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x04, (byte) 0x82,
            (byte) 0x06, (byte) 0xe8, (byte) 0x30, (byte) 0x82, (byte) 0x06, (byte) 0xe4,
            (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x82, (byte) 0x01,
            (byte) 0x81, (byte) 0x00, (byte) 0xc1, (byte) 0x22, (byte) 0xb7, (byte) 0x0b,
            (byte) 0x92, (byte) 0xb9, (byte) 0xb9, (byte) 0xdb, (byte) 0x42, (byte) 0x29,
            (byte) 0x39, (byte) 0xc4, (byte) 0xd7, (byte) 0x87, (byte) 0xbc, (byte) 0xcf,
            (byte) 0x67, (byte) 0x19, (byte) 0xbf, (byte) 0x09, (byte) 0x81, (byte) 0xe1,
            (byte) 0x77, (byte) 0xbe, (byte) 0x6b, (byte) 0xcf, (byte) 0xbb, (byte) 0x40,
            (byte) 0xbb, (byte) 0x9d, (byte) 0x1e, (byte) 0x8a, (byte) 0x1c, (byte) 0xfe,
            (byte) 0x54, (byte) 0x33, (byte) 0x0a, (byte) 0x58, (byte) 0x0a, (byte) 0xe0,
            (byte) 0xc6, (byte) 0xd5, (byte) 0x50, (byte) 0x2d, (byte) 0x03, (byte) 0xdc,
            (byte) 0x51, (byte) 0x3e, (byte) 0x53, (byte) 0x7d, (byte) 0x82, (byte) 0xef,
            (byte) 0xc4, (byte) 0xb1, (byte) 0x2a, (byte) 0x84, (byte) 0xda, (byte) 0x45,
            (byte) 0x6b, (byte) 0x6f, (byte) 0x3e, (byte) 0x63, (byte) 0x66, (byte) 0xf9,
            (byte) 0x46, (byte) 0x85, (byte) 0x4f, (byte) 0xc2, (byte) 0xa4, (byte) 0xc3,
            (byte) 0x25, (byte) 0x27, (byte) 0xa3, (byte) 0xf7, (byte) 0x6f, (byte) 0xfb,
            (byte) 0x65, (byte) 0xc3, (byte) 0xa5, (byte) 0xdf, (byte) 0xf3, (byte) 0x01,
            (byte) 0x14, (byte) 0x3e, (byte) 0xdc, (byte) 0x5c, (byte) 0x00, (byte) 0x7d,
            (byte) 0x6a, (byte) 0x29, (byte) 0x02, (byte) 0x11, (byte) 0x32, (byte) 0x09,
            (byte) 0x54, (byte) 0xb1, (byte) 0xc2, (byte) 0xc0, (byte) 0x9a, (byte) 0xfa,
            (byte) 0xc9, (byte) 0x50, (byte) 0xe2, (byte) 0x3b, (byte) 0x91, (byte) 0x20,
            (byte) 0xc2, (byte) 0x2e, (byte) 0x50, (byte) 0x2d, (byte) 0x4c, (byte) 0x9b,
            (byte) 0x43, (byte) 0x5a, (byte) 0xa6, (byte) 0xd6, (byte) 0x72, (byte) 0x33,
            (byte) 0x74, (byte) 0xe3, (byte) 0xfc, (byte) 0x80, (byte) 0x90, (byte) 0x11,
            (byte) 0xfa, (byte) 0x64, (byte) 0xa3, (byte) 0xda, (byte) 0x95, (byte) 0x21,
            (byte) 0xb8, (byte) 0x8a, (byte) 0xe9, (byte) 0xea, (byte) 0x09, (byte) 0x31,
            (byte) 0x39, (byte) 0x18, (byte) 0xf0, (byte) 0x45, (byte) 0x9f, (byte) 0x02,
            (byte) 0x7e, (byte) 0xd1, (byte) 0x4c, (byte) 0x57, (byte) 0x5f, (byte) 0x47,
            (byte) 0x53, (byte) 0x8b, (byte) 0xb8, (byte) 0xed, (byte) 0x26, (byte) 0x54,
            (byte) 0xe8, (byte) 0xe0, (byte) 0x2d, (byte) 0x6f, (byte) 0x7f, (byte) 0xfa,
            (byte) 0xea, (byte) 0x58, (byte) 0xbf, (byte) 0xa8, (byte) 0x59, (byte) 0xd7,
            (byte) 0xd9, (byte) 0xc0, (byte) 0x30, (byte) 0x0c, (byte) 0x70, (byte) 0xe1,
            (byte) 0x04, (byte) 0xc9, (byte) 0xc7, (byte) 0xb9, (byte) 0x4b, (byte) 0xc0,
            (byte) 0x02, (byte) 0xd7, (byte) 0xec, (byte) 0x1f, (byte) 0xad, (byte) 0x0d,
            (byte) 0x83, (byte) 0x44, (byte) 0x64, (byte) 0x70, (byte) 0xea, (byte) 0x60,
            (byte) 0xbd, (byte) 0xb3, (byte) 0xca, (byte) 0xf4, (byte) 0x16, (byte) 0x02,
            (byte) 0x3d, (byte) 0x87, (byte) 0x0a, (byte) 0x57, (byte) 0xab, (byte) 0x7b,
            (byte) 0xc4, (byte) 0x18, (byte) 0x20, (byte) 0xbc, (byte) 0x64, (byte) 0xbe,
            (byte) 0x4b, (byte) 0x60, (byte) 0x06, (byte) 0x0d, (byte) 0x9c, (byte) 0xac,
            (byte) 0x42, (byte) 0x49, (byte) 0x7b, (byte) 0x85, (byte) 0xdb, (byte) 0x0c,
            (byte) 0x7e, (byte) 0xcb, (byte) 0x03, (byte) 0x7a, (byte) 0xeb, (byte) 0x5e,
            (byte) 0x6b, (byte) 0x22, (byte) 0xa9, (byte) 0xfd, (byte) 0x59, (byte) 0x6d,
            (byte) 0xf1, (byte) 0x45, (byte) 0x13, (byte) 0x32, (byte) 0xbd, (byte) 0x34,
            (byte) 0x5a, (byte) 0xa8, (byte) 0xbc, (byte) 0xbf, (byte) 0xaa, (byte) 0x1a,
            (byte) 0x1f, (byte) 0xb3, (byte) 0x20, (byte) 0xff, (byte) 0xb9, (byte) 0xf3,
            (byte) 0xc4, (byte) 0xa1, (byte) 0x24, (byte) 0x53, (byte) 0xbd, (byte) 0x1f,
            (byte) 0xf4, (byte) 0x43, (byte) 0x9c, (byte) 0x3a, (byte) 0x62, (byte) 0x4e,
            (byte) 0x70, (byte) 0x05, (byte) 0x4d, (byte) 0x65, (byte) 0xd0, (byte) 0x75,
            (byte) 0x3c, (byte) 0x20, (byte) 0xb3, (byte) 0x34, (byte) 0x92, (byte) 0xd1,
            (byte) 0x5c, (byte) 0x36, (byte) 0x3c, (byte) 0x1f, (byte) 0x89, (byte) 0xa8,
            (byte) 0x40, (byte) 0x01, (byte) 0x01, (byte) 0xaf, (byte) 0x43, (byte) 0x78,
            (byte) 0xcb, (byte) 0xd7, (byte) 0x4f, (byte) 0x53, (byte) 0xb2, (byte) 0xf8,
            (byte) 0xd6, (byte) 0x37, (byte) 0x22, (byte) 0xd3, (byte) 0xc7, (byte) 0xcb,
            (byte) 0x2e, (byte) 0xb7, (byte) 0x9d, (byte) 0x06, (byte) 0x55, (byte) 0x23,
            (byte) 0x6a, (byte) 0xd7, (byte) 0x00, (byte) 0xdc, (byte) 0x38, (byte) 0x36,
            (byte) 0x1c, (byte) 0x12, (byte) 0xd1, (byte) 0x9e, (byte) 0x83, (byte) 0x17,
            (byte) 0xe4, (byte) 0x2c, (byte) 0x4c, (byte) 0xda, (byte) 0xe3, (byte) 0xf8,
            (byte) 0x65, (byte) 0x3b, (byte) 0x7b, (byte) 0x84, (byte) 0x86, (byte) 0xfc,
            (byte) 0x41, (byte) 0x91, (byte) 0xf1, (byte) 0x2b, (byte) 0xe5, (byte) 0x76,
            (byte) 0x36, (byte) 0x1f, (byte) 0x41, (byte) 0x35, (byte) 0x85, (byte) 0x2e,
            (byte) 0x0d, (byte) 0x65, (byte) 0xfd, (byte) 0x44, (byte) 0xf5, (byte) 0x84,
            (byte) 0xe3, (byte) 0xa4, (byte) 0x41, (byte) 0x9c, (byte) 0x1d, (byte) 0xb1,
            (byte) 0xa5, (byte) 0xb5, (byte) 0xce, (byte) 0x02, (byte) 0xb2, (byte) 0x7a,
            (byte) 0xe8, (byte) 0x85, (byte) 0x07, (byte) 0x62, (byte) 0x9d, (byte) 0x32,
            (byte) 0x66, (byte) 0xc0, (byte) 0x4a, (byte) 0xaf, (byte) 0x94, (byte) 0xc7,
            (byte) 0x52, (byte) 0xf5, (byte) 0x28, (byte) 0x80, (byte) 0xa8, (byte) 0xd0,
            (byte) 0x88, (byte) 0x25, (byte) 0xc1, (byte) 0x67, (byte) 0x01, (byte) 0xff,
            (byte) 0xc9, (byte) 0xe7, (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00,
            (byte) 0x01, (byte) 0x02, (byte) 0x82, (byte) 0x01, (byte) 0x80, (byte) 0x04,
            (byte) 0xb1, (byte) 0xcc, (byte) 0x53, (byte) 0x3a, (byte) 0xb0, (byte) 0xcb,
            (byte) 0x04, (byte) 0xba, (byte) 0x59, (byte) 0xf8, (byte) 0x2e, (byte) 0x81,
            (byte) 0xb2, (byte) 0xa9, (byte) 0xf3, (byte) 0x3c, (byte) 0xa5, (byte) 0x52,
            (byte) 0x90, (byte) 0x6f, (byte) 0x98, (byte) 0xc4, (byte) 0x69, (byte) 0x5b,
            (byte) 0x83, (byte) 0x84, (byte) 0x20, (byte) 0xb1, (byte) 0xae, (byte) 0xc3,
            (byte) 0x04, (byte) 0x46, (byte) 0x6a, (byte) 0x24, (byte) 0x2f, (byte) 0xcd,
            (byte) 0x6b, (byte) 0x90, (byte) 0x70, (byte) 0x20, (byte) 0x45, (byte) 0x25,
            (byte) 0x1a, (byte) 0xc3, (byte) 0x02, (byte) 0x42, (byte) 0xf3, (byte) 0x49,
            (byte) 0xe2, (byte) 0x3e, (byte) 0x21, (byte) 0x87, (byte) 0xdd, (byte) 0x6a,
            (byte) 0x94, (byte) 0x2a, (byte) 0x1e, (byte) 0x0f, (byte) 0xdb, (byte) 0x77,
            (byte) 0x5f, (byte) 0xc1, (byte) 0x2c, (byte) 0x03, (byte) 0xfb, (byte) 0xcf,
            (byte) 0x91, (byte) 0x82, (byte) 0xa1, (byte) 0xbf, (byte) 0xb0, (byte) 0x73,
            (byte) 0xfa, (byte) 0xda, (byte) 0xbc, (byte) 0xf8, (byte) 0x9f, (byte) 0x45,
            (byte) 0xd3, (byte) 0xe8, (byte) 0xbb, (byte) 0x38, (byte) 0xfb, (byte) 0xc2,
            (byte) 0x2d, (byte) 0x76, (byte) 0x51, (byte) 0x96, (byte) 0x18, (byte) 0x03,
            (byte) 0x15, (byte) 0xd9, (byte) 0xea, (byte) 0x82, (byte) 0x25, (byte) 0x83,
            (byte) 0xff, (byte) 0x5c, (byte) 0x85, (byte) 0x06, (byte) 0x09, (byte) 0xb2,
            (byte) 0x46, (byte) 0x12, (byte) 0x64, (byte) 0x02, (byte) 0x74, (byte) 0x4f,
            (byte) 0xbc, (byte) 0x9a, (byte) 0x25, (byte) 0x18, (byte) 0x01, (byte) 0x07,
            (byte) 0x17, (byte) 0x25, (byte) 0x55, (byte) 0x7c, (byte) 0xdc, (byte) 0xe1,
            (byte) 0xd1, (byte) 0x5a, (byte) 0x2f, (byte) 0x25, (byte) 0xaf, (byte) 0xf6,
            (byte) 0x8f, (byte) 0xa4, (byte) 0x9a, (byte) 0x5a, (byte) 0x3a, (byte) 0xfe,
            (byte) 0x2e, (byte) 0x93, (byte) 0x24, (byte) 0xa0, (byte) 0x27, (byte) 0xac,
            (byte) 0x07, (byte) 0x75, (byte) 0x33, (byte) 0x01, (byte) 0x54, (byte) 0x23,
            (byte) 0x0f, (byte) 0xe8, (byte) 0x9f, (byte) 0xfa, (byte) 0x36, (byte) 0xe6,
            (byte) 0x3a, (byte) 0xd5, (byte) 0x78, (byte) 0xb0, (byte) 0xe4, (byte) 0x6a,
            (byte) 0x16, (byte) 0x50, (byte) 0xbd, (byte) 0x0f, (byte) 0x9f, (byte) 0x32,
            (byte) 0xa1, (byte) 0x6b, (byte) 0xf5, (byte) 0xa4, (byte) 0x34, (byte) 0x58,
            (byte) 0xb6, (byte) 0xa4, (byte) 0xb3, (byte) 0xc3, (byte) 0x83, (byte) 0x08,
            (byte) 0x18, (byte) 0xc7, (byte) 0xef, (byte) 0x95, (byte) 0xe2, (byte) 0x1b,
            (byte) 0xba, (byte) 0x35, (byte) 0x61, (byte) 0xa3, (byte) 0xb4, (byte) 0x30,
            (byte) 0xe0, (byte) 0xd1, (byte) 0xc1, (byte) 0xa2, (byte) 0x3a, (byte) 0xc6,
            (byte) 0xb4, (byte) 0xd2, (byte) 0x80, (byte) 0x5a, (byte) 0xaf, (byte) 0xa4,
            (byte) 0x54, (byte) 0x3c, (byte) 0x66, (byte) 0x5a, (byte) 0x1c, (byte) 0x4d,
            (byte) 0xe1, (byte) 0xd9, (byte) 0x98, (byte) 0x44, (byte) 0x01, (byte) 0x1b,
            (byte) 0x8c, (byte) 0xe9, (byte) 0x80, (byte) 0x54, (byte) 0x83, (byte) 0x3d,
            (byte) 0x96, (byte) 0x25, (byte) 0x41, (byte) 0x1c, (byte) 0xad, (byte) 0xae,
            (byte) 0x3b, (byte) 0x7a, (byte) 0xd7, (byte) 0x9d, (byte) 0x10, (byte) 0x7c,
            (byte) 0xd1, (byte) 0xa7, (byte) 0x96, (byte) 0x39, (byte) 0xa5, (byte) 0x2f,
            (byte) 0xbe, (byte) 0xc3, (byte) 0x2c, (byte) 0x64, (byte) 0x01, (byte) 0xfe,
            (byte) 0xa2, (byte) 0xd1, (byte) 0x6a, (byte) 0xcf, (byte) 0x4c, (byte) 0x76,
            (byte) 0x3b, (byte) 0xc8, (byte) 0x35, (byte) 0x21, (byte) 0xda, (byte) 0x98,
            (byte) 0xcf, (byte) 0xf9, (byte) 0x29, (byte) 0xff, (byte) 0x30, (byte) 0x59,
            (byte) 0x36, (byte) 0x53, (byte) 0x0b, (byte) 0xbb, (byte) 0xfa, (byte) 0xba,
            (byte) 0xc4, (byte) 0x03, (byte) 0x23, (byte) 0xe0, (byte) 0xd3, (byte) 0x33,
            (byte) 0xff, (byte) 0x32, (byte) 0xdb, (byte) 0x30, (byte) 0x64, (byte) 0xc7,
            (byte) 0x56, (byte) 0xca, (byte) 0x55, (byte) 0x14, (byte) 0xee, (byte) 0x58,
            (byte) 0xfe, (byte) 0x96, (byte) 0x7e, (byte) 0x1c, (byte) 0x34, (byte) 0x16,
            (byte) 0xeb, (byte) 0x76, (byte) 0x26, (byte) 0x48, (byte) 0xe2, (byte) 0xe5,
            (byte) 0x5c, (byte) 0xd5, (byte) 0x83, (byte) 0x37, (byte) 0xd9, (byte) 0x09,
            (byte) 0x71, (byte) 0xbc, (byte) 0x54, (byte) 0x25, (byte) 0xca, (byte) 0x2e,
            (byte) 0xdb, (byte) 0x36, (byte) 0x39, (byte) 0xcc, (byte) 0x3a, (byte) 0x81,
            (byte) 0x95, (byte) 0x9e, (byte) 0xf4, (byte) 0x01, (byte) 0xa7, (byte) 0xc0,
            (byte) 0x20, (byte) 0xce, (byte) 0x70, (byte) 0x55, (byte) 0x2c, (byte) 0xe0,
            (byte) 0x93, (byte) 0x72, (byte) 0xa6, (byte) 0x25, (byte) 0xda, (byte) 0x64,
            (byte) 0x19, (byte) 0x18, (byte) 0xd2, (byte) 0x31, (byte) 0xe2, (byte) 0x7c,
            (byte) 0xf2, (byte) 0x30, (byte) 0x9e, (byte) 0x8d, (byte) 0xc6, (byte) 0x14,
            (byte) 0x8a, (byte) 0x38, (byte) 0xf0, (byte) 0x94, (byte) 0xeb, (byte) 0xf4,
            (byte) 0x64, (byte) 0x92, (byte) 0x3d, (byte) 0x67, (byte) 0xa6, (byte) 0x2c,
            (byte) 0x52, (byte) 0xfc, (byte) 0x60, (byte) 0xca, (byte) 0x2a, (byte) 0xcf,
            (byte) 0x24, (byte) 0xd5, (byte) 0x42, (byte) 0x5f, (byte) 0xc7, (byte) 0x9f,
            (byte) 0xf3, (byte) 0xb4, (byte) 0xdf, (byte) 0x76, (byte) 0x6e, (byte) 0x53,
            (byte) 0xa1, (byte) 0x7b, (byte) 0xae, (byte) 0xa5, (byte) 0x84, (byte) 0x1f,
            (byte) 0xfa, (byte) 0xc0, (byte) 0xb4, (byte) 0x6c, (byte) 0xc9, (byte) 0x02,
            (byte) 0x81, (byte) 0xc1, (byte) 0x00, (byte) 0xf3, (byte) 0x17, (byte) 0xd9,
            (byte) 0x48, (byte) 0x17, (byte) 0x87, (byte) 0x84, (byte) 0x16, (byte) 0xea,
            (byte) 0x2d, (byte) 0x31, (byte) 0x1b, (byte) 0xce, (byte) 0xec, (byte) 0xaf,
            (byte) 0xdc, (byte) 0x6b, (byte) 0xaf, (byte) 0xc8, (byte) 0xf1, (byte) 0x40,
            (byte) 0xa7, (byte) 0x4f, (byte) 0xef, (byte) 0x48, (byte) 0x08, (byte) 0x5e,
            (byte) 0x9a, (byte) 0xd1, (byte) 0xc0, (byte) 0xb1, (byte) 0xfe, (byte) 0xe7,
            (byte) 0x03, (byte) 0xd5, (byte) 0x96, (byte) 0x01, (byte) 0xe8, (byte) 0x40,
            (byte) 0xca, (byte) 0x78, (byte) 0xcb, (byte) 0xb3, (byte) 0x28, (byte) 0x1a,
            (byte) 0xf0, (byte) 0xe5, (byte) 0xf6, (byte) 0x46, (byte) 0xef, (byte) 0xcd,
            (byte) 0x1a, (byte) 0x0f, (byte) 0x13, (byte) 0x2d, (byte) 0x38, (byte) 0xf8,
            (byte) 0xf7, (byte) 0x88, (byte) 0x21, (byte) 0x15, (byte) 0xce, (byte) 0x48,
            (byte) 0xf4, (byte) 0x92, (byte) 0x7e, (byte) 0x9b, (byte) 0x2e, (byte) 0x2f,
            (byte) 0x22, (byte) 0x3e, (byte) 0x5c, (byte) 0x67, (byte) 0xd7, (byte) 0x58,
            (byte) 0xf6, (byte) 0xef, (byte) 0x1f, (byte) 0xb4, (byte) 0x04, (byte) 0xc7,
            (byte) 0xfd, (byte) 0x8c, (byte) 0x4e, (byte) 0x27, (byte) 0x9e, (byte) 0xb9,
            (byte) 0xef, (byte) 0x0f, (byte) 0xf7, (byte) 0x4a, (byte) 0xc2, (byte) 0xf4,
            (byte) 0x64, (byte) 0x6b, (byte) 0xe0, (byte) 0xfb, (byte) 0xe3, (byte) 0x45,
            (byte) 0xd5, (byte) 0x37, (byte) 0xa0, (byte) 0x2a, (byte) 0xc6, (byte) 0xf3,
            (byte) 0xf6, (byte) 0xcc, (byte) 0xb5, (byte) 0x94, (byte) 0xbf, (byte) 0x56,
            (byte) 0xa0, (byte) 0x61, (byte) 0x36, (byte) 0x88, (byte) 0x35, (byte) 0xd5,
            (byte) 0xa5, (byte) 0xad, (byte) 0x20, (byte) 0x48, (byte) 0xda, (byte) 0x70,
            (byte) 0x35, (byte) 0xd9, (byte) 0x75, (byte) 0x66, (byte) 0xa5, (byte) 0xac,
            (byte) 0x86, (byte) 0x7a, (byte) 0x75, (byte) 0x49, (byte) 0x88, (byte) 0x40,
            (byte) 0xce, (byte) 0xb0, (byte) 0x6f, (byte) 0x57, (byte) 0x15, (byte) 0x54,
            (byte) 0xd3, (byte) 0x2f, (byte) 0x11, (byte) 0x9b, (byte) 0xe3, (byte) 0x87,
            (byte) 0xc8, (byte) 0x8d, (byte) 0x98, (byte) 0xc6, (byte) 0xe0, (byte) 0xbc,
            (byte) 0x85, (byte) 0xb9, (byte) 0x04, (byte) 0x43, (byte) 0xa9, (byte) 0x41,
            (byte) 0xce, (byte) 0x42, (byte) 0x1a, (byte) 0x57, (byte) 0x10, (byte) 0xd8,
            (byte) 0xe4, (byte) 0x6a, (byte) 0x51, (byte) 0x10, (byte) 0x0a, (byte) 0xec,
            (byte) 0xe4, (byte) 0x57, (byte) 0xc7, (byte) 0xee, (byte) 0xe9, (byte) 0xd6,
            (byte) 0xcb, (byte) 0x3e, (byte) 0xba, (byte) 0xfa, (byte) 0xe9, (byte) 0x0e,
            (byte) 0xed, (byte) 0x87, (byte) 0x04, (byte) 0x9a, (byte) 0x48, (byte) 0xba,
            (byte) 0xaf, (byte) 0x08, (byte) 0xf5, (byte) 0x02, (byte) 0x81, (byte) 0xc1,
            (byte) 0x00, (byte) 0xcb, (byte) 0x63, (byte) 0xd6, (byte) 0x54, (byte) 0xb6,
            (byte) 0xf3, (byte) 0xf3, (byte) 0x8c, (byte) 0xf8, (byte) 0xd0, (byte) 0xd2,
            (byte) 0x84, (byte) 0xc1, (byte) 0xf5, (byte) 0x12, (byte) 0xe0, (byte) 0x02,
            (byte) 0x80, (byte) 0x42, (byte) 0x92, (byte) 0x4e, (byte) 0xa4, (byte) 0x5c,
            (byte) 0xa5, (byte) 0x64, (byte) 0xec, (byte) 0xb7, (byte) 0xdc, (byte) 0xe0,
            (byte) 0x2d, (byte) 0x5d, (byte) 0xac, (byte) 0x0e, (byte) 0x24, (byte) 0x48,
            (byte) 0x13, (byte) 0x05, (byte) 0xe8, (byte) 0xff, (byte) 0x96, (byte) 0x93,
            (byte) 0xba, (byte) 0x3c, (byte) 0x88, (byte) 0xcc, (byte) 0x80, (byte) 0xf9,
            (byte) 0xdb, (byte) 0xa8, (byte) 0x4d, (byte) 0x86, (byte) 0x47, (byte) 0xc8,
            (byte) 0xbf, (byte) 0x34, (byte) 0x2d, (byte) 0xda, (byte) 0xb6, (byte) 0x28,
            (byte) 0xf0, (byte) 0x1e, (byte) 0xd2, (byte) 0x46, (byte) 0x0d, (byte) 0x6f,
            (byte) 0x36, (byte) 0x8e, (byte) 0x84, (byte) 0xd8, (byte) 0xaf, (byte) 0xf7,
            (byte) 0x69, (byte) 0x23, (byte) 0x77, (byte) 0xfb, (byte) 0xc5, (byte) 0x04,
            (byte) 0x08, (byte) 0x18, (byte) 0xac, (byte) 0x85, (byte) 0x80, (byte) 0x87,
            (byte) 0x1c, (byte) 0xfe, (byte) 0x8e, (byte) 0x5d, (byte) 0x00, (byte) 0x7f,
            (byte) 0x5b, (byte) 0x33, (byte) 0xf5, (byte) 0xdf, (byte) 0x70, (byte) 0x81,
            (byte) 0xad, (byte) 0x81, (byte) 0xf4, (byte) 0x5a, (byte) 0x37, (byte) 0x8a,
            (byte) 0x79, (byte) 0x09, (byte) 0xc5, (byte) 0x55, (byte) 0xab, (byte) 0x58,
            (byte) 0x7c, (byte) 0x47, (byte) 0xca, (byte) 0xa5, (byte) 0x80, (byte) 0x49,
            (byte) 0x5f, (byte) 0x71, (byte) 0x83, (byte) 0xfb, (byte) 0x3b, (byte) 0x06,
            (byte) 0xec, (byte) 0x75, (byte) 0x23, (byte) 0xc4, (byte) 0x32, (byte) 0xc7,
            (byte) 0x18, (byte) 0xf6, (byte) 0x82, (byte) 0x95, (byte) 0x98, (byte) 0x39,
            (byte) 0xf7, (byte) 0x92, (byte) 0x31, (byte) 0xc0, (byte) 0x89, (byte) 0xba,
            (byte) 0xd4, (byte) 0xd4, (byte) 0x58, (byte) 0x4e, (byte) 0x38, (byte) 0x35,
            (byte) 0x10, (byte) 0xb9, (byte) 0xf1, (byte) 0x27, (byte) 0xdc, (byte) 0xff,
            (byte) 0xc7, (byte) 0xb2, (byte) 0xba, (byte) 0x1f, (byte) 0x27, (byte) 0xaf,
            (byte) 0x99, (byte) 0xd5, (byte) 0xb0, (byte) 0x39, (byte) 0xe7, (byte) 0x43,
            (byte) 0x88, (byte) 0xd3, (byte) 0xce, (byte) 0x38, (byte) 0xc2, (byte) 0x99,
            (byte) 0x43, (byte) 0xfc, (byte) 0x8a, (byte) 0xe3, (byte) 0x60, (byte) 0x0d,
            (byte) 0x0a, (byte) 0xb8, (byte) 0xc4, (byte) 0x29, (byte) 0xca, (byte) 0x0d,
            (byte) 0x30, (byte) 0xaf, (byte) 0xca, (byte) 0xd0, (byte) 0xaa, (byte) 0x67,
            (byte) 0xb1, (byte) 0xdd, (byte) 0xdb, (byte) 0x7a, (byte) 0x11, (byte) 0xad,
            (byte) 0xeb, (byte) 0x02, (byte) 0x81, (byte) 0xc0, (byte) 0x71, (byte) 0xb8,
            (byte) 0xcf, (byte) 0x72, (byte) 0x35, (byte) 0x67, (byte) 0xb5, (byte) 0x38,
            (byte) 0x8f, (byte) 0x16, (byte) 0xd3, (byte) 0x29, (byte) 0x82, (byte) 0x35,
            (byte) 0x21, (byte) 0xd4, (byte) 0x49, (byte) 0x20, (byte) 0x74, (byte) 0x2d,
            (byte) 0xc0, (byte) 0xa4, (byte) 0x44, (byte) 0xf5, (byte) 0xd8, (byte) 0xc9,
            (byte) 0xe9, (byte) 0x90, (byte) 0x1d, (byte) 0xde, (byte) 0x3a, (byte) 0xa6,
            (byte) 0xd7, (byte) 0xe5, (byte) 0xe8, (byte) 0x4e, (byte) 0x83, (byte) 0xd7,
            (byte) 0xe6, (byte) 0x2f, (byte) 0x92, (byte) 0x31, (byte) 0x21, (byte) 0x3f,
            (byte) 0xfa, (byte) 0xd2, (byte) 0x85, (byte) 0x92, (byte) 0x1f, (byte) 0xff,
            (byte) 0x61, (byte) 0x00, (byte) 0xf6, (byte) 0xda, (byte) 0x6e, (byte) 0xc6,
            (byte) 0x7f, (byte) 0x5a, (byte) 0x35, (byte) 0x79, (byte) 0xdc, (byte) 0xdc,
            (byte) 0xa3, (byte) 0x2e, (byte) 0x9f, (byte) 0x35, (byte) 0xd1, (byte) 0x5c,
            (byte) 0xda, (byte) 0xb9, (byte) 0xf7, (byte) 0x58, (byte) 0x7d, (byte) 0x4f,
            (byte) 0xb6, (byte) 0x13, (byte) 0xd7, (byte) 0x2c, (byte) 0x0a, (byte) 0xa8,
            (byte) 0x4d, (byte) 0xf2, (byte) 0xe4, (byte) 0x67, (byte) 0x4f, (byte) 0x8b,
            (byte) 0xa6, (byte) 0xca, (byte) 0x1a, (byte) 0xbb, (byte) 0x02, (byte) 0x63,
            (byte) 0x8f, (byte) 0xb7, (byte) 0x46, (byte) 0xec, (byte) 0x7a, (byte) 0x8a,
            (byte) 0x09, (byte) 0x0a, (byte) 0x45, (byte) 0x3a, (byte) 0x8d, (byte) 0xa8,
            (byte) 0x83, (byte) 0x4b, (byte) 0x0a, (byte) 0xdb, (byte) 0x4b, (byte) 0x99,
            (byte) 0xf3, (byte) 0x69, (byte) 0x95, (byte) 0xf0, (byte) 0xcf, (byte) 0xe9,
            (byte) 0xf7, (byte) 0x67, (byte) 0xc9, (byte) 0x45, (byte) 0x18, (byte) 0x2f,
            (byte) 0xf0, (byte) 0x5c, (byte) 0x90, (byte) 0xbd, (byte) 0xa6, (byte) 0x66,
            (byte) 0x8c, (byte) 0xfe, (byte) 0x60, (byte) 0x5d, (byte) 0x6c, (byte) 0x27,
            (byte) 0xec, (byte) 0xc1, (byte) 0x84, (byte) 0xb2, (byte) 0xa1, (byte) 0x97,
            (byte) 0x9e, (byte) 0x16, (byte) 0x29, (byte) 0xa7, (byte) 0xe0, (byte) 0x38,
            (byte) 0xa2, (byte) 0x36, (byte) 0x05, (byte) 0x5f, (byte) 0xda, (byte) 0x72,
            (byte) 0x1a, (byte) 0x5f, (byte) 0xa8, (byte) 0x7d, (byte) 0x41, (byte) 0x35,
            (byte) 0xf6, (byte) 0x4e, (byte) 0x0a, (byte) 0x88, (byte) 0x8e, (byte) 0x00,
            (byte) 0x98, (byte) 0xa6, (byte) 0xca, (byte) 0xc1, (byte) 0xdf, (byte) 0x72,
            (byte) 0x6c, (byte) 0xfe, (byte) 0x29, (byte) 0xbe, (byte) 0xa3, (byte) 0x9b,
            (byte) 0x0b, (byte) 0x5c, (byte) 0x0b, (byte) 0x9d, (byte) 0xa7, (byte) 0x71,
            (byte) 0xce, (byte) 0x04, (byte) 0xfa, (byte) 0xac, (byte) 0x01, (byte) 0x8d,
            (byte) 0x52, (byte) 0xa0, (byte) 0x3d, (byte) 0xdd, (byte) 0x02, (byte) 0x81,
            (byte) 0xc1, (byte) 0x00, (byte) 0xc1, (byte) 0xc0, (byte) 0x2e, (byte) 0xa9,
            (byte) 0xee, (byte) 0xca, (byte) 0xff, (byte) 0xe4, (byte) 0xf8, (byte) 0x15,
            (byte) 0xfd, (byte) 0xa5, (byte) 0x68, (byte) 0x1b, (byte) 0x2d, (byte) 0x4a,
            (byte) 0xe6, (byte) 0x37, (byte) 0x06, (byte) 0xb3, (byte) 0xd7, (byte) 0x64,
            (byte) 0xad, (byte) 0xb9, (byte) 0x05, (byte) 0x26, (byte) 0x97, (byte) 0x94,
            (byte) 0x3a, (byte) 0x9e, (byte) 0x1c, (byte) 0xd0, (byte) 0xcd, (byte) 0x7b,
            (byte) 0xf4, (byte) 0x88, (byte) 0xe2, (byte) 0xa5, (byte) 0x6d, (byte) 0xed,
            (byte) 0x24, (byte) 0x77, (byte) 0x52, (byte) 0x39, (byte) 0x43, (byte) 0x0f,
            (byte) 0x4e, (byte) 0x75, (byte) 0xd8, (byte) 0xa3, (byte) 0x59, (byte) 0x5a,
            (byte) 0xc2, (byte) 0xba, (byte) 0x9a, (byte) 0x5b, (byte) 0x60, (byte) 0x31,
            (byte) 0x0d, (byte) 0x58, (byte) 0x89, (byte) 0x13, (byte) 0xe8, (byte) 0x95,
            (byte) 0xdd, (byte) 0xae, (byte) 0xcc, (byte) 0x1f, (byte) 0x73, (byte) 0x48,
            (byte) 0x55, (byte) 0xd8, (byte) 0xfb, (byte) 0x67, (byte) 0xce, (byte) 0x18,
            (byte) 0x85, (byte) 0x59, (byte) 0xad, (byte) 0x1f, (byte) 0x93, (byte) 0xe1,
            (byte) 0xb7, (byte) 0x54, (byte) 0x80, (byte) 0x8e, (byte) 0x5f, (byte) 0xbc,
            (byte) 0x1c, (byte) 0x96, (byte) 0x66, (byte) 0x2e, (byte) 0x40, (byte) 0x17,
            (byte) 0x2e, (byte) 0x01, (byte) 0x7a, (byte) 0x7d, (byte) 0xaa, (byte) 0xff,
            (byte) 0xa3, (byte) 0xd2, (byte) 0xdf, (byte) 0xe2, (byte) 0xf3, (byte) 0x54,
            (byte) 0x51, (byte) 0xeb, (byte) 0xba, (byte) 0x7c, (byte) 0x2a, (byte) 0x22,
            (byte) 0xc6, (byte) 0x42, (byte) 0xbc, (byte) 0xa1, (byte) 0x6c, (byte) 0xcf,
            (byte) 0x73, (byte) 0x2e, (byte) 0x07, (byte) 0xfc, (byte) 0xf5, (byte) 0x67,
            (byte) 0x25, (byte) 0xd0, (byte) 0xfa, (byte) 0xeb, (byte) 0xb4, (byte) 0xd4,
            (byte) 0x19, (byte) 0xcc, (byte) 0x64, (byte) 0xa1, (byte) 0x2e, (byte) 0x78,
            (byte) 0x45, (byte) 0xd9, (byte) 0x7f, (byte) 0x1b, (byte) 0x4c, (byte) 0x10,
            (byte) 0x31, (byte) 0x44, (byte) 0xe8, (byte) 0xcc, (byte) 0xf9, (byte) 0x1b,
            (byte) 0x87, (byte) 0x31, (byte) 0xd6, (byte) 0x69, (byte) 0x85, (byte) 0x4a,
            (byte) 0x49, (byte) 0xf6, (byte) 0xb2, (byte) 0xe0, (byte) 0xb8, (byte) 0x98,
            (byte) 0x3c, (byte) 0xf6, (byte) 0x78, (byte) 0x46, (byte) 0xc8, (byte) 0x3d,
            (byte) 0x60, (byte) 0xc1, (byte) 0xaa, (byte) 0x2f, (byte) 0x28, (byte) 0xa1,
            (byte) 0x14, (byte) 0x6b, (byte) 0x75, (byte) 0x4d, (byte) 0xb1, (byte) 0x3d,
            (byte) 0x80, (byte) 0x49, (byte) 0x33, (byte) 0xfd, (byte) 0x71, (byte) 0xc0,
            (byte) 0x13, (byte) 0x1e, (byte) 0x16, (byte) 0x69, (byte) 0x80, (byte) 0xa4,
            (byte) 0x9c, (byte) 0xd7, (byte) 0x02, (byte) 0x81, (byte) 0xc1, (byte) 0x00,
            (byte) 0x8c, (byte) 0x33, (byte) 0x2d, (byte) 0xd9, (byte) 0xf3, (byte) 0x42,
            (byte) 0x4d, (byte) 0xca, (byte) 0x5e, (byte) 0x60, (byte) 0x14, (byte) 0x10,
            (byte) 0xf6, (byte) 0xf3, (byte) 0x71, (byte) 0x15, (byte) 0x88, (byte) 0x54,
            (byte) 0x84, (byte) 0x21, (byte) 0x04, (byte) 0xb1, (byte) 0xaf, (byte) 0x02,
            (byte) 0x11, (byte) 0x7f, (byte) 0x42, (byte) 0x3e, (byte) 0x86, (byte) 0xcb,
            (byte) 0x6c, (byte) 0xf5, (byte) 0x57, (byte) 0x78, (byte) 0x4a, (byte) 0x03,
            (byte) 0x9b, (byte) 0x80, (byte) 0xc2, (byte) 0x04, (byte) 0x3a, (byte) 0x6b,
            (byte) 0xb3, (byte) 0x30, (byte) 0x31, (byte) 0x7e, (byte) 0xc3, (byte) 0x89,
            (byte) 0x09, (byte) 0x4e, (byte) 0x86, (byte) 0x59, (byte) 0x41, (byte) 0xb5,
            (byte) 0xae, (byte) 0xd5, (byte) 0xc6, (byte) 0x38, (byte) 0xbc, (byte) 0xd7,
            (byte) 0xd7, (byte) 0x8e, (byte) 0xa3, (byte) 0x1a, (byte) 0xde, (byte) 0x32,
            (byte) 0xad, (byte) 0x8d, (byte) 0x15, (byte) 0x81, (byte) 0xfe, (byte) 0xac,
            (byte) 0xbd, (byte) 0xd0, (byte) 0xca, (byte) 0xbc, (byte) 0xd8, (byte) 0x6a,
            (byte) 0xe1, (byte) 0xfe, (byte) 0xda, (byte) 0xc4, (byte) 0xd8, (byte) 0x62,
            (byte) 0x71, (byte) 0x20, (byte) 0xa3, (byte) 0xd3, (byte) 0x06, (byte) 0x11,
            (byte) 0xa9, (byte) 0x53, (byte) 0x7a, (byte) 0x44, (byte) 0x89, (byte) 0x3d,
            (byte) 0x28, (byte) 0x5e, (byte) 0x7d, (byte) 0xf0, (byte) 0x60, (byte) 0xeb,
            (byte) 0xb5, (byte) 0xdf, (byte) 0xed, (byte) 0x4f, (byte) 0x6d, (byte) 0x05,
            (byte) 0x59, (byte) 0x06, (byte) 0xb0, (byte) 0x62, (byte) 0x50, (byte) 0x1c,
            (byte) 0xb7, (byte) 0x2c, (byte) 0x44, (byte) 0xa4, (byte) 0x49, (byte) 0xf8,
            (byte) 0x4f, (byte) 0x4b, (byte) 0xab, (byte) 0x71, (byte) 0x5b, (byte) 0xcb,
            (byte) 0x31, (byte) 0x10, (byte) 0x41, (byte) 0xe0, (byte) 0x1a, (byte) 0x15,
            (byte) 0xdc, (byte) 0x4c, (byte) 0x5d, (byte) 0x4f, (byte) 0x62, (byte) 0x83,
            (byte) 0xa4, (byte) 0x80, (byte) 0x06, (byte) 0x36, (byte) 0xba, (byte) 0xc9,
            (byte) 0xe2, (byte) 0xa4, (byte) 0x11, (byte) 0x98, (byte) 0x6b, (byte) 0x4c,
            (byte) 0xe9, (byte) 0x90, (byte) 0x55, (byte) 0x18, (byte) 0xde, (byte) 0xe1,
            (byte) 0x42, (byte) 0x38, (byte) 0x28, (byte) 0xa3, (byte) 0x54, (byte) 0x56,
            (byte) 0x31, (byte) 0xaf, (byte) 0x5a, (byte) 0xd6, (byte) 0xf0, (byte) 0x26,
            (byte) 0xe0, (byte) 0x7a, (byte) 0xd9, (byte) 0x6c, (byte) 0x64, (byte) 0xca,
            (byte) 0x5d, (byte) 0x6d, (byte) 0x3d, (byte) 0x9a, (byte) 0xfe, (byte) 0x36,
            (byte) 0x93, (byte) 0x9e, (byte) 0x62, (byte) 0x94, (byte) 0xc6, (byte) 0x07,
            (byte) 0x83, (byte) 0x96, (byte) 0xd6, (byte) 0x27, (byte) 0xa6, (byte) 0xd8
    };
    private static final PrivateKey CLIENT_SUITE_B_RSA3072_KEY =
            loadPrivateKey("RSA", CLIENT_SUITE_B_RSA3072_KEY_DATA);

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
    private static final X509Certificate CLIENT_SUITE_B_ECDSA_CERT =
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
    private static final PrivateKey CLIENT_SUITE_B_ECC_KEY =
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

    private WifiNetworkSuggestion.Builder createBuilderWithCommonParams() {
        return createBuilderWithCommonParams(false);
    }

    private WifiNetworkSuggestion.Builder createBuilderWithCommonParams(boolean isPasspoint) {
        WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder();
        if (!isPasspoint) {
            builder.setSsid(TEST_SSID);
            builder.setBssid(MacAddress.fromString(TEST_BSSID));
            builder.setIsEnhancedOpen(false);
            builder.setIsHiddenSsid(true);
        }
        builder.setPriority(TEST_PRIORITY);
        builder.setIsAppInteractionRequired(true);
        builder.setIsUserInteractionRequired(true);
        builder.setIsMetered(true);
        builder.setCarrierId(TelephonyManager.UNKNOWN_CARRIER_ID);
        builder.setCredentialSharedWithUser(true);
        builder.setIsInitialAutojoinEnabled(true);
        builder.setUntrusted(false);
        if (BuildCompat.isAtLeastS()) {
            builder.setOemPaid(false);
            builder.setOemPrivate(false);
            builder.setSubscriptionId(TEST_SUB_ID);
            builder.setPriorityGroup(TEST_PRIORITY_GROUP);
        }
        return builder;
    }

    private void validateCommonParams(WifiNetworkSuggestion suggestion) {
        validateCommonParams(suggestion, false);
    }

    private void validateCommonParams(WifiNetworkSuggestion suggestion, boolean isPasspoint) {
        assertNotNull(suggestion);
        assertNotNull(suggestion.getWifiConfiguration());
        if (!isPasspoint) {
            assertEquals(TEST_SSID, suggestion.getSsid());
            assertEquals(TEST_BSSID, suggestion.getBssid().toString());
            assertFalse(suggestion.isEnhancedOpen());
            assertTrue(suggestion.isHiddenSsid());
        }
        assertEquals(TEST_PRIORITY, suggestion.getPriority());
        assertTrue(suggestion.isAppInteractionRequired());
        assertTrue(suggestion.isUserInteractionRequired());
        assertTrue(suggestion.isMetered());
        assertTrue(suggestion.isCredentialSharedWithUser());
        assertTrue(suggestion.isInitialAutojoinEnabled());
        assertFalse(suggestion.isUntrusted());
        if (BuildCompat.isAtLeastS()) {
            assertFalse(suggestion.isOemPaid());
            assertFalse(suggestion.isOemPrivate());
            assertEquals(TEST_PRIORITY_GROUP, suggestion.getPriorityGroup());
            assertEquals(TEST_SUB_ID, suggestion.getSubscriptionId());
        }
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa2Passphrase() throws Exception {
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                .setWpa2Passphrase(TEST_PASSPHRASE)
                .build();
        validateCommonParams(suggestion);
        assertEquals(TEST_PASSPHRASE, suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Passphrase() throws Exception {
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3Passphrase(TEST_PASSPHRASE)
                        .build();
        validateCommonParams(suggestion);
        assertEquals(TEST_PASSPHRASE, suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWapiPassphrase() throws Exception {
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWapiPassphrase(TEST_PASSPHRASE)
                        .build();
        validateCommonParams(suggestion);
        assertEquals(TEST_PASSPHRASE, suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertNull(suggestion.getPasspointConfig());
    }

    private static WifiEnterpriseConfig createEnterpriseConfig() {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();
        config.setEapMethod(AKA);
        return config;
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa2Enterprise() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = createEnterpriseConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa2EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Enterprise() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = createEnterpriseConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3EnterpriseWithStandardApi() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = createEnterpriseConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseStandardModeConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3EnterpriseWithSuiteBRsaCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_RSA3072_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_RSA3072_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_RSA3072_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3EnterpriseWithSuiteBEccCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Enterprise192bitWithSuiteBRsaCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_RSA3072_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_RSA3072_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_RSA3072_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Enterprise192bitWithSuiteBEccCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWapiEnterprise() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WAPI_CERT);
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWapiEnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Helper function for creating a {@link PasspointConfiguration} for testing.
     *
     * @return {@link PasspointConfiguration}
     */
    private static PasspointConfiguration createPasspointConfig() {
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendly name");
        homeSp.setRoamingConsortiumOis(new long[] {0x55, 0x66});
        Credential cred = new Credential();
        cred.setRealm("realm");
        cred.setUserCredential(null);
        cred.setCertCredential(null);
        cred.setSimCredential(new Credential.SimCredential());
        cred.getSimCredential().setImsi("1234*");
        cred.getSimCredential().setEapType(23); // EAP-AKA
        cred.setCaCertificate(null);
        cred.setClientCertificateChain(null);
        cred.setClientPrivateKey(null);
        PasspointConfiguration config = new PasspointConfiguration();
        config.setHomeSp(homeSp);
        config.setCredential(cred);
        return config;
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithPasspointConfig() throws Exception {
        PasspointConfiguration passpointConfig = createPasspointConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams(true)
                        .setPasspointConfig(passpointConfig)
                        .build();
        validateCommonParams(suggestion, true);
        assertNull(suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertEquals(passpointConfig, suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithCarrierMergedNetwork() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                        .setCarrierMerged(true)
                        .build();
        validateCommonParams(suggestion);
        assertTrue(suggestion.isCarrierMerged());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class with non enterprise
     * network will fail.
     */
    @Test
    public void testBuilderWithCarrierMergedNetworkWithNonEnterpriseNetwork() throws Exception {
        try {
            createBuilderWithCommonParams()
                    .setWpa2Passphrase(TEST_PASSPHRASE)
                    .setCarrierMerged(true)
                    .build();
        } catch (IllegalStateException e) {
            return;
        }
        fail("Did not receive expected IllegalStateException when tried to build a carrier merged "
                + "network suggestion with non enterprise config");
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class with unmetered network
     * will fail.
     */
    @Test
    public void testBuilderWithCarrierMergedNetworkWithUnmeteredNetwork() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        try {
            createBuilderWithCommonParams()
                    .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                    .setCarrierMerged(true)
                    .setIsMetered(false)
                    .build();
        } catch (IllegalStateException e) {
            return;
        }
        fail("Did not receive expected IllegalStateException when tried to build a carrier merged "
                + "network suggestion with unmetered config");
    }

    private static WifiNetworkSuggestion.Builder
    createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
            @NonNull WifiConfiguration network) {
        WifiNetworkSuggestion.Builder suggestionBuilder = new WifiNetworkSuggestion.Builder()
                .setSsid(WifiInfo.sanitizeSsid(network.SSID))
                .setBssid(MacAddress.fromString(network.BSSID));
        if (network.preSharedKey != null) {
            if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                suggestionBuilder.setWpa2Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
            } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                suggestionBuilder.setWpa3Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
            } else {
                fail("Unsupported security type found in saved networks");
            }
        } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.OWE)) {
            suggestionBuilder.setIsEnhancedOpen(true);
        } else if (!network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
            fail("Unsupported security type found in saved networks");
        }
        suggestionBuilder.setIsHiddenSsid(network.hiddenSSID);
        return suggestionBuilder;
    }

    private static class TestNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final CountDownLatch mCountDownLatch;
        public boolean onAvailableCalled = false;
        public boolean onUnavailableCalled = false;
        public NetworkCapabilities networkCapabilities;

        TestNetworkCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onAvailable(Network network, NetworkCapabilities networkCapabilities,
                LinkProperties linkProperties, boolean blocked) {
            onAvailableCalled = true;
            this.networkCapabilities = networkCapabilities;
            mCountDownLatch.countDown();
        }

        @Override
        public void onUnavailable() {
            onUnavailableCalled = true;
            mCountDownLatch.countDown();
        }
    }

    private void assertConnectionEquals(@NonNull WifiConfiguration network,
            @NonNull WifiInfo wifiInfo) {
        assertThat(network.SSID).isEqualTo(wifiInfo.getSSID());
        assertThat(network.BSSID).isEqualTo(wifiInfo.getBSSID());
    }

    /**
     * Tests the entire connection flow using the provided suggestion.
     */
    private void testConnectionFlowWithSuggestion(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @Nullable Integer restrictedNetworkCapability) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // File the network request & wait for the callback.
        mNsNetworkCallback = new TestNetworkCallback(countDownLatch);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // File a request for restricted (oem paid) wifi network.
            NetworkRequest.Builder nrBuilder = new NetworkRequest.Builder()
                    .addTransportType(TRANSPORT_WIFI)
                    .addCapability(NET_CAPABILITY_INTERNET);
            if (restrictedNetworkCapability == null) {
                // If not a restricted connection, a network callback is sufficient.
                mConnectivityManager.registerNetworkCallback(nrBuilder.build(), mNsNetworkCallback);
            } else {
                nrBuilder.addCapability(restrictedNetworkCapability);
                mConnectivityManager.requestNetwork(nrBuilder.build(), mNsNetworkCallback);
            }
            // Add wifi network suggestion.
            assertThat(mWifiManager.addNetworkSuggestions(Arrays.asList(suggestion)))
                    .isEqualTo(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
            // Wait for the request to reach the wifi stack before kick-start periodic scans.
            Thread.sleep(100);
            // Step: Trigger scans periodically to trigger network selection quicker.
            mExecutorService.scheduleAtFixedRate(() -> {
                if (!mWifiManager.startScan()) {
                    Log.w(TAG, "Failed to trigger scan");
                }
            }, 0, DURATION_MILLIS, TimeUnit.MILLISECONDS);
            // now wait for connection to complete and wait for callback
            assertThat(countDownLatch.await(
                    DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        assertThat(mNsNetworkCallback.onAvailableCalled).isTrue();
        assertConnectionEquals(
                network, (WifiInfo) mNsNetworkCallback.networkCapabilities.getTransportInfo());
    }

    /**
     * Connect to a network using suggestion API.
     */
    @Test
    public void testConnectToSuggestion() throws Exception {
        WifiNetworkSuggestion suggestion =
                createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(mTestNetwork)
                        .build();
        testConnectionFlowWithSuggestion(
                mTestNetwork, suggestion, null /* restrictedNetworkCapability */);
    }

    /**
     * Connect to a network using restricted suggestion API.
     *
     * TODO(b/167575586): Wait for S SDK finalization to determine the final minSdkVersion.
     */
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    @Test
    public void testConnectToOemPaidSuggestion() throws Exception {
        WifiNetworkSuggestion suggestion =
                createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(mTestNetwork)
                        .setOemPaid(true)
                        .build();
        testConnectionFlowWithSuggestion(mTestNetwork, suggestion, NET_CAPABILITY_OEM_PAID);
    }

    /**
     * Connect to a network using restricted suggestion API.
     *
     * TODO(b/167575586): Wait for S SDK finalization to determine the final minSdkVersion.
     */
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    @Test
    public void testConnectToOemPrivateSuggestion() throws Exception {
        WifiNetworkSuggestion suggestion =
                createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(mTestNetwork)
                        .setOemPrivate(true)
                        .build();
        testConnectionFlowWithSuggestion(mTestNetwork, suggestion, NET_CAPABILITY_OEM_PRIVATE);
    }
}
