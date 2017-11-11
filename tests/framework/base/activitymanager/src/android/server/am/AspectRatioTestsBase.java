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

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.view.Display;
import android.view.WindowManager;

class AspectRatioTestsBase {

    interface AssertAspectRatioCallback {
        void assertAspectRatio(float actual);
    }

    void runAspectRatioTest(final ActivityTestRule activityRule,
            final AssertAspectRatioCallback callback) {
        final Activity activity = launchActivity(activityRule);
        callback.assertAspectRatio(getAspectRatio(activity));
        finishActivity(activityRule);

        // TODO(b/35810513): All this rotation stuff doesn't really work yet. Need to make sure
        // context is updated correctly here. Also, what does it mean to be holding a reference to
        // this activity if changing the orientation will cause a relaunch?
//        activity.setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
//        waitForIdle();
//        callback.assertAspectRatio(getAspectRatio(activity));
//
//        activity.setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
//        waitForIdle();
//        callback.assertAspectRatio(getAspectRatio(activity));
    }

     static float getAspectRatio(Context context) {
        final Display display =
                context.getSystemService(WindowManager.class).getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);
        final float longSide = Math.max(size.x, size.y);
        final float shortSide = Math.min(size.x, size.y);
        return longSide / shortSide;
    }

    private Activity launchActivity(final ActivityTestRule activityRule) {
        final Activity activity = activityRule.launchActivity(null);
        waitForIdle();
        return activity;
    }

    private void finishActivity(ActivityTestRule activityRule) {
        final Activity activity = activityRule.getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    private static void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    static boolean aspectRatioEqual(float a, float b) {
        // Aspect ratios are considered equal if they ware within to significant digits.
        float diff = Math.abs(a - b);
        return diff < 0.01f;
    }
}
