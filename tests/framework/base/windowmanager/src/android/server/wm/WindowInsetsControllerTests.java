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

import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_SWIPE;
import static android.view.WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_TOUCH;
import static android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsets.Type;
import android.view.WindowInsetsAnimation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Test whether WindowInsetsController controls window insets as expected.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowInsetsControllerTests
 */
@FlakyTest(detail = "Promote once confirmed non-flaky")
@Presubmit
public class WindowInsetsControllerTests extends WindowManagerTestBase {

    private final static long TIMEOUT = 1000; // milliseconds
    private final static AnimationCallback ANIMATION_CALLBACK = new AnimationCallback();

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

    @Test
    public void testImeShowAndHide() {
        final TestActivity activity = startActivity(TestActivity.class);
        final View rootView = activity.getWindow().getDecorView();
        getInstrumentation().runOnMainSync(() -> {
            rootView.getWindowInsetsController().show(ime());
        });
        PollingCheck.waitFor(TIMEOUT, () -> rootView.getRootWindowInsets().isVisible(ime()));
        getInstrumentation().runOnMainSync(() -> {
            rootView.getWindowInsetsController().hide(ime());
        });
        PollingCheck.waitFor(TIMEOUT, () -> !rootView.getRootWindowInsets().isVisible(ime()));
    }

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

    @Test
    public void testHideOnCreate() throws Exception {
        final TestHideOnCreateActivity activity = startActivity(TestHideOnCreateActivity.class);
        final View rootView = activity.getWindow().getDecorView();
        ANIMATION_CALLBACK.waitForFinishing(TIMEOUT);
        PollingCheck.waitFor(TIMEOUT,
                () -> !rootView.getRootWindowInsets().isVisible(statusBars())
                        && !rootView.getRootWindowInsets().isVisible(navigationBars()));
    }

    @Test
    public void testShowImeOnCreate() throws Exception {
        final TestShowOnCreateActivity activity = startActivity(TestShowOnCreateActivity.class);
        final View rootView = activity.getWindow().getDecorView();
        ANIMATION_CALLBACK.waitForFinishing(TIMEOUT);
        PollingCheck.waitFor(TIMEOUT,
                () -> rootView.getRootWindowInsets().isVisible(ime()));
    }

    @Test
    public void testInsetsDispatch() throws Exception {
        // Start an activity which hides system bars.
        final TestHideOnCreateActivity activity = startActivity(TestHideOnCreateActivity.class);
        final View rootView = activity.getWindow().getDecorView();
        ANIMATION_CALLBACK.waitForFinishing(TIMEOUT);
        PollingCheck.waitFor(TIMEOUT,
                () -> !rootView.getRootWindowInsets().isVisible(statusBars())
                        && !rootView.getRootWindowInsets().isVisible(navigationBars()));

        // Add a dialog which hides system bars before the dialog is added to the system while the
        // system bar was hidden previously, and collect the window insets that the dialog receives.
        final ArrayList<WindowInsets> windowInsetsList = new ArrayList<>();
        getInstrumentation().runOnMainSync(() -> {
            final AlertDialog dialog = new AlertDialog.Builder(activity).create();
            final Window dialogWindow = dialog.getWindow();
            dialogWindow.getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
                windowInsetsList.add(insets);
                return view.onApplyWindowInsets(insets);
            });
            dialogWindow.getInsetsController().hide(statusBars() | navigationBars());
            dialog.show();
        });
        getInstrumentation().waitForIdleSync();

        // The dialog must never receive any of visible insets of system bars.
        for (WindowInsets windowInsets : windowInsetsList) {
            assertFalse(windowInsets.isVisible(statusBars()));
            assertFalse(windowInsets.isVisible(navigationBars()));
        }
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

    private static class AnimationCallback extends WindowInsetsAnimation.Callback {

        private boolean mFinished = false;

        AnimationCallback() {
            super(DISPATCH_MODE_CONTINUE_ON_SUBTREE);
        }

        @Override
        public WindowInsets onProgress(WindowInsets insets,
                List<WindowInsetsAnimation> runningAnimations) {
            return insets;
        }

        @Override
        public void onEnd(WindowInsetsAnimation animation) {
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

    private static View setViews(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        View text = new TextView(activity);
        EditText editor = new EditText(activity);
        layout.addView(text);
        layout.addView(editor);
        activity.setContentView(layout);
        editor.requestFocus();
        return layout;
    }

    public static class TestActivity extends FocusableActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setViews(this);
        }
    }

    public static class TestHideOnCreateActivity extends FocusableActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            View layout = setViews(this);
            ANIMATION_CALLBACK.reset();
            getWindow().getDecorView().setWindowInsetsAnimationCallback(ANIMATION_CALLBACK);
            getWindow().getInsetsController().hide(statusBars());
            layout.getWindowInsetsController().hide(navigationBars());
        }
    }

    public static class TestShowOnCreateActivity extends FocusableActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            View layout = setViews(this);
            ANIMATION_CALLBACK.reset();
            getWindow().getDecorView().setWindowInsetsAnimationCallback(ANIMATION_CALLBACK);
            getWindow().getInsetsController().show(ime());
        }
    }
}
