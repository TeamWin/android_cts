/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.app.ActivityTaskManager.INVALID_STACK_ID;
import static android.server.wm.CliIntentExtra.extraInt;
import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.server.wm.app.Components.BAD_BLUR_ACTIVITY;
import static android.server.wm.app.Components.BLUR_ACTIVITY;
import static android.server.wm.app.Components.BLUR_ATTRIBUTES_ACTIVITY;
import static android.server.wm.app.Components.BlurActivity.EXTRA_BACKGROUND_BLUR_RADIUS_PX;
import static android.server.wm.app.Components.BlurActivity.EXTRA_BLUR_BEHIND_RADIUS_PX;
import static android.server.wm.app.Components.BlurActivity.EXTRA_NO_BLUR_BACKGROUND_COLOR;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.systemBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;
import android.view.View;
import android.view.WindowManager;

import androidx.test.uiautomator.UiDevice;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ColorUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.function.Consumer;

@Presubmit
@FlakyTest(bugId = 263872611)
public class BlurTests extends WindowManagerTestBase {
    private static final int BACKGROUND_BLUR_PX = 80;
    private static final int BLUR_BEHIND_PX = 40;
    private static final int NO_BLUR_BACKGROUND_COLOR = Color.BLACK;
    private static final int BLUR_BEHIND_DYNAMIC_UPDATE_WAIT_TIME = 300;
    private static final int BACKGROUND_BLUR_DYNAMIC_UPDATE_WAIT_TIME = 100;
    private static final int DISABLE_BLUR_BROADCAST_WAIT_TIME = 100;

    private Rect mBackgroundActivityBounds;

    private final DumpOnFailure mDumpOnFailure = new DumpOnFailure();

