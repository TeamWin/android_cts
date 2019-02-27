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

import static android.server.am.UiDeviceUtils.pressHomeButton;
import static android.server.am.UiDeviceUtils.pressUnlockButton;
import static android.server.am.UiDeviceUtils.pressWakeupButton;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Point;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowInputTests
 */
@FlakyTest
public class WindowInputTests {
    private final int TOTAL_NUMBER_OF_CLICKS = 100;
    private final ActivityTestRule<Activity> mActivityRule = new ActivityTestRule<>(Activity.class);

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private View mView;
    private final Random mRandom = new Random();

    private int mClickCount = 0;

    @Before
    public void setUp() {
        pressWakeupButton();
        pressUnlockButton();
        pressHomeButton();

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.launchActivity(null);
        mInstrumentation.waitForIdleSync();
        mClickCount = 0;
    }

    @Test
    public void testMoveWindowAndTap() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        final Display display = wm.getDefaultDisplay();
        Point displaySize = new Point();
        display.getSize(displaySize);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        mClickCount = 0;

        // Set up window.
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.width = 20;
            p.height = 20;
            p.gravity = Gravity.LEFT | Gravity.TOP;
            mView.setOnClickListener((v) -> {
                mClickCount++;
            });
            wm.addView(mView, p);
        });
        mInstrumentation.waitForIdleSync();

        final Point locationOnScreen = new Point();
        // Move the window to a random location on screen and attempt to tap on view multiple times.
        for (int i = 0; i < TOTAL_NUMBER_OF_CLICKS; i++) {
            selectRandomLocationOnScreen(displaySize, locationOnScreen);
            mActivityRule.runOnUiThread(() -> {
                p.x = locationOnScreen.x;
                p.y = locationOnScreen.y;
                wm.updateViewLayout(mView, p);
            });
            mInstrumentation.waitForIdleSync();
            CtsTouchUtils.emulateTapOnView(mInstrumentation, mView, 0 /* offsetX */,
                    0 /* offsetY */);
        }

        assertEquals(TOTAL_NUMBER_OF_CLICKS, mClickCount);
    }

    private void selectRandomLocationOnScreen(Point displaySize, Point outLocation) {
        int randomX = mRandom.nextInt(displaySize.x);
        int randomY = mRandom.nextInt(displaySize.y);
        outLocation.set(randomX, randomY);
    }
}
