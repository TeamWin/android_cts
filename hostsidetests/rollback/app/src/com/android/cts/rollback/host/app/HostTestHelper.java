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

package com.android.cts.rollback.host.app;

import static com.android.cts.shim.lib.ShimPackage.PRIVILEGED_SHIM_PACKAGE_NAME;
import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;
import static com.android.cts.shim.lib.ShimPackage.SHIM_PACKAGE_NAME;
import static com.android.cts.rollback.lib.RollbackInfoSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.rollback.RollbackInfo;
import android.content.rollback.RollbackManager;
import android.os.storage.StorageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;
import com.android.cts.rollback.lib.Rollback;
import com.android.cts.rollback.lib.RollbackUtils;

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
import java.nio.file.Files;

/**
 * On-device helper test methods used for host-driven rollback tests.
 */
@RunWith(JUnit4.class)
public class HostTestHelper {
    private static final String TAG = "RollbackTest";

    private static final TestApp Apex2SignedBobRotRollback = new TestApp(
            "Apex2SignedBobRotRollback", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_signed_bob_rot_rollback.apex");
    private static final String APK_VERSION_FILENAME = "ctsrollback_apkversion";
    private static final String APK_VERSION_SEPARATOR = ",";

    /**
     * Adopts common permissions needed to test rollbacks.
     */
    @Before
    public void setup() throws InterruptedException, IOException {
        InstallUtils.adoptShellPermissionIdentity(
                    Manifest.permission.INSTALL_PACKAGES,
                    Manifest.permission.DELETE_PACKAGES,
                    Manifest.permission.TEST_MANAGE_ROLLBACKS);
    }

    /**
     * Drops adopted shell permissions.
     */
    @After
    public void teardown() throws InterruptedException, IOException {
        InstallUtils.dropShellPermissionIdentity();
    }

    @Test
    public void cleanUp() throws Exception {
        // Remove all pending rollbacks
        RollbackManager rm = RollbackUtils.getRollbackManager();
        rm.getAvailableRollbacks().stream().flatMap(info -> info.getPackages().stream())
                .map(info -> info.getPackageName()).forEach(rm::expireRollbackForPackage);
        // remove the version file.
        Files.deleteIfExists(getApkInApexVersionFile().toPath());
    }

    /**
     * Test rollbacks of staged installs involving only apks.
     * Commits TestApp.A2 as a staged install with rollback enabled.
     */
    @Test
    public void testApkOnlyStagedRollback_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setStaged().setEnableRollback().commit();
    }

