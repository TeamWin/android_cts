/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.widget.cts;

import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.widget.NumberPicker;
import android.widget.cts.util.ViewTestUtils;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

@SmallTest
public class NumberPickerTest extends ActivityInstrumentationTestCase2<NumberPickerCtsActivity> {
    private static final String[] NUMBER_NAMES3 = {"One", "Two", "Three"};
    private static final String[] NUMBER_NAMES_ALT3 = {"Three", "Four", "Five"};
    private static final String[] NUMBER_NAMES5 = {"One", "Two", "Three", "Four", "Five"};

    private Instrumentation mInstrumentation;
    private NumberPickerCtsActivity mActivity;
    private NumberPicker mNumberPicker;

    public NumberPickerTest() {
        super("android.widget.cts", NumberPickerCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mNumberPicker = (NumberPicker) mActivity.findViewById(R.id.number_picker);
    }

    @UiThreadTest
    public void testConstructor() {
        new NumberPicker(mActivity);

        new NumberPicker(mActivity, null);

        new NumberPicker(mActivity, null, android.R.attr.numberPickerStyle);

        new NumberPicker(mActivity, null, 0, android.R.style.Widget_Material_NumberPicker);

        new NumberPicker(mActivity, null, 0, android.R.style.Widget_Material_Light_NumberPicker);
    }

    private void verifyDisplayedValues(String[] expected) {
        final String[] displayedValues = mNumberPicker.getDisplayedValues();
        assertEquals(expected.length, displayedValues.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], displayedValues[i]);
        }
    }

    public void testSetDisplayedValuesRangeMatch() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(10);
            mNumberPicker.setMaxValue(12);
            mNumberPicker.setDisplayedValues(NUMBER_NAMES3);
        });

        assertEquals(10, mNumberPicker.getMinValue());
        assertEquals(12, mNumberPicker.getMaxValue());
        verifyDisplayedValues(NUMBER_NAMES3);

        // Set a different displayed values array, but still matching the min/max range
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setDisplayedValues(NUMBER_NAMES_ALT3);
        });

        assertEquals(10, mNumberPicker.getMinValue());
        assertEquals(12, mNumberPicker.getMaxValue());
        verifyDisplayedValues(NUMBER_NAMES_ALT3);

        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(24);
            mNumberPicker.setMaxValue(26);
        });

        assertEquals(24, mNumberPicker.getMinValue());
        assertEquals(26, mNumberPicker.getMaxValue());
        verifyDisplayedValues(NUMBER_NAMES_ALT3);
    }

    public void testSetDisplayedValuesRangeMismatch() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(10);
            mNumberPicker.setMaxValue(14);
        });
        assertEquals(10, mNumberPicker.getMinValue());
        assertEquals(14, mNumberPicker.getMaxValue());

        // Try setting too few displayed entries
        mInstrumentation.runOnMainSync(() -> {
            try {
                // This is expected to fail since the displayed values only has three entries,
                // while the min/max range has five.
                mNumberPicker.setDisplayedValues(NUMBER_NAMES3);
                fail("The size of the displayed values array must be equal to min/max range!");
            } catch (Exception e) {
                // We are expecting to catch an exception. Set displayed values to an array that
                // matches the min/max range.
                mNumberPicker.setDisplayedValues(NUMBER_NAMES5);
            }
        });
    }

    public void testSelectionDisplayedValueFromDisplayedValues() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(1);
            mNumberPicker.setMaxValue(3);
            mNumberPicker.setDisplayedValues(NUMBER_NAMES3);
        });

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(1));
        assertTrue(TextUtils.equals(NUMBER_NAMES3[0],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(2));
        assertTrue(TextUtils.equals(NUMBER_NAMES3[1],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(3));
        assertTrue(TextUtils.equals(NUMBER_NAMES3[2],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        // Switch to a different displayed values array
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setDisplayedValues(NUMBER_NAMES_ALT3);
        });
        assertTrue(TextUtils.equals(NUMBER_NAMES_ALT3[2],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(1));
        assertTrue(TextUtils.equals(NUMBER_NAMES_ALT3[0],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(2));
        assertTrue(TextUtils.equals(NUMBER_NAMES_ALT3[1],
                mNumberPicker.getDisplayedValueForCurrentSelection()));
    }

    public void testSelectionDisplayedValueFromFormatter() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(0);
            mNumberPicker.setMaxValue(4);
            mNumberPicker.setFormatter((int value) -> "entry " + value);
        });

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(0));
        assertTrue(TextUtils.equals("entry 0",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(1));
        assertTrue(TextUtils.equals("entry 1",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(2));
        assertTrue(TextUtils.equals("entry 2",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(3));
        assertTrue(TextUtils.equals("entry 3",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(4));
        assertTrue(TextUtils.equals("entry 4",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        // Switch to a different formatter
        mInstrumentation.runOnMainSync(
                () -> mNumberPicker.setFormatter((int value) -> "row " + value));
        // Check that the currently selected value has new displayed value
        assertTrue(TextUtils.equals("row 4",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        // and check a couple more values for the new formatting
        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(0));
        assertTrue(TextUtils.equals("row 0",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(1));
        assertTrue(TextUtils.equals("row 1",
                mNumberPicker.getDisplayedValueForCurrentSelection()));
    }


    public void testSelectionDisplayedValuePrecedence() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(1);
            mNumberPicker.setMaxValue(3);
            mNumberPicker.setDisplayedValues(NUMBER_NAMES3);
            mNumberPicker.setFormatter((int value) -> "entry " + value);
        });

        // According to the widget documentation, displayed values take precedence over formatter
        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(1));
        assertTrue(TextUtils.equals(NUMBER_NAMES3[0],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(2));
        assertTrue(TextUtils.equals(NUMBER_NAMES3[1],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(3));
        assertTrue(TextUtils.equals(NUMBER_NAMES3[2],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        // Set displayed values to null and test that the widget is using the formatter
        mInstrumentation.runOnMainSync(() -> mNumberPicker.setDisplayedValues(null));
        assertTrue(TextUtils.equals("entry 3",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(1));
        assertTrue(TextUtils.equals("entry 1",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(2));
        assertTrue(TextUtils.equals("entry 2",
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        // Set a different displayed values array and test that it's taking precedence
        mInstrumentation.runOnMainSync(() -> mNumberPicker.setDisplayedValues(NUMBER_NAMES_ALT3));
        assertTrue(TextUtils.equals(NUMBER_NAMES_ALT3[1],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(1));
        assertTrue(TextUtils.equals(NUMBER_NAMES_ALT3[0],
                mNumberPicker.getDisplayedValueForCurrentSelection()));

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(3));
        assertTrue(TextUtils.equals(NUMBER_NAMES_ALT3[2],
                mNumberPicker.getDisplayedValueForCurrentSelection()));
    }

    public void testAccessValue() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(20);
            mNumberPicker.setMaxValue(22);
            mNumberPicker.setDisplayedValues(NUMBER_NAMES3);
        });

        final NumberPicker.OnValueChangeListener mockValueChangeListener =
                mock(NumberPicker.OnValueChangeListener.class);
        mNumberPicker.setOnValueChangedListener(mockValueChangeListener);

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(21));
        assertEquals(21, mNumberPicker.getValue());

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(20));
        assertEquals(20, mNumberPicker.getValue());

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(22));
        assertEquals(22, mNumberPicker.getValue());

        // Check trying to set value out of min/max range
        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(10));
        assertEquals(20, mNumberPicker.getValue());

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(100));
        assertEquals(22, mNumberPicker.getValue());

        // Since all changes to value are via API calls, we should have no interactions /
        // callbacks on our listener.
        verifyZeroInteractions(mockValueChangeListener);
    }

    public void testInteractionWithSwipeDown() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(6);
            mNumberPicker.setMaxValue(8);
            mNumberPicker.setDisplayedValues(NUMBER_NAMES_ALT3);
        });

        final NumberPicker.OnValueChangeListener mockValueChangeListener =
                mock(NumberPicker.OnValueChangeListener.class);
        mNumberPicker.setOnValueChangedListener(mockValueChangeListener);

        final NumberPicker.OnScrollListener mockScrollListener =
                mock(NumberPicker.OnScrollListener.class);
        mNumberPicker.setOnScrollListener(mockScrollListener);

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(7));
        assertEquals(7, mNumberPicker.getValue());

        // Swipe down across our number picker
        final int[] numberPickerLocationOnScreen = new int[2];
        mNumberPicker.getLocationOnScreen(numberPickerLocationOnScreen);

        ViewTestUtils.emulateDragGesture(mInstrumentation,
                numberPickerLocationOnScreen[0] + mNumberPicker.getWidth() / 2,
                numberPickerLocationOnScreen[1] + 1,
                0,
                mNumberPicker.getHeight() - 2);

        // At this point we expect that the drag-down gesture has selected the value
        // that was "above" the previously selected one, and that our value change listener
        // has been notified of that change exactly once.
        assertEquals(6, mNumberPicker.getValue());
        verify(mockValueChangeListener, times(1)).onValueChange(mNumberPicker, 7, 6);
        verifyNoMoreInteractions(mockValueChangeListener);

        // We expect that our scroll listener will be called with specific state changes.
        InOrder inOrder = inOrder(mockScrollListener);
        inOrder.verify(mockScrollListener).onScrollStateChange(mNumberPicker,
                NumberPicker.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
        inOrder.verify(mockScrollListener).onScrollStateChange(mNumberPicker,
                NumberPicker.OnScrollListener.SCROLL_STATE_IDLE);
        verifyNoMoreInteractions(mockScrollListener);
    }

    public void testInteractionWithSwipeUp() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(10);
            mNumberPicker.setMaxValue(12);
            mNumberPicker.setDisplayedValues(NUMBER_NAMES_ALT3);
        });

        final NumberPicker.OnValueChangeListener mockValueChangeListener =
                mock(NumberPicker.OnValueChangeListener.class);
        mNumberPicker.setOnValueChangedListener(mockValueChangeListener);

        final NumberPicker.OnScrollListener mockScrollListener =
                mock(NumberPicker.OnScrollListener.class);
        mNumberPicker.setOnScrollListener(mockScrollListener);

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setValue(11));
        assertEquals(11, mNumberPicker.getValue());

        // Swipe up across our number picker
        final int[] numberPickerLocationOnScreen = new int[2];
        mNumberPicker.getLocationOnScreen(numberPickerLocationOnScreen);

        ViewTestUtils.emulateDragGesture(mInstrumentation,
                numberPickerLocationOnScreen[0] + mNumberPicker.getWidth() / 2,
                numberPickerLocationOnScreen[1] + mNumberPicker.getHeight() - 1,
                0,
                - (mNumberPicker.getHeight() - 2));

        // At this point we expect that the drag-up gesture has selected the value
        // that was "below" the previously selected one, and that our value change listener
        // has been notified of that change exactly once.
        assertEquals(12, mNumberPicker.getValue());
        verify(mockValueChangeListener, times(1)).onValueChange(mNumberPicker, 11, 12);
        verifyNoMoreInteractions(mockValueChangeListener);

        // We expect that our scroll listener will be called with specific state changes.
        InOrder inOrder = inOrder(mockScrollListener);
        inOrder.verify(mockScrollListener).onScrollStateChange(mNumberPicker,
                NumberPicker.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
        inOrder.verify(mockScrollListener).onScrollStateChange(mNumberPicker,
                NumberPicker.OnScrollListener.SCROLL_STATE_IDLE);
        verifyNoMoreInteractions(mockScrollListener);
    }

    public void testAccessWrapSelectorValue() {
        mInstrumentation.runOnMainSync(() -> {
            mNumberPicker.setMinValue(100);
            mNumberPicker.setMaxValue(200);
        });
        // As specified in the Javadocs of NumberPicker.setWrapSelectorWheel, when min/max
        // range is larger than what the widget is showing, the selector wheel is enabled.
        assertTrue(mNumberPicker.getWrapSelectorWheel());

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setWrapSelectorWheel(false));
        assertFalse(mNumberPicker.getWrapSelectorWheel());

        mInstrumentation.runOnMainSync(() -> mNumberPicker.setWrapSelectorWheel(true));
        assertTrue(mNumberPicker.getWrapSelectorWheel());
    }
}
