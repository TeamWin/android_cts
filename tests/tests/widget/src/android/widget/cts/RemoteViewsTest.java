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

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.test.UiThreadTest;


import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.cts.util.WidgetTestUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Parcel;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.AnalogClock;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.DatePicker;
import android.widget.DateTimeView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.StackView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.RemoteViews.ActionException;
import android.widget.ViewFlipper;
import android.widget.cts.util.TestUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Test {@link RemoteViews}.
 */
public class RemoteViewsTest extends ActivityInstrumentationTestCase2<RemoteViewsCtsActivity> {
    private static final String PACKAGE_NAME = "android.widget.cts";

    private static final int INVALID_ID = -1;

    private static final long TEST_TIMEOUT = 5000;

    private Instrumentation mInstrumentation;

    private Activity mActivity;

    private RemoteViews mRemoteViews;

    private View mResult;

    public RemoteViewsTest() {
        super(PACKAGE_NAME, RemoteViewsCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mInstrumentation.runOnMainSync(() -> {
            mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);
            mResult = mRemoteViews.apply(mActivity, null);
        });
    }

    public void testConstructor() {
        new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);

        new RemoteViews(Parcel.obtain());
    }

    public void testGetPackage() {
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());

        mRemoteViews = new RemoteViews(null, R.layout.remoteviews_good);
        assertNull(mRemoteViews.getPackage());
    }

    public void testGetLayoutId() {
        assertEquals(R.layout.remoteviews_good, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        assertEquals(R.layout.listview_layout, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, INVALID_ID);
        assertEquals(INVALID_ID, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, 0);
        assertEquals(0, mRemoteViews.getLayoutId());
    }

    public void testSetContentDescription() {
        View view = mResult.findViewById(R.id.remoteView_frame);

        assertNull(view.getContentDescription());

        CharSequence contentDescription = mActivity.getString(R.string.remote_content_description);
        mRemoteViews.setContentDescription(R.id.remoteView_frame, contentDescription);
        mRemoteViews.reapply(mActivity, mResult);
        assertTrue(TextUtils.equals(contentDescription, view.getContentDescription()));
    }

    public void testSetViewVisibility() {
        View view = mResult.findViewById(R.id.remoteView_chronometer);
        assertEquals(View.VISIBLE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.INVISIBLE);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(View.INVISIBLE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.GONE);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(View.GONE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.VISIBLE);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    public void testSetTextViewText() {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        assertEquals("", textView.getText().toString());

        String expected = "This is content";
        mRemoteViews.setTextViewText(R.id.remoteView_text, expected);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(expected, textView.getText().toString());

        mRemoteViews.setTextViewText(R.id.remoteView_text, null);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals("", textView.getText().toString());

        mRemoteViews.setTextViewText(R.id.remoteView_absolute, "");
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetTextViewTextSize() {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setTextViewTextSize(R.id.remoteView_text, TypedValue.COMPLEX_UNIT_SP, 18);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(mActivity.getResources().getDisplayMetrics().scaledDensity * 18,
                textView.getTextSize());

        mRemoteViews.setTextViewTextSize(R.id.remoteView_absolute, TypedValue.COMPLEX_UNIT_SP, 20);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw Throwable");
        } catch (Throwable t) {
            // expected
        }
    }

    public void testSetIcon() {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Icon iconBlack = Icon.createWithResource(mActivity, R.drawable.icon_black);
        mRemoteViews.setIcon(R.id.remoteView_image, "setImageIcon", iconBlack);
        mRemoteViews.reapply(mActivity, mResult);
        assertNotNull(image.getDrawable());
        BitmapDrawable dBlack = (BitmapDrawable) mActivity.getDrawable(R.drawable.icon_black);
        WidgetTestUtils.assertEquals(dBlack.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());
    }

    public void testSetImageViewIcon() {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Icon iconBlue = Icon.createWithResource(mActivity, R.drawable.icon_blue);
        mRemoteViews.setImageViewIcon(R.id.remoteView_image, iconBlue);
        mRemoteViews.reapply(mActivity, mResult);
        assertNotNull(image.getDrawable());
        BitmapDrawable dBlue = (BitmapDrawable) mActivity.getDrawable(R.drawable.icon_blue);
        WidgetTestUtils.assertEquals(dBlue.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());

    }

    public void testSetImageViewResource() {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        mRemoteViews.setImageViewResource(R.id.remoteView_image, R.drawable.testimage);
        mRemoteViews.reapply(mActivity, mResult);
        assertNotNull(image.getDrawable());
        BitmapDrawable d = (BitmapDrawable) mActivity.getDrawable(R.drawable.testimage);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());

        mRemoteViews.setImageViewResource(R.id.remoteView_absolute, R.drawable.testimage);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetImageViewUri() throws IOException {
        String path = getTestImagePath();
        File imageFile = new File(path);

        try {
            createSampleImage(imageFile, R.raw.testimage);

            Uri uri = Uri.parse(path);
            ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
            assertNull(image.getDrawable());

            mRemoteViews.setImageViewUri(R.id.remoteView_image, uri);
            mRemoteViews.reapply(mActivity, mResult);

            Bitmap imageViewBitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
            Bitmap expectedBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mActivity.getResources(), R.raw.testimage, imageViewBitmap.getConfig());
            WidgetTestUtils.assertEquals(expectedBitmap, imageViewBitmap);
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Returns absolute file path of location where test image should be stored
     */
    private String getTestImagePath() {
        return getInstrumentation().getTargetContext().getFilesDir() + "/test.jpg";
    }

    public void testSetChronometer() {
        long base1 = 50;
        long base2 = -50;
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base1, "HH:MM:SS",
                false);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(base1, chronometer.getBase());
        assertEquals("HH:MM:SS", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base2, "HH:MM:SS",
                false);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(base2, chronometer.getBase());
        assertEquals("HH:MM:SS", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base1, "invalid",
                true);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(base1, chronometer.getBase());
        assertEquals("invalid", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_absolute, base1, "invalid", true);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetChronometerCountDown() {
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setChronometerCountDown(R.id.remoteView_chronometer, true);
        mRemoteViews.reapply(mActivity, mResult);
        assertTrue(chronometer.isCountDown());

        mRemoteViews.setChronometerCountDown(R.id.remoteView_chronometer, false);
        mRemoteViews.reapply(mActivity, mResult);
        assertFalse(chronometer.isCountDown());

        mRemoteViews.setChronometerCountDown(R.id.remoteView_absolute, true);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetProgressBar() {
        ProgressBar progress = (ProgressBar) mResult.findViewById(R.id.remoteView_progress);
        assertEquals(100, progress.getMax());
        assertEquals(0, progress.getProgress());
        // the view uses style progressBarHorizontal, so the default is false
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_progress, 80, 50, true);
        mRemoteViews.reapply(mActivity, mResult);
        // make the bar indeterminate will not affect max and progress
        assertEquals(100, progress.getMax());
        assertEquals(0, progress.getProgress());
        assertTrue(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_progress, 60, 50, false);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(60, progress.getMax());
        assertEquals(50, progress.getProgress());
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_relative, 60, 50, false);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testApply() {
        assertNotNull(mResult);
        assertNotNull(mResult.findViewById(R.id.remoteViews_good));
        assertNotNull(mResult.findViewById(R.id.remoteView_absolute));
        assertNotNull(mResult.findViewById(R.id.remoteView_chronometer));
        assertNotNull(mResult.findViewById(R.id.remoteView_frame));
        assertNotNull(mResult.findViewById(R.id.remoteView_image));
        assertNotNull(mResult.findViewById(R.id.remoteView_linear));
        assertNotNull(mResult.findViewById(R.id.remoteView_progress));
        assertNotNull(mResult.findViewById(R.id.remoteView_relative));
        assertNotNull(mResult.findViewById(R.id.remoteView_text));
    }

    public void testReapply() {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        mRemoteViews.setImageViewResource(R.id.remoteView_image, R.drawable.testimage);
        mRemoteViews.reapply(mActivity, image);
        assertNotNull(image.getDrawable());
        BitmapDrawable d = (BitmapDrawable) mActivity
                .getResources().getDrawable(R.drawable.testimage);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());
    }

    public void testOnLoadClass() {
        mRemoteViews = new RemoteViews(Parcel.obtain());

        assertTrue(mRemoteViews.onLoadClass(AbsoluteLayout.class));
        assertTrue(mRemoteViews.onLoadClass(AnalogClock.class));
        assertTrue(mRemoteViews.onLoadClass(Button.class));
        assertTrue(mRemoteViews.onLoadClass(Chronometer.class));
        assertTrue(mRemoteViews.onLoadClass(DateTimeView.class));
        assertTrue(mRemoteViews.onLoadClass(FrameLayout.class));
        assertTrue(mRemoteViews.onLoadClass(GridLayout.class));
        assertTrue(mRemoteViews.onLoadClass(GridView.class));
        assertTrue(mRemoteViews.onLoadClass(ImageButton.class));
        assertTrue(mRemoteViews.onLoadClass(ImageView.class));
        assertTrue(mRemoteViews.onLoadClass(LinearLayout.class));
        assertTrue(mRemoteViews.onLoadClass(ListView.class));
        assertTrue(mRemoteViews.onLoadClass(ProgressBar.class));
        assertTrue(mRemoteViews.onLoadClass(RelativeLayout.class));
        assertTrue(mRemoteViews.onLoadClass(StackView.class));
        assertTrue(mRemoteViews.onLoadClass(TextClock.class));
        assertTrue(mRemoteViews.onLoadClass(TextView.class));
        assertTrue(mRemoteViews.onLoadClass(ViewFlipper.class));

        // those classes without annotation @RemoteView
        assertFalse(mRemoteViews.onLoadClass(EditText.class));
        assertFalse(mRemoteViews.onLoadClass(DatePicker.class));
        assertFalse(mRemoteViews.onLoadClass(NumberPicker.class));
        assertFalse(mRemoteViews.onLoadClass(RatingBar.class));
        assertFalse(mRemoteViews.onLoadClass(SeekBar.class));
    }

    public void testDescribeContents() {
        mRemoteViews = new RemoteViews(Parcel.obtain());
        mRemoteViews.describeContents();
    }

    @UiThreadTest
    public void testWriteToParcel() {
        mRemoteViews.setTextViewText(R.id.remoteView_text, "This is content");
        mRemoteViews.setViewVisibility(R.id.remoteView_frame, View.GONE);
        Parcel p = Parcel.obtain();
        mRemoteViews.writeToParcel(p, 0);
        p.setDataPosition(0);

        // the package and layout are successfully written into parcel
        mRemoteViews = RemoteViews.CREATOR.createFromParcel(p);
        View result = mRemoteViews.apply(mActivity, null);
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());
        assertEquals(R.layout.remoteviews_good, mRemoteViews.getLayoutId());
        assertEquals("This is content", ((TextView) result.findViewById(R.id.remoteView_text))
                .getText().toString());
        assertEquals(View.GONE, result.findViewById(R.id.remoteView_frame).getVisibility());

        p = Parcel.obtain();
        try {
            mRemoteViews.writeToParcel(null, 0);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }

        // currently the flag is not used
        mRemoteViews.writeToParcel(p, -1);

        p.recycle();

        RemoteViews[] remote = RemoteViews.CREATOR.newArray(1);
        assertNotNull(remote);
        assertEquals(1, remote.length);

        try {
            RemoteViews.CREATOR.newArray(-1);
            fail("should throw NegativeArraySizeException");
        } catch (NegativeArraySizeException e) {
            // expected
        }
    }

    public void testSetImageViewBitmap() {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Bitmap bitmap =
                BitmapFactory.decodeResource(mActivity.getResources(), R.drawable.testimage);
        mRemoteViews.setImageViewBitmap(R.id.remoteView_image, bitmap);
        mRemoteViews.reapply(mActivity, mResult);
        assertNotNull(image.getDrawable());
        WidgetTestUtils.assertEquals(bitmap, ((BitmapDrawable) image.getDrawable()).getBitmap());

        mRemoteViews.setImageViewBitmap(R.id.remoteView_absolute, bitmap);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetBitmap() {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Bitmap bitmap =
                BitmapFactory.decodeResource(mActivity.getResources(), R.drawable.testimage);
        mRemoteViews.setBitmap(R.id.remoteView_image, "setImageBitmap", bitmap);
        mRemoteViews.reapply(mActivity, mResult);
        assertNotNull(image.getDrawable());
        WidgetTestUtils.assertEquals(bitmap, ((BitmapDrawable) image.getDrawable()).getBitmap());

        mRemoteViews.setBitmap(R.id.remoteView_absolute, "setImageBitmap", bitmap);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetBoolean() {
        ProgressBar progress = (ProgressBar) mResult.findViewById(R.id.remoteView_progress);
        // the view uses style progressBarHorizontal, so the default is false
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setBoolean(R.id.remoteView_progress, "setIndeterminate", true);
        mRemoteViews.reapply(mActivity, mResult);
        assertTrue(progress.isIndeterminate());

        mRemoteViews.setBoolean(R.id.remoteView_relative, "setIndeterminate", false);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetCharSequence() {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        assertEquals("", textView.getText().toString());

        String expected = "test setCharSequence";
        mRemoteViews.setCharSequence(R.id.remoteView_text, "setText", expected);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(expected, textView.getText().toString());

        mRemoteViews.setCharSequence(R.id.remoteView_text, "setText", null);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals("", textView.getText().toString());

        mRemoteViews.setCharSequence(R.id.remoteView_absolute, "setText", "");
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetInt() {
        View view = mResult.findViewById(R.id.remoteView_chronometer);
        assertEquals(View.VISIBLE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.INVISIBLE);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(View.INVISIBLE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.GONE);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(View.GONE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.VISIBLE);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    public void testSetString() {
        String format = "HH:MM:SS";
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);
        assertNull(chronometer.getFormat());

        mRemoteViews.setString(R.id.remoteView_chronometer, "setFormat", format);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(format, chronometer.getFormat());

        mRemoteViews.setString(R.id.remoteView_image, "setFormat", format);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetUri() throws IOException {
        String path = getTestImagePath();
        File imagefile = new File(path);

        try {
            createSampleImage(imagefile, R.raw.testimage);

            Uri uri = Uri.parse(path);
            ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
            assertNull(image.getDrawable());

            mRemoteViews.setUri(R.id.remoteView_image, "setImageURI", uri);
            mRemoteViews.reapply(mActivity, mResult);

            Bitmap imageViewBitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
            Bitmap expectedBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mActivity.getResources(), R.raw.testimage, imageViewBitmap.getConfig());
            WidgetTestUtils.assertEquals(expectedBitmap, imageViewBitmap);

            mRemoteViews.setUri(R.id.remoteView_absolute, "setImageURI", uri);
            try {
                mRemoteViews.reapply(mActivity, mResult);
                fail("Should throw ActionException");
            } catch (ActionException e) {
                // expected
            }
        } finally {
            // remove the test image file
            imagefile.delete();
        }
    }

    public void testSetTextColor() {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setTextColor(R.id.remoteView_text, R.color.testcolor1);
        mRemoteViews.reapply(mActivity, mResult);
        assertSame(ColorStateList.valueOf(R.color.testcolor1), textView.getTextColors());

        mRemoteViews.setTextColor(R.id.remoteView_text, R.color.testcolor2);
        mRemoteViews.reapply(mActivity, mResult);
        assertSame(ColorStateList.valueOf(R.color.testcolor2), textView.getTextColors());

        mRemoteViews.setTextColor(R.id.remoteView_absolute, R.color.testcolor1);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetTextCompoundDrawables() {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        TestUtils.verifyCompoundDrawables(textView, -1, -1, -1, -1);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_text, R.drawable.start,
                R.drawable.pass, R.drawable.failed, 0);
        mRemoteViews.reapply(mActivity, mResult);
        TestUtils.verifyCompoundDrawables(textView, R.drawable.start, R.drawable.failed,
                R.drawable.pass, -1);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_text, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mRemoteViews.reapply(mActivity, mResult);
        TestUtils.verifyCompoundDrawables(textView, -1,  R.drawable.icon_red, R.drawable.icon_black,
                R.drawable.icon_green);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_absolute, 0,
                R.drawable.start, R.drawable.failed, 0);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw Throwable");
        } catch (Throwable t) {
            // expected
        }
    }

    public void testSetTextCompoundDrawablesRelative() {
        TextView textViewLtr = (TextView) mResult.findViewById(R.id.remoteView_text_ltr);
        TextView textViewRtl = (TextView) mResult.findViewById(R.id.remoteView_text_rtl);

        TestUtils.verifyCompoundDrawables(textViewLtr, -1, -1, -1, -1);
        TestUtils.verifyCompoundDrawables(textViewRtl, -1, -1, -1, -1);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_ltr,
                R.drawable.start, R.drawable.pass, R.drawable.failed, 0);
        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_rtl,
                R.drawable.start, R.drawable.pass, R.drawable.failed, 0);
        mRemoteViews.reapply(mActivity, mResult);
        TestUtils.verifyCompoundDrawables(textViewLtr, R.drawable.start, R.drawable.failed,
                R.drawable.pass, -1);
        TestUtils.verifyCompoundDrawables(textViewRtl, R.drawable.failed, R.drawable.start,
                R.drawable.pass, -1);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_ltr, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_rtl, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mRemoteViews.reapply(mActivity, mResult);
        TestUtils.verifyCompoundDrawables(textViewLtr, -1, R.drawable.icon_red,
                R.drawable.icon_black, R.drawable.icon_green);
        TestUtils.verifyCompoundDrawables(textViewRtl, R.drawable.icon_red, -1,
                R.drawable.icon_black, R.drawable.icon_green);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_absolute, 0,
                R.drawable.start, R.drawable.failed, 0);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw Throwable");
        } catch (Throwable t) {
            // expected
        }
    }

    public void testSetOnClickPendingIntent() {
        Uri uri = Uri.parse("ctstest://RemoteView/test");
        Instrumentation instrumentation = getInstrumentation();
        ActivityMonitor am = instrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);
        View view = mResult.findViewById(R.id.remoteView_image);
        view.performClick();
        Activity newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNull(newActivity);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        PendingIntent pendingIntent = PendingIntent.getActivity(mActivity, 0, intent, 0);
        mRemoteViews.setOnClickPendingIntent(R.id.remoteView_image, pendingIntent);
        mRemoteViews.reapply(mActivity, mResult);
        view.performClick();
        newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNotNull(newActivity);
        assertTrue(newActivity instanceof MockURLSpanTestActivity);
        newActivity.finish();
    }

    public void testSetLong() {
        long base1 = 50;
        long base2 = -50;
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setLong(R.id.remoteView_chronometer, "setBase", base1);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(base1, chronometer.getBase());

        mRemoteViews.setLong(R.id.remoteView_chronometer, "setBase", base2);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(base2, chronometer.getBase());

        mRemoteViews.setLong(R.id.remoteView_absolute, "setBase", base1);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetFloat() {
        LinearLayout linearLayout = (LinearLayout) mResult.findViewById(R.id.remoteView_linear);
        assertTrue(linearLayout.getWeightSum() <= 0.0f);

        mRemoteViews.setFloat(R.id.remoteView_linear, "setWeightSum", 0.5f);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(0.5f, linearLayout.getWeightSum());

        mRemoteViews.setFloat(R.id.remoteView_absolute, "setWeightSum", 1.0f);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetByte() {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0, customView.getByteField());

        byte b = 100;
        mRemoteViews.setByte(R.id.remoteView_custom, "setByteField", b);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(b, customView.getByteField());

        mRemoteViews.setByte(R.id.remoteView_absolute, "setByteField", b);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetChar() {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals('\u0000', customView.getCharField());

        mRemoteViews.setChar(R.id.remoteView_custom, "setCharField", 'q');
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals('q', customView.getCharField());

        mRemoteViews.setChar(R.id.remoteView_absolute, "setCharField", 'w');
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetDouble() {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0.0, customView.getDoubleField());

        mRemoteViews.setDouble(R.id.remoteView_custom, "setDoubleField", 0.5);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(0.5, customView.getDoubleField());

        mRemoteViews.setDouble(R.id.remoteView_absolute, "setDoubleField", 1.0);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetShort() {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0, customView.getShortField());

        short s = 25;
        mRemoteViews.setShort(R.id.remoteView_custom, "setShortField", s);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(s, customView.getShortField());

        mRemoteViews.setShort(R.id.remoteView_absolute, "setShortField", s);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetBundle() {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertNull(customView.getBundleField());

        final Bundle bundle = new Bundle();
        bundle.putString("STR", "brexit");
        bundle.putInt("INT", 2016);
        mRemoteViews.setBundle(R.id.remoteView_custom, "setBundleField", bundle);
        mRemoteViews.reapply(mActivity, mResult);
        final Bundle fromRemote = customView.getBundleField();
        assertEquals("brexit", fromRemote.getString("STR", ""));
        assertEquals(2016, fromRemote.getInt("INT", 0));

        mRemoteViews.setBundle(R.id.remoteView_absolute, "setBundleField", bundle);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testSetIntent() {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertNull(customView.getIntentField());

        final Intent intent = new Intent(mActivity, SwitchCtsActivity.class);
        intent.putExtra("STR", "brexit");
        intent.putExtra("INT", 2016);
        mRemoteViews.setIntent(R.id.remoteView_custom, "setIntentField", intent);
        mRemoteViews.reapply(mActivity, mResult);
        final Intent fromRemote = customView.getIntentField();
        assertEquals(SwitchCtsActivity.class.getName(), fromRemote.getComponent().getClassName());
        assertEquals("brexit", fromRemote.getStringExtra("STR"));
        assertEquals(2016, fromRemote.getIntExtra("INT", 0));

        mRemoteViews.setIntent(R.id.remoteView_absolute, "setIntentField", intent);
        try {
            mRemoteViews.reapply(mActivity, mResult);
            fail("Should throw ActionException");
        } catch (ActionException e) {
            // expected
        }
    }

    public void testRemoveAllViews() {
        ViewGroup root = (ViewGroup) mResult.findViewById(R.id.remoteViews_good);
        assertTrue(root.getChildCount() > 0);

        mRemoteViews.removeAllViews(R.id.remoteViews_good);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(0, root.getChildCount());
    }

    public void testAddView() {
        ViewGroup root = (ViewGroup) mResult.findViewById(R.id.remoteViews_good);
        int originalChildCount = root.getChildCount();

        assertNull(root.findViewById(R.id.remoteView_frame_extra));

        // Create a RemoteViews wrapper around a layout and add it to our root
        RemoteViews extra = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_extra);
        mRemoteViews.addView(R.id.remoteViews_good, extra);
        mRemoteViews.reapply(mActivity, mResult);

        // Verify that our root has that layout as its last (new) child
        assertEquals(originalChildCount + 1, root.getChildCount());
        assertNotNull(root.findViewById(R.id.remoteView_frame_extra));
        assertEquals(R.id.remoteView_frame_extra, root.getChildAt(originalChildCount).getId());
    }

    public void testSetLabelFor() {
        View labelView = mResult.findViewById(R.id.remoteView_label);
        assertEquals(View.NO_ID, labelView.getLabelFor());

        mRemoteViews.setLabelFor(R.id.remoteView_label, R.id.remoteView_text);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(R.id.remoteView_text, labelView.getLabelFor());
    }

    public void testSetAccessibilityTraversalAfter() {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setAccessibilityTraversalAfter(R.id.remoteView_text, R.id.remoteView_frame);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(R.id.remoteView_frame, textView.getAccessibilityTraversalAfter());

        mRemoteViews.setAccessibilityTraversalAfter(R.id.remoteView_text, R.id.remoteView_linear);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(R.id.remoteView_linear, textView.getAccessibilityTraversalAfter());
    }

    public void testSetAccessibilityTraversalBefore() {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setAccessibilityTraversalBefore(R.id.remoteView_text, R.id.remoteView_frame);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(R.id.remoteView_frame, textView.getAccessibilityTraversalBefore());

        mRemoteViews.setAccessibilityTraversalBefore(R.id.remoteView_text, R.id.remoteView_linear);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(R.id.remoteView_linear, textView.getAccessibilityTraversalBefore());
    }

    public void testSetViewPadding() {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setViewPadding(R.id.remoteView_text, 10, 20, 30, 40);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(10, textView.getPaddingLeft());
        assertEquals(20, textView.getPaddingTop());
        assertEquals(30, textView.getPaddingRight());
        assertEquals(40, textView.getPaddingBottom());

        mRemoteViews.setViewPadding(R.id.remoteView_text, 40, 30, 20, 10);
        mRemoteViews.reapply(mActivity, mResult);
        assertEquals(40, textView.getPaddingLeft());
        assertEquals(30, textView.getPaddingTop());
        assertEquals(20, textView.getPaddingRight());
        assertEquals(10, textView.getPaddingBottom());
    }

    private void createSampleImage(File imagefile, int resid) throws IOException {
        try (InputStream source = mActivity.getResources().openRawResource(resid);
             OutputStream target = new FileOutputStream(imagefile)) {

            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        }
    }
}
