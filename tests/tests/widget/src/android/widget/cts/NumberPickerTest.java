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
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.NumberPicker;

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

    private void verifyDisplayedValues(String[] expected) {
        final String[] displayedValues = mNumberPicker.getDisplayedValues();
        assertEquals(expected.length, displayedValues.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], displayedValues[i]);
        }
    }

    public void testSetDisplayedValues() {
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

    public void testSetDisplayedValuesMismatch() {
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
}
