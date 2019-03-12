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

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_MEDIA_AUDIO;
import static android.Manifest.permission.READ_MEDIA_IMAGES;
import static android.Manifest.permission.READ_MEDIA_VIDEO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_LEGACY_STORAGE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_HIDDEN;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.permission.cts.PermissionUtils.getPermissionFlags;
import static android.permission.cts.PermissionUtils.getPermissions;
import static android.permission.cts.PermissionUtils.getRuntimePermissions;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.isGranted;
import static android.permission.cts.PermissionUtils.isPermissionGranted;
import static android.permission.cts.PermissionUtils.setAppOpByName;
import static android.permission.cts.PermissionUtils.setPermissionFlags;
import static android.permission.cts.PermissionUtils.uninstallApp;

import static com.google.common.truth.Truth.assertThat;

import static java.util.stream.Collectors.toList;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verify changes to the storage permission model when it swiches from one mode to the other
 */
public class DualStoragePermissionModelTest {
    /** The package name of all apps used in the test */
    private static final String APP_PKG = "android.permission.cts.appthataccessesstorage";

    private static final String TMP_DIR = "/data/local/tmp/cts/permissions/";
    private static final String APK_22 = TMP_DIR + "CtsAppThatAccessesStorageOnCommand22.apk";
    private static final String APK_28 = TMP_DIR + "CtsAppThatAccessesStorageOnCommand28.apk";
    private static final String APK_29 = TMP_DIR + "CtsAppThatAccessesStorageOnCommand29.apk";
    private static final String APK_28v3 = TMP_DIR + "CtsAppThatAccessesStorageOnCommand28v3.apk";

    /**
     * Assert that a permission of {@link #APP_PKG} has some flags.
     *
     * @param permission The permission having the flags
     * @param flags The flags required
     */
    private void assertHasFlags(@NonNull String permission, int flags) {
        assertThat(getPermissionFlags(APP_PKG, permission) & flags).isEqualTo(flags);
    }

    /**
     * Assert that a permission of {@link #APP_PKG} does <u>not</u> have some flags.
     *
     * @param permission The permission owning the flags
     * @param flags The flags required to not to be set
     */
    private void assertHasNotFlags(@NonNull String permission, int flags) {
        assertThat(getPermissionFlags(APP_PKG, permission) & flags).isEqualTo(0);
    }

    /**
     * Assert the state that is always true for P apps
     */
    private void assertLegacyStoragePermissionModel() throws Exception {
        assertHasFlags(READ_MEDIA_IMAGES, FLAG_PERMISSION_HIDDEN);
        assertHasFlags(READ_MEDIA_VIDEO, FLAG_PERMISSION_HIDDEN);
        assertHasFlags(READ_MEDIA_AUDIO, FLAG_PERMISSION_HIDDEN);

        assertHasNotFlags(READ_EXTERNAL_STORAGE, FLAG_PERMISSION_HIDDEN);
        assertHasNotFlags(WRITE_EXTERNAL_STORAGE, FLAG_PERMISSION_HIDDEN);

        // Modern permission are always granted to not get into the way when checking both legacy
        // and modern permissions
        assertThat(isGranted(APP_PKG, READ_MEDIA_IMAGES)).isTrue();
        assertThat(isGranted(APP_PKG, READ_MEDIA_VIDEO)).isTrue();
        assertThat(isGranted(APP_PKG, READ_MEDIA_AUDIO)).isTrue();
    }

    /**
     * Assert the state that is always true for <u>not</u> grandfathered Q apps
     */
    private void assertModernStoragePermissionModel() throws Exception {
        assertHasFlags(READ_EXTERNAL_STORAGE, FLAG_PERMISSION_HIDDEN);
        assertHasFlags(WRITE_EXTERNAL_STORAGE, FLAG_PERMISSION_HIDDEN);

        assertHasNotFlags(READ_MEDIA_IMAGES, FLAG_PERMISSION_HIDDEN);

        assertThat(getPermissions(APP_PKG)).doesNotContain(READ_MEDIA_VIDEO);
        assertThat(getPermissions(APP_PKG)).doesNotContain(READ_MEDIA_AUDIO);

        // Legacy permission are always granted to not get into the way when checking both legacy
        // and modern permissions
        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isTrue();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isTrue();
    }

