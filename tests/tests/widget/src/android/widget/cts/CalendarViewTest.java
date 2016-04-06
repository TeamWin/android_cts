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

package android.widget.cts;

import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.CalendarView;

import java.util.Calendar;
import java.util.GregorianCalendar;

@MediumTest
public class CalendarViewTest extends ActivityInstrumentationTestCase2<CalendarViewCtsActivity> {
    private CalendarViewCtsActivity mActivity;
    private CalendarView mCalendarViewMaterial;
    private CalendarView mCalendarViewHolo;

    public CalendarViewTest() {
        super("android.widget.cts", CalendarViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mCalendarViewMaterial = (CalendarView) mActivity.findViewById(R.id.calendar_view_material);
        mCalendarViewHolo = (CalendarView) mActivity.findViewById(R.id.calendar_view_holoyolo);

        // Initialize both calendar views to the current date
        final long currentDate = new GregorianCalendar().getTime().getTime();
        getInstrumentation().runOnMainSync(() -> {
            mCalendarViewMaterial.setDate(currentDate);
            mCalendarViewHolo.setDate(currentDate);
        });
    }

    public void testConstructor() {
        new CalendarView(mActivity);

        new CalendarView(mActivity, null);

        new CalendarView(mActivity, null, android.R.attr.calendarViewStyle);

        new CalendarView(mActivity, null, 0, android.R.style.Widget_Material_Light_CalendarView);
    }

    public void testAccessDate() {
        final Instrumentation instrumentation = getInstrumentation();

        // Go back one year
        final Calendar newCalendar = new GregorianCalendar();
        newCalendar.set(Calendar.YEAR, newCalendar.get(Calendar.YEAR) - 1);
        final long yearAgoDate = newCalendar.getTime().getTime();

        instrumentation.runOnMainSync(
                () -> mCalendarViewMaterial.setDate(yearAgoDate));
        assertEquals(yearAgoDate, mCalendarViewMaterial.getDate());

        // Go forward two years (one year from current date in aggregate)
        newCalendar.set(Calendar.YEAR, newCalendar.get(Calendar.YEAR) + 2);
        final long yearHenceDate = newCalendar.getTime().getTime();

        instrumentation.runOnMainSync(
                () -> mCalendarViewMaterial.setDate(yearHenceDate, true, false));
        assertEquals(yearHenceDate, mCalendarViewMaterial.getDate());
    }

    public void testAccessMinMaxDate() {
        final Instrumentation instrumentation = getInstrumentation();

        // Use a range of minus/plus one year as min/max dates
        final Calendar minCalendar = new GregorianCalendar();
        minCalendar.set(Calendar.YEAR, minCalendar.get(Calendar.YEAR) - 1);
        final Calendar maxCalendar = new GregorianCalendar();
        maxCalendar.set(Calendar.YEAR, maxCalendar.get(Calendar.YEAR) + 1);

        final long minDate = minCalendar.getTime().getTime();
        final long maxDate = maxCalendar.getTime().getTime();

        instrumentation.runOnMainSync(() -> {
            mCalendarViewMaterial.setMinDate(minDate);
            mCalendarViewMaterial.setMaxDate(maxDate);
        });

        assertEquals(mCalendarViewMaterial.getMinDate(), minDate);
        assertEquals(mCalendarViewMaterial.getMaxDate(), maxDate);
    }
}
