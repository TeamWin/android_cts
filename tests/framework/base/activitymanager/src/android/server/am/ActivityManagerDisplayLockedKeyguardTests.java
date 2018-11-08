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

package android.server.am;

import static android.server.am.ActivityManagerState.STATE_RESUMED;
import static android.server.am.ActivityManagerState.STATE_STOPPED;
import static android.server.am.Components.DISMISS_KEYGUARD_ACTIVITY;
import static android.server.am.Components.SHOW_WHEN_LOCKED_ACTIVITY;
import static android.server.am.Components.TEST_ACTIVITY;
import static android.server.am.Components.VIRTUAL_DISPLAY_ACTIVITY;

import static org.junit.Assume.assumeTrue;

import android.server.am.ActivityManagerState.ActivityDisplay;

import org.junit.Before;
import org.junit.Test;

/**
 * Display tests that require a locked keyguard.
 *
 * <p>Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerDisplayLockedKeyguardTests
 */
public class ActivityManagerDisplayLockedKeyguardTests extends ActivityManagerDisplayTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(supportsSecureLock());
    }

    /**
     * Test that virtual display content is hidden when device is locked.
     */
    @Test
    public void testVirtualDisplayHidesContentWhenLocked() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential();

            // Create new usual virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);
            mAmWmState.assertVisibility(TEST_ACTIVITY, true /* visible */);

            // Lock the device.
            lockScreenSession.gotoKeyguard();
            waitAndAssertActivityState(TEST_ACTIVITY, STATE_STOPPED,
                    "Expected stopped activity on secondary display ");
            mAmWmState.assertVisibility(TEST_ACTIVITY, false /* visible */);

            // Unlock and check if visibility is back.
            lockScreenSession.unlockDevice();

            lockScreenSession.enterAndConfirmLockCredential();
            mAmWmState.waitForKeyguardGone();
            mAmWmState.assertKeyguardGone();
            waitAndAssertActivityState(TEST_ACTIVITY, STATE_RESUMED,
                    "Expected resumed activity on secondary display");
            mAmWmState.assertVisibility(TEST_ACTIVITY, true /* visible */);
        }
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity on a secondary display dismisses the keyguard.
     */
    @Test
    public void testDismissKeyguard_secondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential();
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true).
                    createDisplay();

            lockScreenSession.gotoKeyguard();
            mAmWmState.assertKeyguardShowingAndNotOccluded();
            getLaunchActivityBuilder().setUseInstrumentation()
                    .setTargetActivity(DISMISS_KEYGUARD_ACTIVITY).setNewTask(true)
                    .setMultipleTask(true).setDisplayId(newDisplay.mId)
                    .setWaitForLaunched(false).execute();
            waitAndAssertActivityState(DISMISS_KEYGUARD_ACTIVITY, STATE_STOPPED,
                    "Expected stopped activity on secondary display");
            lockScreenSession.enterAndConfirmLockCredential();
            mAmWmState.waitForKeyguardGone();
            mAmWmState.assertKeyguardGone();
            mAmWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
        }
    }

    @Test
    public void testDismissKeyguard_whileOccluded_secondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential();
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true).
                    createDisplay();

            lockScreenSession.gotoKeyguard();
            mAmWmState.assertKeyguardShowingAndNotOccluded();
            launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
            mAmWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
            mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
            getLaunchActivityBuilder().setUseInstrumentation()
                    .setTargetActivity(DISMISS_KEYGUARD_ACTIVITY).setNewTask(true)
                    .setMultipleTask(true).setDisplayId(newDisplay.mId)
                    .setWaitForLaunched(false).execute();
            waitAndAssertActivityState(DISMISS_KEYGUARD_ACTIVITY, STATE_STOPPED,
                    "Expected stopped activity on secondary display");
            lockScreenSession.enterAndConfirmLockCredential();
            mAmWmState.waitForKeyguardGone();
            mAmWmState.assertKeyguardGone();
            mAmWmState.computeState(DISMISS_KEYGUARD_ACTIVITY);
            mAmWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
            mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        }
    }
}
