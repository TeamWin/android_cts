/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.InputDevice;
import android.view.MotionEvent;


import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TouchDelegateTest {
    private Instrumentation mInstrumentation;
    private TouchDelegateTestActivity mActivity;

    @Rule
    public ActivityTestRule<TouchDelegateTestActivity> mActivityRule =
            new ActivityTestRule<>(TouchDelegateTestActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        mActivity.resetCounters();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testParentClick() {
        // If only clicking parent, button should not receive click
        clickParent();
        assertEquals(0, mActivity.getButtonClickCount());
        assertEquals(1, mActivity.getParentClickCount());

        // When clicking TouchDelegate area, both parent and button
        // should receive DOWN and UP events. However, click will only be generated for the button
        mActivity.resetCounters();
        clickTouchDelegateArea();
        assertEquals(1, mActivity.getButtonClickCount());
        assertEquals(0, mActivity.getParentClickCount());

        // Ensure parent can still receive clicks after TouchDelegate has been activated once
        mActivity.resetCounters();
        clickParent();
        assertEquals(0, mActivity.getButtonClickCount());
        assertEquals(1, mActivity.getParentClickCount());
    }

    @Test
    public void testCancelEvent() {
        // Ensure events with ACTION_CANCEL are received by the TouchDelegate
        final long downTime = SystemClock.uptimeMillis();
        dispatchMotionEventToActivity(MotionEvent.ACTION_DOWN, mActivity.touchDelegateY,
                downTime);
        dispatchMotionEventToActivity(MotionEvent.ACTION_CANCEL, mActivity.touchDelegateY,
                downTime);
        mInstrumentation.waitForIdleSync();

        MotionEvent event = mActivity.removeOldestButtonEvent();
        assertNotNull(event);
        assertEquals(MotionEvent.ACTION_DOWN, event.getAction());
        event.recycle();
        event = mActivity.removeOldestButtonEvent();
        assertNotNull(event);
        assertEquals(MotionEvent.ACTION_CANCEL, event.getAction());
        event.recycle();
        assertNull(mActivity.removeOldestButtonEvent());
        assertEquals(0, mActivity.getButtonClickCount());
        assertEquals(0, mActivity.getParentClickCount());
    }

    private void clickParent() {
        click(mActivity.parentViewY);
    }

    private void clickTouchDelegateArea() {
        click(mActivity.touchDelegateY);
    }

    // Low-level input-handling functions for the activity

    private void click(int y) {
        final long downTime = SystemClock.uptimeMillis();
        dispatchMotionEventToActivity(MotionEvent.ACTION_DOWN, y, downTime);
        dispatchMotionEventToActivity(MotionEvent.ACTION_UP, y, downTime);
        mInstrumentation.waitForIdleSync();
    }

    private void dispatchMotionEventToActivity(int action, int y, long downTime) {
        mActivity.runOnUiThread(() -> {
            final long eventTime = SystemClock.uptimeMillis();
            final MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                    mActivity.x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            mActivity.dispatchTouchEvent(event);
            event.recycle();
        });
    }
}
