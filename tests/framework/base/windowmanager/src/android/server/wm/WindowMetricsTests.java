/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.util.Size;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;

import androidx.test.rule.ActivityTestRule;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Presubmit
public class WindowMetricsTests extends WindowManagerTestBase {

    ActivityTestRule<MetricsActivity> mMetricsActivity =
            new ActivityTestRule<>(MetricsActivity.class);

    @Test
    public void test_metrics() {
        final MetricsActivity activity = mMetricsActivity.launchActivity(null);
        activity.waitForLayout();

        assertEquals(activity.mOnLayoutSize, activity.mOnCreateCurrentMetrics.getSize());
        assertTrue(activity.mOnCreateMaximumMetrics.getSize().getWidth()
                >= activity.mOnCreateCurrentMetrics.getSize().getWidth());
        assertTrue(activity.mOnCreateMaximumMetrics.getSize().getHeight()
                >= activity.mOnCreateCurrentMetrics.getSize().getHeight());

        assertEquals(activity.mOnLayoutInsets.getSystemWindowInsets(),
                activity.mOnCreateCurrentMetrics.getWindowInsets().getSystemWindowInsets());
        assertEquals(activity.mOnLayoutInsets.getStableInsets(),
                activity.mOnCreateCurrentMetrics.getWindowInsets().getStableInsets());
        assertEquals(activity.mOnLayoutInsets.getDisplayCutout(),
                activity.mOnCreateCurrentMetrics.getWindowInsets().getDisplayCutout());

    }

    public static class MetricsActivity extends Activity implements View.OnLayoutChangeListener {

        private final CountDownLatch mLayoutLatch = new CountDownLatch(1);

        private WindowMetrics mOnCreateMaximumMetrics;
        private WindowMetrics mOnCreateCurrentMetrics;

        private Size mOnLayoutSize;
        private WindowInsets mOnLayoutInsets;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mOnCreateCurrentMetrics = getWindowManager().getCurrentWindowMetrics();
            mOnCreateMaximumMetrics = getWindowManager().getMaximumWindowMetrics();
            getWindow().getDecorView().addOnLayoutChangeListener(this);
        }


        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            mOnLayoutSize = new Size(getWindow().getDecorView().getWidth(),
                    getWindow().getDecorView().getHeight());
            mOnLayoutInsets = getWindow().getDecorView().getRootWindowInsets();
            mLayoutLatch.countDown();
        }

        public void waitForLayout() {
            try {
                assertTrue("timed out waiting for activity to layout",
                        mLayoutLatch.await(4, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }
}
