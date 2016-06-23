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
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.ImageButton;
import android.widget.cts.util.TestUtils;

@SmallTest
public class ImageButtonTest extends ActivityInstrumentationTestCase2<ImageButtonCtsActivity> {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private ImageButton mImageButton;

    public ImageButtonTest() {
        super("android.widget.cts", ImageButtonCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mImageButton = (ImageButton) mActivity.findViewById(R.id.image_button);
    }

    public void testConstructor() {
        new ImageButton(mActivity);
        new ImageButton(mActivity, null);
        new ImageButton(mActivity, null, android.R.attr.imageButtonStyle);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_DeviceDefault_ImageButton);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_ImageButton);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_Material_ImageButton);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_Material_Light_ImageButton);

        try {
            new ImageButton(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }

        try {
            new ImageButton(null, null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }

        try {
            new ImageButton(null, null, -1);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    public void testImageSource() {
        Drawable imageButtonDrawable = mImageButton.getDrawable();
        TestUtils.assertAllPixelsOfColor("Default source is red", imageButtonDrawable,
                imageButtonDrawable.getIntrinsicWidth(), imageButtonDrawable.getIntrinsicHeight(),
                true, Color.RED, 1, false);

        mInstrumentation.runOnMainSync(() -> mImageButton.setImageResource(R.drawable.icon_green));
        imageButtonDrawable = mImageButton.getDrawable();
        TestUtils.assertAllPixelsOfColor("New source is green", imageButtonDrawable,
                imageButtonDrawable.getIntrinsicWidth(), imageButtonDrawable.getIntrinsicHeight(),
                true, Color.GREEN, 1, false);

        mInstrumentation.runOnMainSync(
                () -> mImageButton.setImageDrawable(mActivity.getDrawable(R.drawable.icon_yellow)));
        imageButtonDrawable = mImageButton.getDrawable();
        TestUtils.assertAllPixelsOfColor("New source is yellow", imageButtonDrawable,
                imageButtonDrawable.getIntrinsicWidth(), imageButtonDrawable.getIntrinsicHeight(),
                true, Color.YELLOW, 1, false);
    }
}
