/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test hover events.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HoverTest {
    private static final String LOG_TAG = "HoverTest";

    private Instrumentation mInstrumentation;
    private Activity mActivity;

    private View mOuter;
    private View mMiddle1;
    private View mMiddle2;
    private View mInner11;
    private View mInner12;
    private View mInner21;
    private View mInner22;

    @Rule
    public ActivityTestRule<HoverCtsActivity> mActivityRule =
            new ActivityTestRule<>(HoverCtsActivity.class);

    static class ActionMatcher extends ArgumentMatcher<MotionEvent> {
        private final int mAction;

        ActionMatcher(int action) {
            mAction = action;
        }

        public boolean matches(Object actual) {
            return (actual instanceof MotionEvent) && ((MotionEvent) actual).getAction() == mAction;
        }

        public void describeTo(Description description) {
            description.appendText("action=" + MotionEvent.actionToString(mAction));
        }
    }

    static class MoveMatcher extends ActionMatcher {
        private final int mX;
        private final int mY;

        MoveMatcher(int x, int y) {
            super(MotionEvent.ACTION_HOVER_MOVE);
            mX = x;
            mY = y;
        }

        public boolean matches(Object actual) {
            return super.matches(actual)
                    && ((int)((MotionEvent)actual).getX()) == mX
                    && ((int)((MotionEvent)actual).getY()) == mY;
        }

        public void describeTo(Description description) {
            super.describeTo(description);
            description.appendText("@(" + mX + "," + mY + ")");
        }
    }

    private final ActionMatcher mEnterMatcher = new ActionMatcher(MotionEvent.ACTION_HOVER_ENTER);
    private final ActionMatcher mMoveMatcher = new ActionMatcher(MotionEvent.ACTION_HOVER_MOVE);
    private final ActionMatcher mExitMatcher = new ActionMatcher(MotionEvent.ACTION_HOVER_EXIT);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();

        mOuter = mActivity.findViewById(R.id.outer);
        mMiddle1 = mActivity.findViewById(R.id.middle1);
        mMiddle2 = mActivity.findViewById(R.id.middle2);
        mInner11 = mActivity.findViewById(R.id.inner11);
        mInner12 = mActivity.findViewById(R.id.inner12);
        mInner21 = mActivity.findViewById(R.id.inner21);
        mInner22 = mActivity.findViewById(R.id.inner22);
    }

    private void verifyHoverSequence(
            View.OnHoverListener listener, View view, int moveCount, boolean exit) {
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).onHover(eq(view), argThat(mEnterMatcher));
        inOrder.verify(listener, times(moveCount)).onHover(eq(view), argThat(mMoveMatcher));
        if (exit) {
            inOrder.verify(listener, times(1)).onHover(eq(view), argThat(mExitMatcher));
        }
        verifyNoMoreInteractions(listener);
    }

    private void verifyEnterMove(View.OnHoverListener listener, View view, int moveCount) {
        verifyHoverSequence(listener, view, moveCount, false);
    }

    private void verifyEnterMoveExit(View.OnHoverListener listener, View view, int moveCount) {
        verifyHoverSequence(listener, view, moveCount, true);
    }

    private void injectHoverMove(View view) {
        injectHoverMove(view, 0, 0);
    }

    private void injectHoverMove(View view, int offsetX, int offsetY) {
        mActivity.getWindow().injectInputEvent(
                obtainMotionEvent(view, MotionEvent.ACTION_HOVER_MOVE, offsetX, offsetY));
        mInstrumentation.waitForIdleSync();
    }

    private MotionEvent obtainMotionEvent(View anchor, int action, int offsetX, int offsetY) {
        final long eventTime = SystemClock.uptimeMillis();
        final int[] screenPos = new int[2];
        anchor.getLocationOnScreen(screenPos);
        final int x = screenPos[0] + offsetX;
        final int y = screenPos[1] + offsetY;
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        return event;
    }

    private View.OnHoverListener installHoverListener(View view) {
        return installHoverListener(view, true);
    }

    private View.OnHoverListener installHoverListener(View view, boolean result) {
        final View.OnHoverListener mockListener = mock(View.OnHoverListener.class);
        view.setOnHoverListener((v, event) -> {
            // Clone the event to work around event instance reuse in the framework.
            mockListener.onHover(v, MotionEvent.obtain(event));
            return result;
        });
        return mockListener;
    }

    private void clearHoverListener(View view) {
        view.setOnHoverListener(null);
    }

    private void remove(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> ((ViewGroup)view.getParent()).removeView(view));
    }

    @Test
    public void testHoverMove() throws Throwable {
        View.OnHoverListener listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);

        clearHoverListener(mInner11);

        verifyEnterMove(listener, mInner11, 1);
    }

    @Test
    public void testHoverMoveMultiple() throws Throwable {
        View.OnHoverListener listener = installHoverListener(mInner11);

        injectHoverMove(mInner11, 1, 2);
        injectHoverMove(mInner11, 3, 4);
        injectHoverMove(mInner11, 5, 6);

        clearHoverListener(mInner11);

        InOrder inOrder = inOrder(listener);

        inOrder.verify(listener, times(1)).onHover(eq(mInner11), argThat(mEnterMatcher));
        inOrder.verify(listener, times(1)).onHover(eq(mInner11), argThat(new MoveMatcher(1, 2)));
        inOrder.verify(listener, times(1)).onHover(eq(mInner11), argThat(new MoveMatcher(3, 4)));
        inOrder.verify(listener, times(1)).onHover(eq(mInner11), argThat(new MoveMatcher(5, 6)));

        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testHoverMoveAndExit() throws Throwable {
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);
        View.OnHoverListener inner12Listener = installHoverListener(mInner12);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);

        clearHoverListener(mInner11);
        clearHoverListener(mInner12);

        verifyEnterMoveExit(inner11Listener, mInner11, 2);
        verifyEnterMove(inner12Listener, mInner12, 1);
    }

    @Test
    public void testRemoveBeforeExit() throws Throwable {
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);
        remove(mInner11);

        clearHoverListener(mMiddle1);
        clearHoverListener(mInner11);

        verifyNoMoreInteractions(middle1Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 1);
    }

    @Test
    public void testRemoveParentBeforeExit() throws Throwable {
        View.OnHoverListener outerListener = installHoverListener(mOuter);
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);
        remove(mMiddle1);

        clearHoverListener(mOuter);
        clearHoverListener(mMiddle1);
        clearHoverListener(mInner11);

        verifyNoMoreInteractions(outerListener);
        verifyNoMoreInteractions(middle1Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 1);
    }

    @Test
    public void testRemoveAfterExit() throws Throwable {
        View.OnHoverListener listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);
        remove(mInner11);

        clearHoverListener(mInner11);

        verifyEnterMoveExit(listener, mInner11, 2);
    }

    @Test
    public void testNoParentInteraction() throws Throwable {
        View.OnHoverListener outerListener = installHoverListener(mOuter);
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener middle2Listener = installHoverListener(mMiddle2);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);
        View.OnHoverListener inner12Listener = installHoverListener(mInner12);
        View.OnHoverListener inner21Listener = installHoverListener(mInner21);
        View.OnHoverListener inner22Listener = installHoverListener(mInner22);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);
        injectHoverMove(mInner21);
        injectHoverMove(mInner22);

        clearHoverListener(mOuter);
        clearHoverListener(mMiddle1);
        clearHoverListener(mMiddle2);
        clearHoverListener(mInner11);
        clearHoverListener(mInner21);

        verifyNoMoreInteractions(outerListener);
        verifyNoMoreInteractions(middle1Listener);
        verifyNoMoreInteractions(middle2Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 2);
        verifyEnterMoveExit(inner12Listener, mInner12, 2);
        verifyEnterMoveExit(inner21Listener, mInner21, 2);
        verifyEnterMove(inner22Listener, mInner22, 1);
    }

    @Test
    public void testParentInteraction() throws Throwable {
        View.OnHoverListener outerListener = installHoverListener(mOuter);
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener middle2Listener = installHoverListener(mMiddle2);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11, false);
        View.OnHoverListener inner12Listener = installHoverListener(mInner12, false);
        View.OnHoverListener inner21Listener = installHoverListener(mInner21);
        View.OnHoverListener inner22Listener = installHoverListener(mInner22);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);
        injectHoverMove(mInner21);
        injectHoverMove(mInner22);

        clearHoverListener(mOuter);
        clearHoverListener(mMiddle1);
        clearHoverListener(mMiddle2);
        clearHoverListener(mInner11);
        clearHoverListener(mInner12);
        clearHoverListener(mInner21);

        verifyNoMoreInteractions(outerListener);
        verifyEnterMoveExit(middle1Listener, mMiddle1, 3);
        verifyNoMoreInteractions(middle2Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 2);
        verifyEnterMoveExit(inner12Listener, mInner12, 2);
        verifyEnterMoveExit(inner21Listener, mInner21, 2);
        verifyEnterMove(inner22Listener, mInner22, 1);
    }
}