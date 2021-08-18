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

package com.android.cts.rollback;

import static com.android.cts.rollback.lib.RollbackInfoSubject.assertThat;
import static com.android.cts.rollback.lib.RollbackUtils.getRollbackManager;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.rollback.RollbackInfo;
import android.content.rollback.RollbackManager;
import android.provider.DeviceConfig;

import androidx.test.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;
import com.android.cts.rollback.lib.Rollback;
import com.android.cts.rollback.lib.RollbackUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * CTS Tests for RollbackManager APIs.
 */
@RunWith(JUnit4.class)
public class RollbackManagerTest {
    // TODO: use PackageManager.RollbackDataPolicy.* when they are system API
    private static final int ROLLBACK_DATA_POLICY_RESTORE = 0;
    private static final int ROLLBACK_DATA_POLICY_WIPE = 1;

    /**
     * Adopts common permissions needed to test rollbacks and uninstalls the
     * test apps.
     */
    @Before
    public void setup() throws InterruptedException, IOException {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                    Manifest.permission.INSTALL_PACKAGES,
                    Manifest.permission.DELETE_PACKAGES,
                    Manifest.permission.TEST_MANAGE_ROLLBACKS,
                    Manifest.permission.READ_DEVICE_CONFIG,
                    Manifest.permission.WRITE_DEVICE_CONFIG);

