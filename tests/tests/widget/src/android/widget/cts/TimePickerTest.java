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
import android.app.Instrumentation;
import android.content.Context;
import android.os.Parcelable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.AttributeSet;
import android.widget.TimePicker;

import static org.mockito.Mockito.*;

/**
 * Test {@link TimePicker}.
 */
@SmallTest
public class TimePickerTest extends ActivityInstrumentationTestCase2<TimePickerCtsActivity> {
    private TimePicker mTimePicker;
    private Activity mActivity;
    private Instrumentation mInstrumentation;

    public TimePickerTest() {
        super("android.widget.cts", TimePickerCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker);
    }

    public void testConstructors() {
        AttributeSet attrs =
            mActivity.getResources().getLayout(android.widget.cts.R.layout.timepicker);
        assertNotNull(attrs);

        new TimePicker(mActivity);
        try {
            new TimePicker(null);
            fail("did not throw NullPointerException when param context is null.");
        } catch (NullPointerException e) {
            // expected
        }

        new TimePicker(mActivity, attrs);
        try {
            new TimePicker(null, attrs);
            fail("did not throw NullPointerException when param context is null.");
        } catch (NullPointerException e) {
            // expected
        }
        new TimePicker(mActivity, null);

        new TimePicker(mActivity, attrs, 0);
        try {
            new TimePicker(null, attrs, 0);
            fail("did not throw NullPointerException when param context is null.");
        } catch (NullPointerException e) {
            // expected
        }
        new TimePicker(mActivity, null, 0);
        new TimePicker(mActivity, attrs, 0);
        new TimePicker(mActivity, null, android.R.attr.timePickerStyle);
        new TimePicker(mActivity, null, 0, android.R.style.Widget_Material_TimePicker);
        new TimePicker(mActivity, null, 0, android.R.style.Widget_Material_Light_TimePicker);
    }

    @UiThreadTest
    public void testSetEnabled() {
        assertTrue(mTimePicker.isEnabled());

        mTimePicker.setEnabled(false);
        assertFalse(mTimePicker.isEnabled());

        mTimePicker.setEnabled(true);
        assertTrue(mTimePicker.isEnabled());
    }

