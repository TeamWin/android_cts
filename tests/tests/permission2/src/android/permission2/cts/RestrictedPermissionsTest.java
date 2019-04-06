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
 * See the License for the specific language governing permissions andf
 * limitations under the License.
 */

package android.permission2.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.fail;
import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.Manifest.permission;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.util.ArraySet;

import java.util.Collections;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import javax.annotation.Nullable;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Tests for restricted permission behaviors.
 */
public class RestrictedPermissionsTest {
    private static final String APK =
            "/data/local/tmp/cts/permissions2/CtsRestrictedPermissionsUser.apk";
    private static final String PKG = "android.permission2.cts.restrictedpermissionuser";

    private static @NonNull BroadcastReceiver sCommandReceiver;

    public interface ThrowingRunnable extends Runnable {
        void runOrThrow() throws Exception;

        @Override
        default void run() {
            try {
                runOrThrow();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @BeforeClass
    public static void setUpOnce() {
        sCommandReceiver = new CommandBroadcastReceiver();
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("installRestrictedPermissionUserApp");
        intentFilter.addAction("uninstallRestrictedPermissionUserApp");
        getContext().registerReceiver(sCommandReceiver, intentFilter);
    }

    @AfterClass
    public static void tearDownOnce() {
        getContext().unregisterReceiver(sCommandReceiver);
    }

    @Test
    public void testWhenFeatureDisabledAppOpsProperlySet() throws Exception {
        assumeTrue(!PackageManager.RESTRICTED_PERMISSIONS_ENABLED);
        try {
            // Install with no whitelisted permissions, not attempting to grant.
            installRestrictedPermissionUserApp(null /*whitelistedPermissions*/,
                    null /*grantedPermissions*/);

            // Appops should be allowed if feature off.
            assertAllRestrictedPermissionAppOpsAllowed();
        } finally {
            uninstallRestrictedPermissionUserApp();
        }
    }

    @Test
    public void testNoRestrictedPermissionsWhitelistedAtInstall() throws Exception {
        assumeTrue(PackageManager.RESTRICTED_PERMISSIONS_ENABLED);
        try {
            // Install with no whitelisted permissions, not attempting to grant.
            installRestrictedPermissionUserApp(null /*whitelistedPermissions*/,
                    null /*grantedPermissions*/);

            // No restricted permission should be whitelisted.
            assertNoRestrictedPermissionWhitelisted();

            // No restricted permission should be granted.
            assertNoRestrictedPermissionGranted();
        } finally {
            uninstallRestrictedPermissionUserApp();
        }
    }

    @Test
    public void testSomeRestrictedPermissionsWhitelistedAtInstall() throws Exception {
        assumeTrue(PackageManager.RESTRICTED_PERMISSIONS_ENABLED);
        try {
            // Whitelist only these permissions.
            final Set<String> whitelistedPermissions = new ArraySet<>(1);
            whitelistedPermissions.add(Manifest.permission.SEND_SMS);
            whitelistedPermissions.add(Manifest.permission.READ_CALL_LOG);

            // Install with some whitelisted permissions, not attempting to grant.
            installRestrictedPermissionUserApp(whitelistedPermissions, null /*grantedPermissions*/);

            // Some restricted permission should be whitelisted.
            assertRestrictedPermissionWhitelisted(whitelistedPermissions);

            // No restricted permission should be granted.
            assertNoRestrictedPermissionGranted();
        } finally {
            uninstallRestrictedPermissionUserApp();
        }
    }

    @Test
    public void testAllRestrictedPermissionWhitelistedAtInstall() throws Exception {
        assumeTrue(PackageManager.RESTRICTED_PERMISSIONS_ENABLED);
        try {
            // Install with all whitelisted permissions, not attempting to grant.
            installRestrictedPermissionUserApp(Collections.emptySet(), null /*grantedPermissions*/);

            // All restricted permission should be whitelisted.
            assertAllRestrictedPermissionWhitelisted();

            // No restricted permission should be granted.
            assertNoRestrictedPermissionGranted();
        } finally {
            uninstallRestrictedPermissionUserApp();
        }
    }

    @Test
    public void testSomeRestrictedPermissionsGrantedAtInstall() throws Exception {
        assumeTrue(PackageManager.RESTRICTED_PERMISSIONS_ENABLED);
        try {
            // Grant only these permissions.
            final Set<String> grantedPermissions = new ArraySet<>(1);
            grantedPermissions.add(Manifest.permission.SEND_SMS);
            grantedPermissions.add(Manifest.permission.READ_CALL_LOG);

            // Install with no whitelisted permissions attempting to grant.
            installRestrictedPermissionUserApp(Collections.emptySet(), grantedPermissions);

            // All restricted permission should be whitelisted.
            assertAllRestrictedPermissionWhitelisted();

            // Some restricted permission should be granted.
            assertRestrictedPermissionGranted(grantedPermissions);
        } finally {
            uninstallRestrictedPermissionUserApp();
        }
    }

    @Test
    public void testAllRestrictedPermissionsGrantedAtInstall() throws Exception {
        assumeTrue(PackageManager.RESTRICTED_PERMISSIONS_ENABLED);
        try {
            // Install with no whitelisted permissions attempting to grant.
            installRestrictedPermissionUserApp(Collections.emptySet(),
                    Collections.emptySet());

            // All restricted permission should be whitelisted.
            assertAllRestrictedPermissionWhitelisted();

            // Some restricted permission should be granted.
            assertAllRestrictedPermissionGranted();
        } finally {
            uninstallRestrictedPermissionUserApp();
        }
    }

    @Test
    public void testWhitelistAccessControl() throws Exception {
        try {
            // Install with no whitelisted permissions not attempting to grant.
            installRestrictedPermissionUserApp(Collections.emptySet(),
                    Collections.emptySet());

            assertWeCannotReadOrWriteWhileShellCanReadAndWrite(
                    PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);

            assertWeCannotReadOrWriteWhileShellCanReadAndWrite(
                    PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE);

            assertWeCannotReadOrWriteWhileShellCanReadAndWrite(
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);
        } finally {
            uninstallRestrictedPermissionUserApp();
        }
    }

    private void assertWeCannotReadOrWriteWhileShellCanReadAndWrite(int whitelist)
            throws Exception {
        final PackageManager packageManager = getContext().getPackageManager();
        try {
            packageManager.getWhitelistedRestrictedPermissions(PKG, whitelist);
            fail();
        } catch (SecurityException expected) {
            /*ignore*/
        }
        try {
            packageManager.addWhitelistedRestrictedPermission(PKG,
                    permission.SEND_SMS, whitelist);
            fail();
        } catch (SecurityException expected) {
            /*ignore*/
        }
        runWithShellPermissionIdentity(() -> {
            packageManager.addWhitelistedRestrictedPermission(PKG,
                    permission.SEND_SMS, whitelist);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    whitelist)).contains(permission.SEND_SMS);
            packageManager.removeWhitelistedRestrictedPermission(PKG,
                    permission.SEND_SMS, whitelist);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    whitelist)).doesNotContain(permission.SEND_SMS);
        });
    }

    private void assertAllRestrictedPermissionWhitelisted() throws Exception {
        assumeTrue(PackageManager.RESTRICTED_PERMISSIONS_ENABLED);
        final PackageManager packageManager = getContext().getPackageManager();

        final PackageInfo packageInfo = packageManager.getPackageInfo(PKG,
                PackageManager.GET_PERMISSIONS);
        if (packageInfo.requestedPermissions == null) {
            return;
        }

        runWithShellPermissionIdentity(() -> {
            final Set<String> whitelistedPermissions = packageManager
                    .getWhitelistedRestrictedPermissions(PKG,
                            PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                                    | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER
                                    | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE);

            if (packageInfo.requestedPermissions != null) {
                final int permissionCount = packageInfo.requestedPermissions.length;
                for (int i = 0; i < permissionCount; i++) {
                    final String permission = packageInfo.requestedPermissions[i];
                    final PermissionInfo permissionInfo = packageManager
                            .getPermissionInfo(permission,
                                    0);
                    if ((permissionInfo.flags & PermissionInfo.FLAG_HARD_RESTRICTED) != 0) {
                        whitelistedPermissions.remove(permission);
                    }
                }
            }

            assertThat(whitelistedPermissions).isNotNull();
            assertThat(whitelistedPermissions).isEmpty();
        });
    }

    private void assertNoRestrictedPermissionWhitelisted() throws Exception {
        assertRestrictedPermissionWhitelisted(null /*expectedWhitelistedPermissions*/);
    }

    private void assertRestrictedPermissionWhitelisted(
            @Nullable Set<String> expectedWhitelistedPermissions) throws Exception {
        final PackageManager packageManager = getContext().getPackageManager();
        runWithShellPermissionIdentity(() -> {
            final Set<String> whitelistedPermissions = packageManager
                .getWhitelistedRestrictedPermissions(PKG,
                        PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                        | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER
                        | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE);


            assertThat(whitelistedPermissions).isNotNull();
            if (expectedWhitelistedPermissions == null) {
                assertThat(whitelistedPermissions.isEmpty()).isTrue();
            } else {
                assertThat(whitelistedPermissions.size()).isSameAs(
                        expectedWhitelistedPermissions.size());
                assertThat(whitelistedPermissions.containsAll(
                        expectedWhitelistedPermissions)).isTrue();
            }

            // Also assert that apps ops are properly set
            final AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
            final PackageInfo packageInfo = packageManager.getPackageInfo(PKG,
                    PackageManager.GET_PERMISSIONS);

            for (String permission : packageInfo.requestedPermissions) {
                if (expectedWhitelistedPermissions != null
                        && expectedWhitelistedPermissions.contains(permission)) {
                    assertThat(appOpsManager.unsafeCheckOpNoThrow(
                            AppOpsManager.permissionToOp(permission),
                            packageInfo.applicationInfo.uid, PKG))
                            .isSameAs(AppOpsManager.MODE_ALLOWED);
                } else {
                    assertThat(appOpsManager.unsafeCheckOpNoThrow(
                            AppOpsManager.permissionToOp(permission),
                            packageInfo.applicationInfo.uid, PKG))
                            .isSameAs(AppOpsManager.MODE_DEFAULT);
                }
            }
        });
    }

    private void assertAllRestrictedPermissionAppOpsAllowed() throws Exception {
        runWithShellPermissionIdentity(() -> {
            // Also assert that apps ops are properly set
            final AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
            final PackageInfo packageInfo = getContext().getPackageManager().getPackageInfo(PKG,
                    PackageManager.GET_PERMISSIONS);
            for (String permission : packageInfo.requestedPermissions) {
                assertThat(appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.permissionToOp(permission),
                        packageInfo.applicationInfo.uid, PKG))
                        .isSameAs(AppOpsManager.MODE_ALLOWED);
            }
        });
    }

    private void assertAllRestrictedPermissionGranted() throws Exception {
        final PackageManager packageManager = getContext().getPackageManager();
        final PackageInfo packageInfo = packageManager.getPackageInfo(
                PKG, PackageManager.GET_PERMISSIONS);
        if (packageInfo.requestedPermissions != null) {
            final int permissionCount = packageInfo.requestedPermissions.length;
            for (int i = 0; i < permissionCount; i++) {
                final String permission = packageInfo.requestedPermissions[i];
                final PermissionInfo permissionInfo = packageManager.getPermissionInfo(
                        permission, 0);
                if ((permissionInfo.flags & PermissionInfo.FLAG_HARD_RESTRICTED) != 0) {
                    assertThat((packageInfo.requestedPermissionsFlags[i]
                            & PackageInfo.REQUESTED_PERMISSION_GRANTED)).isNotSameAs(0);
                }
            }
        }
    }

    private void assertNoRestrictedPermissionGranted() throws Exception {
        assertRestrictedPermissionGranted(null /*expectedGrantedPermissions*/);
    }

    private void assertRestrictedPermissionGranted(@Nullable Set<String> expectedGrantedPermissions)
            throws Exception {
        final PackageManager packageManager = getContext().getPackageManager();
        final PackageInfo packageInfo = packageManager.getPackageInfo(
                PKG, PackageManager.GET_PERMISSIONS);
        if (packageInfo.requestedPermissions != null) {
            final int permissionCount = packageInfo.requestedPermissions.length;
            for (int i = 0; i < permissionCount; i++) {
                final String permission = packageInfo.requestedPermissions[i];
                final PermissionInfo permissionInfo = packageManager.getPermissionInfo(
                        permission, 0);
                if ((permissionInfo.flags & PermissionInfo.FLAG_HARD_RESTRICTED) != 0) {
                    if (expectedGrantedPermissions != null
                            && expectedGrantedPermissions.contains(permission)) {
                        assertThat((packageInfo.requestedPermissionsFlags[i]
                                & PackageInfo.REQUESTED_PERMISSION_GRANTED)).isNotSameAs(0);
                    } else {
                        assertThat((packageInfo.requestedPermissionsFlags[i]
                                & PackageInfo.REQUESTED_PERMISSION_GRANTED)).isSameAs(0);
                    }
                }
            }
        }
    }

    private void installRestrictedPermissionUserApp(@Nullable Set<String> whitelistedPermissions,
            @Nullable Set<String> grantedPermissions) throws Exception {
        // Install the app and whitelist/grant all permisison if requested.
        final StringBuilder command = new StringBuilder("pm install ");
        if (whitelistedPermissions != null && whitelistedPermissions.isEmpty()) {
            command.append("-w ");
        }
        if (grantedPermissions != null && grantedPermissions.isEmpty()) {
            command.append("-g ");
        }
        command.append(APK);
        runShellCommand(command.toString());

        // Whitelist subset of permissions if requested
        if (whitelistedPermissions != null && !whitelistedPermissions.isEmpty()) {
            runWithShellPermissionIdentity(() -> {
                final PackageManager packageManager = getContext().getPackageManager();
                for (String permission : whitelistedPermissions) {
                    packageManager.addWhitelistedRestrictedPermission(PKG, permission,
                            PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);
                }
            });
        }

        // Grant subset of permissions if requested
        if (whitelistedPermissions != null && !whitelistedPermissions.isEmpty()) {
            runWithShellPermissionIdentity(() -> {
                final PackageManager packageManager = getContext().getPackageManager();
                for (String permission : whitelistedPermissions) {
                    packageManager.grantRuntimePermission(PKG, permission,
                            getContext().getUser());
                }
            });
        }
    }

    private void uninstallRestrictedPermissionUserApp() {
        runShellCommand("pm uninstall " + PKG);
    }

    private static @NonNull Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static void runWithShellPermissionIdentity(@NonNull ThrowingRunnable command)
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            command.runOrThrow();
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
}
