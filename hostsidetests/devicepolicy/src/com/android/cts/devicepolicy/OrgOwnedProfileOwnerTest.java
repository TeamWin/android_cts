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

import static com.android.cts.devicepolicy.DeviceAndProfileOwnerTest.DEVICE_ADMIN_COMPONENT_FLATTENED;
import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;
import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.isStatsdEnabled;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Tests for organization-owned Profile Owner.
 */
public class OrgOwnedProfileOwnerTest extends BaseDevicePolicyTest {
    public static final String DEVICE_ADMIN_PKG = DeviceAndProfileOwnerTest.DEVICE_ADMIN_PKG;
    private static final String DEVICE_ADMIN_APK = DeviceAndProfileOwnerTest.DEVICE_ADMIN_APK;
    private static final String ADMIN_RECEIVER_TEST_CLASS =
            DeviceAndProfileOwnerTest.ADMIN_RECEIVER_TEST_CLASS;
    private static final String ACTION_WIPE_DATA =
            "com.android.cts.deviceandprofileowner.WIPE_DATA";

    private static final String DUMMY_IME_APK = "DummyIme.apk";
    private static final String DUMMY_IME_PKG = "com.android.cts.dummyime";
    private static final String DUMMY_IME_COMPONENT = DUMMY_IME_PKG + "/.DummyIme";

