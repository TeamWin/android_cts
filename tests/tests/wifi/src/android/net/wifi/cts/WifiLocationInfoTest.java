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

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests location sensitive APIs exposed by Wi-Fi.
 * Ensures that permissions on these APIs are properly enforced.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiLocationInfoTest {
    private static final String TAG = "WifiLocationInfoTest";

    private static final String WIFI_LOCATION_TEST_APP_APK_PATH =
            "/data/local/tmp/cts/wifi/CtsWifiLocationTestApp.apk";
    private static final String WIFI_LOCATION_TEST_APP_PACKAGE_NAME =
            "android.net.wifi.cts.app";
    private static final String WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_ACTIVITY =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".TriggerScanAndReturnStatusActivity";
    private static final String WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_SERVICE =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".TriggerScanAndReturnStatusService";

    private static final int DURATION_MS = 10_000;

    @Rule
    public final ActivityTestRule<WaitForResultActivity> mActivityRule =
            new ActivityTestRule<>(WaitForResultActivity.class);

    private Context mContext;
    private WifiManager mWifiManager;
    private boolean mWasVerboseLoggingEnabled;
    private boolean mWasScanThrottleEnabled;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported
        assumeTrue(WifiFeature.isWifiSupported(mContext));

        mWifiManager = mContext.getSystemService(WifiManager.class);
        assertThat(mWifiManager).isNotNull();

        installApp(WIFI_LOCATION_TEST_APP_APK_PATH);

        // turn on verbose logging for tests
        mWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setVerboseLoggingEnabled(true));
        // Disable scan throttling for tests.
        mWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setScanThrottleEnabled(false));

        if (!mWifiManager.isWifiEnabled()) setWifiEnabled(true);
        PollingCheck.check("Wifi not enabled", DURATION_MS, () -> mWifiManager.isWifiEnabled());
    }

    @After
    public void tearDown() throws Exception {
        if (!WifiFeature.isWifiSupported(mContext)) return;

        uninstallApp(WIFI_LOCATION_TEST_APP_PACKAGE_NAME);

        if (!mWifiManager.isWifiEnabled()) setWifiEnabled(true);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setScanThrottleEnabled(mWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setVerboseLoggingEnabled(mWasVerboseLoggingEnabled));
    }

    private void setWifiEnabled(boolean enable) throws Exception {
        // now trigger the change using shell commands.
        SystemUtil.runShellCommand("svc wifi " + (enable ? "enable" : "disable"));
    }

    private void turnScreenOn() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "input keyevent KEYCODE_WAKEUP");
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(""
                + "wm dismiss-keyguard");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(2_000);
    }

    private void turnScreenOff() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "input keyevent KEYCODE_SLEEP");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(2_000);
    }

    private void installApp(String apk) throws InterruptedException {
        String installResult = SystemUtil.runShellCommand("pm install -r -d " + apk);
        Thread.sleep(10_000);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    private void uninstallApp(String pkg) throws InterruptedException {
        String uninstallResult = SystemUtil.runShellCommand(
                "pm uninstall " + pkg);
        Thread.sleep(10_000);
        assertThat(uninstallResult.trim()).isEqualTo("Success");
    }

    private void startFgActivityAndAssertStatusIs(
            ComponentName componentName, boolean status) throws Exception {
        turnScreenOn();

        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(componentName);
        assertThat(activity.waitForActivityResult(DURATION_MS)).isEqualTo(status);
    }

    private void startBgServiceAndAssertStatusIs(
            ComponentName componentName, boolean status) throws Exception {
        turnScreenOff();

        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startServiceToWaitForResult(componentName);
        assertThat(activity.waitForServiceResult(DURATION_MS)).isEqualTo(status);
    }

    private void triggerScanFgActivityAndAssertStatusIs(boolean status) throws Exception {
        startFgActivityAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_ACTIVITY), status);
    }

    private void triggerScanBgServiceAndAssertStatusIs(boolean status) throws Exception {
        startBgServiceAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_SERVICE), status);
    }

    @Test
    public void testScanTriggerNotAllowedForForegroundActivityWithNoLocationPermission()
            throws Exception {
        triggerScanFgActivityAndAssertStatusIs(false);
    }

    @Test
    public void testScanTriggerAllowedForForegroundActivityWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        Thread.sleep(100);
        triggerScanFgActivityAndAssertStatusIs(true);
    }

    @Test
    public void testScanTriggerAllowedForBackgroundServiceWithBackgroundLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_BACKGROUND_LOCATION);
        Thread.sleep(100);
        triggerScanBgServiceAndAssertStatusIs(true);
    }

    @Test
    public void testScanTriggerNotAllowedForBackgroundServiceWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        Thread.sleep(100);
        triggerScanBgServiceAndAssertStatusIs(false);
    }
}
