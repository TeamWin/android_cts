/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.cts;

import static com.android.compatibility.common.util.CtsMouseUtil.PositionMatcher;
import static com.android.compatibility.common.util.CtsMouseUtil.clearHoverListener;
import static com.android.compatibility.common.util.CtsMouseUtil.installHoverListener;
import static com.android.compatibility.common.util.CtsMouseUtil.obtainMouseEvent;
import static com.android.compatibility.common.util.CtsMouseUtil.verifyEnterMove;
import static com.android.compatibility.common.util.CtsMouseUtil.verifyEnterMoveExit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Instrumentation;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.compatibility.common.util.CtsMouseUtil.ActionMatcher;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

/**
 * Test {@link View}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PointerCaptureTest {
    private static final long TIMEOUT_DELTA = 10000;

    private Instrumentation mInstrumentation;
    private PointerCaptureCtsActivity mActivity;

    private View mOuter;
    private View mInner;
    private View mTarget;
    private View mTarget2;

    @Rule
    public ActivityTestRule<PointerCaptureCtsActivity> mActivityRule =
            new ActivityTestRule<>(PointerCaptureCtsActivity.class);

    @Rule
    public ActivityTestRule<CtsActivity> mCtsActivityRule =
            new ActivityTestRule<>(CtsActivity.class, false, false);

    private void requestFocusSync(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.requestFocus();
        });
        PollingCheck.waitFor(TIMEOUT_DELTA, view::hasFocus);
    }

    private void requestCaptureSync(View view) throws Throwable {
        mActivityRule.runOnUiThread(view::requestPointerCapture);
        PollingCheck.waitFor(TIMEOUT_DELTA, view::hasPointerCapture);
    }

    private void requestCaptureSync() throws Throwable {
        requestCaptureSync(mOuter);
    }

    private void releaseCaptureSync(View view) throws Throwable {
        mActivityRule.runOnUiThread(view::releasePointerCapture);
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !view.hasPointerCapture());
    }

    private void releaseCaptureSync() throws Throwable {
        releaseCaptureSync(mOuter);
    }

    public static View.OnCapturedPointerListener installCapturedPointerListener(View view) {
        final View.OnCapturedPointerListener mockListener =
                mock(View.OnCapturedPointerListener.class);
        view.setOnCapturedPointerListener((v, event) -> {
            // Clone the event to work around event instance reuse in the framework.
            mockListener.onCapturedPointer(v, MotionEvent.obtain(event));
            return true;
        });
        return mockListener;
    }

    public static void clearCapturedPointerListener(View view) {
        view.setOnCapturedPointerListener(null);
    }

    private static void injectMotionEvent(MotionEvent event) {
        InputManager.getInstance().injectInputEvent(event,
                InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
    }

    private static void injectRelativeMouseEvent(int action, int x, int y) {
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE_RELATIVE);
        injectMotionEvent(event);
    }

    private static void verifyRelativeMouseEvent(InOrder inOrder,
                View.OnCapturedPointerListener listener, View view, int action, int x, int y) {
        inOrder.verify(listener, times(1)).onCapturedPointer(
                eq(view), argThat(new PositionMatcher(action, x, y)));
    }

    private void verifyHoverDispatch() {
        View.OnHoverListener listenerOuter = installHoverListener(mOuter);
        View.OnHoverListener listenerInner = installHoverListener(mInner);
        View.OnHoverListener listenerTarget = installHoverListener(mTarget);
        View.OnHoverListener listenerTarget2 = installHoverListener(mTarget2);

        injectMotionEvent(obtainMouseEvent(MotionEvent.ACTION_HOVER_MOVE, mInner, 0, 0));
        injectMotionEvent(obtainMouseEvent(MotionEvent.ACTION_HOVER_MOVE, mTarget, 0, 0));
        injectMotionEvent(obtainMouseEvent(MotionEvent.ACTION_HOVER_MOVE, mTarget2, 0, 0));

        clearHoverListener(mOuter);
        clearHoverListener(mInner);
        clearHoverListener(mTarget);
        clearHoverListener(mTarget2);

        verifyEnterMoveExit(listenerInner, mInner, 2);
        verifyEnterMoveExit(listenerTarget, mTarget, 2);
        verifyEnterMove(listenerTarget2, mTarget2, 1);

        verifyNoMoreInteractions(listenerOuter);
        verifyNoMoreInteractions(listenerInner);
        verifyNoMoreInteractions(listenerTarget);
        verifyNoMoreInteractions(listenerTarget2);
    }

    private void assertPointerCapture(boolean enabled) {
        assertEquals(enabled, mOuter.hasPointerCapture());
        assertEquals(enabled, mInner.hasPointerCapture());
        assertEquals(enabled, mTarget.hasPointerCapture());
        assertEquals(enabled, mTarget2.hasPointerCapture());
        assertEquals(enabled, mActivity.hasPointerCapture());
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();

        mOuter = mActivity.findViewById(R.id.outer);
        mInner = mActivity.findViewById(R.id.inner);
        mTarget = mActivity.findViewById(R.id.target);
        mTarget2 = mActivity.findViewById(R.id.target2);

        PollingCheck.waitFor(TIMEOUT_DELTA, mActivity::hasWindowFocus);
    }

    @Test
    public void testRequestAndReleaseWorkOnAnyView() throws Throwable {
        requestCaptureSync(mOuter);
        assertPointerCapture(true);

        releaseCaptureSync(mOuter);
        assertPointerCapture(false);

        requestCaptureSync(mInner);
        assertPointerCapture(true);

        releaseCaptureSync(mTarget);
        assertPointerCapture(false);
    }

    @Test
    public void testWindowFocusChangeEndsCapture() throws Throwable {
        requestCaptureSync();
        assertPointerCapture(true);

        // Show a context menu on a widget.
        mActivity.registerForContextMenu(mTarget);
        mActivityRule.runOnUiThread(() -> mTarget.showContextMenu(0, 0));
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !mOuter.hasWindowFocus());
        assertPointerCapture(false);

        mInstrumentation.sendCharacterSync(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> mOuter.hasWindowFocus());
        assertFalse(mTarget.hasPointerCapture());
        assertFalse(mActivity.hasPointerCapture());
    }

    @Test
    public void testActivityFocusChangeEndsCapture() throws Throwable {
        requestCaptureSync();
        assertPointerCapture(true);

        // Launch another activity.
        CtsActivity activity = mCtsActivityRule.launchActivity(null);
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !mActivity.hasWindowFocus());
        assertPointerCapture(false);

        activity.finish();
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> mActivity.hasWindowFocus());
        assertPointerCapture(false);
    }

    @Test
    public void testEventDispatch() throws Throwable {
        verifyHoverDispatch();

        View.OnCapturedPointerListener listenerInner = installCapturedPointerListener(mInner);
        View.OnCapturedPointerListener listenerTarget = installCapturedPointerListener(mTarget);
        View.OnCapturedPointerListener listenerTarget2 = installCapturedPointerListener(mTarget2);
        View.OnHoverListener hoverListenerTarget2 = installHoverListener(mTarget2);

        requestCaptureSync();

        requestFocusSync(mInner);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 1, 2);
        injectRelativeMouseEvent(MotionEvent.ACTION_DOWN, 1, 2);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 3, 4);
        injectRelativeMouseEvent(MotionEvent.ACTION_UP, 3, 4);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 1, 2);

        requestFocusSync(mTarget);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 5, 6);

        requestFocusSync(mTarget2);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 7, 8);

        requestFocusSync(mInner);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 9, 10);

        releaseCaptureSync();

        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 11, 12);  // Should be ignored.

        clearCapturedPointerListener(mInner);
        clearCapturedPointerListener(mTarget);
        clearCapturedPointerListener(mTarget2);
        clearHoverListener(mTarget2);

        InOrder inOrder = inOrder(
                listenerInner, listenerTarget, listenerTarget2, hoverListenerTarget2);

        // mTarget2 is left hovered after the call to verifyHoverDispatch.
        inOrder.verify(hoverListenerTarget2, times(1)).onHover(
                eq(mTarget2), argThat(new ActionMatcher(MotionEvent.ACTION_HOVER_EXIT)));

        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 1, 2);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_DOWN, 1, 2);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 3, 4);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_UP, 3, 4);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 1, 2);

        verifyRelativeMouseEvent(inOrder, listenerTarget, mTarget, MotionEvent.ACTION_MOVE, 5, 6);
        verifyRelativeMouseEvent(inOrder, listenerTarget2, mTarget2, MotionEvent.ACTION_MOVE, 7, 8);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 9, 10);

        inOrder.verifyNoMoreInteractions();

        // Check the regular dispatch again.
        verifyHoverDispatch();
    }
}
