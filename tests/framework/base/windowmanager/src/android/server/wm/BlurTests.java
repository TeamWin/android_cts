/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm;

import static android.app.ActivityTaskManager.INVALID_STACK_ID;
import static android.server.wm.CliIntentExtra.extraInt;
import static android.server.wm.app.Components.BLUR_ACTIVITY;
import static android.server.wm.app.Components.BACKGROUND_IMAGE_ACTIVITY;
import static android.server.wm.app.Components.BlurActivity.ACTION_FINISH;
import static android.server.wm.app.Components.BlurActivity.EXTRA_BACKGROUND_BLUR_RADIUS_PX;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;
import android.graphics.Bitmap;

import androidx.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Test;

@Presubmit
@FlakyTest(detail = "Promote once confirmed non-flaky")
public class BlurTests extends ActivityManagerTestBase {

    @Before
    public void setUp() {
        assumeTrue(supportsBlur());
        launchActivity(BACKGROUND_IMAGE_ACTIVITY);
        mWmState.waitForValidState(BACKGROUND_IMAGE_ACTIVITY);
    }

    @Test
    public void testBackgroundBlurDifferentRadius() throws Exception {
        launchActivity(BLUR_ACTIVITY, extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, 5));
        mWmState.waitForValidState(BLUR_ACTIVITY);

        final int stackId = mWmState.getStackIdByActivity(BLUR_ACTIVITY);
        assertNotEquals(stackId, INVALID_STACK_ID);
        final Bitmap lowBlur = takeScreenshot();

        mBroadcastActionTrigger.finishBroadcastReceiverActivity();

        launchActivity(BLUR_ACTIVITY, extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, 150));
        mWmState.waitForValidState(BLUR_ACTIVITY);

        final int stackId2 = mWmState.getStackIdByActivity(BLUR_ACTIVITY);
        assertNotEquals(stackId2, INVALID_STACK_ID);
        final Bitmap highBlur = takeScreenshot();

        assertFalse(lowBlur.sameAs(highBlur));

        //TODO(b/179990440): Add more tests for blurs in window manager
    }
}
