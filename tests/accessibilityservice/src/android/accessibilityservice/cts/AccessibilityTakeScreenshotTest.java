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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test cases for accessibility service takeScreenshot API.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityTakeScreenshotTest {
    /**
     * The timeout for waiting screenshot had been taken done.
     */
    private static final long TIMEOUT_TAKE_SCREENSHOT_DONE_MILLIS = 1000;

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
    @Mock
    private TakeScreenshotCallback mCallback;
    @Captor
    private ArgumentCaptor<ScreenshotResult> mSuccessResultArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
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
        takeScreenshot();
        verify(mCallback, timeout(TIMEOUT_TAKE_SCREENSHOT_DONE_MILLIS)).onSuccess(
                mSuccessResultArgumentCaptor.capture());

        verifyScreenshotResult(mSuccessResultArgumentCaptor.getValue());
    }

    @Test
    public void testTakeScreenshot_RequestIntervalTime() throws Exception {
        takeScreenshot();
        verify(mCallback, timeout(TIMEOUT_TAKE_SCREENSHOT_DONE_MILLIS)).onSuccess(
                mSuccessResultArgumentCaptor.capture());

        Thread.sleep(
                AccessibilityService.ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS / 2);
        // Requests the API again during interval time from calling the first time.
        takeScreenshot();
        verify(mCallback, timeout(TIMEOUT_TAKE_SCREENSHOT_DONE_MILLIS)).onFailure(
                AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT);

        Thread.sleep(
                AccessibilityService.ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS / 2 +
                        1);
        // Requests the API again after interval time from calling the first time.
        takeScreenshot();
        verify(mCallback, timeout(TIMEOUT_TAKE_SCREENSHOT_DONE_MILLIS)).onSuccess(
                mSuccessResultArgumentCaptor.capture());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTakeScreenshotWithNonDefaultDisplay_GetIllegalArgumentException() {
        // DisplayId isn't the default display, should throw illegalArgument exception.
        mService.takeScreenshot(Display.DEFAULT_DISPLAY + 1,
                mContext.getMainExecutor(), mCallback);
    }

    private void takeScreenshot() {
        mStartTestingTime = SystemClock.uptimeMillis();
        mService.takeScreenshot(Display.DEFAULT_DISPLAY, mContext.getMainExecutor(),
                mCallback);
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
}
