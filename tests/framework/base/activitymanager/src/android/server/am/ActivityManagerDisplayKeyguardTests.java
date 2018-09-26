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

import static android.server.am.Components.DISMISS_KEYGUARD_ACTIVITY;
import static android.server.am.UiDeviceUtils.pressBackButton;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.server.am.ActivityManagerState.ActivityDisplay;

import org.junit.Before;
import org.junit.Test;

/**
 * Display tests that require a keyguard.
 *
 * <p>Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerDisplayKeyguardTests
 */
public class ActivityManagerDisplayKeyguardTests extends ActivityManagerDisplayTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(supportsInsecureLock());
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity on a secondary display is visible (for an
     * insecure keyguard).
     */
    @Test
    public void testDismissKeyguardActivity_secondaryDisplay() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession();
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            lockScreenSession.gotoKeyguard();
            mAmWmState.assertKeyguardShowingAndNotOccluded();
            launchActivityOnDisplay(DISMISS_KEYGUARD_ACTIVITY, newDisplay.mId);
            mAmWmState.waitForKeyguardShowingAndNotOccluded();
            mAmWmState.assertKeyguardShowingAndNotOccluded();
            mAmWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
        }
    }

    /**
     * Tests keyguard dialog should shows on external display.
     * @throws Exception
     */
    @Test
    public void testShowKeyguardDialogOnSecondaryDisplay() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession();
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .createDisplay();

            lockScreenSession.gotoKeyguard();
            WindowManagerState.WindowState keyguardWindowState =
                    mAmWmState.waitForValidProduct(
                            this::getKeyguardDialogWindowState, "KeyguardDialog",
                            w -> w.isShown() && w.getDisplayId() == newDisplay.mId);
            assertNotNull("KeyguardDialog must show up", keyguardWindowState);
            assertEquals("KeyguardDialog should show on external display", newDisplay.mId,
                    keyguardWindowState.getDisplayId());

            // Keyguard dialog mustn't be removed when press back key
            pressBackButton();
            keyguardWindowState = mAmWmState.waitForValidProduct(
                    this::getKeyguardDialogWindowState, "KeyguardDialog",
                    w -> w.isShown() && w.getDisplayId() == newDisplay.mId);
            assertNotNull("KeyguardDialog must show up", keyguardWindowState);
        }
    }

    private WindowManagerState.WindowState getKeyguardDialogWindowState() {
        final WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState();
        return mAmWmState.getWmState().findFirstWindowWithType(TYPE_KEYGUARD_DIALOG);
    }
}