    public void testSetOnTimeChangedListener() {
        // On time change listener is notified on every call to setCurrentHour / setCurrentMinute.
        // We want to make sure that before we register our listener, we initialize the time picker
        // to the time that is explicitly different from the values we'll be testing for in both
        // hour and minute. Otherwise if the test happens to run at the time that ends in
        // "minuteForTesting" minutes, we'll get two onTimeChanged callbacks with identical values.
        final int initialHour = 10;
        final int initialMinute = 38;
        final int hourForTesting = 13;
        final int minuteForTesting = 50;

        mInstrumentation.runOnMainSync(() -> {
            mTimePicker.setHour(initialHour);
            mTimePicker.setMinute(initialMinute);
        });

        // Now register the listener
        TimePicker.OnTimeChangedListener mockOnTimeChangeListener =
                mock(TimePicker.OnTimeChangedListener.class);
        mTimePicker.setOnTimeChangedListener(mockOnTimeChangeListener);
        mInstrumentation.runOnMainSync(() -> {
                mTimePicker.setCurrentHour(Integer.valueOf(hourForTesting));
                mTimePicker.setCurrentMinute(Integer.valueOf(minuteForTesting));
        });
        // We're expecting two onTimeChanged callbacks, one with new hour and one with new
        // hour+minute
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting, initialMinute);
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting, minuteForTesting);

        // set the same hour as current
        reset(mockOnTimeChangeListener);
        mInstrumentation.runOnMainSync(
                () -> mTimePicker.setCurrentHour(Integer.valueOf(hourForTesting)));
        verifyZeroInteractions(mockOnTimeChangeListener);

        mInstrumentation.runOnMainSync(
                () -> mTimePicker.setCurrentHour(Integer.valueOf(hourForTesting + 1)));
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting + 1, minuteForTesting);

        // set the same minute as current
        reset(mockOnTimeChangeListener);
        mInstrumentation.runOnMainSync(() -> mTimePicker.setCurrentMinute(minuteForTesting));
        verifyZeroInteractions(mockOnTimeChangeListener);

        reset(mockOnTimeChangeListener);
        mInstrumentation.runOnMainSync(() -> mTimePicker.setCurrentMinute(minuteForTesting + 1));
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting + 1, minuteForTesting + 1);

        // change time picker mode
        reset(mockOnTimeChangeListener);
        mInstrumentation.runOnMainSync(
                () -> mTimePicker.setIs24HourView(!mTimePicker.is24HourView()));
        verifyZeroInteractions(mockOnTimeChangeListener);
    }

    @UiThreadTest
    public void testAccessCurrentHour() {
        // AM/PM mode
        mTimePicker.setIs24HourView(false);

        mTimePicker.setCurrentHour(0);
        assertEquals(Integer.valueOf(0), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(12);
        assertEquals(Integer.valueOf(12), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(13);
        assertEquals(Integer.valueOf(13), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(23);
        assertEquals(Integer.valueOf(23), mTimePicker.getCurrentHour());

        // for 24 hour mode
        mTimePicker.setIs24HourView(true);

        mTimePicker.setCurrentHour(0);
        assertEquals(Integer.valueOf(0), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(13);
        assertEquals(Integer.valueOf(13), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(23);
        assertEquals(Integer.valueOf(23), mTimePicker.getCurrentHour());
    }

    @UiThreadTest
    public void testAccessHour() {
        // AM/PM mode
        mTimePicker.setIs24HourView(false);

        mTimePicker.setHour(0);
        assertEquals(0, mTimePicker.getHour());

        mTimePicker.setHour(12);
        assertEquals(12, mTimePicker.getHour());

        mTimePicker.setHour(13);
        assertEquals(13, mTimePicker.getHour());

        mTimePicker.setHour(23);
        assertEquals(23, mTimePicker.getHour());

        // for 24 hour mode
        mTimePicker.setIs24HourView(true);

        mTimePicker.setHour(0);
        assertEquals(0, mTimePicker.getHour());

        mTimePicker.setHour(13);
        assertEquals(13, mTimePicker.getHour());

        mTimePicker.setHour(23);
        assertEquals(23, mTimePicker.getHour());
    }

    @UiThreadTest
    public void testAccessIs24HourView() {
        assertFalse(mTimePicker.is24HourView());

        mTimePicker.setIs24HourView(true);
        assertTrue(mTimePicker.is24HourView());

        mTimePicker.setIs24HourView(false);
        assertFalse(mTimePicker.is24HourView());
    }

    @UiThreadTest
    public void testAccessCurrentMinute() {
        mTimePicker.setCurrentMinute(0);
        assertEquals(Integer.valueOf(0), mTimePicker.getCurrentMinute());

        mTimePicker.setCurrentMinute(12);
        assertEquals(Integer.valueOf(12), mTimePicker.getCurrentMinute());

        mTimePicker.setCurrentMinute(33);
        assertEquals(Integer.valueOf(33), mTimePicker.getCurrentMinute());

        mTimePicker.setCurrentMinute(59);
        assertEquals(Integer.valueOf(59), mTimePicker.getCurrentMinute());
    }

    @UiThreadTest
    public void testAccessMinute() {
        mTimePicker.setMinute(0);
        assertEquals(0, mTimePicker.getMinute());

        mTimePicker.setMinute(12);
        assertEquals(12, mTimePicker.getMinute());

        mTimePicker.setMinute(33);
        assertEquals(33, mTimePicker.getMinute());

        mTimePicker.setMinute(59);
        assertEquals(59, mTimePicker.getMinute());
    }

    public void testGetBaseline() {
        assertEquals(-1, mTimePicker.getBaseline());
    }

    public void testOnSaveInstanceStateAndOnRestoreInstanceState() {
        MyTimePicker source = new MyTimePicker(mActivity);
        MyTimePicker dest = new MyTimePicker(mActivity);
        int expectHour = (dest.getCurrentHour() + 10) % 24;
        int expectMinute = (dest.getCurrentMinute() + 10) % 60;
        source.setCurrentHour(expectHour);
        source.setCurrentMinute(expectMinute);

        Parcelable p = source.onSaveInstanceState();
        dest.onRestoreInstanceState(p);

        assertEquals(Integer.valueOf(expectHour), dest.getCurrentHour());
        assertEquals(Integer.valueOf(expectMinute), dest.getCurrentMinute());
    }

    private class MyTimePicker extends TimePicker {
        public MyTimePicker(Context context) {
            super(context);
        }

        @Override
        protected void onRestoreInstanceState(Parcelable state) {
            super.onRestoreInstanceState(state);
        }

        @Override
        protected Parcelable onSaveInstanceState() {
            return super.onSaveInstanceState();
        }
    }
}
