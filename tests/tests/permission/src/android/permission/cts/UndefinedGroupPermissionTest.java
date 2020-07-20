/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.permission.cts.PermissionUtils.eventually;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that the permissioncontroller behaves normally when an app defines a permission in the
 * android.permission-group.UNDEFINED group
 */
public class UndefinedGroupPermissionTest {

    private static final String TEST_APP_DEFINES_UNDEFINED_PERMISSION_GROUP_ELEMENT_APK =
            "/data/local/tmp/cts/permissions/AppThatDefinesUndefinedPermissionGroupElement.apk";

    private static final String APP_PKG_NAME =
            "android.permission.cts.appthatrequestpermission";

    private static final String EXTRA_PERMISSIONS =
            "android.permission.cts.appthatrequestpermission.extra.PERMISSIONS";

    public static final String TEST = "android.permission.cts.appthatrequestpermission.TEST";

    private Instrumentation mInstrumentation;
    private UiDevice mUiDevice;
    private Context mContext;
    private PackageManager mPm;

    @Before
    public void install() {
        runShellCommand("pm install -r " + TEST_APP_DEFINES_UNDEFINED_PERMISSION_GROUP_ELEMENT_APK);
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(mInstrumentation);
        mContext = InstrumentationRegistry.getTargetContext();
        mPm = mContext.getPackageManager();
    }

    @Before
    public void wakeUpScreenAndUnlock() {
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        runShellCommand("input keyevent KEYCODE_MENU");
    }

    @Test
    public void testOtherGroupPermissionsNotGranted_1() {
        testOtherGroupPermissionsNotGranted(CAMERA, RECORD_AUDIO);
    }

    @Test
    public void testOtherGroupPermissionsNotGranted_2() {
        testOtherGroupPermissionsNotGranted(TEST, RECORD_AUDIO);
    }
    @Test
    public void testOtherGroupPermissionsNotGranted_3() {
        testOtherGroupPermissionsNotGranted(CAMERA, TEST);
    }

    /**
     * When the custom permission is granted nothing else gets granted as a byproduct.
     */
    @Test
    public void testCustomPermissionGrantedAlone() {
        assertEquals(mPm.checkPermission(CAMERA, APP_PKG_NAME), PERMISSION_DENIED);
        assertEquals(mPm.checkPermission(RECORD_AUDIO, APP_PKG_NAME), PERMISSION_DENIED);
        assertEquals(mPm.checkPermission(TEST, APP_PKG_NAME), PERMISSION_DENIED);

        eventually(() -> {
            startRequestActivity(new String[]{TEST});
            mUiDevice.waitForIdle();
            mUiDevice.findObject(new UiSelector().resourceId(
                    "com.android.permissioncontroller:id/permission_allow_button")).click();
        });

        eventually(() -> {
            assertEquals(mPm.checkPermission(CAMERA, APP_PKG_NAME), PERMISSION_DENIED);
            assertEquals(mPm.checkPermission(RECORD_AUDIO, APP_PKG_NAME), PERMISSION_DENIED);
            assertEquals(mPm.checkPermission("android.permission.cts.appthatrequestpermission.TEST",
                    APP_PKG_NAME), PERMISSION_GRANTED);
        });

    }

    @After
    public void uninstall() {
        runShellCommand("pm uninstall " + APP_PKG_NAME);
    }


    /**
     * If app has one permission granted, then it can't grant itself another permission for free.
     */
    public void testOtherGroupPermissionsNotGranted(String grantedPerm, String targetPermission) {
        // Grant the permission in the background
        runWithShellPermissionIdentity(
                () -> mPm.grantRuntimePermission(
                        APP_PKG_NAME, grantedPerm, android.os.Process.myUserHandle()));
        assertEquals(grantedPerm + " not granted.", mPm.checkPermission(grantedPerm,
                APP_PKG_NAME), PERMISSION_GRANTED);

        // If the dialog shows, success. If not then either the UI is broken or the permission was
        // granted in the background.
        eventually(() -> {
            startRequestActivity(new String[]{targetPermission});
            mUiDevice.waitForIdle();
            try {
                mUiDevice.findObject(new UiSelector().resourceId(
                        "com.android.permissioncontroller:id/permission_allow_button"))
                        .getClassName();
            } catch (UiObjectNotFoundException tolerated) {
                assertEquals("grant dialog never showed.",
                        mPm.checkPermission(targetPermission,
                                APP_PKG_NAME), PERMISSION_GRANTED);
            }
        });

        assertEquals(mPm.checkPermission(targetPermission, APP_PKG_NAME), PERMISSION_DENIED);
    }

    private void startRequestActivity(String[] permissions) {
        mContext.startActivity(new Intent()
                .setComponent(
                        new ComponentName(APP_PKG_NAME, APP_PKG_NAME + ".RequestPermissions"))
                .putExtra(EXTRA_PERMISSIONS, permissions)
                .setFlags(FLAG_ACTIVITY_NEW_TASK));
    }

}
