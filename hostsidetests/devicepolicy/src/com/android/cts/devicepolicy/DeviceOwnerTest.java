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

import java.util.ArrayList;

/**
 * Set of tests for Device Owner use cases.
 */
public class DeviceOwnerTest extends BaseDevicePolicyTest {

    private static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    private static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";
    private static final String MANAGED_PROFILE_ADMIN =
            MANAGED_PROFILE_PKG + ".BaseManagedProfileTest$BasicAdminReceiver";

    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    private static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String WIFI_CONFIG_CREATOR_PKG =
            "com.android.cts.deviceowner.wificonfigcreator";
    private static final String WIFI_CONFIG_CREATOR_APK = "CtsWifiConfigCreator.apk";

    private static final String ADMIN_RECEIVER_TEST_CLASS =
            DEVICE_OWNER_PKG + ".BaseDeviceOwnerTest$BasicAdminReceiver";
    private static final String CLEAR_DEVICE_OWNER_TEST_CLASS =
            DEVICE_OWNER_PKG + ".ClearDeviceOwnerTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mHasFeature) {
            installApp(DEVICE_OWNER_APK);
            assertTrue("Failed to set device owner",
                    setDeviceOwner(DEVICE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove device owner.",
                    runDeviceTests(DEVICE_OWNER_PKG, CLEAR_DEVICE_OWNER_TEST_CLASS));
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            switchUser(0);
            removeTestUsers();
        }

