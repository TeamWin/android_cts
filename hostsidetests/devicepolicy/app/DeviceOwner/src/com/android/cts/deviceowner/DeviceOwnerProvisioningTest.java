/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static java.util.stream.Collectors.toList;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import com.android.cts.deviceowner.provisioning.SilentProvisioningTestManager;
import java.util.ArrayList;
import java.util.List;


public class DeviceOwnerProvisioningTest extends BaseDeviceOwnerTest {
    private static final String TAG = "DeviceOwnerProvisioningTest";

    private List<String> mEnabledAppsBeforeTest;
    private PackageManager mPackageManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mPackageManager = getContext().getPackageManager();
        mEnabledAppsBeforeTest = getPackageNameList(mPackageManager.getInstalledApplications(
                0 /* Default flags */));
        deviceOwnerProvision();
    }

    @Override
    protected void tearDown() throws Exception {
        enableUninstalledApp();
        super.tearDown();
    }


    public void testProvisionDeviceOwner() throws Exception {
        // empty test to run setUp
    }

    private void enableUninstalledApp() {
        final List<String> currentEnabledApps = getPackageNameList(
                mPackageManager.getInstalledApplications(0 /* Default flags */));

        final List<String> disabledApps = new ArrayList<String>(mEnabledAppsBeforeTest);
        disabledApps.removeAll(currentEnabledApps);

        for (String disabledSystemApp : disabledApps) {
            Log.i(TAG, "enable app : " + disabledSystemApp);
            mDevicePolicyManager.enableSystemApp(BasicAdminReceiver.getComponentName(getContext()),
                    disabledSystemApp);
        }
    }

    private void deviceOwnerProvision() throws Exception {
        SilentProvisioningTestManager provisioningManager =
                SilentProvisioningTestManager.getInstance();
        provisioningManager.startProvisioning(getContext());
        Log.i(TAG, "Start provisioning");

        assertTrue(provisioningManager.waitForProvisioningResult(getContext()));
        Log.i(TAG, "device owner provisioning successful");
    }


    private static List<String> getPackageNameList(List<ApplicationInfo> appInfos) {
        return appInfos.stream()
                .map((ApplicationInfo appInfo) -> appInfo.packageName)
                .collect(toList());
    }
}
