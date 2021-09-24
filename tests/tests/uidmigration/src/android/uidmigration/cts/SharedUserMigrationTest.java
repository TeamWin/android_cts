/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.uidmigration.cts;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.isPermissionGranted;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SharedUserMigrationTest {

    private static final String TMP_APK_PATH = "/data/local/tmp/cts/uidmigration";
    private static final String INSTALL_TEST_PKG = "android.uidmigration.cts.InstallTestApp";
    private static final String PERM_TEST_PKG = "android.uidmigration.cts.PermissionTestApp";

    private Context mContext;
    private PackageManager mPm;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mPm = mContext.getPackageManager();
    }

    @After
    public void tearDown() {
        uninstallPackage(INSTALL_TEST_PKG);
        uninstallPackage(INSTALL_TEST_PKG + "2");
        uninstallPackage(PERM_TEST_PKG);
        uninstallPackage(PERM_TEST_PKG + ".secondary");
    }

    @Test
    public void testAppInstall() throws PackageManager.NameNotFoundException {
        String apk = TMP_APK_PATH + "/InstallTestApp";
        assertTrue(installPackage(apk + ".apk"));
        assertTrue(installPackage(apk + "2.apk"));

        // Both app should share the same UID
        int uid = mPm.getPackageUid(INSTALL_TEST_PKG, 0);
        String[] pkgs = mPm.getPackagesForUid(uid);
        assertNotNull(pkgs);
        assertEquals(2, pkgs.length);

        // Leave shared UID by removing sharedUserId
        assertTrue(installPackage(apk + "Rm.apk"));
        pkgs = mPm.getPackagesForUid(uid);
        assertNotNull(pkgs);
        assertEquals(1, pkgs.length);

        // Uninstall and reinstall the old app
        uninstallPackage(INSTALL_TEST_PKG);
        assertTrue(installPackage(apk + ".apk"));
        pkgs = mPm.getPackagesForUid(uid);
        assertNotNull(pkgs);
        assertEquals(2, pkgs.length);

        // Leave shared UID with sharedUserMaxSdkVersion
        assertTrue(installPackage(apk + "Max.apk"));
        pkgs = mPm.getPackagesForUid(uid);
        assertNotNull(pkgs);
        assertEquals(1, pkgs.length);

        uninstallPackage(INSTALL_TEST_PKG);
        uninstallPackage(INSTALL_TEST_PKG + "2");
    }

    @Test
    public void testPermissionMigration() throws Exception {
        String apk = TMP_APK_PATH + "/PermissionTestApp";
        assertTrue(installPackage(apk + "1.apk"));
        assertTrue(installPackage(apk + "2.apk"));

        String secondaryPkg = PERM_TEST_PKG + ".secondary";

        // Runtime permissions are not granted by default
        assertFalse(isPermissionGranted(secondaryPkg, WRITE_EXTERNAL_STORAGE));

        // Grant a runtime permission
        grantPermission(secondaryPkg, WRITE_EXTERNAL_STORAGE);

        // All apps in the UID group should have the same permissions
        assertTrue(isPermissionGranted(PERM_TEST_PKG, INTERNET));
        assertTrue(isPermissionGranted(PERM_TEST_PKG, WRITE_EXTERNAL_STORAGE));
        assertTrue(isPermissionGranted(secondaryPkg, INTERNET));
        assertTrue(isPermissionGranted(secondaryPkg, WRITE_EXTERNAL_STORAGE));

        // Upgrade and leave shared UID
        assertTrue(installPackage(apk + "3.apk"));

        // The app in the original UID group should no longer have the permissions
        assertFalse(isPermissionGranted(PERM_TEST_PKG, INTERNET));
        assertFalse(isPermissionGranted(PERM_TEST_PKG, WRITE_EXTERNAL_STORAGE));

        // The upgraded app should still have the permissions
        assertTrue(isPermissionGranted(secondaryPkg, INTERNET));
        assertTrue(isPermissionGranted(secondaryPkg, WRITE_EXTERNAL_STORAGE));

        uninstallPackage(PERM_TEST_PKG);
        uninstallPackage(secondaryPkg);
    }

    private boolean installPackage(String apkPath) {
        return runShellCommand("pm install --force-queryable -t " + apkPath).equals("Success\n");
    }

    private void uninstallPackage(String packageName) {
        runShellCommand("pm uninstall " + packageName);
    }
}
