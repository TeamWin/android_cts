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
import android.cts.util.CtsTouchUtils;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.widget.RadioButton;
import android.widget.cts.R;

@SmallTest
public class RadioButtonTest extends ActivityInstrumentationTestCase2<RadioButtonCtsActivity> {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private RadioButton mRadioButton;

    public RadioButtonTest() {
        super("android.widget.cts", RadioButtonCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mRadioButton = (RadioButton) mActivity.findViewById(R.id.radio_button);
    }

    public void testConstructor() {
        new RadioButton(mActivity);
        new RadioButton(mActivity, null);
        new RadioButton(mActivity, null, android.R.attr.radioButtonStyle);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_CompoundButton_RadioButton);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_CompoundButton_RadioButton);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_Material_CompoundButton_RadioButton);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_Material_Light_CompoundButton_RadioButton);

        try {
            new RadioButton(null);
            fail("The constructor should throw NullPointerException when param Context is null.");
        } catch (NullPointerException e) {
        }

        try {
            new RadioButton(null, null);
            fail("The constructor should throw NullPointerException when param Context is null.");
        } catch (NullPointerException e) {
        }
        try {
            new RadioButton(null, null, 0);
            fail("The constructor should throw NullPointerException when param Context is null.");
        } catch (NullPointerException e) {
        }
    }

    public void testText() {
        assertTrue(TextUtils.equals(
                mActivity.getString(R.string.hello_world), mRadioButton.getText()));

        mInstrumentation.runOnMainSync(() -> mRadioButton.setText("new text"));
        assertTrue(TextUtils.equals("new text", mRadioButton.getText()));

        mInstrumentation.runOnMainSync(() -> mRadioButton.setText(R.string.text_name));
        assertTrue(TextUtils.equals(
                mActivity.getString(R.string.text_name), mRadioButton.getText()));
    }

    public void testAccessChecked() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // not checked -> not checked
        mInstrumentation.runOnMainSync(() -> mRadioButton.setChecked(false));
        verifyZeroInteractions(mockCheckedChangeListener);
        assertFalse(mRadioButton.isChecked());

        // not checked -> checked
        mInstrumentation.runOnMainSync(() -> mRadioButton.setChecked(true));
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // checked -> checked
        mInstrumentation.runOnMainSync(() -> mRadioButton.setChecked(true));
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // checked -> not checked
        mInstrumentation.runOnMainSync(() -> mRadioButton.setChecked(false));
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, false);
        assertFalse(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    public void testToggleViaApi() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // toggle to checked
        mInstrumentation.runOnMainSync(() -> mRadioButton.toggle());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // try toggle to not checked - this should leave the radio button in checked state
        mInstrumentation.runOnMainSync(() -> mRadioButton.toggle());
        assertTrue(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    public void testToggleViaEmulatedTap() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // tap to checked
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mRadioButton);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // tap to not checked - this should leave the radio button in checked state
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mRadioButton);
        assertTrue(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    public void testToggleViaPerformClick() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // click to checked
        mInstrumentation.runOnMainSync(() -> mRadioButton.performClick());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // click to not checked - this should leave the radio button in checked state
        mInstrumentation.runOnMainSync(() -> mRadioButton.performClick());
        assertTrue(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }
}
