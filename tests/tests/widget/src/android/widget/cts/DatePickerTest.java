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
import android.test.suitebuilder.annotation.MediumTest;
import android.util.SparseArray;
import android.view.View;
import android.widget.DatePicker;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.mockito.Mockito.*;

/**
 * Test {@link DatePicker}.
 */
@MediumTest
public class DatePickerTest extends ActivityInstrumentationTestCase2<DatePickerCtsActivity> {
    private Activity mActivity;
    private DatePicker mDatePickerSpinnerMode;
    private DatePicker mDatePickerCalendarMode;

    public DatePickerTest() {
        super("android.widget.cts", DatePickerCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mDatePickerSpinnerMode = (DatePicker) mActivity.findViewById(R.id.date_picker_spinner_mode);
        mDatePickerCalendarMode =
                (DatePicker) mActivity.findViewById(R.id.date_picker_calendar_mode);
    }

    public void testConstructor() {
        new DatePicker(mActivity);

        new DatePicker(mActivity, null);

        new DatePicker(mActivity, null, android.R.attr.datePickerStyle);

        new DatePicker(mActivity, null, 0, android.R.style.Widget_DeviceDefault_DatePicker);

        new DatePicker(mActivity, null, 0, android.R.style.Widget_Material_DatePicker);

        new DatePicker(mActivity, null, 0, android.R.style.Widget_Material_Light_DatePicker);
    }

    public void testSetEnabled() {
        final Instrumentation instrumentation = getInstrumentation();

        assertTrue(mDatePickerCalendarMode.isEnabled());

        instrumentation.runOnMainSync(() -> mDatePickerCalendarMode.setEnabled(false));
        assertFalse(mDatePickerCalendarMode.isEnabled());

        instrumentation.runOnMainSync(() -> mDatePickerCalendarMode.setEnabled(true));
        assertTrue(mDatePickerCalendarMode.isEnabled());
    }

    private void verifyInit(DatePicker datePicker) {
        final Instrumentation instrumentation = getInstrumentation();
        final DatePicker.OnDateChangedListener mockDateChangeListener =
                mock(DatePicker.OnDateChangedListener.class);

        instrumentation.runOnMainSync(
                () -> datePicker.init(2000, 10, 15, mockDateChangeListener));
        assertEquals(2000, datePicker.getYear());
        assertEquals(10, datePicker.getMonth());
        assertEquals(15, datePicker.getDayOfMonth());

        verifyZeroInteractions(mockDateChangeListener);
    }

    public void testInit() {
        verifyInit(mDatePickerSpinnerMode);
        verifyInit(mDatePickerCalendarMode);
    }

    private void verifyAccessDate(DatePicker datePicker) {
        final Instrumentation instrumentation = getInstrumentation();
        final DatePicker.OnDateChangedListener mockDateChangeListener =
                mock(DatePicker.OnDateChangedListener.class);

        instrumentation.runOnMainSync(() -> datePicker.init(2000, 10, 15, mockDateChangeListener));
        assertEquals(2000, datePicker.getYear());
        assertEquals(10, datePicker.getMonth());
        assertEquals(15, datePicker.getDayOfMonth());
        verify(mockDateChangeListener, never()).onDateChanged(any(DatePicker.class), anyInt(),
                anyInt(), anyInt());

        instrumentation.runOnMainSync(() -> datePicker.updateDate(1989, 9, 19));
        assertEquals(1989, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(19, datePicker.getDayOfMonth());
        verify(mockDateChangeListener, times(1)).onDateChanged(datePicker, 1989, 9, 19);

        verifyNoMoreInteractions(mockDateChangeListener);
    }

    public void testAccessDate() {
        verifyAccessDate(mDatePickerSpinnerMode);
        verifyAccessDate(mDatePickerCalendarMode);
    }

    private void verifySetOnDateChangedListener(DatePicker datePicker) {
        final Instrumentation instrumentation = getInstrumentation();
        final DatePicker.OnDateChangedListener mockDateChangeListener1 =
                mock(DatePicker.OnDateChangedListener.class);
        final DatePicker.OnDateChangedListener mockDateChangeListener2 =
                mock(DatePicker.OnDateChangedListener.class);

        instrumentation.runOnMainSync(() -> datePicker.init(2000, 10, 15, mockDateChangeListener1));
        instrumentation.runOnMainSync(() -> datePicker.updateDate(1989, 9, 19));
        assertEquals(1989, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(19, datePicker.getDayOfMonth());
        verify(mockDateChangeListener1, times(1)).onDateChanged(datePicker, 1989, 9, 19);
        verify(mockDateChangeListener2, times(0)).onDateChanged(datePicker, 1989, 9, 19);

        instrumentation.runOnMainSync(() -> datePicker.setOnDateChangedListener(
                mockDateChangeListener2));
        instrumentation.runOnMainSync(() -> datePicker.updateDate(2000, 10, 15));
        assertEquals(2000, datePicker.getYear());
        assertEquals(10, datePicker.getMonth());
        assertEquals(15, datePicker.getDayOfMonth());
        verify(mockDateChangeListener1, times(0)).onDateChanged(datePicker, 2000, 10, 15);
        verify(mockDateChangeListener2, times(1)).onDateChanged(datePicker, 2000, 10, 15);

    }

    public void testSetOnDateChangedListener() {
        verifySetOnDateChangedListener(mDatePickerSpinnerMode);
        verifySetOnDateChangedListener(mDatePickerCalendarMode);
    }

    private void verifyUpdateDate(DatePicker datePicker) {
        final Instrumentation instrumentation = getInstrumentation();

        instrumentation.runOnMainSync(() -> datePicker.updateDate(1989, 9, 19));
        assertEquals(1989, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(19, datePicker.getDayOfMonth());
    }

    public void testUpdateDate() {
        verifyUpdateDate(mDatePickerSpinnerMode);
        verifyUpdateDate(mDatePickerCalendarMode);
    }

    private void verifyMinMaxDate(DatePicker datePicker) {
        final Instrumentation instrumentation = getInstrumentation();

        // Use a range of minus/plus one year as min/max dates
        final Calendar minCalendar = new GregorianCalendar();
        minCalendar.set(Calendar.YEAR, minCalendar.get(Calendar.YEAR) - 1);
        final Calendar maxCalendar = new GregorianCalendar();
        maxCalendar.set(Calendar.YEAR, maxCalendar.get(Calendar.YEAR) + 1);

        final long minDate = minCalendar.getTime().getTime();
        final long maxDate = maxCalendar.getTime().getTime();

        instrumentation.runOnMainSync(() -> {
            datePicker.setMinDate(minDate);
            datePicker.setMaxDate(maxDate);
        });

        assertEquals(datePicker.getMinDate(), minDate);
        assertEquals(datePicker.getMaxDate(), maxDate);
    }

    public void testMinMaxDate() {
        verifyMinMaxDate(mDatePickerSpinnerMode);
        verifyMinMaxDate(mDatePickerCalendarMode);
    }

    private void verifyFirstDayOfWeek(DatePicker datePicker) {
        final Instrumentation instrumentation = getInstrumentation();

        instrumentation.runOnMainSync(() -> datePicker.setFirstDayOfWeek(Calendar.TUESDAY));
        assertEquals(Calendar.TUESDAY, datePicker.getFirstDayOfWeek());

        instrumentation.runOnMainSync(() -> datePicker.setFirstDayOfWeek(Calendar.SUNDAY));
        assertEquals(Calendar.SUNDAY, datePicker.getFirstDayOfWeek());
    }

    public void testFirstDayOfWeek() {
        verifyFirstDayOfWeek(mDatePickerSpinnerMode);
        verifyFirstDayOfWeek(mDatePickerCalendarMode);
    }

    public void testCalendarViewInSpinnerMode() {
        final Instrumentation instrumentation = getInstrumentation();

        assertNotNull(mDatePickerSpinnerMode.getCalendarView());

        // Update the DatePicker and test that its CalendarView is synced to the same date
        final Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.YEAR, 2008);
        calendar.set(Calendar.MONTH, Calendar.SEPTEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 23);
        instrumentation.runOnMainSync(
                () -> mDatePickerSpinnerMode.updateDate(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)));

        final Calendar calendarFromSpinner = new GregorianCalendar();
        final long timeFromSpinnerCalendar = mDatePickerSpinnerMode.getCalendarView().getDate();
        calendarFromSpinner.setTimeInMillis(timeFromSpinnerCalendar);

        assertEquals(calendar.get(Calendar.YEAR), calendarFromSpinner.get(Calendar.YEAR));
        assertEquals(calendar.get(Calendar.MONTH), calendarFromSpinner.get(Calendar.MONTH));
        assertEquals(calendar.get(Calendar.DAY_OF_MONTH),
                calendarFromSpinner.get(Calendar.DAY_OF_MONTH));
    }

    public void testPartsVisibilityInSpinnerMode() {
        final Instrumentation instrumentation = getInstrumentation();

        assertTrue(mDatePickerSpinnerMode.getSpinnersShown());
        assertTrue(mDatePickerSpinnerMode.getCalendarViewShown());

        instrumentation.runOnMainSync(() -> mDatePickerSpinnerMode.setSpinnersShown(false));
        assertFalse(mDatePickerSpinnerMode.getSpinnersShown());
        assertTrue(mDatePickerSpinnerMode.getCalendarViewShown());

        instrumentation.runOnMainSync(() -> mDatePickerSpinnerMode.setCalendarViewShown(false));
        assertFalse(mDatePickerSpinnerMode.getSpinnersShown());
        assertFalse(mDatePickerSpinnerMode.getCalendarViewShown());

        instrumentation.runOnMainSync(() -> mDatePickerSpinnerMode.setSpinnersShown(true));
        assertTrue(mDatePickerSpinnerMode.getSpinnersShown());
        assertFalse(mDatePickerSpinnerMode.getCalendarViewShown());

        instrumentation.runOnMainSync(() -> mDatePickerSpinnerMode.setCalendarViewShown(true));
        assertTrue(mDatePickerSpinnerMode.getSpinnersShown());
        assertTrue(mDatePickerSpinnerMode.getCalendarViewShown());
    }

    @UiThreadTest
    public void testAccessInstanceState() {
        MockDatePicker datePicker = new MockDatePicker(mActivity);

        datePicker.updateDate(2008, 9, 10);
        SparseArray<Parcelable> container = new SparseArray<Parcelable>();

        // Test saveHierarchyState -> onSaveInstanceState path
        assertEquals(View.NO_ID, datePicker.getId());
        datePicker.setId(99);
        assertFalse(datePicker.hasCalledOnSaveInstanceState());
        datePicker.saveHierarchyState(container);
        assertEquals(1, datePicker.getChildCount());
        assertTrue(datePicker.hasCalledOnSaveInstanceState());

        // Test dispatchRestoreInstanceState -> onRestoreInstanceState path
        datePicker = new MockDatePicker(mActivity);
        datePicker.setId(99);
        assertFalse(datePicker.hasCalledOnRestoreInstanceState());
        datePicker.dispatchRestoreInstanceState(container);
        assertEquals(2008, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(10, datePicker.getDayOfMonth());
        assertTrue(datePicker.hasCalledOnRestoreInstanceState());
    }

    private class MockDatePicker extends DatePicker {
        private boolean mCalledOnSaveInstanceState = false;
        private boolean mCalledOnRestoreInstanceState = false;

        public MockDatePicker(Context context) {
            super(context);
        }

        @Override
        protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
            super.dispatchRestoreInstanceState(container);
        }

        @Override
        protected Parcelable onSaveInstanceState() {
            mCalledOnSaveInstanceState = true;
            return super.onSaveInstanceState();
        }

        public boolean hasCalledOnSaveInstanceState() {
            return mCalledOnSaveInstanceState;
        }

        @Override
        protected void onRestoreInstanceState(Parcelable state) {
            mCalledOnRestoreInstanceState = true;
            super.onRestoreInstanceState(state);
        }

        public boolean hasCalledOnRestoreInstanceState() {
            return mCalledOnRestoreInstanceState;
        }
    }
}
