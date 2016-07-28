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

import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.cts.util.CtsKeyEventUtil;
import android.cts.util.CtsTouchUtils;
import android.os.Parcelable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Test {@link TimePicker}.
 */
@MediumTest
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
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker_clock);
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

    public void testKeyboardTabTraversal_modeClock() {
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker_clock);

        mInstrumentation.runOnMainSync(() -> mTimePicker.setIs24HourView(false));
        mInstrumentation.waitForIdleSync();
        verifyTimePickerKeyboardTraversal(
                true /* goForward */,
                false /* is24HourView */,
                false /* isSpinner */);
        verifyTimePickerKeyboardTraversal(
                false /* goForward */,
                false /* is24HourView */,
                false /* isSpinner */);

        mInstrumentation.runOnMainSync(() -> mTimePicker.setIs24HourView(true));
        mInstrumentation.waitForIdleSync();
        verifyTimePickerKeyboardTraversal(
                true /* goForward */,
                true /* is24HourView */,
                false /* isSpinner */);
        verifyTimePickerKeyboardTraversal(
                false /* goForward */,
                true /* is24HourView */,
                false /* isSpinner */);
    }

    public void testKeyboardTabTraversal_modeSpinner() {
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker_spinner);

        mInstrumentation.runOnMainSync(() -> mTimePicker.setIs24HourView(false));
        mInstrumentation.waitForIdleSync();
        verifyTimePickerKeyboardTraversal(
                true /* goForward */,
                false /* is24HourView */,
                true /* isSpinner */);
        verifyTimePickerKeyboardTraversal(
                false /* goForward */,
                false /* is24HourView */,
                true /* isSpinner */);

        mInstrumentation.runOnMainSync(() -> mTimePicker.setIs24HourView(true));
        mInstrumentation.waitForIdleSync();
        verifyTimePickerKeyboardTraversal(
                true /* goForward */,
                true /* is24HourView */,
                true /* isSpinner */);
        verifyTimePickerKeyboardTraversal(
                false /* goForward */,
                true /* is24HourView */,
                true /* isSpinner */);
    }

    public void testKeyboardInputModeClockAmPm() {
        final int initialHour = 6;
        final int initialMinute = 59;
        prepareForKeyboardInput(initialHour, initialMinute, false);

        // Input valid hour.
        assertEquals(initialHour, mTimePicker.getHour());
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTimePicker.getHourView());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_0);
        assertEquals(10, mTimePicker.getHour());
        assertTrue(mTimePicker.getMinuteView().hasFocus());

        // Input valid minute.
        assertEquals(initialMinute, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(43, mTimePicker.getMinute());
        assertTrue(mTimePicker.getAmView().hasFocus());

        // Accepting AM changes nothing.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_ENTER);
        assertEquals(10, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());

        // Focus PM radio.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertTrue(mTimePicker.getPmView().hasFocus());
        // Still nothing has changed.
        assertEquals(10, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        // Select PM and verify the hour has changed.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_ENTER);
        assertEquals(22, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        // Set AM again.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getAmView().hasFocus());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_ENTER);
        assertEquals(10, mTimePicker.getHour());

        // Re-focus the hour view.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertTrue(mTimePicker.getHourView().hasFocus());

        // Input an invalid value (larger than 12).
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        // Force setting the hour by moving to minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        // After sending 1 and 3 only 1 is accepted.
        assertEquals(1, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        // The hour view still has focus.
        assertTrue(mTimePicker.getHourView().hasFocus());

        // This time send a valid hour (11).
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        // The value is valid.
        assertEquals(11, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());

        verifyModeClockMinuteInput();
    }

    public void testKeyboardInputModeClock24H() {
        final int initialHour = 6;
        final int initialMinute = 59;
        prepareForKeyboardInput(initialHour, initialMinute, true);

        // Input valid hour.
        assertEquals(initialHour, mTimePicker.getHour());
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTimePicker.getHourView());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_0);
        assertEquals(10, mTimePicker.getHour());
        assertTrue(mTimePicker.getMinuteView().hasFocus());

        // Input valid minute.
        assertEquals(initialMinute, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        assertEquals(43, mTimePicker.getMinute());

        // Re-focus the hour view.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getHourView().hasFocus());

        // Input an invalid value (larger than 24).
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_5);
        // Force setting the hour by moving to minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        // After sending 2 and 5 only 2 is accepted.
        assertEquals(2, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        // The hour view still has focus.
        assertTrue(mTimePicker.getHourView().hasFocus());

        // This time send a valid hour.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        // The value is valid.
        assertEquals(23, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());

        verifyModeClockMinuteInput();
    }

    private void verifyModeClockMinuteInput() {
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        // Send a invalid minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_6);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_7);
        // Sent 6 and 7 but only 6 was valid.
        assertEquals(6, mTimePicker.getMinute());
        // No matter what other invalid values we send, the minute is unchanged and the focus is
        // kept.
        // 61 invalid.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        // 62 invalid.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        // 63 invalid.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        assertEquals(6, mTimePicker.getMinute());
        // Refocus.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getMinuteView().hasFocus());

        // In the end pass a valid minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_5);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_9);
        assertEquals(59, mTimePicker.getMinute());
    }

    private void prepareForKeyboardInput(int initialHour, int initialMinute, boolean is24hFormat) {
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker_clock);
        final View spinnerTimePicker = mActivity.findViewById(R.id.timepicker_spinner);

        mInstrumentation.runOnMainSync(() -> {
            // Remove the spinner TimePicker.
            ((ViewGroup) spinnerTimePicker.getParent()).removeView(spinnerTimePicker);
            mTimePicker.setIs24HourView(is24hFormat);
            mTimePicker.setHour(initialHour);
            mTimePicker.setMinute(initialMinute);
            mTimePicker.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
    }

    private void verifyTimePickerKeyboardTraversal(boolean goForward, boolean is24HourView,
            boolean isSpinner) {
        ArrayList<View> forwardViews = new ArrayList<>();
        String summary = (goForward ? " forward " : " backward ")
                + "traversal, is24HourView=" + is24HourView
                + (isSpinner ? ", mode spinner" : ", mode clock");
        assertNotNull("Unexpected NULL hour view for" + summary, mTimePicker.getHourView());
        forwardViews.add(mTimePicker.getHourView());
        assertNotNull("Unexpected NULL minute view for" + summary, mTimePicker.getMinuteView());
        forwardViews.add(mTimePicker.getMinuteView());
        if (!is24HourView) {
            if (isSpinner) {
                // The spinner mode only contains one view for inputting AM/PM.
                assertNotNull("Unexpected NULL AM/PM view for" + summary, mTimePicker.getAmView());
                forwardViews.add(mTimePicker.getAmView());
            } else {
                assertNotNull("Unexpected NULL AM view for" + summary, mTimePicker.getAmView());
                forwardViews.add(mTimePicker.getAmView());
                assertNotNull("Unexpected NULL PM view for" + summary, mTimePicker.getPmView());
                forwardViews.add(mTimePicker.getPmView());
            }
        }

        if (!goForward) {
            Collections.reverse(forwardViews);
        }

        final int viewsSize = forwardViews.size();
        for (int i = 0; i < viewsSize; i++) {
            final View currentView = forwardViews.get(i);
            String afterKeyCodeFormattedString = "";
            int goForwardKeyCode = KeyEvent.KEYCODE_TAB;
            int modifierKeyCodeToHold = KeyEvent.KEYCODE_SHIFT_LEFT;

            if (i == 0) {
                // Make sure we always start by focusing the 1st element in the list.
                mInstrumentation.runOnMainSync(currentView::requestFocus);
            } else {
                if (goForward) {
                    afterKeyCodeFormattedString = " after pressing="
                            + KeyEvent.keyCodeToString(goForwardKeyCode);
                } else {
                    afterKeyCodeFormattedString = " after pressing="
                            + KeyEvent.keyCodeToString(modifierKeyCodeToHold)
                            + "+" + KeyEvent.keyCodeToString(goForwardKeyCode)  + " for" + summary;
                }
            }

            assertTrue("View='" + currentView + "'" + " with index " + i + " is not enabled"
                    + afterKeyCodeFormattedString + " for" + summary, currentView.isEnabled());
            assertTrue("View='" + currentView + "'" + " with index " + i + " is not focused"
                    + afterKeyCodeFormattedString + " for" + summary, currentView.isFocused());

            if (i < viewsSize - 1) {
                if (goForward) {
                    CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, currentView, goForwardKeyCode);
                } else {
                    CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, currentView,
                            goForwardKeyCode, modifierKeyCodeToHold);
                }
            }
        }
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
