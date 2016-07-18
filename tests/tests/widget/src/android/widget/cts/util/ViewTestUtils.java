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

package android.widget.cts.util;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

/**
 * Utilities for testing View behavior.
 */
public class ViewTestUtils {

    /**
     * Emulates a tap in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "tap"
     */
    public static void emulateTapOnViewCenter(Instrumentation instrumentation, View view) {
        emulateTapOnScreen(instrumentation, view, view.getWidth() / 2, view.getHeight() / 2);
    }

    /**
     * Emulates a tap on a point relative to the top-left corner of the passed {@link View}. Offset
     * parameters are used to compute the final screen coordinates of the tap point.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param anchorView the anchor view to determine the tap location on the screen
     * @param offsetX extra X offset for the tap
     * @param offsetY extra Y offset for the tap
     */
    public static void emulateTapOnScreen(Instrumentation instrumentation, View anchorView,
            int offsetX, int offsetY) {
        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        final int[] viewOnScreenXY = new int[2];
        anchorView.getLocationOnScreen(viewOnScreenXY);
        int emulatedTapX = viewOnScreenXY[0] + offsetX;
        int emulatedTapY = viewOnScreenXY[1] + offsetY;

        // Inject DOWN event
        long downTime = SystemClock.uptimeMillis();
        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, emulatedTapX, emulatedTapY, 1);
        instrumentation.sendPointerSync(eventDown);

        // Inject MOVE event
        long moveTime = SystemClock.uptimeMillis();
        final int touchSlop = ViewConfiguration.get(anchorView.getContext()).getScaledTouchSlop();
        MotionEvent eventMove = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE,
                emulatedTapX + (touchSlop / 2.0f), emulatedTapY + (touchSlop / 2.0f), 1);
        instrumentation.sendPointerSync(eventMove);

        // Inject UP event
        long upTime = SystemClock.uptimeMillis();
        MotionEvent eventUp = MotionEvent.obtain(
                upTime, upTime, MotionEvent.ACTION_UP, emulatedTapX, emulatedTapY, 1);
        instrumentation.sendPointerSync(eventUp);

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }

    /**
     * Emulates a drag gesture across the screen.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param dragStartX Start X of the emulated drag gesture
     * @param dragStartY Start Y of the emulated drag gesture
     * @param dragAmountX X amount of the emulated drag gesture
     * @param dragAmountY Y amount of the emulated drag gesture
     */
    public static void emulateDragGesture(Instrumentation instrumentation,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY) {
        emulateDragGesture(instrumentation, dragStartX, dragStartY, dragAmountX, dragAmountY, 2000);
    }

    private static void emulateDragGesture(Instrumentation instrumentation,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY,
            int dragDurationMs) {
        // Inject DOWN event
        long downTime = SystemClock.uptimeMillis();
        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, dragStartX, dragStartY, 1);
        instrumentation.sendPointerSync(eventDown);

        // Inject a sequence of MOVE events that emulate a "swipe down" gesture
        final int moveEventCount = 20;
        for (int i = 0; i < moveEventCount; i++) {
            long moveTime = SystemClock.uptimeMillis();
            final int moveX = dragStartX + dragAmountX * i / moveEventCount;
            final int moveY = dragStartY + dragAmountY * i / moveEventCount;
            MotionEvent eventMove = MotionEvent.obtain(
                    moveTime, moveTime, MotionEvent.ACTION_MOVE, moveX, moveY, 1);
            instrumentation.sendPointerSync(eventMove);
            // sleep for a bit to emulate the overall swipe gesture
            SystemClock.sleep(dragDurationMs / moveEventCount);
        }

        // Inject UP event
        long upTime = SystemClock.uptimeMillis();
        MotionEvent eventUp = MotionEvent.obtain(
                upTime, upTime, MotionEvent.ACTION_UP, dragStartX + dragAmountX,
                dragStartY + dragAmountY, 1);
        instrumentation.sendPointerSync(eventUp);

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }

    private static class ViewStateSnapshot {
        final View mFirst;
        final View mLast;
        final int mFirstTop;
        final int mLastBottom;
        final int mChildCount;
        private ViewStateSnapshot(ViewGroup viewGroup) {
            mChildCount = viewGroup.getChildCount();
            if (mChildCount == 0) {
                mFirst = mLast = null;
                mFirstTop = mLastBottom = Integer.MIN_VALUE;
            } else {
                mFirst = viewGroup.getChildAt(0);
                mLast = viewGroup.getChildAt(mChildCount - 1);
                mFirstTop = mFirst.getTop();
                mLastBottom = mLast.getBottom();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ViewStateSnapshot that = (ViewStateSnapshot) o;
            return mFirstTop == that.mFirstTop &&
                    mLastBottom == that.mLastBottom &&
                    mFirst == that.mFirst &&
                    mLast == that.mLast &&
                    mChildCount == that.mChildCount;
        }

        @Override
        public int hashCode() {
            int result = mFirst != null ? mFirst.hashCode() : 0;
            result = 31 * result + (mLast != null ? mLast.hashCode() : 0);
            result = 31 * result + mFirstTop;
            result = 31 * result + mLastBottom;
            result = 31 * result + mChildCount;
            return result;
        }
    }

    /**
     * Emulates a scroll to the bottom of the specified {@link ViewGroup}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param viewGroup View group
     */
    public static void emulateScrollToBottom(Instrumentation instrumentation, ViewGroup viewGroup) {
        final int[] viewGroupOnScreenXY = new int[2];
        viewGroup.getLocationOnScreen(viewGroupOnScreenXY);

        final int emulatedX = viewGroupOnScreenXY[0] + viewGroup.getWidth() / 2;
        final int emulatedStartY = viewGroupOnScreenXY[1] + 3 * viewGroup.getHeight() / 4;
        final int swipeAmount = viewGroup.getHeight() / 2;

        ViewStateSnapshot prev;
        ViewStateSnapshot next = new ViewStateSnapshot(viewGroup);
        do {
            prev = next;
            emulateDragGesture(instrumentation, emulatedX, emulatedStartY, 0, -swipeAmount, 300);
            next = new ViewStateSnapshot(viewGroup);
        } while (!prev.equals(next));
    }

    /**
     * Emulates a long click in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "long click"
     * @param extraWaitMs the duration of emulated long click in milliseconds starting
     *      after system-level long press timeout.
     */
    public static void emulateLongClick(Instrumentation instrumentation, View view,
            long extraWaitMs) {
        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);
        int emulatedTapX = viewOnScreenXY[0] + view.getWidth() / 2;
        int emulatedTapY = viewOnScreenXY[1] + view.getHeight() / 2;

        // Inject DOWN event
        long downTime = SystemClock.uptimeMillis();
        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, emulatedTapX, emulatedTapY, 1);
        instrumentation.sendPointerSync(eventDown);

        // Inject MOVE event
        long moveTime = SystemClock.uptimeMillis();
        MotionEvent eventMove = MotionEvent.obtain(
                moveTime, moveTime, MotionEvent.ACTION_MOVE, emulatedTapX, emulatedTapY, 1);
        instrumentation.sendPointerSync(eventMove);

        try {
            Thread.sleep(ViewConfiguration.getLongPressTimeout() + extraWaitMs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Inject UP event
        long upTime = SystemClock.uptimeMillis();
        MotionEvent eventUp = MotionEvent.obtain(
                upTime, upTime, MotionEvent.ACTION_UP, emulatedTapX, emulatedTapY, 1);
        instrumentation.sendPointerSync(eventUp);

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }
}
