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

package android.server.wm;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.UI_MODE_TYPE_NORMAL;
import static android.server.wm.app.Components.MAX_ASPECT_RATIO_ACTIVITY;
import static android.server.wm.app.Components.MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY;
import static android.server.wm.app.Components.MAX_ASPECT_RATIO_UNSET_ACTIVITY;
import static android.server.wm.app.Components.META_DATA_MAX_ASPECT_RATIO_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_UNSET_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.server.wm.MultiDisplayTestBase.DisplayMetricsSession;
import android.util.Size;
import android.view.Display;

import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:AspectRatioTests
 */
@Presubmit
public class AspectRatioTests extends AspectRatioTestsBase {

    private static final ComponentName NON_RESIZEABLE_PORTRAIT_ACTIVITY =
            component(NonResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_LANDSCAPE_ACTIVITY =
            component(NonResizeableLandscapeActivity.class);

    // The max. aspect ratio the test activities are using.
    private static final float MAX_ASPECT_RATIO = 1.0f;

    // The min. aspect ratio the test activities are using.
    private static final float MIN_ASPECT_RATIO = 3.0f;

    // Min allowed aspect ratio for unresizable apps which is used when an app doesn't specify
    // android:minAspectRatio in accordance with the CDD 7.1.1.2 requirement:
    // https://source.android.com/compatibility/12/android-12-cdd#7112_screen_aspect_ratio
    private static final float MIN_UNRESIZABLE_ASPECT_RATIO = 4 / 3f;

    @Test
    public void testMaxAspectRatio() {
        // Activity has a maxAspectRatio, assert that the actual ratio is less than that.
        runAspectRatioTest(MAX_ASPECT_RATIO_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            assertThat(actual, lessThanOrEqualTo(MAX_ASPECT_RATIO));
        });
    }

