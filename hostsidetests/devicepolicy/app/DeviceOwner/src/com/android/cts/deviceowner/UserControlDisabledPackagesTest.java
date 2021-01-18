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

package com.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;

/**
 * Test {@link DevicePolicyManager#setUserControlDisabledPackages} and
 * {@link DevicePolicyManager#getUserControlDisabledPackages}
 * Hostside test uses "am force-stop" and verifies that app is not stopped.
 */
public class UserControlDisabledPackagesTest extends BaseDeviceOwnerTest {
    private static final String TAG = "UserControlDisabledPackagesTest";

    private static final String TEST_APP_APK = "CtsEmptyTestApp.apk";
    private static final String TEST_APP_PKG = "android.packageinstaller.emptytestapp.cts";
    private static final String SIMPLE_APP_APK ="CtsSimpleApp.apk";
    private static final String SIMPLE_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_APP_ACTIVITY =
            "com.android.cts.launcherapps.simpleapp.SimpleActivityImmediateExit";

    public void testSetUserControlDisabledPackages() throws Exception {
        ArrayList<String> protectedPackages= new ArrayList<>();
        protectedPackages.add(SIMPLE_APP_PKG);
        mDevicePolicyManager.setUserControlDisabledPackages(getWho(), protectedPackages);
        // Launch an activity so that the app exits stopped state.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_APP_PKG, SIMPLE_APP_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.d(TAG, "Starting " + intent + " on user " + mUserId);
        mContext.startActivity(intent);
    }

    public void testForceStopWithUserControlDisabled() throws Exception {
        final ArrayList<String> pkgs = new ArrayList<>();
        pkgs.add(SIMPLE_APP_PKG);
        // Check if package is part of UserControlDisabledPackages before checking if 
        // package is stopped since it is a necessary condition to prevent stopping of
        // package

        assertThat(mDevicePolicyManager.getUserControlDisabledPackages(getWho()))
                .containsExactly(SIMPLE_APP_PKG);
        assertPackageStopped(/* stopped= */ false);
    }

    public void testClearSetUserControlDisabledPackages() throws Exception {
        final ArrayList<String> pkgs = new ArrayList<>();
        mDevicePolicyManager.setUserControlDisabledPackages(getWho(), pkgs);
        assertThat(mDevicePolicyManager.getUserControlDisabledPackages(getWho())).isEmpty();
    }

    public void testForceStopWithUserControlEnabled() throws Exception {
        assertPackageStopped(/* stopped= */ true);
        assertThat(mDevicePolicyManager.getUserControlDisabledPackages(getWho())).isEmpty();
    }

    private boolean isPackageStopped(String packageName) throws Exception {
        PackageInfo packageInfo = mContext.getPackageManager()
                .getPackageInfo(packageName, PackageManager.GET_META_DATA);
        boolean stopped = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_STOPPED)
                == ApplicationInfo.FLAG_STOPPED;
        Log.d(TAG, "Application flags for " + packageName + " on user " + mUserId + " = "
                + Integer.toHexString(packageInfo.applicationInfo.flags) + ". Stopped: " + stopped);
        return stopped;
    }

    private void assertPackageStopped(boolean stopped) throws Exception {
        assertWithMessage("Package %s stopped for user %s", SIMPLE_APP_PKG, mUserId)
                .that(isPackageStopped(SIMPLE_APP_PKG)).isEqualTo(stopped);
    }
}
