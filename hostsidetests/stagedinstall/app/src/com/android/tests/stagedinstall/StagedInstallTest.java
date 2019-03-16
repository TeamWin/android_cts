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
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    private static final String TAG = "StagedInstallTest";

    private static final String TEST_APP_A = "com.android.tests.stagedinstall.testapp.A";
    private static final String TEST_APP_B = "com.android.tests.stagedinstall.testapp.B";
    private static final String TEST_STATE_FILE = "/data/local/tmp/ctsstagedinstall/state";

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

    // This is marked as @Test to take advantage of @Before/@After methods of this class. Actual
    // purpose of this method to be called before and after each test case of
    // com.android.test.stagedinstall.host.StagedInstallTest to reduce tests flakiness.
    @Test
    public void cleanUp() throws Exception {
        PackageInstaller packageInstaller =
                InstrumentationRegistry.getContext().getPackageManager().getPackageInstaller();
        List<PackageInstaller.SessionInfo> stagedSessions = packageInstaller.getStagedSessions();
        for (PackageInstaller.SessionInfo sessionInfo : stagedSessions) {
            try {
                Log.i(TAG, "abandoning session " + sessionInfo.getSessionId());
                packageInstaller.abandonSession(sessionInfo.getSessionId());
            } catch (Exception e) {
                Log.e(TAG, "Failed to abandon session " + sessionInfo.getSessionId(), e);
            }
        }
        uninstall(TEST_APP_A);
        uninstall(TEST_APP_B);
        Files.deleteIfExists(new File(TEST_STATE_FILE).toPath());
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
        prepareBroadcastReceiver();
        int sessionId = stageSingleApk(
                "StagedInstallTestAppAv1.apk").assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        unregisterBroadcastReceiver();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallStagedApk_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(1);
    }

    @Test
    public void testInstallMultipleStagedApks_Commit() throws Exception {
        prepareBroadcastReceiver();
        int sessionId = stageMultipleApks(
                "StagedInstallTestAppAv1.apk",
                "StagedInstallTestAppBv1.apk")
                .assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TEST_APP_B)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        unregisterBroadcastReceiver();
        // TODO: test that the staged Session is in place and is ready
    }

    @Test
    public void testInstallMultipleStagedApks_VerifyPostReboot() throws Exception {
        // TODO: test that the staged session is applied.
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(1);
        assertThat(getInstalledVersion(TEST_APP_B)).isEqualTo(1);
    }

    @Test
    public void testFailInstallAnotherSessionAlreadyInProgress() throws Exception {
        int sessionId = stageSingleApk(
                "StagedInstallTestAppAv1.apk").assertSuccessful().getSessionId();
        StageSessionResult failedSessionResult = stageSingleApk("StagedInstallTestAppAv1.apk");
        assertThat(failedSessionResult.getErrorMessage()).contains(
                "There is already in-progress committed staged session");
        InstrumentationRegistry.getContext().getPackageManager().getPackageInstaller()
                .abandonSession(sessionId);
    }

    @Test
    public void testAbandonStagedApkBeforeReboot_CommitAndAbandon() throws Exception {
        prepareBroadcastReceiver();
        int sessionId = stageSingleApk(
                "StagedInstallTestAppAv1.apk").assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        PackageInstaller.SessionInfo session = getStagedSessionInfo(sessionId);
        assertThat(session.isStagedSessionReady()).isTrue();
        abandonSession(sessionId);
        session = getStagedSessionInfo(sessionId);
        assertThat(session).isNull();
        unregisterBroadcastReceiver();
    }

    @Test
    public void testAbandonStagedApkBeforeReboot_VerifyPostReboot() throws Exception {
        assertThat(getInstalledVersion(TEST_APP_A)).isEqualTo(-1);
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
                InstrumentationRegistry.getContext().getPackageManager().getPackageInstaller(),
                false);
    }

    private static int createStagedSession(
            PackageInstaller packageInstaller,
            boolean multiPackage) throws Exception {
        PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (multiPackage) {
            sessionParams.setMultiPackage();
        }
        sessionParams.setStaged();

        return packageInstaller.createSession(sessionParams);
    }

    private static StageSessionResult stageSingleApk(String apkFileName) throws Exception {
        Context context = InstrumentationRegistry.getContext();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();

        Pair<Integer, PackageInstaller.Session> sessionPair =
                prepareSingleApkStagedSession(packageInstaller, apkFileName);
        // Commit the session (this will start the installation workflow).
        Log.i(TAG, "Committing session for apk: " + apkFileName);
        sessionPair.second.commit(LocalIntentSender.getIntentSender());
        return new StageSessionResult(sessionPair.first, LocalIntentSender.getIntentSenderResult());
    }

    private static Pair<Integer, PackageInstaller.Session>
            prepareSingleApkStagedSession(PackageInstaller packageInstaller, String apkFileName)
            throws Exception {
        int sessionId = createStagedSession(packageInstaller, false);
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
        return new Pair<>(sessionId, session);
    }

    private static StageSessionResult stageMultipleApks(String... apkFileNames) throws Exception {
        Context context = InstrumentationRegistry.getContext();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        int multiPackageSessionId = createStagedSession(packageInstaller, true);
        PackageInstaller.Session multiPackageSession = packageInstaller.openSession(
                multiPackageSessionId);
        for (String apkFileName : apkFileNames) {
            Pair<Integer, PackageInstaller.Session> sessionPair =
                    prepareSingleApkStagedSession(packageInstaller, apkFileName);
            multiPackageSession.addChildSessionId(sessionPair.first);
        }
        multiPackageSession.commit(LocalIntentSender.getIntentSender());
        return new StageSessionResult(
                multiPackageSessionId, LocalIntentSender.getIntentSenderResult());
    }

    private static void assertSessionApplied(int sessionId) {
        assertSessionState(sessionId,
                (session) ->  assertThat(session.isStagedSessionApplied()).isTrue());
    }

    private static void assertSessionReady(int sessionId) {
        assertSessionState(sessionId,
                (session) ->  assertThat(session.isStagedSessionReady()).isTrue());
    }

    private static void assertSessionState(
            int sessionId, Consumer<PackageInstaller.SessionInfo> assertion) {
        Context context = InstrumentationRegistry.getContext();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();

        List<PackageInstaller.SessionInfo> sessions = packageInstaller.getStagedSessions();
        boolean found = false;
        for (PackageInstaller.SessionInfo session : sessions) {
            if (session.getSessionId() == sessionId) {
                assertion.accept(session);
                found = true;
            }
        }
        assertWithMessage("Expecting to find session in getStagedSession()")
                .that(found).isTrue();

        // Test also that getSessionInfo correctly returns the session.
        PackageInstaller.SessionInfo sessionInfo = packageInstaller.getSessionInfo(sessionId);
        assertion.accept(sessionInfo);
    }

    private static void storeSessionId(int sessionId) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TEST_STATE_FILE))) {
            writer.write("" + sessionId);
        }
    }

    private static int retrieveLastSessionId() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(TEST_STATE_FILE))) {
            return Integer.parseInt(reader.readLine());
        }
    }

    private static final class StageSessionResult {
        private final int sessionId;
        private final Intent result;

        private StageSessionResult(int sessionId, Intent result) {
            this.sessionId = sessionId;
            this.result = result;
        }

        public int getSessionId() {
            return sessionId;
        }

        public String getErrorMessage() {
            int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == -1) {
                throw new AssertionError("PENDING USER ACTION");
            }
            if (status == 0) {
                throw new AssertionError("Result was successful");
            }
            return result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        }

        public StageSessionResult assertSuccessful() {
            assertStatusSuccess(result);
            return this;
        }
    }

    private static void abandonSession(int sessionId) {
        Context context = InstrumentationRegistry.getContext();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        packageInstaller.abandonSession(sessionId);
    }

    /**
     * Returns the session by session Id, or null if no session is found.
     */
    private static PackageInstaller.SessionInfo getStagedSessionInfo(int sessionId) {
        Context context = InstrumentationRegistry.getContext();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        for (PackageInstaller.SessionInfo session : packageInstaller.getStagedSessions()) {
            if (session.getSessionId() == sessionId) {
                return session;
            }
        }
        return null;
    }

    /**
     * TODO: after fixing b/128513530, make sure this returns null after session is aborted
     */
    private static PackageInstaller.SessionInfo getSessionInfo(int sessionId) {
        Context context = InstrumentationRegistry.getContext();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        return packageInstaller.getSessionInfo(sessionId);
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
        Log.i(TAG, "Waiting for session " + sessionId + " to be ready");
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

    private void unregisterBroadcastReceiver() {
        Context context = InstrumentationRegistry.getContext();
        context.unregisterReceiver(mSessionUpdateReceiver);
    }
}