    @Test
    public void testMetaDataMaxAspectRatio() {
        // Activity has a maxAspectRatio, assert that the actual ratio is less than that.
        runAspectRatioTest(META_DATA_MAX_ASPECT_RATIO_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            assertThat(actual, lessThanOrEqualTo(MAX_ASPECT_RATIO));
        });
    }

    @Test
    public void testMaxAspectRatioResizeableActivity() {
        // Since this activity is resizeable, its max aspect ratio should be ignored.
        runAspectRatioTest(MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            // TODO(b/69982434): Add ability to get native aspect ratio non-default display.
            assumeThat(displayId, is(Display.DEFAULT_DISPLAY));

            final float defaultDisplayAspectRatio =
                    getDisplayAspectRatio(MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY);
            assertThat(actual, greaterThanOrEqualToInexact(defaultDisplayAspectRatio));
        });
    }

    @Test
    public void testMaxAspectRatioUnsetActivity() {
        // Since this activity didn't set an explicit maxAspectRatio, there should be no such
        // ratio enforced.
        runAspectRatioTest(MAX_ASPECT_RATIO_UNSET_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            // TODO(b/69982434): Add ability to get native aspect ratio non-default display.
            assumeThat(displayId, is(Display.DEFAULT_DISPLAY));

            assertThat(actual, greaterThanOrEqualToInexact(
                    getDisplayAspectRatio(MAX_ASPECT_RATIO_UNSET_ACTIVITY)));
        });
    }

    @Test
    public void testMinAspectRatio() {
        // Activity has a minAspectRatio, assert the ratio is at least that.
        runAspectRatioTest(MIN_ASPECT_RATIO_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            assertThat(actual, greaterThanOrEqualToInexact(MIN_ASPECT_RATIO));
        });
    }

    @Test
    public void testMinAspectRatioUnsetActivity() {
        // Since this activity didn't set an explicit minAspectRatio, there should be no such
        // ratio enforced.
        runAspectRatioTest(MIN_ASPECT_RATIO_UNSET_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            // TODO(b/69982434): Add ability to get native aspect ratio non-default display.
            assumeThat(displayId, is(Display.DEFAULT_DISPLAY));

            assertThat(actual, lessThanOrEqualToInexact(
                    getDisplayAspectRatio(MIN_ASPECT_RATIO_UNSET_ACTIVITY)));
        });
    }

    @Test
    public void testMinAspectRatioUnsetActivityRespectsCDDOnLandscapeDisplay() {
        runCDDAspectRatioTest(ORIENTATION_LANDSCAPE, NON_RESIZEABLE_LANDSCAPE_ACTIVITY);
    }

    @Test
    public void testMinAspectRatioUnsetActivityRespectsCDDOnPortraitDisplay() {
        runCDDAspectRatioTest(ORIENTATION_PORTRAIT, NON_RESIZEABLE_PORTRAIT_ACTIVITY);
    }

    private void runCDDAspectRatioTest(int orientation, ComponentName component) {
        UiModeManager mUiModeManager = mContext.getSystemService(UiModeManager.class);
        assumeTrue(UI_MODE_TYPE_NORMAL == mUiModeManager.getCurrentModeType());
        DisplayMetricsSession displayMetricsSession =
                createManagedDisplayMetricsSession(DEFAULT_DISPLAY);
        Size originalDisplaySize = displayMetricsSession.getInitialDisplayMetrics().getSize();
        // Resize to almost square display.
        displayMetricsSession.changeAspectRatio(1.1f, orientation);

        Size currentDisplaySize = displayMetricsSession.getDisplayMetrics().getSize();
        assumeFalse("If a display size is capped, resizing may be a no-op",
                originalDisplaySize.equals(currentDisplaySize));
        runAspectRatioTest(component, (actual, displayId, activitySize, displaySize) -> {
            // TODO(b/69982434): Add ability to get native aspect ratio non-default display.
            assumeThat(displayId, is(Display.DEFAULT_DISPLAY));
            assertThat(actual, greaterThanOrEqualToInexact(MIN_UNRESIZABLE_ASPECT_RATIO));
        });
    }

    @Test
    public void testMinAspectLandscapeActivity() {
        // Activity has requested a fixed orientation, assert the orientation is that.
        runAspectRatioTest(MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            assertThat(activitySize.x, greaterThan(activitySize.y));
            // Since activities must fit within the bounds of the display and they should respect
            // the minimal size, there is an aspect ratio limit that an activity cannot exceed even
            // if set in the app manifest. In such scenarios, we won't expect the aspect ratio to
            // be respected.
            int maxAspectRatioForDisplay = displaySize.x
                    / getMinimalTaskSize(MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY);
            if (MIN_ASPECT_RATIO <= maxAspectRatioForDisplay) {
                // The display size is large enough to support the desired aspect ratio
                // without violating the minimal size restriction.
                assertThat(actual, greaterThanOrEqualToInexact(MIN_ASPECT_RATIO));
            }
        });
    }

    @Test
    public void testMinAspectPortraitActivity() {
        runAspectRatioTest(MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY,
                (actual, displayId, activitySize, displaySize) -> {
            assertThat(activitySize.y, greaterThan(activitySize.x));
            // Since activities must fit within the bounds of the display and they should respect
            // the minimal size, there is an aspect ratio limit that an activity cannot exceed even
            // if set in the app manifest. In such scenarios, we won't expect the aspect ratio to
            // be respected.
            int maxAspectRatioForDisplay = displaySize.y
                    / getMinimalTaskSize(MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY);
            if (MIN_ASPECT_RATIO <= maxAspectRatioForDisplay) {
                // The display size is large enough to support the desired aspect ratio
                // without violating the minimal size restriction.
                assertThat(actual, greaterThanOrEqualToInexact(MIN_ASPECT_RATIO));
            }
        });
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    private DisplayMetricsSession createManagedDisplayMetricsSession(int displayId) {
        return mObjectTracker.manage(new DisplayMetricsSession(displayId));
    }

    private static ComponentName component(Class<? extends Activity> activity) {
        return new ComponentName(getInstrumentation().getContext(), activity);
    }

    public static class NonResizeablePortraitActivity extends Activity {
    }

    public static class NonResizeableLandscapeActivity extends Activity {
    }
}