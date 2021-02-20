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

package android.net.wifi.cts;

import static android.net.NetworkCapabilitiesProto.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilitiesProto.TRANSPORT_WIFI;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
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
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.NetworkRequestMatchCallback;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.WorkSource;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.text.TextUtils;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests multiple concurrent connection flow on devices that support multi STA concurrency
 * (indicated via {@link WifiManager#isMultiStaConcurrencySupported()}.
 *
 * Tests the entire connection flow using {@link WifiNetworkSpecifier} embedded in a
 * {@link NetworkRequest} & passed into {@link ConnectivityManager#requestNetwork(NetworkRequest,
 * ConnectivityManager.NetworkCallback)} along with a concurrent internet connection using
 * {@link WifiManager#connect(int, WifiManager.ActionListener)}.
 *
 * Assumes that all the saved networks is either open/WPA1/WPA2/WPA3 authenticated network.
 *
 * TODO(b/177591382): Refactor some of the utilities to a separate file that are copied over from
 * WifiManagerTest & WifiNetworkSpecifierTest.
 *
 * TODO(b/167575586): Wait for S SDK finalization to determine the final minSdkVersion.
 */
@SdkSuppress(minSdkVersion = 31, codeName = "S")
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MultiStaConcurrencyWifiNetworkSpecifierTest extends WifiJUnit4TestBase {
    private static final String TAG = "MultiStaConcurrencyWifiNetworkSpecifierTest";
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static boolean sWasWifiEnabled;

    private Context mContext;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private UiDevice mUiDevice;
    private WifiConfiguration mTestNetworkForPeerToPeer;
    private WifiConfiguration mTestNetworkForInternetConnection;
    private TestNetworkCallback mNetworkCallback;
    private TestNetworkCallback mNrNetworkCallback;

    private static final int DURATION = 10_000;
    private static final int DURATION_UI_INTERACTION = 25_000;
    private static final int DURATION_NETWORK_CONNECTION = 60_000;
    private static final int DURATION_SCREEN_TOGGLE = 2000;
    private static final int SCAN_RETRY_CNT_TO_FIND_MATCHING_BSSID = 3;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported. Don't use assumeTrue in @BeforeClass
        if (!WifiFeature.isWifiSupported(context)) return;

        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertNotNull(wifiManager);

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
        PollingCheck.check("Wifi not enabled", DURATION, () -> wifiManager.isWifiEnabled());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(context)) return;

        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertNotNull(wifiManager);

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

        // skip the test if WiFi is not supported
        assumeTrue(WifiFeature.isWifiSupported(mContext));
        // skip the test if location is not supported
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION));
        // skip if multi STA not supported.
        assumeTrue(mWifiManager.isMultiStaConcurrencySupported());

        assertTrue("Please enable location for this test!",
                mContext.getSystemService(LocationManager.class).isLocationEnabled());

        // turn screen on
        turnScreenOn();

        // Clear any existing app state before each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));

        // We need 2 AP's for the test. If there are 2 networks saved on the device and in range,
        // use those. Otherwise, check if there are 2 BSSID's in range for the only saved network.
        // This assumes a CTS test environment with at least 2 connectable bssid's (Is that ok?).
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.getPrivilegedConfiguredNetworks());
        List<WifiConfiguration> matchingNetworksWithBssid =
                findMatchingSavedNetworksWithBssid(mWifiManager, savedNetworks);
        assertTrue("Need at least 2 saved network bssids in range",
                matchingNetworksWithBssid.size() >= 2);
        // Pick any 2 bssid for test.
        mTestNetworkForPeerToPeer = matchingNetworksWithBssid.get(0);
        // Try to find a bssid for another saved network in range. If none exists, fallback
        // to using 2 bssid's for the same network.
        mTestNetworkForInternetConnection = matchingNetworksWithBssid.stream()
                .filter(w -> !w.SSID.equals(mTestNetworkForPeerToPeer.SSID))
                .findAny()
                .orElse(matchingNetworksWithBssid.get(1));

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
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
        if (mNrNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNrNetworkCallback);
        }
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
        Thread.sleep(DURATION_SCREEN_TOGGLE);
    }

    private void turnScreenOff() throws Exception {
        mUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE);
    }

    private static class TestScanResultsCallback extends WifiManager.ScanResultsCallback {
        private final CountDownLatch mCountDownLatch;
        public boolean onAvailableCalled = false;

        TestScanResultsCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onScanResultsAvailable() {
            onAvailableCalled = true;
            mCountDownLatch.countDown();
        }
    }

    /**
     * Loops through all the saved networks available in the scan results. Returns a list of
     * WifiConfiguration with the matching bssid filled in {@link WifiConfiguration#BSSID}.
     *
     * Note:
     * a) If there are more than 2 networks with the same SSID, but different credential type, then
     * this matching may pick the wrong one.
     */
    private static List<WifiConfiguration> findMatchingSavedNetworksWithBssid(
            @NonNull WifiManager wifiManager, @NonNull List<WifiConfiguration> savedNetworks) {
        if (savedNetworks.isEmpty()) return Collections.emptyList();
        List<WifiConfiguration> matchingNetworksWithBssids = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        for (int i = 0; i < SCAN_RETRY_CNT_TO_FIND_MATCHING_BSSID; i++) {
            // Trigger a scan to get fresh scan results.
            TestScanResultsCallback scanResultsCallback =
                    new TestScanResultsCallback(countDownLatch);
            try {
                wifiManager.registerScanResultsCallback(
                        Executors.newSingleThreadExecutor(), scanResultsCallback);
                wifiManager.startScan(new WorkSource(myUid()));
                // now wait for callback
                assertTrue(countDownLatch.await(
                        DURATION_NETWORK_CONNECTION, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
            } finally {
                wifiManager.unregisterScanResultsCallback(scanResultsCallback);
            }
            List<ScanResult> scanResults = wifiManager.getScanResults();
            if (scanResults == null || scanResults.isEmpty()) fail("No scan results available");
            for (ScanResult scanResult : scanResults) {
                WifiConfiguration matchingNetwork = savedNetworks.stream()
                        .filter(network -> TextUtils.equals(
                                scanResult.SSID, removeDoubleQuotes(network.SSID)))
                        .findAny()
                        .orElse(null);
                if (matchingNetwork != null) {
                    // make a copy in case we have 2 bssid's for the same network.
                    WifiConfiguration matchingNetworkCopy = new WifiConfiguration(matchingNetwork);
                    matchingNetworkCopy.BSSID = scanResult.BSSID;
                    matchingNetworksWithBssids.add(matchingNetworkCopy);
                }
            }
            if (!matchingNetworksWithBssids.isEmpty()) break;
        }
        return matchingNetworksWithBssids;
    }

    private void assertConnectionEquals(@NonNull WifiConfiguration network,
            @NonNull WifiInfo wifiInfo) {
        assertEquals(network.SSID, wifiInfo.getSSID());
        assertEquals(network.BSSID, wifiInfo.getBSSID());
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

    private static class TestNetworkRequestMatchCallback implements NetworkRequestMatchCallback {
        private final Object mLock;

        public boolean onRegistrationCalled = false;
        public boolean onAbortCalled = false;
        public boolean onMatchCalled = false;
        public boolean onConnectSuccessCalled = false;
        public boolean onConnectFailureCalled = false;
        public WifiManager.NetworkRequestUserSelectionCallback userSelectionCallback = null;
        public List<ScanResult> matchedScanResults = null;

        TestNetworkRequestMatchCallback(Object lock) {
            mLock = lock;
        }

        @Override
        public void onUserSelectionCallbackRegistration(
                WifiManager.NetworkRequestUserSelectionCallback userSelectionCallback) {
            synchronized (mLock) {
                onRegistrationCalled = true;
                this.userSelectionCallback = userSelectionCallback;
                mLock.notify();
            }
        }

        @Override
        public void onAbort() {
            synchronized (mLock) {
                onAbortCalled = true;
                mLock.notify();
            }
        }

        @Override
        public void onMatch(List<ScanResult> scanResults) {
            synchronized (mLock) {
                // This can be invoked multiple times. So, ignore after the first one to avoid
                // disturbing the rest of the test sequence.
                if (onMatchCalled) return;
                onMatchCalled = true;
                matchedScanResults = scanResults;
                mLock.notify();
            }
        }

        @Override
        public void onUserSelectionConnectSuccess(WifiConfiguration config) {
            synchronized (mLock) {
                onConnectSuccessCalled = true;
                mLock.notify();
            }
        }

        @Override
        public void onUserSelectionConnectFailure(WifiConfiguration config) {
            synchronized (mLock) {
                onConnectFailureCalled = true;
                mLock.notify();
            }
        }
    }

    private void handleUiInteractions(WifiConfiguration network, boolean shouldUserReject) {
        // can't use CountDownLatch since there are many callbacks expected and CountDownLatch
        // cannot be reset.
        // TODO(b/177591382): Use ArrayBlockingQueue/LinkedBlockingQueue
        Object uiLock = new Object();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestNetworkRequestMatchCallback networkRequestMatchCallback =
                new TestNetworkRequestMatchCallback(uiLock);
        try {
            uiAutomation.adoptShellPermissionIdentity();

            // 1. Wait for registration callback.
            synchronized (uiLock) {
                try {
                    mWifiManager.registerNetworkRequestMatchCallback(
                            Executors.newSingleThreadExecutor(), networkRequestMatchCallback);
                    uiLock.wait(DURATION_UI_INTERACTION);
                } catch (InterruptedException e) {
                }
            }
            assertTrue(networkRequestMatchCallback.onRegistrationCalled);
            assertNotNull(networkRequestMatchCallback.userSelectionCallback);

            // 2. Wait for matching scan results
            synchronized (uiLock) {
                try {
                    uiLock.wait(DURATION_UI_INTERACTION);
                } catch (InterruptedException e) {
                }
            }
            assertTrue(networkRequestMatchCallback.onMatchCalled);
            assertNotNull(networkRequestMatchCallback.matchedScanResults);
            assertThat(networkRequestMatchCallback.matchedScanResults.size()).isAtLeast(1);

            // 3. Trigger connection to one of the matched networks or reject the request.
            if (shouldUserReject) {
                networkRequestMatchCallback.userSelectionCallback.reject();
            } else {
                networkRequestMatchCallback.userSelectionCallback.select(network);
            }

            // 4. Wait for connection success or abort.
            synchronized (uiLock) {
                try {
                    uiLock.wait(DURATION_UI_INTERACTION);
                } catch (InterruptedException e) {
                }
            }
            if (shouldUserReject) {
                assertTrue(networkRequestMatchCallback.onAbortCalled);
            } else {
                assertTrue(networkRequestMatchCallback.onConnectSuccessCalled);
            }
        } finally {
            mWifiManager.unregisterNetworkRequestMatchCallback(networkRequestMatchCallback);
            uiAutomation.dropShellPermissionIdentity();
        }
    }


    /**
     * Tests the entire connection flow using the provided specifier.
     *
     * @param specifier Specifier to use for network request.
     * @param shouldUserReject Whether to simulate user rejection or not.
     */
    private void testConnectionFlowWithSpecifier(
            WifiConfiguration network, WifiNetworkSpecifier specifier, boolean shouldUserReject) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // Fork a thread to handle the UI interactions.
        Thread uiThread = new Thread(() -> handleUiInteractions(network, shouldUserReject));

        // File the network request & wait for the callback.
        mNrNetworkCallback = new TestNetworkCallback(countDownLatch);
        try {
            // File a request for wifi network.
            mConnectivityManager.requestNetwork(
                    new NetworkRequest.Builder()
                            .addTransportType(TRANSPORT_WIFI)
                            .removeCapability(NET_CAPABILITY_INTERNET)
                            .setNetworkSpecifier(specifier)
                            .build(),
                    mNrNetworkCallback);
            // Wait for the request to reach the wifi stack before kick-starting the UI
            // interactions.
            Thread.sleep(100);
            // Start the UI interactions.
            uiThread.run();
            // now wait for callback
            assertTrue(countDownLatch.await(DURATION_NETWORK_CONNECTION, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
        }
        if (shouldUserReject) {
            assertTrue(mNrNetworkCallback.onUnavailableCalled);
        } else {
            assertTrue(mNrNetworkCallback.onAvailableCalled);
            assertConnectionEquals(
                    network, (WifiInfo) mNrNetworkCallback.networkCapabilities.getTransportInfo());
        }

        try {
            // Ensure that the UI interaction thread has completed.
            uiThread.join(DURATION_UI_INTERACTION);
        } catch (InterruptedException e) {
            fail("UI interaction interrupted");
        }
    }

    private void testSuccessfulConnectionWithSpecifier(
            WifiConfiguration network, WifiNetworkSpecifier specifier) {
        testConnectionFlowWithSpecifier(network, specifier, false);
    }

    private void testUserRejectionWithSpecifier(
            WifiConfiguration network, WifiNetworkSpecifier specifier) {
        testConnectionFlowWithSpecifier(network, specifier, true);
    }

    private static String removeDoubleQuotes(String string) {
        return WifiInfo.sanitizeSsid(string);
    }

    private static class TestActionListener implements WifiManager.ActionListener {
        private final CountDownLatch mCountDownLatch;
        public boolean onSuccessCalled = false;
        public boolean onFailedCalled = false;
        public int failureReason = -1;

        TestActionListener(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() {
            onSuccessCalled = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(int reason) {
            onFailedCalled = true;
            mCountDownLatch.countDown();
        }
    }

    /**
     * Triggers connection to one of the saved networks using {@link WifiManager#connect(
     * WifiConfiguration, WifiManager.ActionListener)}
     */
    private void testConnectionFlowWithConnect(@NonNull WifiConfiguration network) {
        CountDownLatch countDownLatchAl = new CountDownLatch(1);
        CountDownLatch countDownLatchNr = new CountDownLatch(1);
        TestActionListener actionListener = new TestActionListener(countDownLatchAl);
        mNetworkCallback = new TestNetworkCallback(countDownLatchNr);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // File a callback for wifi network.
            mConnectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .addTransportType(TRANSPORT_WIFI)
                            .addCapability(NET_CAPABILITY_INTERNET)
                            .build(),
                    mNetworkCallback);
            // Trigger the connection.
            mWifiManager.connect(network, actionListener);
            // now wait for action listener callback
            assertTrue(countDownLatchAl.await(DURATION_NETWORK_CONNECTION, TimeUnit.MILLISECONDS));
            // check if we got the success callback
            assertTrue(actionListener.onSuccessCalled);

            // Wait for connection to complete & ensure we are connected to the saved network.
            assertTrue(countDownLatchNr.await(DURATION_NETWORK_CONNECTION, TimeUnit.MILLISECONDS));
            assertTrue(mNetworkCallback.onAvailableCalled);
            assertConnectionEquals(
                    network, (WifiInfo) mNetworkCallback.networkCapabilities.getTransportInfo());
        } catch (InterruptedException e) {
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private static WifiNetworkSpecifier.Builder
            createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
            @NonNull WifiConfiguration network) {
        WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder()
                .setSsid(removeDoubleQuotes(network.SSID))
                .setBssid(MacAddress.fromString(network.BSSID));
        if (network.preSharedKey != null) {
            if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                specifierBuilder.setWpa2Passphrase(removeDoubleQuotes(network.preSharedKey));
            } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                specifierBuilder.setWpa3Passphrase(removeDoubleQuotes(network.preSharedKey));
            } else {
                fail("Unsupported security type found in saved networks");
            }
        } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.OWE)) {
            specifierBuilder.setIsEnhancedOpen(true);
        } else if (!network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
            fail("Unsupported security type found in saved networks");
        }
        specifierBuilder.setIsHiddenSsid(network.hiddenSSID);
        return specifierBuilder;
    }

    private long getNumWifiConnections() {
        Network[] networks = mConnectivityManager.getAllNetworks();
        return Arrays.stream(networks)
                .filter(n ->
                        mConnectivityManager.getNetworkCapabilities(n).hasTransport(TRANSPORT_WIFI))
                .count();
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using internet connectivity API.
     * 2. Connect to a network using peer to peer API.
     * 3. Verify that both connections are active.
     */
    @Test
    public void testConnectToPeerPeerNetworkWhenConnectedToInternetNetwork() throws Exception {
        // First trigger internet connectivity.
        testConnectionFlowWithConnect(mTestNetworkForInternetConnection);

        // Now trigger peer to peer connectivity.
        WifiNetworkSpecifier specifier =
                createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetworkForPeerToPeer)
                .build();
        testSuccessfulConnectionWithSpecifier(mTestNetworkForPeerToPeer, specifier);

        // Ensure that there are 2 wifi connections available for apps.
        assertEquals(2, getNumWifiConnections());
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using peer to peer API.
     * 2. Connect to a network using internet connectivity API.
     * 3. Verify that both connections are active.
     */
    @Test
    public void testConnectToInternetNetworkWhenConnectedToPeerPeerNetwork() throws Exception {
        // First trigger peer to peer connectivity.
        WifiNetworkSpecifier specifier =
                createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetworkForPeerToPeer)
                        .build();
        testSuccessfulConnectionWithSpecifier(mTestNetworkForPeerToPeer, specifier);

        // Now trigger internet connectivity.
        testConnectionFlowWithConnect(mTestNetworkForInternetConnection);

        // Ensure that there are 2 wifi connections available for apps.
        assertEquals(2, getNumWifiConnections());
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using internet connectivity API.
     * 2. Trigger connect to a network using peer to peer API which is rejected by user.
     * 3. Verify that only one connection is active.
     */
    @Test
    public void testPeerToPeerConnectionRejectWhenConnectedToInternetNetwork() throws Exception {
        // First trigger internet connectivity.
        testConnectionFlowWithConnect(mTestNetworkForInternetConnection);

        // Now trigger peer to peer connectivity.
        WifiNetworkSpecifier specifier =
                createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetworkForPeerToPeer)
                        .build();
        testUserRejectionWithSpecifier(mTestNetworkForPeerToPeer, specifier);

        // Ensure that there is only 1 wifi connection available for apps.
        assertEquals(1, getNumWifiConnections());
    }
}