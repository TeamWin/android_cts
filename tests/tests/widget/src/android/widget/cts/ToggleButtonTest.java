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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.ToggleButton;
import android.widget.cts.R;


/**
 * Test {@link ToggleButton}.
 */
@SmallTest
public class ToggleButtonTest extends ActivityInstrumentationTestCase2<ToggleButtonCtsActivity> {
    private static final String TEXT_OFF = "text off";
    private static final String TEXT_ON = "text on";

    private Instrumentation mInstrumentation;
    private Activity mActivity;

    public ToggleButtonTest() {
        super("android.widget.cts", ToggleButtonCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
    }

    public void testConstructor() {
        new ToggleButton(mActivity);
        new ToggleButton(mActivity, null);
        new ToggleButton(mActivity, null, android.R.attr.buttonStyleToggle);
        new ToggleButton(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Button_Toggle);
        new ToggleButton(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_Button_Toggle);
        new ToggleButton(mActivity, null, 0, android.R.style.Widget_Material_Button_Toggle);
        new ToggleButton(mActivity, null, 0, android.R.style.Widget_Material_Light_Button_Toggle);

        try {
            new ToggleButton(null, null, -1);
            fail("There should be a NullPointerException thrown out.");
        } catch (NullPointerException e) {
            // expected, test success.
        }

        try {
            new ToggleButton(null, null);
            fail("There should be a NullPointerException thrown out.");
        } catch (NullPointerException e) {
            // expected, test success.
        }

        try {
            new ToggleButton(null);
            fail("There should be a NullPointerException thrown out.");
        } catch (NullPointerException e) {
            // expected, test success.
        }
    }

    public void testAttributesFromStyle() {
        final ToggleButton toggleButton =
                (ToggleButton) mActivity.findViewById(R.id.toggle_with_style);
        assertEquals(mActivity.getString(R.string.toggle_text_on), toggleButton.getTextOn());
        assertEquals(mActivity.getString(R.string.toggle_text_off), toggleButton.getTextOff());
    }

    public void testAttributesFromLayout() {
        final ToggleButton toggleButton =
                (ToggleButton) mActivity.findViewById(R.id.toggle_with_defaults);
        assertEquals(mActivity.getString(R.string.toggle_text_on_alt), toggleButton.getTextOn());
        assertEquals(mActivity.getString(R.string.toggle_text_off_alt), toggleButton.getTextOff());
    }

    public void testAccessTextOff() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        mInstrumentation.runOnMainSync(() -> toggleButton.setTextOff("android"));
        assertEquals("android", toggleButton.getTextOff());
        mInstrumentation.runOnMainSync(() -> toggleButton.setChecked(false));

        mInstrumentation.runOnMainSync(() -> toggleButton.setTextOff(null));
        assertNull(toggleButton.getTextOff());

        mInstrumentation.runOnMainSync(() -> toggleButton.setTextOff(""));
        assertEquals("", toggleButton.getTextOff());
    }

    public void testDrawableStateChanged() {
        final MockToggleButton toggleButton = new MockToggleButton(mActivity);

        // drawableStateChanged without any drawable.
        mInstrumentation.runOnMainSync(() -> toggleButton.drawableStateChanged());

        final StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[] { android.R.attr.state_pressed },
                mActivity.getDrawable(R.drawable.scenery));
        drawable.addState(new int[] {},
                mActivity.getDrawable(R.drawable.scenery));

        // drawableStateChanged when CheckMarkDrawable is not null.
        mInstrumentation.runOnMainSync(() -> toggleButton.setButtonDrawable(drawable));
        drawable.setState(null);
        assertNull(drawable.getState());

        mInstrumentation.runOnMainSync(() -> toggleButton.drawableStateChanged());
        assertNotNull(drawable.getState());
        assertEquals(toggleButton.getDrawableState(), drawable.getState());
    }

    public void testOnFinishInflate() {
        MockToggleButton toggleButton = new MockToggleButton(mActivity);
        toggleButton.onFinishInflate();
    }

    public void testSetChecked() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        assertFalse(toggleButton.isChecked());

        mInstrumentation.runOnMainSync(() -> toggleButton.setChecked(true));
        assertTrue(toggleButton.isChecked());

        mInstrumentation.runOnMainSync(() -> toggleButton.setChecked(false));
        assertFalse(toggleButton.isChecked());
    }

    public void testToggleText() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        mInstrumentation.runOnMainSync(() -> {
            toggleButton.setText("default text");
            toggleButton.setTextOn(TEXT_ON);
            toggleButton.setTextOff(TEXT_OFF);
            toggleButton.setChecked(true);
        });
        assertEquals(TEXT_ON, toggleButton.getText().toString());
        toggleButton.setChecked(false);
        assertFalse(toggleButton.isChecked());
        assertEquals(TEXT_OFF, toggleButton.getText().toString());

        // Set the current displaying text as TEXT_OFF.
        // Then set checked button, but textOn is null.
        mInstrumentation.runOnMainSync(() -> {
            toggleButton.setTextOff(TEXT_OFF);
            toggleButton.setChecked(false);
            toggleButton.setTextOn(null);
            toggleButton.setChecked(true);
        });
        assertEquals(TEXT_OFF, toggleButton.getText().toString());

        // Set the current displaying text as TEXT_ON. Then set unchecked button,
        // but textOff is null.
        mInstrumentation.runOnMainSync(() -> {
            toggleButton.setTextOn(TEXT_ON);
            toggleButton.setChecked(true);
            toggleButton.setTextOff(null);
            toggleButton.setChecked(false);
        });
        assertEquals(TEXT_ON, toggleButton.getText().toString());
    }

    public void testSetBackgroundDrawable() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        final Drawable drawable = mActivity.getDrawable(R.drawable.scenery);

        mInstrumentation.runOnMainSync(() -> toggleButton.setBackgroundDrawable(drawable));
        assertSame(drawable, toggleButton.getBackground());

        // remove the background
        mInstrumentation.runOnMainSync(() -> toggleButton.setBackgroundDrawable(null));
        assertNull(toggleButton.getBackground());
    }

    public void testAccessTextOn() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        mInstrumentation.runOnMainSync(() -> toggleButton.setTextOn("cts"));
        assertEquals("cts", toggleButton.getTextOn());

        mInstrumentation.runOnMainSync(() -> toggleButton.setTextOn(null));
        assertNull(toggleButton.getTextOn());

        mInstrumentation.runOnMainSync(() -> toggleButton.setTextOn(""));
        assertEquals("", toggleButton.getTextOn());
    }

    /**
     * MockToggleButton class for testing.
     */
    private static final class MockToggleButton extends ToggleButton {
        public MockToggleButton(Context context) {
            super(context);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
        }
    }
}
