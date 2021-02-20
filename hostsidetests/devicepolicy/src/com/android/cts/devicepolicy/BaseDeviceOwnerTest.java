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

package com.android.cts.devicepolicy;

import static org.junit.Assert.fail;

import com.android.tradefed.log.LogUtil.CLog;

/**
 * Base class for {@link DeviceOwnerTest} and {@link HeadlessSystemUserDeviceOwnerTest} - it
 * provides the common infra, but doesn't have any test method.
 */
abstract class BaseDeviceOwnerTest extends BaseDevicePolicyTest {

    protected static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    protected static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";

    protected static final String ADMIN_RECEIVER_TEST_CLASS =
            DEVICE_OWNER_PKG + ".BasicAdminReceiver";
    protected static final String DEVICE_OWNER_COMPONENT = DEVICE_OWNER_PKG + "/"
            + ADMIN_RECEIVER_TEST_CLASS;

    private boolean mDeviceOwnerSet;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        installAppAsUser(DEVICE_OWNER_APK, mDeviceOwnerUserId);
        mDeviceOwnerSet = setDeviceOwner(DEVICE_OWNER_COMPONENT, mDeviceOwnerUserId,
                /*expectFailure*/ false);

        if (!mDeviceOwnerSet) {
            removeAdmin(DEVICE_OWNER_COMPONENT, mDeviceOwnerUserId);
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            fail("Failed to set device owner for user " + mDeviceOwnerUserId);
        }

        if (isHeadlessSystemUserMode()) {
            affiliateUsers(DEVICE_OWNER_PKG, mDeviceOwnerUserId, mPrimaryUserId);

            // TODO(b/176993670): INTERACT_ACROSS_USERS is needd by DevicePolicyManagerWrapper to
            // get the current user; the permission is available on mDeviceOwnerUserId because it
            // was installed with -g, but not on mPrimaryUserId as the app is intalled by code
            // (DPMS.manageUserUnchecked(), which don't grant it (as this is a privileged permission
            // that's not available to 3rd party apps). If we get rid of DevicePolicyManagerWrapper,
            // we won't need to grant it anymore.
            executeShellCommand("pm grant --user %d %s android.permission.INTERACT_ACROSS_USERS",
                    mPrimaryUserId, DEVICE_OWNER_PKG);
        }

        // Enable the notification listener
        executeShellCommand("cmd notification allow_listener com.android.cts."
                + "deviceowner/com.android.cts.deviceowner.NotificationListener");
    }

    @Override
    public void tearDown() throws Exception {
        if (mDeviceOwnerSet && !removeAdmin(DEVICE_OWNER_COMPONENT, mDeviceOwnerUserId)) {
            // Don't fail as it could hide the real failure from the test method
            CLog.e("Failed to remove device owner for user " + mDeviceOwnerUserId);
        }
        getDevice().uninstallPackage(DEVICE_OWNER_PKG);

        super.tearDown();
    }

    void affiliateUsers(String deviceAdminPkg, int userId1, int userId2) throws Exception {
        CLog.d("Affiliating users %d and %d on admin package %s", userId1, userId2, deviceAdminPkg);
        runDeviceTestsAsUser(
                deviceAdminPkg, ".AffiliationTest", "testSetAffiliationId1", userId1);
        runDeviceTestsAsUser(
                deviceAdminPkg, ".AffiliationTest", "testSetAffiliationId1", userId2);
    }

    protected final void executeDeviceOwnerTest(String testClassName) throws Exception {
        String testClass = DEVICE_OWNER_PKG + "." + testClassName;
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, testClass, mPrimaryUserId);
    }

    protected final void executeDeviceOwnerTestMethod(String className, String testName)
            throws Exception {
        executeDeviceOwnerPackageTestMethod(className, testName, mDeviceOwnerUserId);
    }

    protected final void executeDeviceTestMethod(String className, String testName)
            throws Exception {
        executeDeviceOwnerPackageTestMethod(className, testName, mPrimaryUserId);
    }

    private void executeDeviceOwnerPackageTestMethod(String className, String testName,
            int userId) throws Exception {
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, className, testName, userId);
    }
}
