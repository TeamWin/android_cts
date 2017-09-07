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


import static android.app.ActivityManager.StackId.PINNED_STACK_ID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases KeyguardLockedTests
 */
public class KeyguardLockedTests extends KeyguardTestBase {

    private static final String SHOW_WHEN_LOCKED_ACTIVITY = "ShowWhenLockedActivity";
    private static final String PIP_ACTIVITY = "PipActivity";
    private static final String PIP_ACTIVITY_ACTION_ENTER_PIP =
            "android.server.am.PipActivity.enter_pip";
    private static final String EXTRA_SHOW_OVER_KEYGUARD = "show_over_keyguard";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setLockCredential();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tearDownLockCredentials();
    }

    @Test
    public void testLockAndUnlock() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        unlockDeviceWithCredential();
        mAmWmState.waitForKeyguardGone();
        assertKeyguardGone();
    }

    @Test
    public void testDismissKeyguard() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        launchActivity("DismissKeyguardActivity");
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone();
        assertKeyguardGone();
        mAmWmState.assertVisibility("DismissKeyguardActivity", true);
    }

    @Test
    public void testDismissKeyguard_whileOccluded() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mAmWmState.computeState(new String[] { SHOW_WHEN_LOCKED_ACTIVITY });
        mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        launchActivity("DismissKeyguardActivity");
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone();
        assertKeyguardGone();
        mAmWmState.assertVisibility("DismissKeyguardActivity", true);
        mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, false);
    }

    @Test
    public void testDismissKeyguard_fromShowWhenLocked_notAllowed() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mAmWmState.computeState(new String[] { SHOW_WHEN_LOCKED_ACTIVITY });
        mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        executeShellCommand("am broadcast -a trigger_broadcast --ez dismissKeyguard true");
        enterAndConfirmLockCredential();

        // Make sure we stay on Keyguard.
        assertShowingAndOccluded();
        mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
    }

    @Test
    public void testDismissKeyguardActivity_method() throws Exception {
        if (!isHandheld()) {
            return;
        }
        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState(null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardMethodActivity");
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone();
        mAmWmState.computeState(new String[] { "DismissKeyguardMethodActivity"});
        mAmWmState.assertVisibility("DismissKeyguardMethodActivity", true);
        assertFalse(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceededInLogcat(logSeparator);
    }

    @Test
    public void testDismissKeyguardActivity_method_cancelled() throws Exception {
        if (!isHandheld()) {
            return;
        }
        final String logSeparator = clearLogcat();
        gotoKeyguard();
        mAmWmState.computeState(null);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        launchActivity("DismissKeyguardMethodActivity");
        pressBackButton();
        assertOnDismissCancelledInLogcat(logSeparator);
        mAmWmState.computeState(new String[] {});
        mAmWmState.assertVisibility("DismissKeyguardMethodActivity", false);
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);
        unlockDeviceWithCredential();
    }

    @Test
    public void testEnterPipOverKeyguard() throws Exception {
        if (!isHandheld() || !supportsPip()) {
            return;
        }

        // Go to the keyguard
        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertTrue(mAmWmState.getAmState().getKeyguardControllerState().keyguardShowing);

        // Enter PiP on an activity on top of the keyguard, and ensure that it prompts the user for
        // their credentials and does not enter picture-in-picture yet
        launchActivity(PIP_ACTIVITY, EXTRA_SHOW_OVER_KEYGUARD, "true");
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForKeyguardShowingAndOccluded();
        assertShowingAndOccluded();
        mAmWmState.assertDoesNotContainStack("Must not contain pinned stack.", PINNED_STACK_ID);

        // Enter the credentials and ensure that the activity actually entered picture-in-picture
        enterAndConfirmLockCredential();
        mAmWmState.waitForKeyguardGone();
        assertKeyguardGone();
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
    }

    @Test
    public void testShowWhenLockedActivityAndPipActivity() throws Exception {
        if (!isHandheld() || !supportsPip()) {
            return;
        }

        launchActivity(PIP_ACTIVITY);
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.computeState(new String[] { PIP_ACTIVITY });
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);

        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mAmWmState.computeState(new String[] { SHOW_WHEN_LOCKED_ACTIVITY });
        mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);

        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndOccluded();
        assertShowingAndOccluded();
        mAmWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mAmWmState.assertVisibility(PIP_ACTIVITY, false);
    }

    @Test
    public void testShowWhenLockedPipActivity() throws Exception {
        if (!isHandheld() || !supportsPip()) {
            return;
        }

        launchActivity(PIP_ACTIVITY, EXTRA_SHOW_OVER_KEYGUARD, "true");
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.computeState(new String[] { PIP_ACTIVITY });
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);

        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        assertShowingAndNotOccluded();
        mAmWmState.assertVisibility(PIP_ACTIVITY, false);
    }
}
