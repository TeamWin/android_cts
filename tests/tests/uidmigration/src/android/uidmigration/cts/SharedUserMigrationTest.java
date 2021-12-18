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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SharedUserMigrationTest {

    private static final String TMP_APK_PATH = "/data/local/tmp/cts/uidmigration";
    private static final String INSTALL_TEST_PKG = "android.uidmigration.cts.InstallTestApp";
    private static final String PERM_TEST_PKG = "android.uidmigration.cts.PermissionTestApp";
    private static final String DATA_TEST_PKG = "android.uidmigration.cts.DataTestApp";
    private static final String ACTION_COUNTDOWN = "android.uidmigration.cts.ACTION_COUNTDOWN";

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
        uninstallPackage(DATA_TEST_PKG);
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

    @Test
    public void testDataMigration() throws PackageManager.NameNotFoundException {
        String apk = TMP_APK_PATH + "/DataTestApp";
        assertTrue(installPackage(apk + "1.apk"));
        int oldUid = mPm.getPackageUid(DATA_TEST_PKG, 0);

        String authority = DATA_TEST_PKG + ".provider";
        ContentResolver resolver = mContext.getContentResolver();

        // Ask the app to generate a new random UUID and persist in data
        Bundle result = resolver.call(authority, "", null, null);
        assertNotNull(result);
        String oldUUID = result.getString("uuid");
        assertNotNull(oldUUID);

        // Need 2 receivers because the intent filters are not compatible
        PackageBroadcastVerifier verifier = new PackageBroadcastVerifier(oldUid);
        BroadcastReceiver r1 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                verifier.verify(intent);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        mContext.registerReceiver(r1, filter);

        BroadcastReceiver r2 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                verifier.verify(intent);
            }
        };
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_UID_REMOVED);
        filter.addAction(ACTION_COUNTDOWN);
        mContext.registerReceiver(r2, filter);

        // Update the data test APK and make sure UID changed
        assertTrue(installPackage(apk + "2.apk"));
        int newUid = mPm.getPackageUid(DATA_TEST_PKG, 0);
        assertNotEquals(oldUid, newUid);

        // Ensure system broadcasts are delivered properly
        try {
            assertTrue(verifier.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertEquals(newUid, verifier.newUid);

        mContext.unregisterReceiver(r1);
        mContext.unregisterReceiver(r2);

        // Ask the app again for a UUID. If data migration is working, it shall be the same
        result = resolver.call(authority, "", null, null);
        assertNotNull(result);
        String newUUID = result.getString("uuid");
        assertEquals(oldUUID, newUUID);

        uninstallPackage(DATA_TEST_PKG);
    }

    private boolean installPackage(String apkPath) {
        return runShellCommand("pm install --force-queryable -t " + apkPath).equals("Success\n");
    }

    private void uninstallPackage(String packageName) {
        runShellCommand("pm uninstall " + packageName);
    }

    static class PackageBroadcastVerifier extends CountDownLatch {

        public int newUid = -1;

        private final int mPreviousUid;
        private int mCounter = 0;

        PackageBroadcastVerifier(int uid) {
            super(1);
            mPreviousUid = uid;
        }

        void verify(Intent intent) {
            String action = intent.getAction();
            assertNotNull(action);

            if (action.equals(Intent.ACTION_UID_REMOVED)) {
                // Not the test package, none of our business
                if (intent.getIntExtra(Intent.EXTRA_UID, -1) != mPreviousUid) {
                    return;
                }
            }

            Uri data = intent.getData();
            if (data != null) {
                assertEquals("package", data.getScheme());
                String pkg = data.getSchemeSpecificPart();
                assertNotNull(pkg);
                // Not the test package, none of our business
                if (!DATA_TEST_PKG.equals(pkg)) {
                    return;
                }
            }

            // Broadcasts must come in the following order:
            // ACTION_PACKAGE_REMOVED -> ACTION_UID_REMOVED
            // -> ACTION_PACKAGE_ADDED -> ACTION_COUNTDOWN

            mCounter++;

            switch (action) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    assertEquals(1, mCounter);
                    assertFalse(intent.hasExtra(Intent.EXTRA_REPLACING));
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_UID_CHANGING, false));
                    assertEquals(mPreviousUid, intent.getIntExtra(Intent.EXTRA_UID, -1));
                    break;
                case Intent.ACTION_UID_REMOVED:
                    assertEquals(2, mCounter);
                    assertFalse(intent.hasExtra(Intent.EXTRA_REPLACING));
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_UID_CHANGING, false));
                    assertEquals(DATA_TEST_PKG, intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME));
                    break;
                case Intent.ACTION_PACKAGE_ADDED:
                    assertEquals(3, mCounter);
                    assertFalse(intent.hasExtra(Intent.EXTRA_REPLACING));
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_UID_CHANGING, false));
                    newUid = intent.getIntExtra(Intent.EXTRA_UID, mPreviousUid);
                    assertNotEquals(mPreviousUid, newUid);
                    assertEquals(mPreviousUid, intent.getIntExtra(Intent.EXTRA_PREVIOUS_UID, -1));
                    break;
                case ACTION_COUNTDOWN:
                    assertEquals(4, mCounter);
                    assertEquals(newUid, intent.getIntExtra(Intent.EXTRA_UID, -2));
                    countDown();
                    break;
                case Intent.ACTION_PACKAGE_REPLACED:
                    fail("PACKAGE_REPLACED should not be called!");
                    break;
                default:
                    fail("Unknown action received");
                    break;
            }
        }
    }
}
