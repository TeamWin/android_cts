/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.app.Components.BackgroundActivityTransition.TRANSITION_REQUESTED;
import static android.server.wm.app.Components.CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.ColorInt;

import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:AnimationBackgroundTests
 */
@Presubmit
@android.server.wm.annotation.Group1
public class AnimationBackgroundTests extends ActivityManagerTestBase {

    @Rule
    public final DumpOnFailure dumpOnFailure = new DumpOnFailure();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mWmState.setSanityCheckWithFocusedWindow(false);
        mWmState.waitForDisplayUnfrozen();
    }

    @After
    public void tearDown() {
        mWmState.setSanityCheckWithFocusedWindow(true);
    }

    /**
     * Checks that the activity's theme's background color is used as the default animation's
     * background color when no override is specified.
     */
    @Test
    public void testThemeBackgroundColorShowsDuringActivityTransition() {
        assumeTrue(ENABLE_SHELL_TRANSITIONS);

        launchBackgroundColorTransition(0);
        final Bitmap screen = screenshotTransition();
        assertAppRegionOfScreenIsColor(screen, Color.WHITE);
    }

    /**
     * Checks that we can override the default background color of the animation through
     * overridePendingTransition
     */
    @Test
    public void testBackgroundColorIsOverridden() {
        assumeTrue(ENABLE_SHELL_TRANSITIONS);

        launchBackgroundColorTransition(Color.GREEN);
        final Bitmap screen = screenshotTransition();
        assertAppRegionOfScreenIsColor(screen, Color.GREEN);
    }

    /**
     * @param backgroundColorOverride a background color override of 0 means we are not going to
     *                                override the background color and just fallback on the default
     *                                value
     */
    private void launchBackgroundColorTransition(int backgroundColorOverride) {
        TestJournalProvider.TestJournalContainer.start();
        final String startActivityCommand = "am start -n "
                + getActivityName(CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY) + " -f 0x18000000 "
                + "--ei backgroundColorOverride " + backgroundColorOverride;
        executeShellCommand(startActivityCommand);
        mWmState.waitForValidState(CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY);
    }

    private Bitmap screenshotTransition() {
        final TestJournalProvider.TestJournal journal = TestJournalProvider.TestJournalContainer
                .get(CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY);
        try {
            TestUtils.waitUntil("Waiting for app to request transition",
                    15 /* timeoutSecond */,
                    () -> journal.extras.getBoolean(TRANSITION_REQUESTED));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // The activity transition is set to last 5 seconds, wait half a second to make sure
        // the activity transition has started after we receive confirmation through the test
        // journal that we have requested to start a new activity.
        SystemClock.sleep(500);

        // Take a screenshot during the transition where we hide both the activities to just
        // show the background of the transition which is set to be white.
        return takeScreenshot();
    }

    private void assertAppRegionOfScreenIsColor(Bitmap screen, @ColorInt int color) {
        final List<WindowManagerState.WindowState> windows = getWmState().getWindows();
        Optional<WindowManagerState.WindowState> screenDecorOverlay =
                windows.stream().filter(
                        w -> w.getName().equals("ScreenDecorOverlay")).findFirst();
        Optional<WindowManagerState.WindowState> screenDecorOverlayBottom =
                windows.stream().filter(
                        w -> w.getName().equals("ScreenDecorOverlayBottom")).findFirst();
        getWmState().getWindowStateForAppToken("screenDecorOverlay");

        final int screenDecorOverlayHeight = screenDecorOverlay.map(
                WindowManagerState.WindowState::getRequestedHeight).orElse(0);
        final int screenDecorOverlayBottomHeight = screenDecorOverlayBottom.map(
                WindowManagerState.WindowState::getRequestedHeight).orElse(0);


        for (int x = 0; x < screen.getWidth(); x++) {
            for (int y = screenDecorOverlayHeight;
                    y < screen.getHeight() - screenDecorOverlayBottomHeight; y++) {
                final Color rawColor = screen.getColor(x, y);
                final Color sRgbColor;
                if (!rawColor.getColorSpace().equals(ColorSpace.get(ColorSpace.Named.SRGB))) {
                    // Conversion is required because the color space of the screenshot may be in
                    // the DCI-P3 color space or some other color space and we want to compare the
                    // color against once in the SRGB color space, so we must convert the color back
                    // to the SRGB color space.
                    sRgbColor = screen.getColor(x, y)
                            .convert(ColorSpace.get(ColorSpace.Named.SRGB));
                } else {
                    sRgbColor = rawColor;
                }

                final Color expectedColor = Color.valueOf(color);
                assertArrayEquals("Screen pixel (" + x + ", " + y + ") is not the right color",
                        new float[] {
                                expectedColor.red(), expectedColor.green(), expectedColor.blue() },
                        new float[] { sRgbColor.red(), sRgbColor.green(), sRgbColor.blue() },
                        0.03f); // need to allow for some variation stemming from conversions
            }
        }
    }
}
