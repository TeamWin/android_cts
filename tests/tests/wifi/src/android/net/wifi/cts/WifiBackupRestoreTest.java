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

import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_METERED;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_NONE;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_NOT_METERED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.UiAutomation;
import android.content.Context;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests for wifi backup/restore functionality.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiBackupRestoreTest {
    private Context mContext;
    private WifiManager mWifiManager;
    private UiDevice mUiDevice;
    private boolean mWasVerboseLoggingEnabled;

    private static final int DURATION = 10_000;
    private static final int DURATION_SCREEN_TOGGLE = 2000;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(mContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        mWifiManager = mContext.getSystemService(WifiManager.class);
        assertThat(mWifiManager).isNotNull();

        // turn on verbose logging for tests
        mWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setVerboseLoggingEnabled(true));
        // Disable scan throttling for tests.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setScanThrottleEnabled(false));

        if (!mWifiManager.isWifiEnabled()) setWifiEnabled(true);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        turnScreenOn();
        PollingCheck.check("Wifi not enabled", DURATION, () -> mWifiManager.isWifiEnabled());

        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.getPrivilegedConfiguredNetworks());
        assertWithMessage("Need at least one saved network").that(savedNetworks).isNotEmpty();
    }

    @After
    public void tearDown() throws Exception {
        if (!WifiFeature.isWifiSupported(mContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        if (!mWifiManager.isWifiEnabled()) setWifiEnabled(true);
        turnScreenOff();
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setVerboseLoggingEnabled(mWasVerboseLoggingEnabled));
    }

    private void setWifiEnabled(boolean enable) throws Exception {
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

    private void flipMeteredOverride(WifiConfiguration network) {
        if (network.meteredOverride == METERED_OVERRIDE_NONE) {
            network.meteredOverride = METERED_OVERRIDE_METERED;
        } else if (network.meteredOverride == METERED_OVERRIDE_METERED) {
            network.meteredOverride = METERED_OVERRIDE_NOT_METERED;
        } else if (network.meteredOverride == METERED_OVERRIDE_NOT_METERED) {
            network.meteredOverride = METERED_OVERRIDE_NONE;
        }
    }

    /**
     * Tests for {@link WifiManager#retrieveBackupData()} &
     * {@link WifiManager#restoreBackupData(byte[])}
     */
    @Test
    public void testCanRestoreBackupData() {
        if (!WifiFeature.isWifiSupported(mContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        WifiConfiguration origNetwork = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();

            // Pick any saved network to modify;
            origNetwork = mWifiManager.getConfiguredNetworks().get(0);

            // Retrieve backup data.
            byte[] backupData = mWifiManager.retrieveBackupData();

            // Modify the metered bit.
            final String origNetworkSsid = origNetwork.SSID;
            WifiConfiguration modNetwork = new WifiConfiguration(origNetwork);
            flipMeteredOverride(modNetwork);
            int networkId = mWifiManager.updateNetwork(modNetwork);
            assertThat(networkId).isEqualTo(origNetwork.networkId);
            assertThat(mWifiManager.getConfiguredNetworks()
                    .stream()
                    .filter(n -> n.SSID.equals(origNetworkSsid))
                    .findAny()
                    .get().meteredOverride)
                    .isNotEqualTo(origNetwork.meteredOverride);

            // Restore the original backup data & ensure that the metered bit is back to orig.
            mWifiManager.restoreBackupData(backupData);
            assertThat(mWifiManager.getConfiguredNetworks()
                    .stream()
                    .filter(n -> n.SSID.equals(origNetworkSsid))
                    .findAny()
                    .get().meteredOverride)
                    .isEqualTo(origNetwork.meteredOverride);
        } finally {
            // Restore the orig network
            if (origNetwork != null) {
                mWifiManager.updateNetwork(origNetwork);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests for {@link WifiManager#retrieveSoftApBackupData()} &
     * {@link WifiManager#restoreSoftApBackupData(byte[])}
     */
    @Test
    public void testCanRestoreSoftApBackupData() {
        if (!WifiFeature.isWifiSupported(mContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        SoftApConfiguration origSoftApConfig = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();

            // Retrieve original soft ap config.
            origSoftApConfig = mWifiManager.getSoftApConfiguration();

            // Retrieve backup data.
            byte[] backupData = mWifiManager.retrieveSoftApBackupData();

            // Modify softap config and set it.
            SoftApConfiguration modSoftApConfig = new SoftApConfiguration.Builder(origSoftApConfig)
                    .setSsid(origSoftApConfig.getSsid() + "b")
                    .build();
            mWifiManager.setSoftApConfiguration(modSoftApConfig);
            // Ensure that it does not match the orig softap config.
            assertThat(mWifiManager.getSoftApConfiguration()).isNotEqualTo(origSoftApConfig);

            // Restore the original backup data & ensure that the orig softap config is restored.
            mWifiManager.restoreSoftApBackupData(backupData);
            assertThat(mWifiManager.getSoftApConfiguration()).isEqualTo(origSoftApConfig);
        } finally {
            if (origSoftApConfig != null) {
                mWifiManager.setSoftApConfiguration(origSoftApConfig);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
