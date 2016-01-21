/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Verify that gestures dispatched from an accessibility service show up in the current UI
 */
public class AccessibilityGestureDispatchTest extends
        ActivityInstrumentationTestCase2<AccessibilityGestureDispatchTest.GestureDispatchActivity> {
    // Match com.android.server.accessibility.AccessibilityManagerService#COMPONENT_NAME_SEPARATOR
    private static final String COMPONENT_NAME_SEPARATOR = ":";
    private static final int TIMEOUT_FOR_SERVICE_ENABLE = 10000; // millis; 10s
    private static final int GESTURE_COMPLETION_TIMEOUT = 5000; // millis
    private static final int MOTION_EVENT_TIMEOUT = 1000; // millis

    final List<MotionEvent> mMotionEvents = new ArrayList<>();
    MyTouchListener mMyTouchListener = new MyTouchListener();
    MyGestureCallback mCallback;
    TextView mFullScreenTextView;
    Rect mViewBounds = new Rect();
    boolean mGotUpEvent;

    public AccessibilityGestureDispatchTest() {
        super(GestureDispatchActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mFullScreenTextView =
                (TextView) getActivity().findViewById(R.id.full_screen_text_view);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mFullScreenTextView.getGlobalVisibleRect(mViewBounds);
                mFullScreenTextView.setOnTouchListener(mMyTouchListener);
            }
        });
        Context context = getInstrumentation().getContext();
        UiAutomation uiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        ParcelFileDescriptor fd = uiAutomation.executeShellCommand(
                "pm grant " + context.getPackageName()
                        + "android.permission.WRITE_SECURE_SETTINGS");
        uiAutomation.destroy();
        fd.close();
        turnAccessibilityOff();
        enableService();
        mMotionEvents.clear();
        mCallback = new MyGestureCallback();
        mGotUpEvent = false;
    }

    @Override
    public void tearDown() {
        turnAccessibilityOff();
    }

    public void testClickAt_producesDownThenUp() throws InterruptedException {
        final int clickXInsideView = 10;
        final int clickYInsideView = 20;
        int clickX = clickXInsideView + mViewBounds.left;
        int clickY = clickYInsideView + mViewBounds.top;
        GestureDescription click = GestureDescription.createClick(clickX, clickY);
        assertTrue(StubGestureAccessibilityService.sConnectedInstance
                .doDispatchGesture(click, mCallback, null));
        mCallback.assertGestureCompletes(GESTURE_COMPLETION_TIMEOUT);
        waitForMotionEvents(3);

        assertEquals(3, mMotionEvents.size());
        MotionEvent clickDown = mMotionEvents.get(0);
        MotionEvent clickMove = mMotionEvents.get(1);
        MotionEvent clickUp = mMotionEvents.get(2);

        assertEquals(MotionEvent.ACTION_DOWN, clickDown.getActionMasked());
        assertEquals(0, clickDown.getActionIndex());
        assertEquals(0, clickDown.getDeviceId());
        assertEquals(0, clickDown.getEdgeFlags());
        assertEquals(1F, clickDown.getXPrecision());
        assertEquals(1F, clickDown.getYPrecision());
        assertEquals(1, clickDown.getPointerCount());
        assertEquals(1F, clickDown.getPressure());
        assertEquals((float) clickXInsideView, clickDown.getX());
        assertEquals((float) clickYInsideView, clickDown.getY());
        assertEquals(clickDown.getDownTime(), clickDown.getEventTime());

        assertEquals(MotionEvent.ACTION_MOVE, clickMove.getActionMasked());
        assertEquals(clickDown.getDownTime(), clickMove.getDownTime());
        assertEquals(ViewConfiguration.getTapTimeout(),
                clickMove.getEventTime() - clickMove.getDownTime());
        assertEquals(0, clickMove.getActionIndex());
        assertEquals(1, clickMove.getPointerCount());
        assertEquals((float) clickXInsideView + 1, clickMove.getX());
        assertEquals((float) clickYInsideView, clickMove.getY());
        assertEquals(clickDown.getPointerId(0),
                clickMove.getPointerId(0));

        assertEquals(MotionEvent.ACTION_UP, clickUp.getActionMasked());
        assertEquals(clickDown.getDownTime(), clickUp.getDownTime());
        assertEquals(clickMove.getEventTime(), clickUp.getEventTime());
        assertTrue(clickDown.getEventTime() + ViewConfiguration.getLongPressTimeout()
                > clickUp.getEventTime());
        assertEquals((float) clickXInsideView + 1, clickUp.getX());
        assertEquals((float) clickYInsideView, clickUp.getY());
    }

    public void testLongClickAt_producesEventsWithLongClickTiming() throws InterruptedException {
        final int clickXInsideView = 10;
        final int clickYInsideView = 20;
        int clickX = clickXInsideView + mViewBounds.left;
        int clickY = clickYInsideView + mViewBounds.top;
        GestureDescription longClick = GestureDescription.createLongClick(clickX, clickY);
        assertTrue(StubGestureAccessibilityService.sConnectedInstance
                .doDispatchGesture(longClick, mCallback, null));
        mCallback.assertGestureCompletes(
                ViewConfiguration.getLongPressTimeout() + GESTURE_COMPLETION_TIMEOUT);

        waitForMotionEvents(3);
        MotionEvent clickDown = mMotionEvents.get(0);
        MotionEvent clickMove = mMotionEvents.get(1);
        MotionEvent clickUp = mMotionEvents.get(2);

        assertEquals(MotionEvent.ACTION_DOWN, clickDown.getActionMasked());

        assertEquals((float) clickXInsideView, clickDown.getX());
        assertEquals((float) clickYInsideView, clickDown.getY());

        assertEquals(MotionEvent.ACTION_MOVE, clickMove.getActionMasked());
        assertEquals(clickDown.getDownTime(), clickMove.getDownTime());
        assertEquals((float) clickXInsideView + 1, clickMove.getX());
        assertEquals((float) clickYInsideView, clickMove.getY());
        assertEquals(clickDown.getPointerId(0), clickMove.getPointerId(0));

        assertEquals(MotionEvent.ACTION_UP, clickUp.getActionMasked());
        assertTrue(clickDown.getEventTime() + ViewConfiguration.getLongPressTimeout()
                <= clickUp.getEventTime());
        assertEquals(clickDown.getDownTime(), clickUp.getDownTime());
        assertEquals((float) clickXInsideView + 1, clickUp.getX());
        assertEquals((float) clickYInsideView, clickUp.getY());
    }

    public void testSwipe_shouldContainPointsInALine() throws InterruptedException {
        int startXInsideView = 10;
        int startYInsideView = 20;
        int endXInsideView = 20;
        int endYInsideView = 40;
        int startX = startXInsideView + mViewBounds.left;
        int startY = startYInsideView + mViewBounds.top;
        int endX = endXInsideView + mViewBounds.left;
        int endY = endYInsideView + mViewBounds.top;
        int gestureTime = 500;
        float swipeTolerance = 2.0f;

        GestureDescription swipe = GestureDescription
                .createSwipe(startX, startY, endX, endY, gestureTime);
        assertTrue(StubGestureAccessibilityService.sConnectedInstance
                .doDispatchGesture(swipe, mCallback, null));
        mCallback.assertGestureCompletes(gestureTime + GESTURE_COMPLETION_TIMEOUT);
        waitForUpEvent();
        int numEvents = mMotionEvents.size();

        MotionEvent downEvent = mMotionEvents.get(0);
        assertEquals(MotionEvent.ACTION_DOWN, downEvent.getActionMasked());
        assertEquals(startXInsideView, (int) downEvent.getX());
        assertEquals(startYInsideView, (int) downEvent.getY());

        MotionEvent upEvent = mMotionEvents.get(numEvents - 1);
        assertEquals(MotionEvent.ACTION_UP, upEvent.getActionMasked());
        assertEquals(endXInsideView, (int) upEvent.getX());
        assertEquals(endYInsideView, (int) upEvent.getY());
        assertEquals(gestureTime, upEvent.getEventTime() - downEvent.getEventTime());

        long lastEventTime = downEvent.getEventTime();
        for (int i = 1; i < numEvents - 1; i++) {
            MotionEvent moveEvent = mMotionEvents.get(i);
            assertEquals(MotionEvent.ACTION_MOVE, moveEvent.getActionMasked());
            assertTrue(moveEvent.getEventTime() >= lastEventTime);
            float fractionOfSwipe =
                    ((float) (moveEvent.getEventTime() - downEvent.getEventTime())) / gestureTime;
            float fractionX = ((float) (endXInsideView - startXInsideView)) * fractionOfSwipe;
            float fractionY = ((float) (endYInsideView - startYInsideView)) * fractionOfSwipe;
            assertEquals(startXInsideView + fractionX, moveEvent.getX(), swipeTolerance);
            assertEquals(startYInsideView + fractionY, moveEvent.getY(), swipeTolerance);
            lastEventTime = moveEvent.getEventTime();
        }
    }

    public void testSlowSwipe_shouldNotContainMovesForTinyMovement() throws InterruptedException {
        int startXInsideView = 10;
        int startYInsideView = 20;
        int endXInsideView = 11;
        int endYInsideView = 22;
        int startX = startXInsideView + mViewBounds.left;
        int startY = startYInsideView + mViewBounds.top;
        int endX = endXInsideView + mViewBounds.left;
        int endY = endYInsideView + mViewBounds.top;
        int gestureTime = 1000;

        GestureDescription swipe = GestureDescription
                .createSwipe(startX, startY, endX, endY, gestureTime);
        assertTrue(StubGestureAccessibilityService.sConnectedInstance
                .doDispatchGesture(swipe, mCallback, null));
        mCallback.assertGestureCompletes(gestureTime + GESTURE_COMPLETION_TIMEOUT);
        waitForUpEvent();

        assertEquals(5, mMotionEvents.size());

        assertEquals(MotionEvent.ACTION_DOWN, mMotionEvents.get(0).getActionMasked());
        assertEquals(MotionEvent.ACTION_MOVE, mMotionEvents.get(1).getActionMasked());
        assertEquals(MotionEvent.ACTION_MOVE, mMotionEvents.get(2).getActionMasked());
        assertEquals(MotionEvent.ACTION_MOVE, mMotionEvents.get(3).getActionMasked());
        assertEquals(MotionEvent.ACTION_UP, mMotionEvents.get(4).getActionMasked());

        assertEquals(startXInsideView, (int) mMotionEvents.get(0).getX());
        assertEquals(startXInsideView, (int) mMotionEvents.get(1).getX());
        assertEquals(startXInsideView + 1, (int) mMotionEvents.get(2).getX());
        assertEquals(startXInsideView + 1, (int) mMotionEvents.get(3).getX());
        assertEquals(startXInsideView + 1, (int) mMotionEvents.get(4).getX());

        assertEquals(startYInsideView, (int) mMotionEvents.get(0).getY());
        assertEquals(startYInsideView + 1, (int) mMotionEvents.get(1).getY());
        assertEquals(startYInsideView + 1, (int) mMotionEvents.get(2).getY());
        assertEquals(startYInsideView + 2, (int) mMotionEvents.get(3).getY());
        assertEquals(startYInsideView + 2, (int) mMotionEvents.get(4).getY());
    }

    public void testAngledPinch_looksReasonable() throws InterruptedException {
        int centerXInsideView = 50;
        int centerYInsideView = 60;
        int centerX = centerXInsideView + mViewBounds.left;
        int centerY = centerYInsideView + mViewBounds.top;
        int startSpacing = 100;
        int endSpacing = 50;
        int gestureTime = 500;
        float pinchTolerance = 2.0f;

        GestureDescription pinch = GestureDescription.createPinch(centerX, centerY, startSpacing,
                endSpacing, 45.0F, gestureTime);
        assertTrue(StubGestureAccessibilityService.sConnectedInstance
                .doDispatchGesture(pinch, mCallback, null));
        mCallback.assertGestureCompletes(gestureTime + GESTURE_COMPLETION_TIMEOUT);
        waitForUpEvent();
        int numEvents = mMotionEvents.size();

        // First two events are the initial down and the pointer down
        assertEquals(MotionEvent.ACTION_DOWN, mMotionEvents.get(0).getActionMasked());
        assertEquals(MotionEvent.ACTION_POINTER_DOWN, mMotionEvents.get(1).getActionMasked());

        // The second event must have two pointers at the initial spacing along a 45 degree angle
        MotionEvent firstEventWithTwoPointers = mMotionEvents.get(1);
        assertEquals(2, firstEventWithTwoPointers.getPointerCount());
        MotionEvent.PointerCoords coords0 = new MotionEvent.PointerCoords();
        MotionEvent.PointerCoords coords1 = new MotionEvent.PointerCoords();
        firstEventWithTwoPointers.getPointerCoords(0, coords0);
        firstEventWithTwoPointers.getPointerCoords(1, coords1);
        // Verify center point
        assertEquals((float) centerXInsideView, (coords0.x + coords1.x) / 2, pinchTolerance);
        assertEquals((float) centerYInsideView, (coords0.y + coords1.y) / 2, pinchTolerance);
        // Verify angle
        assertEquals(coords0.x - centerXInsideView, coords0.y - centerYInsideView, pinchTolerance);
        assertEquals(coords1.x - centerXInsideView, coords1.y - centerYInsideView, pinchTolerance);
        // Verify spacing
        assertEquals(startSpacing, distance(coords0, coords1), pinchTolerance);

        // The last two events are the pointer up and the final up
        assertEquals(MotionEvent.ACTION_UP, mMotionEvents.get(numEvents - 1).getActionMasked());

        MotionEvent lastEventWithTwoPointers = mMotionEvents.get(numEvents - 2);
        assertEquals(MotionEvent.ACTION_POINTER_UP, lastEventWithTwoPointers.getActionMasked());
        lastEventWithTwoPointers.getPointerCoords(0, coords0);
        lastEventWithTwoPointers.getPointerCoords(1, coords1);
        // Verify center point
        assertEquals((float) centerXInsideView, (coords0.x + coords1.x) / 2, pinchTolerance);
        assertEquals((float) centerYInsideView, (coords0.y + coords1.y) / 2, pinchTolerance);
        // Verify angle
        assertEquals(coords0.x - centerXInsideView, coords0.y - centerYInsideView, pinchTolerance);
        assertEquals(coords1.x - centerXInsideView, coords1.y - centerYInsideView, pinchTolerance);
        // Verify spacing
        assertEquals(endSpacing, distance(coords0, coords1), pinchTolerance);

        float lastSpacing = startSpacing;
        for (int i = 2; i < numEvents - 2; i++) {
            MotionEvent eventInMiddle = mMotionEvents.get(i);
            assertEquals(MotionEvent.ACTION_MOVE, eventInMiddle.getActionMasked());
            eventInMiddle.getPointerCoords(0, coords0);
            eventInMiddle.getPointerCoords(1, coords1);
            // Verify center point
            assertEquals((float) centerXInsideView, (coords0.x + coords1.x) / 2, pinchTolerance);
            assertEquals((float) centerYInsideView, (coords0.y + coords1.y) / 2, pinchTolerance);
            // Verify angle
            assertEquals(coords0.x - centerXInsideView, coords0.y - centerYInsideView,
                    pinchTolerance);
            assertEquals(coords1.x - centerXInsideView, coords1.y - centerYInsideView,
                    pinchTolerance);
            float spacing = distance(coords0, coords1);
            assertTrue(spacing <= lastSpacing + pinchTolerance);
            assertTrue(spacing >= endSpacing - pinchTolerance);
            lastSpacing = spacing;
        }
    }

    private void enableService() throws IOException {
        Context context = getInstrumentation().getContext();
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> serviceInfos =
                manager.getInstalledAccessibilityServiceList();
        for (int i = 0; i < serviceInfos.size(); i++) {
            AccessibilityServiceInfo serviceInfo = serviceInfos.get(i);
            if (context.getString(R.string.stub_gesture_a11y_service_description)
                    .equals(serviceInfo.getDescription())) {
                ContentResolver cr = context.getContentResolver();
                String enabledServices = Settings.Secure.getString(cr,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        enabledServices + COMPONENT_NAME_SEPARATOR + serviceInfo.getId());
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
                while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
                    synchronized(StubGestureAccessibilityService.sWaitObjectForConnecting) {
                        if (StubGestureAccessibilityService.sConnectedInstance != null) {
                            return;
                        }
                        try {
                            StubGestureAccessibilityService.sWaitObjectForConnecting.wait(
                                    timeoutTimeMillis - SystemClock.uptimeMillis());
                        } catch (InterruptedException e) {
                            // Ignored; loop again
                        }
                    }
                }
                throw new RuntimeException("Stub accessibility service not starting");
            }
        }
        throw new RuntimeException("Stub accessibility service not found");
    }

    private void turnAccessibilityOff() {
        final Object waitLockForA11yOff = new Object();
        Context context = getInstrumentation().getContext();
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        manager.addAccessibilityStateChangeListener(
                new AccessibilityManager.AccessibilityStateChangeListener() {
                    @Override
                    public void onAccessibilityStateChanged(boolean b) {
                        synchronized (waitLockForA11yOff) {
                            waitLockForA11yOff.notifyAll();
                        }
                    }
                });
        ContentResolver cr = context.getContentResolver();
        Settings.Secure.putString(
                cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null);
        StubGestureAccessibilityService.sConnectedInstance = null;
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (waitLockForA11yOff) {
                if (!manager.isEnabled()) {
                    return;
                }
                try {
                    waitLockForA11yOff.wait(timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Unable to turn accessibility off");
    }

    public static class GestureDispatchActivity extends AccessibilityTestActivity {
        public GestureDispatchActivity() {
            super();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.full_screen_frame_layout);
        }
    }

    public static class MyGestureCallback extends AccessibilityService.GestureResultCallback {
        private boolean mCompleted;
        private boolean mCancelled;

        @Override
        public synchronized void onCompleted(GestureDescription gestureDescription) {
            mCompleted = true;
            notifyAll();
        }

        @Override
        public synchronized void onCancelled(GestureDescription gestureDescription) {
            mCancelled = true;
            notifyAll();
        }

        public synchronized void assertGestureCompletes(long timeout) {
            if (mCompleted) {
                return;
            }
            try {
                wait(timeout);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue("Gesture did not complete.", mCompleted);
        }
    }

    private void waitForMotionEvents(int numEventsExpected) throws InterruptedException {
        synchronized (mMotionEvents) {
            long endMillis = SystemClock.uptimeMillis() + MOTION_EVENT_TIMEOUT;
            while ((mMotionEvents.size() < numEventsExpected)
                    && (SystemClock.uptimeMillis() < endMillis)) {
                mMotionEvents.wait(endMillis - SystemClock.uptimeMillis());
            }
        }
    }

    private void waitForUpEvent() throws InterruptedException {
        synchronized (mMotionEvents) {
            long endMillis = SystemClock.uptimeMillis() + MOTION_EVENT_TIMEOUT;
            while (!mGotUpEvent && (SystemClock.uptimeMillis() < endMillis)) {
                mMotionEvents.wait(endMillis - SystemClock.uptimeMillis());
            }
        }
    }

    private float distance(MotionEvent.PointerCoords point1, MotionEvent.PointerCoords point2) {
        return (float) Math.hypot((double) (point1.x - point2.x), (double) (point1.y - point2.y));
    }

    private class MyTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            synchronized (mMotionEvents) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
                    mGotUpEvent = true;
                }
                mMotionEvents.add(MotionEvent.obtain(motionEvent));
                mMotionEvents.notifyAll();
                return true;
            }
        }
    }
}
