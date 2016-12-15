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

package com.android.cts.devicepolicy;

/**
 * Tests for having both device owner and profile owner. Device owner is setup for you in
 * {@link #setUp()} and it is always the {@link #COMP_DPC_PKG}. You are required to call
 * {@link #createManagedProfile(int)} yourself to create managed profile on each test case.
 */
public class DeviceOwnerPlusManagedProfileTest extends BaseDevicePolicyTest {
    private static final String DEVICE_OWNER_BIND_DEVICE_ADMIN_SERVICE_TEST =
            "com.android.cts.comp.DeviceOwnerBindDeviceAdminServiceTest";
    private static final String DEVICE_OWNER_PROVISIONING_TEST =
            "com.android.cts.comp.provisioning.ManagedProfileProvisioningTest";
    private static final String MANAGED_PROFILE_BIND_DEVICE_ADMIN_SERVICE_TEST =
            "com.android.cts.comp.ManagedProfileBindDeviceAdminServiceTest";
    private static final String AFFILIATION_TEST =
            "com.android.cts.comp.provisioning.AffiliationTest";

    private int mProfileUserId;

    private static final String COMP_DPC_PKG = "com.android.cts.comp";
    private static final String COMP_DPC_APK = "CtsCorpOwnedManagedProfile.apk";
    private static final String COMP_DPC_ADMIN =
            COMP_DPC_PKG + "/com.android.cts.comp.AdminReceiver";
    private static final String COMP_DPC_PKG2 = "com.android.cts.comp2";
    private static final String COMP_DPC_APK2 = "CtsCorpOwnedManagedProfile2.apk";
    private static final String COMP_DPC_ADMIN2 =
            COMP_DPC_PKG2 + "/com.android.cts.comp.AdminReceiver";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // We need managed user to be supported in order to create a profile of the user owner.
        mHasFeature = mHasFeature && hasDeviceFeature("android.software.managed_users");
        if (mHasFeature) {
            // Set device owner.
            installAppAsUser(COMP_DPC_APK, mPrimaryUserId);
            if (!setDeviceOwner(COMP_DPC_ADMIN, mPrimaryUserId, /*expectFailure*/ false)) {
                removeAdmin(COMP_DPC_ADMIN, mPrimaryUserId);
                fail("Failed to set device owner");
            }
        }
    }

    /**
     * Both device owner and profile are the same package ({@link #COMP_DPC_PKG}).
     */
    public void testBindDeviceAdminServiceAsUser_corpOwnedManagedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK, COMP_DPC_ADMIN);
        setSameAffiliationId();
        verifyBindDeviceAdminServiceAsUser();
    }

    /**
     * Same as {@link #testBindDeviceAdminServiceAsUser_corpOwnedManagedProfile} except
     * creating managed profile through ManagedProvisioning like normal flow
     */
    public void testBindDeviceAdminServiceAsUser_corpOwnedManagedProfileWithManagedProvisioning()
            throws Exception {
        if (!mHasFeature) {
            return;
        }
        provisionCorpOwnedManagedProfile();
        setSameAffiliationId();
        verifyBindDeviceAdminServiceAsUser();
    }

    /**
     * Both device owner and profile are the same package ({@link #COMP_DPC_PKG}) but are not
     * affiliated.
     */
    public void testBindDeviceAdminServiceAsUser_NotAffiliated() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK, COMP_DPC_ADMIN);
        setDifferentAffiliationId();

        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        installAppAsUser(COMP_DPC_APK2, mProfileUserId);

        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_OWNER_BIND_DEVICE_ADMIN_SERVICE_TEST,
                "testBindDeviceAdminServiceForUser_shouldFail",
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGED_PROFILE_BIND_DEVICE_ADMIN_SERVICE_TEST,
                "testBindDeviceAdminServiceForUser_shouldFail",
                mProfileUserId);
    }

    /**
     * Device owner is {@link #COMP_DPC_PKG} while profile owner is {@link #COMP_DPC_PKG2}.
     */
    public void testBindDeviceAdminServiceAsUser_byodPlusDeviceOwner() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK2, COMP_DPC_ADMIN2);
        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_OWNER_BIND_DEVICE_ADMIN_SERVICE_TEST,
                "testBindDeviceAdminServiceForUser_shouldFail",
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG2,
                MANAGED_PROFILE_BIND_DEVICE_ADMIN_SERVICE_TEST,
                "testBindDeviceAdminServiceForUser_shouldFail",
                mProfileUserId);
    }

    private void verifyBindDeviceAdminServiceAsUser() throws Exception {
        // Installing a non managing app (neither device owner nor profile owner).
        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        installAppAsUser(COMP_DPC_APK2, mProfileUserId);

        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_OWNER_BIND_DEVICE_ADMIN_SERVICE_TEST,
                "testBindDeviceAdminServiceForUser_corpOwnedManagedProfile",
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGED_PROFILE_BIND_DEVICE_ADMIN_SERVICE_TEST,
                "testBindDeviceAdminServiceForUser_corpOwnedManagedProfile",
                mProfileUserId);
    }

    private void setSameAffiliationId() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                mProfileUserId);
    }

    private void setDifferentAffiliationId() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId2",
                mProfileUserId);
    }

    protected void setupManagedProfile(String apkName, String adminReceiverClassName)
            throws Exception {
        mProfileUserId = createManagedProfile(mPrimaryUserId);
        installAppAsUser(apkName, mProfileUserId);
        setProfileOwnerOrFail(adminReceiverClassName, mProfileUserId);
        startUser(mProfileUserId);
    }


    protected void provisionCorpOwnedManagedProfile()
            throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_OWNER_PROVISIONING_TEST,
                "testProvisioningCorpOwnedManagedProfile",
                mPrimaryUserId);
        mProfileUserId = getFirstManagedProfileUserId();
    }
}
