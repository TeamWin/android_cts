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

import static com.android.cts.rollback.lib.RollbackInfoSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.rollback.RollbackInfo;

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

import java.io.IOException;

/**
 * On-device helper test methods used for host-driven rollback tests.
 */
@RunWith(JUnit4.class)
public class HostTestHelper {
    private static final TestApp Apex2SignedBobRot = new TestApp(
            "Apex2SignedBobRot", TestApp.Apex, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_signed_bob_rot.apex");

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


    /**
     * Test rollbacks of staged installs involving only apks.
     * Commits TestApp.A2 as a staged install with rollback enabled.
     */
    @Test
    public void testApkOnlyEnableRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setStaged().setEnableRollback().commit();

        // At this point, the host test driver will reboot the device and run
        // testApkOnlyCommitRollback().
    }

    /**
     * Test rollbacks of staged installs involving only apks.
     * Confirms a staged rollback is available for TestApp.A2 and commits the
     * rollback.
     */
    @Test
    public void testApkOnlyCommitRollback() throws Exception {
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
        InstallUtils.waitForSessionReady(committed.getCommittedSessionId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        // At this point, the host test driver will reboot the device and run
        // testApkOnlyConfirmRollback().
    }

    /**
     * Test rollbacks of staged installs involving only apks.
     * Confirms TestApp.A2 was rolled back.
     */
    @Test
    public void testApkOnlyConfirmRollback() throws Exception {
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
     * Test rollbacks of staged installs involving only apex.
     * Install first version phase.
     *
     * <p> We start by installing version 2. The test ultimately rolls back from 3 to 2.
     */
    @Test
    public void testApexOnlyInstallFirstVersion() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(1);

        Install.single(TestApp.Apex2).setStaged().commit();

        // At this point, the host test driver will reboot the device and run
        // testApexOnlyEnableRollback().
    }

    /**
     * Test rollbacks of staged installs involving only apex.
     * Enable rollback phase.
     */
    @Test
    public void testApexOnlyEnableRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        Install.single(TestApp.Apex3).setStaged().setEnableRollback().commit();

        // At this point, the host test driver will reboot the device and run
        // testApexOnlyCommitRollback().
    }

    /**
     * Test rollbacks of staged installs involving only apex.
     * Commit rollback phase.
     */
    @Test
    public void testApexOnlyCommitRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(3);
        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.Apex);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2));

        RollbackUtils.rollback(available.getRollbackId(), TestApp.Apex3);
        RollbackInfo committed = RollbackUtils.getCommittedRollbackById(available.getRollbackId());
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex3);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        InstallUtils.waitForSessionReady(committed.getCommittedSessionId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(3);

        // At this point, the host test driver will reboot the device and run
        // testApexOnlyConfirmRollback().
    }

    /**
     * Test rollbacks of staged installs involving only apex.
     * Confirm rollback phase.
     */
    @Test
    public void testApexOnlyConfirmRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);

        // Rollback data for shim apex will remain in storage since the apex cannot be completely
        // removed and thus the rollback data won't be expired. Unfortunately, we can't also delete
        // the rollback data manually from storage.

        // At this point, the host test driver will reboot the device to complete the uninstall.
    }

    /**
     * Test rollback to system version involving apex only
     */
    @Test
    public void testApexOnlySystemVersion_EnableRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        Install.single(TestApp.Apex2).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testApexOnlySystemVersion_CommitRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.Apex);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.Apex2).to(TestApp.Apex1));

        RollbackUtils.rollback(available.getRollbackId(), TestApp.Apex2);
        RollbackInfo committed = RollbackUtils.getCommittedRollbackById(available.getRollbackId());
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex2).to(TestApp.Apex1));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        InstallUtils.waitForSessionReady(committed.getCommittedSessionId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testApexOnlySystemVersion_ConfirmRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(1);
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Install first version phase.
     *
     * <p> See {@link #testApexOnlyInstallFirstVersion()}
     */
    @Test
    public void testApexAndApkInstallFirstVersion() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(-1);

        Install.multi(TestApp.Apex2, TestApp.A1).setStaged().commit();

        // At this point, the host test driver will reboot the device and run
        // testApexOnlyEnableRollback().
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Enable rollback phase.
     */
    @Test
    public void testApexAndApkEnableRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        Install.multi(TestApp.Apex3, TestApp.A2).setStaged().setEnableRollback().commit();

        // At this point, the host test driver will reboot the device and run
        // testApexOnlyCommitRollback().
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Commit rollback phase.
     */
    @Test
    public void testApexAndApkCommitRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(3);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);

        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.Apex);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(TestApp.A2).to(TestApp.A1));

        RollbackUtils.rollback(available.getRollbackId(), TestApp.Apex3, TestApp.A2);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex3, TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        InstallUtils.waitForSessionReady(committed.getCommittedSessionId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(3);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        // At this point, the host test driver will reboot the device and run
        // testApexOnlyConfirmRollback().
    }

    /**
     * Test rollbacks of staged installs involving apex and apk.
     * Confirm rollback phase.
     */
    @Test
    public void testApexAndApkConfirmRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);

        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.Apex3).to(TestApp.Apex2),
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.Apex3, TestApp.A2);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Rollback data for shim apex will remain in storage since the apex cannot be completely
        // removed and thus the rollback data won't be expired. Unfortunately, we can't also delete
        // the rollback data manually from storage due to SEPolicy rules.

        // At this point, the host test driver will reboot the device to complete the uninstall.
    }

    /**
     * Tests that apex update expires existing rollbacks for that apex.
     * Enable rollback phase.
     */
    @Test
    public void testApexRollbackExpirationEnableRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(1);

        Install.single(TestApp.Apex2).setStaged().setEnableRollback().commit();

        // At this point, the host test driver will reboot the device and run
        // testApexRollbackExpirationUpdateApex().
    }

    /**
     * Tests that apex update expires existing rollbacks for that apex.
     * Update apex phase.
     */
    @Test
    public void testApexRollbackExpirationUpdateApex() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        assertThat(RollbackUtils.getAvailableRollback(TestApp.Apex)).isNotNull();
        Install.single(TestApp.Apex3).setStaged().commit();

        // At this point, the host test driver will reboot the device and run
        // testApexRollbackExpirationConfirmExpiration().
    }

    /**
     * Tests that apex update expires existing rollbacks for that apex.
     * Confirm expiration phase.
     */
    @Test
    public void testApexRollbackExpirationConfirmExpiration() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(3);
        assertThat(RollbackUtils.getAvailableRollback(TestApp.Apex)).isNull();
    }

    /**
     * Test rollback with key downgrade for apex only
     */
    @Test
    public void testApexKeyRotation_EnableRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(1);
        Install.single(Apex2SignedBobRot).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testApexKeyRotation_CommitRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.Apex);
        assertThat(available).isStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(Apex2SignedBobRot).to(TestApp.Apex1));

        RollbackUtils.rollback(available.getRollbackId(), Apex2SignedBobRot);
        RollbackInfo committed = RollbackUtils.getCommittedRollbackById(available.getRollbackId());
        assertThat(committed).isNotNull();
        assertThat(committed).isStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(Apex2SignedBobRot).to(TestApp.Apex1));
        assertThat(committed).causePackagesContainsExactly(Apex2SignedBobRot);
        assertThat(committed.getCommittedSessionId()).isNotEqualTo(-1);

        // Note: The app is not rolled back until after the rollback is staged
        // and the device has been rebooted.
        InstallUtils.waitForSessionReady(committed.getCommittedSessionId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(2);
    }

    @Test
    public void testApexKeyRotation_CofirmRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.Apex)).isEqualTo(1);
    }
}
