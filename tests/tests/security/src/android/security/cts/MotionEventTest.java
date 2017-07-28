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
package android.security.cts;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import android.security.cts.activity.MotionEventTestActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;


public class MotionEventTest extends ActivityInstrumentationTestCase2<MotionEventTestActivity> {

    private Instrumentation mInstrumentation;
    private MotionEventTestActivity mActivity;

    public MotionEventTest() {
        super(MotionEventTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Start the activity and get a reference to it.
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        // Wait for the UI Thread to become idle.
        getInstrumentation().waitForIdleSync();
        boolean hasFocus = mActivity.waitForWindowFocus(10000); // 10 s wait
        assertTrue(hasFocus);
    }

    /**
     * Test for whether ACTION_OUTSIDE events contain information about whether touches are
     * obscured.
     *
     * If ACTION_OUTSIDE_EVENTS contain information about whether the touch is obscured, then a
     * pattern of invisible, untouchable, unfocusable SYSTEM_ALERT_WINDOWS can be placed across the
     * screen to determine approximate locations of touch events without the user knowing.
     * @throws Exception
     */
    public void testActionOutsideDoesNotContainedObscuredInformation() throws Exception {
        final OnTouchListener listener = new OnTouchListener();
        final Point size = new Point();
        final View[] viewHolder = new View[1];
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                final WindowManager wm =
                        (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.getDefaultDisplay().getSize(size);

                WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                wmlp.width = size.x / 4;
                wmlp.height = size.y / 4;
                wmlp.gravity = Gravity.TOP | Gravity.LEFT;
                wmlp.setTitle(mActivity.getPackageName());

                ViewGroup.LayoutParams vglp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);

                View v = new View(mActivity);
                v.setOnTouchListener(listener);
                v.setBackgroundColor(Color.GREEN);
                v.setLayoutParams(vglp);
                wm.addView(v, wmlp);

                wmlp.gravity = Gravity.TOP | Gravity.RIGHT;

                v = new View(mActivity);
                v.setBackgroundColor(Color.BLUE);
                v.setOnTouchListener(listener);
                v.setLayoutParams(vglp);
                viewHolder[0] = v;

                wm.addView(v, wmlp);
            }
        });
        mInstrumentation.waitForIdleSync();

        FutureTask<Point> task = new FutureTask<Point> (new Callable<Point>() {

            @Override
            public Point call() throws Exception {
                final int[] viewLocation = new int[2];
                viewHolder[0].getLocationOnScreen(viewLocation);
                return new Point(viewLocation[0], viewLocation[1]);
            }
        });
        mActivity.runOnUiThread(task);
        Point viewLocation = task.get(5, TimeUnit.SECONDS);
        injectTap(viewLocation.x, viewLocation.y);

        List<MotionEvent> outsideEvents = listener.getOutsideEvents();
        assertEquals(2, outsideEvents.size());
        for (MotionEvent e : outsideEvents) {
            assertEquals(0, e.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
        }
    }

    private void injectTap(int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        injectEvent(MotionEvent.ACTION_DOWN, x, y, downTime);
        injectEvent(MotionEvent.ACTION_UP, x, y, downTime);
    }

    private void injectEvent(int action, int x, int y, long downTime) {
        final UiAutomation automation = mInstrumentation.getUiAutomation();
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        automation.injectInputEvent(event, true);
        event.recycle();
    }

    private static class OnTouchListener implements View.OnTouchListener {
        private List<MotionEvent> mOutsideEvents;

        public OnTouchListener() {
            mOutsideEvents = new ArrayList<MotionEvent>();
        }

        public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
                mOutsideEvents.add(MotionEvent.obtain(e));
            }
            return true;
        }

        public List<MotionEvent> getOutsideEvents() {
            return mOutsideEvents;
        }
    }
}
