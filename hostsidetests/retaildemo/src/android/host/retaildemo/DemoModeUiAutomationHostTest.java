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
package android.host.retaildemo;

import com.android.tradefed.device.DeviceNotAvailableException;

import java.util.ArrayList;

public class DemoModeUiAutomationHostTest extends BaseTestCase {
    private static final int USER_INACTIVITY_TIMEOUT_MS = 120 * 1000; // 2 min

    private static final int TIMEOUT_DEMO_USER_START_MS = 2000; // 2 sec
    private static final int CHECK_INTERVAL_DEMO_USER_START_MS = 200; // 0.2 sec

    private static final int TIMEOUT_DEMO_USER_UNLOCK_MS = 4000; // 4 sec
    private static final int CHECK_INTERVAL_DEMO_USER_UNLOCK_MS = 500; // 0.5 sec

    private static final int TIMEOUT_SCREEN_TURN_ON_ATTRACT_LOOP_MS = 1000; // 1 sec
    private static final int TIMEOUT_SCREEN_TURN_ON_DEMO_SESSION_MS = 4000; // 4 sec

    private int mStartUserId;
    private String mStartRetailDemoModeConstants;
    private ArrayList<Integer> existingDemoUsers;
    private Integer mCurrentDemoUserId;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (!mSupportsMultiUser) {
            return;
        }
        mStartUserId = getDevice().getCurrentUser();
        existingDemoUsers = new ArrayList<>();
        // Increase the inactivity timeout so that the demo session is not reset during the test.
        mStartRetailDemoModeConstants = getRetailDemoModeConstants();
        setUserInactivityTimeoutMs(USER_INACTIVITY_TIMEOUT_MS);
        enableDemoMode(true);
    }

    public void testResetNotification() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        initializeDemoUser();
        executeDeviceTest("RetailDemoPkg is not launched", "testRetailDemoPkgLaunched");
        executeDeviceTest("Notification to reset demo mode not found", "testResetNotification");

        if (!awaitDemoSessionStart()) {
            fail("Demo session is not recreated.");
        }
        installAppAsUser(RETAIL_DEMO_TEST_APK, mCurrentDemoUserId);
        executeDeviceTest("RetailDemoPkg is not launched after session restart",
                "testRetailDemoPkgLaunched");
    }

    public void testResetAfterInactivity() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        initializeDemoUser();
        executeDeviceTest("RetailDemoPkg is not launched", "testRetailDemoPkgLaunched");
        final long timeoutMs = 15 * 1000; // 15 sec
        setUserInactivityTimeoutMs(timeoutMs);
        setWarningDialogTimeoutMs(0);
        getDevice().executeShellCommand("input tap 1 1"); // Tap to enter demo session.
        getDevice().executeShellCommand("input keyevent 3"); // Tap home (any user activity is ok)
        Thread.sleep(timeoutMs);

        if (!awaitDemoSessionStart()) {
            fail("Demo session is not recreated.");
        }
        installAppAsUser(RETAIL_DEMO_TEST_APK, mCurrentDemoUserId);
        executeDeviceTest("RetailDemoPkg is not launched after session restart",
                "testRetailDemoPkgLaunched");
    }

    public void testUserRestrictions() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        initializeDemoUser();
        executeDeviceTest("User restrictions not set correctly for demo user",
                "testUserRestrictions_inDemoUser");
        executeDeviceTestAsUser("User restrictions not set correctly for system user",
                "testUserRestrictions_inSystemUser", USER_SYSTEM);
    }

    public void testScreenTurnsOn_attractLoop() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        initializeDemoUser();
        executeDeviceTest("RetailDemoPkg is not launched", "testRetailDemoPkgLaunched");
        getDevice().executeShellCommand("input keyevent 26"); // Turn off the screen.
        // Wait for the screen to turn on.
        Thread.sleep(TIMEOUT_SCREEN_TURN_ON_ATTRACT_LOOP_MS);
        // Verify that the screen is turned on.
        executeDeviceTest("Screen is not turned on automatically during attract loop",
                "testScreenIsInteractive");
    }

    public void testScreenTurnsOn_demoSession() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        initializeDemoUser();
        executeDeviceTest("RetailDemoPkg is not launched", "testRetailDemoPkgLaunched");
        getDevice().executeShellCommand("input tap 1 1"); // Tap to enter demo session.
        getDevice().executeShellCommand("input keyevent 26"); // Turn off the screen.
        // Wait for the screen to turn on.
        Thread.sleep(TIMEOUT_SCREEN_TURN_ON_DEMO_SESSION_MS);
        // Verify that the screen is turned on.
        executeDeviceTest("Screen is not turned on automatically during demo session",
                "testScreenIsInteractive");
    }

    @Override
    public void tearDown() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        enableDemoMode(false);
        Thread.sleep(2000 /* 2 sec */); // Wait for the setting to be persisted.
        getDevice().switchUser(mStartUserId);
        setRetailDemoModeConstants(mStartRetailDemoModeConstants);
        super.tearDown();
    }

    private void initializeDemoUser() throws Exception {
        // Wait for the demo user to start.
        if (!awaitDemoSessionStart()) {
            fail("Demo user is not created");
        }
        installAppAsUser(RETAIL_DEMO_TEST_APK, mCurrentDemoUserId);
        // Wait for the demo user to unlock.
        if (!awaitDemoUserUnlock()) {
            fail("Demo user is not unlocked");
        }
    }

    private boolean awaitDemoUserUnlock()
            throws InterruptedException, DeviceNotAvailableException {
        final long endTime = System.currentTimeMillis() + TIMEOUT_DEMO_USER_UNLOCK_MS;
        while (!isUserUnlocked(mCurrentDemoUserId)
                && System.currentTimeMillis() < endTime) {
            Thread.sleep(CHECK_INTERVAL_DEMO_USER_UNLOCK_MS);
        }
        return isUserUnlocked(mCurrentDemoUserId);
    }

    private boolean awaitDemoSessionStart()
            throws InterruptedException, DeviceNotAvailableException {
        final long endTime = System.currentTimeMillis() + TIMEOUT_DEMO_USER_START_MS;
        while (mStartUserId == getDevice().getCurrentUser()
                && System.currentTimeMillis() < endTime) {
            Thread.sleep(CHECK_INTERVAL_DEMO_USER_START_MS);
        }
        mCurrentDemoUserId = getDemoUserId(existingDemoUsers);
        if (mCurrentDemoUserId != null) {
            existingDemoUsers.add(mCurrentDemoUserId);
            return true;
        }
        return false;
    }

    private void executeDeviceTest(String msg, String testMethodName) throws Exception {
        executeDeviceTestAsUser(msg, testMethodName, mCurrentDemoUserId);
    }

    private void executeDeviceTestAsUser(String msg, String testMethodName, int userId)
            throws Exception {
        assertTrue(msg, runDeviceTestsAsUser(".DemoModeUiAutomationTest", testMethodName, userId));
    }
}