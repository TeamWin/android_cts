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

package com.android.cts.deviceandprofileowner;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.pm.PackageManager;

public class ApplicationHiddenParentTest extends BaseDeviceAdminTest {

    private DevicePolicyManager mParentDevicePolicyManager;
    private PackageManager mPackageManager;

    private static final String SYSTEM_PACKAGE_TO_HIDE = "com.google.android.youtube";
    private static final String NON_SYSTEM_PACKAGE_TO_HIDE = "com.android.cts.permissionapp";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mParentDevicePolicyManager =
                mDevicePolicyManager.getParentProfileInstance(ADMIN_RECEIVER_COMPONENT);
        mPackageManager = mContext.getPackageManager();
        assertThat(mParentDevicePolicyManager).isNotNull();

        assertThat(mDevicePolicyManager.isProfileOwnerApp(ADMIN_RECEIVER_COMPONENT.getPackageName())).isTrue();
        assertThat(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()).isTrue();
    }

    public void testSetApplicationHidden_systemPackage()
            throws PackageManager.NameNotFoundException {
        assertThat(mPackageManager.getPackageInfo(SYSTEM_PACKAGE_TO_HIDE, 0)).isNotNull();

        assertThat(mParentDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                SYSTEM_PACKAGE_TO_HIDE, true)).isTrue();
        assertThat(mParentDevicePolicyManager.isApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                SYSTEM_PACKAGE_TO_HIDE)).isTrue();
        assertThat(mPackageManager.getPackageInfo(SYSTEM_PACKAGE_TO_HIDE,
                PackageManager.MATCH_UNINSTALLED_PACKAGES)).isNotNull();

        assertThat(mParentDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                SYSTEM_PACKAGE_TO_HIDE, false)).isTrue();
        assertThat(mParentDevicePolicyManager.isApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                SYSTEM_PACKAGE_TO_HIDE)).isFalse();
        assertThat(mPackageManager.getPackageInfo(SYSTEM_PACKAGE_TO_HIDE, 0)).isNotNull();
    }

    public void testSetApplicationHidden_nonSystemPackage() {
        assertThrows(IllegalArgumentException.class, () -> {
            mParentDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                    NON_SYSTEM_PACKAGE_TO_HIDE, true);
            mParentDevicePolicyManager.isApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                    NON_SYSTEM_PACKAGE_TO_HIDE);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            mParentDevicePolicyManager.setApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                    NON_SYSTEM_PACKAGE_TO_HIDE, false);
            mParentDevicePolicyManager.isApplicationHidden(ADMIN_RECEIVER_COMPONENT,
                    NON_SYSTEM_PACKAGE_TO_HIDE);
        });
    }
}
