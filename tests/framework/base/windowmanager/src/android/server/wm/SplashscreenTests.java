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

import static android.server.wm.CliIntentExtra.extraBool;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.app.Components.HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY;
import static android.server.wm.app.Components.SPLASHSCREEN_ACTIVITY;
import static android.server.wm.app.Components.SPLASH_SCREEN_REPLACE_ICON_ACTIVITY;
import static android.server.wm.app.Components.TestStartingWindowKeys.CANCEL_HANDLE_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.CONTAINS_CENTER_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.DELAY_RESUME;
import static android.server.wm.app.Components.TestStartingWindowKeys.HANDLE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_ANIMATING;
import static android.server.wm.app.Components.TestStartingWindowKeys.RECEIVE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.REPLACE_ICON_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_CREATE;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_RESUME;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.systemBars;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:SplashscreenTests
 */
@Presubmit
public class SplashscreenTests extends ActivityManagerTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mWmState.setSanityCheckWithFocusedWindow(false);
    }

    @After
    public void tearDown() {
        mWmState.setSanityCheckWithFocusedWindow(true);
    }

    @Test
    public void testSplashscreenContent() {
        launchActivityNoWait(SPLASHSCREEN_ACTIVITY);
        testSplashScreenColor(SPLASHSCREEN_ACTIVITY, Color.RED, Color.BLACK);
    }

    private void testSplashScreenColor(ComponentName name, int primaryColor, int secondaryColor) {
        // Activity may not be launched yet even if app transition is in idle state.
        mWmState.waitForActivityState(name, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        final Bitmap image = takeScreenshot();
        final WindowMetrics windowMetrics = mWm.getMaximumWindowMetrics();
        final Rect stableBounds = new Rect(windowMetrics.getBounds());
        stableBounds.inset(windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(
                systemBars() & ~captionBar()));
        final Rect appBounds = new Rect(mWmState.findFirstWindowWithType(
                WindowManager.LayoutParams.TYPE_APPLICATION_STARTING).getBounds());
        appBounds.intersect(stableBounds);
        // Use ratios to flexibly accommodate circular or not quite rectangular displays
        // Note: Color.BLACK is the pixel color outside of the display region
        assertColors(image, appBounds, primaryColor, 0.50f, secondaryColor, 0.02f);
    }

    private void assertColors(Bitmap img, Rect bounds, int primaryColor,
        float expectedPrimaryRatio, int secondaryColor, float acceptableWrongRatio) {

        int primaryPixels = 0;
        int secondaryPixels = 0;
        int wrongPixels = 0;
        for (int x = bounds.left; x < bounds.right; x++) {
            for (int y = bounds.top; y < bounds.bottom; y++) {
                assertThat(x, lessThan(img.getWidth()));
                assertThat(y, lessThan(img.getHeight()));
                final int color = img.getPixel(x, y);
                if (primaryColor == color) {
                    primaryPixels++;
                } else if (secondaryColor == color) {
                    secondaryPixels++;
                } else {
                    wrongPixels++;
                }
            }
        }

        final int totalPixels = bounds.width() * bounds.height();
        final float primaryRatio = (float) primaryPixels / totalPixels;
        if (primaryRatio < expectedPrimaryRatio) {
            fail("Less than " + (expectedPrimaryRatio * 100.0f)
                    + "% of pixels have non-primary color primaryPixels=" + primaryPixels
                    + " secondaryPixels=" + secondaryPixels + " wrongPixels=" + wrongPixels);
        }
        // Some pixels might be covered by screen shape decorations, like rounded corners.
        // On circular displays, there is an antialiased edge.
        final float wrongRatio = (float) wrongPixels / totalPixels;
        if (wrongRatio > acceptableWrongRatio) {
            fail("More than " + (acceptableWrongRatio * 100.0f)
                    + "% of pixels have wrong color primaryPixels=" + primaryPixels
                    + " secondaryPixels=" + secondaryPixels + " wrongPixels=" + wrongPixels);
        }
    }

    private void assumeNewApisEnabled() {
        // Temporary verify by shell command before new APIs enable.
        final String enableTest =
                executeShellCommand("getprop persist.debug.shell_starting_surface").trim();
        assumeTrue(Boolean.parseBoolean(enableTest));
    }

    @Test
    public void testHandleExitAnimationOnCreate() throws Exception {
        assumeNewApisEnabled();
        launchRuntimeHandleExitAnimationActivity(true, false, false, true);
    }
    @Test
    public void testHandleExitAnimationOnResume() throws Exception {
        assumeNewApisEnabled();
        launchRuntimeHandleExitAnimationActivity(false, true, false, true);
    }
    @Test
    public void testHandleExitAnimationCancel() throws Exception {
        assumeNewApisEnabled();
        launchRuntimeHandleExitAnimationActivity(true, false, true, false);
    }

    private void launchRuntimeHandleExitAnimationActivity(boolean extraOnCreate,
            boolean extraOnResume, boolean extraCancel, boolean expectResult) throws Exception {
        TestJournalProvider.TestJournalContainer.start();
        launchActivity(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY,
                extraBool(REQUEST_HANDLE_EXIT_ON_CREATE, extraOnCreate),
                extraBool(REQUEST_HANDLE_EXIT_ON_RESUME, extraOnResume),
                extraBool(CANCEL_HANDLE_EXIT, extraCancel));

        mWmState.computeState(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY);
        mWmState.assertVisibility(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY, true);
        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(HANDLE_SPLASH_SCREEN_EXIT);
        TestUtils.waitUntil("Waiting for runtime onSplashScreenExit", 5 /* timeoutSecond */,
                () -> expectResult == journal.extras.getBoolean(RECEIVE_SPLASH_SCREEN_EXIT));
        assertEquals(expectResult, journal.extras.getBoolean(CONTAINS_CENTER_VIEW));
    }


    @Test
    public void testSetBackgroundColorActivity() {
        assumeNewApisEnabled();
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, extraBool(DELAY_RESUME, true));
        testSplashScreenColor(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testHandleExitIconAnimatingActivity() throws Exception {
        assumeNewApisEnabled();
        TestJournalProvider.TestJournalContainer.start();
        launchActivity(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY,
                extraBool(REQUEST_HANDLE_EXIT_ON_CREATE, true));
        mWmState.computeState(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY);
        mWmState.assertVisibility(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, true);
        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(REPLACE_ICON_EXIT);
        TestUtils.waitUntil("Waiting for runtime onSplashScreenExit", 5 /* timeoutSecond */,
                () -> journal.extras.getBoolean(RECEIVE_SPLASH_SCREEN_EXIT));
        assertTrue(journal.extras.getBoolean(CONTAINS_CENTER_VIEW));
        assertFalse(journal.extras.getBoolean(ICON_ANIMATING));
    }

    @Test
    public void testCancelHandleExitIconAnimatingActivity() {
        assumeNewApisEnabled();
        TestJournalProvider.TestJournalContainer.start();
        launchActivity(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY,
                extraBool(REQUEST_HANDLE_EXIT_ON_CREATE, true),
                extraBool(CANCEL_HANDLE_EXIT, true));
        mWmState.waitForActivityState(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);

        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(REPLACE_ICON_EXIT);
        assertFalse(journal.extras.getBoolean(RECEIVE_SPLASH_SCREEN_EXIT));
    }
}
