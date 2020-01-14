/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.FlakyTest;

import com.android.tradefed.device.DeviceNotAvailableException;

import org.junit.Test;

/**
 * Tests for organization-owned Profile Owner.
 */
public class OrgOwnedProfileOwnerTest extends BaseDevicePolicyTest {
    public static final String DEVICE_ADMIN_PKG = DeviceAndProfileOwnerTest.DEVICE_ADMIN_PKG;
    private static final String DEVICE_ADMIN_APK = DeviceAndProfileOwnerTest.DEVICE_ADMIN_APK;
    private static final String ADMIN_RECEIVER_TEST_CLASS =
            DeviceAndProfileOwnerTest.ADMIN_RECEIVER_TEST_CLASS;

    private static final String RELINQUISH_DEVICE_TEST_CLASS =
            DEVICE_ADMIN_PKG + ".RelinquishDeviceTest";

    private int mParentUserId = -1;
    protected int mUserId;
    private boolean mHasProfileToRemove = true;
    private boolean mHasSecondaryProfileToRemove = false;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // We need managed users to be supported in order to create a profile of the user owner.
        mHasFeature &= hasDeviceFeature("android.software.managed_users");

        if (mHasFeature) {
            removeTestUsers();
            mParentUserId = mPrimaryUserId;
            createManagedProfile();
        }
    }

    private void createManagedProfile() throws Exception {
        mUserId = createManagedProfile(mParentUserId);
        switchUser(mParentUserId);
        startUserAndWait(mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mUserId);
        setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        startUserAndWait(mUserId);
        restrictManagedProfileRemoval();
        mHasProfileToRemove = true;
    }

    @Override
    public void tearDown() throws Exception {
        if (mHasFeature && mHasProfileToRemove) {
            removeOrgOwnedProfile();
            removeUser(mUserId);
        }
        if (mHasSecondaryProfileToRemove) {
            removeTestUsers();
            getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
        }
        super.tearDown();
    }

    private void restrictManagedProfileRemoval() throws DeviceNotAvailableException {
            getDevice().executeShellCommand(
                    String.format("dpm mark-profile-owner-on-organization-owned-device --user %d '%s'",
                            mUserId, DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS));
    }

    @Test
    public void testCannotRemoveManagedProfile() throws DeviceNotAvailableException {
        if (!mHasFeature) {
            return;
        }

        assertThat(getDevice().removeUser(mUserId)).isFalse();
    }

    @Test
    public void testCanRelinquishControlOverDevice() throws Exception {
        if (!mHasFeature) {
            return;
        }

        removeOrgOwnedProfile();
        assertHasNoUser(mUserId);

        mHasProfileToRemove = false;
    }

    @Test
    public void testLockScreenInfo() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockScreenInfoTest", mUserId);
    }

    @Test
    public void testProfileOwnerCanGetDeviceIdentifiers() throws Exception {
        // The Profile Owner should have access to all device identifiers.
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceIdentifiersTest",
                "testProfileOwnerCanGetDeviceIdentifiersWithPermission", mUserId);
    }

    @Test
    public void testProfileOwnerCannotGetDeviceIdentifiersWithoutPermission() throws Exception {
        if (!mHasFeature) {
            return;
        }

        // Revoke the READ_PHONE_STATE permission for the profile user ID to ensure the profile
        // owner cannot access device identifiers without consent.
        getDevice().executeShellCommand(
                "pm revoke --user " + mUserId + " " + DEVICE_ADMIN_PKG
                        + " android.permission.READ_PHONE_STATE");
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceIdentifiersTest",
                "testProfileOwnerCannotGetDeviceIdentifiersWithoutPermission", mUserId);
    }

    @Test
    public void testDevicePolicyManagerParentSupport() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".OrgOwnedProfileOwnerParentTest", mUserId);
    }

    @Test
    public void testUserRestrictionsSetOnParentAreNotPersisted() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int secondaryUserId = createUser();
        setPoAsUser(secondaryUserId);
        mHasSecondaryProfileToRemove = true;

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testAddUserRestriction_onParent", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testHasUserRestriction", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testHasUserRestriction", secondaryUserId);
        removeOrgOwnedProfile();
        assertHasNoUser(mUserId);
        mHasProfileToRemove = false;

        // Make sure the user restrictions are removed before continuing
        waitForBroadcastIdle();

        // User restrictions are not persist after organization-owned profile owner is removed
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testUserRestrictionAreNotPersisted", secondaryUserId);
    }

    @Test
    public void testSetTime() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".TimeManagementTest", "testSetTime", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".TimeManagementTest",
                "testSetTime_failWhenAutoTimeEnabled", mUserId);
    }

    @Test
    public void testSetTimeZone() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".TimeManagementTest", "testSetTimeZone", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".TimeManagementTest",
                "testSetTimeZone_failIfAutoTimeZoneEnabled", mUserId);
    }

    @FlakyTest(bugId = 137088260)
    @Test
    public void testWifi() throws Exception {
        if (!mHasFeature || !hasDeviceFeature("android.hardware.wifi")) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".WifiTest", "testGetWifiMacAddress", mUserId);
    }

    @Test
    public void testFactoryResetProtectionPolicy() throws Exception {
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(
                DEVICE_ADMIN_PKG, ".FactoryResetProtectionPolicyTest", mUserId);
    }

    private void removeOrgOwnedProfile() throws DeviceNotAvailableException {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, RELINQUISH_DEVICE_TEST_CLASS, mUserId);
    }

    private void assertHasNoUser(int userId) throws DeviceNotAvailableException {
        int numWaits = 0;
        final int MAX_NUM_WAITS = 15;
        while (listUsers().contains(userId) && (numWaits < MAX_NUM_WAITS)) {
            try {
                Thread.sleep(1000);
                numWaits += 1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertThat(listUsers()).doesNotContain(userId);
    }

    private void setPoAsUser(int userId) throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, true, true, userId);
        assertTrue("Failed to set profile owner",
                setProfileOwner(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                        userId, /* expectFailure */ false));
    }

}
