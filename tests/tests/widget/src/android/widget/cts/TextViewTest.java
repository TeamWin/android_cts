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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.cts.util.CtsTouchUtils;
import android.cts.util.KeyEventUtil;
import android.cts.util.PollingCheck;
import android.cts.util.WidgetTestUtils;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.DateKeyListener;
import android.text.method.DateTimeKeyListener;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.QwertyKeyListener;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.text.method.TimeKeyListener;
import android.text.method.TransformationMethod;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.cts.util.TestUtils;

import org.mockito.invocation.InvocationOnMock;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Locale;

/**
 * Test {@link TextView}.
 */
public class TextViewTest extends ActivityInstrumentationTestCase2<TextViewCtsActivity> {

    private TextView mTextView;
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private static final String LONG_TEXT = "This is a really long string which exceeds "
            + "the width of the view. New devices have a much larger screen which "
            + "actually enables long strings to be displayed with no fading. "
            + "I have made this string longer to fix this case. If you are correcting "
            + "this text, I would love to see the kind of devices you guys now use!";
    private static final long TIMEOUT = 5000;
    private CharSequence mTransformedText;
    private KeyEventUtil mKeyEventUtil;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public TextViewTest() {
        super("android.widget.cts", TextViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mInstrumentation = getInstrumentation();
        mKeyEventUtil = new KeyEventUtil(mInstrumentation);
    }

    /**
     * Promotes the TextView to editable and places focus in it to allow simulated typing. Used in
     * test methods annotated with {@link android.test.UiThreadTest}.
     */
    private void initTextViewForTyping() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
        mTextView.setText("", BufferType.EDITABLE);
        mTextView.requestFocus();
    }

    /**
     * Used in test methods that can not entirely be run on the UiThread (e.g: tests that need to
     * emulate touches and/or key presses).
     */
    private void initTextViewForTypingOnUiThread() {
        mActivity.runOnUiThread(this::initTextViewForTyping);
        mInstrumentation.waitForIdleSync();
    }

    public void testConstructor() {
        new TextView(mActivity);

        new TextView(mActivity, null);

        new TextView(mActivity, null, android.R.attr.textViewStyle);

        new TextView(mActivity, null, 0, android.R.style.Widget_DeviceDefault_TextView);

        new TextView(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_TextView);

        new TextView(mActivity, null, 0, android.R.style.Widget_Material_TextView);

        new TextView(mActivity, null, 0, android.R.style.Widget_Material_Light_TextView);
    }

    @UiThreadTest
    public void testAccessText() {
        TextView tv = findTextView(R.id.textview_text);

        String expected = mActivity.getResources().getString(R.string.text_view_hello);
        tv.setText(expected);
        assertEquals(expected, tv.getText().toString());

        tv.setText(null);
        assertEquals("", tv.getText().toString());
    }

    public void testGetLineHeight() {
        mTextView = new TextView(mActivity);
        assertTrue(mTextView.getLineHeight() > 0);

        mTextView.setLineSpacing(1.2f, 1.5f);
        assertTrue(mTextView.getLineHeight() > 0);
    }

    public void testGetLayout() {
        mActivity.runOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setGravity(Gravity.CENTER);
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mTextView.getLayout());