    /**
     * Assert the state that is always true for grandfathered Q apps
     */
    private void assertGrandfatheredStoragePermissionModel() throws Exception {
        assertHasFlags(READ_MEDIA_IMAGES, FLAG_PERMISSION_HIDDEN);

        assertHasNotFlags(READ_EXTERNAL_STORAGE, FLAG_PERMISSION_HIDDEN);
        assertHasNotFlags(WRITE_EXTERNAL_STORAGE, FLAG_PERMISSION_HIDDEN);

        assertThat(getPermissions(APP_PKG)).doesNotContain(READ_MEDIA_VIDEO);
        assertThat(getPermissions(APP_PKG)).doesNotContain(READ_MEDIA_AUDIO);

        // In grandfathered mode all permissions are always granted
        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isTrue();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isTrue();
        assertThat(isGranted(APP_PKG, READ_MEDIA_IMAGES)).isTrue();
    }

    /**
     * Install an app as if it would have been grandfathered during the P->Q update
     *
     * @param apkFile The file to install
     */
    private void installGrandfatheredApp(@NonNull String apkFile) {
        install(apkFile);
        setAppOpByName(APP_PKG, OPSTR_LEGACY_STORAGE, MODE_ALLOWED);

        // Re-run storage mode change code
        install(apkFile);
    }

    @Before
    @After
    public void uninstallTestApp() {
        uninstallApp(APP_PKG);
    }

    @Test
    public void lNoUpdate() throws Exception {
        install(APK_22);

        assertLegacyStoragePermissionModel();

        // L apps always have all permissions granted, but the app-ops might be denied
        assertThat(getRuntimePermissions(APP_PKG)).containsExactlyElementsIn(
                        getRuntimePermissions(APP_PKG).stream()
                        .filter(p -> isPermissionGranted(APP_PKG, p))
                        .collect(toList()));

        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isTrue();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isTrue();
    }

    @Test
    public void pNoUpdate() throws Exception {
        install(APK_28);

        assertLegacyStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isFalse();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isFalse();
    }

