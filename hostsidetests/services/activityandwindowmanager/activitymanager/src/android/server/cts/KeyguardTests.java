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

package android.server.cts;

import android.server.cts.WindowManagerState.WindowState;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test android.server.cts.KeyguardTests
 */
public class KeyguardTests extends KeyguardTestBase {

    public void testKeyguardHidesActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("TestActivity");
        mAmWmState.computeState(mDevice, new String[] { "TestActivity"});
        mAmWmState.assertVisibility("TestActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        assertShowingAndNotOccluded();
        mAmWmState.assertVisibility("TestActivity", false);
        unlockDevice();
    }

    public void testShowWhenLockedActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedActivity"});
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether dialogs from SHOW_WHEN_LOCKED activities are also visible if Keyguard is
     * showing.
     */
    public void testShowWhenLockedActivity_withDialog() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedWithDialogActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedWithDialogActivity"});
        mAmWmState.assertVisibility("ShowWhenLockedWithDialogActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
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
    public void testMultipleShowWhenLockedActivities() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedActivity");
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedActivity",
                "ShowWhenLockedTranslucentActivity"});
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED_ACTIVITY, the wallpaper should also be showing.
     */
    public void testTranslucentShowWhenLockedActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedTranslucentActivity"});
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        assertWallpaperShowing();
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED activity, the activity behind should not be shown.
     */
    public void testTranslucentDoesntRevealBehind() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("TestActivity");
        launchActivity("ShowWhenLockedTranslucentActivity");
        mAmWmState.computeState(mDevice, new String[] { "TestActivity",
                "ShowWhenLockedTranslucentActivity"});
        mAmWmState.assertVisibility("TestActivity", true);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        mAmWmState.assertVisibility("ShowWhenLockedTranslucentActivity", true);
        mAmWmState.assertVisibility("TestActivity", false);
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    public void testDialogShowWhenLockedActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedDialogActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedDialogActivity"});
        mAmWmState.assertVisibility("ShowWhenLockedDialogActivity", true);
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        mAmWmState.assertVisibility("ShowWhenLockedDialogActivity", true);
        assertWallpaperShowing();
        assertShowingAndOccluded();
        pressHomeButton();
        unlockDevice();
    }

    /**
     * Tests whether we can dismiss Keyguard when we start a FLAG_DISMISS_KEYGUARD activity.
     */
    public void testDismissKeyguardActivity() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardActivity");
        mAmWmState.waitForKeyguardGone(mDevice);
        mAmWmState.computeState(mDevice, new String[] { "DismissKeyguardActivity"});
        mAmWmState.assertVisibility("DismissKeyguardActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
    }

    public void testDismissKeyguardActivity_method() throws Exception {
        if (!isHandheld()) {
            return;
        }
        clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardMethodActivity");
        mAmWmState.waitForKeyguardGone(mDevice);
        mAmWmState.computeState(mDevice, new String[] { "DismissKeyguardMethodActivity"});
        mAmWmState.assertVisibility("DismissKeyguardMethodActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat();
    }

    public void testDismissKeyguardActivity_method_notTop() throws Exception {
        if (!isHandheld()) {
            return;
        }
        clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState(mDevice, null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("BroadcastReceiverActivity");
        launchActivity("TestActivity");
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguardMethod true");
        assertOnDismissErrorInLogcat();
    }

    public void testDismissKeyguardActivity_method_turnScreenOn() throws Exception {
        if (!isHandheld()) {
            return;
        }
        clearLogcat();
        sleepDevice();
        mAmWmState.computeState(mDevice, null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("TurnScreenOnDismissKeyguardActivity");
        mAmWmState.waitForKeyguardGone(mDevice);
        mAmWmState.computeState(mDevice, new String[] { "TurnScreenOnDismissKeyguardActivity"});
        mAmWmState.assertVisibility("TurnScreenOnDismissKeyguardActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat();
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity repeatedly dismissed Keyguard, i.e. whenever
     * lockscreen would show it gets dismissed immediately.
     */
    public void testDismissKeyguardActivity_repeating() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("DismissKeyguardActivity");
        for (int i = 0; i < 3; i++) {
            gotoKeyguard();
            mAmWmState.computeState(mDevice, new String[] { "DismissKeyguardActivity"});
            mAmWmState.assertVisibility("DismissKeyguardActivity", true);
            assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        }
    }

    public void testDismissKeyguard_fromShowWhenLocked() throws Exception {
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedActivity" });
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguard true");
        mAmWmState.waitForKeyguardGone(mDevice);
        assertKeyguardGone();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
    }

    public void testKeyguardLock() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
        launchActivity("KeyguardLockActivity");
        mAmWmState.computeState(mDevice, new String[] { "KeyguardLockActivity" });
        mAmWmState.assertVisibility("KeyguardLockActivity", true);
        executeShellCommand("am broadcast -a trigger_broadcast --ez finish true");
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
    }

    public void testUnoccludeRotationChange() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        assertShowingAndNotOccluded();
        executeShellCommand(getAmStartCmd("ShowWhenLockedActivity"));
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedActivity" });
        mAmWmState.assertVisibility("ShowWhenLockedActivity", true);
        setDeviceRotation(1);
        pressHomeButton();
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        mAmWmState.waitForDisplayUnfrozen(mDevice);
        mAmWmState.assertSanity();
        mAmWmState.assertHomeActivityVisible(false);
        assertShowingAndNotOccluded();
        mAmWmState.assertVisibility("ShowWhenLockedActivity", false);
    }

    private void assertWallpaperShowing() {
        WindowState wallpaper =
                mAmWmState.getWmState().findFirstWindowWithType(WindowState.TYPE_WALLPAPER);
        assertNotNull(wallpaper);
        assertTrue(wallpaper.isShown());
    }
}