        TestLayoutRunnable runnable = new TestLayoutRunnable(mTextView) {
            public void run() {
                // change the text of TextView.
                mTextView.setText("Hello, Android!");
                saveLayout();
            }
        };
        mActivity.runOnUiThread(runnable);
        mInstrumentation.waitForIdleSync();
        assertNull(runnable.getLayout());
        assertNotNull(mTextView.getLayout());
    }

    public void testAccessKeyListener() {
        mActivity.runOnUiThread(() -> mTextView = findTextView(R.id.textview_text));
        mInstrumentation.waitForIdleSync();

        assertNull(mTextView.getKeyListener());

        final KeyListener digitsKeyListener = DigitsKeyListener.getInstance();

        mActivity.runOnUiThread(() -> mTextView.setKeyListener(digitsKeyListener));
        mInstrumentation.waitForIdleSync();
        assertSame(digitsKeyListener, mTextView.getKeyListener());

        final QwertyKeyListener qwertyKeyListener
                = QwertyKeyListener.getInstance(false, Capitalize.NONE);
        mActivity.runOnUiThread(() -> mTextView.setKeyListener(qwertyKeyListener));
        mInstrumentation.waitForIdleSync();
        assertSame(qwertyKeyListener, mTextView.getKeyListener());
    }

    public void testAccessMovementMethod() {
        final CharSequence LONG_TEXT = "Scrolls the specified widget to the specified "
                + "coordinates, except constrains the X scrolling position to the horizontal "
                + "regions of the text that will be visible after scrolling to "
                + "the specified Y position.";
        final int selectionStart = 10;
        final int selectionEnd = LONG_TEXT.length();
        final MovementMethod movementMethod = ArrowKeyMovementMethod.getInstance();
        mActivity.runOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setMovementMethod(movementMethod);
            mTextView.setText(LONG_TEXT, BufferType.EDITABLE);
            Selection.setSelection((Editable) mTextView.getText(),
                    selectionStart, selectionEnd);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertSame(movementMethod, mTextView.getMovementMethod());
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionEnd, Selection.getSelectionEnd(mTextView.getText()));
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_DPAD_UP);
        // the selection has been removed.
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionStart, Selection.getSelectionEnd(mTextView.getText()));

        mActivity.runOnUiThread(() -> {
            mTextView.setMovementMethod(null);
            Selection.setSelection((Editable) mTextView.getText(),
                    selectionStart, selectionEnd);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertNull(mTextView.getMovementMethod());
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionEnd, Selection.getSelectionEnd(mTextView.getText()));
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_DPAD_UP);
        // the selection will not be changed.
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionEnd, Selection.getSelectionEnd(mTextView.getText()));
    }

    @UiThreadTest
    public void testLength() {
        mTextView = findTextView(R.id.textview_text);

        String content = "This is content";
        mTextView.setText(content);
        assertEquals(content.length(), mTextView.length());

        mTextView.setText("");
        assertEquals(0, mTextView.length());

        mTextView.setText(null);
        assertEquals(0, mTextView.length());
    }

    @UiThreadTest
    public void testAccessGravity() {
        mActivity.setContentView(R.layout.textview_gravity);

        mTextView = findTextView(R.id.gravity_default);
        assertEquals(Gravity.TOP | Gravity.START, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_bottom);
        assertEquals(Gravity.BOTTOM | Gravity.START, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_right);
        assertEquals(Gravity.TOP | Gravity.RIGHT, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_center);
        assertEquals(Gravity.CENTER, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_fill);
        assertEquals(Gravity.FILL, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_center_vertical_right);
        assertEquals(Gravity.CENTER_VERTICAL | Gravity.RIGHT, mTextView.getGravity());

        mTextView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        assertEquals(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, mTextView.getGravity());
        mTextView.setGravity(Gravity.FILL);
        assertEquals(Gravity.FILL, mTextView.getGravity());
        mTextView.setGravity(Gravity.CENTER);
        assertEquals(Gravity.CENTER, mTextView.getGravity());

        mTextView.setGravity(Gravity.NO_GRAVITY);
        assertEquals(Gravity.TOP | Gravity.START, mTextView.getGravity());

        mTextView.setGravity(Gravity.RIGHT);
        assertEquals(Gravity.TOP | Gravity.RIGHT, mTextView.getGravity());

        mTextView.setGravity(Gravity.FILL_VERTICAL);
        assertEquals(Gravity.FILL_VERTICAL | Gravity.START, mTextView.getGravity());

        //test negative input value.
        mTextView.setGravity(-1);
        assertEquals(-1, mTextView.getGravity());
    }

    public void testAccessAutoLinkMask() {
        mTextView = findTextView(R.id.textview_text);
        final CharSequence text1 =
                new SpannableString("URL: http://www.google.com. mailto: account@gmail.com");
        mActivity.runOnUiThread(() -> {
            mTextView.setAutoLinkMask(Linkify.ALL);
            mTextView.setText(text1, BufferType.EDITABLE);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(Linkify.ALL, mTextView.getAutoLinkMask());

        Spannable spanString = (Spannable) mTextView.getText();
        URLSpan[] spans = spanString.getSpans(0, spanString.length(), URLSpan.class);
        assertNotNull(spans);
        assertEquals(2, spans.length);
        assertEquals("http://www.google.com", spans[0].getURL());
        assertEquals("mailto:account@gmail.com", spans[1].getURL());

        final CharSequence text2 =
            new SpannableString("name: Jack. tel: +41 44 800 8999");
        mActivity.runOnUiThread(() -> {
            mTextView.setAutoLinkMask(Linkify.PHONE_NUMBERS);
            mTextView.setText(text2, BufferType.EDITABLE);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(Linkify.PHONE_NUMBERS, mTextView.getAutoLinkMask());

        spanString = (Spannable) mTextView.getText();
        spans = spanString.getSpans(0, spanString.length(), URLSpan.class);
        assertNotNull(spans);
        assertEquals(1, spans.length);
        assertEquals("tel:+41448008999", spans[0].getURL());

        layout(R.layout.textview_autolink);
        // 1 for web, 2 for email, 4 for phone, 7 for all(web|email|phone)
        assertEquals(0, getAutoLinkMask(R.id.autolink_default));
        assertEquals(Linkify.WEB_URLS, getAutoLinkMask(R.id.autolink_web));
        assertEquals(Linkify.EMAIL_ADDRESSES, getAutoLinkMask(R.id.autolink_email));
        assertEquals(Linkify.PHONE_NUMBERS, getAutoLinkMask(R.id.autolink_phone));
        assertEquals(Linkify.ALL, getAutoLinkMask(R.id.autolink_all));
        assertEquals(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES,
                getAutoLinkMask(R.id.autolink_compound1));
        assertEquals(Linkify.WEB_URLS | Linkify.PHONE_NUMBERS,
                getAutoLinkMask(R.id.autolink_compound2));
        assertEquals(Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS,
                getAutoLinkMask(R.id.autolink_compound3));
        assertEquals(Linkify.PHONE_NUMBERS | Linkify.ALL,
                getAutoLinkMask(R.id.autolink_compound4));
    }

    public void testAccessTextSize() {
        DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();

        mTextView = new TextView(mActivity);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        // setTextSize by default unit "sp"
        mTextView.setTextSize(20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        mTextView.setTextSize(200f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 200f, metrics),
                mTextView.getTextSize(), 0.01f);
    }

    public void testAccessTextColor() {
        mTextView = new TextView(mActivity);

        mTextView.setTextColor(Color.GREEN);
        assertEquals(Color.GREEN, mTextView.getCurrentTextColor());
        assertSame(ColorStateList.valueOf(Color.GREEN), mTextView.getTextColors());

        mTextView.setTextColor(Color.BLACK);
        assertEquals(Color.BLACK, mTextView.getCurrentTextColor());
        assertSame(ColorStateList.valueOf(Color.BLACK), mTextView.getTextColors());

        mTextView.setTextColor(Color.RED);
        assertEquals(Color.RED, mTextView.getCurrentTextColor());
        assertSame(ColorStateList.valueOf(Color.RED), mTextView.getTextColors());

        // using ColorStateList
        // normal
        ColorStateList colors = new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_focused}, new int[0] },
                new int[] { Color.rgb(0, 255, 0), Color.BLACK });
        mTextView.setTextColor(colors);
        assertSame(colors, mTextView.getTextColors());
        assertEquals(Color.BLACK, mTextView.getCurrentTextColor());

        // exceptional
        try {
            mTextView.setTextColor(null);
            fail("Should thrown exception if the colors is null");
        } catch (NullPointerException e){
        }
    }

    public void testGetTextColor() {
        // TODO: How to get a suitable TypedArray to test this method.

        try {
            TextView.getTextColor(mActivity, null, -1);
            fail("There should be a NullPointerException thrown out.");
        } catch (NullPointerException e) {
        }
    }

    public void testAccessHighlightColor() {
        final TextView textView = (TextView) mActivity.findViewById(R.id.textview_text);

        mActivity.runOnUiThread(() -> {
            textView.setTextIsSelectable(true);
            textView.setText("abcd", BufferType.EDITABLE);
            textView.setHighlightColor(Color.BLUE);
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(textView.isTextSelectable());
        assertEquals(Color.BLUE, textView.getHighlightColor());

        // Long click on the text selects all text and shows selection handlers. The view has an
        // attribute layout_width="wrap_content", so clicked location (the center of the view)
        // should be on the text.
        CtsTouchUtils.emulateLongClick(mInstrumentation, textView);

        // At this point the entire content of our TextView should be selected and highlighted
        // with blue. Now change the highlight to red while the selection is still on.
        mActivity.runOnUiThread(() -> textView.setHighlightColor(Color.RED));
        mInstrumentation.waitForIdleSync();

        assertEquals(Color.RED, textView.getHighlightColor());
        assertTrue(TextUtils.equals("abcd", textView.getText()));

        // Remove the selection
        mActivity.runOnUiThread(() -> Selection.removeSelection((Spannable) textView.getText()));
        mInstrumentation.waitForIdleSync();

        // And switch highlight to green after the selection has been removed
        mActivity.runOnUiThread(() -> textView.setHighlightColor(Color.GREEN));
        mInstrumentation.waitForIdleSync();

        assertEquals(Color.GREEN, textView.getHighlightColor());
        assertTrue(TextUtils.equals("abcd", textView.getText()));
    }

    @MediumTest
    public void testSetShadowLayer() {
        // test values
        final MockTextView mockTextView = new MockTextView(mActivity);

        mockTextView.setShadowLayer(1.0f, 0.3f, 0.4f, Color.CYAN);
        assertEquals(Color.CYAN, mockTextView.getShadowColor());
        assertEquals(0.3f, mockTextView.getShadowDx());
        assertEquals(0.4f, mockTextView.getShadowDy());
        assertEquals(1.0f, mockTextView.getShadowRadius());

        // shadow is placed to the left and below the text
        mockTextView.setShadowLayer(1.0f, 0.3f, 0.3f, Color.CYAN);
        assertTrue(mockTextView.isPaddingOffsetRequired());
        assertEquals(0, mockTextView.getLeftPaddingOffset());
        assertEquals(0, mockTextView.getTopPaddingOffset());
        assertEquals(1, mockTextView.getRightPaddingOffset());
        assertEquals(1, mockTextView.getBottomPaddingOffset());

        // shadow is placed to the right and above the text
        mockTextView.setShadowLayer(1.0f, -0.8f, -0.8f, Color.CYAN);
        assertTrue(mockTextView.isPaddingOffsetRequired());
        assertEquals(-1, mockTextView.getLeftPaddingOffset());
        assertEquals(-1, mockTextView.getTopPaddingOffset());
        assertEquals(0, mockTextView.getRightPaddingOffset());
        assertEquals(0, mockTextView.getBottomPaddingOffset());

        // no shadow
        mockTextView.setShadowLayer(0.0f, 0.0f, 0.0f, Color.CYAN);
        assertFalse(mockTextView.isPaddingOffsetRequired());
        assertEquals(0, mockTextView.getLeftPaddingOffset());
        assertEquals(0, mockTextView.getTopPaddingOffset());
        assertEquals(0, mockTextView.getRightPaddingOffset());
        assertEquals(0, mockTextView.getBottomPaddingOffset());
    }

    @UiThreadTest
    public void testSetSelectAllOnFocus() {
        mActivity.setContentView(R.layout.textview_selectallonfocus);
        String content = "This is the content";
        String blank = "";
        mTextView = findTextView(R.id.selectAllOnFocus_default);
        mTextView.setText(blank, BufferType.SPANNABLE);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        mTextView.setText(content, BufferType.SPANNABLE);
        mTextView.setSelectAllOnFocus(true);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(content.length(), mTextView.getSelectionEnd());

        Selection.setSelection((Spannable) mTextView.getText(), 0);
        mTextView.setSelectAllOnFocus(false);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(0, mTextView.getSelectionEnd());

        mTextView.setText(blank, BufferType.SPANNABLE);
        mTextView.setSelectAllOnFocus(true);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(blank.length(), mTextView.getSelectionEnd());

        Selection.setSelection((Spannable) mTextView.getText(), 0);
        mTextView.setSelectAllOnFocus(false);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(0, mTextView.getSelectionEnd());
    }

    public void testGetPaint() {
        mTextView = new TextView(mActivity);
        TextPaint tp = mTextView.getPaint();
        assertNotNull(tp);

        assertEquals(mTextView.getPaintFlags(), tp.getFlags());
    }

    @UiThreadTest
    public void testAccessLinksClickable() {
        mActivity.setContentView(R.layout.textview_hint_linksclickable_freezestext);

        mTextView = findTextView(R.id.hint_linksClickable_freezesText_default);
        assertTrue(mTextView.getLinksClickable());

        mTextView = findTextView(R.id.linksClickable_true);
        assertTrue(mTextView.getLinksClickable());

        mTextView = findTextView(R.id.linksClickable_false);
        assertFalse(mTextView.getLinksClickable());

        mTextView.setLinksClickable(false);
        assertFalse(mTextView.getLinksClickable());

        mTextView.setLinksClickable(true);
        assertTrue(mTextView.getLinksClickable());

        assertNull(mTextView.getMovementMethod());

        final CharSequence text = new SpannableString("name: Jack. tel: +41 44 800 8999");

        mTextView.setAutoLinkMask(Linkify.PHONE_NUMBERS);
        mTextView.setText(text, BufferType.EDITABLE);

        // Movement method will be automatically set to LinkMovementMethod
        assertTrue(mTextView.getMovementMethod() instanceof LinkMovementMethod);
    }

    public void testAccessHintTextColor() {
        mTextView = new TextView(mActivity);
        // using int values
        // normal
        mTextView.setHintTextColor(Color.GREEN);
        assertEquals(Color.GREEN, mTextView.getCurrentHintTextColor());
        assertSame(ColorStateList.valueOf(Color.GREEN), mTextView.getHintTextColors());

        mTextView.setHintTextColor(Color.BLUE);
        assertSame(ColorStateList.valueOf(Color.BLUE), mTextView.getHintTextColors());
        assertEquals(Color.BLUE, mTextView.getCurrentHintTextColor());

        mTextView.setHintTextColor(Color.RED);
        assertSame(ColorStateList.valueOf(Color.RED), mTextView.getHintTextColors());
        assertEquals(Color.RED, mTextView.getCurrentHintTextColor());

        // using ColorStateList
        // normal
        ColorStateList colors = new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_focused}, new int[0] },
                new int[] { Color.rgb(0, 255, 0), Color.BLACK });
        mTextView.setHintTextColor(colors);
        assertSame(colors, mTextView.getHintTextColors());
        assertEquals(Color.BLACK, mTextView.getCurrentHintTextColor());

        // exceptional
        mTextView.setHintTextColor(null);
        assertNull(mTextView.getHintTextColors());
        assertEquals(mTextView.getCurrentTextColor(), mTextView.getCurrentHintTextColor());
    }

    public void testAccessLinkTextColor() {
        mTextView = new TextView(mActivity);
        // normal
        mTextView.setLinkTextColor(Color.GRAY);
        assertSame(ColorStateList.valueOf(Color.GRAY), mTextView.getLinkTextColors());
        assertEquals(Color.GRAY, mTextView.getPaint().linkColor);

        mTextView.setLinkTextColor(Color.YELLOW);
        assertSame(ColorStateList.valueOf(Color.YELLOW), mTextView.getLinkTextColors());
        assertEquals(Color.YELLOW, mTextView.getPaint().linkColor);

        mTextView.setLinkTextColor(Color.WHITE);
        assertSame(ColorStateList.valueOf(Color.WHITE), mTextView.getLinkTextColors());
        assertEquals(Color.WHITE, mTextView.getPaint().linkColor);

        ColorStateList colors = new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_expanded}, new int[0] },
                new int[] { Color.rgb(0, 255, 0), Color.BLACK });
        mTextView.setLinkTextColor(colors);
        assertSame(colors, mTextView.getLinkTextColors());
        assertEquals(Color.BLACK, mTextView.getPaint().linkColor);

        mTextView.setLinkTextColor(null);
        assertNull(mTextView.getLinkTextColors());
        assertEquals(Color.BLACK, mTextView.getPaint().linkColor);
    }

    public void testAccessPaintFlags() {
        mTextView = new TextView(mActivity);
        assertEquals(Paint.DEV_KERN_TEXT_FLAG | Paint.EMBEDDED_BITMAP_TEXT_FLAG
                | Paint.ANTI_ALIAS_FLAG, mTextView.getPaintFlags());

        mTextView.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
        assertEquals(Paint.UNDERLINE_TEXT_FLAG | Paint.FAKE_BOLD_TEXT_FLAG,
                mTextView.getPaintFlags());

        mTextView.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
        assertEquals(Paint.STRIKE_THRU_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG,
                mTextView.getPaintFlags());
    }

    @MediumTest
    public void testHeight() {
        mTextView = findTextView(R.id.textview_text);
        final int originalHeight = mTextView.getHeight();

        // test setMaxHeight
        int newHeight = originalHeight + 1;
        setMaxHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        newHeight = originalHeight - 1;
        setMaxHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        newHeight = -1;
        setMaxHeight(newHeight);
        assertEquals(0, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        newHeight = Integer.MAX_VALUE;
        setMaxHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        // test setMinHeight
        newHeight = originalHeight + 1;
        setMinHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = originalHeight - 1;
        setMinHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = -1;
        setMinHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        // reset min and max height
        setMinHeight(0);
        setMaxHeight(Integer.MAX_VALUE);

        // test setHeight
        newHeight = originalHeight + 1;
        setHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = originalHeight - 1;
        setHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = -1;
        setHeight(newHeight);
        assertEquals(0, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        setHeight(originalHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(originalHeight, mTextView.getMaxHeight());
        assertEquals(originalHeight, mTextView.getMinHeight());

        // setting max/min lines should cause getMaxHeight/getMinHeight to return -1
        setMaxLines(2);
        assertEquals("Setting maxLines should return -1 fir maxHeight",
                -1, mTextView.getMaxHeight());

        setMinLines(1);
        assertEquals("Setting minLines should return -1 for minHeight",
                -1, mTextView.getMinHeight());
    }

    @MediumTest
    public void testWidth() {
        mTextView = findTextView(R.id.textview_text);
        int originalWidth = mTextView.getWidth();

        int newWidth = mTextView.getWidth() / 8;
        setWidth(newWidth);
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMaxWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        // Min Width
        newWidth = originalWidth + 1;
        setMinWidth(newWidth);
        assertEquals(1, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        newWidth = originalWidth - 1;
        setMinWidth(originalWidth - 1);
        assertEquals(2, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        // Width
        newWidth = originalWidth + 1;
        setWidth(newWidth);
        assertEquals(1, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMaxWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        newWidth = originalWidth - 1;
        setWidth(newWidth);
        assertEquals(2, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMaxWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        // setting ems should cause getMaxWidth/getMinWidth to return -1
        setEms(1);
        assertEquals("Setting ems should return -1 for maxWidth", -1, mTextView.getMaxWidth());
        assertEquals("Setting ems should return -1 for maxWidth", -1, mTextView.getMinWidth());
    }

    @MediumTest
    public void testSetMinEms() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(1, mTextView.getLineCount());

        final int originalWidth = mTextView.getWidth();
        final int originalEms = originalWidth / mTextView.getLineHeight();

        setMinEms(originalEms + 1);
        assertEquals((originalEms + 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(originalEms + 1, mTextView.getMinEms());

        setMinEms(originalEms - 1);
        assertEquals(originalWidth, mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(originalEms - 1, mTextView.getMinEms());

        setMinWidth(1);
        assertEquals(-1, mTextView.getMinEms());
    }

    @MediumTest
    public void testSetMaxEms() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(1, mTextView.getLineCount());

        final int originalWidth = mTextView.getWidth();
        final int originalEms = originalWidth / mTextView.getLineHeight();

        setMaxEms(originalEms + 1);
        assertEquals(1, mTextView.getLineCount());
        assertEquals(originalWidth, mTextView.getWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms + 1, mTextView.getMaxEms());

        setMaxEms(originalEms - 1);
        assertTrue(1 < mTextView.getLineCount());
        assertEquals((originalEms - 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms - 1, mTextView.getMaxEms());

        setMaxWidth(originalWidth);
        assertEquals(-1, mTextView.getMaxEms());
    }

    @MediumTest
    public void testSetEms() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals("check height", 1, mTextView.getLineCount());
        final int originalWidth = mTextView.getWidth();
        final int originalEms = originalWidth / mTextView.getLineHeight();

        setEms(originalEms + 1);
        assertEquals(1, mTextView.getLineCount());
        assertEquals((originalEms + 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms + 1, mTextView.getMinEms());
        assertEquals(originalEms + 1, mTextView.getMaxEms());

        setEms(originalEms - 1);
        assertTrue((1 < mTextView.getLineCount()));
        assertEquals((originalEms - 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms - 1, mTextView.getMinEms());
        assertEquals(originalEms - 1, mTextView.getMaxEms());
    }

    public void testSetLineSpacing() {
        mTextView = new TextView(mActivity);
        int originalLineHeight = mTextView.getLineHeight();

        // normal
        float add = 1.2f;
        float mult = 1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());
        add = 0.0f;
        mult = 1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());

        // abnormal
        add = -1.2f;
        mult = 1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());
        add = -1.2f;
        mult = -1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());
        add = 1.2f;
        mult = 0.0f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());

        // edge
        add = Float.MIN_VALUE;
        mult = Float.MIN_VALUE;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());

        // edge case where the behavior of Math.round() deviates from
        // FastMath.round(), requiring us to use an explicit 0 value
        add = Float.MAX_VALUE;
        mult = Float.MAX_VALUE;
        setLineSpacing(add, mult);
        assertEquals(0, mTextView.getLineHeight());
    }

    public void testSetElegantLineHeight() {
        mTextView = findTextView(R.id.textview_text);
        assertFalse(mTextView.getPaint().isElegantTextHeight());
        mActivity.runOnUiThread(() -> {
            mTextView.setWidth(mTextView.getWidth() / 3);
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();

        int oldHeight = mTextView.getHeight();
        mActivity.runOnUiThread(() -> mTextView.setElegantTextHeight(true));
        mInstrumentation.waitForIdleSync();

        assertTrue(mTextView.getPaint().isElegantTextHeight());
        assertTrue(mTextView.getHeight() > oldHeight);

        mActivity.runOnUiThread(() -> mTextView.setElegantTextHeight(false));
        mInstrumentation.waitForIdleSync();
        assertFalse(mTextView.getPaint().isElegantTextHeight());
        assertTrue(mTextView.getHeight() == oldHeight);
    }

    public void testInstanceState() {
        // Do not test. Implementation details.
    }

    public void testAccessFreezesText() throws Throwable {
        layout(R.layout.textview_hint_linksclickable_freezestext);

        mTextView = findTextView(R.id.hint_linksClickable_freezesText_default);
        assertFalse(mTextView.getFreezesText());

        mTextView = findTextView(R.id.freezesText_true);
        assertTrue(mTextView.getFreezesText());

        mTextView = findTextView(R.id.freezesText_false);
        assertFalse(mTextView.getFreezesText());

        mTextView.setFreezesText(false);
        assertFalse(mTextView.getFreezesText());

        final CharSequence text = "Hello, TextView.";
        mActivity.runOnUiThread(() -> mTextView.setText(text));
        mInstrumentation.waitForIdleSync();

        final URLSpan urlSpan = new URLSpan("ctstest://TextView/test");
        // TODO: How to simulate the TextView in frozen icicles.
        Instrumentation instrumentation = getInstrumentation();
        ActivityMonitor am = instrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);

        mActivity.runOnUiThread(() -> {
            Uri uri = Uri.parse(urlSpan.getURL());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            mActivity.startActivity(intent);
        });

        Activity newActivity = am.waitForActivityWithTimeout(TIMEOUT);
        assertNotNull(newActivity);
        newActivity.finish();
        instrumentation.removeMonitor(am);
        // the text of TextView is removed.
        mTextView = findTextView(R.id.freezesText_false);

        assertEquals(text.toString(), mTextView.getText().toString());

        mTextView.setFreezesText(true);
        assertTrue(mTextView.getFreezesText());

        mActivity.runOnUiThread(() -> mTextView.setText(text));
        mInstrumentation.waitForIdleSync();
        // TODO: How to simulate the TextView in frozen icicles.
        am = instrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);

        mActivity.runOnUiThread(() -> {
            Uri uri = Uri.parse(urlSpan.getURL());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            mActivity.startActivity(intent);
        });

        Activity oldActivity = newActivity;
        while (true) {
            newActivity = am.waitForActivityWithTimeout(TIMEOUT);
            assertNotNull(newActivity);
            if (newActivity != oldActivity) {
                break;
            }
        }
        newActivity.finish();
        instrumentation.removeMonitor(am);
        // the text of TextView is still there.
        mTextView = findTextView(R.id.freezesText_false);
        assertEquals(text.toString(), mTextView.getText().toString());
    }

    public void testSetEditableFactory() {
        mTextView = new TextView(mActivity);
        String text = "sample";

        final Editable.Factory mockEditableFactory = spy(new Editable.Factory());
        doCallRealMethod().when(mockEditableFactory).newEditable(any(CharSequence.class));
        mTextView.setEditableFactory(mockEditableFactory);

        mTextView.setText(text);
        verify(mockEditableFactory, never()).newEditable(any(CharSequence.class));

        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.SPANNABLE);
        verify(mockEditableFactory, never()).newEditable(any(CharSequence.class));

        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.NORMAL);
        verify(mockEditableFactory, never()).newEditable(any(CharSequence.class));

        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.EDITABLE);
        verify(mockEditableFactory, times(1)).newEditable(text);

        mTextView.setKeyListener(DigitsKeyListener.getInstance());
        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.EDITABLE);
        verify(mockEditableFactory, times(1)).newEditable(text);

        try {
            mTextView.setEditableFactory(null);
            fail("The factory can not set to null!");
        } catch (NullPointerException e) {
        }
    }

    public void testSetSpannableFactory() {
        mTextView = new TextView(mActivity);
        String text = "sample";

        final Spannable.Factory mockSpannableFactory = spy(new Spannable.Factory());
        doCallRealMethod().when(mockSpannableFactory).newSpannable(any(CharSequence.class));
        mTextView.setSpannableFactory(mockSpannableFactory);

        mTextView.setText(text);
        verify(mockSpannableFactory, never()).newSpannable(any(CharSequence.class));

        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.EDITABLE);
        verify(mockSpannableFactory, never()).newSpannable(any(CharSequence.class));

        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.NORMAL);
        verify(mockSpannableFactory, never()).newSpannable(any(CharSequence.class));

        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.SPANNABLE);
        verify(mockSpannableFactory, times(1)).newSpannable(text);

        mTextView.setMovementMethod(LinkMovementMethod.getInstance());
        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.NORMAL);
        verify(mockSpannableFactory, times(1)).newSpannable(text);

        try {
            mTextView.setSpannableFactory(null);
            fail("The factory can not set to null!");
        } catch (NullPointerException e) {
        }
    }

    public void testTextChangedListener() {
        mTextView = new TextView(mActivity);
        MockTextWatcher watcher0 = new MockTextWatcher();
        MockTextWatcher watcher1 = new MockTextWatcher();

        mTextView.addTextChangedListener(watcher0);
        mTextView.addTextChangedListener(watcher1);

        watcher0.reset();
        watcher1.reset();
        mTextView.setText("Changed");
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertTrue(watcher1.hasCalledBeforeTextChanged());
        assertTrue(watcher1.hasCalledOnTextChanged());
        assertTrue(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        // BeforeTextChanged and OnTextChanged are called though the strings are same
        mTextView.setText("Changed");
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertTrue(watcher1.hasCalledBeforeTextChanged());
        assertTrue(watcher1.hasCalledOnTextChanged());
        assertTrue(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        // BeforeTextChanged and OnTextChanged are called twice (The text is not
        // Editable, so in Append() it calls setText() first)
        mTextView.append("and appended");
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertTrue(watcher1.hasCalledBeforeTextChanged());
        assertTrue(watcher1.hasCalledOnTextChanged());
        assertTrue(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        // Methods are not called if the string does not change
        mTextView.append("");
        assertFalse(watcher0.hasCalledBeforeTextChanged());
        assertFalse(watcher0.hasCalledOnTextChanged());
        assertFalse(watcher0.hasCalledAfterTextChanged());
        assertFalse(watcher1.hasCalledBeforeTextChanged());
        assertFalse(watcher1.hasCalledOnTextChanged());
        assertFalse(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        mTextView.removeTextChangedListener(watcher1);
        mTextView.setText(null);
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertFalse(watcher1.hasCalledBeforeTextChanged());
        assertFalse(watcher1.hasCalledOnTextChanged());
        assertFalse(watcher1.hasCalledAfterTextChanged());
    }

    public void testSetTextKeepState1() {
        mTextView = new TextView(mActivity);

        String longString = "very long content";
        String shortString = "short";

        // selection is at the exact place which is inside the short string
        mTextView.setText(longString, BufferType.SPANNABLE);
        Selection.setSelection((Spannable) mTextView.getText(), 3);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(3, mTextView.getSelectionStart());
        assertEquals(3, mTextView.getSelectionEnd());

        // selection is at the exact place which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), shortString.length() + 1);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        // select the sub string which is inside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 1, 4);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(1, mTextView.getSelectionStart());
        assertEquals(4, mTextView.getSelectionEnd());

        // select the sub string which ends outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 2, shortString.length() + 1);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(2, mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        // select the sub string which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(),
                shortString.length() + 1, shortString.length() + 3);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());
    }

    @UiThreadTest
    public void testGetEditableText() {
        TextView tv = findTextView(R.id.textview_text);

        String text = "Hello";
        tv.setText(text, BufferType.EDITABLE);
        assertEquals(text, tv.getText().toString());
        assertTrue(tv.getText() instanceof Editable);
        assertEquals(text, tv.getEditableText().toString());

        tv.setText(text, BufferType.SPANNABLE);
        assertEquals(text, tv.getText().toString());
        assertTrue(tv.getText() instanceof Spannable);
        assertNull(tv.getEditableText());

        tv.setText(null, BufferType.EDITABLE);
        assertEquals("", tv.getText().toString());
        assertTrue(tv.getText() instanceof Editable);
        assertEquals("", tv.getEditableText().toString());

        tv.setText(null, BufferType.SPANNABLE);
        assertEquals("", tv.getText().toString());
        assertTrue(tv.getText() instanceof Spannable);
        assertNull(tv.getEditableText());
    }

    @UiThreadTest
    public void testSetText2() {
        String string = "This is a test for setting text content by char array";
        char[] input = string.toCharArray();
        TextView tv = findTextView(R.id.textview_text);

        tv.setText(input, 0, input.length);
        assertEquals(string, tv.getText().toString());

        tv.setText(input, 0, 5);
        assertEquals(string.substring(0, 5), tv.getText().toString());

        try {
            tv.setText(input, -1, input.length);
            fail("Should throw exception if the start position is negative!");
        } catch (IndexOutOfBoundsException exception) {
        }

        try {
            tv.setText(input, 0, -1);
            fail("Should throw exception if the length is negative!");
        } catch (IndexOutOfBoundsException exception) {
        }

        try {
            tv.setText(input, 1, input.length);
            fail("Should throw exception if the end position is out of index!");
        } catch (IndexOutOfBoundsException exception) {
        }

        tv.setText(input, 1, 0);
        assertEquals("", tv.getText().toString());
    }

    @UiThreadTest
    public void testSetText1() {
        mTextView = findTextView(R.id.textview_text);

        String longString = "very long content";
        String shortString = "short";

        // selection is at the exact place which is inside the short string
        mTextView.setText(longString, BufferType.SPANNABLE);
        Selection.setSelection((Spannable) mTextView.getText(), 3);
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(3, mTextView.getSelectionStart());
        assertEquals(3, mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // selection is at the exact place which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), longString.length());
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // select the sub string which is inside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 1, shortString.length() - 1);
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(1, mTextView.getSelectionStart());
        assertEquals(shortString.length() - 1, mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // select the sub string which ends outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 2, longString.length());
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(2, mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // select the sub string which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(),
                shortString.length() + 1, shortString.length() + 3);
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());
    }

    @UiThreadTest
    public void testSetText3() {
        TextView tv = findTextView(R.id.textview_text);

        int resId = R.string.text_view_hint;
        String result = mActivity.getResources().getString(resId);

        tv.setText(resId);
        assertEquals(result, tv.getText().toString());

        try {
            tv.setText(-1);
            fail("Should throw exception with illegal id");
        } catch (NotFoundException e) {
        }
    }

    @MediumTest
    public void testSetText_updatesHeightAfterRemovingImageSpan() {
        // Height calculation had problems when TextView had width: match_parent
        final int textViewWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        final Spannable text = new SpannableString("some text");
        final int spanHeight = 100;

        // prepare TextView, width: MATCH_PARENT
        TextView textView = new TextView(getActivity());
        textView.setSingleLine(true);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 2);
        textView.setPadding(0, 0, 0, 0);
        textView.setIncludeFontPadding(false);
        textView.setText(text);
        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(textViewWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(textView, layoutParams);
        layout.setLayoutParams(layoutParams);
        mActivity.runOnUiThread(() -> getActivity().setContentView(layout));
        getInstrumentation().waitForIdleSync();

        // measure height of text with no span
        final int heightWithoutSpan = textView.getHeight();
        assertTrue("Text height should be smaller than span height",
                heightWithoutSpan < spanHeight);

        // add ImageSpan to text
        Drawable drawable = mInstrumentation.getContext().getDrawable(R.drawable.scenery);
        drawable.setBounds(0, 0, spanHeight, spanHeight);
        ImageSpan span = new ImageSpan(drawable);
        text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mActivity.runOnUiThread(() -> textView.setText(text));
        mInstrumentation.waitForIdleSync();

        // measure height with span
        final int heightWithSpan = textView.getHeight();
        assertTrue("Text height should be greater or equal than span height",
                heightWithSpan >= spanHeight);

        // remove the span
        text.removeSpan(span);
        mActivity.runOnUiThread(() -> textView.setText(text));
        mInstrumentation.waitForIdleSync();

        final int heightAfterRemoveSpan = textView.getHeight();
        assertEquals("Text height should be same after removing the span",
                heightWithoutSpan, heightAfterRemoveSpan);
    }

    public void testRemoveSelectionWithSelectionHandles() {
        initTextViewForTypingOnUiThread();

        assertFalse(mTextView.isTextSelectable());
        mActivity.runOnUiThread(() -> {
            mTextView.setTextIsSelectable(true);
            mTextView.setText("abcd", BufferType.EDITABLE);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isTextSelectable());

        // Long click on the text selects all text and shows selection handlers. The view has an
        // attribute layout_width="wrap_content", so clicked location (the center of the view)
        // should be on the text.
        CtsTouchUtils.emulateLongClick(mInstrumentation, mTextView);

        mActivity.runOnUiThread(() -> Selection.removeSelection((Spannable) mTextView.getText()));
        mInstrumentation.waitForIdleSync();

        assertTrue(TextUtils.equals("abcd", mTextView.getText()));
    }

    public void testUndo_insert() {
        initTextViewForTypingOnUiThread();

        // Type some text.
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Precondition: The cursor is at the end of the text.
            assertEquals(3, mTextView.getSelectionStart());

            // Undo removes the typed string in one step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());

            // Redo restores the text and cursor position.
            mTextView.onTextContextMenuItem(android.R.id.redo);
            assertEquals("abc", mTextView.getText().toString());
            assertEquals(3, mTextView.getSelectionStart());

            // Undoing the redo clears the text again.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());

            // Undo when the undo stack is empty does nothing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testUndo_delete() {
        initTextViewForTypingOnUiThread();

        // Simulate deleting text and undoing it.
        mKeyEventUtil.sendString(mTextView, "xyz");
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_DEL);
        mActivity.runOnUiThread(() -> {
            // Precondition: The text was actually deleted.
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());

            // Undo restores the typed string and cursor position in one step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("xyz", mTextView.getText().toString());
            assertEquals(3, mTextView.getSelectionStart());

            // Redo removes the text in one step.
            mTextView.onTextContextMenuItem(android.R.id.redo);
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());

            // Undoing the redo restores the text again.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("xyz", mTextView.getText().toString());
            assertEquals(3, mTextView.getSelectionStart());

            // Undoing again undoes the original typing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());
        });
        mInstrumentation.waitForIdleSync();
    }

    // Initialize the text view for simulated IME typing. Must be called on UI thread.
    private InputConnection initTextViewForSimulatedIme() {
        mTextView = findTextView(R.id.textview_text);
        return initTextViewForSimulatedIme(mTextView);
    }

    private InputConnection initTextViewForSimulatedIme(TextView textView) {
        textView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
        textView.setText("", BufferType.EDITABLE);
        return textView.onCreateInputConnection(new EditorInfo());
    }

    // Simulates IME composing text behavior.
    private void setComposingTextInBatch(InputConnection input, CharSequence text) {
        input.beginBatchEdit();
        input.setComposingText(text, 1);  // Leave cursor at end.
        input.endBatchEdit();
    }

    @UiThreadTest
    public void testUndo_imeInsertLatin() {
        InputConnection input = initTextViewForSimulatedIme();

        // Simulate IME text entry behavior. The Latin IME enters text by replacing partial words,
        // such as "c" -> "ca" -> "cat" -> "cat ".
        setComposingTextInBatch(input, "c");
        setComposingTextInBatch(input, "ca");

        // The completion and space are added in the same batch.
        input.beginBatchEdit();
        input.commitText("cat", 1);
        input.commitText(" ", 1);
        input.endBatchEdit();

        // The repeated replacements undo in a single step.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    @UiThreadTest
    public void testUndo_imeInsertJapanese() {
        InputConnection input = initTextViewForSimulatedIme();

        // The Japanese IME does repeated replacements of Latin characters to hiragana to kanji.
        final String HA = "\u306F";  // HIRAGANA LETTER HA
        final String NA = "\u306A";  // HIRAGANA LETTER NA
        setComposingTextInBatch(input, "h");
        setComposingTextInBatch(input, HA);
        setComposingTextInBatch(input, HA + "n");
        setComposingTextInBatch(input, HA + NA);

        // The result may be a surrogate pair. The composition ends in the same batch.
        input.beginBatchEdit();
        input.commitText("\uD83C\uDF37", 1);  // U+1F337 TULIP
        input.setComposingText("", 1);
        input.endBatchEdit();

        // The repeated replacements are a single undo step.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    @UiThreadTest
    public void testUndo_imeInsertAndDeleteLatin() {
        InputConnection input = initTextViewForSimulatedIme();

        setComposingTextInBatch(input, "t");
        setComposingTextInBatch(input, "te");
        setComposingTextInBatch(input, "tes");
        setComposingTextInBatch(input, "test");
        setComposingTextInBatch(input, "tes");
        setComposingTextInBatch(input, "te");
        setComposingTextInBatch(input, "t");

        input.beginBatchEdit();
        input.setComposingText("", 1);
        input.finishComposingText();
        input.endBatchEdit();

        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("test", mTextView.getText().toString());
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    @UiThreadTest
    public void testUndo_imeAutoCorrection() {
        mTextView = findTextView(R.id.textview_text);
        TextView spiedTextView = spy(mTextView);
        InputConnection input = initTextViewForSimulatedIme(spiedTextView);

        // Start typing a composition.
        setComposingTextInBatch(input, "t");
        setComposingTextInBatch(input, "te");
        setComposingTextInBatch(input, "teh");

        CorrectionInfo correctionInfo = new CorrectionInfo(0, "teh", "the");
        reset(spiedTextView);
        input.beginBatchEdit();
        // Auto correct "teh" to "the".
        assertTrue(input.commitCorrection(correctionInfo));
        input.commitText("the", 1);
        input.endBatchEdit();

        verify(spiedTextView, times(1)).onCommitCorrection(refEq(correctionInfo));

        assertEquals("the", spiedTextView.getText().toString());
        spiedTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("teh", spiedTextView.getText().toString());
        spiedTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", spiedTextView.getText().toString());
    }

    @UiThreadTest
    public void testUndo_imeAutoCompletion() {
        mTextView = findTextView(R.id.textview_text);
        TextView spiedTextView = spy(mTextView);
        InputConnection input = initTextViewForSimulatedIme(spiedTextView);

        // Start typing a composition.
        setComposingTextInBatch(input, "a");
        setComposingTextInBatch(input, "an");
        setComposingTextInBatch(input, "and");

        CompletionInfo completionInfo = new CompletionInfo(0, 0, "android");
        reset(spiedTextView);
        input.beginBatchEdit();
        // Auto complete "and" to "android".
        assertTrue(input.commitCompletion(completionInfo));
        input.commitText("android", 1);
        input.endBatchEdit();

        verify(spiedTextView, times(1)).onCommitCompletion(refEq(completionInfo));

        assertEquals("android", spiedTextView.getText().toString());
        spiedTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", spiedTextView.getText().toString());
    }

    @UiThreadTest
    public void testUndo_imeCancel() {
        InputConnection input = initTextViewForSimulatedIme();
        mTextView.setText("flower");

        // Start typing a composition.
        final String HA = "\u306F";  // HIRAGANA LETTER HA
        setComposingTextInBatch(input, "h");
        setComposingTextInBatch(input, HA);
        setComposingTextInBatch(input, HA + "n");

        // Cancel the composition.
        setComposingTextInBatch(input, "");

        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals(HA + "n" + "flower", mTextView.getText().toString());
        mTextView.onTextContextMenuItem(android.R.id.redo);
        assertEquals("flower", mTextView.getText().toString());
    }

    @UiThreadTest
    public void testUndo_imeEmptyBatch() {
        InputConnection input = initTextViewForSimulatedIme();
        mTextView.setText("flower");

        // Send an empty batch edit. This happens if the IME is hidden and shown.
        input.beginBatchEdit();
        input.endBatchEdit();

        // Undo and redo do nothing.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("flower", mTextView.getText().toString());
        mTextView.onTextContextMenuItem(android.R.id.redo);
        assertEquals("flower", mTextView.getText().toString());
    }

    public void testUndo_setText() {
        initTextViewForTypingOnUiThread();

        // Create two undo operations, an insert and a delete.
        mKeyEventUtil.sendString(mTextView, "xyz");
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_DEL);
        mActivity.runOnUiThread(() -> {
            // Calling setText() clears both undo operations, so undo doesn't happen.
            mTextView.setText("Hello", BufferType.EDITABLE);
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("Hello", mTextView.getText().toString());

            // Clearing text programmatically does not undo either.
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testRedo_setText() {
        initTextViewForTypingOnUiThread();

        // Type some text. This creates an undo entry.
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Undo the typing to create a redo entry.
            mTextView.onTextContextMenuItem(android.R.id.undo);

            // Calling setText() clears the redo stack, so redo doesn't happen.
            mTextView.setText("Hello", BufferType.EDITABLE);
            mTextView.onTextContextMenuItem(android.R.id.redo);
            assertEquals("Hello", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testUndo_directAppend() {
        initTextViewForTypingOnUiThread();

        // Type some text.
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Programmatically append some text.
            mTextView.append("def");
            assertEquals("abcdef", mTextView.getText().toString());

            // Undo removes the append as a separate step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("abc", mTextView.getText().toString());

            // Another undo removes the original typing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testUndo_directInsert() {
        initTextViewForTypingOnUiThread();

        // Type some text.
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Directly modify the underlying Editable to insert some text.
            // NOTE: This is a violation of the API of getText() which specifies that the
            // returned object should not be modified. However, some apps do this anyway and
            // the framework needs to handle it.
            Editable text = (Editable) mTextView.getText();
            text.insert(0, "def");
            assertEquals("defabc", mTextView.getText().toString());

            // Undo removes the insert as a separate step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("abc", mTextView.getText().toString());

            // Another undo removes the original typing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    public void testUndo_noCursor() {
        initTextViewForTyping();

        // Append some text to create an undo operation. There is no cursor present.
        mTextView.append("cat");

        // Place the cursor at the end of the text so the undo will have to change it.
        Selection.setSelection((Spannable) mTextView.getText(), 3);

        // Undo the append. This should not crash, despite not having a valid cursor
        // position in the undo operation.
        mTextView.onTextContextMenuItem(android.R.id.undo);
    }

    public void testUndo_textWatcher() {
        initTextViewForTypingOnUiThread();

        // Add a TextWatcher that converts the text to spaces on each change.
        mTextView.addTextChangedListener(new ConvertToSpacesTextWatcher());

        // Type some text.
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // TextWatcher altered the text.
            assertEquals("   ", mTextView.getText().toString());

            // Undo reverses both changes in one step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    public void testUndo_textWatcherDirectAppend() {
        initTextViewForTyping();

        // Add a TextWatcher that converts the text to spaces on each change.
        mTextView.addTextChangedListener(new ConvertToSpacesTextWatcher());

        // Programmatically append some text. The TextWatcher changes it to spaces.
        mTextView.append("abc");
        assertEquals("   ", mTextView.getText().toString());

        // Undo reverses both changes in one step.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    public void testUndo_shortcuts() {
        initTextViewForTypingOnUiThread();

        // Type some text.
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Pressing Control-Z triggers undo.
            KeyEvent control = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0,
                    KeyEvent.META_CTRL_LEFT_ON);
            assertTrue(mTextView.onKeyShortcut(KeyEvent.KEYCODE_Z, control));
            assertEquals("", mTextView.getText().toString());

            // Pressing Control-Shift-Z triggers redo.
            KeyEvent controlShift = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z,
                    0, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
            assertTrue(mTextView.onKeyShortcut(KeyEvent.KEYCODE_Z, controlShift));
            assertEquals("abc", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testUndo_saveInstanceState() {
        initTextViewForTypingOnUiThread();

        // Type some text to create an undo operation.
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Parcel and unparcel the TextView.
            Parcelable state = mTextView.onSaveInstanceState();
            mTextView.onRestoreInstanceState(state);
        });
        mInstrumentation.waitForIdleSync();

        // Delete a character to create a new undo operation.
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_DEL);
        mActivity.runOnUiThread(() -> {
            assertEquals("ab", mTextView.getText().toString());

            // Undo the delete.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("abc", mTextView.getText().toString());

            // Undo the typing, which verifies that the original undo operation was parceled
            // correctly.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());

            // Parcel and unparcel the undo stack (which is empty but has been used and may
            // contain other state).
            Parcelable state = mTextView.onSaveInstanceState();
            mTextView.onRestoreInstanceState(state);
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testUndo_saveInstanceStateEmpty() {
        initTextViewForTypingOnUiThread();

        // Type and delete to create two new undo operations.
        mKeyEventUtil.sendString(mTextView, "a");
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_DEL);
        mActivity.runOnUiThread(() -> {
            // Empty the undo stack then parcel and unparcel the TextView. While the undo
            // stack contains no operations it may contain other state.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            mTextView.onTextContextMenuItem(android.R.id.undo);
            Parcelable state = mTextView.onSaveInstanceState();
            mTextView.onRestoreInstanceState(state);
        });
        mInstrumentation.waitForIdleSync();

        // Create two more undo operations.
        mKeyEventUtil.sendString(mTextView, "b");
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_DEL);
        mActivity.runOnUiThread(() -> {
            // Verify undo still works.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("b", mTextView.getText().toString());
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    public void testCopyAndPaste() {
        initTextViewForTyping();

        mTextView.setText("abcd", BufferType.EDITABLE);
        mTextView.setSelected(true);

        // Copy "bc".
        Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        mTextView.onTextContextMenuItem(android.R.id.copy);

        // Paste "bc" between "b" and "c".
        Selection.setSelection((Spannable) mTextView.getText(), 2, 2);
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("abbccd", mTextView.getText().toString());

        // Select entire text and paste "bc".
        Selection.selectAll((Spannable) mTextView.getText());
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("bc", mTextView.getText().toString());
    }

    public void testCopyAndPaste_byKey() {
        initTextViewForTypingOnUiThread();

        // Type "abc".
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Select "bc"
            Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        });
        mInstrumentation.waitForIdleSync();
        // Copy "bc"
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_COPY);

        mActivity.runOnUiThread(() -> {
            // Set cursor between 'b' and 'c'.
            Selection.setSelection((Spannable) mTextView.getText(), 2, 2);
        });
        mInstrumentation.waitForIdleSync();
        // Paste "bc"
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_PASTE);
        assertEquals("abbcc", mTextView.getText().toString());

        mActivity.runOnUiThread(() -> {
            Selection.selectAll((Spannable) mTextView.getText());
            KeyEvent copyWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_COPY, 0, KeyEvent.META_SHIFT_LEFT_ON);
            // Shift + copy doesn't perform copy.
            mTextView.onKeyDown(KeyEvent.KEYCODE_COPY, copyWithMeta);
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
            mTextView.onTextContextMenuItem(android.R.id.paste);
            assertEquals("bcabbcc", mTextView.getText().toString());

            Selection.selectAll((Spannable) mTextView.getText());
            copyWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COPY, 0,
                    KeyEvent.META_CTRL_LEFT_ON);
            // Control + copy doesn't perform copy.
            mTextView.onKeyDown(KeyEvent.KEYCODE_COPY, copyWithMeta);
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
            mTextView.onTextContextMenuItem(android.R.id.paste);
            assertEquals("bcbcabbcc", mTextView.getText().toString());

            Selection.selectAll((Spannable) mTextView.getText());
            copyWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COPY, 0,
                    KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_CTRL_LEFT_ON);
            // Control + Shift + copy doesn't perform copy.
            mTextView.onKeyDown(KeyEvent.KEYCODE_COPY, copyWithMeta);
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
            mTextView.onTextContextMenuItem(android.R.id.paste);
            assertEquals("bcbcbcabbcc", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    public void testCutAndPaste() {
        initTextViewForTyping();

        mTextView.setText("abcd", BufferType.EDITABLE);
        mTextView.setSelected(true);

        // Cut "bc".
        Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        mTextView.onTextContextMenuItem(android.R.id.cut);
        assertEquals("ad", mTextView.getText().toString());

        // Cut "ad".
        Selection.setSelection((Spannable) mTextView.getText(), 0, 2);
        mTextView.onTextContextMenuItem(android.R.id.cut);
        assertEquals("", mTextView.getText().toString());

        // Paste "ad".
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("ad", mTextView.getText().toString());
    }

    public void testCutAndPaste_byKey() {
        initTextViewForTypingOnUiThread();

        // Type "abc".
        mKeyEventUtil.sendString(mTextView, "abc");
        mActivity.runOnUiThread(() -> {
            // Select "bc"
            Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        });
        mInstrumentation.waitForIdleSync();
        // Cut "bc"
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_CUT);

        mActivity.runOnUiThread(() -> {
            assertEquals("a", mTextView.getText().toString());
            // Move cursor to the head
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
        });
        mInstrumentation.waitForIdleSync();
        // Paste "bc"
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_PASTE);
        assertEquals("bca", mTextView.getText().toString());

        mInstrumentation.runOnMainSync(() -> {
            Selection.selectAll((Spannable) mTextView.getText());
            KeyEvent cutWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_CUT, 0, KeyEvent.META_SHIFT_LEFT_ON);
            // Shift + cut doesn't perform cut.
            mTextView.onKeyDown(KeyEvent.KEYCODE_CUT, cutWithMeta);
            assertEquals("bca", mTextView.getText().toString());

            cutWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CUT, 0,
                    KeyEvent.META_CTRL_LEFT_ON);
            // Control + cut doesn't perform cut.
            mTextView.onKeyDown(KeyEvent.KEYCODE_CUT, cutWithMeta);
            assertEquals("bca", mTextView.getText().toString());

            cutWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CUT, 0,
                    KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_CTRL_LEFT_ON);
            // Control + Shift + cut doesn't perform cut.
            mTextView.onKeyDown(KeyEvent.KEYCODE_CUT, cutWithMeta);
            assertEquals("bca", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    private static boolean hasSpansAtMiddleOfText(final TextView textView, final Class<?> type) {
        final Spannable spannable = (Spannable)textView.getText();
        final int at = spannable.length() / 2;
        return spannable.getSpans(at, at, type).length > 0;
    }

    @UiThreadTest
    public void testCutAndPaste_withAndWithoutStyle() {
        initTextViewForTyping();

        mTextView.setText("example", BufferType.EDITABLE);
        mTextView.setSelected(true);

        // Set URLSpan.
        final Spannable spannable = (Spannable) mTextView.getText();
        spannable.setSpan(new URLSpan("http://example.com"), 0, spannable.length(), 0);
        assertTrue(hasSpansAtMiddleOfText(mTextView, URLSpan.class));

        // Cut entire text.
        Selection.selectAll((Spannable) mTextView.getText());
        mTextView.onTextContextMenuItem(android.R.id.cut);
        assertEquals("", mTextView.getText().toString());

        // Paste without style.
        mTextView.onTextContextMenuItem(android.R.id.pasteAsPlainText);
        assertEquals("example", mTextView.getText().toString());
        // Check that the text doesn't have URLSpan.
        assertFalse(hasSpansAtMiddleOfText(mTextView, URLSpan.class));

        // Paste with style.
        Selection.selectAll((Spannable) mTextView.getText());
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("example", mTextView.getText().toString());
        // Check that the text has URLSpan.
        assertTrue(hasSpansAtMiddleOfText(mTextView, URLSpan.class));
    }

    @UiThreadTest
    public void testSaveInstanceState() {
        // should save text when freezesText=true
        TextView originalTextView = new TextView(mActivity);
        final String text = "This is a string";
        originalTextView.setText(text);
        originalTextView.setFreezesText(true);  // needed to actually save state
        Parcelable state = originalTextView.onSaveInstanceState();

        TextView restoredTextView = new TextView(mActivity);
        restoredTextView.onRestoreInstanceState(state);
        assertEquals(text, restoredTextView.getText().toString());
    }

    @UiThreadTest
    public void testOnSaveInstanceState_whenFreezesTextIsFalse() {
        final String text = "This is a string";
        { // should not save text when freezesText=false
            // prepare TextView for before saveInstanceState
            TextView textView1 = new TextView(mActivity);
            textView1.setFreezesText(false);
            textView1.setText(text);

            // prepare TextView for after saveInstanceState
            TextView textView2 = new TextView(mActivity);
            textView2.setFreezesText(false);

            textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

            assertEquals("", textView2.getText().toString());
        }

        { // should not save text even when textIsSelectable=true
            // prepare TextView for before saveInstanceState
            TextView textView1 = new TextView(mActivity);
            textView1.setFreezesText(false);
            textView1.setTextIsSelectable(true);
            textView1.setText(text);

            // prepare TextView for after saveInstanceState
            TextView textView2 = new TextView(mActivity);
            textView2.setFreezesText(false);
            textView2.setTextIsSelectable(true);

            textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

            assertEquals("", textView2.getText().toString());
        }
    }

    @UiThreadTest
    @SmallTest
    public void testOnSaveInstanceState_doesNotSaveSelectionWhenDoesNotExist() {
        // prepare TextView for before saveInstanceState
        final String text = "This is a string";
        TextView textView1 = new TextView(mActivity);
        textView1.setFreezesText(true);
        textView1.setText(text);

        // prepare TextView for after saveInstanceState
        TextView textView2 = new TextView(mActivity);
        textView2.setFreezesText(true);

        textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

        assertEquals(-1, textView2.getSelectionStart());
        assertEquals(-1, textView2.getSelectionEnd());
    }

    @UiThreadTest
    @SmallTest
    public void testOnSaveInstanceState_doesNotRestoreSelectionWhenTextIsAbsent() {
        // prepare TextView for before saveInstanceState
        final String text = "This is a string";
        TextView textView1 = new TextView(mActivity);
        textView1.setFreezesText(false);
        textView1.setTextIsSelectable(true);
        textView1.setText(text);
        Selection.setSelection((Spannable) textView1.getText(), 2, text.length() - 2);

        // prepare TextView for after saveInstanceState
        TextView textView2 = new TextView(mActivity);
        textView2.setFreezesText(false);
        textView2.setTextIsSelectable(true);

        textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

        assertEquals("", textView2.getText().toString());
        //when textIsSelectable, selection start and end are initialized to 0
        assertEquals(0, textView2.getSelectionStart());
        assertEquals(0, textView2.getSelectionEnd());
    }

    @UiThreadTest
    @SmallTest
    public void testOnSaveInstanceState_savesSelectionWhenExists() {
        final String text = "This is a string";
        // prepare TextView for before saveInstanceState
        TextView textView1 = new TextView(mActivity);
        textView1.setFreezesText(true);
        textView1.setTextIsSelectable(true);
        textView1.setText(text);
        Selection.setSelection((Spannable) textView1.getText(), 2, text.length() - 2);

        // prepare TextView for after saveInstanceState
        TextView textView2 = new TextView(mActivity);
        textView2.setFreezesText(true);
        textView2.setTextIsSelectable(true);

        textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

        assertEquals(textView1.getSelectionStart(), textView2.getSelectionStart());
        assertEquals(textView1.getSelectionEnd(), textView2.getSelectionEnd());
    }

    @UiThreadTest
    public void testSetText() {
        TextView tv = findTextView(R.id.textview_text);

        int resId = R.string.text_view_hint;
        String result = mActivity.getResources().getString(resId);

        tv.setText(resId, BufferType.EDITABLE);
        assertEquals(result, tv.getText().toString());
        assertTrue(tv.getText() instanceof Editable);

        tv.setText(resId, BufferType.SPANNABLE);
        assertEquals(result, tv.getText().toString());
        assertTrue(tv.getText() instanceof Spannable);

        try {
            tv.setText(-1, BufferType.EDITABLE);
            fail("Should throw exception with illegal id");
        } catch (NotFoundException e) {
        }
    }

    @UiThreadTest
    public void testAccessHint() {
        mActivity.setContentView(R.layout.textview_hint_linksclickable_freezestext);

        mTextView = findTextView(R.id.hint_linksClickable_freezesText_default);
        assertNull(mTextView.getHint());

        mTextView = findTextView(R.id.hint_blank);
        assertEquals("", mTextView.getHint());

        mTextView = findTextView(R.id.hint_string);
        assertEquals(mActivity.getResources().getString(R.string.text_view_simple_hint),
                mTextView.getHint());

        mTextView = findTextView(R.id.hint_resid);
        assertEquals(mActivity.getResources().getString(R.string.text_view_hint),
                mTextView.getHint());

        mTextView.setHint("This is hint");
        assertEquals("This is hint", mTextView.getHint().toString());

        mTextView.setHint(R.string.text_view_hello);
        assertEquals(mActivity.getResources().getString(R.string.text_view_hello),
                mTextView.getHint().toString());

        // Non-exist resid
        try {
            mTextView.setHint(-1);
            fail("Should throw exception if id is illegal");
        } catch (NotFoundException e) {
        }
    }

    public void testAccessError() {
        mTextView = findTextView(R.id.textview_text);
        assertNull(mTextView.getError());

        final String errorText = "Oops! There is an error";

        mActivity.runOnUiThread(() -> mTextView.setError(null));
        mInstrumentation.waitForIdleSync();
        assertNull(mTextView.getError());

        final Drawable icon = TestUtils.getDrawable(mActivity, R.drawable.failed);
        mActivity.runOnUiThread(() -> mTextView.setError(errorText, icon));
        mInstrumentation.waitForIdleSync();
        assertEquals(errorText, mTextView.getError().toString());
        // can not check whether the drawable is set correctly

        mActivity.runOnUiThread(() -> mTextView.setError(null, null));
        mInstrumentation.waitForIdleSync();
        assertNull(mTextView.getError());

        mActivity.runOnUiThread(() -> {
            mTextView.setKeyListener(DigitsKeyListener.getInstance(""));
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.setError(errorText);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(errorText, mTextView.getError().toString());

        mKeyEventUtil.sendString(mTextView, "a");
        // a key event that will not change the TextView's text
        assertEquals("", mTextView.getText().toString());
        // The icon and error message will not be reset to null
        assertEquals(errorText, mTextView.getError().toString());

        mActivity.runOnUiThread(() -> {
            mTextView.setKeyListener(DigitsKeyListener.getInstance());
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.setError(errorText);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        mKeyEventUtil.sendString(mTextView, "1");
        // a key event cause changes to the TextView's text
        assertEquals("1", mTextView.getText().toString());
        // the error message and icon will be cleared.
        assertNull(mTextView.getError());
    }

    public void testAccessFilters() {
        final InputFilter[] expected = { new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(2) };

        final QwertyKeyListener qwertyKeyListener
                = QwertyKeyListener.getInstance(false, Capitalize.NONE);
        mActivity.runOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setKeyListener(qwertyKeyListener);
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.setFilters(expected);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertSame(expected, mTextView.getFilters());

        mKeyEventUtil.sendString(mTextView, "a");
        // the text is capitalized by InputFilter.AllCaps
        assertEquals("A", mTextView.getText().toString());
        mKeyEventUtil.sendString(mTextView, "b");
        // the text is capitalized by InputFilter.AllCaps
        assertEquals("AB", mTextView.getText().toString());
        mKeyEventUtil.sendString(mTextView, "c");
        // 'C' could not be accepted, because there is a length filter.
        assertEquals("AB", mTextView.getText().toString());

        try {
            mTextView.setFilters(null);
            fail("Should throw IllegalArgumentException!");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testGetFocusedRect() {
        Rect rc = new Rect();

        // Basic
        mTextView = new TextView(mActivity);
        mTextView.getFocusedRect(rc);
        assertEquals(mTextView.getScrollX(), rc.left);
        assertEquals(mTextView.getScrollX() + mTextView.getWidth(), rc.right);
        assertEquals(mTextView.getScrollY(), rc.top);
        assertEquals(mTextView.getScrollY() + mTextView.getHeight(), rc.bottom);

        // Single line
        mTextView = findTextView(R.id.textview_text);
        mTextView.getFocusedRect(rc);
        assertEquals(mTextView.getScrollX(), rc.left);
        assertEquals(mTextView.getScrollX() + mTextView.getWidth(), rc.right);
        assertEquals(mTextView.getScrollY(), rc.top);
        assertEquals(mTextView.getScrollY() + mTextView.getHeight(), rc.bottom);

        mActivity.runOnUiThread(() -> {
            mTextView.setSelected(true);
            SpannableString text = new SpannableString(mTextView.getText());
            Selection.setSelection(text, 3, 13);
            mTextView.setText(text);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        assertNotNull(mTextView.getLayout());
        /* Cursor coordinates from getPrimaryHorizontal() may have a fractional
         * component, while the result of getFocusedRect is in int coordinates.
         * It's not practical for these to match exactly, so we compare that the
         * integer components match - there can be a fractional pixel
         * discrepancy, which should be okay for all practical applications. */
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(3), rc.left);
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(13), rc.right);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(0), rc.bottom);

        mActivity.runOnUiThread(() -> {
            mTextView.setSelected(true);
            SpannableString text = new SpannableString(mTextView.getText());
            Selection.setSelection(text, 13, 3);
            mTextView.setText(text);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        assertNotNull(mTextView.getLayout());
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(3) - 2, rc.left);
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(3) + 2, rc.right);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(0), rc.bottom);

        // Multi lines
        mTextView = findTextView(R.id.textview_text_two_lines);
        mTextView.getFocusedRect(rc);
        assertEquals(mTextView.getScrollX(), rc.left);
        assertEquals(mTextView.getScrollX() + mTextView.getWidth(), rc.right);
        assertEquals(mTextView.getScrollY(), rc.top);
        assertEquals(mTextView.getScrollY() + mTextView.getHeight(), rc.bottom);

        mActivity.runOnUiThread(() -> {
            mTextView.setSelected(true);
            SpannableString text = new SpannableString(mTextView.getText());
            Selection.setSelection(text, 2, 4);
            mTextView.setText(text);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        assertNotNull(mTextView.getLayout());
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(2), rc.left);
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(4), rc.right);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(0), rc.bottom);

        mActivity.runOnUiThread(() -> {
            mTextView.setSelected(true);
            SpannableString text = new SpannableString(mTextView.getText());
            Selection.setSelection(text, 2, 10); // cross the "\n" and two lines
            mTextView.setText(text);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        Path path = new Path();
        mTextView.getLayout().getSelectionPath(2, 10, path);
        RectF rcf = new RectF();
        path.computeBounds(rcf, true);
        assertNotNull(mTextView.getLayout());
        assertEquals(rcf.left - 1, (float) rc.left);
        assertEquals(rcf.right + 1, (float) rc.right);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(1), rc.bottom);

        // Exception
        try {
            mTextView.getFocusedRect(null);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }
    }

    public void testGetLineCount() {
        mTextView = findTextView(R.id.textview_text);
        // this is an one line text with default setting.
        assertEquals(1, mTextView.getLineCount());

        // make it multi-lines
        setMaxWidth(mTextView.getWidth() / 3);
        assertTrue(1 < mTextView.getLineCount());

        // make it to an one line
        setMaxWidth(Integer.MAX_VALUE);
        assertEquals(1, mTextView.getLineCount());

        // set min lines don't effect the lines count for actual text.
        setMinLines(12);
        assertEquals(1, mTextView.getLineCount());

        mTextView = new TextView(mActivity);
        // the internal Layout has not been built.
        assertNull(mTextView.getLayout());
        assertEquals(0, mTextView.getLineCount());
    }

    public void testGetLineBounds() {
        Rect rc = new Rect();
        mTextView = new TextView(mActivity);
        assertEquals(0, mTextView.getLineBounds(0, null));

        assertEquals(0, mTextView.getLineBounds(0, rc));
        assertEquals(0, rc.left);
        assertEquals(0, rc.right);
        assertEquals(0, rc.top);
        assertEquals(0, rc.bottom);

        mTextView = findTextView(R.id.textview_text);
        assertEquals(mTextView.getBaseline(), mTextView.getLineBounds(0, null));

        assertEquals(mTextView.getBaseline(), mTextView.getLineBounds(0, rc));
        assertEquals(0, rc.left);
        assertEquals(mTextView.getWidth(), rc.right);
        assertEquals(0, rc.top);
        assertEquals(mTextView.getHeight(), rc.bottom);

        mActivity.runOnUiThread(() -> {
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getBaseline(), mTextView.getLineBounds(0, rc));
        assertEquals(mTextView.getTotalPaddingLeft(), rc.left);
        assertEquals(mTextView.getWidth() - mTextView.getTotalPaddingRight(), rc.right);
        assertEquals(mTextView.getTotalPaddingTop(), rc.top);
        assertEquals(mTextView.getHeight() - mTextView.getTotalPaddingBottom(), rc.bottom);
    }

    public void testGetBaseLine() {
        mTextView = new TextView(mActivity);
        assertEquals(-1, mTextView.getBaseline());

        mTextView = findTextView(R.id.textview_text);
        assertEquals(mTextView.getLayout().getLineBaseline(0), mTextView.getBaseline());

        mActivity.runOnUiThread(() -> {
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();
        int expected = mTextView.getTotalPaddingTop() + mTextView.getLayout().getLineBaseline(0);
        assertEquals(expected, mTextView.getBaseline());
    }

    public void testPressKey() {
        initTextViewForTypingOnUiThread();

        mKeyEventUtil.sendString(mTextView, "a");
        assertEquals("a", mTextView.getText().toString());
        mKeyEventUtil.sendString(mTextView, "b");
        assertEquals("ab", mTextView.getText().toString());
        mKeyEventUtil.sendKeys(mTextView, KeyEvent.KEYCODE_DEL);
        assertEquals("a", mTextView.getText().toString());
    }

    public void testSetIncludeFontPadding() {
        mTextView = findTextView(R.id.textview_text);
        assertTrue(mTextView.getIncludeFontPadding());
        mActivity.runOnUiThread(() -> {
            mTextView.setWidth(mTextView.getWidth() / 3);
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();

        int oldHeight = mTextView.getHeight();
        mActivity.runOnUiThread(() -> mTextView.setIncludeFontPadding(false));
        mInstrumentation.waitForIdleSync();

        assertTrue(mTextView.getHeight() < oldHeight);
        assertFalse(mTextView.getIncludeFontPadding());
    }

    public void testScroll() {
        mTextView = new TextView(mActivity);

        assertEquals(0, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());

        //don't set the Scroller, nothing changed.
        mTextView.computeScroll();
        assertEquals(0, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());

        //set the Scroller
        Scroller s = new Scroller(mActivity);
        assertNotNull(s);
        s.startScroll(0, 0, 320, 480, 0);
        s.abortAnimation();
        s.forceFinished(false);
        mTextView.setScroller(s);

        mTextView.computeScroll();
        assertEquals(320, mTextView.getScrollX());
        assertEquals(480, mTextView.getScrollY());
    }

    public void testDebug() {
        mTextView = new TextView(mActivity);
        mTextView.debug(0);

        mTextView.setText("Hello!");
        layout(mTextView);
        mTextView.debug(1);
    }

    public void testSelection() {
        mTextView = new TextView(mActivity);
        String text = "This is the content";
        mTextView.setText(text, BufferType.SPANNABLE);
        assertFalse(mTextView.hasSelection());

        Selection.selectAll((Spannable) mTextView.getText());
        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(text.length(), mTextView.getSelectionEnd());
        assertTrue(mTextView.hasSelection());

        int selectionStart = 5;
        int selectionEnd = 7;
        Selection.setSelection((Spannable) mTextView.getText(), selectionStart);
        assertEquals(selectionStart, mTextView.getSelectionStart());
        assertEquals(selectionStart, mTextView.getSelectionEnd());
        assertFalse(mTextView.hasSelection());

        Selection.setSelection((Spannable) mTextView.getText(), selectionStart, selectionEnd);
        assertEquals(selectionStart, mTextView.getSelectionStart());
        assertEquals(selectionEnd, mTextView.getSelectionEnd());
        assertTrue(mTextView.hasSelection());
    }

    @MediumTest
    public void testOnSelectionChanged_isTriggeredWhenSelectionChanges() {
        final MockTextView textView = spy(new MockTextView(mActivity));
        final String text = "any text";
        textView.setText(text, BufferType.SPANNABLE);

        // assert that there is currently no selection
        assertFalse(textView.hasSelection());

        // select all
        Selection.selectAll((Spannable) textView.getText());
        // After selectAll OnSelectionChanged should have been called
        verify(textView, times(1)).onSelectionChanged(0, text.length());

        reset(textView);
        // change selection
        Selection.setSelection((Spannable) textView.getText(), 1, 5);
        verify(textView, times(1)).onSelectionChanged(1, 5);

        reset(textView);
        // clear selection
        Selection.removeSelection((Spannable) textView.getText());
        verify(textView, times(1)).onSelectionChanged(-1, -1);
    }

    @UiThreadTest
    public void testAccessEllipsize() {
        mActivity.setContentView(R.layout.textview_ellipsize);

        mTextView = findTextView(R.id.ellipsize_default);
        assertNull(mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_none);
        assertNull(mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_start);
        assertSame(TruncateAt.START, mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_middle);
        assertSame(TruncateAt.MIDDLE, mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_end);
        assertSame(TruncateAt.END, mTextView.getEllipsize());

        mTextView.setEllipsize(TextUtils.TruncateAt.START);
        assertSame(TextUtils.TruncateAt.START, mTextView.getEllipsize());

        mTextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        assertSame(TextUtils.TruncateAt.MIDDLE, mTextView.getEllipsize());

        mTextView.setEllipsize(TextUtils.TruncateAt.END);
        assertSame(TextUtils.TruncateAt.END, mTextView.getEllipsize());

        mTextView.setEllipsize(null);
        assertNull(mTextView.getEllipsize());

        mTextView.setWidth(10);
        mTextView.setEllipsize(TextUtils.TruncateAt.START);
        mTextView.setText("ThisIsAVeryLongVeryLongVeryLongVeryLongVeryLongWord");
        mTextView.invalidate();

        assertSame(TextUtils.TruncateAt.START, mTextView.getEllipsize());
        // there is no method to check if '...yLongVeryLongWord' is painted in the screen.
    }

    public void testEllipsizeEndAndNoEllipsizeHasSameBaselineForSingleLine() {
        TextView tvEllipsizeEnd = new TextView(getActivity());
        tvEllipsizeEnd.setEllipsize(TruncateAt.END);
        tvEllipsizeEnd.setMaxLines(1);
        tvEllipsizeEnd.setText(LONG_TEXT);

        TextView tvEllipsizeNone = new TextView(getActivity());
        tvEllipsizeNone.setText("a");

        final int textWidth = (int) tvEllipsizeEnd.getPaint().measureText(LONG_TEXT) / 4;
        tvEllipsizeEnd.setWidth(textWidth);
        tvEllipsizeNone.setWidth(textWidth);

        final FrameLayout layout = new FrameLayout(mActivity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(tvEllipsizeEnd, layoutParams);
        layout.addView(tvEllipsizeNone, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivity.runOnUiThread(() -> getActivity().setContentView(layout));
        getInstrumentation().waitForIdleSync();

        assertEquals("Ellipsized and non ellipsized single line texts should have the same " +
                        "baseline",
                tvEllipsizeEnd.getLayout().getLineBaseline(0),
                tvEllipsizeNone.getLayout().getLineBaseline(0));
    }

    public void testEllipsizeEndAndNoEllipsizeHasSameBaselineForMultiLine() {
        TextView tvEllipsizeEnd = new TextView(getActivity());
        tvEllipsizeEnd.setEllipsize(TruncateAt.END);
        tvEllipsizeEnd.setMaxLines(2);
        tvEllipsizeEnd.setText(LONG_TEXT);

        TextView tvEllipsizeNone = new TextView(getActivity());
        tvEllipsizeNone.setMaxLines(2);
        tvEllipsizeNone.setText(LONG_TEXT);

        final int textWidth = (int) tvEllipsizeEnd.getPaint().measureText(LONG_TEXT) / 2;
        tvEllipsizeEnd.setWidth(textWidth);
        tvEllipsizeNone.setWidth(textWidth);

        final FrameLayout layout = new FrameLayout(mActivity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        layout.addView(tvEllipsizeEnd, layoutParams);
        layout.addView(tvEllipsizeNone, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivity.runOnUiThread(() -> getActivity().setContentView(layout));
        getInstrumentation().waitForIdleSync();

        for (int i = 0; i < tvEllipsizeEnd.getLineCount(); i++) {
            assertEquals("Ellipsized and non ellipsized multi line texts should have the same " +
                            "baseline for line " + i,
                    tvEllipsizeEnd.getLayout().getLineBaseline(i),
                    tvEllipsizeNone.getLayout().getLineBaseline(i));
        }
    }

    public void testTextViewInWeigthenedLayoutChangesWidthAfterSetText() {
        final TextView textView = new TextView(getActivity());
        textView.setEllipsize(TruncateAt.END);
        textView.setSingleLine(true);
        textView.setText("a");

        TextView otherTextView = new TextView(getActivity());
        otherTextView.setSingleLine(true);
        otherTextView.setText("any");

        final LinearLayout layout = new LinearLayout(mActivity);
        layout.setOrientation(LinearLayout.HORIZONTAL);

        // TextView under test has weight 1, and width 0
        layout.addView(textView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // other TextView has default weight
        layout.addView(otherTextView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // main layout params
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mActivity.runOnUiThread(() -> getActivity().setContentView(layout));
        getInstrumentation().waitForIdleSync();

        int oldWidth = textView.getWidth();

        mActivity.runOnUiThread(() -> textView.setText("aaa"));
        getInstrumentation().waitForIdleSync();

        assertTrue("TextView should have larger width after a longer text is set",
                textView.getWidth() > oldWidth);
    }

    public void testAccessCursorVisible() {
        mTextView = new TextView(mActivity);

        mTextView.setCursorVisible(true);
        assertTrue(mTextView.isCursorVisible());
        mTextView.setCursorVisible(false);
        assertFalse(mTextView.isCursorVisible());
    }

    public void testOnWindowFocusChanged() {
        // Do not test. Implementation details.
    }

    public void testOnTouchEvent() {
        // Do not test. Implementation details.
    }

    public void testOnTrackballEvent() {
        // Do not test. Implementation details.
    }

    public void testGetTextColors() {
        // TODO: How to get a suitable TypedArray to test this method.
    }

    public void testOnKeyShortcut() {
        // Do not test. Implementation details.
    }

    @UiThreadTest
    public void testPerformLongClick() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setText("This is content");

        View.OnLongClickListener mockOnLongClickListener = mock(View.OnLongClickListener.class);
        when(mockOnLongClickListener.onLongClick(any(View.class))).thenReturn(Boolean.TRUE);

        View.OnCreateContextMenuListener mockOnCreateContextMenuListener =
                mock(View.OnCreateContextMenuListener.class);
        doAnswer((InvocationOnMock invocation) -> {
            ((ContextMenu) invocation.getArguments() [0]).add("menu item");
            return null;
        }).when(mockOnCreateContextMenuListener).onCreateContextMenu(
                any(ContextMenu.class), any(View.class), any(ContextMenuInfo.class));

        mTextView.setOnLongClickListener(mockOnLongClickListener);
        mTextView.setOnCreateContextMenuListener(mockOnCreateContextMenuListener);
        assertTrue(mTextView.performLongClick());
        verify(mockOnLongClickListener, times(1)).onLongClick(mTextView);
        verifyZeroInteractions(mockOnCreateContextMenuListener);

        reset(mockOnLongClickListener);
        when(mockOnLongClickListener.onLongClick(any(View.class))).thenReturn(Boolean.FALSE);
        assertTrue(mTextView.performLongClick());
        verify(mockOnLongClickListener, times(1)).onLongClick(mTextView);
        verify(mockOnCreateContextMenuListener, times(1)).onCreateContextMenu(
                any(ContextMenu.class), eq(mTextView), any(ContextMenuInfo.class));

        reset(mockOnCreateContextMenuListener);
        mTextView.setOnLongClickListener(null);
        doNothing().when(mockOnCreateContextMenuListener).onCreateContextMenu(
                any(ContextMenu.class), any(View.class), any(ContextMenuInfo.class));
        assertFalse(mTextView.performLongClick());
        verifyNoMoreInteractions(mockOnLongClickListener);
        verify(mockOnCreateContextMenuListener, times(1)).onCreateContextMenu(
                any(ContextMenu.class), eq(mTextView), any(ContextMenuInfo.class));
    }

    @UiThreadTest
    public void testTextAttr() {
        mTextView = findTextView(R.id.textview_textAttr);
        // getText
        assertEquals(mActivity.getString(R.string.text_view_hello), mTextView.getText().toString());

        // getCurrentTextColor
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getHintTextColors().getDefaultColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getLinkTextColors().getDefaultColor());

        // getTextScaleX
        assertEquals(1.2f, mTextView.getTextScaleX(), 0.01f);

        // setTextScaleX
        mTextView.setTextScaleX(2.4f);
        assertEquals(2.4f, mTextView.getTextScaleX(), 0.01f);

        mTextView.setTextScaleX(0f);
        assertEquals(0f, mTextView.getTextScaleX(), 0.01f);

        mTextView.setTextScaleX(- 2.4f);
        assertEquals(- 2.4f, mTextView.getTextScaleX(), 0.01f);

        // getTextSize
        assertEquals(20f, mTextView.getTextSize(), 0.01f);

        // getTypeface
        // getTypeface will be null if android:typeface is set to normal,
        // and android:style is not set or is set to normal, and
        // android:fontFamily is not set
        assertNull(mTextView.getTypeface());

        mTextView.setTypeface(Typeface.DEFAULT);
        assertSame(Typeface.DEFAULT, mTextView.getTypeface());
        // null type face
        mTextView.setTypeface(null);
        assertNull(mTextView.getTypeface());

        // default type face, bold style, note: the type face will be changed
        // after call set method
        mTextView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        assertSame(Typeface.BOLD, mTextView.getTypeface().getStyle());

        // null type face, BOLD style
        mTextView.setTypeface(null, Typeface.BOLD);
        assertSame(Typeface.BOLD, mTextView.getTypeface().getStyle());

        // old type face, null style
        mTextView.setTypeface(Typeface.DEFAULT, 0);
        assertEquals(Typeface.NORMAL, mTextView.getTypeface().getStyle());
    }

    @UiThreadTest
    public void testAppend() {
        mTextView = new TextView(mActivity);

        // 1: check the original length, should be blank as initialised.
        assertEquals(0, mTextView.getText().length());

        // 2: append a string use append(CharSquence) into the original blank
        // buffer, check the content. And upgrading it to BufferType.EDITABLE if it was
        // not already editable.
        assertFalse(mTextView.getText() instanceof Editable);
        mTextView.append("Append.");
        assertEquals("Append.", mTextView.getText().toString());
        assertTrue(mTextView.getText() instanceof Editable);

        // 3: append a string from 0~3.
        mTextView.append("Append", 0, 3);
        assertEquals("Append.App", mTextView.getText().toString());
        assertTrue(mTextView.getText() instanceof Editable);

        // 4: append a string from 0~0, nothing will be append as expected.
        mTextView.append("Append", 0, 0);
        assertEquals("Append.App", mTextView.getText().toString());
        assertTrue(mTextView.getText() instanceof Editable);

        // 5: append a string from -3~3. check the wrong left edge.
        try {
            mTextView.append("Append", -3, 3);
            fail("Should throw StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException e) {
        }

        // 6: append a string from 3~10. check the wrong right edge.
        try {
            mTextView.append("Append", 3, 10);
            fail("Should throw StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException e) {
        }

        // 7: append a null string.
        try {
            mTextView.append(null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    @UiThreadTest
    public void testAppend_doesNotAddLinksWhenAppendedTextDoesNotContainLinks() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without URL");

        mTextView.append(" another text without URL");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be zero", 0, urlSpans.length);
        assertEquals("text without URL another text without URL", text.toString());
    }

    @UiThreadTest
    public void testAppend_doesNotAddLinksWhenAutoLinkIsNotEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setText("text without URL");

        mTextView.append(" text with URL http://android.com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be zero", 0, urlSpans.length);
        assertEquals("text without URL text with URL http://android.com", text.toString());
    }

    @UiThreadTest
    public void testAppend_addsLinksWhenAutoLinkIsEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without URL");

        mTextView.append(" text with URL http://android.com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be one after appending a URL", 1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[0].getURL(), "http://android.com");
        assertEquals("text without URL text with URL http://android.com", text.toString());
    }

    @UiThreadTest
    public void testAppend_addsLinksEvenWhenThereAreUrlsSetBefore() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text with URL http://android.com/before");

        mTextView.append(" text with URL http://android.com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be two after appending another URL", 2, urlSpans.length);
        assertEquals("First URLSpan URL should be same",
                urlSpans[0].getURL(), "http://android.com/before");
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[1].getURL(), "http://android.com");
        assertEquals("text with URL http://android.com/before text with URL http://android.com",
                text.toString());
    }

    @UiThreadTest
    public void testAppend_setsMovementMethodWhenTextContainsUrlAndAutoLinkIsEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without a URL");

        mTextView.append(" text with a url: http://android.com");

        assertNotNull("MovementMethod should not be null when text contains url",
                mTextView.getMovementMethod());
        assertTrue("MovementMethod should be instance of LinkMovementMethod when text contains url",
                mTextView.getMovementMethod() instanceof LinkMovementMethod);
    }

    @UiThreadTest
    public void testAppend_addsLinksWhenTextIsSpannableAndContainsUrlAndAutoLinkIsEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without a URL");

        mTextView.append(new SpannableString(" text with a url: http://android.com"));

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be one after appending a URL", 1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[0].getURL(), "http://android.com");
    }

    @UiThreadTest
    public void testAppend_addsLinkIfAppendedTextCompletesPartialUrlAtTheEndOfExistingText() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text with a partial url android.");

        mTextView.append("com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be one after appending to partial URL",
                1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[0].getURL(), "http://android.com");
    }

    @UiThreadTest
    public void testAppend_addsLinkIfAppendedTextUpdatesUrlAtTheEndOfExistingText() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text with a url http://android.com");

        mTextView.append("/textview");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should still be one after extending a URL", 1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the new URL",
                urlSpans[0].getURL(), "http://android.com/textview");
    }

    @MediumTest
    public void testGetLetterSpacing_returnsValueThatWasSet() {
        mTextView = new TextView(mActivity);
        mTextView.setLetterSpacing(2f);
        assertEquals("getLetterSpacing should return the value that was set",
                2f, mTextView.getLetterSpacing());
    }

    @MediumTest
    public void testSetLetterSpacing_changesTextWidth() {
        final TextView textView = new TextView(mActivity);
        textView.setText("aa");
        textView.setLetterSpacing(0f);
        textView.setTextSize(8f);

        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(textView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivity.runOnUiThread(() -> getActivity().setContentView(layout));
        getInstrumentation().waitForIdleSync();

        // measure text with zero letter spacing
        final float zeroSpacing = textView.getLayout().getLineWidth(0);

        getActivity().runOnUiThread(() -> textView.setLetterSpacing(1f));
        getInstrumentation().waitForIdleSync();

        // measure text with single letter spacing
        final float singleSpacing = textView.getLayout().getLineWidth(0);

        getActivity().runOnUiThread(() -> textView.setLetterSpacing(2f));
        getInstrumentation().waitForIdleSync();

        // measure text with double letter spacing
        final float doubleSpacing = textView.getLayout().getLineWidth(0);

        assertEquals("Double spacing should have two times the spacing of single spacing",
                doubleSpacing - zeroSpacing, 2f * (singleSpacing - zeroSpacing), 1f);
    }

    @MediumTest
    public void testGetFontFeatureSettings_returnsValueThatWasSet() {
        mTextView = new TextView(mActivity);
        mTextView.setFontFeatureSettings("\"smcp\" on");
        assertEquals("getFontFeatureSettings should return the value that was set",
                "\"smcp\" on", mTextView.getFontFeatureSettings());
    }

    @MediumTest
    public void testGetOffsetForPosition_singleLineLtr() {
        // asserts getOffsetPosition returns correct values for a single line LTR text
        String text = "aaaaa";
        final TextView textView = new TextView(mActivity);
        textView.setText(text);
        textView.setTextSize(8f);
        textView.setSingleLine(true);

        // add a compound drawable to TextView to make offset calculation more interesting
        final Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, 10, 10);
        textView.setCompoundDrawables(drawable, drawable, drawable, drawable);

        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(textView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mInstrumentation.runOnMainSync(() -> getActivity().setContentView(layout));
        mInstrumentation.waitForIdleSync();

        final int firstOffset = 0;
        final int lastOffset = text.length() - 1;
        final int midOffset = text.length() / 2;

        // left edge of view
        float x = 0f;
        float y = textView.getHeight() / 2f;
        assertEquals(firstOffset, textView.getOffsetForPosition(x, y));

        // right edge of text
        x = textView.getLayout().getLineWidth(0) - 1f;
        assertEquals(lastOffset, textView.getOffsetForPosition(x, y));

        // right edge of view
        x = textView.getWidth();
        assertEquals(lastOffset + 1, textView.getOffsetForPosition(x, y));

        // left edge of view - out of bounds
        x = -1f;
        assertEquals(firstOffset, textView.getOffsetForPosition(x, y));

        // horizontal center of text
        x = (float) Math.floor(textView.getLayout().getLineWidth(0) / 2f + 0.5f);
        assertEquals(midOffset, textView.getOffsetForPosition(x, y));
    }

    @MediumTest
    public void testGetOffsetForPosition_multiLineLtr() {
        final String line = "aaa\n";
        final String threeLines = line + line + line;
        final TextView textView = new TextView(mActivity);
        textView.setText(threeLines);
        textView.setTextSize(8f);
        textView.setLines(2);

        // add a compound drawable to TextView to make offset calculation more interesting
        final Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, 10, 10);
        textView.setCompoundDrawables(drawable, drawable, drawable, drawable);

        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(textView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mInstrumentation.runOnMainSync(() -> getActivity().setContentView(layout));
        mInstrumentation.waitForIdleSync();

        final Rect lineBounds = new Rect();
        textView.getLayout().getLineBounds(0, lineBounds);

        // left edge of view at first line
        float x = 0f;
        float y = lineBounds.height() / 2f;
        assertEquals(0, textView.getOffsetForPosition(x, y));

        // right edge of view at first line
        x = textView.getWidth() - 1f;
        assertEquals(line.length() - 1, textView.getOffsetForPosition(x, y));

        // update lineBounds to be the second line
        textView.getLayout().getLineBounds(1, lineBounds);
        y = lineBounds.top + lineBounds.height() / 2;

        // left edge of view at second line
        x = 0f;
        assertEquals(line.length(), textView.getOffsetForPosition(x, y));

        // right edge of text at second line
        x = textView.getLayout().getLineWidth(1) - 1f;
        assertEquals(line.length() + line.length() - 1, textView.getOffsetForPosition(x, y));

        // right edge of view at second line
        x = textView.getWidth() - 1f;
        assertEquals(line.length() + line.length() - 1, textView.getOffsetForPosition(x, y));

        // horizontal center of text at second line
        x = (float) Math.floor(textView.getLayout().getLineWidth(1) / 2f + 0.5f);
        // second line mid offset should not include next line, therefore subtract one
        assertEquals(line.length() + (line.length() - 1) / 2, textView.getOffsetForPosition(x, y));
    }

    @MediumTest
    public void testGetOffsetForPosition_multiLineRtl() {
        final String line = "\u0635\u0635\u0635\n";
        final String threeLines = line + line + line;
        final TextView textView = new TextView(mActivity);
        textView.setText(threeLines);
        textView.setTextSize(8f);
        textView.setLines(2);

        // add a compound drawable to TextView to make offset calculation more interesting
        final Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, 10, 10);
        textView.setCompoundDrawables(drawable, drawable, drawable, drawable);

        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(textView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mInstrumentation.runOnMainSync(() -> getActivity().setContentView(layout));
        mInstrumentation.waitForIdleSync();

        final Rect lineBounds = new Rect();
        textView.getLayout().getLineBounds(0, lineBounds);

        // right edge of view at first line
        float x = textView.getWidth() - 1f;
        float y = lineBounds.height() / 2f;
        assertEquals(0, textView.getOffsetForPosition(x, y));

        // left edge of view at first line
        x = 0f;
        assertEquals(line.length() - 1, textView.getOffsetForPosition(x, y));

        // update lineBounds to be the second line
        textView.getLayout().getLineBounds(1, lineBounds);
        y = lineBounds.top + lineBounds.height() / 2f;

        // right edge of view at second line
        x = textView.getWidth() - 1f;
        assertEquals(line.length(), textView.getOffsetForPosition(x, y));

        // left edge of view at second line
        x = 0f;
        assertEquals(line.length() + line.length() - 1, textView.getOffsetForPosition(x, y));

        // right edge of text at second line
        x = textView.getWidth() - textView.getLayout().getLineWidth(1) + 1f;
        assertEquals(line.length() + line.length() - 1, textView.getOffsetForPosition(x, y));

        // horizontal center of text at second line
        x = textView.getWidth() - (float) Math.floor(
                textView.getLayout().getLineWidth(1) / 2f + 0.5f);
        // second line mid offset should not include next line, therefore subtract one
        assertEquals(line.length() + (line.length() - 1) / 2, textView.getOffsetForPosition(x, y));
    }

    @MediumTest
    public void testIsTextSelectable_returnsFalseByDefault() {
        final TextView textView = new TextView(getActivity());
        textView.setText("any text");
        assertFalse(textView.isTextSelectable());
    }

    @MediumTest
    public void testIsTextSelectable_returnsTrueIfSetTextIsSelectableCalledWithTrue() {
        final TextView textView = new TextView(getActivity());
        textView.setText("any text");
        textView.setTextIsSelectable(true);
        assertTrue(textView.isTextSelectable());
    }

    @MediumTest
    public void testSetIsTextSelectable() {
        final TextView textView = new TextView(getActivity());

        assertFalse(textView.isTextSelectable());
        assertFalse(textView.isFocusable());
        assertFalse(textView.isFocusableInTouchMode());
        assertFalse(textView.isClickable());
        assertFalse(textView.isLongClickable());

        textView.setTextIsSelectable(true);

        assertTrue(textView.isTextSelectable());
        assertTrue(textView.isFocusable());
        assertTrue(textView.isFocusableInTouchMode());
        assertTrue(textView.isClickable());
        assertTrue(textView.isLongClickable());
        assertNotNull(textView.getMovementMethod());
    }

    public void testAccessTransformationMethod() {
        // check the password attribute in xml
        mTextView = findTextView(R.id.textview_password);
        assertNotNull(mTextView);
        assertSame(PasswordTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        // check the singleLine attribute in xml
        mTextView = findTextView(R.id.textview_singleLine);
        assertNotNull(mTextView);
        assertSame(SingleLineTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        final QwertyKeyListener qwertyKeyListener = QwertyKeyListener.getInstance(false,
                Capitalize.NONE);
        final TransformationMethod method = PasswordTransformationMethod.getInstance();
        // change transformation method by function
        mActivity.runOnUiThread(() -> {
            mTextView.setKeyListener(qwertyKeyListener);
            mTextView.setTransformationMethod(method);
            mTransformedText = method.getTransformation(mTextView.getText(), mTextView);

            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertSame(PasswordTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        mKeyEventUtil.sendKeys(mTextView, "H E 2*L O");
        mActivity.runOnUiThread(() -> mTextView.append(" "));
        mInstrumentation.waitForIdleSync();

        // It will get transformed after a while
        // We're waiting for transformation to "******"
        PollingCheck.waitFor(TIMEOUT, () -> mTransformedText.toString()
                .equals("\u2022\u2022\u2022\u2022\u2022\u2022"));

        // set null
        mActivity.runOnUiThread(() -> mTextView.setTransformationMethod(null));
        mInstrumentation.waitForIdleSync();
        assertNull(mTextView.getTransformationMethod());
    }

    @UiThreadTest
    public void testCompound() {
        mTextView = new TextView(mActivity);
        int padding = 3;
        Drawable[] drawables = mTextView.getCompoundDrawables();
        assertNull(drawables[0]);
        assertNull(drawables[1]);
        assertNull(drawables[2]);
        assertNull(drawables[3]);

        // test setCompoundDrawablePadding and getCompoundDrawablePadding
        mTextView.setCompoundDrawablePadding(padding);
        assertEquals(padding, mTextView.getCompoundDrawablePadding());

        // using resid, 0 represents null
        mTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.start, R.drawable.pass,
                R.drawable.failed, 0);
        drawables = mTextView.getCompoundDrawables();

        // drawableLeft
        WidgetTestUtils.assertEquals(TestUtils.getBitmap(mActivity, R.drawable.start),
                ((BitmapDrawable) drawables[0]).getBitmap());
        // drawableTop
        WidgetTestUtils.assertEquals(TestUtils.getBitmap(mActivity, R.drawable.pass),
                ((BitmapDrawable) drawables[1]).getBitmap());
        // drawableRight
        WidgetTestUtils.assertEquals(TestUtils.getBitmap(mActivity, R.drawable.failed),
                ((BitmapDrawable) drawables[2]).getBitmap());
        // drawableBottom
        assertNull(drawables[3]);

        Drawable left = TestUtils.getDrawable(mActivity, R.drawable.blue);
        Drawable right = TestUtils.getDrawable(mActivity, R.drawable.yellow);
        Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);

        // using drawables directly
        mTextView.setCompoundDrawablesWithIntrinsicBounds(left, top, right, null);
        drawables = mTextView.getCompoundDrawables();

        // drawableLeft
        assertSame(left, drawables[0]);
        // drawableTop
        assertSame(top, drawables[1]);
        // drawableRight
        assertSame(right, drawables[2]);
        // drawableBottom
        assertNull(drawables[3]);

        // check compound padding
        assertEquals(mTextView.getPaddingLeft() + padding + left.getIntrinsicWidth(),
                mTextView.getCompoundPaddingLeft());
        assertEquals(mTextView.getPaddingTop() + padding + top.getIntrinsicHeight(),
                mTextView.getCompoundPaddingTop());
        assertEquals(mTextView.getPaddingRight() + padding + right.getIntrinsicWidth(),
                mTextView.getCompoundPaddingRight());
        assertEquals(mTextView.getPaddingBottom(), mTextView.getCompoundPaddingBottom());

        // set bounds to drawables and set them again.
        left.setBounds(0, 0, 1, 2);
        right.setBounds(0, 0, 3, 4);
        top.setBounds(0, 0, 5, 6);
        // usinf drawables
        mTextView.setCompoundDrawables(left, top, right, null);
        drawables = mTextView.getCompoundDrawables();

        // drawableLeft
        assertSame(left, drawables[0]);
        // drawableTop
        assertSame(top, drawables[1]);
        // drawableRight
        assertSame(right, drawables[2]);
        // drawableBottom
        assertNull(drawables[3]);

        // check compound padding
        assertEquals(mTextView.getPaddingLeft() + padding + left.getBounds().width(),
                mTextView.getCompoundPaddingLeft());
        assertEquals(mTextView.getPaddingTop() + padding + top.getBounds().height(),
                mTextView.getCompoundPaddingTop());
        assertEquals(mTextView.getPaddingRight() + padding + right.getBounds().width(),
                mTextView.getCompoundPaddingRight());
        assertEquals(mTextView.getPaddingBottom(), mTextView.getCompoundPaddingBottom());
    }

    @MediumTest
    @UiThreadTest
    public void testGetCompoundDrawablesRelative() {
        // prepare textview
        mTextView = new TextView(mActivity);

        // prepare drawables
        final Drawable start = TestUtils.getDrawable(mActivity, R.drawable.blue);
        final Drawable end = TestUtils.getDrawable(mActivity, R.drawable.yellow);
        final Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);
        final Drawable bottom = TestUtils.getDrawable(mActivity, R.drawable.black);
        assertNotNull(start);
        assertNotNull(end);
        assertNotNull(top);
        assertNotNull(bottom);

        Drawable[] drawables = mTextView.getCompoundDrawablesRelative();
        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertNull(drawables[0]);
        assertNull(drawables[1]);
        assertNull(drawables[2]);
        assertNull(drawables[3]);

        mTextView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mTextView.setCompoundDrawablesRelative(start, top, end, bottom);
        drawables = mTextView.getCompoundDrawablesRelative();

        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertSame(start, drawables[0]);
        assertSame(top, drawables[1]);
        assertSame(end, drawables[2]);
        assertSame(bottom, drawables[3]);

        mTextView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        mTextView.setCompoundDrawablesRelative(start, top, end, bottom);
        drawables = mTextView.getCompoundDrawablesRelative();

        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertSame(start, drawables[0]);
        assertSame(top, drawables[1]);
        assertSame(end, drawables[2]);
        assertSame(bottom, drawables[3]);

        mTextView.setCompoundDrawablesRelative(null, null, null, null);
        drawables = mTextView.getCompoundDrawablesRelative();

        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertNull(drawables[0]);
        assertNull(drawables[1]);
        assertNull(drawables[2]);
        assertNull(drawables[3]);
    }

    @MediumTest
    public void testSingleLine() {
        final TextView textView = new TextView(mActivity);
        setSpannableText(textView, "This is a really long sentence"
                + " which can not be placed in one line on the screen.");

        // Narrow layout assures that the text will get wrapped.
        final FrameLayout innerLayout = new FrameLayout(mActivity);
        innerLayout.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        innerLayout.addView(textView);

        final FrameLayout layout = new FrameLayout(mActivity);
        layout.addView(innerLayout);

        mActivity.runOnUiThread(() -> {
            mActivity.setContentView(layout);
            textView.setSingleLine(true);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(SingleLineTransformationMethod.getInstance(),
                textView.getTransformationMethod());

        int singleLineWidth = 0;
        int singleLineHeight = 0;

        if (textView.getLayout() != null) {
            singleLineWidth = textView.getLayout().getWidth();
            singleLineHeight = textView.getLayout().getHeight();
        }

        mActivity.runOnUiThread(() -> textView.setSingleLine(false));
        mInstrumentation.waitForIdleSync();
        assertEquals(null, textView.getTransformationMethod());

        if (textView.getLayout() != null) {
            assertTrue(textView.getLayout().getHeight() > singleLineHeight);
            assertTrue(textView.getLayout().getWidth() < singleLineWidth);
        }

        // same behaviours as setSingLine(true)
        mActivity.runOnUiThread(textView::setSingleLine);
        mInstrumentation.waitForIdleSync();
        assertEquals(SingleLineTransformationMethod.getInstance(),
                textView.getTransformationMethod());

        if (textView.getLayout() != null) {
            assertEquals(singleLineHeight, textView.getLayout().getHeight());
            assertEquals(singleLineWidth, textView.getLayout().getWidth());
        }
    }

    @UiThreadTest
    public void testAccessMaxLines() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setWidth((int) (mTextView.getPaint().measureText(LONG_TEXT) / 4));
        mTextView.setText(LONG_TEXT);

        final int maxLines = 2;
        assertTrue(mTextView.getLineCount() > maxLines);

        mTextView.setMaxLines(maxLines);
        mTextView.requestLayout();

        assertEquals(2, mTextView.getMaxLines());
        assertEquals(-1, mTextView.getMaxHeight());
        assertTrue(mTextView.getHeight() <= maxLines * mTextView.getLineHeight());
    }

    @UiThreadTest
    public void testHyphenationNotHappen_frequencyNone() {
        final int[] BREAK_STRATEGIES = {
            Layout.BREAK_STRATEGY_SIMPLE, Layout.BREAK_STRATEGY_HIGH_QUALITY,
            Layout.BREAK_STRATEGY_BALANCED };

        mTextView = findTextView(R.id.textview_text);

        for (int breakStrategy : BREAK_STRATEGIES) {
            for (int charWidth = 10; charWidth < 120; charWidth += 5) {
                // Change the text view's width to charWidth width.
                final String substring = LONG_TEXT.substring(0, charWidth);
                mTextView.setWidth((int) Math.ceil(mTextView.getPaint().measureText(substring)));

                mTextView.setText(LONG_TEXT);
                mTextView.setBreakStrategy(breakStrategy);

                mTextView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);

                mTextView.requestLayout();
                mTextView.onPreDraw();  // For freezing the layout.
                Layout layout = mTextView.getLayout();

                final int lineCount = layout.getLineCount();
                for (int line = 0; line < lineCount; ++line) {
                    final int lineEnd = layout.getLineEnd(line);
                    // In any width, any break strategy, hyphenation should not happen if
                    // HYPHENATION_FREQUENCY_NONE is specified.
                    assertTrue(lineEnd == LONG_TEXT.length() ||
                            Character.isWhitespace(LONG_TEXT.charAt(lineEnd - 1)));
                }
            }
        }
    }

    @UiThreadTest
    public void testHyphenationNotHappen_breakStrategySimple() {
        final int[] HYPHENATION_FREQUENCIES = {
            Layout.HYPHENATION_FREQUENCY_NORMAL, Layout.HYPHENATION_FREQUENCY_FULL,
            Layout.HYPHENATION_FREQUENCY_NONE };

        mTextView = findTextView(R.id.textview_text);

        for (int hyphenationFrequency: HYPHENATION_FREQUENCIES) {
            for (int charWidth = 10; charWidth < 120; charWidth += 5) {
                // Change the text view's width to charWidth width.
                final String substring = LONG_TEXT.substring(0, charWidth);
                mTextView.setWidth((int) Math.ceil(mTextView.getPaint().measureText(substring)));

                mTextView.setText(LONG_TEXT);
                mTextView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);

                mTextView.setHyphenationFrequency(hyphenationFrequency);

                mTextView.requestLayout();
                mTextView.onPreDraw();  // For freezing the layout.
                Layout layout = mTextView.getLayout();

                final int lineCount = layout.getLineCount();
                for (int line = 0; line < lineCount; ++line) {
                    final int lineEnd = layout.getLineEnd(line);
                    // In any width, any hyphenation frequency, hyphenation should not happen if
                    // BREAK_STRATEGY_SIMPLE is specified.
                    assertTrue(lineEnd == LONG_TEXT.length() ||
                            Character.isWhitespace(LONG_TEXT.charAt(lineEnd - 1)));
                }
            }
        }
    }

    @UiThreadTest
    public void testSetMaxLinesException() {
        mTextView = new TextView(mActivity);
        mActivity.setContentView(mTextView);
        mTextView.setWidth(mTextView.getWidth() >> 3);
        mTextView.setMaxLines(-1);
    }

    public void testAccessMinLines() {
        mTextView = findTextView(R.id.textview_text);
        setWidth(mTextView.getWidth() >> 3);
        int originalLines = mTextView.getLineCount();

        setMinLines(originalLines - 1);
        assertTrue((originalLines - 1) * mTextView.getLineHeight() <= mTextView.getHeight());
        assertEquals(originalLines - 1, mTextView.getMinLines());
        assertEquals(-1, mTextView.getMinHeight());

        setMinLines(originalLines + 1);
        assertTrue((originalLines + 1) * mTextView.getLineHeight() <= mTextView.getHeight());
        assertEquals(originalLines + 1, mTextView.getMinLines());
        assertEquals(-1, mTextView.getMinHeight());
    }

    public void testSetLines() {
        mTextView = findTextView(R.id.textview_text);
        // make it multiple lines
        setWidth(mTextView.getWidth() >> 3);
        int originalLines = mTextView.getLineCount();

        setLines(originalLines - 1);
        assertTrue((originalLines - 1) * mTextView.getLineHeight() <= mTextView.getHeight());

        setLines(originalLines + 1);
        assertTrue((originalLines + 1) * mTextView.getLineHeight() <= mTextView.getHeight());
    }

    @UiThreadTest
    public void testSetLinesException() {
        mTextView = new TextView(mActivity);
        mActivity.setContentView(mTextView);
        mTextView.setWidth(mTextView.getWidth() >> 3);
        mTextView.setLines(-1);
    }

    @UiThreadTest
    public void testGetExtendedPaddingTop() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getExtendedPaddingTop());

        // After Set a Drawable
        final Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);
        top.setBounds(0, 0, 100, 10);
        mTextView.setCompoundDrawables(null, top, null, null);
        assertEquals(mTextView.getCompoundPaddingTop(), mTextView.getExtendedPaddingTop());

        // Change line count
        mTextView.setLines(mTextView.getLineCount() - 1);
        mTextView.setGravity(Gravity.BOTTOM);

        assertTrue(mTextView.getExtendedPaddingTop() > 0);
    }

    @UiThreadTest
    public void testGetExtendedPaddingBottom() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getExtendedPaddingBottom());

        // After Set a Drawable
        final Drawable bottom = TestUtils.getDrawable(mActivity, R.drawable.red);
        bottom.setBounds(0, 0, 100, 10);
        mTextView.setCompoundDrawables(null, null, null, bottom);
        assertEquals(mTextView.getCompoundPaddingBottom(), mTextView.getExtendedPaddingBottom());

        // Change line count
        mTextView.setLines(mTextView.getLineCount() - 1);
        mTextView.setGravity(Gravity.CENTER_VERTICAL);

        assertTrue(mTextView.getExtendedPaddingBottom() > 0);
    }

    public void testGetTotalPaddingTop() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingTop());

        // After Set a Drawable
        final Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);
        top.setBounds(0, 0, 100, 10);
        mActivity.runOnUiThread(() -> {
            mTextView.setCompoundDrawables(null, top, null, null);
            mTextView.setLines(mTextView.getLineCount() - 1);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getExtendedPaddingTop(), mTextView.getTotalPaddingTop());

        // Change line count
        setLines(mTextView.getLineCount() + 1);
        int expected = mTextView.getHeight()
                - mTextView.getExtendedPaddingBottom()
                - mTextView.getLayout().getLineTop(mTextView.getLineCount());
        assertEquals(expected, mTextView.getTotalPaddingTop());
    }

    public void testGetTotalPaddingBottom() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingBottom());

        // After Set a Drawable
        final Drawable bottom = TestUtils.getDrawable(mActivity, R.drawable.red);
        bottom.setBounds(0, 0, 100, 10);
        mActivity.runOnUiThread(() -> {
            mTextView.setCompoundDrawables(null, null, null, bottom);
            mTextView.setLines(mTextView.getLineCount() - 1);
            mTextView.setGravity(Gravity.CENTER_VERTICAL);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getExtendedPaddingBottom(), mTextView.getTotalPaddingBottom());

        // Change line count
        setLines(mTextView.getLineCount() + 1);
        int expected = ((mTextView.getHeight()
                - mTextView.getExtendedPaddingBottom()
                - mTextView.getExtendedPaddingTop()
                - mTextView.getLayout().getLineBottom(mTextView.getLineCount())) >> 1)
                + mTextView.getExtendedPaddingBottom();
        assertEquals(expected, mTextView.getTotalPaddingBottom());
    }

    @UiThreadTest
    public void testGetTotalPaddingLeft() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingLeft());

        // After Set a Drawable
        Drawable left = TestUtils.getDrawable(mActivity, R.drawable.red);
        left.setBounds(0, 0, 10, 100);
        mTextView.setCompoundDrawables(left, null, null, null);
        mTextView.setGravity(Gravity.RIGHT);
        assertEquals(mTextView.getCompoundPaddingLeft(), mTextView.getTotalPaddingLeft());

        // Change width
        mTextView.setWidth(Integer.MAX_VALUE);
        assertEquals(mTextView.getCompoundPaddingLeft(), mTextView.getTotalPaddingLeft());
    }

    @UiThreadTest
    public void testGetTotalPaddingRight() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingRight());

        // After Set a Drawable
        Drawable right = TestUtils.getDrawable(mActivity, R.drawable.red);
        right.setBounds(0, 0, 10, 100);
        mTextView.setCompoundDrawables(null, null, right, null);
        mTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        assertEquals(mTextView.getCompoundPaddingRight(), mTextView.getTotalPaddingRight());

        // Change width
        mTextView.setWidth(Integer.MAX_VALUE);
        assertEquals(mTextView.getCompoundPaddingRight(), mTextView.getTotalPaddingRight());
    }

    public void testGetUrls() {
        mTextView = new TextView(mActivity);

        URLSpan[] spans = mTextView.getUrls();
        assertEquals(0, spans.length);

        String url = "http://www.google.com";
        String email = "name@gmail.com";
        String string = url + " mailto:" + email;
        SpannableString spannable = new SpannableString(string);
        spannable.setSpan(new URLSpan(url), 0, url.length(), 0);
        mTextView.setText(spannable, BufferType.SPANNABLE);
        spans = mTextView.getUrls();
        assertEquals(1, spans.length);
        assertEquals(url, spans[0].getURL());

        spannable.setSpan(new URLSpan(email), 0, email.length(), 0);
        mTextView.setText(spannable, BufferType.SPANNABLE);

        spans = mTextView.getUrls();
        assertEquals(2, spans.length);
        assertEquals(url, spans[0].getURL());
        assertEquals(email, spans[1].getURL());

        // test the situation that param what is not a URLSpan
        spannable.setSpan(new Object(), 0, 9, 0);
        mTextView.setText(spannable, BufferType.SPANNABLE);
        spans = mTextView.getUrls();
        assertEquals(2, spans.length);
    }

    public void testSetPadding() {
        mTextView = new TextView(mActivity);

        mTextView.setPadding(0, 1, 2, 4);
        assertEquals(0, mTextView.getPaddingLeft());
        assertEquals(1, mTextView.getPaddingTop());
        assertEquals(2, mTextView.getPaddingRight());
        assertEquals(4, mTextView.getPaddingBottom());

        mTextView.setPadding(10, 20, 30, 40);
        assertEquals(10, mTextView.getPaddingLeft());
        assertEquals(20, mTextView.getPaddingTop());
        assertEquals(30, mTextView.getPaddingRight());
        assertEquals(40, mTextView.getPaddingBottom());
    }

    public void testDeprecatedSetTextAppearance() {
        mTextView = new TextView(mActivity);

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_All);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(20f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.BOLD, mTextView.getTypeface().getStyle());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getLinkTextColors().getDefaultColor());

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_Colors);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.yellow),
                mTextView.getLinkTextColors().getDefaultColor());

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_NotColors);
        assertEquals(17f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.NORMAL, mTextView.getTypeface().getStyle());

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_Style);
        assertEquals(null, mTextView.getTypeface());
    }

    public void testSetTextAppearance() {
        mTextView = new TextView(mActivity);

        mTextView.setTextAppearance(R.style.TextAppearance_All);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(20f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.BOLD, mTextView.getTypeface().getStyle());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getLinkTextColors().getDefaultColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.yellow),
                mTextView.getHighlightColor());

        mTextView.setTextAppearance(R.style.TextAppearance_Colors);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.yellow),
                mTextView.getLinkTextColors().getDefaultColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getHighlightColor());

        mTextView.setTextAppearance(R.style.TextAppearance_NotColors);
        assertEquals(17f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.NORMAL, mTextView.getTypeface().getStyle());

        mTextView.setTextAppearance(R.style.TextAppearance_Style);
        assertEquals(null, mTextView.getTypeface());
    }

    public void testOnPreDraw() {
        // Do not test. Implementation details.
    }

    public void testAccessCompoundDrawableTint() {
        mTextView = new TextView(mActivity);

        ColorStateList colors = ColorStateList.valueOf(Color.RED);
        mTextView.setCompoundDrawableTintList(colors);
        mTextView.setCompoundDrawableTintMode(PorterDuff.Mode.XOR);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());

        // Ensure the tint is preserved across drawable changes.
        mTextView.setCompoundDrawablesRelative(null, null, null, null);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());

        mTextView.setCompoundDrawables(null, null, null, null);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());

        ColorDrawable dr1 = new ColorDrawable(Color.RED);
        ColorDrawable dr2 = new ColorDrawable(Color.GREEN);
        ColorDrawable dr3 = new ColorDrawable(Color.BLUE);
        ColorDrawable dr4 = new ColorDrawable(Color.YELLOW);
        mTextView.setCompoundDrawables(dr1, dr2, dr3, dr4);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());
    }

    public void testSetHorizontallyScrolling() {
        // make the text view has more than one line
        mTextView = findTextView(R.id.textview_text);
        setWidth(mTextView.getWidth() >> 1);
        assertTrue(mTextView.getLineCount() > 1);

        setHorizontallyScrolling(true);
        assertEquals(1, mTextView.getLineCount());

        setHorizontallyScrolling(false);
        assertTrue(mTextView.getLineCount() > 1);
    }

    public void testComputeHorizontalScrollRange() {
        MockTextView textView = new MockTextView(mActivity);
        // test when layout is null
        assertNull(textView.getLayout());
        assertEquals(textView.getWidth(), textView.computeHorizontalScrollRange());

        textView.setFrame(0, 0, 40, 50);
        assertEquals(textView.getWidth(), textView.computeHorizontalScrollRange());

        // set the layout
        layout(textView);
        assertEquals(textView.getLayout().getWidth(), textView.computeHorizontalScrollRange());
    }

    public void testComputeVerticalScrollRange() {
        MockTextView textView = new MockTextView(mActivity);
        // test when layout is null
        assertNull(textView.getLayout());
        assertEquals(0, textView.computeVerticalScrollRange());

        textView.setFrame(0, 0, 40, 50);
        assertEquals(textView.getHeight(), textView.computeVerticalScrollRange());

        //set the layout
        layout(textView);
        assertEquals(textView.getLayout().getHeight(), textView.computeVerticalScrollRange());
    }

    public void testDrawableStateChanged() {
        MockTextView textView = spy(new MockTextView(mActivity));
        reset(textView);
        textView.refreshDrawableState();
        verify(textView, times(1)).drawableStateChanged();
    }

    public void testGetDefaultEditable() {
        MockTextView textView = new MockTextView(mActivity);

        //the TextView#getDefaultEditable() does nothing, and always return false.
        assertFalse(textView.getDefaultEditable());
    }

    public void testGetDefaultMovementMethod() {
        MockTextView textView = new MockTextView(mActivity);

        //the TextView#getDefaultMovementMethod() does nothing, and always return null.
        assertNull(textView.getDefaultMovementMethod());
    }

    public void testOnCreateContextMenu() {
        // Do not test. Implementation details.
    }

    public void testOnDetachedFromWindow() {
        // Do not test. Implementation details.
    }

    public void testOnDraw() {
        // Do not test. Implementation details.
    }

    public void testOnFocusChanged() {
        // Do not test. Implementation details.
    }

    public void testOnMeasure() {
        // Do not test. Implementation details.
    }

    public void testOnTextChanged() {
        // Do not test. Implementation details.
    }

    public void testSetFrame() {
        MockTextView textView = new MockTextView(mActivity);

        //Assign a new size to this view
        assertTrue(textView.setFrame(0, 0, 320, 480));
        assertEquals(0, textView.getLeft());
        assertEquals(0, textView.getTop());
        assertEquals(320, textView.getRight());
        assertEquals(480, textView.getBottom());

        //Assign a same size to this view
        assertFalse(textView.setFrame(0, 0, 320, 480));

        //negative input
        assertTrue(textView.setFrame(-1, -1, -1, -1));
        assertEquals(-1, textView.getLeft());
        assertEquals(-1, textView.getTop());
        assertEquals(-1, textView.getRight());
        assertEquals(-1, textView.getBottom());
    }

    public void testMarquee() {
        // Both are pointing to the same object. This works around current limitation in CTS
        // coverage report tool for properly reporting coverage of base class method calls.
        final MockTextView mockTextView = new MockTextView(mActivity);
        final TextView textView = mockTextView;

        textView.setText(LONG_TEXT);
        textView.setSingleLine();
        textView.setEllipsize(TruncateAt.MARQUEE);
        textView.setLayoutParams(new ViewGroup.LayoutParams(100, 100));

        final FrameLayout layout = new FrameLayout(mActivity);
        layout.addView(textView);

        // make the fading to be shown
        textView.setHorizontalFadingEdgeEnabled(true);

        mActivity.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        TestSelectedRunnable runnable = new TestSelectedRunnable(textView) {
            public void run() {
                textView.setMarqueeRepeatLimit(-1);
                // force the marquee to start
                saveIsSelected1();
                textView.setSelected(true);
                saveIsSelected2();
            }
        };
        mActivity.runOnUiThread(runnable);

        // wait for the marquee to run
        // fading is shown on both sides if the marquee runs for a while
        PollingCheck.waitFor(TIMEOUT, () -> mockTextView.getLeftFadingEdgeStrength() > 0.0f
                && mockTextView.getRightFadingEdgeStrength() > 0.0f);

        // wait for left marquee to fully apply
        PollingCheck.waitFor(TIMEOUT, () -> mockTextView.getLeftFadingEdgeStrength() > 0.99f);

        assertFalse(runnable.getIsSelected1());
        assertTrue(runnable.getIsSelected2());
        assertEquals(-1, textView.getMarqueeRepeatLimit());

        runnable = new TestSelectedRunnable(textView) {
            public void run() {
                textView.setMarqueeRepeatLimit(0);
                // force the marquee to stop
                saveIsSelected1();
                textView.setSelected(false);
                saveIsSelected2();
                textView.setGravity(Gravity.LEFT);
            }
        };
        // force the marquee to stop
        mActivity.runOnUiThread(runnable);
        mInstrumentation.waitForIdleSync();
        assertTrue(runnable.getIsSelected1());
        assertFalse(runnable.getIsSelected2());
        assertEquals(0.0f, mockTextView.getLeftFadingEdgeStrength(), 0.01f);
        assertTrue(mockTextView.getRightFadingEdgeStrength() > 0.0f);
        assertEquals(0, textView.getMarqueeRepeatLimit());

        mActivity.runOnUiThread(() -> textView.setGravity(Gravity.RIGHT));
        mInstrumentation.waitForIdleSync();
        assertTrue(mockTextView.getLeftFadingEdgeStrength() > 0.0f);
        assertEquals(0.0f, mockTextView.getRightFadingEdgeStrength(), 0.01f);

        mActivity.runOnUiThread(() -> textView.setGravity(Gravity.CENTER_HORIZONTAL));
        mInstrumentation.waitForIdleSync();
        // there is no left fading (Is it correct?)
        assertEquals(0.0f, mockTextView.getLeftFadingEdgeStrength(), 0.01f);
        assertTrue(mockTextView.getRightFadingEdgeStrength() > 0.0f);
    }

    @MediumTest
    public void testGetMarqueeRepeatLimit() {
        final TextView textView = new TextView(mActivity);

        textView.setMarqueeRepeatLimit(10);
        assertEquals(10, textView.getMarqueeRepeatLimit());
    }

    public void testOnKeyMultiple() {
        // Do not test. Implementation details.
    }

    public void testAccessInputExtras() throws XmlPullParserException, IOException {
        TextView textView = new TextView(mActivity);
        textView.setText(null, BufferType.EDITABLE);
        textView.setInputType(InputType.TYPE_CLASS_TEXT);

        // do not create the extras
        assertNull(textView.getInputExtras(false));

        // create if it does not exist
        Bundle inputExtras = textView.getInputExtras(true);
        assertNotNull(inputExtras);
        assertTrue(inputExtras.isEmpty());

        // it is created already
        assertNotNull(textView.getInputExtras(false));

        try {
            textView.setInputExtras(R.xml.input_extras);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }
    }

    public void testAccessContentType() {
        TextView textView = new TextView(mActivity);
        textView.setText(null, BufferType.EDITABLE);
        textView.setKeyListener(null);
        textView.setTransformationMethod(null);

        textView.setInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL, textView.getInputType());
        assertTrue(textView.getKeyListener() instanceof DateTimeKeyListener);

        textView.setInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE, textView.getInputType());
        assertTrue(textView.getKeyListener() instanceof DateKeyListener);

        textView.setInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME, textView.getInputType());
        assertTrue(textView.getKeyListener() instanceof TimeKeyListener);

        textView.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        assertEquals(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, textView.getInputType());
        assertSame(textView.getKeyListener(), DigitsKeyListener.getInstance(true, true));

        textView.setInputType(InputType.TYPE_CLASS_PHONE);
        assertEquals(InputType.TYPE_CLASS_PHONE, textView.getInputType());
        assertTrue(textView.getKeyListener() instanceof DialerKeyListener);

        textView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, textView.getInputType());
        assertSame(textView.getKeyListener(), TextKeyListener.getInstance(true, Capitalize.NONE));

        textView.setSingleLine();
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        textView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, textView.getInputType());
        assertSame(textView.getKeyListener(),
                TextKeyListener.getInstance(false, Capitalize.CHARACTERS));
        assertNull(textView.getTransformationMethod());

        textView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, textView.getInputType());
        assertSame(textView.getKeyListener(),
                TextKeyListener.getInstance(false, Capitalize.WORDS));
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);

        textView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, textView.getInputType());
        assertSame(textView.getKeyListener(),
                TextKeyListener.getInstance(false, Capitalize.SENTENCES));

        textView.setInputType(InputType.TYPE_NULL);
        assertEquals(InputType.TYPE_NULL, textView.getInputType());
        assertTrue(textView.getKeyListener() instanceof TextKeyListener);
    }

    public void testAccessRawContentType() {
        TextView textView = new TextView(mActivity);
        textView.setText(null, BufferType.EDITABLE);
        textView.setKeyListener(null);
        textView.setTransformationMethod(null);

        textView.setRawInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL, textView.getInputType());
        assertNull(textView.getTransformationMethod());
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE, textView.getInputType());
        assertNull(textView.getTransformationMethod());
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME, textView.getInputType());
        assertNull(textView.getTransformationMethod());
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        assertEquals(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, textView.getInputType());
        assertNull(textView.getTransformationMethod());
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_CLASS_PHONE);
        assertEquals(InputType.TYPE_CLASS_PHONE, textView.getInputType());
        assertNull(textView.getTransformationMethod());
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, textView.getInputType());
        assertNull(textView.getTransformationMethod());
        assertNull(textView.getKeyListener());

        textView.setSingleLine();
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        textView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, textView.getInputType());
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, textView.getInputType());
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, textView.getInputType());
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(textView.getKeyListener());

        textView.setRawInputType(InputType.TYPE_NULL);
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(textView.getKeyListener());
    }

    public void testOnPrivateIMECommand() {
        // Do not test. Implementation details.
    }

    public void testFoo() {
        // Do not test. Implementation details.
    }

    public void testVerifyDrawable() {
        MockTextView textView = new MockTextView(mActivity);

        Drawable d = TestUtils.getDrawable(mActivity, R.drawable.pass);
        assertFalse(textView.verifyDrawable(d));

        textView.setCompoundDrawables(null, d, null, null);
        assertTrue(textView.verifyDrawable(d));
    }

    public void testAccessPrivateImeOptions() {
        mTextView = findTextView(R.id.textview_text);
        assertNull(mTextView.getPrivateImeOptions());

        mTextView.setPrivateImeOptions("com.example.myapp.SpecialMode=3");
        assertEquals("com.example.myapp.SpecialMode=3", mTextView.getPrivateImeOptions());

        mTextView.setPrivateImeOptions(null);
        assertNull(mTextView.getPrivateImeOptions());
    }

    public void testSetOnEditorActionListener() {
        mTextView = findTextView(R.id.textview_text);

        final TextView.OnEditorActionListener mockOnEditorActionListener =
                mock(TextView.OnEditorActionListener.class);
        verifyZeroInteractions(mockOnEditorActionListener);

        mTextView.setOnEditorActionListener(mockOnEditorActionListener);
        verifyZeroInteractions(mockOnEditorActionListener);

        mTextView.onEditorAction(EditorInfo.IME_ACTION_DONE);
        verify(mockOnEditorActionListener, times(1)).onEditorAction(mTextView,
                EditorInfo.IME_ACTION_DONE, null);
    }

    public void testAccessImeOptions() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(EditorInfo.IME_NULL, mTextView.getImeOptions());

        mTextView.setImeOptions(EditorInfo.IME_ACTION_GO);
        assertEquals(EditorInfo.IME_ACTION_GO, mTextView.getImeOptions());

        mTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        assertEquals(EditorInfo.IME_ACTION_DONE, mTextView.getImeOptions());

        mTextView.setImeOptions(EditorInfo.IME_NULL);
        assertEquals(EditorInfo.IME_NULL, mTextView.getImeOptions());
    }

    public void testAccessImeActionLabel() {
        mTextView = findTextView(R.id.textview_text);
        assertNull(mTextView.getImeActionLabel());
        assertEquals(0, mTextView.getImeActionId());

        mTextView.setImeActionLabel("pinyin", 1);
        assertEquals("pinyin", mTextView.getImeActionLabel().toString());
        assertEquals(1, mTextView.getImeActionId());
    }

    public void testAccessImeHintLocales() {
        final TextView textView = new TextView(mActivity);
        textView.setText("", BufferType.EDITABLE);
        textView.setKeyListener(null);
        textView.setRawInputType(InputType.TYPE_CLASS_TEXT);
        assertNull(textView.getImeHintLocales());
        {
            final EditorInfo editorInfo = new EditorInfo();
            textView.onCreateInputConnection(editorInfo);
            assertNull(editorInfo.hintLocales);
        }

        final LocaleList localeList = LocaleList.forLanguageTags("en-PH,en-US");
        textView.setImeHintLocales(localeList);
        assertEquals(localeList, textView.getImeHintLocales());
        {
            final EditorInfo editorInfo = new EditorInfo();
            textView.onCreateInputConnection(editorInfo);
            assertEquals(localeList, editorInfo.hintLocales);
        }
    }

    @UiThreadTest
    public void testSetExtractedText() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(mActivity.getResources().getString(R.string.text_view_hello),
                mTextView.getText().toString());

        ExtractedText et = new ExtractedText();

        // Update text and selection.
        et.text = "test";
        et.selectionStart = 0;
        et.selectionEnd = 2;

        mTextView.setExtractedText(et);
        assertEquals("test", mTextView.getText().toString());
        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(2, mTextView.getSelectionEnd());

        // Use partialStartOffset and partialEndOffset
        et.partialStartOffset = 2;
        et.partialEndOffset = 3;
        et.text = "x";
        et.selectionStart = 2;
        et.selectionEnd = 3;

        mTextView.setExtractedText(et);
        assertEquals("text", mTextView.getText().toString());
        assertEquals(2, mTextView.getSelectionStart());
        assertEquals(3, mTextView.getSelectionEnd());

        // Update text with spans.
        final SpannableString ss = new SpannableString("ex");
        ss.setSpan(new UnderlineSpan(), 0, 2, 0);
        ss.setSpan(new URLSpan("ctstest://TextView/test"), 1, 2, 0);

        et.text = ss;
        et.partialStartOffset = 1;
        et.partialEndOffset = 3;
        mTextView.setExtractedText(et);

        assertEquals("text", mTextView.getText().toString());
        final Editable editable = mTextView.getEditableText();
        final UnderlineSpan[] underlineSpans = mTextView.getEditableText().getSpans(
                0, editable.length(), UnderlineSpan.class);
        assertEquals(1, underlineSpans.length);
        assertEquals(1, editable.getSpanStart(underlineSpans[0]));
        assertEquals(3, editable.getSpanEnd(underlineSpans[0]));

        final URLSpan[] urlSpans = mTextView.getEditableText().getSpans(
                0, editable.length(), URLSpan.class);
        assertEquals(1, urlSpans.length);
        assertEquals(2, editable.getSpanStart(urlSpans[0]));
        assertEquals(3, editable.getSpanEnd(urlSpans[0]));
        assertEquals("ctstest://TextView/test", urlSpans[0].getURL());
    }

    public void testMoveCursorToVisibleOffset() throws Throwable {
        mTextView = findTextView(R.id.textview_text);

        // not a spannable text
        runTestOnUiThread(() -> assertFalse(mTextView.moveCursorToVisibleOffset()));
        mInstrumentation.waitForIdleSync();

        // a selection range
        final String spannableText = "text";
        mTextView = new TextView(mActivity);

        runTestOnUiThread(() -> mTextView.setText(spannableText, BufferType.SPANNABLE));
        mInstrumentation.waitForIdleSync();
        Selection.setSelection((Spannable) mTextView.getText(), 0, spannableText.length());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(spannableText.length(), mTextView.getSelectionEnd());
        runTestOnUiThread(() -> assertFalse(mTextView.moveCursorToVisibleOffset()));
        mInstrumentation.waitForIdleSync();

        // a spannable without range
        runTestOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setText(spannableText, BufferType.SPANNABLE);
        });
        mInstrumentation.waitForIdleSync();

        runTestOnUiThread(() -> assertTrue(mTextView.moveCursorToVisibleOffset()));
        mInstrumentation.waitForIdleSync();
    }

    public void testIsInputMethodTarget() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertFalse(mTextView.isInputMethodTarget());

        assertFalse(mTextView.isFocused());
        runTestOnUiThread(() -> {
            mTextView.setFocusable(true);
            mTextView.requestFocus();
         });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocused());

        PollingCheck.waitFor(mTextView::isInputMethodTarget);
    }

    @MediumTest
    public void testBeginEndBatchEditAreNotCalledForNonEditableText() {
        final TextView mockTextView = spy(new TextView(mActivity));

        // TextView should not call onBeginBatchEdit or onEndBatchEdit during initialization
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();


        mockTextView.beginBatchEdit();
        // Since TextView doesn't support editing, the callbacks should not be called
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();

        mockTextView.endBatchEdit();
        // Since TextView doesn't support editing, the callbacks should not be called
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();
    }

    @MediumTest
    public void testBeginEndBatchEditCallbacksAreCalledForEditableText() {
        final TextView mockTextView = spy(new TextView(mActivity));

        final FrameLayout layout = new FrameLayout(getActivity());
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mockTextView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivity.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        mActivity.runOnUiThread(() -> {
            mockTextView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
            mockTextView.setText("", BufferType.EDITABLE);
            mockTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        reset(mockTextView);
        assertTrue(mockTextView.hasFocus());
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();

        mockTextView.beginBatchEdit();

        verify(mockTextView, times(1)).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();

        reset(mockTextView);
        mockTextView.endBatchEdit();
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, times(1)).onEndBatchEdit();
    }

    @UiThreadTest
    public void testBringPointIntoView() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertFalse(mTextView.bringPointIntoView(1));

        mTextView.layout(0, 0, 100, 100);
        assertFalse(mTextView.bringPointIntoView(2));
    }

    public void testCancelLongPress() {
        mTextView = findTextView(R.id.textview_text);
        CtsTouchUtils.emulateLongClick(mInstrumentation, mTextView);
        mTextView.cancelLongPress();
    }

    @UiThreadTest
    public void testClearComposingText() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setText("Hello world!", BufferType.SPANNABLE);
        Spannable text = (Spannable) mTextView.getText();

        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));

        BaseInputConnection.setComposingSpans((Spannable) mTextView.getText());
        assertEquals(0, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(0, BaseInputConnection.getComposingSpanStart(text));

        mTextView.clearComposingText();
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
    }

    public void testComputeVerticalScrollExtent() {
        MockTextView textView = new MockTextView(mActivity);
        assertEquals(0, textView.computeVerticalScrollExtent());

        Drawable d = TestUtils.getDrawable(mActivity, R.drawable.pass);
        textView.setCompoundDrawables(null, d, null, d);

        assertEquals(0, textView.computeVerticalScrollExtent());
    }

    @UiThreadTest
    public void testDidTouchFocusSelect() {
        mTextView = new EditText(mActivity);
        assertFalse(mTextView.didTouchFocusSelect());

        mTextView.setFocusable(true);
        mTextView.requestFocus();
        assertTrue(mTextView.didTouchFocusSelect());
    }

    public void testSelectAllJustAfterTap() {
        // Prepare an EditText with focus.
        mActivity.runOnUiThread(() -> {
            mTextView = new EditText(mActivity);
            mActivity.setContentView(mTextView);

            assertFalse(mTextView.didTouchFocusSelect());
            mTextView.setFocusable(true);
            mTextView.requestFocus();
            assertTrue(mTextView.didTouchFocusSelect());

            mTextView.setText("Hello, World.", BufferType.SPANNABLE);
        });
        mInstrumentation.waitForIdleSync();

        // Tap the view to show InsertPointController.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        // bad workaround for waiting onStartInputView of LeanbackIme.apk done
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Execute SelectAll context menu.
        mActivity.runOnUiThread(() -> mTextView.onTextContextMenuItem(android.R.id.selectAll));
        mInstrumentation.waitForIdleSync();

        // The selection must be whole of the text contents.
        assertEquals(0, mTextView.getSelectionStart());
        assertEquals("Hello, World.", mTextView.getText().toString());
        assertEquals(mTextView.length(), mTextView.getSelectionEnd());
    }

    public void testExtractText() {
        mTextView = new TextView(mActivity);

        ExtractedTextRequest request = new ExtractedTextRequest();
        ExtractedText outText = new ExtractedText();

        request.token = 0;
        request.flags = 10;
        request.hintMaxLines = 2;
        request.hintMaxChars = 20;
        assertTrue(mTextView.extractText(request, outText));

        mTextView = findTextView(R.id.textview_text);
        assertTrue(mTextView.extractText(request, outText));

        assertEquals(mActivity.getResources().getString(R.string.text_view_hello),
                outText.text.toString());

        // Tests for invalid arguments.
        assertFalse(mTextView.extractText(request, null));
        assertFalse(mTextView.extractText(null, outText));
        assertFalse(mTextView.extractText(null, null));
    }

    @UiThreadTest
    public void testTextDirectionDefault() {
        TextView tv = new TextView(mActivity);
        assertEquals(View.TEXT_DIRECTION_INHERIT, tv.getRawTextDirection());
    }

    @UiThreadTest
    public void testSetGetTextDirection() {
        TextView tv = new TextView(mActivity);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_INHERIT, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getRawTextDirection());
    }

    @UiThreadTest
    public void testGetResolvedTextDirectionLtr() {
        TextView tv = new TextView(mActivity);
        tv.setText("this is a test");

        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    public void testGetResolvedTextDirectionLtrWithInheritance() {
        LinearLayout ll = new LinearLayout(mActivity);
        ll.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);

        TextView tv = new TextView(mActivity);
        tv.setText("this is a test");
        ll.addView(tv);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    public void testGetResolvedTextDirectionRtl() {
        TextView tv = new TextView(mActivity);
        tv.setText("\u05DD\u05DE"); // hebrew

        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    public void testGetResolvedTextDirectionRtlWithInheritance() {
        LinearLayout ll = new LinearLayout(mActivity);
        ll.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);

        TextView tv = new TextView(mActivity);
        tv.setText("\u05DD\u05DE"); // hebrew
        ll.addView(tv);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

        // Force to RTL text direction on the layout
        ll.setTextDirection(View.TEXT_DIRECTION_RTL);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    public void testResetTextDirection() {
        LinearLayout ll = (LinearLayout) mActivity.findViewById(R.id.layout_textviewtest);
        TextView tv = (TextView) mActivity.findViewById(R.id.textview_rtl);

        ll.setTextDirection(View.TEXT_DIRECTION_RTL);
        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        // No reset when we remove the view
        ll.removeView(tv);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        // Reset is done when we add the view
        ll.addView(tv);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());
    }

    @UiThreadTest
    public void testTextDirectionFirstStrongLtr() {
        {
            // The first directional character is LTR, the paragraph direction is LTR.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("this is a test");
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is RTL, the paragraph direction is RTL.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\u05DD\u05DE"); // Hebrew
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_RIGHT_TO_LEFT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is not a strong directional character, the paragraph
            // direction is LTR.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\uFFFD");  // REPLACEMENT CHARACTER. Neutral direction.
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0));
        }
    }

    @UiThreadTest
    public void testTextDirectionFirstStrongRtl() {
        {
            // The first directional character is LTR, the paragraph direction is LTR.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("this is a test");
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is RTL, the paragraph direction is RTL.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\u05DD\u05DE"); // Hebrew
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_RIGHT_TO_LEFT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is not a strong directional character, the paragraph
            // direction is RTL.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\uFFFD");  // REPLACEMENT CHARACTER. Neutral direction.
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_RIGHT_TO_LEFT, layout.getParagraphDirection(0));
        }
    }

    public void testTextLocales() {
        TextView tv = new TextView(mActivity);
        assertEquals(Locale.getDefault(), tv.getTextLocale());
        assertEquals(LocaleList.getDefault(), tv.getTextLocales());

        tv.setTextLocale(Locale.CHINESE);
        assertEquals(Locale.CHINESE, tv.getTextLocale());
        assertEquals(new LocaleList(Locale.CHINESE), tv.getTextLocales());

        tv.setTextLocales(LocaleList.forLanguageTags("en,ja"));
        assertEquals(Locale.forLanguageTag("en"), tv.getTextLocale());
        assertEquals(LocaleList.forLanguageTags("en,ja"), tv.getTextLocales());

        try {
            tv.setTextLocale(null);
            fail("Setting the text locale to null should throw");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }

        try {
            tv.setTextLocales(null);
            fail("Setting the text locales to null should throw");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }

        try {
            tv.setTextLocales(new LocaleList());
            fail("Setting the text locale to an empty list should throw");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    public void testAllCapsLocalization() {
        String testString = "abcdefghijklmnopqrstuvwxyz";

        // The capitalized characters of "i" on Turkish and Azerbaijani are different from English.
        Locale[] testLocales = {
            new Locale("az", "AZ"),
            new Locale("tr", "TR"),
            new Locale("en", "US"),
        };

        TextView tv = new TextView(mActivity);
        tv.setAllCaps(true);
        for (Locale locale: testLocales) {
            tv.setTextLocale(locale);
            assertEquals("Locale: " + locale.getDisplayName(),
                         testString.toUpperCase(locale),
                         tv.getTransformationMethod().getTransformation(testString, tv).toString());
        }
    }

    @UiThreadTest
    public void testTextAlignmentDefault() {
        TextView tv = new TextView(getActivity());
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getRawTextAlignment());
        // resolved default text alignment is GRAVITY
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());
    }

    @UiThreadTest
    public void testSetGetTextAlignment() {
        TextView tv = new TextView(getActivity());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_END, tv.getRawTextAlignment());
    }

    @UiThreadTest
    public void testGetResolvedTextAlignment() {
        TextView tv = new TextView(getActivity());

        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());

        // Test center alignment first so that we dont hit the default case
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        // Test the default case too
        tv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_END, tv.getTextAlignment());
    }

    @UiThreadTest
    public void testGetResolvedTextAlignmentWithInheritance() {
        LinearLayout ll = new LinearLayout(getActivity());
        ll.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

        TextView tv = new TextView(getActivity());
        ll.addView(tv);

        // check defaults
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getRawTextAlignment());
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());

        // set inherit and check that child is following parent
        tv.setTextAlignment(View.TEXT_ALIGNMENT_INHERIT);
        assertEquals(View.TEXT_ALIGNMENT_INHERIT, tv.getRawTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_END, tv.getTextAlignment());

        // now get rid of the inheritance but still change the parent
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        ll.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());
    }

    @UiThreadTest
    public void testResetTextAlignment() {
        TextViewCtsActivity activity = getActivity();

        LinearLayout ll = (LinearLayout) activity.findViewById(R.id.layout_textviewtest);
        TextView tv = (TextView) activity.findViewById(R.id.textview_rtl);

        ll.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_INHERIT);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        // No reset when we remove the view
        ll.removeView(tv);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        // Reset is done when we add the view
        // Default text alignment is GRAVITY
        ll.addView(tv);
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());
    }

    @UiThreadTest
    public void testDrawableResolution() {
        // Case 1.1: left / right drawable defined in default LTR mode
        TextView tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, -1, -1,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 1.2: left / right drawable defined in default RTL mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, -1, -1,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 2.1: start / end drawable defined in LTR mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_2_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 2.2: start / end drawable defined in RTL mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_2_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 3.1: left / right / start / end drawable defined in LTR mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_3_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 3.2: left / right / start / end drawable defined in RTL mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_3_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 4.1: start / end drawable defined in LTR mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_4_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 4.2: start / end drawable defined in RTL mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_4_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 5.1: left / right / start / end drawable defined in LTR mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_5_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 5.2: left / right / start / end drawable defined in RTL mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_5_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
    }

    @UiThreadTest
    public void testDrawableResolution2() {
        // Case 1.1: left / right drawable defined in default LTR mode
        TextView tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        tv.setCompoundDrawables(null, null,
                TestUtils.getDrawable(mActivity, R.drawable.icon_yellow), null);
        TestUtils.verifyCompoundDrawables(tv, -1, R.drawable.icon_yellow, -1, -1);

        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        tv.setCompoundDrawables(TestUtils.getDrawable(mActivity, R.drawable.icon_yellow), null,
                null, null);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_yellow, -1, -1, -1);

        tv = (TextView) mActivity.findViewById(R.id.textview_ltr);
        TestUtils.verifyCompoundDrawables(tv, -1, -1, -1, -1);

        tv.setCompoundDrawables(TestUtils.getDrawable(mActivity, R.drawable.icon_blue), null,
                TestUtils.getDrawable(mActivity, R.drawable.icon_red), null);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red, -1, -1);

        tv.setCompoundDrawablesRelative(TestUtils.getDrawable(mActivity, R.drawable.icon_yellow),
                null, null, null);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_yellow, -1, -1, -1);
    }

    public void testCompoundAndTotalPadding() {
        final Resources res = mActivity.getResources();
        final int drawablePadding = res.getDimensionPixelSize(R.dimen.textview_drawable_padding);
        final int paddingLeft = res.getDimensionPixelSize(R.dimen.textview_padding_left);
        final int paddingRight = res.getDimensionPixelSize(R.dimen.textview_padding_right);
        final int paddingTop = res.getDimensionPixelSize(R.dimen.textview_padding_top);
        final int paddingBottom = res.getDimensionPixelSize(R.dimen.textview_padding_bottom);
        final int iconSize = TestUtils.dpToPx(mActivity, 32);

        final TextView textViewLtr = (TextView) mActivity.findViewById(
                R.id.textview_compound_drawable_ltr);
        final int combinedPaddingLeftLtr = paddingLeft + drawablePadding + iconSize;
        final int combinedPaddingRightLtr = paddingRight + drawablePadding + iconSize;
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getCompoundPaddingLeft());
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getCompoundPaddingStart());
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getTotalPaddingLeft());
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getTotalPaddingStart());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getCompoundPaddingRight());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getCompoundPaddingEnd());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getTotalPaddingRight());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getTotalPaddingEnd());
        assertEquals(paddingTop + drawablePadding + iconSize,
                textViewLtr.getCompoundPaddingTop());
        assertEquals(paddingBottom + drawablePadding + iconSize,
                textViewLtr.getCompoundPaddingBottom());

        final TextView textViewRtl = (TextView) mActivity.findViewById(
                R.id.textview_compound_drawable_rtl);
        final int combinedPaddingLeftRtl = paddingLeft + drawablePadding + iconSize;
        final int combinedPaddingRightRtl = paddingRight + drawablePadding + iconSize;
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getCompoundPaddingLeft());
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getCompoundPaddingEnd());
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getTotalPaddingLeft());
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getTotalPaddingEnd());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getCompoundPaddingRight());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getCompoundPaddingStart());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getTotalPaddingRight());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getTotalPaddingStart());
        assertEquals(paddingTop + drawablePadding + iconSize,
                textViewRtl.getCompoundPaddingTop());
        assertEquals(paddingBottom + drawablePadding + iconSize,
                textViewRtl.getCompoundPaddingBottom());
    }

    public void testSetGetBreakStrategy() {
        TextView tv = new TextView(mActivity);

        final PackageManager pm = getInstrumentation().getTargetContext().getPackageManager();

        // The default value is from the theme, here the default is BREAK_STRATEGY_HIGH_QUALITY for
        // TextView except for Android Wear. The default value for Android Wear is
        // BREAK_STRATEGY_BALANCED.
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            // Android Wear
            assertEquals(Layout.BREAK_STRATEGY_BALANCED, tv.getBreakStrategy());
        } else {
            // All other form factor.
            assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, tv.getBreakStrategy());
        }

        tv.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, tv.getBreakStrategy());

        tv.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, tv.getBreakStrategy());

        tv.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        assertEquals(Layout.BREAK_STRATEGY_BALANCED, tv.getBreakStrategy());

        EditText et = new EditText(mActivity);

        // The default value is from the theme, here the default is BREAK_STRATEGY_SIMPLE for
        // EditText.
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, et.getBreakStrategy());

        et.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, et.getBreakStrategy());

        et.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, et.getBreakStrategy());

        et.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        assertEquals(Layout.BREAK_STRATEGY_BALANCED, et.getBreakStrategy());
    }

    public void testSetGetHyphenationFrequency() {
        TextView tv = new TextView(mActivity);

        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, tv.getHyphenationFrequency());

        tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, tv.getHyphenationFrequency());

        tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, tv.getHyphenationFrequency());

        tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
        assertEquals(Layout.HYPHENATION_FREQUENCY_FULL, tv.getHyphenationFrequency());
    }

    public void testSetAndGetCustomSelectionActionModeCallback() {
        final String text = "abcde";
        mActivity.runOnUiThread(() -> {
            mTextView = new EditText(mActivity);
            mActivity.setContentView(mTextView);
            mTextView.setText(text, BufferType.SPANNABLE);
            mTextView.setTextIsSelectable(true);
            mTextView.requestFocus();
            mTextView.setSelected(true);
        });
        mInstrumentation.waitForIdleSync();

        // Check default value.
        assertNull(mTextView.getCustomSelectionActionModeCallback());

        final ActionMode.Callback mockActionModeCallback = mock(ActionMode.Callback.class);
        when(mockActionModeCallback.onCreateActionMode(any(ActionMode.class), any(Menu.class))).
                thenReturn(Boolean.FALSE);
        mTextView.setCustomSelectionActionModeCallback(mockActionModeCallback);
        assertEquals(mockActionModeCallback,
                mTextView.getCustomSelectionActionModeCallback());

        mActivity.runOnUiThread(() -> {
            // Set selection and try to start action mode.
            final Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
            mTextView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
        });
        mInstrumentation.waitForIdleSync();

        verify(mockActionModeCallback, times(1)).onCreateActionMode(
                any(ActionMode.class), any(Menu.class));

        mActivity.runOnUiThread(() -> {
            // Remove selection and stop action mode.
            mTextView.onTextContextMenuItem(android.R.id.copy);
        });
        mInstrumentation.waitForIdleSync();

        // Action mode was blocked.
        verify(mockActionModeCallback, never()).onDestroyActionMode(any(ActionMode.class));

        // Reset and reconfigure callback.
        reset(mockActionModeCallback);
        when(mockActionModeCallback.onCreateActionMode(any(ActionMode.class), any(Menu.class))).
                thenReturn(Boolean.TRUE);
        assertEquals(mockActionModeCallback, mTextView.getCustomSelectionActionModeCallback());

        mActivity.runOnUiThread(() -> {
            // Set selection and try to start action mode.
            final Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
            mTextView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SET_SELECTION, args);

        });
        mInstrumentation.waitForIdleSync();

        verify(mockActionModeCallback, times(1)).onCreateActionMode(
                any(ActionMode.class), any(Menu.class));

        mActivity.runOnUiThread(() -> {
            // Remove selection and stop action mode.
            mTextView.onTextContextMenuItem(android.R.id.copy);
        });
        mInstrumentation.waitForIdleSync();

        // Action mode was started
        verify(mockActionModeCallback, times(1)).onDestroyActionMode(any(ActionMode.class));
    }

    @UiThreadTest
    public void testSetAndGetCustomInsertionActionMode() {
        initTextViewForTyping();
        // Check default value.
        assertNull(mTextView.getCustomInsertionActionModeCallback());

        final ActionMode.Callback mockActionModeCallback = mock(ActionMode.Callback.class);
        when(mockActionModeCallback.onCreateActionMode(any(ActionMode.class), any(Menu.class))).
                thenReturn(Boolean.FALSE);
        mTextView.setCustomInsertionActionModeCallback(mockActionModeCallback);
        assertEquals(mockActionModeCallback, mTextView.getCustomInsertionActionModeCallback());
        // TODO(Bug: 22033189): Tests the set callback is actually used.
    }

    public void testTextShadows() {
        final TextView textViewWithConfiguredShadow =
                (TextView) mActivity.findViewById(R.id.textview_with_shadow);
        assertEquals(1.0f, textViewWithConfiguredShadow.getShadowDx());
        assertEquals(2.0f, textViewWithConfiguredShadow.getShadowDy());
        assertEquals(3.0f, textViewWithConfiguredShadow.getShadowRadius());
        assertEquals(Color.GREEN, textViewWithConfiguredShadow.getShadowColor());

        final TextView textView = (TextView) mActivity.findViewById(R.id.textview_text);
        assertEquals(0.0f, textView.getShadowDx());
        assertEquals(0.0f, textView.getShadowDy());
        assertEquals(0.0f, textView.getShadowRadius());

        mActivity.runOnUiThread(() -> textView.setShadowLayer(5.0f, 3.0f, 4.0f, Color.RED));
        mInstrumentation.waitForIdleSync();
        assertEquals(3.0f, textView.getShadowDx());
        assertEquals(4.0f, textView.getShadowDy());
        assertEquals(5.0f, textView.getShadowRadius());
        assertEquals(Color.RED, textView.getShadowColor());
    }

    public void testFontFeatureSettings() {
        final TextView textView = (TextView) mActivity.findViewById(R.id.textview_text);
        assertTrue(TextUtils.isEmpty(textView.getFontFeatureSettings()));

        mActivity.runOnUiThread(() -> textView.setFontFeatureSettings("smcp"));
        mInstrumentation.waitForIdleSync();
        assertEquals("smcp", textView.getFontFeatureSettings());

        mActivity.runOnUiThread(() -> textView.setFontFeatureSettings("frac"));
        mInstrumentation.waitForIdleSync();
        assertEquals("frac", textView.getFontFeatureSettings());
    }

    private static class SoftInputResultReceiver extends ResultReceiver {
        private boolean mIsDone;
        private int mResultCode;

        public SoftInputResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mResultCode = resultCode;
            mIsDone = true;
        }

        public void reset() {
            mIsDone = false;
        }
    }

    public void testAccessShowSoftInputOnFocus() {
        if (!mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS)) {
            return;
        }

        // Scroll down to our EditText
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroller);
        mTextView = findTextView(R.id.editview_text);
        mActivity.runOnUiThread(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        mInstrumentation.waitForIdleSync();

        // Mark it to show soft input on focus
        mActivity.runOnUiThread(() -> mTextView.setShowSoftInputOnFocus(true));
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.getShowSoftInputOnFocus());

        // And emulate click on it
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);

        // Verify that input method manager is active and accepting text
        final InputMethodManager imManager = (InputMethodManager) mActivity
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        PollingCheck.waitFor(imManager::isActive);
        assertTrue(imManager.isAcceptingText());
        assertTrue(imManager.isActive(mTextView));

        // Since there is no API to check that soft input is showing, we're going to ask
        // the input method manager to show soft input, passing our custom result receiver.
        // We're expecting to get UNCHANGED_SHOWN, indicating that the soft input was already
        // showing before showSoftInput was called.
        SoftInputResultReceiver receiver = new SoftInputResultReceiver(mHandler);
        imManager.showSoftInput(mTextView, 0, receiver);
        PollingCheck.waitFor(() -> receiver.mIsDone);
        assertEquals(InputMethodManager.RESULT_UNCHANGED_SHOWN, receiver.mResultCode);

        // Close soft input
        sendKeys(KeyEvent.KEYCODE_BACK);

        // Reconfigure our edit text to not show soft input on focus
        mActivity.runOnUiThread(() -> mTextView.setShowSoftInputOnFocus(false));
        mInstrumentation.waitForIdleSync();
        assertFalse(mTextView.getShowSoftInputOnFocus());

        // Emulate click on it
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);

        // Ask input method manager to show soft input again. This time we're expecting to get
        // SHOWN, indicating that the soft input was not showing before showSoftInput was called.
        receiver.reset();
        imManager.showSoftInput(mTextView, 0, receiver);
        PollingCheck.waitFor(() -> receiver.mIsDone);
        assertEquals(InputMethodManager.RESULT_SHOWN, receiver.mResultCode);

        // Close soft input
        sendKeys(KeyEvent.KEYCODE_BACK);
    }

    public void testIsSuggestionsEnabled() {
        mTextView = findTextView(R.id.textview_text);

        // Anything without InputType.TYPE_CLASS_TEXT doesn't have suggestions enabled
        mInstrumentation.runOnMainSync(() -> mTextView.setInputType(InputType.TYPE_CLASS_DATETIME));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(() -> mTextView.setInputType(InputType.TYPE_CLASS_PHONE));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(() -> mTextView.setInputType(InputType.TYPE_CLASS_NUMBER));
        assertFalse(mTextView.isSuggestionsEnabled());

        // From this point our text view has InputType.TYPE_CLASS_TEXT

        // Anything with InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS doesn't have suggestions enabled
        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL |
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS |
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        assertFalse(mTextView.isSuggestionsEnabled());

        // Otherwise suggestions are enabled for specific type variations enumerated in the
        // documentation of TextView.isSuggestionsEnabled
        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL));
        assertTrue(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT));
        assertTrue(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE));
        assertTrue(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
        assertTrue(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT));
        assertTrue(mTextView.isSuggestionsEnabled());

        // and not on any other type variation
        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PHONETIC));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mInstrumentation.runOnMainSync(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
        assertFalse(mTextView.isSuggestionsEnabled());
    }

    public void testAccessLetterSpacing() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(0.0f, mTextView.getLetterSpacing());

        final CharSequence text = mTextView.getText();
        final int textLength = text.length();

        // Get advance widths of each character at the default letter spacing
        final float[] initialWidths = new float[textLength];
        mTextView.getPaint().getTextWidths(text.toString(), initialWidths);

        // Get advance widths of each character at letter spacing = 1.0f
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mTextView,
                () -> mTextView.setLetterSpacing(1.0f));
        assertEquals(1.0f, mTextView.getLetterSpacing());
        final float[] singleWidths = new float[textLength];
        mTextView.getPaint().getTextWidths(text.toString(), singleWidths);

        // Get advance widths of each character at letter spacing = 2.0f
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mTextView,
                () -> mTextView.setLetterSpacing(2.0f));
        assertEquals(2.0f, mTextView.getLetterSpacing());
        final float[] doubleWidths = new float[textLength];
        mTextView.getPaint().getTextWidths(text.toString(), doubleWidths);

        // Since letter spacing setter treats the parameter as EM units, and we don't have
        // a way to convert EMs into pixels, go over the three arrays of advance widths and
        // test that the extra advance width at letter spacing 2.0f is double the extra
        // advance width at letter spacing 1.0f.
        for (int i = 0; i < textLength; i++) {
            float singleWidthDelta = singleWidths[i] - initialWidths[i];
            float doubleWidthDelta = doubleWidths[i] - initialWidths[i];
            assertEquals("At index " + i + " initial is " + initialWidths[i] +
                ", single is " + singleWidths[i] + " and double is " + doubleWidths[i],
                    singleWidthDelta * 2.0f, doubleWidthDelta, 0.05f);
        }
    }

    public void testTextIsSelectableFocusAndOnClick() {
        // Prepare a focusable TextView with an onClickListener attached.
        final View.OnClickListener mockOnClickListener = mock(View.OnClickListener.class);
        mActivity.runOnUiThread(() -> {
            mTextView = new TextView(mActivity);
            mTextView.setText("...text 11:11. some more text is in here...");
            mTextView.setFocusable(true);
            mTextView.setOnClickListener(mockOnClickListener);
            mActivity.setContentView(mTextView);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocusable());
        assertFalse(mTextView.isTextSelectable());
        assertFalse(mTextView.isFocusableInTouchMode());
        assertFalse(mTextView.isFocused());
        assertFalse(mTextView.isInTouchMode());

        // First tap on the view triggers onClick() but does not focus the TextView.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        assertTrue(mTextView.isInTouchMode());
        assertFalse(mTextView.isFocused());
        verify(mockOnClickListener, times(1)).onClick(mTextView);
        // So does the second tap.
        reset(mockOnClickListener);
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        assertTrue(mTextView.isInTouchMode());
        assertFalse(mTextView.isFocused());
        verify(mockOnClickListener, times(1)).onClick(mTextView);

        mActivity.runOnUiThread(() -> mTextView.setTextIsSelectable(true));
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocusable());
        assertTrue(mTextView.isTextSelectable());
        assertTrue(mTextView.isFocusableInTouchMode());
        assertFalse(mTextView.isFocused());

        // First tap on the view focuses the TextView but does not trigger onClick().
        reset(mockOnClickListener);
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        assertTrue(mTextView.isInTouchMode());
        assertTrue(mTextView.isFocused());
        verify(mockOnClickListener, never()).onClick(mTextView);
        // The second tap triggers onClick() and keeps the focus.
        reset(mockOnClickListener);
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        assertTrue(mTextView.isInTouchMode());
        assertTrue(mTextView.isFocused());
        verify(mockOnClickListener, times(1)).onClick(mTextView);
    }

    private void verifyGetOffsetForPosition(final int x, final int y) {
        final int actual = mTextView.getOffsetForPosition(x, y);

        final Layout layout = mTextView.getLayout();
        if (layout == null) {
            assertEquals("For [" + x + ", " + y + "]", -1, actual);
            return;
        }

        // Get the line which corresponds to the Y position
        final int line = layout.getLineForVertical(y + mTextView.getScrollY());
        // Get the offset in that line that corresponds to the X position
        final int expected = layout.getOffsetForHorizontal(line, x + mTextView.getScrollX());
        assertEquals("For [" + x + ", " + y + "]", expected, actual);
    }

    public void testGetOffsetForPosition() {
        mTextView = findTextView(R.id.textview_text);
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mTextView, () -> {
            mTextView.setText(LONG_TEXT);
            mTextView.setPadding(0, 0, 0, 0);
        });

        assertNotNull(mTextView.getLayout());
        final int viewWidth = mTextView.getWidth();
        final int viewHeight = mTextView.getHeight();
        final int lineHeight = mTextView.getLineHeight();

        verifyGetOffsetForPosition(0, 0);
        verifyGetOffsetForPosition(0, viewHeight / 2);
        verifyGetOffsetForPosition(viewWidth / 3, lineHeight / 2);
        verifyGetOffsetForPosition(viewWidth / 2, viewHeight / 2);
        verifyGetOffsetForPosition(viewWidth, viewHeight);
    }

    @UiThreadTest
    public void testOnResolvePointerIcon() throws InterruptedException {
        final TextView selectableTextView = findTextView(R.id.textview_pointer);
        final MotionEvent event = createMouseHoverEvent(selectableTextView);

        // A selectable view shows the I beam
        selectableTextView.setTextIsSelectable(true);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_TEXT),
                selectableTextView.onResolvePointerIcon(event, 0));
        selectableTextView.setTextIsSelectable(false);

        // A clickable view shows the hand
        selectableTextView.setLinksClickable(true);
        SpannableString builder = new SpannableString("hello world");
        selectableTextView.setText(builder, BufferType.SPANNABLE);
        Spannable text = (Spannable) selectableTextView.getText();
        text.setSpan(
                new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {

                    }
                }, 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HAND),
                selectableTextView.onResolvePointerIcon(event, 0));

        // A selectable & clickable view shows hand
        selectableTextView.setTextIsSelectable(true);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HAND),
                selectableTextView.onResolvePointerIcon(event, 0));

        // An editable view shows the I-beam
        final TextView editableTextView = new EditText(mActivity);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_TEXT),
                editableTextView.onResolvePointerIcon(event, 0));
    }

    private MotionEvent createMouseHoverEvent(View view) {
        final int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        float x = xy[0] + viewWidth / 2.0f;
        float y = xy[1] + viewHeight / 2.0f;
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[1];
        pointerCoords[0] = new MotionEvent.PointerCoords();
        pointerCoords[0].x = x;
        pointerCoords[0].y = y;
        final int[] pointerIds = new int[1];
        pointerIds[0] = 0;
        return MotionEvent.obtain(0, eventTime, MotionEvent.ACTION_HOVER_MOVE, 1, pointerIds,
                pointerCoords, 0, 0, 0, 0, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private void layout(final TextView textView) {
        mActivity.runOnUiThread(() -> mActivity.setContentView(textView));
        mInstrumentation.waitForIdleSync();
    }

    private void layout(final int layoutId) {
        mActivity.runOnUiThread(() -> mActivity.setContentView(layoutId));
        mInstrumentation.waitForIdleSync();
    }

    private TextView findTextView(int id) {
        return (TextView) mActivity.findViewById(id);
    }

    private int getAutoLinkMask(int id) {
        return findTextView(id).getAutoLinkMask();
    }

    private void setMaxLines(final int lines) {
        mActivity.runOnUiThread(() -> mTextView.setMaxLines(lines));
        mInstrumentation.waitForIdleSync();
    }

    private void setMaxWidth(final int pixels) {
        mActivity.runOnUiThread(() -> mTextView.setMaxWidth(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinWidth(final int pixels) {
        mActivity.runOnUiThread(() -> mTextView.setMinWidth(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMaxHeight(final int pixels) {
        mActivity.runOnUiThread(() -> mTextView.setMaxHeight(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinHeight(final int pixels) {
        mActivity.runOnUiThread(() -> mTextView.setMinHeight(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinLines(final int minlines) {
        mActivity.runOnUiThread(() -> mTextView.setMinLines(minlines));
        mInstrumentation.waitForIdleSync();
    }

    /**
     * Convenience for {@link TextView#setText(CharSequence, BufferType)}. And
     * the buffer type is fixed to SPANNABLE.
     *
     * @param tv the text view
     * @param content the content
     */
    private void setSpannableText(final TextView tv, final String content) {
        mActivity.runOnUiThread(() -> tv.setText(content, BufferType.SPANNABLE));
        mInstrumentation.waitForIdleSync();
    }

    private void setLines(final int lines) {
        mActivity.runOnUiThread(() -> mTextView.setLines(lines));
        mInstrumentation.waitForIdleSync();
    }

    private void setHorizontallyScrolling(final boolean whether) {
        mActivity.runOnUiThread(() -> mTextView.setHorizontallyScrolling(whether));
        mInstrumentation.waitForIdleSync();
    }

    private void setWidth(final int pixels) {
        mActivity.runOnUiThread(() -> mTextView.setWidth(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setHeight(final int pixels) {
        mActivity.runOnUiThread(() -> mTextView.setHeight(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinEms(final int ems) {
        mActivity.runOnUiThread(() -> mTextView.setMinEms(ems));
        mInstrumentation.waitForIdleSync();
    }

    private void setMaxEms(final int ems) {
        mActivity.runOnUiThread(() -> mTextView.setMaxEms(ems));
        mInstrumentation.waitForIdleSync();
    }

    private void setEms(final int ems) {
        mActivity.runOnUiThread(() -> mTextView.setEms(ems));
        mInstrumentation.waitForIdleSync();
    }

    private void setLineSpacing(final float add, final float mult) {
        mActivity.runOnUiThread(() -> mTextView.setLineSpacing(add, mult));
        mInstrumentation.waitForIdleSync();
    }

    private static abstract class TestSelectedRunnable implements Runnable {
        private TextView mTextView;
        private boolean mIsSelected1;
        private boolean mIsSelected2;

        public TestSelectedRunnable(TextView textview) {
            mTextView = textview;
        }

        public boolean getIsSelected1() {
            return mIsSelected1;
        }

        public boolean getIsSelected2() {
            return mIsSelected2;
        }

        public void saveIsSelected1() {
            mIsSelected1 = mTextView.isSelected();
        }

        public void saveIsSelected2() {
            mIsSelected2 = mTextView.isSelected();
        }
    }

    private static abstract class TestLayoutRunnable implements Runnable {
        private TextView mTextView;
        private Layout mLayout;

        public TestLayoutRunnable(TextView textview) {
            mTextView = textview;
        }

        public Layout getLayout() {
            return mLayout;
        }

        public void saveLayout() {
            mLayout = mTextView.getLayout();
        }
    }

    private static class MockTextWatcher implements TextWatcher {
        private boolean mHasCalledAfterTextChanged;
        private boolean mHasCalledBeforeTextChanged;
        private boolean mHasOnTextChanged;

        public void reset(){
            mHasCalledAfterTextChanged = false;
            mHasCalledBeforeTextChanged = false;
            mHasOnTextChanged = false;
        }

        public boolean hasCalledAfterTextChanged() {
            return mHasCalledAfterTextChanged;
        }

        public boolean hasCalledBeforeTextChanged() {
            return mHasCalledBeforeTextChanged;
        }

        public boolean hasCalledOnTextChanged() {
            return mHasOnTextChanged;
        }

        public void afterTextChanged(Editable s) {
            mHasCalledAfterTextChanged = true;
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mHasCalledBeforeTextChanged = true;
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mHasOnTextChanged = true;
        }
    }

    /**
     * A TextWatcher that converts the text to spaces whenever the text changes.
     */
    private static class ConvertToSpacesTextWatcher implements TextWatcher {
        boolean mChangingText;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Avoid infinite recursion.
            if (mChangingText) {
                return;
            }
            mChangingText = true;
            // Create a string of s.length() spaces.
            StringBuilder builder = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                builder.append(' ');
            }
            s.replace(0, s.length(), builder.toString());
            mChangingText = false;
        }
    }
}
