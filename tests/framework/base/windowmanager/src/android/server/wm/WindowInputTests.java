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

import static android.server.wm.ActivityManagerTestBase.launchHomeActivityNoWait;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowInputTests
 */
@Presubmit
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
        launchHomeActivityNoWait();

        mInstrumentation = getInstrumentation();
        mActivity = mActivityRule.launchActivity(null);
        mInstrumentation.waitForIdleSync();
        mClickCount = 0;
    }

    @Test
    public void testMoveWindowAndTap() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        Point displaySize = new Point();
        mActivity.getDisplay().getSize(displaySize);

        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
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

        WindowInsets insets = mActivity.getWindow().getDecorView().getRootWindowInsets();
        final Rect windowBounds = new Rect(insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                displaySize.x - insets.getSystemWindowInsetRight(),
                displaySize.y - insets.getSystemWindowInsetBottom());

        // Move the window to a random location in the window and attempt to tap on view multiple
        // times.
        final Point locationInWindow = new Point();
        for (int i = 0; i < TOTAL_NUMBER_OF_CLICKS; i++) {
            selectRandomLocationInWindow(windowBounds, locationInWindow);
            mActivityRule.runOnUiThread(() -> {
                p.x = locationInWindow.x;
                p.y = locationInWindow.y;
                wm.updateViewLayout(mView, p);
            });
            mInstrumentation.waitForIdleSync();
            CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        }

        assertEquals(TOTAL_NUMBER_OF_CLICKS, mClickCount);
    }

    private void selectRandomLocationInWindow(Rect bounds, Point outLocation) {
        int randomX = mRandom.nextInt(bounds.right - bounds.left) + bounds.left;
        int randomY = mRandom.nextInt(bounds.bottom - bounds.top) + bounds.top;
        outLocation.set(randomX, randomY);
    }

    @Test
    public void testFilterTouchesWhenObscured() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        mClickCount = 0;

        // Set up window.
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.width = 20;
            p.height = 20;
            p.gravity = Gravity.LEFT | Gravity.TOP;
            mView.setFilterTouchesWhenObscured(true);
            mView.setOnClickListener((v) -> {
                mClickCount++;
            });
            wm.addView(mView, p);

            View viewOverlap = new View(mActivity);
            p.gravity = Gravity.RIGHT | Gravity.TOP;
            p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            wm.addView(viewOverlap, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);
    }

    @Test
    public void testOverlapWindow() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();

        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        mClickCount = 0;
        try (final PointerLocationSession session = new PointerLocationSession()) {
            session.set(1);
            // Set up window.
            mActivityRule.runOnUiThread(() -> {
                mView = new View(mActivity);
                p.width = 20;
                p.height = 20;
                p.gravity = Gravity.LEFT | Gravity.TOP;
                mView.setFilterTouchesWhenObscured(true);
                mView.setOnClickListener((v) -> {
                    mClickCount++;
                });
                wm.addView(mView, p);

            });
            mInstrumentation.waitForIdleSync();

            CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        }
        assertEquals(1, mClickCount);
    }

    @Test
    public void testWindowBecomesUnTouchable() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        mClickCount = 0;

        final View viewOverlap = new View(mActivity);

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

            p.width = 100;
            p.height = 100;
            p.gravity = Gravity.LEFT | Gravity.TOP;
            p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            wm.addView(viewOverlap, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);

        mActivityRule.runOnUiThread(() -> {
            p.flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE;
            wm.updateViewLayout(viewOverlap, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(1, mClickCount);
    }

    /** Helper class to save, set, and restore pointer location preferences. */
    private static class PointerLocationSession extends SettingsSession<Integer> {
        PointerLocationSession() {
            super(Settings.System.getUriFor("pointer_location" /* POINTER_LOCATION */),
                    PointerLocationSession::getInt,
                    PointerLocationSession::putInt);
        }

        private static void putInt(ContentResolver contentResolver, String s, Integer v) {
            SystemUtil.runShellCommand("settings put system " + "pointer_location" + " " + v);
        }

        private static Integer getInt(ContentResolver contentResolver, String s) {
            try {
                return Integer.parseInt(SystemUtil.runShellCommand(
                        "settings get system " + "pointer_location").trim());
            } catch (NumberFormatException e) {
                return 0;
            }

        }
    }
}
