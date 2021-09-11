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
 * limitations under the License.
 */

package android.display.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DefaultDisplayModeTest {
    private final static int DISPLAY_CHANGE_TIMEOUT_SECS = 3;

    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;
    private Display.Mode mOriginalDisplayModeSettings;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Manifest.permission.HDMI_CEC);

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue("Need an Android TV device to run this test.", FeatureUtil.isTV());
        assertTrue("Physical display is expected.", DisplayUtil.isDisplayConnected(context));

        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDefaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        cacheOriginalUserPreferredModeSetting();
        mDisplayManager.clearUserPreferredDisplayMode();
    }

    @After
    public void tearDown() throws Exception {
        restoreOriginalDisplayModeSettings();
    }

    @Test
    public void testSetUserPreferredDisplayModeThrowsExceptionWithInvalidMode() {
        assertThrows(
                "The mode is invalid. Width, height and refresh rate should be positive.",
                IllegalArgumentException.class,
                () -> mDisplayManager.setUserPreferredDisplayMode(
                        new Display.Mode(-1, 1080, 120.0f)));

        assertThrows(
                "The mode is invalid. Width, height and refresh rate should be positive.",
                IllegalArgumentException.class,
                () -> mDisplayManager.setUserPreferredDisplayMode(
                        new Display.Mode(720, 1080, 0.0f)));
    }

    @Test
    public void testDisplayChangedOnSetAndClearUserPreferredDisplayMode() throws Exception {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Test set
        Display.Mode initialDefaultMode = mDefaultDisplay.getDefaultMode();

        Display.Mode newDefaultMode = findNonDefaultMode(mDefaultDisplay);
        assertNotNull(newDefaultMode);
        DefaultModeListener listener =
                new DefaultModeListener(mDefaultDisplay, newDefaultMode.getModeId());
        Handler handler = new Handler(Looper.getMainLooper());
        mDisplayManager.registerDisplayListener(listener, handler);
        try {
            mDisplayManager.setUserPreferredDisplayMode(newDefaultMode);
            assertTrue(listener.await());
        } finally {
            mDisplayManager.unregisterDisplayListener(listener);
        }

        // Test clear
        listener = new DefaultModeListener(mDefaultDisplay, initialDefaultMode.getModeId());
        mDisplayManager.registerDisplayListener(listener, handler);
        try {
            mDisplayManager.clearUserPreferredDisplayMode();
            assertTrue(listener.await());
        } finally {
            mDisplayManager.unregisterDisplayListener(listener);
        }
    }

    @Test
    public void testGetUserPreferredDisplayMode() {
        Display.Mode[] modes = mDefaultDisplay.getSupportedModes();
        assumeTrue("Need two or more display modes to exercise switching.", modes.length > 1);

        // Set a display mode which is different from default display mode
        Display.Mode newDefaultMode = findNonDefaultMode(mDefaultDisplay);
        assertNotNull(newDefaultMode);
        mDisplayManager.setUserPreferredDisplayMode(newDefaultMode);
        assertTrue(mDisplayManager.getUserPreferredDisplayMode()
                .matches(newDefaultMode.getPhysicalWidth(),
                        newDefaultMode.getPhysicalHeight(),
                        newDefaultMode.getRefreshRate()));

        mDisplayManager.clearUserPreferredDisplayMode();
        assertNull(mDisplayManager.getUserPreferredDisplayMode());
    }

    private void cacheOriginalUserPreferredModeSetting() {
        mOriginalDisplayModeSettings = mDisplayManager.getUserPreferredDisplayMode();
    }

    private void restoreOriginalDisplayModeSettings() {
        // mDisplayManager can be null if the test assumptions if setUp have failed.
        if (mDisplayManager == null) {
            return;
        }
        if (mOriginalDisplayModeSettings == null) {
            mDisplayManager.clearUserPreferredDisplayMode();
        } else {
            mDisplayManager.setUserPreferredDisplayMode(mOriginalDisplayModeSettings);
        }
    }

    private Display.Mode findNonDefaultMode(Display display) {
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getModeId() != display.getDefaultMode().getModeId()) {
                return mode;
            }
        }
        return null;
    }

    private static class DefaultModeListener implements DisplayManager.DisplayListener {
        private final Display mDisplay;
        private final int mAwaitedDefaultModeId;
        private final CountDownLatch mLatch;

        private DefaultModeListener(Display display, int awaitedDefaultModeId) {
            mDisplay = display;
            mAwaitedDefaultModeId = awaitedDefaultModeId;
            mLatch = new CountDownLatch(1);
        }

        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != mDisplay.getDisplayId()) {
                return;
            }

            if (mAwaitedDefaultModeId == mDisplay.getDefaultMode().getModeId()) {
                mLatch.countDown();
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {}

        public boolean await() throws InterruptedException {
            return mLatch.await(DISPLAY_CHANGE_TIMEOUT_SECS, TimeUnit.SECONDS);
        }
    };
}