        super.tearDown();
    }

    public void testCaCertManagement() throws Exception {
        executeDeviceOwnerTest("CaCertManagementTest");
    }

    public void testDeviceOwnerSetup() throws Exception {
        executeDeviceOwnerTest("DeviceOwnerSetupTest");
    }

    public void testKeyManagement() throws Exception {
        executeDeviceOwnerTest("KeyManagementTest");
    }

    public void testLockScreenInfo() throws Exception {
        executeDeviceOwnerTest("LockScreenInfoTest");
    }

    public void testRemoteBugreportWithTwoUsers() throws Exception {
        if (!mHasFeature || getMaxNumberOfUsersSupported() < 2) {
            return;
        }
        int userId = -1;
        try {
            userId = createUser();
            executeDeviceTestMethod(".RemoteBugreportTest",
                    "testRequestBugreportNotStartedIfMoreThanOneUserPresent");
        } finally {
            removeUser(userId);
        }
    }

    public void testRemoteBugreportWithSingleUser() throws Exception {
        executeDeviceTestMethod(".RemoteBugreportTest", "testSubsequentRemoteBugreportThrottled");
    }

    /** Tries to toggle the force-ephemeral-users on and checks it was really set. */
    public void testSetForceEphemeralUsers() throws Exception {
        if (!mHasFeature || getDevice().getApiLevel() < 24 /* Build.VERSION_CODES.N */
                || !canCreateAdditionalUsers(1)) {
            return;
        }
        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceOwnerTest("ForceEphemeralUsersTest");
    }

    /**
     * All users (except of the system user) must be removed after toggling the
     * force-ephemeral-users policy to true.
     *
     * <p>If the current user is the system user, the other users are removed straight away.
     */
    public void testRemoveUsersOnSetForceEphemeralUsers() throws Exception {
        if (!mHasFeature || getDevice().getApiLevel() < 24 /* Build.VERSION_CODES.N */
                || !canCreateAdditionalUsers(1)) {
            return;
        }

        // Create a user.
        int userId = createUser();
        assertTrue("User must have been created", listUsers().contains(userId));

        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceOwnerTest("ForceEphemeralUsersTest");

        // Users have to be removed when force-ephemeral-users is toggled on.
        assertFalse("User must have been removed", listUsers().contains(userId));
    }

    /**
     * All users (except of the system user) must be removed after toggling the
     * force-ephemeral-users policy to true.
     *
     * <p>If the current user is not the system user, switching to the system user should happen
     * before all other users are removed.
     */
    public void testRemoveUsersOnSetForceEphemeralUsersWithUserSwitch() throws Exception {
        if (!mHasFeature || getDevice().getApiLevel() < 24 /* Build.VERSION_CODES.N */
                || !canCreateAdditionalUsers(1)) {
            return;
        }

        // Create a user.
        int userId = createUser();
        assertTrue("User must have been created", listUsers().contains(userId));

        // Switch to the new (non-system) user.
        switchUser(userId);

        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceOwnerTestAsUser("ForceEphemeralUsersTest", 0);

        // Make sure the user has been removed. As it is not a synchronous operation - switching to
        // the system user must happen first - give the system a little bit of time for finishing
        // it.
        final int sleepMs = 500;
        final int maxSleepMs = 10000;
        for (int totalSleptMs = 0; totalSleptMs < maxSleepMs; totalSleptMs += sleepMs) {
            // Wait a little while for the user's removal.
            Thread.sleep(sleepMs);

            if (!listUsers().contains(userId)) {
                // Success - the user has been removed.
                return;
            }
        }

        // The user hasn't been removed within the given time.
        fail("User must have been removed");
    }

    /** The users created after setting force-ephemeral-users policy to true must be ephemeral. */
    public void testCreateUserAfterSetForceEphemeralUsers() throws Exception {
        if (!mHasFeature || getDevice().getApiLevel() < 24 /* Build.VERSION_CODES.N */
                || !canCreateAdditionalUsers(1)) {
            return;
        }

        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceOwnerTest("ForceEphemeralUsersTest");

        int userId = createUser();
        assertTrue("User must be ephemeral", 0 != (getUserFlags(userId) & FLAG_EPHEMERAL));
    }

    /**
     * Test creating an epehemeral user using the DevicePolicyManager's createAndManageUser method.
     */
    public void testCreateAndManageEphemeralUser() throws Exception {
        if (!mHasFeature || getDevice().getApiLevel() < 24 /* Build.VERSION_CODES.N */
                || !canCreateAdditionalUsers(1)) {
            return;
        }

        ArrayList<Integer> originalUsers = listUsers();
        assertTrue(runDeviceTests(DEVICE_OWNER_PKG, ".CreateAndManageUserTest",
                "testCreateAndManageEphemeralUser", 0));

        ArrayList<Integer> newUsers = listUsers();

        // Check that exactly one new user was created.
        assertEquals(
                "One user should have been created", originalUsers.size() + 1, newUsers.size());

        // Get the id of the newly created user.
        int newUserId = -1;
        for (int userId : newUsers) {
            if (!originalUsers.contains(userId)) {
                newUserId = userId;
                break;
            }
        }

        // Get the flags of the new user and check the user is ephemeral.
        int flags = getUserFlags(newUserId);
        assertEquals("Ephemeral flag must be set", FLAG_EPHEMERAL, flags & FLAG_EPHEMERAL);
    }

    public void testLockTask() throws Exception {
        try {
            installApp(INTENT_RECEIVER_APK);
            executeDeviceOwnerTest("LockTaskTest");
        } finally {
            getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
        }
    }

    public void testSystemUpdatePolicy() throws Exception {
        executeDeviceOwnerTest("SystemUpdatePolicyTest");
    }

    public void testWifiConfigLockdown() throws Exception {
        final boolean hasWifi = hasDeviceFeature("android.hardware.wifi");
        if (hasWifi && mHasFeature) {
            try {
                installApp(WIFI_CONFIG_CREATOR_APK);
                executeDeviceOwnerTest("WifiConfigLockdownTest");
            } finally {
                getDevice().uninstallPackage(WIFI_CONFIG_CREATOR_PKG);
            }
        }
    }

    public void testCannotSetDeviceOwnerAgain() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // verify that we can't set the same admin receiver as device owner again
        assertFalse(setDeviceOwner(DEVICE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS));

        // verify that we can't set a different admin receiver as device owner
        try {
            installApp(MANAGED_PROFILE_APK);
            assertFalse(setDeviceOwner(MANAGED_PROFILE_PKG + "/" + MANAGED_PROFILE_ADMIN));
        } finally {
            getDevice().uninstallPackage(MANAGED_PROFILE_PKG);
        }
    }

    private void executeDeviceOwnerTest(String testClassName) throws Exception {
        if (!mHasFeature) {
            return;
        }
        String testClass = DEVICE_OWNER_PKG + "." + testClassName;
        assertTrue(testClass + " failed.", runDeviceTests(DEVICE_OWNER_PKG, testClass));
    }

    private void executeDeviceTestMethod(String className, String testName) throws Exception {
        assertTrue(runDeviceTestsAsUser(DEVICE_OWNER_PKG, className, testName,
                /* deviceOwnerUserId */0));
    }

    private void executeDeviceOwnerTestAsUser(String testClassName, int userId) throws Exception {
        if (!mHasFeature) {
            return;
        }
        String testClass = DEVICE_OWNER_PKG + "." + testClassName;
        assertTrue(testClass + " failed.", runDeviceTestsAsUser(DEVICE_OWNER_PKG, testClass, userId));
    }
}
