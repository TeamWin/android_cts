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

import java.util.List;

/**
 * Tests for having both device owner and profile owner. Device owner is setup for you in
 * {@link #setUp()} and it is always the {@link #COMP_DPC_PKG}. You are required to call
 * {@link #createManagedProfile(int)} yourself to create managed profile on each test case.
 */
public class DeviceOwnerPlusManagedProfileTest extends BaseDevicePolicyTest {
    private static final String BIND_DEVICE_ADMIN_SERVICE_GOOD_SETUP_TEST =
            "com.android.cts.comp.BindDeviceAdminServiceGoodSetupTest";
    private static final String MANAGED_PROFILE_PROVISIONING_TEST =
            "com.android.cts.comp.provisioning.ManagedProfileProvisioningTest";
    private static final String BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST =
            "com.android.cts.comp.BindDeviceAdminServiceFailsTest";
    private static final String DEVICE_WIDE_LOGGING_TEST =
            "com.android.cts.comp.DeviceWideLoggingFeaturesTest";
    private static final String AFFILIATION_TEST =
            "com.android.cts.comp.provisioning.AffiliationTest";
    private static final String USER_RESTRICTION_TEST =
            "com.android.cts.comp.provisioning.UserRestrictionTest";
    private static final String MANAGEMENT_TEST =
            "com.android.cts.comp.ManagementTest";

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
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    MANAGEMENT_TEST,
                    "testIsDeviceOwner",
                    mPrimaryUserId);
        }
    }

    /**
     * Both device owner and profile are the same package ({@link #COMP_DPC_PKG}).
     */
    public void testBindDeviceAdminServiceAsUser_corpOwnedManagedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);
        setSameAffiliationId();
        assertOtherProfilesEqualsBindTargetUsers();
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
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGED_PROFILE_PROVISIONING_TEST,
                "testEnableProfile",
                mProfileUserId);
        assertOtherProfilesEqualsBindTargetUsers();
        verifyBindDeviceAdminServiceAsUser();
    }

    /**
     * Same as
     * {@link #testBindDeviceAdminServiceAsUser_corpOwnedManagedProfileWithManagedProvisioning}
     * except we don't enable the profile.
     */
    public void testBindDeviceAdminServiceAsUser_dontEnableProfile()
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
        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);
        setDifferentAffiliationId();

        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        installAppAsUser(COMP_DPC_APK2, mProfileUserId);

        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                mProfileUserId);
    }

    /**
     * Device owner is {@link #COMP_DPC_PKG} while profile owner is {@link #COMP_DPC_PKG2}.
     */
    public void testBindDeviceAdminServiceAsUser_byodPlusDeviceOwner() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);
        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG2,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                mProfileUserId);
    }

    public void testCannotRemoveProfileIfRestrictionSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);
        try {
            addDisallowRemoveManagedProfileRestriction();
            assertFalse(getDevice().removeUser(mProfileUserId));
        } finally {
            clearDisallowRemoveManagedProfileRestriction();
        }
        assertTrue(getDevice().removeUser(mProfileUserId));
    }

    public void testCanRemoveProfileEvenIfDisallowRemoveUserSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);
        addDisallowRemoveUserRestriction();
        // DISALLOW_REMOVE_USER only affects users, not profiles.
        assertTrue(getDevice().removeUser(mProfileUserId));
        assertUserGetsRemoved(mProfileUserId);
    }

    public void testDoCanRemoveProfileEvenIfUserRestrictionSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);
        addDisallowRemoveUserRestriction();
        addDisallowRemoveManagedProfileRestriction();

        // The DO should be allowed to remove the managed profile, even though disallow remove user
        // and disallow remove managed profile restrictions are set.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testCanRemoveManagedProfile",
                mPrimaryUserId);
        assertUserGetsRemoved(mProfileUserId);
    }


    public void testCannotAddProfileIfRestrictionSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // by default, disallow add managed profile users restriction is set.
        assertCannotCreateManagedProfile(mPrimaryUserId);
    }

    public void testNetworkAndSecurityLoggingAvailableIfAffiliated() throws Exception {
        if (!mHasFeature) {
            return;
        }

        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testEnablingNetworkAndSecurityLogging",
                mPrimaryUserId);
        try {
            // No affiliation ids have been set, the features shouldn't be available.
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    DEVICE_WIDE_LOGGING_TEST,
                    "testRetrievingLogsThrowsSecurityException",
                    mPrimaryUserId);

            setSameAffiliationId();
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    DEVICE_WIDE_LOGGING_TEST,
                    "testRetrievingLogsDoesNotThrowException",
                    mPrimaryUserId);

            setDifferentAffiliationId();
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    DEVICE_WIDE_LOGGING_TEST,
                    "testRetrievingLogsThrowsSecurityException",
                    mPrimaryUserId);
        } finally {
            runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testDisablingNetworkAndSecurityLogging",
                mPrimaryUserId);
        }
    }

    public void testRequestBugreportAvailableIfAffiliated() throws Exception {
        if (!mHasFeature) {
            return;
        }

        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);

        // No affiliation ids have been set, the feature shouldn't be available.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testRequestBugreportThrowsSecurityException",
                mPrimaryUserId);

        setSameAffiliationId();
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testRequestBugreportDoesNotThrowException",
                mPrimaryUserId);

        setDifferentAffiliationId();
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testRequestBugreportThrowsSecurityException",
                mPrimaryUserId);
    }

    private void verifyBindDeviceAdminServiceAsUser() throws Exception {
        // Installing a non managing app (neither device owner nor profile owner).
        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        installAppAsUser(COMP_DPC_APK2, mProfileUserId);

        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_GOOD_SETUP_TEST,
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_GOOD_SETUP_TEST,
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

    /**
     * Both device owner and profile are the same package ({@link #COMP_DPC_PKG}).
     */
    public void testIsProvisioningAllowed() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        // By default, disallow add managed profile is set, so provisioning a managed profile is
        // not allowed for DPCs other than the device owner.
        assertProvisionManagedProfileNotAllowed(COMP_DPC_PKG2);
        // But the device owner can still provision a managed profile because it owns the
        // restriction.
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG);

        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);

        clearDisallowAddManagedProfileRestriction();
        // We've created a managed profile, but it's still possible to delete it to create a new
        // one.
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG2);
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG);

        addDisallowRemoveManagedProfileRestriction();
        // Now we can't delete the managed profile any more to create a new one.
        assertProvisionManagedProfileNotAllowed(COMP_DPC_PKG2);
        // But if it is initiated by the device owner, it is still possible, because the device
        // owner itself has set the restriction
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG);
    }

    private void assertProvisionManagedProfileAllowed(String packageName) throws Exception {
        runDeviceTestsAsUser(
                packageName,
                MANAGEMENT_TEST,
                "testProvisionManagedProfileAllowed",
                mPrimaryUserId);
    }

    private void assertProvisionManagedProfileNotAllowed(String packageName) throws Exception {
        runDeviceTestsAsUser(
                packageName,
                MANAGEMENT_TEST,
                "testProvisionManagedProfileNotAllowed",
                mPrimaryUserId);
    }

    public void testWipeData() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);
        addDisallowRemoveManagedProfileRestriction();
        // The PO of the managed profile should be allowed to delete the managed profile, even
        // though the disallow remove profile restriction is set.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testWipeData",
                mProfileUserId);
        assertUserGetsRemoved(mProfileUserId);
    }

    public void testCannotBindToSecondaryUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testCreateSecondaryUser",
                mPrimaryUserId);
        List<Integer> newUsers = getUsersCreatedByTests();
        assertEquals(1, newUsers.size());
        int secondaryUserId = newUsers.get(0);
        getDevice().startUser(secondaryUserId);

        // Set the same affiliation ids on both users.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                secondaryUserId);

        // But check that we still can't bind to the other user.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testNoBindDeviceAdminTargetUsers",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testNoBindDeviceAdminTargetUsers",
                secondaryUserId);
    }

    protected void setupManagedProfile(String apkName, String packageName,
            String adminReceiverClassName) throws Exception {
        // Temporary disable the DISALLOW_ADD_MANAGED_PROFILE, so that we can create profile
        // using adb command.
        clearDisallowAddManagedProfileRestriction();
        try {
            mProfileUserId = createManagedProfile(mPrimaryUserId);
            installAppAsUser(apkName, mProfileUserId);
            setProfileOwnerOrFail(adminReceiverClassName, mProfileUserId);
            startUser(mProfileUserId);
            runDeviceTestsAsUser(
                    packageName,
                    MANAGEMENT_TEST,
                    "testIsManagedProfile",
                    mProfileUserId);
        } finally {
            // Adding back DISALLOW_ADD_MANAGED_PROFILE.
            addDisallowAddManagedProfileRestriction();
        }
    }

    protected void provisionCorpOwnedManagedProfile() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGED_PROFILE_PROVISIONING_TEST,
                "testProvisioningCorpOwnedManagedProfile",
                mPrimaryUserId);
        mProfileUserId = getFirstManagedProfileUserId();

    }

    /**
     * Clear {@link android.os.UserManager#DISALLOW_ADD_MANAGED_PROFILE}.
     */
    private void clearDisallowAddManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testClearDisallowAddManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Add {@link android.os.UserManager#DISALLOW_ADD_MANAGED_PROFILE}.
     */
    private void addDisallowAddManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testAddDisallowAddManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Clear {@link android.os.UserManager#DISALLOW_REMOVE_MANAGED_PROFILE}.
     */
    private void clearDisallowRemoveManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testClearDisallowRemoveManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Add {@link android.os.UserManager#DISALLOW_REMOVE_MANAGED_PROFILE}.
     */
    private void addDisallowRemoveManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testAddDisallowRemoveManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Add {@link android.os.UserManager#DISALLOW_REMOVE_USER}.
     */
    private void addDisallowRemoveUserRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testAddDisallowRemoveUserRestriction",
                mPrimaryUserId);
    }

    private void assertOtherProfilesEqualsBindTargetUsers() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testOtherProfilesEqualsBindTargetUsers",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testOtherProfilesEqualsBindTargetUsers",
                mProfileUserId);
    }
}
