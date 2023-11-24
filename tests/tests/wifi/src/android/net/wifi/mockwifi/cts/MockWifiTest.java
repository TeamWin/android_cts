/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi.mockwifi.cts;

import static android.content.Context.RECEIVER_EXPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.cts.WifiFeature;
import android.os.Build;
import android.os.PowerManager;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.wifi.mockwifi.MockWifiModemManager;
import android.wifi.mockwifi.nl80211.IClientInterfaceImp;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager/WifiNl80211Manager in instant app mode")
public class MockWifiTest {
    private static final String TAG = "MockWifiTest";

    private static final int TEST_WAIT_DURATION_MS = 10_000;
    private static final int WAIT_MS = 60;
    private static final int WIFI_CONNECT_TIMEOUT_MS = 30_000;

    private static Context sContext;
    private static boolean sShouldRunTest = false;

    private static final int STATE_NULL = 0;
    private static final int STATE_WIFI_CHANGING = 1;
    private static final int STATE_WIFI_ENABLED = 2;
    private static final int STATE_WIFI_DISABLED = 3;
    private static final int STATE_SCANNING = 4;
    private static final int STATE_SCAN_DONE = 5;

    private static class MySync {
        public int expectedState = STATE_NULL;
    }

    private static MySync sMySync;
    private static WifiManager sWifiManager;
    private static ConnectivityManager sConnectivityManager;
    private static UiDevice sUiDevice;
    private static PowerManager.WakeLock sWakeLock;
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static List<ScanResult> sScanResults = null;
    private static NetworkInfo sNetworkInfo =
            new NetworkInfo(ConnectivityManager.TYPE_WIFI, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    "wifi", "unknown");

    private static void turnScreenOnNoDelay() throws Exception {
        if (sWakeLock.isHeld()) sWakeLock.release();
        sUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        sUiDevice.executeShellCommand("wm dismiss-keyguard");
    }

    private static final BroadcastReceiver sReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                synchronized (sMySync) {
                    if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                        sScanResults = sWifiManager.getScanResults();
                    } else {
                        sScanResults = null;
                    }
                    sMySync.expectedState = STATE_SCAN_DONE;
                    sMySync.notifyAll();
                }
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int newState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN);
                synchronized (sMySync) {
                    if (newState == WifiManager.WIFI_STATE_ENABLED) {
                        Log.d(TAG, "*** New WiFi state is ENABLED ***");
                        sMySync.expectedState = STATE_WIFI_ENABLED;
                        sMySync.notifyAll();
                    } else if (newState == WifiManager.WIFI_STATE_DISABLED) {
                        Log.d(TAG, "*** New WiFi state is DISABLED ***");
                        sMySync.expectedState = STATE_WIFI_DISABLED;
                        sMySync.notifyAll();
                    }
                }
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                synchronized (sMySync) {
                    sNetworkInfo =
                            (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (sNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        sMySync.notifyAll();
                    }
                }
            }
        }
    };

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(sContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        sShouldRunTest = true;
        sMySync = new MySync();
        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();
        sConnectivityManager = sContext.getSystemService(ConnectivityManager.class);

        // turn on verbose logging for tests
        sWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(true));
        // Disable scan throttling for tests.
        sWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(false));

        sWakeLock = sContext.getSystemService(PowerManager.class).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, TAG);
        sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        turnScreenOnNoDelay();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.ACTION_PICK_WIFI_NETWORK);
        intentFilter.setPriority(999);

        sContext.registerReceiver(sReceiver, intentFilter, RECEIVER_EXPORTED);

        synchronized (sMySync) {
            sMySync.expectedState = STATE_NULL;
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) {
            return;
        }
        if (!sWifiManager.isWifiEnabled()) {
            setWifiEnabled(true);
        }
        sContext.unregisterReceiver(sReceiver);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);

        // enable Wifi
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        PollingCheck.check("Wifi not enabled", TEST_WAIT_DURATION_MS,
                () -> sWifiManager.isWifiEnabled());

        sWifiManager.startScan();
        waitForConnection(); // ensures that there is at-least 1 saved network on the device.
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        synchronized (sMySync) {
            if (sWifiManager.isWifiEnabled() != enable) {
                // the new state is different, we expect it to change
                sMySync.expectedState = STATE_WIFI_CHANGING;
            } else {
                sMySync.expectedState = (enable ? STATE_WIFI_ENABLED : STATE_WIFI_DISABLED);
            }
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setWifiEnabled(enable));
            waitForExpectedWifiState(enable);
        }
    }

    private static void waitForExpectedWifiState(boolean enabled) throws InterruptedException {
        synchronized (sMySync) {
            long timeout = System.currentTimeMillis() + TEST_WAIT_DURATION_MS;
            int expected = (enabled ? STATE_WIFI_ENABLED : STATE_WIFI_DISABLED);
            while (System.currentTimeMillis() < timeout
                    && sMySync.expectedState != expected) {
                sMySync.wait(WAIT_MS);
            }
            assertEquals(expected, sMySync.expectedState);
        }
    }

    private void waitForNetworkInfoState(NetworkInfo.State state, int timeoutMillis)
            throws Exception {
        synchronized (sMySync) {
            if (sNetworkInfo.getState() == state) return;
            long timeout = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < timeout
                    && sNetworkInfo.getState() != state) {
                sMySync.wait(WAIT_MS);
            }
            assertEquals(state, sNetworkInfo.getState());
        }
    }

    private void waitForConnection() throws Exception {
        waitForNetworkInfoState(NetworkInfo.State.CONNECTED, WIFI_CONNECT_TIMEOUT_MS);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testMockSignalPollOnMockWifi() throws Exception {
        int testRssi = -30;

        MockWifiModemManager sMockModemManager = new MockWifiModemManager(sContext);
        assertNotNull(sMockModemManager);

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            Network wifiCurrentNetwork = sWifiManager.getCurrentNetwork();
            assertNotNull(wifiCurrentNetwork);
            LinkProperties wifiLinkProperties = sConnectivityManager.getLinkProperties(
                    wifiCurrentNetwork);
            String ifaceName = wifiLinkProperties.getInterfaceName();
            WifiInfo wifiInfo = sWifiManager.getConnectionInfo();

            assertTrue(sMockModemManager.connectMockWifiModemService(sContext));
            assertTrue(sMockModemManager.configureClientInterfaceMock(ifaceName,
                    new IClientInterfaceImp.ClientInterfaceMock() {
                        @Override
                        public int[] signalPoll() {
                            return new int[]{
                                    testRssi,
                                    wifiInfo.getTxLinkSpeedMbps(), wifiInfo.getRxLinkSpeedMbps(),
                                    wifiInfo.getFrequency()
                            };
                        }
                    }));
            sMockModemManager.updateConfiguredMockedMethods();
            PollingCheck.check(
                    "Rssi update fail", 30_000,
                    () -> {
                        WifiInfo newWifiInfo = sWifiManager.getConnectionInfo();
                        return newWifiInfo.getRssi() == testRssi;
                    });
        } finally {
            sMockModemManager.disconnectMockWifiModemService();
        }
    }
}
