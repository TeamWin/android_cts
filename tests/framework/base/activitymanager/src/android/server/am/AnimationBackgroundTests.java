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

package android.server.am;


import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Build: mmma -j32 cts/tests/framework/base
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.AnimationBackgroundTests
 */
public class AnimationBackgroundTests extends ActivityManagerTestBase {

    @Test
    public void testAnimationBackground_duringAnimation() throws Exception {
        launchActivity(LAUNCHING_ACTIVITY);
        getLaunchActivityBuilder()
                .setTargetActivityName("AnimationTestActivity")
                .setWaitForLaunched(false)
                .execute();

        // Make sure we are in the middle of the animation.
        mAmWmState.waitForWithWmState(state -> state
                .getStandardStackByWindowingMode(WINDOWING_MODE_FULLSCREEN)
                        .isWindowAnimationBackgroundSurfaceShowing(),
                "***Waiting for animation background showing");
        assertTrue("window animation background needs to be showing", mAmWmState.getWmState()
                .getStandardStackByWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .isWindowAnimationBackgroundSurfaceShowing());
    }

    @Test
    public void testAnimationBackground_gone() throws Exception {
        launchActivity(LAUNCHING_ACTIVITY);
        getLaunchActivityBuilder().setTargetActivityName("AnimationTestActivity").execute();
        mAmWmState.computeState(new WaitForValidActivityState.Builder("AnimationTestActivity").build());
        assertFalse("window animation background needs to be gone", mAmWmState.getWmState()
                .getStandardStackByWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .isWindowAnimationBackgroundSurfaceShowing());
    }
}
