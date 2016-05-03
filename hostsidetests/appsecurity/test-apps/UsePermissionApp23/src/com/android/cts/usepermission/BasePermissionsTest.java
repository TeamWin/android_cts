/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.usepermission;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiSelector;
import android.util.ArrayMap;
import android.widget.Switch;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(AndroidJUnit4.class)
public abstract class BasePermissionsTest {
    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final long IDLE_TIMEOUT_MILLIS = 500;
    private static final long GLOBAL_TIMEOUT_MILLIS = 5000;

    private static Map<String, String> sPermissionToLabelResNameMap = new ArrayMap<>();
    static {
        // Contacts
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_CONTACTS,
                "@android:string/permgrouplab_contacts");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_CONTACTS,
                "@android:string/permgrouplab_contacts");
        // Calendar
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_CALENDAR,
                "@android:string/permgrouplab_calendar");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_CALENDAR,
                "@android:string/permgrouplab_calendar");
        // SMS
        sPermissionToLabelResNameMap.put(Manifest.permission.SEND_SMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.RECEIVE_SMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_SMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.RECEIVE_WAP_PUSH,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.RECEIVE_MMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put("android.permission.READ_CELL_BROADCASTS",
                "@android:string/permgrouplab_sms");
        // Storage
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_EXTERNAL_STORAGE,
                "@android:string/permgrouplab_storage");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "@android:string/permgrouplab_storage");
        // Location
        sPermissionToLabelResNameMap.put(Manifest.permission.ACCESS_FINE_LOCATION,
                "@android:string/permgrouplab_location");
        sPermissionToLabelResNameMap.put(Manifest.permission.ACCESS_COARSE_LOCATION,
                "@android:string/permgrouplab_location");
        // Phone
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_PHONE_STATE,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.CALL_PHONE,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put("android.permission.ACCESS_IMS_CALL_SERVICE",
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_CALL_LOG,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_CALL_LOG,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.ADD_VOICEMAIL,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.USE_SIP,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.PROCESS_OUTGOING_CALLS,
                "@android:string/permgrouplab_phone");
        // Microphone
        sPermissionToLabelResNameMap.put(Manifest.permission.RECORD_AUDIO,
                "@android:string/permgrouplab_microphone");
        // Camera
        sPermissionToLabelResNameMap.put(Manifest.permission.CAMERA,
                "@android:string/permgrouplab_camera");
        // Body sensors
        sPermissionToLabelResNameMap.put(Manifest.permission.BODY_SENSORS,
                "@android:string/permgrouplab_sensors");
    }

    private Context mContext;
    private Resources mPlatformResources;

    protected static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    protected static void assertPermissionRequestResult(BasePermissionActivity.Result result,
            int requestCode, String[] permissions, boolean[] granted) {
        assertEquals(requestCode, result.requestCode);
        for (int i = 0; i < permissions.length; i++) {
            assertEquals(permissions[i], result.permissions[i]);
            assertEquals(granted[i] ? PackageManager.PERMISSION_GRANTED
                    : PackageManager.PERMISSION_DENIED, result.grantResults[i]);

        }
    }

    protected static UiDevice getUiDevice() {
        return UiDevice.getInstance(getInstrumentation());
    }

    protected static Activity launchActivity(String packageName,
            Class<?> clazz, Bundle extras) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(packageName, clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) {
            intent.putExtras(extras);
        }
        Activity activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();

        return activity;
    }

    @Before
    public void beforeTest() {
        mContext = InstrumentationRegistry.getTargetContext();
        try {
            Context platformContext = mContext.createPackageContext(PLATFORM_PACKAGE_NAME, 0);
            mPlatformResources = platformContext.getResources();
        } catch (PackageManager.NameNotFoundException e) {
            /* cannot happen */
        }
    }

    protected BasePermissionActivity.Result requestPermissions(
            String[] permissions, int requestCode, Class<?> clazz, Runnable postRequestAction)
            throws Exception {
        // Start an activity
        BasePermissionActivity activity = (BasePermissionActivity) launchActivity(
                getInstrumentation().getTargetContext().getPackageName(), clazz, null);

        activity.waitForOnCreate();

        // Request the permissions
        activity.requestPermissions(permissions, requestCode);

        // Define a more conservative idle criteria
        getInstrumentation().getUiAutomation().waitForIdle(
                IDLE_TIMEOUT_MILLIS, GLOBAL_TIMEOUT_MILLIS);

        // Perform the post-request action
        if (postRequestAction != null) {
            postRequestAction.run();
        }

        BasePermissionActivity.Result result = activity.getResult();
        activity.finish();
        return result;
    }

    protected void clickAllowButton() throws Exception {
        getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.packageinstaller:id/permission_allow_button")).click();
    }

    protected void clickDenyButton() throws Exception {
        getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.packageinstaller:id/permission_deny_button")).click();
    }

    protected void clickDontAskAgainCheckbox() throws Exception {
        getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.packageinstaller:id/do_not_ask_checkbox")).click();
    }

    protected void grantPermission(String permission) throws Exception {
        grantPermissions(new String[]{permission});
    }

    protected void grantPermissions(String[] permissions) throws Exception {
        setPermissionGrantState(permissions, true, false);
    }

    protected void revokePermission(String permission) throws Exception {
        revokePermissions(new String[] {permission}, false);
    }

    protected void revokePermissions(String[] permissions, boolean legacyApp) throws Exception {
        setPermissionGrantState(permissions, false, legacyApp);
    }

    private void setPermissionGrantState(String[] permissions, boolean granted,
            boolean legacyApp) throws Exception {
        getUiDevice().pressBack();
        getUiDevice().waitForIdle();
        getUiDevice().pressBack();
        getUiDevice().waitForIdle();

        // Open the app details settings
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + mContext.getPackageName()));
        mContext.startActivity(intent);

        getUiDevice().waitForIdle();

        // Open the permissions UI
        UiObject permissionItem = getUiDevice().findObject(new UiSelector().text("Permissions"));
        permissionItem.click();

        getUiDevice().waitForIdle();

        for (String permission : permissions) {
            // Find the permission toggle
            String permissionLabel = getPermissionLabel(permission);

            UiObject2 toggleSwitch = null;
            UiObject2 current = getUiDevice().findObject(By.text(permissionLabel));
            Assert.assertNotNull("Permission should be present");

            while (toggleSwitch == null) {
                UiObject2 parent = current.getParent();
                if (parent == null) {
                    fail("Cannot find permission list item");
                }
                toggleSwitch = current.findObject(By.clazz(Switch.class));
                current = parent;
            }

            final boolean wasGranted = toggleSwitch.isChecked();
            if (granted != wasGranted) {
                // Toggle the permission
                toggleSwitch.click();

                getUiDevice().waitForIdle();

                if (wasGranted && legacyApp) {
                    String packageName = getInstrumentation().getContext().getPackageManager()
                            .getPermissionControllerPackageName();
                    String resIdName = "com.android.packageinstaller"
                            + ":string/grant_dialog_button_deny_anyway";
                    Resources resources = getInstrumentation().getContext()
                            .createPackageContext(packageName, 0).getResources();
                    final int confirmResId = resources.getIdentifier(resIdName, null, null);
                    String confirmTitle = resources.getString(confirmResId);
                    UiObject denyAnyway = getUiDevice().findObject(new UiSelector()
                            .text(confirmTitle.toUpperCase()));
                    denyAnyway.click();

                    getUiDevice().waitForIdle();
                }
            }
        }

        getUiDevice().pressBack();
        getUiDevice().waitForIdle();
        getUiDevice().pressBack();
        getUiDevice().waitForIdle();
    }

    private String getPermissionLabel(String permission) throws Exception {
        String labelResName = sPermissionToLabelResNameMap.get(permission);
        assertNotNull("Unknown permisison " + permission, labelResName);
        final int resourceId = mPlatformResources.getIdentifier(labelResName, null, null);
        return mPlatformResources.getString(resourceId);
    }
}
