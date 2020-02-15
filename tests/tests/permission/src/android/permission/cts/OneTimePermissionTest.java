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
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.SystemUtil.eventually;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OneTimePermissionTest {
    private static final String APP_PKG_NAME = "android.permission.cts.appthatrequestpermission";
    private static final String APK =
            "/data/local/tmp/cts/permissions/CtsAppThatRequestsOneTimePermission.apk";
    private static final String EXTRA_FOREGROUND_SERVICE_LIFESPAN =
            "android.permission.cts.OneTimePermissionTest.EXTRA_FOREGROUND_SERVICE_LIFESPAN";

    private static final long ONE_TIME_TIMEOUT_MILLIS = 5000;
    private static final long ONE_TIME_TIMER_LOWER_GRACE_PERIOD = 500;
    private static final long ONE_TIME_TIMER_UPPER_GRACE_PERIOD = 10000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice mUiDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final ActivityManager mActivityManager =
            mContext.getSystemService(ActivityManager.class);

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

        CompletableFuture<Long> exitTime = registerAppExitListener();

        clickOneTimeButton();

        exitApp();

        assertGranted(5000);

        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD);

        assertExpectedLifespan(exitTime, ONE_TIME_TIMEOUT_MILLIS);
    }

    @Test
    public void testForegroundServiceMaintainsPermission() throws Throwable {
        startApp();

        CompletableFuture<Long> exitTime = registerAppExitListener();

        clickOneTimeButton();

        long expectedLifespanMillis = 2 * ONE_TIME_TIMEOUT_MILLIS;
        startAppForegroundService(expectedLifespanMillis);

        exitApp();

        assertGranted(5000);

        assertDenied(expectedLifespanMillis + ONE_TIME_TIMER_UPPER_GRACE_PERIOD);

        assertExpectedLifespan(exitTime, expectedLifespanMillis);

    }

    @Test
    public void testPermissionRevokedOnKill() throws Throwable {
        startApp();

        clickOneTimeButton();

        exitApp();

        assertGranted(5000);

        mUiDevice.waitForIdle();

        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        SystemUtil.runWithShellPermissionIdentity(() ->
                activityManager.killBackgroundProcesses(APP_PKG_NAME));

        assertDenied(500);
    }

    private void assertGrantedState(String s, int permissionGranted, long timeoutMillis) {
        eventually(() -> Assert.assertEquals(s,
                permissionGranted, mContext.getPackageManager()
                        .checkPermission(ACCESS_FINE_LOCATION, APP_PKG_NAME)), timeoutMillis);
    }

    private void assertGranted(long timeoutMillis) {
        assertGrantedState("Permission was never granted", PackageManager.PERMISSION_GRANTED,
                timeoutMillis);
    }

    private void assertDenied(long timeoutMillis) {
        assertGrantedState("Permission was never revoked", PackageManager.PERMISSION_DENIED,
                timeoutMillis);
    }

    private void assertExpectedLifespan(CompletableFuture<Long> exitTime, long expectedLifespan)
            throws InterruptedException, java.util.concurrent.ExecutionException,
            java.util.concurrent.TimeoutException {
        long grantedLength = System.currentTimeMillis() - exitTime.get(0, TimeUnit.MILLISECONDS);
        if (grantedLength + ONE_TIME_TIMER_LOWER_GRACE_PERIOD < expectedLifespan) {
            throw new AssertionError(
                    "The one time permission lived shorter than expected. expected: "
                            + expectedLifespan + "ms but was: " + grantedLength + "ms");
        }
    }

    private void exitApp() {
        eventually(() -> {
            mUiDevice.pressHome();
            mUiDevice.pressBack();
            runWithShellPermissionIdentity(() -> {
                if (mActivityManager.getPackageImportance(APP_PKG_NAME)
                        <= IMPORTANCE_FOREGROUND) {
                    throw new AssertionError("Unable to exit application");
                }
            });
        });
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

    private CompletableFuture<Long> registerAppExitListener() {
        CompletableFuture<Long> exitTimeCallback = new CompletableFuture<>();
        try {
            int uid = mContext.getPackageManager().getPackageUid(APP_PKG_NAME, 0);
            runWithShellPermissionIdentity(() ->
                    mActivityManager.addOnUidImportanceListener(new SingleAppExitListener(
                            uid, IMPORTANCE_FOREGROUND, exitTimeCallback), IMPORTANCE_FOREGROUND));
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError("Package not found.", e);
        }
        return exitTimeCallback;
    }

    private class SingleAppExitListener implements ActivityManager.OnUidImportanceListener {

        private final int mUid;
        private final int mImportance;
        private final CompletableFuture<Long> mCallback;

        SingleAppExitListener(int uid, int importance, CompletableFuture<Long> callback) {
            mUid = uid;
            mImportance = importance;
            mCallback = callback;
        }

        @Override
        public void onUidImportance(int uid, int importance) {
            if (uid == mUid && importance > mImportance) {
                mCallback.complete(System.currentTimeMillis());
                mActivityManager.removeOnUidImportanceListener(this);
            }
        }
    }
}
