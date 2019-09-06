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

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;

import java.util.Collections;

public class ManagedProfileCrossProfileTest extends BaseManagedProfileTest {
    private static final String NOTIFICATION_APK = "CtsNotificationSenderApp.apk";
    private static final String WIDGET_PROVIDER_APK = "CtsWidgetProviderApp.apk";
    private static final String WIDGET_PROVIDER_PKG = "com.android.cts.widgetprovider";
    private static final String PARAM_PROFILE_ID = "profile-id";

    @LargeTest
    public void testCrossProfileIntentFilters() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Set up activities: ManagedProfileActivity will only be enabled in the managed profile and
        // PrimaryUserActivity only in the primary one
        disableActivityForUser("ManagedProfileActivity", mParentUserId);
        disableActivityForUser("PrimaryUserActivity", mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                MANAGED_PROFILE_PKG + ".ManagedProfileTest", mProfileUserId);

        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".ManagedProfileTest",
                    "testAddCrossProfileIntentFilter_all", mProfileUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.ADD_CROSS_PROFILE_INTENT_FILTER_VALUE)
                .setAdminPackageName(MANAGED_PROFILE_PKG)
                .setInt(1)
                .setStrings("com.android.cts.managedprofile.ACTION_TEST_ALL_ACTIVITY")
                .build());

        // Set up filters from primary to managed profile
        String command = "am start -W --user " + mProfileUserId + " " + MANAGED_PROFILE_PKG
                + "/.PrimaryUserFilterSetterActivity";
        LogUtil.CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".PrimaryUserTest", mParentUserId);
        // TODO: Test with startActivity
    }

    @FlakyTest
    public void testCrossProfileContent() throws Exception {
        if (!mHasFeature) {
            return;
        }

        // Storage permission shouldn't be granted, we check if missing permissions are respected
        // in ContentTest#testSecurity.
        installAppAsUser(INTENT_SENDER_APK, false /* grantPermissions */, USER_ALL);
        installAppAsUser(INTENT_RECEIVER_APK, USER_ALL);

        // Test from parent to managed
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAddManagedCanAccessParentFilters", mProfileUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".ContentTest", mParentUserId);

        // Test from managed to parent
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAddParentCanAccessManagedFilters", mProfileUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".ContentTest", mProfileUserId);

    }

    @FlakyTest
    public void testCrossProfileNotificationListeners_EmptyWhitelist() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        // Profile owner in the profile sets an empty whitelist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetEmptyWhitelist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can only see personal notifications.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCannotReceiveProfileNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    public void testCrossProfileNotificationListeners_NullWhitelist() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        // Profile owner in the profile sets a null whitelist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetNullWhitelist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can see profile and personal notifications
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCanReceiveNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    public void testCrossProfileNotificationListeners_InWhitelist() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        // Profile owner in the profile adds listener to the whitelist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testAddListenerToWhitelist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can see profile and personal notifications
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCanReceiveNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    public void testCrossProfileNotificationListeners_setAndGet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetAndGetPermittedCrossProfileNotificationListeners", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    @FlakyTest
    public void testCrossProfileCopyPaste() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(INTENT_RECEIVER_APK, USER_ALL);
        installAppAsUser(INTENT_SENDER_APK, USER_ALL);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAllowCrossProfileCopyPaste", mProfileUserId);
        // Test that managed can see what is copied in the parent.
        testCrossProfileCopyPasteInternal(mProfileUserId, true);
        // Test that the parent can see what is copied in managed.
        testCrossProfileCopyPasteInternal(mParentUserId, true);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testDisallowCrossProfileCopyPaste", mProfileUserId);
        // Test that managed can still see what is copied in the parent.
        testCrossProfileCopyPasteInternal(mProfileUserId, true);
        // Test that the parent cannot see what is copied in managed.
        testCrossProfileCopyPasteInternal(mParentUserId, false);
    }

    private void testCrossProfileCopyPasteInternal(int userId, boolean shouldSucceed)
            throws DeviceNotAvailableException {
        final String direction = (userId == mParentUserId)
                ? "testAddManagedCanAccessParentFilters"
                : "testAddParentCanAccessManagedFilters";
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                direction, mProfileUserId);
        if (shouldSucceed) {
            runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                    "testCanReadAcrossProfiles", userId);
        } else {
            runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                    "testCannotReadAcrossProfiles", userId);
        }
    }

    @FlakyTest
    public void testCrossProfileWidgets() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            installAppAsUser(WIDGET_PROVIDER_APK, USER_ALL);
            getDevice().executeShellCommand("appwidget grantbind --user " + mParentUserId
                    + " --package " + WIDGET_PROVIDER_PKG);
            setIdleWhitelist(WIDGET_PROVIDER_PKG, true);
            startWidgetHostService();

            String commandOutput = changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                    "add-cross-profile-widget", mProfileUserId);
            assertTrue("Command was expected to succeed " + commandOutput,
                    commandOutput.contains("Status: ok"));

            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileWidgetTest",
                    "testCrossProfileWidgetProviderAdded", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHasCrossProfileWidgetProvider_true", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHostReceivesWidgetUpdates_true", mParentUserId);

            commandOutput = changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                    "remove-cross-profile-widget", mProfileUserId);
            assertTrue("Command was expected to succeed " + commandOutput,
                    commandOutput.contains("Status: ok"));

            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileWidgetTest",
                    "testCrossProfileWidgetProviderRemoved", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHasCrossProfileWidgetProvider_false", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHostReceivesWidgetUpdates_false", mParentUserId);
        } finally {
            changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG, "remove-cross-profile-widget",
                    mProfileUserId);
            getDevice().uninstallPackage(WIDGET_PROVIDER_PKG);
        }
    }

    @FlakyTest
    public void testCrossProfileWidgetsLogged() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            installAppAsUser(WIDGET_PROVIDER_APK, USER_ALL);
            getDevice().executeShellCommand("appwidget grantbind --user " + mParentUserId
                    + " --package " + WIDGET_PROVIDER_PKG);
            setIdleWhitelist(WIDGET_PROVIDER_PKG, true);
            startWidgetHostService();

            assertMetricsLogged(getDevice(), () -> {
                changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                        "add-cross-profile-widget", mProfileUserId);
                changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                        "remove-cross-profile-widget", mProfileUserId);
            }, new DevicePolicyEventWrapper
                        .Builder(EventId.ADD_CROSS_PROFILE_WIDGET_PROVIDER_VALUE)
                        .setAdminPackageName(MANAGED_PROFILE_PKG)
                        .build(),
                new DevicePolicyEventWrapper
                        .Builder(EventId.REMOVE_CROSS_PROFILE_WIDGET_PROVIDER_VALUE)
                        .setAdminPackageName(MANAGED_PROFILE_PKG)
                        .build());
        } finally {
            changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG, "remove-cross-profile-widget",
                    mProfileUserId);
            getDevice().uninstallPackage(WIDGET_PROVIDER_PKG);
        }
    }

    public void testCrossProfileCalendarPackage() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCrossProfileCalendarPackage", mProfileUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_CROSS_PROFILE_CALENDAR_PACKAGES_VALUE)
                    .setAdminPackageName(MANAGED_PROFILE_PKG)
                    .setStrings(MANAGED_PROFILE_PKG)
                    .build());
    }

    @FlakyTest
    public void testCrossProfileCalendar() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runCrossProfileCalendarTestsWhenWhitelistedAndEnabled();
        runCrossProfileCalendarTestsWhenAllPackagesWhitelisted();
        runCrossProfileCalendarTestsWhenDisabled();
        runCrossProfileCalendarTestsWhenNotWhitelisted();
    }

    @FlakyTest
    public void testDisallowSharingIntoPersonalFromProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Set up activities: PrimaryUserActivity will only be enabled in the personal user
        // This activity is used to find out the ground truth about the system's cross profile
        // intent forwarding activity.
        disableActivityForUser("PrimaryUserActivity", mProfileUserId);

        // Tests from the profile side
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                ".DisallowSharingIntoProfileTest", "testSharingFromProfile", mProfileUserId);
    }

    public void testDisallowSharingIntoProfileFromPersonal() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Set up activities: ManagedProfileActivity will only be enabled in the managed profile
        // This activity is used to find out the ground truth about the system's cross profile
        // intent forwarding activity.
        disableActivityForUser("ManagedProfileActivity", mParentUserId);

        // Tests from the personal side, which is mostly driven from host side.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DisallowSharingIntoProfileTest",
                "testSetUp", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DisallowSharingIntoProfileTest",
                "testDisableSharingIntoProfile", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DisallowSharingIntoProfileTest",
                "testSharingFromPersonalFails", mParentUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DisallowSharingIntoProfileTest",
                "testEnableSharingIntoProfile", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DisallowSharingIntoProfileTest",
                "testSharingFromPersonalSucceeds", mParentUserId);
    }

    private void runCrossProfileCalendarTestsWhenWhitelistedAndEnabled() throws Exception {
        try {
            // Setup. Add the test package into cross-profile calendar whitelist, enable
            // cross-profile calendar in settings, and insert test data into calendar provider.
            // All setups should be done in managed profile.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testWhitelistManagedProfilePackage", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testAddTestCalendarDataForWorkProfile", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testEnableCrossProfileCalendarSettings", mProfileUserId);

            // Testing.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkCalendarsWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkEventsWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkInstancesWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkInstancesByDayWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_canAccessWorkInstancesSearch1", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_canAccessWorkInstancesSearch2", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_canAccessWorkInstancesSearchByDay", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getExceptionWhenQueryNonWhitelistedColumns", mParentUserId);
        } finally {
            // Cleanup.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCleanupWhitelist", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCleanupTestCalendarDataForWorkProfile", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testDisableCrossProfileCalendarSettings", mProfileUserId);
        }
    }

    private void runCrossProfileCalendarTestsWhenAllPackagesWhitelisted() throws Exception {
        try {
            // Setup. Allow all packages to access cross-profile calendar APIs by setting
            // the whitelist to null, enable cross-profile calendar in settings,
            // and insert test data into calendar provider.
            // All setups should be done in managed profile.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testWhitelistAllPackages", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testAddTestCalendarDataForWorkProfile", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testEnableCrossProfileCalendarSettings", mProfileUserId);

            // Testing.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkCalendarsWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkEventsWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkInstancesWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getCorrectWorkInstancesByDayWhenEnabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_canAccessWorkInstancesSearch1", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_canAccessWorkInstancesSearch2", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_canAccessWorkInstancesSearchByDay", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_getExceptionWhenQueryNonWhitelistedColumns", mParentUserId);
        } finally {
            // Cleanup.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCleanupWhitelist", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCleanupTestCalendarDataForWorkProfile", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testDisableCrossProfileCalendarSettings", mProfileUserId);
        }
    }

    private void runCrossProfileCalendarTestsWhenDisabled() throws Exception {
        try {
            // Setup. Add the test package into cross-profile calendar whitelist,
            // and insert test data into calendar provider. But disable cross-profile calendar
            // in settings. Thus cross-profile calendar Uris should not be accessible.
            // All setups should be done in managed profile.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testWhitelistManagedProfilePackage", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testAddTestCalendarDataForWorkProfile", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testDisableCrossProfileCalendarSettings", mProfileUserId);

            // Testing.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_cannotAccessWorkCalendarsWhenDisabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_cannotAccessWorkEventsWhenDisabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_cannotAccessWorkInstancesWhenDisabled", mParentUserId);
        } finally {
            // Cleanup.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCleanupWhitelist", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCleanupTestCalendarDataForWorkProfile", mProfileUserId);
        }
    }

    private void runCrossProfileCalendarTestsWhenNotWhitelisted() throws Exception {
        try {
            // Setup. Enable cross-profile calendar in settings and insert test data into calendar
            // provider. But make sure that the test package is not whitelisted for cross-profile
            // calendar. Thus cross-profile calendar Uris should not be accessible.
            // All setups should be done in managed profile.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testAddTestCalendarDataForWorkProfile", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testEnableCrossProfileCalendarSettings", mProfileUserId);

            // Testing.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_cannotAccessWorkCalendarsWhenDisabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_cannotAccessWorkEventsWhenDisabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testPrimaryProfile_cannotAccessWorkInstancesWhenDisabled", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testViewEventCrossProfile_intentFailedWhenNotWhitelisted", mParentUserId);
        } finally {
            // Cleanup.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCleanupTestCalendarDataForWorkProfile", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testDisableCrossProfileCalendarSettings", mProfileUserId);
        }
    }

    private void setIdleWhitelist(String packageName, boolean enabled)
            throws DeviceNotAvailableException {
        String command = "cmd deviceidle whitelist " + (enabled ? "+" : "-") + packageName;
        LogUtil.CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    private String changeCrossProfileWidgetForUser(String packageName, String command, int userId)
            throws DeviceNotAvailableException {
        String adbCommand = "am start -W --user " + userId
                + " -c android.intent.category.DEFAULT "
                + " --es extra-command " + command
                + " --es extra-package-name " + packageName
                + " " + MANAGED_PROFILE_PKG + "/.SetPolicyActivity";
        String commandOutput = getDevice().executeShellCommand(adbCommand);
        LogUtil.CLog.d("Output for command " + adbCommand + ": " + commandOutput);
        return commandOutput;
    }

    private void startWidgetHostService() throws Exception {
        String command = "am startservice --user " + mParentUserId
                + " -a " + WIDGET_PROVIDER_PKG + ".REGISTER_CALLBACK "
                + "--ei user-extra " + getUserSerialNumber(mProfileUserId)
                + " " + WIDGET_PROVIDER_PKG + "/.SimpleAppWidgetHostService";
        LogUtil.CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }
}