        Uninstall.packages(TestApp.A, TestApp.B, TestApp.C);
    }

    /**
     * Drops adopted shell permissions and uninstalls the test apps.
     */
    @After
    public void teardown() throws InterruptedException, IOException {
        Uninstall.packages(TestApp.A, TestApp.B, TestApp.C);

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Test basic rollbacks.
     */
    @Test
    public void testBasic() throws Exception {
        Install.single(TestApp.A1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        RollbackUtils.waitForRollbackGone(
                () -> getRollbackManager().getAvailableRollbacks(), TestApp.A);
        RollbackUtils.waitForRollbackGone(
                () -> getRollbackManager().getRecentlyCommittedRollbacks(), TestApp.A);

        Install.single(TestApp.A2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);
        RollbackInfo available = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(available).isNotStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(RollbackUtils.getCommittedRollback(TestApp.A)).isNull();

        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);
        RollbackUtils.waitForUnavailableRollback(TestApp.A);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isNotNull();
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isNotStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
    }

    /**
     * Tests rollback of multi-package installs is implemented.
     */
    @Test
    public void testBasic_MultiPackage() throws Exception {
        // Prep installation of the test apps.
        Install.multi(TestApp.A1, TestApp.B1).commit();
        Install.multi(TestApp.A2, TestApp.B2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);

        // TestApp.A should now be available for rollback.
        RollbackInfo rollback = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(rollback).isNotNull();
        assertThat(rollback).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));

        // Rollback the app. It should cause both test apps to be rolled back.
        RollbackUtils.rollback(rollback.getRollbackId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(1);

        // We should see recent rollbacks listed for both A and B.
        RollbackInfo rollbackA = RollbackUtils.getCommittedRollback(TestApp.A);
        RollbackInfo rollbackB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(rollbackA).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));
        assertThat(rollbackA).hasRollbackId(rollback.getRollbackId());
        assertThat(rollbackB).hasRollbackId(rollback.getRollbackId());
    }

    /**
     * Tests rollbacks are properly persisted.
     */
    @Test
    public void testSingleRollbackPersistence() throws Exception {
        RollbackManager rm = RollbackUtils.getRollbackManager();

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();
        RollbackInfo rollbackA = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(rollbackA).isNotNull();

        // Check the available rollback is persisted correctly
        rm.reloadPersistedData();
        rollbackA = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(rollbackA).packagesContainsExactly(Rollback.from(TestApp.A2).to(TestApp.A1));

        // Rollback the app
        TestApp cause = new TestApp("Foo", "com.android.tests.rollback.testapp.Foo",
                /*versionCode*/ 42, /*isApex*/ false);
        RollbackUtils.rollback(rollbackA.getRollbackId(), cause);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isNotNull();

        // Check the committed rollback is persisted correctly
        rm.reloadPersistedData();
        committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).hasRollbackId(rollbackA.getRollbackId());
        assertThat(committed).isNotStaged();
        assertThat(committed).packagesContainsExactly(Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(cause);
    }

    /**
     * Tests rollbacks are properly persisted.
     */
    @Test
    public void testMultiRollbackPersistence() throws Exception {
        RollbackManager rm = RollbackUtils.getRollbackManager();

        Install.multi(TestApp.A1, TestApp.B1).commit();
        Install.multi(TestApp.A2, TestApp.B2).setEnableRollback().commit();
        RollbackInfo rollbackA = RollbackUtils.waitForAvailableRollback(TestApp.A);
        RollbackInfo rollbackB = RollbackUtils.waitForAvailableRollback(TestApp.B);
        assertThat(rollbackA).isNotNull();
        assertThat(rollbackB).isNotNull();

        // Check the available rollback is persisted correctly
        rm.reloadPersistedData();
        rollbackA = RollbackUtils.getAvailableRollback(TestApp.A);
        rollbackB = RollbackUtils.getAvailableRollback(TestApp.B);
        assertThat(rollbackB).hasRollbackId(rollbackA.getRollbackId());
        assertThat(rollbackA).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));

        // Rollback the app
        RollbackUtils.rollback(rollbackA.getRollbackId());
        RollbackInfo committedA = RollbackUtils.getCommittedRollback(TestApp.A);
        RollbackInfo committedB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committedA).isNotNull();
        assertThat(committedB).isNotNull();

        // Check the committed rollback is persisted correctly
        rm.reloadPersistedData();
        committedA = RollbackUtils.getCommittedRollback(TestApp.A);
        committedB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committedA).hasRollbackId(rollbackA.getRollbackId());
        assertThat(committedB).hasRollbackId(rollbackA.getRollbackId());
        assertThat(committedA).isNotStaged();
        assertThat(committedA).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));
    }

    /**
     * Tests that the MANAGE_ROLLBACKS permission is required to call
     * RollbackManager APIs.
     */
    @Test
    public void testManageRollbacksPermission() throws Exception {
        // We shouldn't be allowed to call any of the RollbackManager APIs
        // without the MANAGE_ROLLBACKS permission.
        InstallUtils.dropShellPermissionIdentity();
        RollbackManager rm = RollbackUtils.getRollbackManager();

        try {
            rm.getAvailableRollbacks();
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            rm.getRecentlyCommittedRollbacks();
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            LocalIntentSender sender = new LocalIntentSender();
            rm.commitRollback(0, Collections.emptyList(), sender.getIntentSender());
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            rm.reloadPersistedData();
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            rm.expireRollbackForPackage(TestApp.A);
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }
    }

    /**
     * Tests that you cannot enable rollback for a package without the
     * MANAGE_ROLLBACKS permission.
     */
    @Test
    public void testEnableRollbackPermission() throws Exception {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();

        // We expect v2 of the app was installed, but rollback has not
        // been enabled.
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_ROLLBACKS,
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES);
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Tests that you cannot enable rollback for a non-module package when
     * holding the MANAGE_ROLLBACKS permission without TEST_MANAGE_ROLLBACKS.
     */
    @Test
    public void testNonModuleEnableRollback() throws Exception {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES,
                Manifest.permission.MANAGE_ROLLBACKS);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();

        // We expect v2 of the app was installed, but rollback has not
        // been enabled because the test app is not a module.
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Tests failure to enable rollback for multi-package installs.
     * If any one of the packages fail to enable rollback, we shouldn't enable
     * rollback for any package.
     */
    @Test
    public void testMultiPackageEnableFail() throws Exception {
        Install.single(TestApp.A1).commit();
        // We should fail to enable rollback here because TestApp B is not
        // already installed.
        Install.multi(TestApp.A2, TestApp.B2).setEnableRollback().commit();

        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);

        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
        assertThat(RollbackUtils.getAvailableRollback(TestApp.B)).isNull();
    }

    @Test
    public void testGetRollbackDataPolicy() throws Exception {
        final int rollBackDataPolicy = ROLLBACK_DATA_POLICY_WIPE;

        Install.single(TestApp.A1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        // Enable rollback with rollBackDataPolicy
        final int sessionId = Install.single(TestApp.A2).setEnableRollback(
                rollBackDataPolicy).createSession();

        try {
            assertThat(InstallUtils.getPackageInstaller().getSessionInfo(
                    sessionId).getRollbackDataPolicy()).isEqualTo(rollBackDataPolicy);
        } finally {
            // Abandon the session
            InstallUtils.getPackageInstaller().abandonSession(sessionId);
        }
    }

    /**
     * Test that flags are cleared when a rollback is committed.
     */
    @Test
    public void testRollbackClearsFlags() throws Exception {
        Install.single(TestApp.A1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        RollbackUtils.waitForRollbackGone(
                () -> getRollbackManager().getAvailableRollbacks(), TestApp.A);

        Install.single(TestApp.A2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        RollbackInfo available = RollbackUtils.waitForAvailableRollback(TestApp.A);

        DeviceConfig.setProperty("configuration", "namespace_to_package_mapping",
                "testspace:" + TestApp.A, false);
        DeviceConfig.setProperty("testspace", "flagname", "hello", false);
        DeviceConfig.setProperty("testspace", "another", "12345", false);
        assertThat(DeviceConfig.getProperties("testspace").getKeyset()).hasSize(2);

        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        assertThat(DeviceConfig.getProperties("testspace").getKeyset()).hasSize(0);
    }

    /**
     * Tests an app can be rolled back to the previous signing key.
     *
     * <p>The rollback capability in the signing lineage allows an app to be updated to an APK
     * signed with a previous signing key in the lineage; however this often defeats the purpose
     * of key rotation as a compromised key could then be used to roll an app back to the previous
     * key. To avoid requiring the rollback capability to support APK rollbacks the PackageManager
     * allows an app to be rolled back to the previous signing key if the rollback install reason
     * is set.
     */
    @Test
    public void testRollbackAfterKeyRotation() throws Exception {
        Install.single(TestApp.AOriginal1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        Install.single(TestApp.ARotated2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        RollbackInfo available = RollbackUtils.waitForAvailableRollback(TestApp.A);
        RollbackUtils.rollback(available.getRollbackId(), TestApp.ARotated2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
    }

    /**
     * Test we can't enable rollback for non-allowlisted app without
     * TEST_MANAGE_ROLLBACKS permission
     */
    @Test
    public void testNonRollbackAllowlistedApp() throws Exception {
        InstallUtils.dropShellPermissionIdentity();
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES,
                Manifest.permission.MANAGE_ROLLBACKS);

        Install.single(TestApp.A1).commit();
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();

        Install.single(TestApp.A2).setEnableRollback().commit();
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Tests user data is restored according to the preset rollback data policy.
     */
    @Test
    public void testRollbackDataPolicy() throws Exception {
        Install.multi(TestApp.A1, TestApp.B1, TestApp.C1).commit();
        // Write user data version = 1
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        Install a2 = Install.single(TestApp.A2)
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_WIPE);
        Install b2 = Install.single(TestApp.B2)
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_RESTORE);
        Install c2 = Install.single(TestApp.C2)
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_RETAIN);
        Install.multi(a2, b2, c2).setEnableRollback().commit();
        // Write user data version = 2
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        RollbackInfo info = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(info.getRollbackId());
        // Read user data version from userdata.txt
        // A's user data version is -1 for user data is wiped.
        // B's user data version is 1 for user data is restored.
        // C's user data version is 2 for user data is retained.
        assertThat(InstallUtils.getUserDataVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.B)).isEqualTo(1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.C)).isEqualTo(2);
    }

    /**
     * Tests user data is restored according to the rollback data policy defined in the manifest.
     */
    @Test
    public void testRollbackDataPolicy_Manifest() throws Exception {
        Install.multi(TestApp.A1, TestApp.B1, TestApp.C1).commit();
        // Write user data version = 1
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        Install a2 = Install.single(TestApp.ARollbackWipe2).setEnableRollback();
        Install b2 = Install.single(TestApp.BRollbackRestore2).setEnableRollback();
        Install c2 = Install.single(TestApp.CRollbackRetain2).setEnableRollback();
        Install.multi(a2, b2, c2).setEnableRollback().commit();
        // Write user data version = 2
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        RollbackInfo info = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(info.getRollbackId());
        // Read user data version from userdata.txt
        // A's user data version is -1 for user data is wiped.
        // B's user data version is 1 for user data is restored.
        // C's user data version is 2 for user data is retained.
        assertThat(InstallUtils.getUserDataVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.B)).isEqualTo(1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.C)).isEqualTo(2);
    }
}
