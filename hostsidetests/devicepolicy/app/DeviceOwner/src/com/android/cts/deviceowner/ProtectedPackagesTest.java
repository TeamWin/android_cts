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

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;

/**
 * Test {@link DevicePolicyManager#setProtectedPackages} and
 * {@link DevicePolicyManager#getProtectedPackages}
 * Hostside test uses "am force-stop" and verifies that app is not stopped.
 */
public class ProtectedPackagesTest extends BaseDeviceOwnerTest {

    private static final String TEST_APP_APK = "CtsEmptyTestApp.apk";
    private static final String TEST_APP_PKG = "android.packageinstaller.emptytestapp.cts";
    private static final String SIMPLE_APP_APK ="CtsSimpleApp.apk";
    private static final String SIMPLE_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_APP_ACTIVITY =
            "com.android.cts.launcherapps.simpleapp.SimpleActivityImmediateExit";

    public void testSetProtectedPackages() throws Exception {
        ArrayList<String> protectedPackages= new ArrayList<>();
        protectedPackages.add(SIMPLE_APP_PKG);
        mDevicePolicyManager.setProtectedPackages(getWho(), protectedPackages);

        // Launch app so that the app exits stopped state.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_APP_PKG, SIMPLE_APP_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

    }

    public void testForceStopForProtectedPackages() throws Exception {
        final ArrayList<String> pkgs = new ArrayList<>();
        pkgs.add(SIMPLE_APP_PKG);
        assertFalse(isPackageStopped(SIMPLE_APP_PKG));
        assertEquals(pkgs, mDevicePolicyManager.getProtectedPackages(getWho()));
    }

    public void testClearProtectedPackages() throws Exception {
        final ArrayList<String> pkgs = new ArrayList<>();
        mDevicePolicyManager.setProtectedPackages(getWho(), pkgs);
        assertEquals(pkgs, mDevicePolicyManager.getProtectedPackages(getWho()));
    }

    public void testForceStopForUnprotectedPackages() throws Exception {
        assertTrue(isPackageStopped(SIMPLE_APP_PKG));
    }

    private boolean isPackageStopped(String packageName) throws Exception {
        PackageInfo packageInfo = mContext.getPackageManager()
                .getPackageInfo(packageName, PackageManager.GET_META_DATA);
        return ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_STOPPED)
                == ApplicationInfo.FLAG_STOPPED) ? true : false;
    }

}
