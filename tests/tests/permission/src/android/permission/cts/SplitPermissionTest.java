/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_CONTACTS;
import static android.app.AppOpsManager.MODE_FOREGROUND;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.permission.cts.PermissionUtils.getAppOp;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.isGranted;
import static android.permission.cts.PermissionUtils.setAppOp;
import static android.permission.cts.PermissionUtils.uninstallApp;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Tests how split permissions behave.
 *
 * <ul>
 *     <li>Default permission grant behavior</li>
 *     <li>Changes to the grant state during upgrade of apps with split permissions</li>
 *     <li>Special behavior of background location</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class SplitPermissionTest {
    /** The package name of all apps used in the test */
    private static final String APP_PKG = "android.permission.cts.appthatrequestpermission";

    private static final String TMP_DIR = "/data/local/tmp/cts/permissions/";
    private static final String APK_CONTACTS_16 =
            TMP_DIR + "CtsAppThatRequestsContactsPermission16.apk";
    private static final String APK_CONTACTS_15 =
            TMP_DIR + "CtsAppThatRequestsContactsPermission15.apk";
    private static final String APK_CONTACTS_CALLLOG_16 =
            TMP_DIR + "CtsAppThatRequestsContactsAndCallLogPermission16.apk";
    private static final String APK_LOCATION_29 =
            TMP_DIR + "CtsAppThatRequestsLocationPermission29.apk";
    private static final String APK_LOCATION_28 =
            TMP_DIR + "CtsAppThatRequestsLocationPermission28.apk";
    private static final String APK_LOCATION_22 =
            TMP_DIR + "CtsAppThatRequestsLocationPermission22.apk";
    private static final String APK_LOCATION_BACKGROUND_29 =
            TMP_DIR + "CtsAppThatRequestsLocationAndBackgroundPermission29.apk";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final String LOG_TAG = SplitPermissionTest.class.getSimpleName();

    /**
     * Revoke a permission from an app.
     *
     * <p>This correctly handles pre-M apps by setting the app-ops.
     * <p>This also correctly handles the location background permission, but does not handle any
     * other background permission
     *
     * @param packageName The app that should have the permission revoked
     * @param permission The permission to revoke
     */
    private void revokePermission(@NonNull String packageName, @NonNull String permission)
            throws Exception {
        sUiAutomation.revokeRuntimePermission(packageName, permission);

        if (permission.equals(ACCESS_BACKGROUND_LOCATION)) {
            // The app-op for background location is encoded into the mode of the foreground
            // location
            if (isGranted(packageName, ACCESS_COARSE_LOCATION)) {
                setAppOp(packageName, ACCESS_COARSE_LOCATION, MODE_FOREGROUND);
            }
        } else {
            setAppOp(packageName, permission, MODE_IGNORED);
        }
    }

    /**
     * Get all permissions that an app requests. This includes the split permissions.
     *
     * @param packageName The package that requests the permissions.
     *
     * @return The permissions requested by the app
     */
    private @NonNull List<String> getPermissions(@NonNull String packageName) throws Exception {
        PackageInfo appInfo = sContext.getPackageManager().getPackageInfo(packageName,
                GET_PERMISSIONS);

        return Arrays.asList(appInfo.requestedPermissions);
    }

    /**
     * Assert that {@link #APP_PKG} requests a certain permission.
     *
     * @param permName The permission that needs to be requested
     */
    private void assertRequestsPermission(@NonNull String permName) throws Exception {
        assertThat(getPermissions(APP_PKG)).contains(permName);
    }

    /**
     * Assert that {@link #APP_PKG} <u>does not</u> request a certain permission.
     *
     * @param permName The permission that needs to be not requested
     */
    private void assertNotRequestsPermission(@NonNull String permName) throws Exception {
        assertThat(getPermissions(APP_PKG)).doesNotContain(permName);
    }

    /**
     * Assert that a permission is granted to {@link #APP_PKG}.
     *
     * @param permName The permission that needs to be granted
     */
    private void assertPermissionGranted(@NonNull String permName) throws Exception {
        assertThat(isGranted(APP_PKG, permName)).named(permName + " is granted").isTrue();
    }

    /**
     * Assert that a permission is <u>not </u> granted to {@link #APP_PKG}.
     *
     * @param permName The permission that should not be granted
     */
    private void assertPermissionRevoked(@NonNull String permName) throws Exception {
        assertThat(isGranted(APP_PKG, permName)).named(permName + " is granted").isFalse();
    }

    /**
     * Install an APK.
     *
     * @param apkFile The apk to install
     */
    public void install(@NonNull String apkFile) {
        PermissionUtils.install(apkFile);
    }

    @After
    public void uninstallTestApp() {
        uninstallApp(APP_PKG);
    }

    /**
     * Apps with a targetSDK after the split should <u>not</u> have been added implicitly the new
     * permission.
     */
    @Test
    public void permissionsDoNotSplitWithHighTargetSDK() throws Exception {
        install(APK_LOCATION_29);

        assertRequestsPermission(ACCESS_COARSE_LOCATION);
        assertNotRequestsPermission(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * Apps with a targetSDK after the split should <u>not</u> have been added implicitly the new
     * permission.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void permissionsDoNotSplitWithHighTargetSDKPreM() throws Exception {
        install(APK_CONTACTS_16);

        assertRequestsPermission(READ_CONTACTS);
        assertNotRequestsPermission(READ_CALL_LOG);
    }

    /**
     * Apps with a targetSDK before the split should have been added implicitly the new permission.
     */
    @Test
    public void permissionsSplitWithLowTargetSDK() throws Exception {
        install(APK_LOCATION_28);

        assertRequestsPermission(ACCESS_COARSE_LOCATION);
        assertRequestsPermission(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * Apps with a targetSDK before the split should have been added implicitly the new permission.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void permissionsSplitWithLowTargetSDKPreM() throws Exception {
        install(APK_CONTACTS_15);

        assertRequestsPermission(READ_CONTACTS);
        assertRequestsPermission(READ_CALL_LOG);
    }

    /**
     * Permissions are revoked by default for post-M apps
     */
    @Test
    public void nonInheritedStateHighTargetSDK() throws Exception {
        install(APK_LOCATION_29);

        assertPermissionRevoked(ACCESS_COARSE_LOCATION);
    }

    /**
     * Permissions are granted by default for pre-M apps
     */
    @Test
    public void nonInheritedStateHighLowTargetSDKPreM() throws Exception {
        install(APK_CONTACTS_15);

        assertPermissionGranted(READ_CONTACTS);
    }

    /**
     * Permissions are revoked by default for post-M apps. This also applies to permissions added
     * implicitly due to splits.
     */
    @Test
    public void nonInheritedStateLowTargetSDK() throws Exception {
        install(APK_LOCATION_28);

        assertPermissionRevoked(ACCESS_COARSE_LOCATION);
        assertPermissionRevoked(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * Permissions are granted by default for pre-M apps. This also applies to permissions added
     * implicitly due to splits.
     */
    @Test
    public void nonInheritedStateLowTargetSDKPreM() throws Exception {
        install(APK_CONTACTS_15);

        assertPermissionGranted(READ_CONTACTS);
        assertPermissionGranted(READ_CALL_LOG);
    }

    /**
     * The background location permission is never granted by default, not even for pre-M apps.
     */
    @Test
    public void backgroundLocationPermissionDefaultGrantPreM() throws Exception {
        install(APK_LOCATION_22);

        assertPermissionGranted(ACCESS_COARSE_LOCATION);
        assertPermissionRevoked(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * If a permission was granted before the split happens, the new permission should inherit the
     * granted state.
     */
    @Ignore("There is no post-M permission that has the default inheritence behavior")
    @Test
    public void inheritGrantedPermissionState() throws Exception {
        install(APK_LOCATION_29);
        grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);

        install(APK_LOCATION_28);

        assertPermissionGranted(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * Background location permissions do not inherit the state on split.
     */
    @Test
    public void backgroundLocationDoesNotInheritGrantedPermissionState() throws Exception {
        install(APK_LOCATION_29);
        grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);

        install(APK_LOCATION_28);

        assertPermissionRevoked(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * If a permission was granted before the split happens, the new permission should inherit the
     * granted state.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void inheritGrantedPermissionStatePreM() throws Exception {
        install(APK_CONTACTS_16);

        install(APK_CONTACTS_15);

        assertPermissionGranted(READ_CALL_LOG);
    }

    /**
     * If a permission was revoked before the split happens, the new permission should inherit the
     * revoked state.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void inheritRevokedPermissionState() throws Exception {
        install(APK_LOCATION_29);

        install(APK_LOCATION_28);

        assertPermissionRevoked(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * If a permission was revoked before the split happens, the new permission should inherit the
     * revoked state.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void inheritRevokedPermissionStatePreM() throws Exception {
        install(APK_CONTACTS_16);
        revokePermission(APP_PKG, READ_CONTACTS);

        install(APK_CONTACTS_15);

        assertPermissionRevoked(READ_CALL_LOG);
    }

    /**
     * It should be possible to grant a permission implicitly added due to a split.
     */
    @Test
    public void grantNewSplitPermissionState() throws Exception {
        install(APK_LOCATION_28);

        // Background permission can only be granted together with foreground permission
        grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);
        grantPermission(APP_PKG, ACCESS_BACKGROUND_LOCATION);

        assertPermissionGranted(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * It should be possible to grant a permission implicitly added due to a split.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void grantNewSplitPermissionStatePreM() throws Exception {
        install(APK_CONTACTS_15);
        revokePermission(APP_PKG, READ_CONTACTS);

        grantPermission(APP_PKG, READ_CALL_LOG);

        assertPermissionGranted(READ_CALL_LOG);
    }

    /**
     * It should be possible to revoke a permission implicitly added due to a split.
     */
    @Test
    public void revokeNewSplitPermissionState() throws Exception {
        install(APK_LOCATION_28);

        // Background permission can only be granted together with foreground permission
        grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);
        grantPermission(APP_PKG, ACCESS_BACKGROUND_LOCATION);

        revokePermission(APP_PKG, ACCESS_BACKGROUND_LOCATION);

        assertPermissionRevoked(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * It should be possible to revoke a permission implicitly added due to a split.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void revokeNewSplitPermissionStatePreM() throws Exception {
        install(APK_CONTACTS_15);

        revokePermission(APP_PKG, READ_CALL_LOG);

        assertPermissionRevoked(READ_CALL_LOG);
    }

    /**
     * An implicit permission should get revoked when the app gets updated and now requests the
     * permission.
     */
    @Test
    public void newPermissionGetRevokedOnUpgrade() throws Exception {
        install(APK_LOCATION_28);

        // Background permission can only be granted together with foreground permission
        grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);
        grantPermission(APP_PKG, ACCESS_BACKGROUND_LOCATION);

        install(APK_LOCATION_BACKGROUND_29);

        assertPermissionRevoked(ACCESS_BACKGROUND_LOCATION);
    }

    /**
     * An implicit background permission should get revoked when the app gets updated and now
     * requests the permission. Revoking a background permission should have changed the app-op of
     * the foreground permission.
     */
    @Test
    public void newBackgroundPermissionGetRevokedOnUpgrade() throws Exception {
        install(APK_LOCATION_28);

        // Background permission can only be granted together with foreground permission
        grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);
        grantPermission(APP_PKG, ACCESS_BACKGROUND_LOCATION);

        install(APK_LOCATION_BACKGROUND_29);

        assertThat(getAppOp(APP_PKG, ACCESS_COARSE_LOCATION)).named("foreground app-op")
                .isEqualTo(MODE_FOREGROUND);
    }

    /**
     * An implicit permission should get revoked when the app gets updated and now requests the
     * permission.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void newPermissionGetRevokedOnUpgradePreM() throws Exception {
        install(APK_CONTACTS_15);

        install(APK_CONTACTS_CALLLOG_16);

        assertPermissionRevoked(READ_CALL_LOG);
    }

    /**
     * When a requested permission was granted before upgrade it should still be granted.
     */
    @Test
    public void oldPermissionStaysGrantedOnUpgrade() throws Exception {
        install(APK_LOCATION_28);
        grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);

        install(APK_LOCATION_BACKGROUND_29);

        assertPermissionGranted(ACCESS_COARSE_LOCATION);
    }

    /**
     * When a requested permission was granted before upgrade it should still be granted.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void oldPermissionStaysGrantedOnUpgradePreM() throws Exception {
        install(APK_CONTACTS_15);

        install(APK_CONTACTS_CALLLOG_16);

        assertPermissionGranted(READ_CONTACTS);
    }

    /**
     * When a requested permission was revoked before upgrade it should still be revoked.
     */
    @Test
    public void oldPermissionStaysRevokedOnUpgrade() throws Exception {
        install(APK_LOCATION_28);

        install(APK_LOCATION_BACKGROUND_29);

        assertPermissionRevoked(ACCESS_COARSE_LOCATION);
    }

    /**
     * When a requested permission was revoked before upgrade it should still be revoked.
     *
     * <p>(Pre-M version of test)
     */
    @Test
    public void oldPermissionStaysRevokedOnUpgradePreM() throws Exception {
        install(APK_CONTACTS_15);
        revokePermission(APP_PKG, READ_CONTACTS);

        install(APK_CONTACTS_CALLLOG_16);

        assertPermissionRevoked(READ_CONTACTS);
    }
}
