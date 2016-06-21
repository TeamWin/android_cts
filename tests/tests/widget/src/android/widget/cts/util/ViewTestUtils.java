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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnDrawListener;
import junit.framework.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for testing View behavior.
 */
public class ViewTestUtils {

    /**
     * Runs the specified Runnable on the main thread and ensures that the specified View's tree is
     * drawn before returning.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view whose tree should be drawn before returning
     * @param runner the runnable to run on the main thread, or {@code null} to
     *               simply force invalidation and a draw pass
     */
    public static void runOnMainAndDrawSync(@NonNull Instrumentation instrumentation,
            @NonNull final View view, @Nullable final Runnable runner) {
        final CountDownLatch latch = new CountDownLatch(1);

        instrumentation.runOnMainSync(() -> {
            final ViewTreeObserver observer = view.getViewTreeObserver();
            final OnDrawListener listener = new OnDrawListener() {
                @Override
                public void onDraw() {
                    observer.removeOnDrawListener(this);
                    view.post(() -> latch.countDown());
                }
            };

            observer.addOnDrawListener(listener);

            if (runner != null) {
                runner.run();
            } else {
                view.invalidate();
            }
        });

        try {
            Assert.assertTrue("Expected draw pass occurred within 5 seconds",
                    latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

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
        MotionEvent eventMove = MotionEvent.obtain(
                moveTime, moveTime, MotionEvent.ACTION_MOVE, emulatedTapX, emulatedTapY, 1);
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
            // sleep for a bit to emulate a 2-second swipe
            SystemClock.sleep(2000 / moveEventCount);
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