/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.cts;

import static android.server.cts.ActivityManagerState.STATE_RESUMED;
import static android.server.cts.ActivityManagerState.STATE_STOPPED;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Display tests that require a locked keyguard.
 *
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsActivityManagerDeviceTestCases android.server.cts.ActivityManagerDisplayLockedKeyguardTests
 */
public class ActivityManagerDisplayLockedKeyguardTests extends ActivityManagerDisplayTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String VIRTUAL_DISPLAY_ACTIVITY = "VirtualDisplayActivity";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setLockCredential();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        removeLockCredential();
        gotoKeyguard();
        super.tearDown();
    }

    /**
     * Test that virtual display content is hidden when device is locked.
     */
    @Test
    public void testVirtualDisplayHidesContentWhenLocked() throws Exception {
        if (!supportsMultiDisplay() || !isHandheld()) { return; }

        // Create new usual virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);

        // Lock the device.
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.waitForActivityState(TEST_ACTIVITY_NAME, STATE_STOPPED);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, false /* visible */);

        // Unlock and check if visibility is back.
        unlockDeviceWithCredential();
        mAmWmState.waitForKeyguardGone();
        mAmWmState.waitForActivityState(TEST_ACTIVITY_NAME, STATE_RESUMED);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
    }
}
