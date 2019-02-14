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

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_FOREGROUND;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.permissionToOp;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

public class PermissionUtils {
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    /**
     * Get the state of an app-op.
     *
     * @param packageName The package the app-op belongs to
     * @param permission The permission the app-op belongs to
     *
     * @return The mode the op is on
     */
    static int getAppOp(@NonNull String packageName, @NonNull String permission) throws Exception {
        return callWithShellPermissionIdentity(
                () -> sContext.getSystemService(AppOpsManager.class).unsafeCheckOpRaw(
                        permissionToOp(permission),
                        sContext.getPackageManager().getPackageUid(packageName, 0), packageName));
    }

    /**
     * Install an APK.
     *
     * @param apkFile The apk to install
     */
    static void install(@NonNull String apkFile) {
        runShellCommand("pm install -r --force-sdk " + apkFile);
    }

    /**
     * Uninstall a package.
     *
     * @param packageName Name of package to be uninstalled
     */
    static void uninstallApp(@NonNull String packageName) {
        runShellCommand("pm uninstall " + packageName);
    }

    /**
     * Set a new state for an app-op
     *
     * @param packageName The package the app-op belongs to
     * @param permission The permission the app-op belongs to
     * @param mode The new mode
     */
    static void setAppOp(@NonNull String packageName, @NonNull String permission, int mode) {
        runWithShellPermissionIdentity(() -> sContext.getSystemService(AppOpsManager.class)
                .setUidMode(permissionToOp(permission),
                        sContext.getPackageManager().getPackageUid(packageName, 0), mode));
    }

    /**
     * Checks if a permission is granted for a package.
     *
     * <p>This correctly handles pre-M apps by checking the app-ops instead.
     * <p>This also correctly handles the location background permission, but does not handle any
     * other background permission
     *
     * @param packageName The package that might have the permission granted
     * @param permission The permission that might be granted
     *
     * @return {@code true} iff the permission is granted
     */
    static boolean isGranted(@NonNull String packageName, @NonNull String permission)
            throws Exception {
        if (sContext.getPackageManager().checkPermission(permission, packageName)
                == PERMISSION_DENIED) {
            return false;
        }

        if (permission.equals(ACCESS_BACKGROUND_LOCATION)) {
            // The app-op for background location is encoded into the mode of the foreground
            // location
            return getAppOp(packageName, ACCESS_COARSE_LOCATION) == MODE_ALLOWED;
        } else {
            return getAppOp(packageName, permission) != MODE_IGNORED;
        }
    }

    /**
     * Grant a permission to an app.
     *
     * <p>This correctly handles pre-M apps by setting the app-ops.
     * <p>This also correctly handles the location background permission, but does not handle any
     * other background permission
     *
     * @param packageName The app that should have the permission granted
     * @param permission The permission to grant
     */
    static void grantPermission(@NonNull String packageName, @NonNull String permission)
            throws Exception {
        sUiAutomation.grantRuntimePermission(packageName, permission);

        if (permission.equals(ACCESS_BACKGROUND_LOCATION)) {
            // The app-op for background location is encoded into the mode of the foreground
            // location
            if (sContext.getPackageManager().checkPermission(ACCESS_COARSE_LOCATION, packageName)
                    == PERMISSION_GRANTED) {
                setAppOp(packageName, ACCESS_COARSE_LOCATION, MODE_ALLOWED);
            } else {
                setAppOp(packageName, ACCESS_COARSE_LOCATION, MODE_FOREGROUND);
            }
        } else if (permission.equals(ACCESS_COARSE_LOCATION)) {
            // The app-op for location depends on the state of the bg location
            if (sContext.getPackageManager().checkPermission(ACCESS_BACKGROUND_LOCATION,
                    packageName) == PERMISSION_GRANTED) {
                setAppOp(packageName, ACCESS_COARSE_LOCATION, MODE_ALLOWED);
            } else {
                setAppOp(packageName, ACCESS_COARSE_LOCATION, MODE_FOREGROUND);
            }
        } else {
            setAppOp(packageName, permission, MODE_ALLOWED);
        }
    }
}
