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

import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;
import static android.content.pm.PackageManager.GET_PERMISSIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.platform.test.annotations.SecurityTest;
import android.support.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;

public class RemovePermissionTest {
    private static final String APP_PKG_NAME = "android.permission.cts.revokepermissionwhenremoved";
    private static final String USER_PKG_NAME =
            "android.permission.cts.revokepermissionwhenremoved.userapp";
    private static final String TEST_PERMISSION =
            "android.permission.cts.revokepermissionwhenremoved.TestPermission";

    private Context mContext;
    private Instrumentation mInstrumentation;

    @Before
    public void setContextAndInstrumentation() {
        mContext = InstrumentationRegistry.getTargetContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Before
    public void wakeUpScreen() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");
    }

    private boolean permissionGranted(String permName) throws PackageManager.NameNotFoundException {
        PackageInfo appInfo = mContext.getPackageManager().getPackageInfo(USER_PKG_NAME,
                GET_PERMISSIONS);

        for (int i = 0; i < appInfo.requestedPermissions.length; i++) {
            if (appInfo.requestedPermissions[i].equals(permName)
                    && ((appInfo.requestedPermissionsFlags[i] & REQUESTED_PERMISSION_GRANTED)
                    != 0)) {
                return true;
            }
        }
        return false;
    }

    private void installApp(String apk) {
        String installResult = SystemUtil.runShellCommand(
                "pm install -r data/local/tmp/cts/permissions/" + apk + ".apk");
        assertEquals("Success", installResult.trim());
    }

    private void uninstallApp(String pkg) {
        String uninstallResult = SystemUtil.runShellCommand(
                "pm uninstall " + pkg);
        assertEquals("Success", uninstallResult.trim());
    }

    private void grantPermission(String pkg, String permission) {
        mInstrumentation.getUiAutomation().grantRuntimePermission(
                pkg, permission);
    }

    @SecurityTest
    @Test
    public void permissionShouldBeRevokedIfRemoved() throws Throwable {
        installApp("CtsAdversarialPermissionDefinerApp");
        installApp("CtsAdversarialPermissionUserApp");

        grantPermission(USER_PKG_NAME, TEST_PERMISSION);
        assertTrue(permissionGranted(TEST_PERMISSION));

        // Uninstall app which defines a permission with the same name as in victim app.
        // Install the victim app.
        uninstallApp(APP_PKG_NAME + ".AdversarialPermissionDefinerApp");
        installApp("CtsVictimPermissionDefinerApp");
        assertFalse(permissionGranted(TEST_PERMISSION));
    }
}