    private int mParentUserId = -1;
    protected int mUserId;
    private boolean mHasProfileToRemove = true;
    private boolean mHasSecondaryProfileToRemove = false;
    private static final String DISALLOW_CONFIG_LOCATION = "no_config_location";
    private static final String CALLED_FROM_PARENT = "calledFromParent";

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
        }
        if (mHasSecondaryProfileToRemove || !getUsersCreatedByTests().isEmpty()) {
            removeTestUsers();
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
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockScreenInfoTest", "testSetAndGetLockInfo",
                mUserId);

        removeOrgOwnedProfile();
        assertHasNoUser(mUserId);
        mHasProfileToRemove = false;

        try {
            installAppAsUser(DEVICE_ADMIN_APK, mParentUserId);
            setDeviceOwner(DEVICE_ADMIN_COMPONENT_FLATTENED, mParentUserId, /*expectFailure*/false);
            mHasSecondaryProfileToRemove = true;
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockScreenInfoTest", "testLockInfoIsNull",
                    mParentUserId);
        } finally {
            removeAdmin(DEVICE_ADMIN_COMPONENT_FLATTENED, mParentUserId);
            getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
        }
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
    public void testUserRestrictionSetOnParentLogged() throws Exception {
        if (!mHasFeature|| !isStatsdEnabled(getDevice())) {
            return;
        }
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingParentTest",
                    "testUserRestrictionLogged", mUserId);
                }, new DevicePolicyEventWrapper.Builder(EventId.ADD_USER_RESTRICTION_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setStrings(DISALLOW_CONFIG_LOCATION, CALLED_FROM_PARENT)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.REMOVE_USER_RESTRICTION_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setStrings(DISALLOW_CONFIG_LOCATION, CALLED_FROM_PARENT)
                        .build());
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
                "testAddUserRestrictionDisallowConfigDateTime_onParent", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testHasUserRestrictionDisallowConfigDateTime", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testHasUserRestrictionDisallowConfigDateTime", secondaryUserId);
        removeOrgOwnedProfile();
        assertHasNoUser(mUserId);
        mHasProfileToRemove = false;

        // Make sure the user restrictions are removed before continuing
        waitForBroadcastIdle();

        // User restrictions are not persist after organization-owned profile owner is removed
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testUserRestrictionDisallowConfigDateTimeIsNotPersisted", secondaryUserId);
    }

    @Test
    public void testUserRestrictionsSetOnParentAreEnforced() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int userId = createUser();
        removeUser(userId);

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testAddUserRestrictionDisallowAddUser_onParent", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testHasUserRestrictionDisallowAddUser", mUserId);

        // Make sure the user restriction is enforced
        failToCreateUser();

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testHasUserRestrictionDisallowAddUser", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testClearUserRestrictionDisallowAddUser", mUserId);
    }

    @Test
    public void testCameraDisabledOnParentLogged() throws Exception {
        if (!mHasFeature || !isStatsdEnabled(getDevice())) {
            return;
        }
        assertMetricsLogged(getDevice(), () -> {
                    runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingParentTest",
                            "testCameraDisabledLogged", mUserId);
                }, new DevicePolicyEventWrapper.Builder(EventId.SET_CAMERA_DISABLED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(true)
                        .setStrings(CALLED_FROM_PARENT)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.SET_CAMERA_DISABLED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(false)
                        .setStrings(CALLED_FROM_PARENT)
                        .build());
    }

    private void failToCreateUser() throws Exception {
        String command ="pm create-user " + "TestUser_" + System.currentTimeMillis();
        String commandOutput = getDevice().executeShellCommand(command);

        String[] tokens = commandOutput.split("\\s+");
        assertTrue(tokens.length > 0);
        assertEquals("Error:", tokens[0]);
    }

    protected int createUser() throws Exception {
        String command ="pm create-user " + "TestUser_" + System.currentTimeMillis();
        String commandOutput = getDevice().executeShellCommand(command);

        String[] tokens = commandOutput.split("\\s+");
        assertTrue(tokens.length > 0);
        assertEquals("Success:", tokens[0]);
        int userId = Integer.parseInt(tokens[tokens.length-1]);
        startUser(userId);
        return userId;
    }

    protected void removeUser(int userId) throws Exception  {
        if (listUsers().contains(userId) && userId != USER_SYSTEM) {
            String command = "am stop-user -w -f " + userId;
            getDevice().executeShellCommand(command);
        }
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
        boolean hasPersistentDataBlock = hasService("persistent_data_block");
        if (!mHasFeature || !hasPersistentDataBlock) {
            return;
        }

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".FactoryResetProtectionPolicyTest", mUserId);
    }

    @LargeTest
    @Test
    @Ignore("b/145932189")
    public void testSystemUpdatePolicy() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".systemupdate.SystemUpdatePolicyTest", mUserId);
    }

    @Test
    public void testInstallUpdate() throws Exception {
        if (!mHasFeature) {
            return;
        }

        pushUpdateFileToDevice("notZip.zi");
        pushUpdateFileToDevice("empty.zip");
        pushUpdateFileToDevice("wrongPayload.zip");
        pushUpdateFileToDevice("wrongHash.zip");
        pushUpdateFileToDevice("wrongSize.zip");
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".systemupdate.InstallUpdateTest", mUserId);
    }

    @Test
    public void testIsDeviceOrganizationOwnedWithManagedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceOwnershipTest",
                "testCallingIsOrganizationOwnedWithManagedProfileExpectingTrue",
                mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceOwnershipTest",
                "testCallingIsOrganizationOwnedWithManagedProfileExpectingTrue",
                mPrimaryUserId);
    }

    @Test
    public void testCommonCriteriaMode() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".CommonCriteriaModeTest", mUserId);
    }

    @Test
    public void testAdminConfiguredNetworks() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".AdminConfiguredNetworksTest", mUserId);
    }

    @Test
    public void testApplicationHidden() throws Exception {
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".ApplicationHiddenParentTest", mUserId);
    }

    private void removeOrgOwnedProfile() throws Exception {
        sendWipeProfileBroadcast(mUserId);
        waitUntilUserRemoved(mUserId);
    }

    private void sendWipeProfileBroadcast(int userId) throws Exception {
        final String cmd = "am broadcast --receiver-foreground --user " + userId
                + " -a " + ACTION_WIPE_DATA
                + " com.android.cts.deviceandprofileowner/.WipeDataReceiver";
        getDevice().executeShellCommand(cmd);
    }

    @Test
    public void testPersonalAppsSuspensionNormalApp() throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        // Initially the app should be launchable.
        assertCanStartPersonalApp(DEVICE_ADMIN_PKG, true);
        setPersonalAppsSuspended(true);
        // Now the app should be suspended and not launchable
        assertCanStartPersonalApp(DEVICE_ADMIN_PKG, false);
        setPersonalAppsSuspended(false);
        // Should be launchable again.
        assertCanStartPersonalApp(DEVICE_ADMIN_PKG, true);
    }

    private void setPersonalAppsSuspended(boolean suspended) throws DeviceNotAvailableException {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                suspended ? "testSuspendPersonalApps" : "testUnsuspendPersonalApps", mUserId);
    }

    @Test
    public void testPersonalAppsSuspensionIme() throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        setupIme(mPrimaryUserId, DUMMY_IME_APK, DUMMY_IME_COMPONENT);
        setPersonalAppsSuspended(true);
        // Active IME should not be suspended.
        assertCanStartPersonalApp(DUMMY_IME_PKG, true);
        setPersonalAppsSuspended(false);
    }

    private void setupIme(int userId, String imeApk, String imePackage) throws Exception {
        installAppAsUser(imeApk, userId);
        // Wait until IMS service is registered by the system.
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            final String availableImes = getDevice().executeShellCommand(
                    String.format("ime list --user %d -s -a", userId));
            if (availableImes.contains(imePackage)) {
                break;
            }
            assertTrue("Failed waiting for IME to become available", System.nanoTime() < deadline);
            Thread.sleep(100);
        }

        executeShellCommand("ime enable " + imePackage);
        executeShellCommand("ime set " + imePackage);
    }


    private void assertCanStartPersonalApp(String packageName, boolean canStart)
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(packageName, "com.android.cts.suspensionchecker.ActivityLaunchTest",
                canStart ? "testCanStartActivity" : "testCannotStartActivity", mParentUserId);
    }

    @Test
    public void testScreenCaptureDisabled() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".ScreenCaptureDisabledTest",
                "testSetScreenCaptureDisabledOnParent", mUserId);
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

    private boolean hasService(String service) {
        String command = "service check " + service;
        try {
            String commandOutput = getDevice().executeShellCommand(command);
            return !commandOutput.contains("not found");
        } catch (Exception e) {
            LogUtil.CLog.w("Exception running '" + command + "': " + e);
            return false;
        }
    }

    @Test
    public void testSetPersonalAppsSuspendedLogged() throws Exception {
        if (!mHasFeature|| !isStatsdEnabled(getDevice())) {
            return;
        }
        assertMetricsLogged(getDevice(), () -> {
                    runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingTest",
                            "testSetPersonalAppsSuspendedLogged", mUserId);
                }, new DevicePolicyEventWrapper.Builder(EventId.SET_PERSONAL_APPS_SUSPENDED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(true)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.SET_PERSONAL_APPS_SUSPENDED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(false)
                        .build());
    }

    @Test
    public void testSetManagedProfileMaximumTimeOffLogged() throws Exception {
        if (!mHasFeature|| !isStatsdEnabled(getDevice())) {
            return;
        }
        assertMetricsLogged(getDevice(), () -> {
                    runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingTest",
                            "testSetManagedProfileMaximumTimeOffLogged", mUserId);
                }, new DevicePolicyEventWrapper.Builder(
                        EventId.SET_MANAGED_PROFILE_MAXIMUM_TIME_OFF_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setTimePeriod(1234567)
                        .build(),
                new DevicePolicyEventWrapper.Builder(
                        EventId.SET_MANAGED_PROFILE_MAXIMUM_TIME_OFF_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setTimePeriod(0)
                        .build());
    }
}
