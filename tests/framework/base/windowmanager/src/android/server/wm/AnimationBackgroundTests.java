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

import static android.server.wm.app.Components.BackgroundActivityTransition.TRANSITION_REQUESTED;
import static android.server.wm.app.Components.CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;

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

    @Test
    public void testBackgroundColorShowsDuringActivityTransition() {
        assumeTrue(ENABLE_SHELL_TRANSITIONS);

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

        TestJournalProvider.TestJournalContainer.start();
        final TestJournalProvider.TestJournal journal = TestJournalProvider.TestJournalContainer
                .get(CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY);
        launchActivityInNewTask(CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY);

        try {
            TestUtils.waitUntil("Waiting for app to complete work", 15 /* timeoutSecond */,
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
        final Bitmap screenshot = takeScreenshot();
        final float[] white = new float[] {1, 1, 1};
        for (int x = 0; x < screenshot.getWidth(); x++) {
            for (int y = screenDecorOverlayHeight;
                    y < screenshot.getHeight() - screenDecorOverlayBottomHeight; y++) {
                final Color c = screenshot.getColor(x, y);
                assertArrayEquals("Transition Background pixel (" + x + ", " + y + ") is not white",
                        white, new float[] {c.red(), c.green(), c.blue()}, 0);
            }
        }
    }
}
