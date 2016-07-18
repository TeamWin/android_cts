/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;

import android.app.Activity;
import android.cts.util.CtsTouchUtils;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;
import android.widget.ZoomButton;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SmallTest
public class ZoomButtonTest extends ActivityInstrumentationTestCase2<ZoomButtonCtsActivity> {
    private static long NANOS_IN_MILLI = 1000000;
    private ZoomButton mZoomButton;
    private Activity mActivity;

    public ZoomButtonTest() {
        super("android.widget.cts", ZoomButtonCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mZoomButton = (ZoomButton) getActivity().findViewById(R.id.zoombutton_test);
        mActivity = getActivity();
    }

    @UiThreadTest
    public void testConstructor() {
        new ZoomButton(mActivity);

        new ZoomButton(mActivity, null);

        new ZoomButton(mActivity, null, android.R.attr.imageButtonStyle);

        new ZoomButton(mActivity, null, 0, android.R.style.Widget_Material_Light_ImageButton);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.zoombutton_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        assertNotNull(attrs);
        new ZoomButton(mActivity, attrs);
        new ZoomButton(mActivity, attrs, 0);

        try {
            new ZoomButton(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }

        try {
            new ZoomButton(null, null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }

        try {
            new ZoomButton(null, null, 0);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @UiThreadTest
    public void testSetEnabled() {
        assertFalse(mZoomButton.isPressed());
        mZoomButton.setEnabled(true);
        assertTrue(mZoomButton.isEnabled());
        assertFalse(mZoomButton.isPressed());

        mZoomButton.setPressed(true);
        assertTrue(mZoomButton.isPressed());
        mZoomButton.setEnabled(true);
        assertTrue(mZoomButton.isEnabled());
        assertTrue(mZoomButton.isPressed());

        mZoomButton.setEnabled(false);
        assertFalse(mZoomButton.isEnabled());
        assertFalse(mZoomButton.isPressed());
    }

    @UiThreadTest
    public void testDispatchUnhandledMove() {
        assertFalse(mZoomButton.dispatchUnhandledMove(new ListView(mActivity), View.FOCUS_DOWN));

        assertFalse(mZoomButton.dispatchUnhandledMove(null, View.FOCUS_DOWN));
    }

    private void verifyZoomSpeed(ZoomClickListener zoomClickListener, long zoomSpeedMs) {
        mZoomButton.setZoomSpeed(zoomSpeedMs);

        final long startTime = System.nanoTime();
        // Emulate long click that "lasts" for ten seconds
        CtsTouchUtils.emulateLongClick(getInstrumentation(), mZoomButton, 10000);

        final List<Long> callbackInvocations = zoomClickListener.getClickTimes();
        assertFalse("Expecting at least one callback", callbackInvocations.isEmpty());

        // Verify that the first callback is fired after the system-level long press timeout.
        final long minTimeUntilFirstInvocationMs = ViewConfiguration.getLongPressTimeout();
        final long actualTimeUntilFirstInvocationNs = callbackInvocations.get(0) - startTime;
        assertTrue("First callback not during long press timeout was " +
                        actualTimeUntilFirstInvocationNs / NANOS_IN_MILLI +
                        " while long press timeout is " + minTimeUntilFirstInvocationMs,
                (callbackInvocations.get(0) - startTime) >
                        minTimeUntilFirstInvocationMs * NANOS_IN_MILLI);

        // Verify that subsequent callbacks are at least zoom-speed milliseconds apart. Note that
        // we do not have any hard guarantee about the max limit on the time between successive
        // callbacks.
        final long minTimeBetweenInvocationsNs = zoomSpeedMs * NANOS_IN_MILLI;
        if (callbackInvocations.size() > 1) {
            for (int i = 0; i < callbackInvocations.size() - 1; i++) {
                final long actualTimeBetweenInvocationsNs =
                        (callbackInvocations.get(i + 1) - callbackInvocations.get(i)) *
                                NANOS_IN_MILLI;
                assertTrue("Callback " + (i + 1) + " happened " +
                                actualTimeBetweenInvocationsNs / NANOS_IN_MILLI +
                                " after the previous one, while zoom speed is " + zoomSpeedMs,
                        actualTimeBetweenInvocationsNs > minTimeBetweenInvocationsNs);
            }
        }
    }

    @LargeTest
    public void testOnLongClick() {
        // Since Mockito doesn't have utilities to track the timestamps of method invocations,
        // we're using our own custom click listener for that. We want to verify that the
        // first listener invocation was after long press timeout, and the rest were spaced
        // by at least our zoom speed milliseconds

        mZoomButton.setEnabled(true);
        ZoomClickListener zoomClickListener = new ZoomClickListener();
        mZoomButton.setOnClickListener(zoomClickListener);

        verifyZoomSpeed(zoomClickListener, 2000);
    }

    public void testOnTouchEvent() {
        // Do not test. Implementation details.
    }

    public void testOnKeyUp() {
        // Do not test. Implementation details.
    }

    @LargeTest
    public void testSetZoomSpeed() {
        final long[] zoomSpeeds = { 100, -1, 5000, 1000, 2500 };
        mZoomButton.setEnabled(true);
        ZoomClickListener zoomClickListener = new ZoomClickListener();
        mZoomButton.setOnClickListener(zoomClickListener);

        for (long zoomSpeed : zoomSpeeds) {
            // Reset the tracker list of our listener, but continue using it for testing
            // various zoom speeds on the same ZoomButton
            zoomClickListener.reset();
            verifyZoomSpeed(zoomClickListener, zoomSpeed);
        }
    }

    private static class ZoomClickListener implements View.OnClickListener {
        private List<Long> mClickTimes = new ArrayList<>();

        public void reset() {
            mClickTimes.clear();
        }

        public List<Long> getClickTimes() {
            return Collections.unmodifiableList(mClickTimes);
        }

        public void onClick(View v) {
            // Add the current system time to the tracker list
            mClickTimes.add(System.nanoTime());
        }
    }
}
