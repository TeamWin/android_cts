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

import android.os.SystemClock;
import android.view.MotionEvent;
import junit.framework.Assert;

import android.app.Instrumentation;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnDrawListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for testing View behavior.
 */
public class ViewTestUtils {

    /**
     * Runs the specified Runnable on the main thread and ensures that the
     * specified View's tree is drawn before returning.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view whose tree should be drawn before returning
     * @param runner the runnable to run on the main thread
     */
    public static void runOnMainAndDrawSync(Instrumentation instrumentation,
            final View view, final Runnable runner) {
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
            runner.run();
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
     * Emulates a tap on a point relative to the top-left corner of the passed {@link View}.
     * Offset parameters are used to compute the final screen coordinates of the tap point.
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
}
