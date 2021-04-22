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

import static android.app.UiModeManager.MODE_NIGHT_AUTO;
import static android.app.UiModeManager.MODE_NIGHT_CUSTOM;
import static android.app.UiModeManager.MODE_NIGHT_NO;
import static android.app.UiModeManager.MODE_NIGHT_YES;
import static android.server.wm.CliIntentExtra.extraBool;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.app.Components.HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY;
import static android.server.wm.app.Components.SPLASHSCREEN_ACTIVITY;
import static android.server.wm.app.Components.SPLASH_SCREEN_REPLACE_ICON_ACTIVITY;
import static android.server.wm.app.Components.TestStartingWindowKeys.CANCEL_HANDLE_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.CONTAINS_BRANDING_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.CONTAINS_CENTER_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.DELAY_RESUME;
import static android.server.wm.app.Components.TestStartingWindowKeys.GET_NIGHT_MODE_ACTIVITY_CHANGED;
import static android.server.wm.app.Components.TestStartingWindowKeys.HANDLE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_ANIMATION_DURATION;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_ANIMATION_START;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_BACKGROUND_COLOR;
import static android.server.wm.app.Components.TestStartingWindowKeys.RECEIVE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.REPLACE_ICON_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_CREATE;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_RESUME;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_SET_NIGHT_MODE_ON_CREATE;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.systemBars;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
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

import java.util.Collections;

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

    @Test
    public void testHandleExitAnimationOnCreate() throws Exception {
        assumeFalse(isLeanBack());
        launchRuntimeHandleExitAnimationActivity(true, false, false, true);
    }
    @Test
    public void testHandleExitAnimationOnResume() throws Exception {
        assumeFalse(isLeanBack());
        launchRuntimeHandleExitAnimationActivity(false, true, false, true);
    }
    @Test
    public void testHandleExitAnimationCancel() throws Exception {
        assumeFalse(isLeanBack());
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
        assertEquals(expectResult, journal.extras.getBoolean(CONTAINS_BRANDING_VIEW));
        assertEquals(expectResult ? Color.BLUE : Color.TRANSPARENT,
                journal.extras.getInt(ICON_BACKGROUND_COLOR));
    }

    @Test
    public void testSetApplicationNightMode() throws Exception {
        final UiModeManager uiModeManager = mContext.getSystemService(UiModeManager.class);
        assumeTrue(uiModeManager != null);
        final int systemNightMode = uiModeManager.getNightMode();
        final int testNightMode = (systemNightMode == MODE_NIGHT_AUTO
                || systemNightMode == MODE_NIGHT_CUSTOM) ? MODE_NIGHT_YES
                : systemNightMode == MODE_NIGHT_YES ? MODE_NIGHT_NO : MODE_NIGHT_YES;
        final int testConfigNightMode = testNightMode == MODE_NIGHT_YES
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        final String nightModeNo = String.valueOf(testNightMode);

        TestJournalProvider.TestJournalContainer.start();
        launchActivity(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY,
                extraString(REQUEST_SET_NIGHT_MODE_ON_CREATE, nightModeNo));
        mWmState.computeState(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY);
        mWmState.assertVisibility(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY, true);
        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(HANDLE_SPLASH_SCREEN_EXIT);
        TestUtils.waitUntil("Waiting for night mode changed", 5 /* timeoutSecond */, () ->
                testConfigNightMode == journal.extras.getInt(GET_NIGHT_MODE_ACTIVITY_CHANGED));
        assertEquals(testConfigNightMode,
                journal.extras.getInt(GET_NIGHT_MODE_ACTIVITY_CHANGED));
    }

    @Test
    public void testSetBackgroundColorActivity() {
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, extraBool(DELAY_RESUME, true));
        testSplashScreenColor(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testHandleExitIconAnimatingActivity() throws Exception {
        assumeFalse(isLeanBack());
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
        final long iconAnimationStart = journal.extras.getLong(ICON_ANIMATION_START);
        final long iconAnimationDuration = journal.extras.getLong(ICON_ANIMATION_DURATION);
        assertTrue(iconAnimationStart != 0);
        assertEquals(iconAnimationDuration, 500);
        assertFalse(journal.extras.getBoolean(CONTAINS_BRANDING_VIEW));
    }

    @Test
    public void testCancelHandleExitIconAnimatingActivity() {
        assumeFalse(isLeanBack());
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

    @Test
    public void testShortcutChangeTheme() {
        final LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
        final ShortcutManager shortcutManager = mContext.getSystemService(ShortcutManager.class);
        assumeTrue(launcherApps != null && shortcutManager != null);

        final String shortCutId = "shortcut1";
        final ShortcutInfo.Builder b = new ShortcutInfo.Builder(
                mContext, shortCutId);
        final Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(SPLASHSCREEN_ACTIVITY);
        final ShortcutInfo shortcut = b.setShortLabel("label")
                .setLongLabel("long label")
                .setIntent(i)
                .setStartingTheme(android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .build();
        try {
            shortcutManager.addDynamicShortcuts(Collections.singletonList(shortcut));
            runWithShellPermission(() -> launcherApps.startShortcut(shortcut, null, null));
            testSplashScreenColor(SPLASHSCREEN_ACTIVITY, Color.BLACK, Color.BLACK);
        } finally {
            shortcutManager.removeDynamicShortcuts(Collections.singletonList(shortCutId));
        }
    }
}
