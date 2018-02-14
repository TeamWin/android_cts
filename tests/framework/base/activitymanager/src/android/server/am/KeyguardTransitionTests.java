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

import static android.server.am.WindowManagerState.TRANSIT_ACTIVITY_OPEN;
import static android.server.am.WindowManagerState.TRANSIT_KEYGUARD_GOING_AWAY;
import static android.server.am.WindowManagerState.TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER;
import static android.server.am.WindowManagerState.TRANSIT_KEYGUARD_OCCLUDE;
import static android.server.am.WindowManagerState.TRANSIT_KEYGUARD_UNOCCLUDE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:KeyguardTransitionTests
 */
public class KeyguardTransitionTests extends ActivityManagerTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(isHandheld());
        assumeFalse(isUiModeLockedToVrHeadset());
    }

    @Test
    public void testUnlock() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            launchActivity("TestActivity");
            lockScreenSession.gotoKeyguard()
                    .unlockDevice();
            mAmWmState.computeState(new WaitForValidActivityState("TestActivity"));
            assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_GOING_AWAY,
                    mAmWmState.getWmState().getLastTransition());
        }
    }

    @Test
    public void testUnlockWallpaper() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            launchActivity("WallpaperActivity");
            lockScreenSession.gotoKeyguard()
                    .unlockDevice();
            mAmWmState.computeState(new WaitForValidActivityState("WallpaperActivity"));
            assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER,
                    mAmWmState.getWmState().getLastTransition());
        }
    }

    @Test
    public void testOcclude() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.gotoKeyguard();
            launchActivity("ShowWhenLockedActivity");
            mAmWmState.computeState(new WaitForValidActivityState("ShowWhenLockedActivity"));
            assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                    mAmWmState.getWmState().getLastTransition());
        }
    }

    @Test
    public void testUnocclude() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.gotoKeyguard();
            launchActivity("ShowWhenLockedActivity");
            launchActivity("TestActivity");
            mAmWmState.waitForKeyguardShowingAndNotOccluded();
            mAmWmState.computeState(true);
            assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_UNOCCLUDE,
                    mAmWmState.getWmState().getLastTransition());
        }
    }

    @Test
    public void testNewActivityDuringOccluded() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            launchActivity("ShowWhenLockedActivity");
            lockScreenSession.gotoKeyguard();
            launchActivity("ShowWhenLockedWithDialogActivity");
            mAmWmState.computeState(
                    new WaitForValidActivityState("ShowWhenLockedWithDialogActivity"));
            assertEquals("Picked wrong transition", TRANSIT_ACTIVITY_OPEN,
                    mAmWmState.getWmState().getLastTransition());
        }
    }

    @Test
    public void testOccludeManifestAttr() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            final String activityName = "ShowWhenLockedAttrActivity";

            lockScreenSession.gotoKeyguard();
            final String logSeparator = clearLogcat();
            launchActivity(activityName);
            mAmWmState.computeState(new WaitForValidActivityState(activityName));
            assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                    mAmWmState.getWmState().getLastTransition());
            assertSingleLaunch(activityName, logSeparator);
        }
    }

    @Test
    public void testOccludeAttrRemove() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            final String activityName = "ShowWhenLockedAttrRemoveAttrActivity";

            lockScreenSession.gotoKeyguard();
            String logSeparator = clearLogcat();
            launchActivity(activityName);
            mAmWmState.computeState(new WaitForValidActivityState(activityName));
            assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                    mAmWmState.getWmState().getLastTransition());
            assertSingleLaunch(activityName, logSeparator);

            lockScreenSession.gotoKeyguard();
            logSeparator = clearLogcat();
            launchActivity(activityName);
            mAmWmState.computeState(new WaitForValidActivityState(activityName));
            assertSingleStartAndStop(activityName, logSeparator);
        }
    }

    @Test
    public void testNewActivityDuringOccludedWithAttr() throws Exception {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            final String activityName1 = "ShowWhenLockedAttrActivity";
            final String activityName2 = "ShowWhenLockedAttrWithDialogActivity";

            launchActivity(activityName1);
            lockScreenSession.gotoKeyguard();
            launchActivity(activityName2);
            mAmWmState.computeState(new WaitForValidActivityState(activityName2));
            assertEquals("Picked wrong transition", TRANSIT_ACTIVITY_OPEN,
                    mAmWmState.getWmState().getLastTransition());
        }
    }
}
