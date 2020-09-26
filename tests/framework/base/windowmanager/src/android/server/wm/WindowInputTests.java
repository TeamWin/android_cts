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
 * limitations under the License
 */

package android.server.wm;

import static android.server.wm.ActivityManagerTestBase.launchHomeActivityNoWait;
import static android.server.wm.BarTestUtils.assumeHasStatusBar;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;
import android.util.ArraySet;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowInputTests
 */
@Presubmit
public class WindowInputTests {
    private final int TOTAL_NUMBER_OF_CLICKS = 100;
    private final ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    private Instrumentation mInstrumentation;
    private TestActivity mActivity;
    private View mView;
    private final Random mRandom = new Random();

    private int mClickCount = 0;

    @Before
    public void setUp() {
        pressWakeupButton();
        pressUnlockButton();
        launchHomeActivityNoWait();

        mInstrumentation = getInstrumentation();
        mActivity = mActivityRule.launchActivity(null);
        mInstrumentation.waitForIdleSync();
        mClickCount = 0;
    }

    @Test
    public void testMoveWindowAndTap() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        Point displaySize = new Point();
        mActivity.getDisplay().getSize(displaySize);

        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        // Set up window.
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.width = 20;
            p.height = 20;
            p.gravity = Gravity.LEFT | Gravity.TOP;
            mView.setOnClickListener((v) -> {
                mClickCount++;
            });
            mActivity.addWindow(mView, p);
        });
        mInstrumentation.waitForIdleSync();

        WindowInsets insets = mActivity.getWindow().getDecorView().getRootWindowInsets();
        final Rect windowBounds = new Rect(insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                displaySize.x - insets.getSystemWindowInsetRight(),
                displaySize.y - insets.getSystemWindowInsetBottom());

        // Move the window to a random location in the window and attempt to tap on view multiple
        // times.
        final Point locationInWindow = new Point();
        for (int i = 0; i < TOTAL_NUMBER_OF_CLICKS; i++) {
            selectRandomLocationInWindow(windowBounds, locationInWindow);
            mActivityRule.runOnUiThread(() -> {
                p.x = locationInWindow.x;
                p.y = locationInWindow.y;
                wm.updateViewLayout(mView, p);
            });
            mInstrumentation.waitForIdleSync();
            CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        }

        assertEquals(TOTAL_NUMBER_OF_CLICKS, mClickCount);
    }

    private void selectRandomLocationInWindow(Rect bounds, Point outLocation) {
        int randomX = mRandom.nextInt(bounds.right - bounds.left) + bounds.left;
        int randomY = mRandom.nextInt(bounds.bottom - bounds.top) + bounds.top;
        outLocation.set(randomX, randomY);
    }

    @Test
    public void testTouchModalWindow() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        // Set up 2 touch modal windows, expect the last one will receive all touch events.
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.width = 20;
            p.height = 20;
            p.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            mView.setFilterTouchesWhenObscured(true);
            mView.setOnClickListener((v) -> {
                mClickCount++;
            });
            mActivity.addWindow(mView, p);

            View view2 = new View(mActivity);
            p.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            mActivity.addWindow(view2, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);
    }

    // If a window is obscured by another window from the same app (same process), touches should
    // still get delivered to the bottom window, and the FLAG_WINDOW_IS_OBSCURED should not be set.
    // We do not consider windows from the same process to be obscuring each other.
    @Test
    public void testFilterTouchesWhenWindowHasSamePid() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        // Set up a touchable window.
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
            p.width = 100;
            p.height = 100;
            p.gravity = Gravity.CENTER;
            mView.setFilterTouchesWhenObscured(true);
            mView.setOnClickListener((v) -> {
                mClickCount++;
            });
            mView.setOnTouchListener((v, ev) -> {
                assertEquals((ev.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED), 0);
                return false;
            });
            mActivity.addWindow(mView, p);

            // Set up an overlap window, use same process.
            View overlay = new View(mActivity);
            p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_TOUCHABLE;
            p.width = 100;
            p.height = 100;
            p.gravity = Gravity.CENTER;
            p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            mActivity.addWindow(overlay, p);
        });
        mInstrumentation.waitForIdleSync();
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

        assertEquals(1, mClickCount);
    }

    @Test
    public void testFilterTouchesWhenObscured() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Intent intent = new Intent(mActivity, TestService.class);
        final String windowName = "Test Overlay";
        try {
            // Set up a touchable window.
            mActivityRule.runOnUiThread(() -> {
                mView = new View(mActivity);
                p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
                p.width = 100;
                p.height = 100;
                p.gravity = Gravity.CENTER;
                mView.setFilterTouchesWhenObscured(true);
                mView.setOnClickListener((v) -> {
                    mClickCount++;
                });
                mView.setOnTouchListener((v, ev) -> {
                    assertEquals((ev.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED),
                            MotionEvent.FLAG_WINDOW_IS_OBSCURED);
                    return false;
                });
                mActivity.addWindow(mView, p);

                // Set up an overlap window from service, use different process.
                intent.putExtra(TestService.EXTRA_WINDOW_NAME, windowName);
                mActivity.startService(intent);
            });
            mInstrumentation.waitForIdleSync();

            final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
            wmState.waitForWithAmState(state -> {
                return state.isWindowSurfaceShown(windowName);
            }, windowName + "'s surface is appeared");

            CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertEquals(0, mClickCount);
        } finally {
            mActivity.stopService(intent);
        }
    }

    @Test
    public void testTrustedOverlapWindow() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        try (final PointerLocationSession session = new PointerLocationSession()) {
            session.set(true);
            session.waitForReady(mActivity.getDisplayId());

            // Set up window.
            mActivityRule.runOnUiThread(() -> {
                mView = new View(mActivity);
                p.width = 20;
                p.height = 20;
                p.gravity = Gravity.CENTER;
                mView.setFilterTouchesWhenObscured(true);
                mView.setOnClickListener((v) -> {
                    mClickCount++;
                });
                mActivity.addWindow(mView, p);

            });
            mInstrumentation.waitForIdleSync();

            CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        }
        assertEquals(1, mClickCount);
    }

    @Test
    public void testWindowBecomesUnTouchable() throws Throwable {
        final WindowManager wm = mActivity.getWindowManager();
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final View viewOverlap = new View(mActivity);

        // Set up window.
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.width = 20;
            p.height = 20;
            p.gravity = Gravity.CENTER;
            mView.setOnClickListener((v) -> {
                mClickCount++;
            });
            mActivity.addWindow(mView, p);

            p.width = 100;
            p.height = 100;
            p.gravity = Gravity.CENTER;
            p.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            mActivity.addWindow(viewOverlap, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);

        mActivityRule.runOnUiThread(() -> {
            p.flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE;
            wm.updateViewLayout(viewOverlap, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(1, mClickCount);
    }

    @Test
    public void testTapInsideUntouchableWindowResultInOutsideTouches() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        final Set<MotionEvent> events = new ArraySet<>();
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.width = 20;
            p.height = 20;
            p.gravity = Gravity.CENTER;
            p.flags = FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
            mView.setOnTouchListener((v, e) -> {
                // Copying to make sure we are not dealing with a reused object
                events.add(MotionEvent.obtain(e));
                return false;
            });
            mActivity.addWindow(mView, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

        assertEquals(1, events.size());
        MotionEvent event = events.iterator().next();
        assertEquals(MotionEvent.ACTION_OUTSIDE, event.getAction());
    }

    @Test
    public void testTapOutsideUntouchableWindowResultInOutsideTouches() throws Throwable {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();

        Set<MotionEvent> events = new ArraySet<>();
        int size = 20;
        mActivityRule.runOnUiThread(() -> {
            mView = new View(mActivity);
            p.width = size;
            p.height = size;
            p.gravity = Gravity.CENTER;
            p.flags = FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
            mView.setOnTouchListener((v, e) -> {
                // Copying to make sure we are not dealing with a reused object
                events.add(MotionEvent.obtain(e));
                return false;
            });
            mActivity.addWindow(mView, p);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnView(mInstrumentation, mActivityRule, mView, size + 5, size + 5);

        assertEquals(1, events.size());
        MotionEvent event = events.iterator().next();
        assertEquals(MotionEvent.ACTION_OUTSIDE, event.getAction());
    }

    @Test
    public void testInjectToStatusBar() {
        // Try to inject event to status bar.
        assumeHasStatusBar(mActivityRule);
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent eventHover = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_HOVER_MOVE, 0, 0, 0);
        eventHover.setSource(InputDevice.SOURCE_MOUSE);
        try {
            mInstrumentation.sendPointerSync(eventHover);
            fail("Not allowed to inject event to the window from another process.");
        } catch (SecurityException e) {
            // Should not be allowed to inject event to the window from another process.
        }
    }

    @Test
    public void testInjectFromThread() throws InterruptedException {
        // Continually inject event to activity from thread.
        final Point displaySize = new Point();
        mActivity.getDisplay().getSize(displaySize);
        final Point testPoint = new Point(displaySize.x / 2, displaySize.y / 2);

        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, testPoint.x, testPoint.y, 1);
        mInstrumentation.sendPointerSync(eventDown);

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            mInstrumentation.sendPointerSync(eventDown);
            for (int i = 0; i < 20; i++) {
                final long eventTime = SystemClock.uptimeMillis();
                final MotionEvent eventMove = MotionEvent.obtain(
                        downTime, eventTime, MotionEvent.ACTION_MOVE, testPoint.x, testPoint.y, 1);
                try {
                    mInstrumentation.sendPointerSync(eventMove);
                } catch (SecurityException e) {
                    fail("Should be allowed to inject event.");
                }
            }
        });

        // Launch another activity, should not crash the process.
        final Intent intent = new Intent(mActivity, TestActivity.class);
        mActivityRule.launchActivity(intent);
        mInstrumentation.waitForIdleSync();

        executor.shutdown();
        executor.awaitTermination(5L, TimeUnit.SECONDS);
    }

    public static class TestActivity extends Activity {
        private ArrayList<View> mViews = new ArrayList<>();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        void addWindow(View view, WindowManager.LayoutParams attrs) {
            getWindowManager().addView(view, attrs);
            mViews.add(view);
        }

        void removeAllWindows() {
            for (View view : mViews) {
                getWindowManager().removeViewImmediate(view);
            }
            mViews.clear();
        }

        @Override
        protected void onPause() {
            super.onPause();
            removeAllWindows();
        }
    }

    public static class TestService extends Service {
        private static final String EXTRA_WINDOW_NAME = "WINDOW_NAME";
        private WindowManager mWindowManager;
        private View mView;

        @Override
        public void onCreate() {
            super.onCreate();
            mWindowManager = getSystemService(WindowManager.class);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent != null && intent.hasExtra(EXTRA_WINDOW_NAME)) {
                addWindow(intent.getStringExtra(EXTRA_WINDOW_NAME));
            }
            return START_NOT_STICKY;
        }

        private void addWindow(final String windowName) {
            mView = new View(this);
            mView.setBackgroundColor(Color.RED);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams();
            p.setTitle(windowName);
            p.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_TOUCHABLE;
            p.width = 100;
            p.height = 100;
            p.alpha = 0.5f;
            p.gravity = Gravity.CENTER;
            p.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            mWindowManager.addView(mView, p);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mView != null) {
                mWindowManager.removeViewImmediate(mView);
                mView = null;
            }
        }
    }

    /** Helper class to save, set, and restore pointer location preferences. */
    private static class PointerLocationSession extends SettingsSession<Boolean> {
        PointerLocationSession() {
            super(Settings.System.getUriFor("pointer_location" /* POINTER_LOCATION */),
                    PointerLocationSession::get,
                    PointerLocationSession::put);
        }

        private static void put(ContentResolver contentResolver, String s, boolean v) {
            SystemUtil.runShellCommand(
                    "settings put system " + "pointer_location" + " " + (v ? 1 : 0));
        }

        private static boolean get(ContentResolver contentResolver, String s) {
            try {
                return Integer.parseInt(SystemUtil.runShellCommand(
                        "settings get system " + "pointer_location").trim()) == 1;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Wait until pointer location surface shown.
        static void waitForReady(int displayId) {
            final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
            final String windowName = "PointerLocation - display " + displayId;
            wmState.waitForWithAmState(state -> {
                return state.isWindowSurfaceShown(windowName);
            }, windowName + "'s surface is appeared");
        }
    }
}
