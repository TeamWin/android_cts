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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.revokePermission;
import static android.permission.cts.PermissionUtils.uninstallApp;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;

import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the behavior of the
 * {@link android.Manifest.permission_group#NEARBY_DEVICES} permission group
 * under various permutations of grant states.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class NearbyDevicesPermissionTest {
    private static final String TEST_APP_PKG = "android.permission.cts.appthatrequestpermission";
    private static final String TEST_APP_AUTHORITY = "appthatrequestpermission";

    private static final String TMP_DIR = "/data/local/tmp/cts/permissions/";
    private static final String APK_BLUETOOTH_30 = TMP_DIR
            + "CtsAppThatRequestsBluetoothPermission30.apk";
    private static final String APK_BLUETOOTH_31 = TMP_DIR
            + "CtsAppThatRequestsBluetoothPermission31.apk";
    private static final String APK_BLUETOOTH_NEVER_FOR_LOCATION_31 = TMP_DIR
            + "CtsAppThatRequestsBluetoothPermissionNeverForLocation31.apk";

    private static final String METHOD_SCAN_BLUETOOTH = "scan_bluetooth";

    private enum Result {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    @BeforeClass
    public static void enableTestMode() {
        runShellCommand("dumpsys activity service"
                + " com.android.bluetooth/.btservice.AdapterService set-test-mode enabled");
    }

    @AfterClass
    public static void disableTestMode() {
        runShellCommand("dumpsys activity service"
                + " com.android.bluetooth/.btservice.AdapterService set-test-mode disabled");
    }

    @After
    public void uninstallTestApp() {
        uninstallApp(TEST_APP_PKG);
    }

    @Test
    public void testRequestBluetoothPermission30_Default() throws Throwable {
        install(APK_BLUETOOTH_30);
        assertScanBluetoothResult(Result.EMPTY);
    }

    @Test
    public void testRequestBluetoothPermission30_GrantLocation() throws Throwable {
        install(APK_BLUETOOTH_30);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);
    }

    @Test
    public void testRequestBluetoothPermission31_Default() throws Throwable {
        install(APK_BLUETOOTH_31);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    public void testRequestBluetoothPermission31_GrantNearby() throws Throwable {
        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.EMPTY);
    }

    @Test
    public void testRequestBluetoothPermission31_GrantLocation() throws Throwable {
        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    public void testRequestBluetoothPermission31_GrantNearby_GrantLocation() throws Throwable {
        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);
    }

    @Test
    public void testRequestBluetoothPermissionNeverForLocation31_Default() throws Throwable {
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    public void testRequestBluetoothPermissionNeverForLocation31_GrantNearby() throws Throwable {
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.FILTERED);
    }

    @Test
    public void testRequestBluetoothPermissionNeverForLocation31_GrantLocation() throws Throwable {
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    public void testRequestBluetoothPermissionNeverForLocation31_GrantNearby_GrantLocation()
            throws Throwable {
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FILTERED);
    }

    /**
     * Verify that upgrading an app doesn't gain them any access to Bluetooth
     * scan results; they'd always need to involve the user to gain permissions.
     */
    @Test
    public void testRequestBluetoothPermission_Upgrade() throws Throwable {
        install(APK_BLUETOOTH_30);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);

        // Upgrading to target a new SDK level means they need to explicitly
        // request the new runtime permission; by default it's denied
        install(APK_BLUETOOTH_31);
        assertScanBluetoothResult(Result.EXCEPTION);

        // If the user does grant it, they can scan again
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.FULL);
    }

    /**
     * Verify that downgrading an app doesn't gain them any access to Bluetooth
     * scan results; they'd always need to involve the user to gain permissions.
     */
    @Test
    public void testRequestBluetoothPermission_Downgrade() throws Throwable {
        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);

        // Revoking nearby permission means modern app can't scan
        revokePermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        revokePermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.EXCEPTION);

        // And if they attempt to downgrade, confirm that they can't obtain the
        // split-permission grant from the older non-runtime permissions
        install(APK_BLUETOOTH_30);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    private void assertScanBluetoothResult(Result expected) {
        final ContentResolver resolver = InstrumentationRegistry.getTargetContext()
                .getContentResolver();
        final Bundle res = resolver.call(TEST_APP_AUTHORITY, METHOD_SCAN_BLUETOOTH, null, null);
        assertEquals(expected, Result.values()[res.getInt(Intent.EXTRA_INDEX)]);
    }
}
