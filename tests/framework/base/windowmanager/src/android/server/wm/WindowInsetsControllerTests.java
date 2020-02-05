/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_SWIPE;
import static android.view.WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_TOUCH;
import static android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assume.assumeTrue;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsets.Type;
import android.view.WindowInsetsAnimationCallback;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Test whether WindowInsetsController controls window insets as expected.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowInsetsControllerTests
 */
public class WindowInsetsControllerTests extends WindowManagerTestBase {

    private final static long TIMEOUT = 1000; // milliseconds
    private final static AnimationCallback ANIMATION_CALLBACK = new AnimationCallback();

    @Ignore("Disabled until insets flag is flipped")
    @Test
    public void testHide() {
        final TestActivity activity = startActivity(TestActivity.class);
        final View rootView = activity.getWindow().getDecorView();

        testHideInternal(rootView, Type.statusBars());
        testHideInternal(rootView, Type.navigationBars());
    }

    private void testHideInternal(View rootView, int types) {
        if (rootView.getRootWindowInsets().isVisible(types)) {
            getInstrumentation().runOnMainSync(() -> {
                rootView.getWindowInsetsController().hide(types);
            });
            PollingCheck.waitFor(TIMEOUT, () -> !rootView.getRootWindowInsets().isVisible(types));
        }
    }

    @Ignore("Disabled until insets flag is flipped")
    @Test
    public void testShow() {
        final TestActivity activity = startActivity(TestActivity.class);
        final View rootView = activity.getWindow().getDecorView();

        testShowInternal(rootView, Type.statusBars());
        testShowInternal(rootView, Type.navigationBars());
    }

    private void testShowInternal(View rootView, int types) {
        if (rootView.getRootWindowInsets().isVisible(types)) {
            getInstrumentation().runOnMainSync(() -> {
                rootView.getWindowInsetsController().hide(types);
            });
            PollingCheck.waitFor(TIMEOUT, () -> !rootView.getRootWindowInsets().isVisible(types));
            getInstrumentation().runOnMainSync(() -> {
                rootView.getWindowInsetsController().show(types);
            });
            PollingCheck.waitFor(TIMEOUT, () -> rootView.getRootWindowInsets().isVisible(types));
        }
    }

    @Ignore("Disabled until insets flag is flipped")
    @Test
    public void testSetSystemBarsBehavior_showBarsByTouch() throws InterruptedException {
        final TestActivity activity = startActivity(TestActivity.class);
        final View rootView = activity.getWindow().getDecorView();

        // The show-by-touch behavior will only be applied while navigation bars get hidden.
        final int types = Type.navigationBars();
        assumeTrue(rootView.getRootWindowInsets().isVisible(types));

        rootView.getWindowInsetsController().setSystemBarsBehavior(BEHAVIOR_SHOW_BARS_BY_TOUCH);

        hideInsets(rootView, types);

        // Touching on display can show bars.
        tapOnDisplay(rootView.getWidth() / 2f, rootView.getHeight() / 2f);
        PollingCheck.waitFor(TIMEOUT, () -> rootView.getRootWindowInsets().isVisible(types));
    }

    @Ignore("Disabled until insets flag is flipped")
    @Test
    public void testSetSystemBarsBehavior_showBarsBySwipe() throws InterruptedException {
        final TestActivity activity = startActivity(TestActivity.class);
        final View rootView = activity.getWindow().getDecorView();

        // Assume we have the bars and they can be visible.
        final int types = Type.statusBars();
        assumeTrue(rootView.getRootWindowInsets().isVisible(types));

        rootView.getWindowInsetsController().setSystemBarsBehavior(BEHAVIOR_SHOW_BARS_BY_SWIPE);

        hideInsets(rootView, types);

        // Tapping on display cannot show bars.
        tapOnDisplay(rootView.getWidth() / 2f, rootView.getHeight() / 2f);
        PollingCheck.waitFor(TIMEOUT, () -> !rootView.getRootWindowInsets().isVisible(types));

        // Swiping from top of display can show bars.
        dragOnDisplay(rootView.getWidth() / 2f, 0 /* downY */,
                rootView.getWidth() / 2f, rootView.getHeight() / 2f);
        PollingCheck.waitFor(TIMEOUT, () -> rootView.getRootWindowInsets().isVisible(types));
    }

    @Ignore("Disabled until insets flag is flipped")
    @Test
    public void testSetSystemBarsBehavior_showTransientBarsBySwipe() throws InterruptedException {
        final TestActivity activity = startActivity(TestActivity.class);
        final View rootView = activity.getWindow().getDecorView();

        // Assume we have the bars and they can be visible.
        final int types = Type.statusBars();
        assumeTrue(rootView.getRootWindowInsets().isVisible(types));

        rootView.getWindowInsetsController().setSystemBarsBehavior(
                BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        hideInsets(rootView, types);

        // Tapping on display cannot show bars.
        tapOnDisplay(rootView.getWidth() / 2f, rootView.getHeight() / 2f);
        PollingCheck.waitFor(TIMEOUT, () -> !rootView.getRootWindowInsets().isVisible(types));

        // Swiping from top of display can show transient bars, but apps cannot detect that.
        dragOnDisplay(rootView.getWidth() / 2f, 0 /* downY */,
                rootView.getWidth() / 2f, rootView.getHeight() /2f);
        PollingCheck.waitFor(TIMEOUT, () -> !rootView.getRootWindowInsets().isVisible(types));
    }

    private static void hideInsets(View view, int types) throws InterruptedException {
        ANIMATION_CALLBACK.reset();
        getInstrumentation().runOnMainSync(() -> {
            view.setWindowInsetsAnimationCallback(ANIMATION_CALLBACK);
            view.getWindowInsetsController().hide(types);
        });
        ANIMATION_CALLBACK.waitForFinishing(TIMEOUT);
        PollingCheck.waitFor(TIMEOUT, () -> !view.getRootWindowInsets().isVisible(types));
    }

    private void tapOnDisplay(float x, float y) {
        dragOnDisplay(x, y, x, y);
    }

    private void dragOnDisplay(float downX, float downY, float upX, float upY) {
        final long downTime = SystemClock.elapsedRealtime();

        // down event
        MotionEvent event = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                downX, downY, 0 /* metaState */);
        sendPointerSync(event);
        event.recycle();

        // move event
        event = MotionEvent.obtain(downTime, downTime + 1, MotionEvent.ACTION_MOVE,
                (downX + upX) / 2f, (downY + upY) / 2f, 0 /* metaState */);
        sendPointerSync(event);
        event.recycle();

        // up event
        event = MotionEvent.obtain(downTime, downTime + 2, MotionEvent.ACTION_UP,
                upX, upY, 0 /* metaState */);
        sendPointerSync(event);
        event.recycle();
    }

    private void sendPointerSync(MotionEvent event) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> getInstrumentation().sendPointerSync(event));
    }

    private static class AnimationCallback implements WindowInsetsAnimationCallback {

        private boolean mFinished = false;

        @Override
        public int getDispatchMode() {
            return DISPATCH_MODE_CONTINUE_ON_SUBTREE;
        }

        @Override
        public WindowInsets onProgress(WindowInsets insets) {
            return insets;
        }

        @Override
        public void onFinish(InsetsAnimation animation) {
            synchronized (this) {
                mFinished = true;
                notify();
            }
        }

        void waitForFinishing(long timeout) throws InterruptedException {
            synchronized (this) {
                if (!mFinished) {
                    wait(timeout);
                }
            }
        }

        void reset() {
            synchronized (this) {
                mFinished = false;
            }
        }
    }

    public static class TestActivity extends FocusableActivity { }
}
