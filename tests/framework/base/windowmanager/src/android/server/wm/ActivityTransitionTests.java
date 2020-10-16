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

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.Presubmit;
import android.server.wm.cts.R;
import android.util.Range;

import org.junit.Test;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:ActivityTransitionTests
 */
@Presubmit
public class ActivityTransitionTests extends ActivityManagerTestBase {
    @Test
    public void testActivityTransitionDurationNoShortenAsExpected() throws Exception {
        final long expectedDurationMs = 500L - 100L;
        final long minDurationMs = expectedDurationMs;
        final long maxDurationMs = expectedDurationMs + 300L;
        final Range<Long> durationRange = new Range<>(minDurationMs, maxDurationMs);

        final CountDownLatch latch = new CountDownLatch(1);
        long[] transitionStartTime = new long[1];
        long[] transitionEndTime = new long[1];

        final ActivityOptions.OnAnimationStartedListener startedListener = () -> {
            transitionStartTime[0] = System.currentTimeMillis();
        };

        final ActivityOptions.OnAnimationFinishedListener finishedListener = () -> {
            transitionEndTime[0] = System.currentTimeMillis();
            latch.countDown();
        };

        final Intent intent = new Intent(mContext, LauncherActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final LauncherActivity launcherActivity =
            (LauncherActivity) instrumentation.startActivitySync(intent);

        final Bundle bundle = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha, 0, new Handler(Looper.getMainLooper()), startedListener,
                finishedListener).toBundle();
        launcherActivity.startTransitionActivity(bundle);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertTopResumedActivity(new ComponentName(mContext, TransitionActivity.class),
            DEFAULT_DISPLAY, "Activity must be launched");

        latch.await(2, TimeUnit.SECONDS);
        final long totalTime = transitionEndTime[0] - transitionStartTime[0];
        assertTrue("Actual transition duration should be in the range "
                + "<" + minDurationMs + ", " + maxDurationMs + "> ms, "
                + "actual=" + totalTime, durationRange.contains(totalTime));
    }

    public static class LauncherActivity extends Activity {

        public void startTransitionActivity(Bundle bundle) {
            startActivity(new Intent(this, TransitionActivity.class), bundle);
        }
    }

    public static class TransitionActivity extends Activity {
    }
}
