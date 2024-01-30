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
package android.packageinstaller.uninstall.cts;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.MATCH_ARCHIVED_PACKAGES;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.Flags;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.SearchCondition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.install.lib.LocalIntentSender;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class ArchiveTest {
    private static final String LOG_TAG = ArchiveTest.class.getSimpleName();

    private static final String ARCHIVE_APK =
            "/data/local/tmp/cts/uninstall/CtsArchiveTestApp.apk";
    private static final String ARCHIVE_APK_PACKAGE_NAME =
            "android.packageinstaller.archive.cts.archiveapp";
    private static final String SYSTEM_PACKAGE_NAME = "android";

    private static final long TIMEOUT_MS = 30000;

    private static CompletableFuture<Integer> sUnarchiveId;
    private static CompletableFuture<String> sUnarchiveReceiverPackageName;
    private static CompletableFuture<Boolean> sUnarchiveReceiverAllUsers;

    private Context mContext;
    private UiDevice mUiDevice;
    private PackageManager mPackageManager;
    private PackageInstaller mPackageInstaller;

    @Before
    public void setup() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getTargetContext();
        mPackageManager = mContext.getPackageManager();
        mPackageInstaller = mPackageManager.getPackageInstaller();
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, UnarchiveBroadcastReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        // Unblock UI
        mUiDevice = UiDevice.getInstance(instrumentation);
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        AppOpsUtils.reset(mContext.getPackageName());
        sUnarchiveId = new CompletableFuture<>();
        sUnarchiveReceiverPackageName = new CompletableFuture<>();
        sUnarchiveReceiverAllUsers = new CompletableFuture<>();
    }

    @After
    public void tearDown() {
        uninstallPackage(ARCHIVE_APK_PACKAGE_NAME);
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private void dumpWindowHierarchy() throws InterruptedException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mUiDevice.dumpWindowHierarchy(outputStream);
        String windowHierarchy = outputStream.toString(StandardCharsets.UTF_8.name());

        Log.w(LOG_TAG, "Window hierarchy:");
        for (String line : windowHierarchy.split("\n")) {
            Thread.sleep(10);
            Log.w(LOG_TAG, line);
        }
    }

    private UiObject2 waitFor(SearchCondition<UiObject2> condition)
            throws IOException, InterruptedException {
        final long OneSecond = TimeUnit.SECONDS.toMillis(1);
        final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < TIMEOUT_MS) {
            try {
                var result = mUiDevice.wait(condition, OneSecond);
                if (result == null) {
                    continue;
                }
                return result;
            } catch (Throwable e) {
                Thread.sleep(OneSecond);
            }
        }
        dumpWindowHierarchy();
        fail("Unable to wait for the uninstaller activity");
        return null;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    @Ignore("b/315953294")
    public void requestArchive_confirmationDialog() throws Exception {
        installPackage(ARCHIVE_APK);
        assertFalse(mPackageManager.getPackageInfo(ARCHIVE_APK_PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).applicationInfo.isArchived);
        prepareDevice();
        LocalIntentSender sender = new LocalIntentSender();
        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestArchive(ARCHIVE_APK_PACKAGE_NAME,
                        sender.getIntentSender(),
                        PackageManager.DELETE_SHOW_DIALOG),
                Manifest.permission.DELETE_PACKAGES);
        Intent intent = sender.getResult();
        assertThat(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100)).isEqualTo(
                PackageInstaller.STATUS_PENDING_USER_ACTION);

        Intent extraIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        extraIntent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        runWithShellPermissionIdentity(() -> mContext.startActivity(extraIntent),
                Manifest.permission.DELETE_PACKAGES);

        // wait for device idle
        mUiDevice.waitForIdle();

        UiObject2 headerTitle = waitFor(
                Until.findObject(By.res(SYSTEM_PACKAGE_NAME, "alertTitle")));
        UiObject2 message = waitFor(Until.findObject(By.res(SYSTEM_PACKAGE_NAME, "message")));
        assertThat(headerTitle.getText()).contains("Archive");
        assertThat(message.getText()).contains("data will be saved");

        // Confirm uninstall
        UiObject2 clickableView = mUiDevice.findObject(By.res(SYSTEM_PACKAGE_NAME, "button1"));
        if (clickableView == null) {
            Assert.fail("OK button not shown");
        }
        clickableView.click();

        for (int i = 0; i < 30; i++) {
            // We can't detect the confirmation Toast with UiAutomator, so we'll poll
            Thread.sleep(500);
            if (!isInstalled()) {
                break;
            }
        }
        assertTrue(mPackageManager.getPackageInfo(ARCHIVE_APK_PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).applicationInfo.isArchived);
    }

    @Test
    public void unarchiveApp_weakPermissions() throws Exception {
        installPackage(ARCHIVE_APK);
        LocalIntentSender archiveSender = new LocalIntentSender();
        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestArchive(ARCHIVE_APK_PACKAGE_NAME,
                        archiveSender.getIntentSender(), 0),
                Manifest.permission.DELETE_PACKAGES);
        Intent archiveIntent = archiveSender.getResult();
        assertThat(archiveIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100)).isEqualTo(
                PackageInstaller.STATUS_SUCCESS);

        SessionListener sessionListener = new SessionListener();
        mPackageInstaller.registerSessionCallback(sessionListener,
                new Handler(Looper.getMainLooper()));

        LocalIntentSender unarchiveSender = new LocalIntentSender();
        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestUnarchive(ARCHIVE_APK_PACKAGE_NAME,
                        unarchiveSender.getIntentSender()),
                Manifest.permission.REQUEST_INSTALL_PACKAGES);
        Intent unarchiveIntent = unarchiveSender.pollResult(5, TimeUnit.SECONDS);
        assertThat(unarchiveIntent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)).isEqualTo(
                ARCHIVE_APK_PACKAGE_NAME);
        assertThat(unarchiveIntent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_STATUS,
                -100)).isEqualTo(
                PackageInstaller.STATUS_PENDING_USER_ACTION);

        Intent unarchiveExtraIntent = unarchiveIntent.getParcelableExtra(Intent.EXTRA_INTENT,
                Intent.class);
        unarchiveExtraIntent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        prepareDevice();
        mContext.startActivity(unarchiveExtraIntent);
        mUiDevice.waitForIdle();

        assertThat(waitFor(Until.findObject(By.textContains("Restore")))).isNotNull();

        UiObject2 clickableView = mUiDevice.findObject(By.res(SYSTEM_PACKAGE_NAME, "button1"));
        if (clickableView == null) {
            Assert.fail("Restore button not shown");
        }
        clickableView.click();

        assertThat(sUnarchiveReceiverPackageName.get(10, TimeUnit.SECONDS)).isEqualTo(
                ARCHIVE_APK_PACKAGE_NAME);
        int unarchiveId = sUnarchiveId.get(10, TimeUnit.MILLISECONDS);

        mPackageInstaller.abandonSession(unarchiveId);
    }

    private void prepareDevice() throws Exception {
        mUiDevice.waitForIdle();
        // wake up the screen
        mUiDevice.wakeUp();
        // unlock the keyguard or the expected window is by systemui or other alert window
        mUiDevice.pressMenu();
        // dismiss the system alert window for requesting permissions
        mUiDevice.pressBack();
        // return to home/launcher to prevent from being obscured by systemui or other alert window
        mUiDevice.pressHome();
        // Wait for device idle
        mUiDevice.waitForIdle();
    }

    private void installPackage(String path) {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                String.format("pm install -r -i %s -t -g %s", mContext.getPackageName(),
                        path)));
    }

    private boolean isInstalled() {
        Log.d(LOG_TAG, "Testing if package " + ARCHIVE_APK_PACKAGE_NAME + " is installed for user "
                + mContext.getUser());
        try {
            mContext.getPackageManager().getPackageInfo(ARCHIVE_APK_PACKAGE_NAME, /* flags= */ 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.v(LOG_TAG, "Package " + ARCHIVE_APK_PACKAGE_NAME + " not installed for user "
                    + mContext.getUser() + ": " + e);
            return false;
        }
    }

    public static class UnarchiveBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(Intent.ACTION_UNARCHIVE_PACKAGE)) {
                return;
            }
            if (sUnarchiveId == null) {
                sUnarchiveId = new CompletableFuture<>();
            }
            sUnarchiveId.complete(intent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_ID, -1));
            if (sUnarchiveReceiverPackageName == null) {
                sUnarchiveReceiverPackageName = new CompletableFuture<>();
            }
            sUnarchiveReceiverPackageName.complete(
                    intent.getStringExtra(PackageInstaller.EXTRA_UNARCHIVE_PACKAGE_NAME));
            if (sUnarchiveReceiverAllUsers == null) {
                sUnarchiveReceiverAllUsers = new CompletableFuture<>();
            }
            sUnarchiveReceiverAllUsers.complete(
                    intent.getBooleanExtra(PackageInstaller.EXTRA_UNARCHIVE_ALL_USERS,
                            true /* defaultValue */));
        }
    }

    static class SessionListener extends PackageInstaller.SessionCallback {

        final CompletableFuture<Integer> mSessionIdCreated = new CompletableFuture<>();
        final CompletableFuture<Integer> mSessionIdFinished = new CompletableFuture<>();

        @Override
        public void onCreated(int sessionId) {
            mSessionIdCreated.complete(sessionId);
        }

        @Override
        public void onBadgingChanged(int sessionId) {
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) {
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            mSessionIdFinished.complete(sessionId);
        }
    }
}
