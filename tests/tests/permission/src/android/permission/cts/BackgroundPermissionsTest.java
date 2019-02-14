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

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.app.AppOpsManager.MODE_FOREGROUND;
import static android.content.pm.PermissionInfo.PROTECTION_DANGEROUS;
import static android.permission.cts.PermissionUtils.getAppOp;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.uninstallApp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.platform.test.annotations.AppModeFull;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BackgroundPermissionsTest {
    private static final String LOG_TAG = BackgroundPermissionsTest.class.getSimpleName();

    /** The package name of all apps used in the test */
    private static final String APP_PKG = "android.permission.cts.appthatrequestpermission";

    private static final String TMP_DIR = "/data/local/tmp/cts/permissions/";
    private static final String APK_LOCATION_BACKGROUND_29 =
            TMP_DIR + "CtsAppThatRequestsLocationAndBackgroundPermission29.apk";
    private static final String APK_LOCATION_29v4 =
            TMP_DIR + "CtsAppThatRequestsLocationPermission29v4.apk";

    @Test
    @AppModeFull(reason = "Instant apps cannot read properties of other packages")
    public void verifybackgroundPermissionsProperties() throws Exception {
        PackageInfo pkg = InstrumentationRegistry.getContext().getPackageManager().getPackageInfo(
                "android", PackageManager.GET_PERMISSIONS);
        ArrayMap<String, String> potentialBackgroundPermissionsToGroup = new ArrayMap<>();

        int numPermissions = pkg.permissions.length;
        for (int i = 0; i < numPermissions; i++) {
            PermissionInfo permission = pkg.permissions[i];

            // background permissions must be dangerous
            if ((permission.getProtection() & PROTECTION_DANGEROUS) != 0) {
                potentialBackgroundPermissionsToGroup.put(permission.name, permission.group);
            }
        }

        for (int i = 0; i < numPermissions; i++) {
            PermissionInfo permission = pkg.permissions[i];
            String backgroundPermissionName = permission.backgroundPermission;

            if (backgroundPermissionName != null) {
                Log.i(LOG_TAG, permission.name + "->" + backgroundPermissionName);

                // foreground permissions must be dangerous
                assertNotEquals(0, permission.getProtection() & PROTECTION_DANGEROUS);

                // All foreground permissions need an app op
                assertNotNull(AppOpsManager.permissionToOp(permission.name));

                // the background permission must exist
                assertTrue(potentialBackgroundPermissionsToGroup
                        .containsKey(backgroundPermissionName));
            }
        }
    }

    /**
     * If a bg permission is lost during an upgrade, the app-op should downgrade to foreground
     */
    @Test
    public void appOpGetsDowngradedWhenBgPermIsNotRequestedAnymore() throws Exception {
        install(APK_LOCATION_BACKGROUND_29);
        try {
            grantPermission(APP_PKG, ACCESS_COARSE_LOCATION);
            install(APK_LOCATION_29v4);

            assertThat(getAppOp(APP_PKG, ACCESS_COARSE_LOCATION)).named(
                    "foreground app-op").isEqualTo(MODE_FOREGROUND);
        } finally {
            uninstallApp(APP_PKG);
        }
    }
}
