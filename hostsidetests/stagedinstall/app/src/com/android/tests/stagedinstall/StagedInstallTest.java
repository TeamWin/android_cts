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

package com.android.tests.stagedinstall;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This series of tests are meant to be driven by a host, since some of the interactions being
 * tested require the device to be rebooted, and some assertions to be verified post-reboot.
 * The convention used here (not enforced) is that the test methods in this file will be named
 * the same way as the test methods in the "host" class (see e.g.
 * {@code com.android.test.stagedinstall.host.StagedInstallTest}), with an optional suffix preceded
 * by an underscore, in case of multiple phases.
 * Example:
 * - In {@code com.android.test.stagedinstall.host.StagedInstallTest}:
 *
 * @Test
 * public void testInstallStagedApk() throws Exception {
 *  runPhase("testInstallStagedApk_Commit");
 *  getDevice().reboot();
 *  runPhase("testInstallStagedApk_VerifyPostReboot");
 * }
 * - In this class:
 * @Test public void testInstallStagedApk_Commit() throws Exception;
 * @Test public void testInstallStagedApk_VerifyPostReboot() throws Exception;
 */
@RunWith(JUnit4.class)
public class StagedInstallTest {

    private static final String TEST_APP_A = "com.android.tests.stagedinstall.testapp.A";

    @Before
    public void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.DELETE_PACKAGES);
    }

    @After
    public void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testFailInstallIfNoPermission() throws Exception {
        dropShellPermissions();
        try {
            createStagedSession();
            fail(); // Should have thrown SecurityException.
        } catch (SecurityException e) {
            // This would be a better version, but it requires a version of truth not present in the
            // tree yet.
            // assertThat(e).hasMessageThat().containsMatch(...);
            assertThat(e.getMessage()).containsMatch(
                    "Neither user [0-9]+ nor current process has "
                    + "android.permission.INSTALL_PACKAGES");
        }
    }

    @Test
    public void testInstallStagedApk_Commit() throws Exception {
        uninstall(TEST_APP_A);
        prepareBroadcastReceiver();
        int sessionId = stageSingleApk("StagedInstallTestAppAv1.apk");
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        unregisterBroacastReceiver();
        // TODO: test that the staged Session is in place and is ready
    }

    @Test
    public void testInstallStagedApk_VerifyPostReboot() throws Exception {
        // TODO: test that the staged session is applied.
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(1);
        // Cleanup.
        // TODO: This should be done via a target preparer or similar.
        uninstall(TEST_APP_A);
    }

    private static long getInstalledVersion(String packageName) {
        Context context = InstrumentationRegistry.getContext();
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private static int createStagedSession() throws Exception {
        return createStagedSession(
                InstrumentationRegistry.getContext().getPackageManager().getPackageInstaller());
    }

    private static int createStagedSession(PackageInstaller packageInstaller) throws Exception {
        PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sessionParams.setStaged();

        return packageInstaller.createSession(sessionParams);
    }

    private static int stageSingleApk(String apkFileName) throws Exception {
        Context context = InstrumentationRegistry.getContext();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        int sessionId = createStagedSession(packageInstaller);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        try (OutputStream packageInSession = session.openWrite(apkFileName, 0, -1);
             InputStream is =
                     StagedInstallTest.class.getClassLoader().getResourceAsStream(apkFileName)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = is.read(buffer)) >= 0) {
                packageInSession.write(buffer, 0, n);
            }
        }

        // Commit the session (this will start the installation workflow).
        session.commit(LocalIntentSender.getIntentSender());
        assertStatusSuccess(LocalIntentSender.getIntentSenderResult());
        return sessionId;
    }

    private static void uninstall(String packageName) throws Exception {
        // No need to uninstall if the package isn't installed.
        if (getInstalledVersion(packageName) == -1) {
            return;
        }

        Context context = InstrumentationRegistry.getContext();
        PackageManager packageManager = context.getPackageManager();
        PackageInstaller packageInstaller = packageManager.getPackageInstaller();
        packageInstaller.uninstall(packageName, LocalIntentSender.getIntentSender());
        assertStatusSuccess(LocalIntentSender.getIntentSenderResult());
    }

    private static void assertStatusSuccess(Intent result) {
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == -1) {
            throw new AssertionError("PENDING USER ACTION");
        } else if (status > 0) {
            String message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            throw new AssertionError(message == null ? "UNKNOWN FAILURE" : message);
        }
    }

    private final BlockingQueue<PackageInstaller.SessionInfo> mSessionBroadcasts
            = new LinkedBlockingQueue<>();

    // TODO(b/124897340): Move the receiver to its own class and declare it in manifest, when this
    //   will become an explicit broadcast.
    private BroadcastReceiver mSessionUpdateReceiver = null;
    private void prepareBroadcastReceiver() {
        mSessionBroadcasts.clear();
        mSessionUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                PackageInstaller.SessionInfo info =
                        intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
                if (info != null) {
                    try {
                        mSessionBroadcasts.put(info);
                    } catch (InterruptedException e) {

                    }
                }
            }
        };
        IntentFilter sessionUpdatedFilter =
                new IntentFilter(PackageInstaller.ACTION_SESSION_UPDATED);
        Context context = InstrumentationRegistry.getContext();
        context.registerReceiver(mSessionUpdateReceiver, sessionUpdatedFilter);
    }

    private void waitForIsReadyBroadcast(int sessionId) {
        try {
            PackageInstaller.SessionInfo info =
                    mSessionBroadcasts.poll(60, TimeUnit.SECONDS);
            assertThat(info.getSessionId()).isEqualTo(sessionId);
            assertThat(info.isStagedSessionReady()).isTrue();
            assertThat(info.isStagedSessionApplied()).isFalse();
            assertWithMessage(info.getStagedSessionErrorMessage())
                    .that(info.isStagedSessionFailed()).isFalse();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private void unregisterBroacastReceiver() {
        Context context = InstrumentationRegistry.getContext();
        context.unregisterReceiver(mSessionUpdateReceiver);
    }
}