    private final TestRule mEnableBlurRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.DISABLE_WINDOW_BLURS),
            Settings.Global::getInt,
            Settings.Global::putInt,
            0);

    private final ActivityTestRule<BackgroundActivity> mBackgroundActivity =
            new ActivityTestRule<>(BackgroundActivity.class);

    @Rule
    public final TestRule methodRules = RuleChain.outerRule(mDumpOnFailure)
            .around(mEnableBlurRule)
            .around(mBackgroundActivity);

    @Before
    public void setUp() throws Exception {
        assumeTrue(supportsBlur());
        mBackgroundActivity.getActivity().waitAndAssertWindowFocusState(true);

        // Use the background activity's bounds when taking the device screenshot.
        // This is needed for multi-screen devices (foldables) where
        // the launched activity covers just one screen
        ComponentName cn = mBackgroundActivity.getActivity().getComponentName();
        WindowManagerState.WindowState windowState = mWmState.getWindowState(cn);
        WindowManagerState.Activity act = mWmState.getActivity(cn);
        mBackgroundActivityBounds = act.getBounds();
        Optional<WindowManagerState.InsetsSource> captionInsetsOptional =
                windowState.getMergedLocalInsetsSources().stream().filter(
                        insets -> insets.isCaptionBar()).findFirst();
        captionInsetsOptional.ifPresent(captionInsets -> {
            captionInsets.insetGivenFrame(mBackgroundActivityBounds);
        });

        // Wait for the first frame *after* the splash screen is removed to take screenshots.
        // We don't currently have a definite event / callback for this.
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        UiDevice.getInstance(getInstrumentation()).waitForIdle();
        getInstrumentation().getUiAutomation().syncInputTransactions();

        // Basic checks common to all tests
        verifyOnlyBackgroundImageVisible();
        assertTrue(mContext.getSystemService(WindowManager.class).isCrossWindowBlurEnabled());
    }

    @Test
    public void testBackgroundBlurSimple() {
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, BACKGROUND_BLUR_PX));

        final Rect windowFrame = getFloatingWindowFrame(BLUR_ACTIVITY);
        assertBackgroundBlur(takeScreenshotForBounds(mBackgroundActivityBounds), windowFrame);
    }

    @Test
    public void testBlurBehindSimple() {
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BLUR_BEHIND_RADIUS_PX, BLUR_BEHIND_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, NO_BLUR_BACKGROUND_COLOR));

        final Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        final Rect windowFrame = getFloatingWindowFrame(BLUR_ACTIVITY);
        assertBlurBehind(screenshot, windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);
    }

    @Test
    public void testNoBackgroundBlurWhenBlurDisabled() {
        setForceBlurDisabled(true);
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, BACKGROUND_BLUR_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, Color.TRANSPARENT));
        verifyOnlyBackgroundImageVisible();
    }

    @Test
    public void testNoBackgroundBlurForNonTranslucentWindow() {
        startTestActivity(BAD_BLUR_ACTIVITY,
                          extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, BACKGROUND_BLUR_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, Color.TRANSPARENT));
        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @FlakyTest(bugId = 263872611)
    public void testNoBlurBehindWhenBlurDisabled() {
        setForceBlurDisabled(true);
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BLUR_BEHIND_RADIUS_PX, BLUR_BEHIND_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, Color.TRANSPARENT));
        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @FlakyTest(bugId = 263872611)
    public void testNoBlurBehindWhenFlagNotSet() {
        startTestActivity(BAD_BLUR_ACTIVITY,
                          extraInt(EXTRA_BLUR_BEHIND_RADIUS_PX, BLUR_BEHIND_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, Color.TRANSPARENT));
        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @FlakyTest(bugId = 263872611)
    public void testBackgroundBlurActivatesFallbackDynamically() throws Exception {
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, BACKGROUND_BLUR_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, NO_BLUR_BACKGROUND_COLOR));
        final Rect windowFrame = getFloatingWindowFrame(BLUR_ACTIVITY);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        setForceBlurDisabled(true);
        Thread.sleep(BACKGROUND_BLUR_DYNAMIC_UPDATE_WAIT_TIME);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        setForceBlurDisabled(false);
        Thread.sleep(BACKGROUND_BLUR_DYNAMIC_UPDATE_WAIT_TIME);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);
    }

    @Test
    public void testBlurBehindDisabledDynamically() throws Exception {
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BLUR_BEHIND_RADIUS_PX, BLUR_BEHIND_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, NO_BLUR_BACKGROUND_COLOR));
        final Rect windowFrame = getFloatingWindowFrame(BLUR_ACTIVITY);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);

        setForceBlurDisabled(true);
        Thread.sleep(BLUR_BEHIND_DYNAMIC_UPDATE_WAIT_TIME);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        setForceBlurDisabled(false);
        Thread.sleep(BLUR_BEHIND_DYNAMIC_UPDATE_WAIT_TIME);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot,  windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);
    }

    @Test
    @FlakyTest(bugId = 263872611)
    public void testBlurBehindAndBackgroundBlur() throws Exception {
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BLUR_BEHIND_RADIUS_PX, BLUR_BEHIND_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, NO_BLUR_BACKGROUND_COLOR),
                          extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, BACKGROUND_BLUR_PX));
        final Rect windowFrame = getFloatingWindowFrame(BLUR_ACTIVITY);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);

        setForceBlurDisabled(true);
        Thread.sleep(BLUR_BEHIND_DYNAMIC_UPDATE_WAIT_TIME);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        setForceBlurDisabled(false);
        Thread.sleep(BLUR_BEHIND_DYNAMIC_UPDATE_WAIT_TIME);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
    }

    @Test
    public void testBlurBehindAndBackgroundBlurSetWithAttributes() {
        startTestActivity(BLUR_ATTRIBUTES_ACTIVITY);
        final Rect windowFrame = getFloatingWindowFrame(BLUR_ATTRIBUTES_ACTIVITY);
        final Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);

        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
    }

    @Test
    public void testBlurDestroyedAfterActivityFinished() {
        startTestActivity(BLUR_ACTIVITY,
                          extraInt(EXTRA_BLUR_BEHIND_RADIUS_PX, BLUR_BEHIND_PX),
                          extraInt(EXTRA_NO_BLUR_BACKGROUND_COLOR, NO_BLUR_BACKGROUND_COLOR),
                          extraInt(EXTRA_BACKGROUND_BLUR_RADIUS_PX, BACKGROUND_BLUR_PX));
        final Rect windowFrame = getFloatingWindowFrame(BLUR_ACTIVITY);
        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);

        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);

        mBroadcastActionTrigger.finishBroadcastReceiverActivity();
        mWmState.waitAndAssertActivityRemoved(BLUR_ACTIVITY);

        verifyOnlyBackgroundImageVisible();
    }

    @Test
    public void testIsCrossWindowBlurEnabledUpdatedCorrectly() throws Exception {
        setForceBlurDisabled(true);
        Thread.sleep(DISABLE_BLUR_BROADCAST_WAIT_TIME);
        assertFalse(mContext.getSystemService(WindowManager.class).isCrossWindowBlurEnabled());

        setForceBlurDisabled(false);
        Thread.sleep(DISABLE_BLUR_BROADCAST_WAIT_TIME);
        assertTrue(mContext.getSystemService(WindowManager.class).isCrossWindowBlurEnabled());
    }

    @Test
    public void testBlurListener() throws Exception {
        final BackgroundActivity activity = mBackgroundActivity.getActivity();
        Mockito.verify(activity.mBlurEnabledListener).accept(true);

        setForceBlurDisabled(true);
        Thread.sleep(DISABLE_BLUR_BROADCAST_WAIT_TIME);
        assertFalse(mContext.getSystemService(WindowManager.class).isCrossWindowBlurEnabled());
        Mockito.verify(activity.mBlurEnabledListener).accept(false);

        setForceBlurDisabled(false);
        Thread.sleep(DISABLE_BLUR_BROADCAST_WAIT_TIME);
        assertTrue(mContext.getSystemService(WindowManager.class).isCrossWindowBlurEnabled());
        Mockito.verify(activity.mBlurEnabledListener, times(2)).accept(true);
    }

    public static class BlurListener implements Consumer<Boolean> {
        @Override
        public void accept(Boolean enabled) {}
    }

    public static class BackgroundActivity extends FocusableActivity {

        public final Consumer<Boolean> mBlurEnabledListener = spy(new BlurListener());

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getSplashScreen().setOnExitAnimationListener(view -> view.remove());

            final View contentView = new View(this);
            contentView.addOnAttachStateChangeListener(new AttachListener());
            setContentView(contentView);

            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(systemBars());
        }

        private class AttachListener implements View.OnAttachStateChangeListener {
            @Override
            public void onViewAttachedToWindow(View view) {
                getWindowManager().addCrossWindowBlurEnabledListener(mBlurEnabledListener);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                getWindowManager().removeCrossWindowBlurEnabledListener(mBlurEnabledListener);
            }
        }
    }

    private void startTestActivity(ComponentName activityName, final CliIntentExtra... extras) {
        launchActivityWithNoAnimation(activityName, extras);
        assertNotEquals(mWmState.getRootTaskIdByActivity(activityName), INVALID_STACK_ID);
        waitAndAssertResumedActivity(activityName, activityName + " must be resumed");
        UiDevice.getInstance(getInstrumentation()).waitForIdle();
        getInstrumentation().getUiAutomation().syncInputTransactions();
    }

    private Rect getFloatingWindowFrame(ComponentName activityName) {
        String windowName = getWindowName(activityName);
        mWmState.computeState(activityName);
        Rect windowFrame =
                new Rect(mWmState.getMatchingVisibleWindowState(windowName).get(0).getFrame());
        // Offset the frame of the BlurActivity to the coordinates of
        // mBackgroundActivityBounds, because we only take the screenshot in that area.
        windowFrame.offset(-mBackgroundActivityBounds.left, -mBackgroundActivityBounds.top);
        return windowFrame;
    }

    private void verifyOnlyBackgroundImageVisible() {
        final Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        mDumpOnFailure.dumpOnFailure("verifyOnlyBackgroundImageVisible", screenshot);
        final int height = screenshot.getHeight();
        final int width = screenshot.getWidth();

        final int blueWidth = width / 2;

        final int[] row = new int[width];

        for (int y = 0; y < height; y++) {
            screenshot.getPixels(row, 0, width, 0, y, row.length, 1);
            for (int x = 0; x < width; x++) {
                final int actual = row[x];
                final int expected = (x < blueWidth ? Color.BLUE : Color.RED);

                if (actual != expected) {
                    ColorUtils.verifyColor("failed for pixel (x, y) = (" + x + ", " + y + ")",
                            expected, actual, 1);
                }
            }
        }
    }

    private void assertBlurBehind(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertBlurBehind", screenshot);
        assertBlur(screenshot, BLUR_BEHIND_PX, 0, windowFrame.top);
        assertBlur(screenshot, BLUR_BEHIND_PX, windowFrame.bottom, screenshot.getHeight());
    }

    private void assertBackgroundBlur(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertBackgroundBlur", screenshot);
        assertBlur(screenshot, BACKGROUND_BLUR_PX, windowFrame.top, windowFrame.bottom);
    }

    private void assertBackgroundBlurOverBlurBehind(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertBackgroundBlurOverBlurBehind", screenshot);
        assertBlur(
                screenshot,
                (int) Math.hypot(BACKGROUND_BLUR_PX, BLUR_BEHIND_PX),
                windowFrame.top,
                windowFrame.bottom);
    }

    private void assertNoBlurBehind(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertNoBlurBehind", screenshot);

        // Batch fetch pixels from each row of the bitmap to speed up the test.
        final int[] row = new int[screenshot.getWidth()];

        for (int y = 0; y < screenshot.getHeight(); y++) {
            screenshot.getPixels(row, 0, screenshot.getWidth(), 0, y, row.length, 1);
            for (int x = 0; x < screenshot.getWidth(); x++) {
                if (!windowFrame.contains(x, y)) {
                    final int actual = row[x];
                    final int expected = (x < screenshot.getWidth() / 2 ? Color.BLUE : Color.RED);

                    if (actual != expected) {
                        ColorUtils.verifyColor(
                               "failed for pixel (x, y) = (" + x + ", " + y + ")",
                                expected, actual, 1);
                    }
                }
            }
        }
    }

    private void assertNoBackgroundBlur(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertNoBackgroundBlur", screenshot);

        // Batch fetch pixels from each row of the bitmap to speed up the test.
        final int[] row = new int[windowFrame.width()];

        for (int y = windowFrame.top; y < windowFrame.bottom; y++) {
            screenshot.getPixels(
                    row, 0, screenshot.getWidth(), windowFrame.left, y, row.length, 1);
            for (int x = windowFrame.left; x < windowFrame.right; x++) {
                final int actual = row[x - windowFrame.left];
                final int expected = NO_BLUR_BACKGROUND_COLOR;

                if (actual != expected) {
                    ColorUtils.verifyColor("failed for pixel (x, y) = (" + x + ", " + y + ")",
                            expected, actual, 1);
                }
            }
        }
    }

    private void assertBlur(Bitmap screenshot, int blurRadius, int startHeight,
            int endHeight) {
        final int width = screenshot.getWidth();

        // Adjust the test to check a smaller part of the blurred area in order to accept
        // various blur algorithm approximations used in RenderEngine
        final int stepSize = blurRadius / 4;
        final int blurAreaStartX = width / 2 - blurRadius + stepSize;
        final int blurAreaEndX = width / 2 + blurRadius;

        // At 2 * radius there should be no visible blur effects.
        final int unaffectedBluePixelX = width / 2 - blurRadius * 2 - 1;
        final int unaffectedRedPixelX = width / 2 + blurRadius * 2 + 1;

        for (int y = startHeight; y < endHeight; y++) {
            Color previousColor = Color.valueOf(Color.BLUE);
            for (int x = blurAreaStartX; x < blurAreaEndX; x += stepSize) {
                Color currentColor = screenshot.getColor(x, y);

                if (previousColor.blue() <= currentColor.blue()) {
                    assertTrue("assertBlur failed for blue for pixel (x, y) = ("
                            + x + ", " + y + ");"
                            + " previousColor blue: " + previousColor.blue()
                            + ", currentColor blue: " + currentColor.blue()
                            , previousColor.blue() > currentColor.blue());
                }
                if (previousColor.red() >= currentColor.red()) {
                    assertTrue("assertBlur failed for red for pixel (x, y) = ("
                           + x + ", " + y + ");"
                           + " previousColor red: " + previousColor.red()
                           + ", currentColor red: " + currentColor.red(),
                           previousColor.red() < currentColor.red());
                }
                previousColor = currentColor;
            }
        }

        for (int y = startHeight; y < endHeight; y++) {
            final int unaffectedBluePixel = screenshot.getPixel(unaffectedBluePixelX, y);
            if (unaffectedBluePixel != Color.BLUE) {
                ColorUtils.verifyColor(
                        "failed for pixel (x, y) = (" + unaffectedBluePixelX + ", " + y + ")",
                        Color.BLUE, unaffectedBluePixel, 1);
            }
            final int unaffectedRedPixel = screenshot.getPixel(unaffectedRedPixelX, y);
            if (unaffectedRedPixel != Color.RED) {
                ColorUtils.verifyColor(
                        "failed for pixel (x, y) = (" + unaffectedRedPixelX + ", " + y + ")",
                        Color.RED, unaffectedRedPixel, 1);
            }
        }
    }

    private void setForceBlurDisabled(boolean disable) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DISABLE_WINDOW_BLURS, disable ? 1 : 0);
    }
}
