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

import static android.server.wm.app.Components.TestInteractiveLiveWallpaperKeys.COMPONENT;
import static android.server.wm.app.Components.TestInteractiveLiveWallpaperKeys.LAST_RECEIVED_MOTION_EVENT;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.WallpaperManager;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.app.Components;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.test.core.app.ActivityScenario;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;


/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:WallpaperWindowInputTests
 */
@Presubmit
public class WallpaperWindowInputTests {
    private static final String TAG = "WallpaperWindowInputTests";

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private MotionEvent mLastMotionEvent;

    @Before
    public void setUp() {
        mInstrumentation = getInstrumentation();
        ActivityScenario.launch(Activity.class).onActivity((activity) -> mActivity = activity);
        mInstrumentation.waitForIdleSync();

        SystemUtil.runWithShellPermissionIdentity(() ->
                WallpaperManager.getInstance(mActivity).setWallpaperComponent(
                        Components.TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE));
    }

    @Test
    public void testShowWallpaper_withTouchEnabled() {
        // Set up wallpaper in window.
        mActivity.runOnUiThread(() -> {
            WindowManager.LayoutParams p = mActivity.getWindow().getAttributes();
            p.flags |= FLAG_SHOW_WALLPAPER;
            mActivity.getWindow().setAttributes(p);
        });
        mInstrumentation.waitForIdleSync();
        TestJournalProvider.TestJournalContainer.start();
        MotionEvent motionEvent = getDownEventForViewCenter(mActivity.getWindow().getDecorView());
        mInstrumentation.getUiAutomation().injectInputEvent(motionEvent, true, true);

        PollingCheck.waitFor(2000 /* timeout */, this::updateLastMotionEventFromTestJournal,
                "Waiting for wallpaper to receive the touch events");

        assertNotNull(mLastMotionEvent);
        assertMotionEvent(mLastMotionEvent, motionEvent);
    }

    @Test
    public void testShowWallpaper_withWallpaperTouchDisabled() {
        // Set up wallpaper in window.
        mActivity.runOnUiThread(() -> {
            WindowManager.LayoutParams p = mActivity.getWindow().getAttributes();
            p.flags |= FLAG_SHOW_WALLPAPER;
            p.setWallpaperTouchEventsEnabled(false /* enable */);
            mActivity.getWindow().setAttributes(p);
        });
        mInstrumentation.waitForIdleSync();

        TestJournalProvider.TestJournalContainer.start();
        MotionEvent motionEvent = getDownEventForViewCenter(mActivity.getWindow().getDecorView());
        mInstrumentation.getUiAutomation().injectInputEvent(motionEvent, true, true);

        final String failMsg = "Waiting for wallpaper to receive the touch events";
        Throwable exception = assertThrows(AssertionFailedError.class,
                () -> PollingCheck.waitFor(2000 /* timeout */,
                        this::updateLastMotionEventFromTestJournal,
                        "Waiting for wallpaper to receive the touch events"));
        assertEquals(failMsg, exception.getMessage());
    }

    private boolean updateLastMotionEventFromTestJournal() {
        TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(COMPONENT);
        TestJournalProvider.TestJournalContainer.withThreadSafeAccess(() -> {
            mLastMotionEvent = journal.extras.containsKey(LAST_RECEIVED_MOTION_EVENT)
                    ? journal.extras.getParcelable(LAST_RECEIVED_MOTION_EVENT,
                    MotionEvent.class) : null;
        });
        return mLastMotionEvent != null;
    }

    private static MotionEvent getDownEventForViewCenter(View view) {
        // Get anchor coordinates on the screen
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + view.getWidth() / 2;
        int yOnScreen = viewOnScreenXY[1] + view.getHeight() / 2;
        final long downTime = SystemClock.uptimeMillis();

        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, 1);
        eventDown.setSource(InputDevice.SOURCE_TOUCHSCREEN);

        return eventDown;
    }

    private static void assertMotionEvent(MotionEvent event, MotionEvent expectedEvent) {
        assertEquals(TAG + "(action)", event.getAction(), expectedEvent.getAction());
        assertEquals(TAG + "(source)", event.getSource(), expectedEvent.getSource());
        assertEquals(TAG + "(down time)", event.getDownTime(), expectedEvent.getDownTime());
        assertEquals(TAG + "(event time)", event.getEventTime(), expectedEvent.getEventTime());
        assertEquals(TAG + "(position x)", event.getX(), expectedEvent.getX(), 0.01F);
        assertEquals(TAG + "(position y)", event.getY(), expectedEvent.getY(), 0.01F);
    }
}