    @Test
    public void qNoUpdate() throws Exception {
        install(APK_29);

        assertModernStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_MEDIA_IMAGES)).isFalse();
    }

    @Test
    public void lToPUpdate() throws Exception {
        install(APK_22);
        install(APK_28v3);

        assertLegacyStoragePermissionModel();

        // Permissions will get revoked on upgrade 22->23
        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isFalse();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isFalse();
    }

    @Test
    public void lToQUpdate() throws Exception {
        install(APK_22);
        install(APK_29);

        assertModernStoragePermissionModel();

        // Permissions will get revoked on upgrade 22->23
        assertThat(isGranted(APP_PKG, READ_MEDIA_IMAGES)).isFalse();
    }

    @Test
    public void pToPUpdate() throws Exception {
        install(APK_28);
        // No change of permission model, just version change of app
        install(APK_28v3);

        assertLegacyStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isFalse();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isFalse();
    }

    @Test
    public void pToQUpdate() throws Exception {
        install(APK_28);
        install(APK_29);

        assertModernStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_MEDIA_IMAGES)).isFalse();
    }

    @Test
    public void qToPUpdate() throws Exception {
        install(APK_29);
        install(APK_28v3);

        assertLegacyStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isFalse();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isFalse();
    }

    @Test
    public void pToPUpdateGranted() throws Exception {
        install(APK_28);
        grantPermission(APP_PKG, READ_EXTERNAL_STORAGE);
        // No change of permission model, just version change of app
        install(APK_28v3);

        assertLegacyStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isTrue();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isFalse();
    }

    @Test
    public void pToQUpdateGranted() throws Exception {
        install(APK_28);
        grantPermission(APP_PKG, WRITE_EXTERNAL_STORAGE);
        install(APK_29);

        assertModernStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_MEDIA_IMAGES)).isTrue();
    }

    @Test
    public void qToPUpdateGranted() throws Exception {
        install(APK_29);
        grantPermission(APP_PKG, READ_MEDIA_IMAGES);
        install(APK_28v3);

        assertLegacyStoragePermissionModel();

        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isTrue();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isTrue();
    }

    @Test
    public void preserveUserSetDuringPToQUpdate() {
        install(APK_28);
        setPermissionFlags(APP_PKG, READ_EXTERNAL_STORAGE, FLAG_PERMISSION_USER_SET,
                FLAG_PERMISSION_USER_SET);
        install(APK_29);

        assertHasFlags(READ_MEDIA_IMAGES, FLAG_PERMISSION_USER_SET);
    }

    @Test
    public void preserveUserSetDuringQToPUpdate() {
        install(APK_29);
        setPermissionFlags(APP_PKG, READ_MEDIA_IMAGES, FLAG_PERMISSION_USER_SET,
                FLAG_PERMISSION_USER_SET);
        install(APK_28v3);

        assertHasFlags(READ_EXTERNAL_STORAGE, FLAG_PERMISSION_USER_SET);
        assertHasFlags(WRITE_EXTERNAL_STORAGE, FLAG_PERMISSION_USER_SET);
    }

    @Test
    public void dontCreateSystemFixedGrantedDuringPToQUpdate() throws Exception {
        install(APK_28);
        setPermissionFlags(APP_PKG, READ_EXTERNAL_STORAGE, FLAG_PERMISSION_USER_FIXED,
                FLAG_PERMISSION_USER_FIXED);
        grantPermission(APP_PKG, WRITE_EXTERNAL_STORAGE);
        install(APK_29);

        // USER_FIXED and granted cannot happen at the same time. Hence when inheriting the state,
        // granted is preferred
        assertThat(isGranted(APP_PKG, READ_MEDIA_IMAGES)).isTrue();
        assertHasNotFlags(READ_MEDIA_IMAGES, FLAG_PERMISSION_USER_FIXED);
    }

    @Test
    public void grandfatheredLNoUpdate() throws Exception {
        installGrandfatheredApp(APK_22);

        assertLegacyStoragePermissionModel();
    }

    @Test
    public void grandfatheredPNoUpdate() throws Exception {
        installGrandfatheredApp(APK_28);

        assertLegacyStoragePermissionModel();
    }

    @Test
    public void grandfatheredQNoUpdate() throws Exception {
        installGrandfatheredApp(APK_29);

        assertGrandfatheredStoragePermissionModel();
    }

    @Test
    public void grandfatheredLToPUpdate() throws Exception {
        installGrandfatheredApp(APK_22);
        install(APK_28);

        assertLegacyStoragePermissionModel();

        // Permissions will get revoked on upgrade 22->23
        assertThat(isGranted(APP_PKG, READ_EXTERNAL_STORAGE)).isFalse();
        assertThat(isGranted(APP_PKG, WRITE_EXTERNAL_STORAGE)).isFalse();
    }

    @Test
    public void grandfatheredLToQUpdate() throws Exception {
        installGrandfatheredApp(APK_22);
        install(APK_29);

        assertGrandfatheredStoragePermissionModel();
    }

    @Test
    public void grandfatheredPToQUpdate() throws Exception {
        installGrandfatheredApp(APK_28);
        install(APK_29);

        assertGrandfatheredStoragePermissionModel();
    }

    @Test
    public void grandfatheredQToPUpdate() throws Exception {
        installGrandfatheredApp(APK_29);
        install(APK_28v3);

        assertLegacyStoragePermissionModel();
    }
}