    /**
     * Test rollbacks of staged installs involving only apks.
     * Confirms a staged rollback is available for TestApp.A2 and commits the
     * rollback.
     */
    @Test
    public void testApkOnlyStagedRollback_Phase2_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);

        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(RollbackUtils.getCommittedRollback(TestApp.A)).isNull();

        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
    }

    /**
     * Test rollbacks of staged installs involving only apks.
     * Confirms TestApp.A2 was rolled back.
     */
    @Test
    public void testApkOnlyStagedRollback_Phase3_Confirm() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);

        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);
    }

    /**
     * Test rollbacks of multiple staged installs involving only apks.
     * Commits TestApp.A2 and TestApp.B2 as a staged install with rollback enabled.
     */
    @Test
    public void testApkOnlyMultipleStagedRollback_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(-1);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setStaged().setEnableRollback().commit();

        Install.single(TestApp.B1).commit();
        Install.single(TestApp.B2).setStaged().setEnableRollback().commit();
    }

    /**
     * Test rollbacks of multiple staged installs involving only apks.
     * Confirms staged rollbacks are available for TestApp.A2 and TestApp.b2, and commits the
     * rollback.
     */
    @Test
    public void testApkOnlyMultipleStagedRollback_Phase2_RollBack() throws Exception {
        // Process TestApp.A
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);
        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(RollbackUtils.getCommittedRollback(TestApp.A)).isNull();

        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        // Process TestApp.B
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.B);
        available = RollbackUtils.getAvailableRollback(TestApp.B);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.B2).to(TestApp.B1));
        assertThat(RollbackUtils.getCommittedRollback(TestApp.B)).isNull();

        RollbackUtils.rollback(available.getRollbackId(), TestApp.B2);
        committed = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.B2).to(TestApp.B1));
        assertThat(committed).causePackagesContainsExactly(TestApp.B2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
    }

    /**
     * Test rollbacks of staged installs involving only apks.
     * Confirms TestApp.A2 and TestApp.B2 was rolled back.
     */
    @Test
    public void testApkOnlyMultipleStagedRollback_Phase3_Confirm() throws Exception {
        // Process TestApp.A
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Process TestApp.B
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.B);
        committed = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.B2).to(TestApp.B1));
        assertThat(committed).causePackagesContainsExactly(TestApp.B2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);
    }

    /**
     * Test partial rollbacks of multiple staged installs involving only apks.
     * Commits TestApp.A2 and TestApp.B2 as a staged install with rollback enabled.
     */
    @Test
    public void testApkOnlyMultipleStagedPartialRollback_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(-1);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setStaged().setEnableRollback().commit();

        Install.single(TestApp.B1).commit();
        Install.single(TestApp.B2).setStaged().commit();
    }

    /**
     * Test partial rollbacks of multiple staged installs involving only apks.
     * Confirms staged rollbacks are available for TestApp.A2, and commits the
     * rollback.
     */
    @Test
    public void testApkOnlyMultipleStagedPartialRollback_Phase2_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);
        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(RollbackUtils.getCommittedRollback(TestApp.A)).isNull();

        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
    }

    /**
     * Test partial rollbacks of staged installs involving only apks.
     * Confirms TestApp.A2 was rolled back.
     */
    @Test
    public void testApkOnlyMultipleStagedPartialRollback_Phase3_Confirm() throws Exception {
        // Process TestApp.A
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Process TestApp.B
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);
    }

    /**
     * Test rollbacks of staged installs involving only apex.
     * Install first version phase.
     *
     * <p> We start by installing version 2. The test ultimately rolls back from 3 to 2.
     */
    @Test
    public void testApexOnlyStagedRollback_Phase1_InstallFirst() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        Install.single(TestApp.Apex2).setStaged().commit();
    }

    /**
     * Test rollbacks of staged installs involving only apex.
     * Enable rollback phase.
     */
    @Test
    public void testApexOnlyStagedRollback_Phase2_InstallSecond() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);

        // keep the versions of the apks in shim apex for verifying in phase3
        recordApkInApexVersion();

        Install.single(TestApp.Apex3).setStaged().setEnableRollback().commit();
    }

    /**
     * Test rollbacks of staged installs involving only apex.
     * Commit rollback phase.
     */
    @Test
    public void testApexOnlyStagedRollback_Phase3_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);

        long[] versions = retrieveApkInApexVersion();
        final long apkInShimApexVersion = versions[0];
        final long privApkInShimApexVersion = versions[1];

        RollbackInfo available = RollbackUtils.getAvailableRollback(SHIM_APEX_PACKAGE_NAME);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));

        RollbackUtils.rollback(available.getRollbackId(), TestApp.Apex3);
        RollbackInfo committed = RollbackUtils.getCommittedRollbackById(available.getRollbackId());
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex3);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
    }

    /**
     * Test rollbacks of staged installs involving only apex.
     * Confirm rollback phase.
     */
    @Test
    public void testApexOnlyStagedRollback_Phase4_Confirm() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);

        // Rollback data for shim apex will remain in storage since the apex cannot be completely
        // removed and thus the rollback data won't be expired. Unfortunately, we can't also delete
        // the rollback data manually from storage.
    }

    /**
     * Test rollback to system version involving apex only
     */
    @Test
    public void testApexOnlySystemVersionStagedRollback_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        // keep the versions of the apks in shim apex for verifying in phase2
        recordApkInApexVersion();

        Install.single(TestApp.Apex2).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testApexOnlySystemVersionStagedRollback_Phase2_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);

        long[] versions = retrieveApkInApexVersion();
        final long apkInShimApexVersion = versions[0];
        final long privApkInShimApexVersion = versions[1];

        RollbackInfo available = RollbackUtils.getAvailableRollback(SHIM_APEX_PACKAGE_NAME);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.Apex2).to(TestApp.Apex1),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));

        RollbackUtils.rollback(available.getRollbackId(), TestApp.Apex2);
        RollbackInfo committed = RollbackUtils.getCommittedRollbackById(available.getRollbackId());
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex2).to(TestApp.Apex1),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
    }

    @Test
    public void testApexOnlySystemVersionStagedRollback_Phase3_Confirm() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Install first version phase.
     */
    @Test
    public void testApexAndApkStagedRollback_Phase1_InstallFirst() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);

        Install.multi(TestApp.Apex2, TestApp.A1).setStaged().commit();
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Enable rollback phase.
     */
    @Test
    public void testApexAndApkStagedRollback_Phase2_InstallSecond() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        // keep the versions of the apks in shim apex for verifying in phase3 and phase4
        recordApkInApexVersion();

        Install.multi(TestApp.Apex3, TestApp.A2).setStaged().setEnableRollback().commit();
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Commit rollback phase.
     */
    @Test
    public void testApexAndApkStagedRollback_Phase3_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);

        long[] versions = retrieveApkInApexVersion();
        final long apkInShimApexVersion = versions[0];
        final long privApkInShimApexVersion = versions[1];

        RollbackInfo available = RollbackUtils.getAvailableRollback(SHIM_APEX_PACKAGE_NAME);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));

        RollbackUtils.rollback(available.getRollbackId(), TestApp.Apex3, TestApp.A2);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex3, TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Confirm rollback phase.
     */
    @Test
    public void testApexAndApkStagedRollback_Phase4_Confirm() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);

        long[] versions = retrieveApkInApexVersion();
        final long apkInShimApexVersion = versions[0];
        final long privApkInShimApexVersion = versions[1];

        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex3, TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Rollback data for shim apex will remain in storage since the apex cannot be completely
        // removed and thus the rollback data won't be expired. Unfortunately, we can't also delete
        // the rollback data manually from storage due to SEPolicy rules.
    }

    /**
     * Tests that apex update expires existing rollbacks for that apex.
     * Enable rollback phase.
     */
    @Test
    public void testApexRollbackExpiration_Phase1_InstallFirst() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        Install.single(TestApp.Apex2).setStaged().setEnableRollback().commit();
    }

    /**
     * Tests that apex update expires existing rollbacks for that apex.
     * Update apex phase.
     */
    @Test
    public void testApexRollbackExpiration_Phase2_InstallSecond() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        assertThat(RollbackUtils.getAvailableRollback(SHIM_APEX_PACKAGE_NAME)).isNotNull();
        Install.single(TestApp.Apex3).setStaged().commit();
    }

    /**
     * Tests that apex update expires existing rollbacks for that apex.
     * Confirm expiration phase.
     */
    @Test
    public void testApexRollbackExpiration_Phase3_Confirm() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
        assertThat(RollbackUtils.getAvailableRollback(SHIM_APEX_PACKAGE_NAME)).isNull();
    }

    /**
     * Test rollback with key downgrade for apex only
     */
    @Test
    public void testApexKeyRotationStagedRollback_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        // keep the versions of the apks in shim apex for verifying in phase2
        recordApkInApexVersion();

        Install.single(Apex2SignedBobRotRollback).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testApexKeyRotationStagedRollback_Phase2_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        RollbackInfo available = RollbackUtils.getAvailableRollback(SHIM_APEX_PACKAGE_NAME);
        long[] versions = retrieveApkInApexVersion();
        final long apkInShimApexVersion = versions[0];
        final long privApkInShimApexVersion = versions[1];

        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(Apex2SignedBobRotRollback).to(TestApp.Apex1),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));

        RollbackUtils.rollback(available.getRollbackId(), Apex2SignedBobRotRollback);
        RollbackInfo committed = RollbackUtils.getCommittedRollbackById(available.getRollbackId());
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(Apex2SignedBobRotRollback).to(TestApp.Apex1),
                Rollback.from(SHIM_PACKAGE_NAME, 0)
                        .to(SHIM_PACKAGE_NAME, apkInShimApexVersion),
                Rollback.from(PRIVILEGED_SHIM_PACKAGE_NAME, 0)
                        .to(PRIVILEGED_SHIM_PACKAGE_NAME, privApkInShimApexVersion));
        assertThat(committed).causePackagesContainsExactly(Apex2SignedBobRotRollback);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
    }

    @Test
    public void testApexKeyRotationStagedRollback_Phase3_Confirm() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testApkRollbackByAnotherInstaller_Phase1_FirstInstaller() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();
    }

    @Test
    public void testFingerprintChange_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(-1);

        // Both available and enabling rollbacks should be invalidated after fingerprint changes.
        Install.multi(TestApp.A1, TestApp.B1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();
        Install.single(TestApp.B2).setEnableRollback().setStaged().commit();
    }

    @Test
    public void testFingerprintChange_Phase2_Confirm() throws Exception {
        assertThat(RollbackUtils.getRollbackManager().getAvailableRollbacks()).isEmpty();
    }

    @Test
    public void testRollbackFailsOtherSessions_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testRollbackFailsOtherSessions_Phase2_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);
        // Stage session for package A to check if it can block rollback of A
        final int sessionIdA = Install.single(TestApp.A3).setStaged().setEnableRollback().commit();

        // Stage another package not related to the rollback
        Install.single(TestApp.B1).commit();
        final int sessionIdB = Install.single(TestApp.B2).setStaged().setEnableRollback().commit();

        final RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        final RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // This staged session should fail when there is already a staged rollback
        InstallUtils.commitExpectingFailure(AssertionError.class,
                "Session was failed by rollback",
                Install.single(TestApp.C1).setStaged());

        // Assert that blocking staged session is failed
        final PackageInstaller.SessionInfo sessionA = InstallUtils.getStagedSessionInfo(sessionIdA);
        assertThat(sessionA).isNotNull();
        assertThat(sessionA.isStagedSessionFailed()).isTrue();

        // Assert that the unrelated staged session is also failed
        final PackageInstaller.SessionInfo sessionB = InstallUtils.getStagedSessionInfo(sessionIdB);
        assertThat(sessionB).isNotNull();
        assertThat(sessionB.isStagedSessionFailed()).isTrue();

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
    }

    @Test
    public void testRollbackFailsOtherSessions_Phase3_Confirm() throws Exception {
        // Process TestApp.A
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);
        final RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Assert that unrelated package was also failed
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(1);
    }

    @Test
    public void testSimultaneousRollbacksBothSucceed_Phase1_Install() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(-1);
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setStaged().setEnableRollback().commit();
        Install.single(TestApp.B1).commit();
        Install.single(TestApp.B2).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testSimultaneousRollbacksBothSucceed_Phase2_RollBack() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);

        final RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        final RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        final RollbackInfo availableB = RollbackUtils.getAvailableRollback(TestApp.B);
        RollbackUtils.rollback(availableB.getRollbackId(), TestApp.B2);
        final RollbackInfo committedB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committedB).hasRollbackId(availableB.getRollbackId());
        assertThat(committedB).isStaged();
        assertThat(committedB).packagesContainsExactly(
                Rollback.from(TestApp.B2).to(TestApp.B1));
        assertThat(committedB).causePackagesContainsExactly(TestApp.B2);
        assertThat(committedB.getCommittedSessionId()).isNotEqualTo(-1);
    }

    @Test
    public void testSimultaneousRollbacksBothSucceed_Phase3_Confirm() throws Exception {
        // Process TestApp.A
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);
        final RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Process TestApp.B
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.B);
        final RollbackInfo committedB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committedB).isStaged();
        assertThat(committedB).packagesContainsExactly(
                Rollback.from(TestApp.B2).to(TestApp.B1));
        assertThat(committedB).causePackagesContainsExactly(TestApp.B2);
        assertThat(committedB.getCommittedSessionId()).isNotEqualTo(-1);
    }

    /**
     * Record the versions of Apk in shim apex and PrivApk in shim apex
     * in the order into {@link #APK_VERSION_FILENAME}.
     *
     * @see ShimPackage#SHIM_PACKAGE_NAME
     * @see ShimPackage#PRIVILEGED_SHIM_PACKAGE_NAME
     */
    private void recordApkInApexVersion() throws Exception {
        final File versionFile = getApkInApexVersionFile();

        if (!versionFile.exists()) {
            versionFile.createNewFile();
        }

        final long apkInApexVersion = InstallUtils.getInstalledVersion(SHIM_PACKAGE_NAME);
        final long privApkInApexVersion = InstallUtils.getInstalledVersion(
                PRIVILEGED_SHIM_PACKAGE_NAME);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(versionFile))) {
            writer.write(apkInApexVersion + APK_VERSION_SEPARATOR + privApkInApexVersion);
        }
    }

    /**
     * Returns the array of the versions of Apk in shim apex and PrivApk in shim apex
     * in the order from {@link #APK_VERSION_FILENAME}.
     *
     * @see ShimPackage#SHIM_PACKAGE_NAME
     * @see ShimPackage#PRIVILEGED_SHIM_PACKAGE_NAME
     */
    private long[] retrieveApkInApexVersion() throws Exception {
        final File versionFile = getApkInApexVersionFile();

        if (!versionFile.exists()) {
            throw new IllegalStateException("The RollbackTest version file not found");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            String[] versions = reader.readLine().split(APK_VERSION_SEPARATOR);

            if (versions.length != 2) {
                throw new IllegalStateException("The RollbackTest version file is wrong.");
            }
            return new long[]{Long.parseLong(versions[0]), Long.parseLong(versions[1])};
        }
    }

    private File getApkInApexVersionFile() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return new File(context.getFilesDir(), APK_VERSION_FILENAME);
    }
}
