/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityView;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.annotation.UiThreadTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Build/Install/Run:
 *      atest CtsWindowManagerDeviceTestCases:ActivityViewTest
 */
@Presubmit
@android.server.wm.annotation.Group3
public class ActivityViewTest extends ActivityManagerTestBase {
    private static final long IME_EVENT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    private Instrumentation mInstrumentation;
    private ActivityView mActivityView;

    @Rule
    public final ActivityTestRule<ActivityViewTestActivity> mActivityRule =
            new ActivityTestRule<>(ActivityViewTestActivity.class, true /* initialTouchMode */,
                false /* launchActivity */);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsMultiDisplay());
        mInstrumentation = getInstrumentation();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            ActivityViewTestActivity activity = mActivityRule.launchActivity(null);
            mActivityView = activity.getActivityView();
        });
        separateTestJournal();
    }

    @After
    public void tearDown() throws Throwable {
        if (mActivityView != null) {
            // Detach ActivityView before releasing to avoid accessing removed display.
            mActivityRule.runOnUiThread(
                    () -> ((ViewGroup) mActivityView.getParent()).removeView(mActivityView));
            SystemUtil.runWithShellPermissionIdentity(() -> mActivityView.release());
        }
    }

    @Test
    public void testStartActivity() {
        launchActivityInActivityView(TEST_ACTIVITY);
        assertSingleLaunch(TEST_ACTIVITY);
    }

    @UiThreadTest
    @Test
    public void testResizeActivityView() {
        final int width = 500;
        final int height = 500;

        launchActivityInActivityView(TEST_ACTIVITY);
        assertSingleLaunch(TEST_ACTIVITY);

        mActivityView.layout(0, 0, width, height);

        boolean boundsMatched = checkDisplaySize(TEST_ACTIVITY, width, height);
        assertTrue("displayWidth and displayHeight must equal " + width + "x" + height,
                boundsMatched);
    }

    /** @return {@code true} if the display size for the activity matches the given size. */
    private boolean checkDisplaySize(ComponentName activity, int requestedWidth,
            int requestedHeight) {
        // Display size for the activity may not get updated right away. Retry in case.
        return Condition.waitFor("display size=" + requestedWidth + "x" + requestedHeight, () -> {
            final WindowManagerState wmState = mWmState;
            wmState.computeState();

            final int displayId = mWmState.getDisplayByActivity(activity);
            final WindowManagerState.DisplayContent display = wmState.getDisplay(displayId);
            int avDisplayWidth = 0;
            int avDisplayHeight = 0;
            if (display != null) {
                Rect bounds = display.mFullConfiguration.windowConfiguration.getAppBounds();
                if (bounds != null) {
                    avDisplayWidth = bounds.width();
                    avDisplayHeight = bounds.height();
                }
            }
            return avDisplayWidth == requestedWidth && avDisplayHeight == requestedHeight;
        });
    }

    @Test
    public void testAppStoppedWithVisibilityGone() {
        launchActivityInActivityView(TEST_ACTIVITY);
        assertSingleLaunch(TEST_ACTIVITY);

        separateTestJournal();
        mInstrumentation.runOnMainSync(() -> mActivityView.setVisibility(View.GONE));
        mInstrumentation.waitForIdleSync();
        mWmState.waitForActivityState(TEST_ACTIVITY, STATE_STOPPED);

        assertLifecycleCounts(TEST_ACTIVITY, 0, 0, 0, 1, 1, 0, CountSpec.DONT_CARE);
    }

    @Test
    public void testAppStoppedWithVisibilityInvisible() {
        launchActivityInActivityView(TEST_ACTIVITY);
        assertSingleLaunch(TEST_ACTIVITY);

        separateTestJournal();
        mInstrumentation.runOnMainSync(() -> mActivityView.setVisibility(View.INVISIBLE));
        mInstrumentation.waitForIdleSync();
        mWmState.waitForActivityState(TEST_ACTIVITY, STATE_STOPPED);

        assertLifecycleCounts(TEST_ACTIVITY, 0, 0, 0, 1, 1, 0, CountSpec.DONT_CARE);
    }

    @Test
    public void testAppStopAndStartWithVisibilityChange() {
        launchActivityInActivityView(TEST_ACTIVITY);
        assertSingleLaunch(TEST_ACTIVITY);

        separateTestJournal();
        mInstrumentation.runOnMainSync(() -> mActivityView.setVisibility(View.INVISIBLE));
        mInstrumentation.waitForIdleSync();
        mWmState.waitForActivityState(TEST_ACTIVITY, STATE_STOPPED);

        assertLifecycleCounts(TEST_ACTIVITY, 0, 0, 0, 1, 1, 0, CountSpec.DONT_CARE);

        separateTestJournal();
        mInstrumentation.runOnMainSync(() -> mActivityView.setVisibility(View.VISIBLE));
        mInstrumentation.waitForIdleSync();
        mWmState.waitForActivityState(TEST_ACTIVITY, STATE_RESUMED);

        assertLifecycleCounts(TEST_ACTIVITY, 0, 1, 1, 0, 0, 0, CountSpec.DONT_CARE);
    }

    private void launchActivityInActivityView(ComponentName activity) {
        Intent intent = new Intent();
        intent.setComponent(activity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        SystemUtil.runWithShellPermissionIdentity(() -> mActivityView.startActivity(intent));
        mWmState.waitForValidState(activity);
    }

    private Rect getActivityViewBoundsOnScreen() {
        final int[] location = new int[2];
        mInstrumentation.runOnMainSync(() -> {
            mActivityView.getLocationOnScreen(location);
        });
        return new Rect(location[0], location[1], location[0] + mActivityView.getWidth(),
                location[1] + mActivityView.getHeight());
    }

    // Test activity
    public static class ActivityViewTestActivity extends Activity {
        private ActivityView mActivityView;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mActivityView = new ActivityView(this);
            setContentView(mActivityView);

            ViewGroup.LayoutParams layoutParams = mActivityView.getLayoutParams();
            layoutParams.width = MATCH_PARENT;
            layoutParams.height = MATCH_PARENT;
            mActivityView.requestLayout();
        }

        ActivityView getActivityView() {
            return mActivityView;
        }
    }
}
