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

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.cts.surfacevalidator.BitmapPixelChecker;
import android.view.cts.surfacevalidator.PixelColor;
import android.window.SplashScreen;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:SnapshotTaskTests
 */
public class SnapshotTaskTests {

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(TestActivity.class);

    private TestActivity mActivity;
    private WindowManager mWindowManager;
    private UiAutomation mUiAutomation;

    private static final int MATCHING_PIXEL_MISMATCH_ALLOWED = 100;

    @Before
    public void setup() throws InterruptedException {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        mWindowManager = mActivity.getSystemService(WindowManager.class);
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity();

        final KeyguardManager km = mActivity.getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked() || !Objects.requireNonNull(
                mActivity.getSystemService(PowerManager.class)).isInteractive()) {
            pressWakeupButton();
            pressUnlockButton();
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mActivity.waitUntilReady();
    }

    @After
    public void cleanup() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetDisablePreviewScreenshots() {
        BitmapPixelChecker pixelChecker = new BitmapPixelChecker(PixelColor.RED);

        Bitmap bitmap = mWindowManager.snapshotTaskForRecents(mActivity.getTaskId());
        assertNotNull(bitmap);
        int expectedMatching =
                bitmap.getWidth() * bitmap.getHeight() - MATCHING_PIXEL_MISMATCH_ALLOWED;
        Rect boundToCheck = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        int matchingPixels = pixelChecker.getNumMatchingPixels(bitmap, boundToCheck);
        assertTrue("Expected >=" + expectedMatching + " actual=" + matchingPixels,
                matchingPixels >= expectedMatching);


        mActivity.setRecentsScreenshotEnabled(false);
        bitmap = mWindowManager.snapshotTaskForRecents(mActivity.getTaskId());
        assertNotNull(bitmap);
        boundToCheck.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matchingPixels = pixelChecker.getNumMatchingPixels(bitmap, boundToCheck);
        assertTrue("Expected <=" + MATCHING_PIXEL_MISMATCH_ALLOWED + " matched " + matchingPixels,
                matchingPixels <= MATCHING_PIXEL_MISMATCH_ALLOWED);
    }

    public static class TestActivity extends Activity {
        private final CountDownLatch mReadyToStart = new CountDownLatch(2);

        @Override
        public void onEnterAnimationComplete() {
            mReadyToStart.countDown();
        }

        public void waitUntilReady() throws InterruptedException {
            mReadyToStart.await(5, TimeUnit.SECONDS);
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            View view = new View(this);
            view.setBackgroundColor(Color.RED);
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            setContentView(view, layoutParams);

            WindowInsetsController windowInsetsController = getWindow().getInsetsController();
            windowInsetsController.hide(
                    WindowInsets.Type.navigationBars() | WindowInsets.Type.statusBars());
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            getWindow().setAttributes(params);
            getWindow().setDecorFitsSystemWindows(false);

            SplashScreen splashscreen = getSplashScreen();
            splashscreen.setOnExitAnimationListener(splashView -> {
                splashView.remove();
                mReadyToStart.countDown();
            });
        }
    }
}
