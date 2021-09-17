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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SharedUserMigrationTest {

    private static final String TMP_APK_PATH = "/data/local/tmp/cts/uidmigration";
    private static final String INSTALL_TEST_PKG = "android.uidmigration.cts.InstallTestApp";

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
    }

    private boolean installPackage(String apkPath) {
        return SystemUtil.runShellCommand("pm install -t " + apkPath).equals("Success\n");
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand("pm uninstall " + packageName);
    }
}
