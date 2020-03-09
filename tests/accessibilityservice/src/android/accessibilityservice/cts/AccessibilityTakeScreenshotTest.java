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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Point;
import android.hardware.HardwareBuffer;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Test cases for accessibility service takeScreenshot API.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityTakeScreenshotTest {
    private InstrumentedAccessibilityServiceTestRule<StubTakeScreenshotService> mServiceRule =
            new InstrumentedAccessibilityServiceTestRule<>(StubTakeScreenshotService.class);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mServiceRule)
            .around(mDumpOnFailureRule);

    private StubTakeScreenshotService mService;
    private Context mContext;
    private Point mDisplaySize;
    private long mStartTestingTime;

    @Before
    public void setUp() throws Exception {
        mService = mServiceRule.getService();
        mContext = mService.getApplicationContext();

        WindowManager windowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();

        mDisplaySize = new Point();
        display.getRealSize(mDisplaySize);
    }

    @Test
    public void testTakeScreenshot_GetScreenshotResult() {
        mStartTestingTime = SystemClock.uptimeMillis();
        Consumer<AccessibilityService.ScreenshotResult> screenshotConsumer =
                new TakeScreenshotConsumer();
        mService.takeScreenshot(Display.DEFAULT_DISPLAY, mContext.getMainExecutor(),
                screenshotConsumer);
    }

    @Test
    public void testTakeScreenshot_RequestIntervalTime() throws Exception {
        final Consumer callback = mock(Consumer.class);
        assertTrue(mService.takeScreenshot(Display.DEFAULT_DISPLAY, mContext.getMainExecutor(),
                callback));
        Thread.sleep(
                AccessibilityService.ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS / 2);
        // Requests the API again during interval time from calling the first time.
        assertFalse(mService.takeScreenshot(Display.DEFAULT_DISPLAY, mContext.getMainExecutor(),
                callback));
        Thread.sleep(
                AccessibilityService.ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS / 2 +
                        1);
        // Requests the API again after interval time from calling the first time.
        assertTrue(mService.takeScreenshot(Display.DEFAULT_DISPLAY, mContext.getMainExecutor(),
                callback));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTakeScreenshotWithNonDefaultDisplay_GetIllegalArgumentException() {
        final Consumer callback = mock(Consumer.class);
        // DisplayId isn't the default display, should throw illegalArgument exception.
        mService.takeScreenshot(Display.DEFAULT_DISPLAY + 1,
                mContext.getMainExecutor(), callback);
    }

    private void verifyScreenshotResult(AccessibilityService.ScreenshotResult screenshot) {
        assertNotNull(screenshot);
        final HardwareBuffer hardwareBuffer = screenshot.getHardwareBuffer();
        assertEquals(mDisplaySize.x, hardwareBuffer.getWidth());
        assertEquals(mDisplaySize.y, hardwareBuffer.getHeight());

        // The colorSpace should not be null for taking the screenshot case.
        final ColorSpace colorSpace = screenshot.getColorSpace();
        assertNotNull(colorSpace);

        final long finishTestingTime = screenshot.getTimestamp();
        assertTrue(finishTestingTime > mStartTestingTime);

        // The bitmap should not be null for ScreenshotResult's payload.
        final Bitmap bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
        assertNotNull(bitmap);
    }

    class TakeScreenshotConsumer implements Consumer<AccessibilityService.ScreenshotResult> {
        public void accept(AccessibilityService.ScreenshotResult screenshot) {
            verifyScreenshotResult(screenshot);
        }
    }
}
