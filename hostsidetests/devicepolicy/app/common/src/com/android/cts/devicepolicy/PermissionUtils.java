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

package com.android.cts.devicepolicy;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static junit.framework.TestCase.assertNotNull;

import static org.junit.Assert.assertEquals;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.platform.app.InstrumentationRegistry;

public class PermissionUtils {

    private static final String ACTION_CHECK_HAS_PERMISSION
            = "com.android.cts.permission.action.CHECK_HAS_PERMISSION";
    private static final String ACTION_REQUEST_PERMISSION
            = "com.android.cts.permission.action.REQUEST_PERMISSION";
    private static final String EXTRA_PERMISSION = "com.android.cts.permission.extra.PERMISSION";

    public static void launchActivityAndCheckPermission(PermissionBroadcastReceiver receiver,
            String permission, int expected, String packageName, String activityName)
            throws Exception {
        launchActivityWithAction(permission, ACTION_CHECK_HAS_PERMISSION,
                packageName, activityName);
        assertEquals(expected, receiver.waitForBroadcast());
    }

    public static void launchActivityAndRequestPermission(PermissionBroadcastReceiver receiver,
            String permission, int expected, String packageName, String activityName)
            throws Exception {
        launchActivityWithAction(permission, ACTION_REQUEST_PERMISSION,
                packageName, activityName);
        assertEquals(expected, receiver.waitForBroadcast());
    }

    public static void launchActivityAndRequestPermission(PermissionBroadcastReceiver
            receiver, UiDevice device, String permission, int expected,
            String packageName, String activityName) throws Exception {
        final String resName;
        switch(expected) {
            case PERMISSION_DENIED:
                resName = "permission_deny_button";
                break;
            case PERMISSION_GRANTED:
                resName = "permission_allow_button";
                break;
            default:
                throw new IllegalArgumentException("Invalid expected permission");
        }
        launchActivityWithAction(permission, ACTION_REQUEST_PERMISSION,
                packageName, activityName);
        pressPermissionPromptButton(device, resName);
        assertEquals(expected, receiver.waitForBroadcast());
    }

    private static void launchActivityWithAction(String permission, String action,
            String packageName, String activityName) {
        Intent launchIntent = new Intent();
        launchIntent.setComponent(new ComponentName(packageName, activityName));
        launchIntent.putExtra(EXTRA_PERMISSION, permission);
        launchIntent.setAction(action);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        getContext().startActivity(launchIntent);
    }

    public static void checkPermission(String permission, int expected, String packageName) {
        assertEquals(getContext().getPackageManager()
                .checkPermission(permission, packageName), expected);
    }

    /**
     * Correctly check a runtime permission. This also works for pre-m apps.
     */
    public static void checkPermissionAndAppOps(String permission, int expected, String packageName)
            throws Exception {
        assertEquals(checkPermissionAndAppOps(permission, packageName), expected);
    }

    private static int checkPermissionAndAppOps(String permission, String packageName)
            throws Exception {
        PackageInfo packageInfo = getContext().getPackageManager().getPackageInfo(packageName, 0);
        if (getContext().checkPermission(permission, -1, packageInfo.applicationInfo.uid)
                == PERMISSION_DENIED) {
            return PERMISSION_DENIED;
        }

        AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
        if (appOpsManager != null && appOpsManager.noteProxyOpNoThrow(
                AppOpsManager.permissionToOp(permission), packageName,
                packageInfo.applicationInfo.uid, null, null)
                != AppOpsManager.MODE_ALLOWED) {
            return PERMISSION_DENIED;
        }

        return PERMISSION_GRANTED;
    }

    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static void pressPermissionPromptButton(UiDevice mDevice, String resName) {
        if (resName == null) {
            throw new IllegalArgumentException("resName must not be null");
        }

        BySelector selector = By
                .clazz(android.widget.Button.class.getName())
                .res("com.android.packageinstaller", resName);
        mDevice.wait(Until.hasObject(selector), 5000);
        UiObject2 button = mDevice.findObject(selector);
        assertNotNull("Couldn't find button with resource id: " + resName, button);
        button.click();
    }
}
