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

import static com.android.cts.install.lib.InstallUtils.getPackageInstaller;
import static com.android.tests.stagedinstall.PackageInstallerSessionInfoSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    private File mTestStateFile = new File(
            InstrumentationRegistry.getInstrumentation().getContext().getFilesDir(),
            "ctsstagedinstall_state");

    private static final Duration WAIT_FOR_SESSION_REMOVED_TTL = Duration.ofSeconds(10);
    private static final Duration SLEEP_DURATION = Duration.ofMillis(200);

    private static final String SHIM_PACKAGE_NAME = "com.android.apex.cts.shim";
    private static final TestApp TESTAPP_SAME_NAME_AS_APEX = new TestApp(
            "TestAppSamePackageNameAsApex", SHIM_PACKAGE_NAME, 1, /*isApex*/ false,
            "StagedInstallTestAppSamePackageNameAsApex.apk");
    public static final TestApp Apex2DifferentCertificate = new TestApp(
            "Apex2DifferentCertificate", SHIM_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_different_certificate.apex");
    private static final TestApp Apex2SignedBob = new TestApp(
            "Apex2SignedBob", SHIM_PACKAGE_NAME, 2, /*isApex*/true,
                    "com.android.apex.cts.shim.v2_signed_bob.apex");
    private static final TestApp Apex2SignedBobRot = new TestApp(
            "Apex2SignedBobRot", SHIM_PACKAGE_NAME, 2, /*isApex*/true,
                    "com.android.apex.cts.shim.v2_signed_bob_rot.apex");
    private static final TestApp Apex2SignedEve = new TestApp(
            "Apex2SignedEve", SHIM_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_signed_eve.apex");
    private static final TestApp Apex3SignedBob = new TestApp(
            "Apex3SignedBob", SHIM_PACKAGE_NAME, 3, /*isApex*/true,
                    "com.android.apex.cts.shim.v3_signed_bob.apex");
    private static final TestApp Apex3SignedBobRot = new TestApp(
            "Apex3SignedBobRot", SHIM_PACKAGE_NAME, 3, /*isApex*/true,
                    "com.android.apex.cts.shim.v3_signed_bob_rot.apex");

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

    @Before
    public void clearBroadcastReceiver() {
        SessionUpdateBroadcastReceiver.sessionBroadcasts.clear();
    }

    // This is marked as @Test to take advantage of @Before/@After methods of this class. Actual
    // purpose of this method to be called before and after each test case of
    // com.android.test.stagedinstall.host.StagedInstallTest to reduce tests flakiness.
    @Test
    public void cleanUp() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        List<PackageInstaller.SessionInfo> stagedSessions = packageInstaller.getStagedSessions();
        for (PackageInstaller.SessionInfo sessionInfo : stagedSessions) {
            try {
                Log.i(TAG, "abandoning session " + sessionInfo.getSessionId());
                packageInstaller.abandonSession(sessionInfo.getSessionId());
            } catch (Exception e) {
                Log.e(TAG, "Failed to abandon session " + sessionInfo.getSessionId(), e);
            }
        }
        Uninstall.packages(TestApp.A, TestApp.B);
        Files.deleteIfExists(mTestStateFile.toPath());
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
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
    }

    @Test
    public void testInstallStagedApk_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
    }

    @Test
    public void testInstallStagedApk_AbandonSessionIsNoop() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // Session is in a final state. Test that abandoning the session doesn't remove it from the
        // session database.
        getPackageInstaller().abandonSession(sessionId);
        assertSessionApplied(sessionId);
    }

    @Test
    public void testInstallMultipleStagedApks_Commit() throws Exception {
        int sessionId = stageMultipleApks(TestApp.A1, TestApp.B1)
                .assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
    }

    @Test
    public void testInstallMultipleStagedApks_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);
    }

    @Test
    public void testFailInstallAnotherSessionAlreadyInProgress_BothSinglePackage()
            throws Exception {
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        StageSessionResult failedSessionResult = stageSingleApk(TestApp.A1);
        assertThat(failedSessionResult.getErrorMessage()).contains(
                "There is already in-progress committed staged session");
        getPackageInstaller().abandonSession(sessionId);
    }

    @Test
    public void testFailInstallAnotherSessionAlreadyInProgress_SinglePackageMultiPackage()
            throws Exception {
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        StageSessionResult failedSessionResult = stageMultipleApks(TestApp.A1, TestApp.B1);
        assertThat(failedSessionResult.getErrorMessage()).contains(
                "There is already in-progress committed staged session");
        getPackageInstaller().abandonSession(sessionId);
    }

    @Test
    public void testFailInstallAnotherSessionAlreadyInProgress_MultiPackageSinglePackage()
            throws Exception {
        int sessionId = stageMultipleApks(TestApp.A1, TestApp.B1)
                .assertSuccessful().getSessionId();
        StageSessionResult failedSessionResult = stageSingleApk(TestApp.A1);
        assertThat(failedSessionResult.getErrorMessage()).contains(
                "There is already in-progress committed staged session");
        getPackageInstaller().abandonSession(sessionId);
    }

    @Test
    public void testFailInstallAnotherSessionAlreadyInProgress_BothMultiPackage()
            throws Exception {
        int sessionId = stageMultipleApks(TestApp.A1, TestApp.B1)
                .assertSuccessful().getSessionId();
        StageSessionResult failedSessionResult = stageMultipleApks(TestApp.A1, TestApp.B1);
        assertThat(failedSessionResult.getErrorMessage()).contains(
                "There is already in-progress committed staged session");
        getPackageInstaller().abandonSession(sessionId);
    }

    @Test
    public void testAbandonStagedApkBeforeReboot_CommitAndAbandon() throws Exception {
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        PackageInstaller.SessionInfo session = getStagedSessionInfo(sessionId);
        assertSessionReady(sessionId);
        abandonSession(sessionId);
        assertThat(getStagedSessionInfo(sessionId)).isNull();
        // Allow the session to be removed from PackageInstaller
        Duration spentWaiting = Duration.ZERO;
        while (spentWaiting.compareTo(WAIT_FOR_SESSION_REMOVED_TTL) < 0) {
            session = getSessionInfo(sessionId);
            if (session == null) {
                Log.i(TAG, "Done waiting after " + spentWaiting);
                break;
            }
            try {
                Thread.sleep(SLEEP_DURATION.toMillis());
                spentWaiting = spentWaiting.plus(SLEEP_DURATION);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        assertThat(session).isNull();
    }

    @Test
    public void testAbandonStagedApkBeforeReboot_VerifyPostReboot() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
    }

    @Test
    public void testGetActiveStagedSession() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo session = packageInstaller.getActiveStagedSession();
        assertThat(session.getSessionId()).isEqualTo(sessionId);
    }

    @Test
    public void testGetActiveStagedSessionNoSessionActive() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        PackageInstaller.SessionInfo session = packageInstaller.getActiveStagedSession();
        assertThat(session).isNull();
    }

    @Test
    public void testGetGetActiveStagedSession_MultiApkSession() throws Exception {
        int sessionId = stageMultipleApks(TestApp.A1, TestApp.B1)
                .assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo session = getPackageInstaller().getActiveStagedSession();
        assertThat(session.getSessionId()).isEqualTo(sessionId);
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeNotRequested_Fails_Commit()  throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        Install.single(TestApp.A2).commit();
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        PackageInstaller.SessionInfo sessionInfo = waitForBroadcast(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeRequested_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        Install.single(TestApp.A2).commit();
        int sessionId = stageDowngradeSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeRequested_Fails_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        Install.single(TestApp.A2).commit();
        int sessionId = stageDowngradeSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        PackageInstaller.SessionInfo sessionInfo = waitForBroadcast(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeRequested_DebugBuild_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // App should be downgraded.
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
    }

    @Test
    public void testInstallStagedApex_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        int sessionId = stageSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        // Version shouldn't change before reboot.
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
    }

    @Test
    public void testInstallStagedApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testInstallStagedApexAndApk_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        int sessionId = stageMultipleApks(TestApp.Apex2, TestApp.A1)
                .assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        // Version shouldn't change before reboot.
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
    }

    @Test
    public void testInstallStagedApexAndApk_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
    }

    @Test
    public void testsFailsNonStagedApexInstall() throws Exception {
        PackageInstaller installer = getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setInstallAsApex();
        try {
            installer.createSession(params);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains(
                    "APEX files can only be installed as part of a staged session");
        }
    }

    @Test
    public void testInstallStagedNonPreInstalledApex_Fails() throws Exception {
        assertThat(getInstalledVersion(TestApp.NotPreInstalledApex)).isEqualTo(-1);
        int sessionId = stageSingleApk(
                TestApp.ApexNotPreInstalled)
                .assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = waitForBroadcast(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
    }

    @Test
    public void testStageApkWithSameNameAsApexShouldFail_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        int sessionId = stageSingleApk(TESTAPP_SAME_NAME_AS_APEX)
                .assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStageApkWithSameNameAsApexShouldFail_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
    }

    @Test
    public void testNonStagedInstallApkWithSameNameAsApexShouldFail() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        InstallUtils.commitExpectingFailure(AssertionError.class,
                "is an APEX package and can't be installed as an APK",
                Install.single(TESTAPP_SAME_NAME_AS_APEX));
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
    }

    @Test
    public void testInstallV2Apex_Commit() throws Exception {
        int sessionId = stageSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV2Apex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testInstallV2SignedBobApex_Commit() throws Exception {
        int sessionId = stageSingleApk(Apex2SignedBobRot).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV2SignedBobApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testInstallV3Apex_Commit() throws Exception {
        int sessionId = stageSingleApk(TestApp.Apex3).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV3Apex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(3);
    }

    @Test
    public void testInstallV3SignedBobApex_Commit() throws Exception {
        int sessionId = stageSingleApk(Apex2SignedBobRot).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV3SignedBobApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeNotRequested_Fails_Commit()
            throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(3);
        int sessionId = stageSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = waitForBroadcast(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
        // Also verify that correct session info is reported by PackageManager.
        assertSessionFailed(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeNotRequested_Fails_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        // INSTALL_REQUEST_DOWNGRADE wasn't set, so apex shouldn't be downgraded.
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(3);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_DebugBuild_Commit()
            throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(3);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_DebugBuild_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // Apex should be downgraded.
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_UserBuild_Fails_Commit()
            throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(3);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = waitForBroadcast(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
        // Also verify that correct session info is reported by PackageManager.
        assertSessionFailed(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_UserBuild_Fails_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        // Apex shouldn't be downgraded.
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(3);
    }

    @Test
    public void testStagedInstallDowngradeApexToSystemVersion_DebugBuild_Commit()
            throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex1).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApexToSystemVersion_DebugBuild_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // Apex should be downgraded.
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
    }

    @Test
    public void testInstallApex_DeviceDoesNotSupportApex_Fails() throws Exception {
        InstallUtils.commitExpectingFailure(IllegalArgumentException.class,
                "This device doesn't support the installation of APEX files",
                Install.single(TestApp.Apex2).setStaged());
    }

    @Test
    public void testFailsInvalidApexInstall_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        int sessionId = stageSingleApk(TestApp.ApexWrongSha2).assertSuccessful()
                .getSessionId();
        waitForIsFailedBroadcast(sessionId);
        assertSessionFailed(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testFailsInvalidApexInstall_AbandonSessionIsNoop() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        // Session is in a final state. Test that abandoning the session doesn't remove it from the
        // session database.
        getPackageInstaller().abandonSession(sessionId);
        assertSessionFailed(sessionId);
    }

    @Test
    public void testStagedApkSessionCallbacks() throws Exception {

        List<Integer> created = new ArrayList<Integer>();
        List<Integer> finished = new ArrayList<Integer>();

        HandlerThread handlerThread = new HandlerThread(
                "StagedApkSessionCallbacksTestHandlerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        PackageInstaller.SessionCallback callback = new PackageInstaller.SessionCallback() {

            @Override
            public void onCreated(int sessionId) {
                synchronized (created) {
                    created.add(sessionId);
                }
            }

            @Override public void onBadgingChanged(int sessionId) { }
            @Override public void onActiveChanged(int sessionId, boolean active) { }
            @Override public void onProgressChanged(int sessionId, float progress) { }

            @Override
            public void onFinished(int sessionId, boolean success) {
                synchronized (finished) {
                    finished.add(sessionId);
                }
            }
        };

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageInstaller packageInstaller = getPackageInstaller();
        packageInstaller.registerSessionCallback(callback, handler);

        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();

        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);

        packageInstaller.unregisterSessionCallback(callback);

        handlerThread.quitSafely();
        handlerThread.join();

        synchronized (created) {
            assertThat(created).containsExactly(sessionId);
        }
        synchronized (finished) {
            assertThat(finished).containsExactly(sessionId);
        }
        packageInstaller.abandonSession(sessionId);
    }

    @Test
    public void testInstallStagedApexWithoutApexSuffix_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);

        int sessionId = stageSingleApk("com.android.apex.cts.shim.v2.apex", "package")
                .assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        // Version shouldn't change before reboot.
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(1);
    }

    @Test
    public void testInstallStagedApexWithoutApexSuffix_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testRejectsApexDifferentCertificate() throws Exception {
        int sessionId = stageSingleApk(Apex2DifferentCertificate)
                .assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionFailed();
        assertThat(info.getStagedSessionErrorMessage()).contains("is not compatible with the one "
                + "currently installed on device");
    }

    /**
     * Tests for staged install involving rotated keys.
     *
     * Here alice means the original default key that cts.shim.v1 package was signed with and
     * bob is the new key alice rotates to. Where ambiguous, we will refer keys as alice and bob
     * instead of "old key" and "new key".
     */

    // The update should fail if it is signed with a different non-rotated key
    @Test
    public void testUpdateWithDifferentKeyButNoRotation() throws Exception {
        int sessionId = stageSingleApk(Apex2SignedBob).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionFailed();
    }

    // The update should pass if it is signed with a proper rotated key
    @Test
    public void testUpdateWithDifferentKey_Commit() throws Exception {
        int sessionId = stageSingleApk(Apex2SignedBobRot).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionReady();
    }

    @Test
    public void testUpdateWithDifferentKey_VerifyPostReboot() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    // Once updated with a new rotated key (bob), further updates with old key (alice) should fail
    @Test
    public void testAfterRotationOldKeyIsRejected() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        int sessionId = stageSingleApk(TestApp.Apex3).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionFailed();
    }

    // Once updated with a new rotated key (bob), further updates with new key (bob) should pass
    @Test
    public void testAfterRotationNewKeyCanUpdateFurther_CommitPostReboot() throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        int sessionId = stageSingleApk(Apex3SignedBobRot).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionReady();
    }

    @Test
    public void testAfterRotationNewKeyCanUpdateFurther_VerifyPostReboot() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(3);
    }

    // Once updated with a new rotated key (bob), further updates can be done with key only
    @Test
    public void testAfterRotationNewKeyCanUpdateFurtherWithoutLineage()
            throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        int sessionId = stageSingleApk(Apex3SignedBob).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionReady();
    }

    // Key downgrade should fail if new key is not ancestor of current key
    @Test
    public void testKeyDowngradeFailIfMismatch()
            throws Exception {
        assertThat(getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        int sessionId = stageDowngradeSingleApk(Apex2SignedEve).assertSuccessful().getSessionId();
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionFailed();
        assertThat(info.getStagedSessionErrorMessage()).contains("is not compatible with the one "
                + "currently installed on device");
    }

    @Test
    public void testSamegradeSystemApex_Commit() throws Exception {
        final PackageInfo shim = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().getPackageInfo(SHIM_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(shim.getLongVersionCode()).isEqualTo(1);
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM).isEqualTo(
                ApplicationInfo.FLAG_SYSTEM);
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED).isEqualTo(
                ApplicationInfo.FLAG_INSTALLED);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex1).assertSuccessful().getSessionId();
        waitForIsReadyBroadcast(sessionId);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testSamegradeSystemApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        final PackageInfo shim = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().getPackageInfo(SHIM_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(shim.getLongVersionCode()).isEqualTo(1);
        // Check that APEX on /data wins.
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM).isEqualTo(0);
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED).isEqualTo(
                ApplicationInfo.FLAG_INSTALLED);
    }

    private static long getInstalledVersion(String packageName) {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    // It becomes harder to maintain this variety of install-related helper methods.
    // TODO(ioffe): refactor install-related helper methods into a separate utility.
    private static int createStagedSession() throws Exception {
        return Install.single(TestApp.A1).setStaged().createSession();
    }

    private static void commitSession(int sessionId) throws IOException {
        InstallUtils.openPackageInstallerSession(sessionId)
                .commit(LocalIntentSender.getIntentSender());
    }

    private static StageSessionResult stageDowngradeSingleApk(TestApp testApp) throws Exception {
        Log.i(TAG, "Staging a downgrade of " + testApp);
        int sessionId = Install.single(testApp).setStaged().setRequestDowngrade().createSession();
        // Commit the session (this will start the installation workflow).
        Log.i(TAG, "Committing downgrade session for apk: " + testApp);
        commitSession(sessionId);
        return new StageSessionResult(sessionId, LocalIntentSender.getIntentSenderResult());
    }

    private static StageSessionResult stageSingleApk(String apkFileName, String outputFileName)
            throws Exception {
        Log.i(TAG, "Staging an install of " + apkFileName);
        // this is a trick to open an empty install session so we can manually write the package
        // using writeApk
        TestApp empty = new TestApp(null, null, -1,
                apkFileName.endsWith(".apex"));
        int sessionId = Install.single(empty).setStaged().createSession();
        PackageInstaller.Session session = InstallUtils.openPackageInstallerSession(sessionId);
        writeApk(session, apkFileName, outputFileName);
        // Commit the session (this will start the installation workflow).
        Log.i(TAG, "Committing session for apk: " + apkFileName);
        commitSession(sessionId);
        return new StageSessionResult(sessionId, LocalIntentSender.getIntentSenderResult());
    }

    private static StageSessionResult stageSingleApk(TestApp testApp) throws Exception {
        Log.i(TAG, "Staging an install of " + testApp);
        int sessionId = Install.single(testApp).setStaged().createSession();
        // Commit the session (this will start the installation workflow).
        Log.i(TAG, "Committing session for apk: " + testApp);
        commitSession(sessionId);
        return new StageSessionResult(sessionId, LocalIntentSender.getIntentSenderResult());
    }

    private static StageSessionResult stageMultipleApks(TestApp... testApps) throws Exception {
        Log.i(TAG, "Staging an install of " + Arrays.toString(testApps));
        int multiPackageSessionId = Install.multi(testApps).setStaged().createSession();
        commitSession(multiPackageSessionId);
        return new StageSessionResult(
                multiPackageSessionId, LocalIntentSender.getIntentSenderResult());
    }

    private static void assertSessionApplied(int sessionId) {
        assertSessionState(sessionId,
                (session) -> assertThat(session).isStagedSessionApplied());
    }

    private static void assertSessionReady(int sessionId) {
        assertSessionState(sessionId,
                (session) -> assertThat(session).isStagedSessionReady());
    }

    private static void assertSessionFailed(int sessionId) {
        assertSessionState(sessionId,
                (session) -> assertThat(session).isStagedSessionFailed());
    }

    private static void assertSessionState(
            int sessionId, Consumer<PackageInstaller.SessionInfo> assertion) {
        PackageInstaller packageInstaller = getPackageInstaller();

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

    private void storeSessionId(int sessionId) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mTestStateFile))) {
            writer.write("" + sessionId);
        }
    }

    private int retrieveLastSessionId() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(mTestStateFile))) {
            return Integer.parseInt(reader.readLine());
        }
    }

    private static void writeApk(PackageInstaller.Session session, String apkFileName,
            String outputFileName)
            throws Exception {
        try (OutputStream packageInSession = session.openWrite(outputFileName, 0, -1);
             InputStream is =
                     StagedInstallTest.class.getClassLoader().getResourceAsStream(apkFileName)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = is.read(buffer)) >= 0) {
                packageInSession.write(buffer, 0, n);
            }
        }
    }

    // TODO(ioffe): not really-tailored to staged install, rename to InstallResult?
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
            return extractErrorMessage(result);
        }

        public StageSessionResult assertSuccessful() {
            assertStatusSuccess(result);
            return this;
        }
    }

    private static String extractErrorMessage(Intent result) {
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

    private static void abandonSession(int sessionId) {
        getPackageInstaller().abandonSession(sessionId);
    }

    /**
     * Returns the session by session Id, or null if no session is found.
     */
    private static PackageInstaller.SessionInfo getStagedSessionInfo(int sessionId) {
        PackageInstaller packageInstaller = getPackageInstaller();
        for (PackageInstaller.SessionInfo session : packageInstaller.getStagedSessions()) {
            if (session.getSessionId() == sessionId) {
                return session;
            }
        }
        return null;
    }

    private static PackageInstaller.SessionInfo getSessionInfo(int sessionId) {
        return getPackageInstaller().getSessionInfo(sessionId);
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

    private void waitForIsFailedBroadcast(int sessionId) {
        Log.i(TAG, "Waiting for session " + sessionId + " to be marked as failed");
        try {

            PackageInstaller.SessionInfo info = waitForBroadcast(sessionId);
            assertThat(info).isStagedSessionFailed();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void waitForIsReadyBroadcast(int sessionId) {
        Log.i(TAG, "Waiting for session " + sessionId + " to be ready");
        try {
            PackageInstaller.SessionInfo info = waitForBroadcast(sessionId);
            assertThat(info).isStagedSessionReady();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private PackageInstaller.SessionInfo waitForBroadcast(int sessionId) throws Exception {
        PackageInstaller.SessionInfo info =
                SessionUpdateBroadcastReceiver.sessionBroadcasts.poll(60, TimeUnit.SECONDS);
        assertThat(info).isNotNull();
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        return info;
    }
}
