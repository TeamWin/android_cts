/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.permission.cts.PermissionUtils.eventually;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.DeviceConfig;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UiAutomatorUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OneTimePermissionTest {
    private static final String APP_PKG_NAME = "android.permission.cts.appthatrequestpermission";
    private static final String APK =
            "/data/local/tmp/cts/permissions/CtsAppThatRequestsOneTimePermission.apk";
    private static final String EXTRA_FOREGROUND_SERVICE_LIFESPAN =
            "android.permission.cts.OneTimePermissionTest.EXTRA_FOREGROUND_SERVICE_LIFESPAN";

    private static final long ONE_TIME_TIMEOUT_MILLIS = 5000;
    private static final long ONE_TIME_REVOKE_DELAY_TIMEOUT = 10000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice mUiDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private String mOldOneTimePermissionTimeoutValue;

    @Before
    public void wakeUpScreen() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");

        SystemUtil.runShellCommand("input keyevent 82");
    }

    @Before
    public void installApp() {
        runShellCommand("pm install -r " + APK);
    }

    @Before
    public void prepareDeviceForOneTime() {
        runWithShellPermissionIdentity(() -> {
            mOldOneTimePermissionTimeoutValue = DeviceConfig.getProperty("permissions",
                    "one_time_permissions_timeout_millis");
            DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                    Long.toString(ONE_TIME_TIMEOUT_MILLIS), false);
        });
    }

    @After
    public void uninstallApp() {
        runShellCommand("pm uninstall " + APP_PKG_NAME);
    }

    @After
    public void restoreDeviceForOneTime() {
        runWithShellPermissionIdentity(
                () -> DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                        mOldOneTimePermissionTimeoutValue, false));
    }

    @Test
    public void testOneTimePermission() throws Throwable {
        startApp();

        clickOneTimeButton();

        pressHome();

        assertGranted(5000);

        long oneTimeStart = System.currentTimeMillis();

        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_REVOKE_DELAY_TIMEOUT);

        long grantedLength = System.currentTimeMillis() - oneTimeStart;
        if (grantedLength < ONE_TIME_TIMEOUT_MILLIS) {
            throw new AssertionError("The one time permission lived shorter than expected");
        }
    }

    @Test
    public void testForegroundServiceMaintainsPermission() throws Throwable {
        startApp();

        clickOneTimeButton();
        long oneTimeStart = System.currentTimeMillis();

        long lifespanMillis = 2 * ONE_TIME_TIMEOUT_MILLIS;
        startAppForegroundService(lifespanMillis);

        pressHome();

        assertGranted(5000);

        assertDenied(lifespanMillis + ONE_TIME_REVOKE_DELAY_TIMEOUT);

        long grantedLength = System.currentTimeMillis() - oneTimeStart;
        if (grantedLength < lifespanMillis) {
            throw new AssertionError(
                    "The one time permission lived shorter than expected. expected: "
                            + lifespanMillis + "ms but was: " + grantedLength);
        }

    }

    @Test
    public void testPermissionRevokedOnKill() throws Throwable {
        startApp();

        clickOneTimeButton();

        pressHome();

        assertGranted(5000);

        mUiDevice.waitForIdle();

        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        SystemUtil.runWithShellPermissionIdentity(() ->
                activityManager.killBackgroundProcesses(APP_PKG_NAME));

        assertDenied(500);
    }

    private void assertGrantedState(String s, int permissionGranted, long timeoutMillis)
            throws Throwable {
        eventually(() -> Assert.assertEquals(s,
                permissionGranted, mContext.getPackageManager()
                        .checkPermission(ACCESS_FINE_LOCATION, APP_PKG_NAME)), timeoutMillis);
    }

    private void assertGranted(long timeoutMillis) throws Throwable {
        assertGrantedState("Permission was never granted", PackageManager.PERMISSION_GRANTED,
                timeoutMillis);
    }

    private void assertDenied(long timeoutMillis) throws Throwable {
        assertGrantedState("Permission was never revoked", PackageManager.PERMISSION_DENIED,
                timeoutMillis);
    }

    private void pressHome() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_HOME");
    }

    private void clickOneTimeButton() throws Throwable {
        UiAutomatorUtils.waitFindObject(By.res(
                "com.android.permissioncontroller:id/permission_allow_one_time_button"), 10000)
                .click();
    }

    /**
     * Start the app. The app will request the permissions.
     */
    private void startApp() {
        Intent startApp = new Intent();
        startApp.setComponent(new ComponentName(APP_PKG_NAME, APP_PKG_NAME + ".RequestPermission"));
        startApp.setFlags(FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(startApp);
    }

    private void startAppForegroundService(long lifespanMillis) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                APP_PKG_NAME, APP_PKG_NAME + ".KeepAliveForegroundService"));
        intent.putExtra(EXTRA_FOREGROUND_SERVICE_LIFESPAN, lifespanMillis);
        mContext.startService(intent);
    }
}
