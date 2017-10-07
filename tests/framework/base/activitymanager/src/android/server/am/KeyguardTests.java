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
 * limitations under the License
 */

package android.server.am;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.server.am.WindowManagerState.WindowState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.KeyguardTests
 */
public class KeyguardTests extends KeyguardTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Set screen lock (swipe)
        setLockDisabled(false);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        tearDownLockCredentials();
    }

    @Test
    public void testKeyguardHidesActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("TestActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "TestActivity").build());
        mAmWmState.assertVisibility("TestActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        assertShowingAndNotOccluded();
        mAmWmState.assertVisibility("TestActivity", false);
        unlockDevice();
    }

    @Test
    public void testShowWhenLockedActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether dialogs from SHOW_WHEN_LOCKED activities are also visible if Keyguard is
     * showing.
     */
    @Test
    public void testShowWhenLockedActivity_withDialog() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedWithDialogActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("ShowWhenLockedWithDialogActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedWithDialogActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedWithDialogActivity", true);
        assertTrue(mAmWmState.getWmState().allWindowsVisible(
                getWindowName("ShowWhenLockedWithDialogActivity")));
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether multiple SHOW_WHEN_LOCKED activities are shown if the topmost is translucent.
     */
    @Test
    public void testMultipleShowWhenLockedActivities() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedActivity");
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("ShowWhenLockedActivity").build(),
                new WaitForValidActivityState.Builder("ShowWhenLockedTranslucentActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED_ACTIVITY, the wallpaper should also be showing.
     */
    @Test
    public void testTranslucentShowWhenLockedActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("ShowWhenLockedTranslucentActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        assertWallpaperShowing();
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED activity, the activity behind should not be shown.
     */
    @Test
    public void testTranslucentDoesntRevealBehind() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("TestActivity");
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder("TestActivity").build(),
                new WaitForValidActivityState.Builder("ShowWhenLockedTranslucentActivity").build());
        mAmWmState.assertVisibility("TestActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        mAmWmState.assertVisibility("TestActivity", false);
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    @Test
    public void testDialogShowWhenLockedActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedDialogActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedDialogActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedDialogActivity", true);
        gotoKeyguard();
        mAmWmState.computeState();
        mAmWmState.assertVisibility("ShowWhenLockedDialogActivity", true);
        assertWallpaperShowing();
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Test that showWhenLocked activity is fullscreen when shown over keyguard
     */
    @Test
    public void testShowWhenLockedActivityWhileSplit() throws Exception {
        if (!isHandheld() || !supportsSplitScreenMultiWindow()) {
            return;
        }
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        launchActivityToSide(true, false, "ShowWhenLockedActivity");
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedActivity" ).build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        assertShowingAndOccluded();
        mAmWmState.assertDoesNotContainStack("Activity must be full screen.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity occludes Keyguard.
     */
    @Test
    public void testDismissKeyguardActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardActivity");
        mAmWmState.waitForKeyguardShowingAndOccluded();
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "DismissKeyguardActivity").build());
        mAmWmState.assertVisibility("DismissKeyguardActivity", true);
        assertShowingAndOccluded();
    }

    @Test
    public void testDismissKeyguardActivity_method() throws Exception {
        if (!isHandheld()) {
            return;
        }
        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardMethodActivity");
        mAmWmState.waitForKeyguardGone();
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "DismissKeyguardMethodActivity").build());
        mAmWmState.assertVisibility("DismissKeyguardMethodActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat(logSeparator);
    }

    @Test
    public void testDismissKeyguardActivity_method_notTop() throws Exception {
        if (!isHandheld()) {
            return;
        }
        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("BroadcastReceiverActivity");
        launchActivity("TestActivity");
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguardMethod true");
        assertOnDismissErrorInLogcat(logSeparator);
    }

    @Test
    public void testDismissKeyguardActivity_method_turnScreenOn() throws Exception {
        if (!isHandheld()) {
            return;
        }
        final String logSeparator = clearLogcat();
        sleepDevice();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("TurnScreenOnDismissKeyguardActivity");
        mAmWmState.waitForKeyguardGone();
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "TurnScreenOnDismissKeyguardActivity").build());
        mAmWmState.assertVisibility("TurnScreenOnDismissKeyguardActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat(logSeparator);
    }

    @Test
    public void testDismissKeyguard_fromShowWhenLocked_notAllowed() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedActivity" ).build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        assertShowingAndOccluded();
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguard true");
        assertShowingAndOccluded();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
    }

    @Test
    public void testKeyguardLock() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        launchActivity("KeyguardLockActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "KeyguardLockActivity" ).build());
        mAmWmState.assertVisibility("KeyguardLockActivity", true);
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
    }

    @Test
    public void testUnoccludeRotationChange() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        executeShellCommand(getAmStartCmd("ShowWhenLockedActivity"));
        mAmWmState.computeState(new WaitForValidActivityState.Builder("ShowWhenLockedActivity").build());
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        setDeviceRotation(1);
        pressHomeButton();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.waitForDisplayUnfrozen();
        mAmWmState.assertSanity();
        mAmWmState.assertHomeActivityVisible(false);
        assertShowingAndNotOccluded();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", false);
    }

    private void assertWallpaperShowing() {
        WindowState wallpaper =
                mAmWmState.getWmState().findFirstWindowWithType(TYPE_WALLPAPER);
        assertNotNull(wallpaper);
        assertTrue(wallpaper.isShown());
    }

    @Test
    public void testDismissKeyguardAttrActivity_method_turnScreenOn() throws Exception {
        if (!isHandheld()) {
            return;
        }

        final String activityName = "TurnScreenOnAttrDismissKeyguardActivity";
        sleepDevice();

        final String logSeparator = clearLogcat();
        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity(activityName);
        mAmWmState.waitForKeyguardGone();
        mAmWmState.assertVisibility(activityName, true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat(logSeparator);
        assertTrue(isDisplayOn());
    }

    @Test
    public void testDismissKeyguardAttrActivity_method_turnScreenOn_withSecureKeyguard() throws Exception {
        if (!isHandheld()) {
            return;
        }

        final String activityName = "TurnScreenOnAttrDismissKeyguardActivity";

        setLockCredential();
        sleepDevice();

        mAmWmState.computeState();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity(activityName);
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertVisibility(activityName, false);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertTrue(isDisplayOn());
    }

    @Test
    public void testScreenOffWhileOccludedStopsActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }

        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        launchActivity("ShowWhenLockedAttrActivity");
        mAmWmState.computeState(new WaitForValidActivityState.Builder( "ShowWhenLockedAttrActivity" ).build());
        mAmWmState.assertVisibility("ShowWhenLockedAttrActivity", true);
        assertShowingAndOccluded();
        sleepDevice();
        assertSingleLaunchAndStop("ShowWhenLockedAttrActivity", logSeparator);
    }

    @Test
    public void testScreenOffCausesSingleStop() throws Exception {
        if (!isHandheld()) {
            return;
        }

        final String logSeparator = clearLogcat();
        launchActivity("TestActivity");
        mAmWmState.assertVisibility("TestActivity", true);
        sleepDevice();
        assertSingleLaunchAndStop("TestActivity", logSeparator);
    }
}
