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
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.cts.util.ViewTestUtils;

@SmallTest
public class CheckBoxTest extends ActivityInstrumentationTestCase2<CheckBoxCtsActivity> {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private CheckBox mCheckBox;

    public CheckBoxTest() {
        super("android.widget.cts", CheckBoxCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mCheckBox = (CheckBox) mActivity.findViewById(R.id.check_box);
    }

    public void testConstructor() {
        new CheckBox(mActivity);
        new CheckBox(mActivity, null);
        new CheckBox(mActivity, null, android.R.attr.checkboxStyle);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_CompoundButton_CheckBox);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_CompoundButton_CheckBox);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_Material_CompoundButton_CheckBox);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_Material_Light_CompoundButton_CheckBox);

        try {
            new CheckBox(null, null, -1);
            fail("Should throw NullPointerException.");
        } catch (NullPointerException e) {
            // expected, test success.
        }

        try {
            new CheckBox(null, null);
            fail("Should throw NullPointerException.");
        } catch (NullPointerException e) {
            // expected, test success.
        }

        try {
            new CheckBox(null);
            fail("Should throw NullPointerException.");
        } catch (NullPointerException e) {
            // expected, test success.
        }
    }

    public void testText() {
        assertTrue(TextUtils.equals(
                mActivity.getString(R.string.hello_world), mCheckBox.getText()));

        mInstrumentation.runOnMainSync(() -> mCheckBox.setText("new text"));
        assertTrue(TextUtils.equals("new text", mCheckBox.getText()));

        mInstrumentation.runOnMainSync(() -> mCheckBox.setText(R.string.text_name));
        assertTrue(TextUtils.equals(mActivity.getString(R.string.text_name), mCheckBox.getText()));
    }

    public void testAccessChecked() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // not checked -> not checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.setChecked(false));
        verifyZeroInteractions(mockCheckedChangeListener);
        assertFalse(mCheckBox.isChecked());

        // not checked -> checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.setChecked(true));
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // checked -> checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.setChecked(true));
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // checked -> not checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.setChecked(false));
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    public void testToggleViaApi() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // toggle to checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.toggle());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // toggle to not checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.toggle());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    public void testToggleViaEmulatedTap() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // tap to checked
        ViewTestUtils.emulateTapOnViewCenter(mInstrumentation, mCheckBox);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // tap to not checked
        ViewTestUtils.emulateTapOnViewCenter(mInstrumentation, mCheckBox);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    public void testToggleViaPerformClick() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // click to checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.performClick());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // click to not checked
        mInstrumentation.runOnMainSync(() -> mCheckBox.performClick());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }
}
