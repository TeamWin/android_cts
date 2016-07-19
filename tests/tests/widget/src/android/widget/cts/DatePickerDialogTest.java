/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link DatePickerDialog}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class DatePickerDialogTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private DatePickerDialog mDatePickerDialog;

    @Rule
    public ActivityTestRule<DatePickerDialogCtsActivity> mActivityRule
            = new ActivityTestRule<>(DatePickerDialogCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        mInstrumentation.runOnMainSync(() -> {
                new DatePickerDialog(mActivity, null, 1970, 1, 1);

                new DatePickerDialog(mActivity, AlertDialog.THEME_TRADITIONAL, null, 1970, 1, 1);

                new DatePickerDialog(mActivity, AlertDialog.THEME_HOLO_DARK, null, 1970, 1, 1);

                new DatePickerDialog(mActivity,
                        android.R.style.Theme_Material_Dialog_Alert, null, 1970, 1, 1);
        });
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext() {
        new DatePickerDialog(null, null, 1970, 1, 1);
    }

    @Test
    public void testShowDismiss() {
        mInstrumentation.runOnMainSync(
                () -> mDatePickerDialog = new DatePickerDialog(mActivity, null, 1970, 1, 1));

        mInstrumentation.runOnMainSync(mDatePickerDialog::show);
        assertTrue("Showing date picker", mDatePickerDialog.isShowing());

        mInstrumentation.runOnMainSync(mDatePickerDialog::show);
        assertTrue("Date picker still showing", mDatePickerDialog.isShowing());

        mInstrumentation.runOnMainSync(mDatePickerDialog::dismiss);
        assertFalse("Dismissed date picker", mDatePickerDialog.isShowing());

        mInstrumentation.runOnMainSync(mDatePickerDialog::dismiss);
        assertFalse("Date picker still dismissed", mDatePickerDialog.isShowing());
    }
}
