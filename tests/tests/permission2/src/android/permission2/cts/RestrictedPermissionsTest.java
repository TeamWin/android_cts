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
import android.platform.test.annotations.AppModeFull;
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
    private static final String APK_USES_SMS_CALL_LOG =
            "/data/local/tmp/cts/permissions2/CtsRestrictedPermissionsUser.apk";

    private static final String APK_USES_STORAGE_DEFAULT_28 =
            "/data/local/tmp/cts/permissions2/CtsStoragePermissionsUserDefaultSdk28.apk";

    private static final String APK_USES_STORAGE_DEFAULT_29 =
            "/data/local/tmp/cts/permissions2/CtsStoragePermissionsUserDefaultSdk29.apk";

    private static final String APK_USES_STORAGE_OPT_IN_28 =
            "/data/local/tmp/cts/permissions2/CtsStoragePermissionsUserOptInSdk28.apk";

    private static final String APK_USES_STORAGE_OPT_OUT_29 =
            "/data/local/tmp/cts/permissions2/CtsStoragePermissionsUserOptOutSdk29.apk";

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
        intentFilter.addAction("uninstallApp");
        getContext().registerReceiver(sCommandReceiver, intentFilter);
    }

    @AfterClass
    public static void tearDownOnce() {
        getContext().unregisterReceiver(sCommandReceiver);
    }

    @Test
    @AppModeFull
    public void testDefaultAllRestrictedPermissionsWhitelistedAtInstall() throws Exception {
        try {
            // Install with no changes to whitelisted permissions, not attempting to grant.
            installRestrictedPermissionUserApp(null /*whitelistedPermissions*/,
                    null /*grantedPermissions*/);

            // All restricted permission should be whitelisted.
            assertAllRestrictedPermissionWhitelisted();

            // No restricted permission should be granted.
            assertNoRestrictedPermissionGranted();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testSomeRestrictedPermissionsWhitelistedAtInstall() throws Exception {
        try {
            // Whitelist only these permissions.
            final Set<String> whitelistedPermissions = new ArraySet<>(2);
            whitelistedPermissions.add(Manifest.permission.SEND_SMS);
            whitelistedPermissions.add(Manifest.permission.READ_CALL_LOG);

            // Install with some whitelisted permissions, not attempting to grant.
            installRestrictedPermissionUserApp(whitelistedPermissions, null /*grantedPermissions*/);

            // Some restricted permission should be whitelisted.
            assertRestrictedPermissionWhitelisted(whitelistedPermissions);

            // No restricted permission should be granted.
            assertNoRestrictedPermissionGranted();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testNoneRestrictedPermissionWhitelistedAtInstall() throws Exception {
        try {
            // Install with all whitelisted permissions, not attempting to grant.
            installRestrictedPermissionUserApp(Collections.emptySet(),
                    null /*grantedPermissions*/);

            // No restricted permission should be whitelisted.
            assertNoRestrictedPermissionWhitelisted();

            // No restricted permission should be granted.
            assertNoRestrictedPermissionGranted();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testSomeRestrictedPermissionsGrantedAtInstall() throws Exception {
        try {
            // Grant only these permissions.
            final Set<String> grantedPermissions = new ArraySet<>(1);
            grantedPermissions.add(Manifest.permission.SEND_SMS);
            grantedPermissions.add(Manifest.permission.READ_CALL_LOG);

            // Install with no whitelisted permissions attempting to grant.
            installRestrictedPermissionUserApp(null /*whitelistedPermissions*/, grantedPermissions);

            // All restricted permission should be whitelisted.
            assertAllRestrictedPermissionWhitelisted();

            // Some restricted permission should be granted.
            assertRestrictedPermissionGranted(grantedPermissions);
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testAllRestrictedPermissionsGrantedAtInstall() throws Exception {
        try {
            // Install with whitelisted permissions attempting to grant.
            installRestrictedPermissionUserApp(null /*whitelistedPermissions*/,
                    Collections.emptySet());

            // All restricted permission should be whitelisted.
            assertAllRestrictedPermissionWhitelisted();

            // Some restricted permission should be granted.
            assertAllRestrictedPermissionGranted();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
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
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk28DefaultWhitelistedHasFullAccess() throws Exception {
        try {
            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_28, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasFullStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk28DefaultNotWhitelistedHasIsolatedAccess() throws Exception {
        try {
            // Install with no whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_28, Collections.emptySet());

            // Check expected storage mode
            assertHasIsolatedStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk28OptInWhitelistedHasIsolatedAccess() throws Exception {
        try {
            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_OPT_IN_28, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasIsolatedStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk28OptInNotWhitelistedHasIsolatedAccess() throws Exception {
        try {
            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_OPT_IN_28, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasIsolatedStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk29DefaultWhitelistedHasIsolatedAccess() throws Exception {
        try {
            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_29, Collections.emptySet());

            // Check expected storage mode
            assertHasIsolatedStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk29DefaultNotWhitelistedHasIsolatedAccess() throws Exception {
        try {
            // Install with no whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_29, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasIsolatedStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk29OptOutWhitelistedHasFullAccess() throws Exception {
        try {
            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_OPT_OUT_29, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasFullStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageTargetingSdk29OptOutNotWhitelistedHasIsolatedAccess() throws Exception {
        try {
            // Install with no whitelisted permissions.
            installApp(APK_USES_STORAGE_OPT_OUT_29, Collections.emptySet() );

            // Check expected storage mode
            assertHasIsolatedStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testStorageDoesNotChangeOnUpdate() throws Exception {
        try {
            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_28, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasFullStorageAccess();

            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_OPT_IN_28, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasFullStorageAccess();

            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_29, null /*whitelistedPermissions*/);

            // Check expected storage mode
            assertHasFullStorageAccess();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testCannotControlStorageWhitelistPostInstall1() throws Exception {
        try {
            // Install with whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_28, null /*whitelistedPermissions*/);

            // Check expected state of restricted permissions.
            assertCannotUnWhitelistStorage();
        } finally {
            uninstallApp();
        }
    }

    @Test
    @AppModeFull
    public void testCannotControlStorageWhitelistPostInstall2() throws Exception {
        try {
            // Install with no whitelisted permissions.
            installApp(APK_USES_STORAGE_DEFAULT_28, Collections.emptySet());

            // Check expected state of restricted permissions.
            assertCannotWhitelistStorage();
        } finally {
            uninstallApp();
        }
    }

    private void assertHasFullStorageAccess() throws Exception {
        runWithShellPermissionIdentity(() -> {
            AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
            final int uid = getContext().getPackageManager().getPackageUid(PKG, 0);
            assertThat(appOpsManager.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_LEGACY_STORAGE,
                    uid, PKG)).isEqualTo(AppOpsManager.MODE_ALLOWED);
        });
    }

    private void assertHasIsolatedStorageAccess() throws Exception {
        runWithShellPermissionIdentity(() -> {
            AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
            final int uid = getContext().getPackageManager().getPackageUid(PKG, 0);
            assertThat(appOpsManager.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_LEGACY_STORAGE,
                    uid, PKG)).isNotEqualTo(AppOpsManager.MODE_ALLOWED);
        });
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

    private void assertCannotWhitelistStorage()
            throws Exception {
        final PackageManager packageManager = getContext().getPackageManager();

        runWithShellPermissionIdentity(() -> {
            // Assert added only to none whitelist.
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                            | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .doesNotContain(permission.READ_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                            | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .doesNotContain(permission.WRITE_EXTERNAL_STORAGE);
        });

        // Assert we cannot add.
        try {
            packageManager.addWhitelistedRestrictedPermission(PKG,
                    permission.READ_EXTERNAL_STORAGE,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);
            fail();
        } catch (SecurityException expected) {}
        try {
            packageManager.addWhitelistedRestrictedPermission(PKG,
                    permission.WRITE_EXTERNAL_STORAGE,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);
            fail();
        } catch (SecurityException expected) {}

        runWithShellPermissionIdentity(() -> {
            // Assert added only to none whitelist.
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                            | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .doesNotContain(permission.READ_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                            | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .doesNotContain(permission.WRITE_EXTERNAL_STORAGE);
        });
    }

    private void assertCannotUnWhitelistStorage()
            throws Exception {
        final PackageManager packageManager = getContext().getPackageManager();

        runWithShellPermissionIdentity(() -> {
            // Assert added only to install whitelist.
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .contains(permission.READ_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .contains(permission.WRITE_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM))
                    .doesNotContain(permission.READ_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM))
                    .doesNotContain(permission.WRITE_EXTERNAL_STORAGE);
        });

        try {
            // Assert we cannot remove.
            packageManager.removeWhitelistedRestrictedPermission(PKG,
                    permission.READ_EXTERNAL_STORAGE,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);
            fail();
        } catch (SecurityException expected) {}
        try {
            packageManager.removeWhitelistedRestrictedPermission(PKG,
                    permission.WRITE_EXTERNAL_STORAGE,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);
            fail();
        } catch (SecurityException expected) {}

        runWithShellPermissionIdentity(() -> {
            // Assert added only to install whitelist.
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .contains(permission.READ_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER))
                    .contains(permission.WRITE_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM))
                    .doesNotContain(permission.READ_EXTERNAL_STORAGE);
            assertThat(packageManager.getWhitelistedRestrictedPermissions(PKG,
                    PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                            | PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM))
                    .doesNotContain(permission.WRITE_EXTERNAL_STORAGE);
        });
    }

    private void assertAllRestrictedPermissionWhitelisted() throws Exception {
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
                assertThat(whitelistedPermissions.size()).isEqualTo(
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
                            .isEqualTo(AppOpsManager.MODE_ALLOWED);
                } else {
                    assertThat(appOpsManager.unsafeCheckOpNoThrow(
                            AppOpsManager.permissionToOp(permission),
                            packageInfo.applicationInfo.uid, PKG))
                            .isEqualTo(AppOpsManager.MODE_DEFAULT);
                }
            }
        });
    }

    private void assertAllHardRestrictedPermissionAppOpsAllowed() throws Exception {
        runWithShellPermissionIdentity(() -> {
            // Also assert that apps ops are properly set
            final AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
            final PackageInfo packageInfo = getContext().getPackageManager().getPackageInfo(PKG,
                    PackageManager.GET_PERMISSIONS);
            for (String permission : packageInfo.requestedPermissions) {
                final PermissionInfo permissionInfo = getContext().getPackageManager()
                        .getPermissionInfo(permission, 0);
                if ((permissionInfo.flags & PermissionInfo.FLAG_HARD_RESTRICTED) != 0) {
                    assertThat(appOpsManager.unsafeCheckOpNoThrow(
                            AppOpsManager.permissionToOp(permission),
                            packageInfo.applicationInfo.uid, PKG))
                            .isEqualTo(AppOpsManager.MODE_ALLOWED);
                }
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
                            & PackageInfo.REQUESTED_PERMISSION_GRANTED)).isNotEqualTo(0);
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
                                & PackageInfo.REQUESTED_PERMISSION_GRANTED)).isNotEqualTo(0);
                    } else {
                        assertThat((packageInfo.requestedPermissionsFlags[i]
                                & PackageInfo.REQUESTED_PERMISSION_GRANTED)).isEqualTo(0);
                    }
                }
            }
        }
    }

    private void installRestrictedPermissionUserApp(@Nullable Set<String> whitelistedPermissions,
            @Nullable Set<String> grantedPermissions) throws Exception {
        installApp(APK_USES_SMS_CALL_LOG, whitelistedPermissions, grantedPermissions);
    }

    private void installApp(@NonNull String app, @Nullable Set<String> whitelistedPermissions)
            throws Exception {
        installApp(app, whitelistedPermissions, null /*grantedPermissions*/);
    }

    private void installApp(@NonNull String app, @Nullable Set<String> whitelistedPermissions,
            @Nullable Set<String> grantedPermissions) throws Exception {
        // Install the app and whitelist/grant all permission if requested.
        final StringBuilder command = new StringBuilder("pm install ");
        if (whitelistedPermissions != null) {
            command.append("--restrict-permissions ");
        }
        if (grantedPermissions != null && grantedPermissions.isEmpty()) {
            command.append("-g ");
        }
        command.append(app);
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
        if (grantedPermissions != null && !grantedPermissions.isEmpty()) {
            runWithShellPermissionIdentity(() -> {
                final PackageManager packageManager = getContext().getPackageManager();
                for (String permission : grantedPermissions) {
                    packageManager.grantRuntimePermission(PKG, permission,
                            getContext().getUser());
                }
            });
        }
    }

    private void uninstallApp() {
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
