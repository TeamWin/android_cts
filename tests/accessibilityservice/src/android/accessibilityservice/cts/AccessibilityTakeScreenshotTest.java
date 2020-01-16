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
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.cts.activities.AccessibilityScreenshotActivity;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Verify the screenshot by takeScreenshot API is correct.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityTakeScreenshotTest {
    static final String TAG = "AccessibilityTakeScreenshotTest";
    /**
     * The timeout for waiting the view had been drawn done on the screen.
     */
    private static final long TIMEOUT_VIEW_DRAW_DONE_MILLIS = 200;

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private Activity mActivity;
    private Point mDisplaySize;
    private ImageView mImageView;

    private ActivityTestRule<AccessibilityScreenshotActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityScreenshotActivity.class, false, false);

    InstrumentedAccessibilityService mService;

    private InstrumentedAccessibilityServiceTestRule<StubTakeScreenshotService>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubTakeScreenshotService.class);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
    }

    @AfterClass
    public static void finalTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);

        final Display display = mActivity.getWindowManager().getDefaultDisplay();
        mDisplaySize = new Point();
        display.getRealSize(mDisplaySize);
    }

    @After
    public void tearDown() throws Exception {
        if (mActivity != null && mImageView != null) {
            sInstrumentation.runOnMainSync(
                    () -> mActivity.getWindowManager().removeView(mImageView));
        }
    }

    @Test
    public void testTakeScreenshot_GetDefaultColorScreenshot() throws Throwable {
        pollingCheckScreenshotWithColor(AccessibilityScreenshotActivity.DEFAULT_COLOR);
    }

    @Test
    public void testTakeScreenshotWithNormalWindow_GetContrastingColorScreenshot()
            throws Throwable {
        putSolidColorWindowOnScreen(AccessibilityScreenshotActivity.CONTRASTING_COLOR, 0);
        pollingCheckScreenshotWithColor(AccessibilityScreenshotActivity.CONTRASTING_COLOR);
    }

    @Test
    public void testTakeScreenshotWithSecureWindow_GetSecureWindowColorScreenshot()
            throws Throwable {
        putSolidColorWindowOnScreen(AccessibilityScreenshotActivity.CONTRASTING_COLOR,
                WindowManager.LayoutParams.FLAG_SECURE);
        pollingCheckScreenshotWithColor(AccessibilityScreenshotActivity.SECUREWINDOW_COLOR);
    }

    @Test
    public void testTakeScreenshotWithCallback_GetCallbackReturn() {
        mService = mServiceRule.enableService();
        Consumer callback = mock(Consumer.class);
        assertTrue(mService.takeScreenshot(Display.DEFAULT_DISPLAY,
                sInstrumentation.getContext().getMainExecutor(), callback));
        verify(callback, timeout(TIMEOUT_VIEW_DRAW_DONE_MILLIS).atLeastOnce())
                .accept(any());
        mService.disableSelf();
    }

    private void putSolidColorWindowOnScreen(int color, int paramsFlags) throws Throwable {
        mImageView = new ImageView(mActivity);
        mImageView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        mImageView.setImageDrawable(new ColorDrawable(color));
        assertNotNull(mImageView);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | paramsFlags;

        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                () -> {
                    mActivity.getWindowManager().addView(mImageView, params);
                }),
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ADDED),
                DEFAULT_TIMEOUT_MS);
    }

    private Point findPointNotMatchingColor(Bitmap screenshot, int color) {
        final Point misMatchPosition = new Point();
        if (screenshot.getWidth() != mDisplaySize.x || screenshot.getHeight() != mDisplaySize.y) {
            Log.e(TAG, "width or height didn't match: " + mDisplaySize + " vs "
                    + screenshot.getWidth() + "," + screenshot.getHeight());
            // Using position (display.width, display.height) indicates this error.
            misMatchPosition.x = mDisplaySize.x;
            misMatchPosition.y = mDisplaySize.y;
            return misMatchPosition;
        }

        final int[] pixels = new int[mDisplaySize.x * mDisplaySize.y];
        screenshot.getPixels(pixels, 0, mDisplaySize.x, 0, 0, mDisplaySize.x,
                mDisplaySize.y);

        int misMatchIndex = 0;
        for (int pixel : pixels) {
            if ((Color.red(pixel) != Color.red(color))
                    || (Color.green(pixel) != Color.green(color))
                    || (Color.blue(pixel) != Color.blue(color))) {
                misMatchPosition.x = misMatchIndex % mDisplaySize.x;
                misMatchPosition.y = misMatchIndex / mDisplaySize.x;
                Log.e(TAG, "screenshot color mismatch at position (" + misMatchPosition.x + ","
                        + misMatchPosition.y + ")");
                return misMatchPosition;
            }
            misMatchIndex ++;
        }

        return null;
    }

    private void pollingCheckScreenshotWithColor(int color) throws Throwable {
        final StringBuilder failureMessage = new StringBuilder();
        PollingCheck.check(failureMessage, TIMEOUT_VIEW_DRAW_DONE_MILLIS,
                new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        final Bitmap screenshot = sUiAutomation.takeScreenshot();
                        final Point misMatchPosition = findPointNotMatchingColor(screenshot, color);
                        if (misMatchPosition == null) {
                            return true;
                        } else {
                            failureMessage.setLength(0);
                            failureMessage.append("Screenshot mismatch at " + misMatchPosition);
                            return false;
                        }
                    }
                });
    }
}
