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
package com.android.cts.devicepolicy;

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import android.platform.test.annotations.LargeTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.AssertionFailedError;

/**
 * Set of tests for Managed Profile use cases.
 */
public class ManagedProfileTest extends BaseManagedProfileTest {

    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    private static final String FEATURE_BLUETOOTH = "android.hardware.bluetooth";
    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_WIFI = "android.hardware.wifi";
    private static final String FEATURE_CONNECTION_SERVICE = "android.software.connectionservice";
    private static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    private static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";
    private static final String DEVICE_OWNER_ADMIN =
            DEVICE_OWNER_PKG + ".BaseDeviceOwnerTest$BasicAdminReceiver";

    public void testManagedProfilesSupportedWithLockScreenOnly() throws Exception {
        if (mHasFeature) {
            // Managed profiles should be only supported if the device supports the secure lock
            // screen feature.
            assertTrue(mHasSecureLockScreen);
        }
    }

    public void testManagedProfileSetup() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".ManagedProfileSetupTest",
                mProfileUserId);
    }

    public void testMaxOneManagedProfile() throws Exception {
        int newUserId = -1;
        try {
            newUserId = createManagedProfile(mParentUserId);
        } catch (AssertionFailedError expected) {
        }
        if (newUserId > 0) {
            removeUser(newUserId);
            fail(mHasFeature ? "Device must allow creating only one managed profile"
                    : "Device must not allow creating a managed profile");
        }
    }

    /**
     * Verify that removing a managed profile will remove all networks owned by that profile.
     */
    public void testProfileWifiCleanup() throws Exception {
        if (!mHasFeature || !hasDeviceFeature(FEATURE_WIFI)) {
            return;
        }
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testRemoveWifiNetworkIfExists", mParentUserId);

        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testAddWifiNetwork", mProfileUserId);

        // Now delete the user - should undo the effect of testAddWifiNetwork.
        removeUser(mProfileUserId);
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testWifiNetworkDoesNotExist",
                mParentUserId);
    }

    public void testWifiMacAddress() throws Exception {
        if (!mHasFeature || !hasDeviceFeature(FEATURE_WIFI)) {
            return;
        }
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testCannotGetWifiMacAddress", mProfileUserId);
    }

    @LargeTest
    public void testAppLinks_verificationStatus() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Disable all pre-existing browsers in the managed profile so they don't interfere with
        // intents resolution.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testDisableAllBrowsers", mProfileUserId);
        installAppAsUser(INTENT_RECEIVER_APK, USER_ALL);
        installAppAsUser(INTENT_SENDER_APK, USER_ALL);

        changeVerificationStatus(mParentUserId, INTENT_RECEIVER_PKG, "ask");
        changeVerificationStatus(mProfileUserId, INTENT_RECEIVER_PKG, "ask");
        // We should have two receivers: IntentReceiverActivity and BrowserActivity in the
        // managed profile
        assertAppLinkResult("testTwoReceivers");

        changeUserRestrictionOrFail("allow_parent_profile_app_linking", true, mProfileUserId);
        // Now we should also have one receiver in the primary user, so three receivers in total.
        assertAppLinkResult("testThreeReceivers");

        changeVerificationStatus(mParentUserId, INTENT_RECEIVER_PKG, "never");
        // The primary user one has been set to never: we should only have the managed profile ones.
        assertAppLinkResult("testTwoReceivers");

        changeVerificationStatus(mProfileUserId, INTENT_RECEIVER_PKG, "never");
        // Now there's only the browser in the managed profile left
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        changeVerificationStatus(mProfileUserId, INTENT_RECEIVER_PKG, "always");
        changeVerificationStatus(mParentUserId, INTENT_RECEIVER_PKG, "always");
        // We have one always in the primary user and one always in the managed profile: the managed
        // profile one should have precedence.
        assertAppLinkResult("testReceivedByAppLinkActivityInManaged");
    }

    @LargeTest
    public void testAppLinks_enabledStatus() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Disable all pre-existing browsers in the managed profile so they don't interfere with
        // intents resolution.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testDisableAllBrowsers", mProfileUserId);
        installAppAsUser(INTENT_RECEIVER_APK, USER_ALL);
        installAppAsUser(INTENT_SENDER_APK, USER_ALL);

        final String APP_HANDLER_COMPONENT = "com.android.cts.intent.receiver/.AppLinkActivity";

        // allow_parent_profile_app_linking is not set, try different enabled state combinations.
        // We should not have app link handler in parent user no matter whether it is enabled.

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        // We now set allow_parent_profile_app_linking, and hence we should have the app handler
        // in parent user if it is enabled.
        changeUserRestrictionOrFail("allow_parent_profile_app_linking", true, mProfileUserId);

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testThreeReceivers");
    }

    public void testSettingsIntents() throws Exception {
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".SettingsIntentsTest",
                mProfileUserId);
    }

    /** Tests for the API helper class. */
    public void testCurrentApiHelper() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CurrentApiHelperTest",
                mProfileUserId);
    }

    /** Test: unsupported public APIs are disabled on a parent profile. */
    public void testParentProfileApiDisabled() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ParentProfileTest",
                "testParentProfileApiDisabled", mProfileUserId);
    }

    // TODO: This test is not specific to managed profiles, but applies to multi-user in general.
    // Move it to a MultiUserTest class when there is one. Should probably move
    // SetPolicyActivity to a more generic apk too as it might be useful for different kinds
    // of tests (same applies to ComponentDisablingActivity).
    public void testNoDebuggingFeaturesRestriction() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // If adb is running as root, then the adb uid is 0 instead of SHELL_UID,
        // so the DISALLOW_DEBUGGING_FEATURES restriction does not work and this test
        // fails.
        if (getDevice().isAdbRoot()) {
            CLog.logAndDisplay(LogLevel.WARN,
                    "Cannot test testNoDebuggingFeaturesRestriction() in eng/userdebug build");
            return;
        }
        String restriction = "no_debugging_features";  // UserManager.DISALLOW_DEBUGGING_FEATURES

        changeUserRestrictionOrFail(restriction, true, mProfileUserId);


        // This should now fail, as the shell is not available to start activities under a different
        // user once the restriction is in place.
        String addRestrictionCommandOutput =
                changeUserRestriction(restriction, true, mProfileUserId);
        assertTrue(
                "Expected SecurityException when starting the activity "
                        + addRestrictionCommandOutput,
                addRestrictionCommandOutput.contains("SecurityException"));
    }

    // Test the bluetooth API from a managed profile.
    public void testBluetooth() throws Exception {
        boolean hasBluetooth = hasDeviceFeature(FEATURE_BLUETOOTH);
        if (!mHasFeature || !hasBluetooth) {
            return;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testEnableDisable", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testGetAddress", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testListenUsingRfcommWithServiceRecord", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testGetRemoteDevice", mProfileUserId);
    }

    public void testCameraPolicy() throws Exception {
        boolean hasCamera = hasDeviceFeature(FEATURE_CAMERA);
        if (!mHasFeature || !hasCamera) {
            return;
        }
        try {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testDisableCameraInManagedProfile",
                    mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testEnableCameraInManagedProfile",
                    mProfileUserId);
        } finally {
            final String adminHelperClass = ".PrimaryUserAdminHelper";
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    adminHelperClass, "testClearDeviceAdmin", mParentUserId);
        }
    }

    public void testOrganizationInfo() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                "testDefaultOrganizationColor", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                "testDefaultOrganizationNameIsNull", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                mProfileUserId);
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".OrganizationInfoTest",
                    "testSetOrganizationColor", mProfileUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_ORGANIZATION_COLOR_VALUE)
                .setAdminPackageName(MANAGED_PROFILE_PKG)
                .build());
    }

    public void testDevicePolicyManagerParentSupport() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".DevicePolicyManagerParentSupportTest", mProfileUserId);
    }

    public void testBluetoothContactSharingDisabled() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testSetBluetoothContactSharingDisabled_setterAndGetter", mProfileUserId);
        }, new DevicePolicyEventWrapper
                    .Builder(EventId.SET_BLUETOOTH_CONTACT_SHARING_DISABLED_VALUE)
                    .setAdminPackageName(MANAGED_PROFILE_PKG)
                    .setBoolean(false)
                    .build(),
            new DevicePolicyEventWrapper
                    .Builder(EventId.SET_BLUETOOTH_CONTACT_SHARING_DISABLED_VALUE)
                    .setAdminPackageName(MANAGED_PROFILE_PKG)
                    .setBoolean(true)
                    .build());
    }

    public void testCannotSetProfileOwnerAgain() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // verify that we can't set the same admin receiver as profile owner again
        assertFalse(setProfileOwner(
                MANAGED_PROFILE_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mProfileUserId,
                /*expectFailure*/ true));

        // verify that we can't set a different admin receiver as profile owner
        installAppAsUser(DEVICE_OWNER_APK, mProfileUserId);
        assertFalse(setProfileOwner(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mProfileUserId,
                /*expectFailure*/ true));
    }

    @LargeTest
    public void testCannotSetDeviceOwnerWhenProfilePresent() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            installAppAsUser(DEVICE_OWNER_APK, mParentUserId);
            assertFalse(setDeviceOwner(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mParentUserId,
                    /*expectFailure*/ true));
        } finally {
            // make sure we clean up in case we succeeded in setting the device owner
            removeAdmin(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mParentUserId);
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
        }
    }

    public void testNfcRestriction() throws Exception {
        if (!mHasFeature || !mHasNfcFeature) {
            return;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mParentUserId);

        changeUserRestrictionOrFail("no_outgoing_beam" /* UserManager.DISALLOW_OUTGOING_BEAM */,
                true, mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareDisabled", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mParentUserId);
    }

    public void testIsProvisioningAllowed() throws DeviceNotAvailableException {
        if (!mHasFeature) {
            return;
        }
        // In Managed profile user when managed profile is provisioned
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PreManagedProfileTest",
                "testIsProvisioningAllowedFalse", mProfileUserId);

        // In parent user when managed profile is provisioned
        // It's allowed to provision again by removing the previous profile
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PreManagedProfileTest",
                "testIsProvisioningAllowedTrue", mParentUserId);
    }

    public void testPhoneAccountVisibility() throws Exception {
        if (!mHasFeature) {
            return;
        }
        if (!shouldRunTelecomTest()) {
            return;
        }
        try {
            // Register phone account in parent user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testRegisterPhoneAccount",
                    mParentUserId);
            // The phone account should not be visible in managed user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testPhoneAccountNotRegistered",
                    mProfileUserId);
        } finally {
            // Unregister the phone account.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testUnregisterPhoneAccount",
                    mParentUserId);
        }

        try {
            // Register phone account in profile user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testRegisterPhoneAccount",
                    mProfileUserId);
            // The phone account should not be visible in parent user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testPhoneAccountNotRegistered",
                    mParentUserId);
        } finally {
            // Unregister the phone account.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testUnregisterPhoneAccount",
                    mProfileUserId);
        }
    }

    @LargeTest
    public void testManagedCall() throws Exception {
        if (!mHasFeature) {
            return;
        }
        if (!shouldRunTelecomTest()) {
            return;
        }
        getDevice().executeShellCommand("telecom set-default-dialer " + MANAGED_PROFILE_PKG);

        // Place a outgoing call through work phone account using TelecomManager and verify the
        // call is inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testOutgoingCallUsingTelecomManager",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Place a outgoing call through work phone account using ACTION_CALL and verify the call
        // is inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testOutgoingCallUsingActionCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Add an incoming call with parent user's phone account and verify the call is inserted
        // properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testIncomingCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Add an incoming missed call with parent user's phone account and verify the call is
        // inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testIncomingMissedCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);
    }

    public void testTrustAgentInfo() throws Exception {
        if (!mHasFeature || !mHasSecureLockScreen) {
            return;
        }
        // Set and get trust agent config using child dpm instance.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetAndGetTrustAgentConfiguration_child",
                mProfileUserId);
        // Set and get trust agent config using parent dpm instance.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetAndGetTrustAgentConfiguration_parent",
                mProfileUserId);
        // Unified case
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndUnified",
                mProfileUserId);
        // Non-unified case
        try {
            changeUserCredential("1234", null, mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                    "testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndNonUnified",
                    mProfileUserId);
        } finally {
            changeUserCredential(null, "1234", mProfileUserId);
        }
    }

    public void testSanityCheck() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Install SimpleApp in work profile only and check activity in it can be launched.
        installAppAsUser(SIMPLE_APP_APK, mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".SanityTest", mProfileUserId);
    }

    public void testBluetoothSharingRestriction() throws Exception {
        final boolean hasBluetooth = hasDeviceFeature(FEATURE_BLUETOOTH);
        if (!mHasFeature || !hasBluetooth) {
            return;
        }

        // Primary profile should be able to use bluetooth sharing.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothSharingRestrictionPrimaryProfileTest",
                "testBluetoothSharingAvailable", mPrimaryUserId);

        // Managed profile owner should be able to control it via DISALLOW_BLUETOOTH_SHARING.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothSharingRestrictionTest",
                "testOppDisabledWhenRestrictionSet", mProfileUserId);
    }

    public void testProfileOwnerCanGetDeviceIdentifiers() throws Exception {
        // The Profile Owner should have access to all device identifiers.
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DeviceIdentifiersTest",
                "testProfileOwnerCanGetDeviceIdentifiersWithPermission", mProfileUserId);
    }

    public void testProfileOwnerCannotGetDeviceIdentifiersWithoutPermission() throws Exception {
        if (!mHasFeature) {
            return;
        }

        // Revoke the READ_PHONE_STATE permission for the profile user ID to ensure the profile
        // owner cannot access device identifiers without consent.
        getDevice().executeShellCommand(
                "pm revoke --user " + mProfileUserId + " " + MANAGED_PROFILE_PKG
                        + " android.permission.READ_PHONE_STATE");
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DeviceIdentifiersTest",
                "testProfileOwnerCannotGetDeviceIdentifiersWithoutPermission", mProfileUserId);
    }

    public void testSetProfileNameLogged() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".DevicePolicyLoggingTest",
                    "testSetProfileNameLogged", mProfileUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_PROFILE_NAME_VALUE)
                .setAdminPackageName(MANAGED_PROFILE_PKG)
                .build());
    }

    private void changeUserRestrictionOrFail(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        changeUserRestrictionOrFail(key, value, userId, MANAGED_PROFILE_PKG);
    }

    private String changeUserRestriction(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        return changeUserRestriction(key, value, userId, MANAGED_PROFILE_PKG);
    }

    // status should be one of never, undefined, ask, always
    private void changeVerificationStatus(int userId, String packageName, String status)
            throws DeviceNotAvailableException {
        String command = "pm set-app-link --user " + userId + " " + packageName + " " + status;
        CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    private void assertAppLinkResult(String methodName) throws DeviceNotAvailableException {
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".AppLinkTest", methodName,
                mProfileUserId);
    }

    private boolean shouldRunTelecomTest() throws DeviceNotAvailableException {
        return hasDeviceFeature(FEATURE_TELEPHONY) && hasDeviceFeature(FEATURE_CONNECTION_SERVICE);
    }
}
