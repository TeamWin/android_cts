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

import static android.content.Context.WINDOW_SERVICE;
import static android.content.pm.PackageManager.FEATURE_WATCH;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Build: mmma -j32 cts/tests/framework/base/activitymanager
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.AspectRatioTests
 */
@RunWith(AndroidJUnit4.class)
public class AspectRatioTests extends AspectRatioTestsBase {

    // The max. aspect ratio the test activities are using.
    private static final float MAX_ASPECT_RATIO = 1.0f;

    // The minimum supported device aspect ratio.
    private static final float MIN_DEVICE_ASPECT_RATIO = 1.333f;

    // The minimum supported device aspect ratio for watches.
    private static final float MIN_WATCH_DEVICE_ASPECT_RATIO = 1.0f;

    // Test target activity that has maxAspectRatio="true" and resizeableActivity="false".
    public static class MaxAspectRatioActivity extends Activity {
    }

    // Test target activity that has maxAspectRatio="1.0" and resizeableActivity="true".
    public static class MaxAspectRatioResizeableActivity extends Activity {
    }

    // Test target activity that has no maxAspectRatio defined and resizeableActivity="false".
    public static class MaxAspectRatioUnsetActivity extends Activity {
    }

    // Test target activity that has maxAspectRatio defined as
    //   <meta-data android:name="android.max_aspect" android:value="1.0" />
    // and resizeableActivity="false".
    public static class MetaDataMaxAspectRatioActivity extends Activity {
    }

    @Rule
    public ActivityTestRule<MaxAspectRatioActivity> mMaxAspectRatioActivity =
            new ActivityTestRule<>(MaxAspectRatioActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    @Rule
    public ActivityTestRule<MaxAspectRatioResizeableActivity> mMaxAspectRatioResizeableActivity =
            new ActivityTestRule<>(MaxAspectRatioResizeableActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    @Rule
    public ActivityTestRule<MetaDataMaxAspectRatioActivity> mMetaDataMaxAspectRatioActivity =
            new ActivityTestRule<>(MetaDataMaxAspectRatioActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    @Rule
    public ActivityTestRule<MaxAspectRatioUnsetActivity> mMaxAspectRatioUnsetActivity =
            new ActivityTestRule<>(MaxAspectRatioUnsetActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    @Test
    @Presubmit
    public void testDeviceAspectRatio() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        float longSide = Math.max(metrics.widthPixels, metrics.heightPixels);
        float shortSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        float deviceAspectRatio = longSide / shortSide;
        float expectedMinAspectRatio = context.getPackageManager().hasSystemFeature(FEATURE_WATCH)
                ? MIN_WATCH_DEVICE_ASPECT_RATIO : MIN_DEVICE_ASPECT_RATIO;

        if (deviceAspectRatio < expectedMinAspectRatio) {
            fail("deviceAspectRatio=" + deviceAspectRatio
                    + " is less than expectedMinAspectRatio=" + expectedMinAspectRatio);
        }
    }

    @Test
    @Presubmit
    public void testMaxAspectRatio() throws Exception {
        runAspectRatioTest(mMaxAspectRatioActivity, actual -> {
            if (MAX_ASPECT_RATIO >= actual) return;
            fail("actual=" + actual + " is greater than expected=" + MAX_ASPECT_RATIO);
        });
    }

    @Test
    @Presubmit
    public void testMetaDataMaxAspectRatio() throws Exception {
        runAspectRatioTest(mMetaDataMaxAspectRatioActivity, actual -> {
            if (MAX_ASPECT_RATIO >= actual) return;
            fail("actual=" + actual + " is greater than expected=" + MAX_ASPECT_RATIO);
        });
    }

    @Test
    @FlakyTest // TODO: Currently 10% flaky so not part of pre-submit for now
    public void testMaxAspectRatioResizeableActivity() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final float expected = getAspectRatio(context);

        // Since this activity is resizeable, its aspect ratio shouldn't be less than the device's
        runAspectRatioTest(mMaxAspectRatioResizeableActivity, actual -> {
            if (aspectRatioEqual(expected, actual) || expected < actual) return;
            fail("actual=" + actual + " is less than expected=" + expected);
        });
    }

    @Test
    @Presubmit
    public void testMaxAspectRatioUnsetActivity() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final float expected = getAspectRatio(context);

        // Since this activity didn't set an aspect ratio, its aspect ratio shouldn't be less than
        // the device's
        runAspectRatioTest(mMaxAspectRatioUnsetActivity, actual -> {
            if (aspectRatioEqual(expected, actual) || expected < actual) return;
            fail("actual=" + actual + " is less than expected=" + expected);
        });
    }
}
