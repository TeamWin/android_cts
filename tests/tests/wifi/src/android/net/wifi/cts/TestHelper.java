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

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiAutomation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkCapabilitiesProto;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.WorkSource;
import android.support.test.uiautomator.UiDevice;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.os.BuildCompat;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Class to hold helper methods that are repeated across wifi CTS tests.
 */
public class TestHelper {
    private static final String TAG = "WifiTestHelper";

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final UiDevice mUiDevice;

    private static final int DURATION_MILLIS = 10_000;
    private static final int DURATION_NETWORK_CONNECTION_MILLIS = 40_000;
    private static final int DURATION_SCREEN_TOGGLE_MILLIS = 2000;
    private static final int DURATION_UI_INTERACTION_MILLIS = 25_000;
    private static final int SCAN_RETRY_CNT_TO_FIND_MATCHING_BSSID = 3;

    public TestHelper(@NonNull Context context, @NonNull UiDevice uiDevice) {
        mContext = context;
        mWifiManager = context.getSystemService(WifiManager.class);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mUiDevice = uiDevice;
    }

    public void turnScreenOn() throws Exception {
        mUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE_MILLIS);
    }

    public void turnScreenOff() throws Exception {
        mUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE_MILLIS);
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
     *
     * @param wifiManager WifiManager service
     * @param savedNetworks List of saved networks on the device.
     */
    public static List<WifiConfiguration> findMatchingSavedNetworksWithBssid(
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
                assertThat(countDownLatch.await(DURATION_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
            } finally {
                wifiManager.unregisterScanResultsCallback(scanResultsCallback);
            }
            List<ScanResult> scanResults = wifiManager.getScanResults();
            if (scanResults == null || scanResults.isEmpty()) fail("No scan results available");
            for (ScanResult scanResult : scanResults) {
                WifiConfiguration matchingNetwork = savedNetworks.stream()
                        .filter(network -> TextUtils.equals(
                                scanResult.SSID, WifiInfo.sanitizeSsid(network.SSID)))
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

    /**
     * Convert the provided saved network to a corresponding suggestion builder.
     */
    public static WifiNetworkSuggestion.Builder
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


    /**
     * Convert the provided saved network to a corresponding specifier builder.
     */
    public static WifiNetworkSpecifier.Builder createSpecifierBuilderWithCredentialFromSavedNetwork(
            @NonNull WifiConfiguration network) {
        WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(network.SSID));
        if (network.preSharedKey != null) {
            if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                specifierBuilder.setWpa2Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
            } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                specifierBuilder.setWpa3Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
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

    /**
     * Convert the provided saved network to a corresponding specifier builder.
     */
    public static WifiNetworkSpecifier.Builder
            createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
            @NonNull WifiConfiguration network) {
        return createSpecifierBuilderWithCredentialFromSavedNetwork(network)
                .setBssid(MacAddress.fromString(network.BSSID));
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

    private static void assertConnectionEquals(@NonNull WifiConfiguration network,
            @NonNull WifiInfo wifiInfo) {
        assertThat(network.SSID).isEqualTo(wifiInfo.getSSID());
        assertThat(network.BSSID).isEqualTo(wifiInfo.getBSSID());
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
     *
     * @param network saved network from the device to use for the connection.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithConnect(
            @NonNull WifiConfiguration network) {
        CountDownLatch countDownLatchAl = new CountDownLatch(1);
        CountDownLatch countDownLatchNr = new CountDownLatch(1);
        TestActionListener actionListener = new TestActionListener(countDownLatchAl);
        TestNetworkCallback testNetworkCallback = new TestNetworkCallback(countDownLatchNr);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // File a callback for wifi network.
            mConnectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            // Needed to ensure that the restricted concurrent connection does not
                            // match this request.
                            .addUnwantedCapability(NET_CAPABILITY_OEM_PAID)
                            .addUnwantedCapability(NET_CAPABILITY_OEM_PRIVATE)
                            .build(),
                    testNetworkCallback);
            // Trigger the connection.
            mWifiManager.connect(network, actionListener);
            // now wait for action listener callback
            assertThat(countDownLatchAl.await(
                    DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            // check if we got the success callback
            assertThat(actionListener.onSuccessCalled).isTrue();

            // Wait for connection to complete & ensure we are connected to the saved network.
            assertThat(countDownLatchNr.await(
                    DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(testNetworkCallback.onAvailableCalled).isTrue();
            assertConnectionEquals(
                    network, (WifiInfo) testNetworkCallback.networkCapabilities.getTransportInfo());
        } catch (InterruptedException e) {
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        return testNetworkCallback;
    }

    /**
     * Tests the entire connection success flow using the provided suggestion.
     *
     * @param network saved network from the device to use for the connection.
     * @param suggestion suggestion to use for the connection.
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param restrictedNetworkCapability Whether this connection should be restricted with
     *                                    the provided capability.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithSuggestion(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @NonNull ScheduledExecutorService executorService,
            @Nullable Integer restrictedNetworkCapability) {
        return testConnectionFlowWithSuggestionInternal(
                network, suggestion, executorService, restrictedNetworkCapability, true);
    }

    /**
     * Tests the connection failure flow using the provided suggestion.
     *
     * @param network saved network from the device to use for the connection.
     * @param suggestion suggestion to use for the connection.
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param restrictedNetworkCapability Whether this connection should be restricted with
     *                                    the provided capability.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFailureFlowWithSuggestion(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @NonNull ScheduledExecutorService executorService,
            @Nullable Integer restrictedNetworkCapability) {
        return testConnectionFlowWithSuggestionInternal(
                network, suggestion, executorService, restrictedNetworkCapability, false);
    }

    /**
     * Tests the entire connection success/failure flow using the provided suggestion.
     *
     * @param network saved network from the device to use for the connection.
     * @param suggestion suggestion to use for the connection.
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param restrictedNetworkCapability Whether this connection should be restricted with
     *                                    the provided capability.
     * @param expectConnectionSuccess Whether to expect connection success or not.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    private ConnectivityManager.NetworkCallback testConnectionFlowWithSuggestionInternal(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @NonNull ScheduledExecutorService executorService,
            @Nullable Integer restrictedNetworkCapability,
            boolean expectConnectionSuccess) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // File the network request & wait for the callback.
        TestNetworkCallback testNetworkCallback = new TestNetworkCallback(countDownLatch);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // File a request for restricted (oem paid) wifi network.
            NetworkRequest.Builder nrBuilder = new NetworkRequest.Builder()
                    .addTransportType(TRANSPORT_WIFI)
                    .addCapability(NET_CAPABILITY_INTERNET);
            if (restrictedNetworkCapability == null) {
                // If not a restricted connection, a network callback is sufficient.
                mConnectivityManager.registerNetworkCallback(
                        nrBuilder.build(), testNetworkCallback);
            } else {
                nrBuilder.addCapability(restrictedNetworkCapability);
                mConnectivityManager.requestNetwork(nrBuilder.build(), testNetworkCallback);
            }
            // Add wifi network suggestion.
            assertThat(mWifiManager.addNetworkSuggestions(Arrays.asList(suggestion)))
                    .isEqualTo(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
            // Wait for the request to reach the wifi stack before kick-start periodic scans.
            Thread.sleep(100);
            // Step: Trigger scans periodically to trigger network selection quicker.
            executorService.scheduleAtFixedRate(() -> {
                if (!mWifiManager.startScan()) {
                    Log.w(TAG, "Failed to trigger scan");
                }
            }, 0, DURATION_MILLIS, TimeUnit.MILLISECONDS);
            if (expectConnectionSuccess) {
                // now wait for connection to complete and wait for callback
                assertThat(countDownLatch.await(
                        DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
                assertThat(testNetworkCallback.onAvailableCalled).isTrue();
                assertConnectionEquals(
                        network,
                        (WifiInfo) testNetworkCallback.networkCapabilities.getTransportInfo());
            } else {
                // now wait for connection to timeout.
                assertThat(countDownLatch.await(
                        DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)).isFalse();
            }
        } catch (InterruptedException e) {
        } finally {
            uiAutomation.dropShellPermissionIdentity();
            executorService.shutdown();
        }
        return testNetworkCallback;
    }

    private static class TestNetworkRequestMatchCallback implements
            WifiManager.NetworkRequestMatchCallback {
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
                    uiLock.wait(DURATION_UI_INTERACTION_MILLIS);
                } catch (InterruptedException e) {
                }
            }
            assertThat(networkRequestMatchCallback.onRegistrationCalled).isTrue();
            assertThat(networkRequestMatchCallback.userSelectionCallback).isNotNull();

            // 2. Wait for matching scan results
            synchronized (uiLock) {
                try {
                    uiLock.wait(DURATION_UI_INTERACTION_MILLIS);
                } catch (InterruptedException e) {
                }
            }
            assertThat(networkRequestMatchCallback.onMatchCalled).isTrue();
            assertThat(networkRequestMatchCallback.matchedScanResults).isNotNull();
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
                    uiLock.wait(DURATION_UI_INTERACTION_MILLIS);
                } catch (InterruptedException e) {
                }
            }
            if (shouldUserReject) {
                assertThat(networkRequestMatchCallback.onAbortCalled).isTrue();
            } else {
                assertThat(networkRequestMatchCallback.onConnectSuccessCalled).isTrue();
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
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithSpecifier(
            WifiConfiguration network, WifiNetworkSpecifier specifier, boolean shouldUserReject) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // Fork a thread to handle the UI interactions.
        Thread uiThread = new Thread(() -> handleUiInteractions(network, shouldUserReject));

        // File the network request & wait for the callback.
        TestNetworkCallback testNetworkCallback = new TestNetworkCallback(countDownLatch);
        try {
            // File a request for wifi network.
            mConnectivityManager.requestNetwork(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilitiesProto.TRANSPORT_WIFI)
                            .removeCapability(NetworkCapabilitiesProto.NET_CAPABILITY_INTERNET)
                            .setNetworkSpecifier(specifier)
                            .build(),
                    testNetworkCallback);
            // Wait for the request to reach the wifi stack before kick-starting the UI
            // interactions.
            Thread.sleep(100);
            // Start the UI interactions.
            uiThread.run();
            // now wait for callback
            assertThat(countDownLatch.await(
                    DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
        }
        if (shouldUserReject) {
            assertThat(testNetworkCallback.onUnavailableCalled).isTrue();
        } else {
            assertThat(testNetworkCallback.onAvailableCalled).isTrue();
            final WifiInfo wifiInfo;
            if (BuildCompat.isAtLeastS()) {
                // WifiInfo in transport info, only available in S.
                wifiInfo = (WifiInfo) testNetworkCallback.networkCapabilities.getTransportInfo();
            } else {
                wifiInfo = mWifiManager.getConnectionInfo();
            }
            assertConnectionEquals(network, wifiInfo);
        }
        try {
            // Ensure that the UI interaction thread has completed.
            uiThread.join(DURATION_UI_INTERACTION_MILLIS);
        } catch (InterruptedException e) {
            fail("UI interaction interrupted");
        }
        return testNetworkCallback;
    }

    /**
     * Returns the number of wifi connections visible at the networking layer.
     */
    public long getNumWifiConnections() {
        Network[] networks = mConnectivityManager.getAllNetworks();
        return Arrays.stream(networks)
                .filter(n ->
                        mConnectivityManager.getNetworkCapabilities(n).hasTransport(TRANSPORT_WIFI))
                .count();
    }
}
