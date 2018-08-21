/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.graphics.Rect;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.provider.Settings.Global.WINDOW_ANIMATION_SCALE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Test whether WindowManager performs the correct layout after we make some changes to it.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:LayoutTests
 */
@Presubmit
@FlakyTest(detail = "Can be promoted to pre-submit once confirmed stable.")
@RunWith(AndroidJUnit4.class)
public class LayoutTests {
    private final long TIMEOUT_LAYOUT = 100; // milliseconds
    private final long TIMEOUT_REMOVE_WINDOW = 500;
    private final long TIMEOUT_SYSTEM_UI_VISIBILITY_CHANGE = 1000;
    private final int SYSTEM_UI_FLAG_HIDE_ALL = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

    private Instrumentation mInstrumentation;
    private LayoutTestsActivity mActivity;
    private ContentResolver mResolver;
    private float mWindowAnimationScale;
    private boolean mSystemUiFlagsGotCleared = false;

    @Rule
    public final ActivityTestRule<LayoutTestsActivity> mActivityRule =
            new ActivityTestRule<>(LayoutTestsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mResolver = mInstrumentation.getContext().getContentResolver();

        SystemUtil.runWithShellPermissionIdentity(() -> {
            // The layout will be performed at the end of the animation of hiding
            // status/navigation bar, which will recover the possible issue, so we disable the
            // animation during the test.
            mWindowAnimationScale = Settings.Global.getFloat(mResolver, WINDOW_ANIMATION_SCALE, 1f);
            Settings.Global.putFloat(mResolver, WINDOW_ANIMATION_SCALE, 0);
        });
    }

    @After
    public void tearDown() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            // Restore the animation we disabled previously.
            Settings.Global.putFloat(mResolver, WINDOW_ANIMATION_SCALE, mWindowAnimationScale);
        });
    }

    @Test
    public void testLayoutAfterRemovingFocus() throws InterruptedException {
        mActivity = mActivityRule.getActivity();
        assertNotNull(mActivity);

        // Get the visible frame of the main activity before adding any window.
        Rect visibleFrame = new Rect();
        mActivity.getWindowVisibleDisplayFrame(visibleFrame);

        doTestLayoutAfterRemovingFocus(visibleFrame, mActivity::addWindowHidingStatusBar);
        doTestLayoutAfterRemovingFocus(visibleFrame, mActivity::addWindowHidingNavigationBar);
        doTestLayoutAfterRemovingFocus(visibleFrame, mActivity::addWindowHidingBothSystemBars);
    }

    private void doTestLayoutAfterRemovingFocus(Rect visibleFrameBeforeAddingWindow,
            Runnable toAddWindow) throws InterruptedException {
        // Add a window which can affect the global layout.
        mInstrumentation.runOnMainSync(toAddWindow);

        // Wait a bit for the layout finish triggered by adding window.
        SystemClock.sleep(TIMEOUT_LAYOUT);

        // Remove the window we added previously.
        mInstrumentation.runOnMainSync(mActivity::removeWindow);
        mInstrumentation.runOnMainSync(this::stopWaiting);
        synchronized (this) {
            wait(TIMEOUT_REMOVE_WINDOW);
        }

        // Get the visible frame of the main activity after removing the window we added.
        Rect visibleFrameAfterRemovingWindow = new Rect();
        mActivity.getWindowVisibleDisplayFrame(visibleFrameAfterRemovingWindow);

        // Test whether the visible frame after removing window is the same as one before adding
        // window. If not, it shows that the layout after removing window has a problem.
        assertEquals(visibleFrameBeforeAddingWindow, visibleFrameAfterRemovingWindow);
    }

    private void stopWaiting() {
        synchronized (this) {
            notify();
        }
    }

    @Test
    public void testAddingImmersiveWindow() throws InterruptedException {
        mActivity = mActivityRule.getActivity();
        assertNotNull(mActivity);

        mInstrumentation.runOnMainSync(this::addImmersiveWindow);
        synchronized (this) {
            wait(TIMEOUT_SYSTEM_UI_VISIBILITY_CHANGE);
        }
        assertFalse("System UI flags should not be cleared.", mSystemUiFlagsGotCleared);
    }

    private void addImmersiveWindow() {
        View view = new View(mActivity);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | SYSTEM_UI_FLAG_HIDE_ALL);
        view.setOnSystemUiVisibilityChangeListener(
                visibility -> {
                    if ((visibility & SYSTEM_UI_FLAG_HIDE_ALL) != SYSTEM_UI_FLAG_HIDE_ALL) {
                        mSystemUiFlagsGotCleared = true;
                        // Early break because things go wrong already
                        stopWaiting();
                    }
                });
        mActivity.addWindow(view);
    }
}
