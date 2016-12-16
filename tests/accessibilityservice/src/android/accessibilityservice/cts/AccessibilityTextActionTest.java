/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import android.app.UiAutomation;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import android.accessibilityservice.cts.R;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test cases for actions taken on text views.
 */
public class AccessibilityTextActionTest extends
        AccessibilityActivityTestCase<AccessibilityTextTraversalActivity> {
    final Object mClickableSpanCallbackLock = new Object();
    final AtomicBoolean mClickableSpanCalled = new AtomicBoolean(false);
    UiAutomation mUiAutomation;

    public AccessibilityTextActionTest() {
        super(AccessibilityTextTraversalActivity.class);
    }

    public void setUp() throws Exception {
        super.setUp();
        mUiAutomation = getInstrumentation().getUiAutomation();
        mClickableSpanCalled.set(false);
    }

    public void tearDown() throws Exception {
        mUiAutomation.destroy();
        super.tearDown();
    }

    public void testNotEditableTextView_shouldNotExposeOrRespondToSetTextAction() {
        final TextView textView = (TextView) getActivity().findViewById(R.id.text);
        makeTextViewVisibleAndSetText(textView, getString(R.string.a_b));

        final AccessibilityNodeInfo text = mUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.a_b)).get(0);

        assertFalse("Standard text view should not support SET_TEXT", text.getActionList()
                .contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT));
        assertEquals("Standard text view should not support SET_TEXT", 0,
                text.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                getString(R.string.text_input_blah));
        assertFalse(text.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));

        getInstrumentation().waitForIdleSync();
        assertTrue("Text view should not update on failed set text",
                TextUtils.equals(getString(R.string.a_b), textView.getText()));
    }

    public void testEditableTextView_shouldExposeAndRespondToSetTextAction() {
        final TextView textView = (TextView) getActivity().findViewById(R.id.text);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                textView.setVisibility(View.VISIBLE);
                textView.setText(getString(R.string.a_b), TextView.BufferType.EDITABLE);
            }
        });

        final AccessibilityNodeInfo text = mUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.a_b)).get(0);

        assertTrue("Editable text view should support SET_TEXT", text.getActionList()
                .contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT));
        assertEquals("Editable text view should support SET_TEXT",
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                text.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT);

        Bundle args = new Bundle();
        String textToSet = getString(R.string.text_input_blah);
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSet);

        assertTrue(text.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));

        getInstrumentation().waitForIdleSync();
        assertTrue("Editable text should update on set text",
                TextUtils.equals(textToSet, textView.getText()));
    }

    public void testEditText_shouldExposeAndRespondToSetTextAction() {
        final EditText editText = (EditText) getActivity().findViewById(R.id.edit);
        makeTextViewVisibleAndSetText(editText, getString(R.string.a_b));

        final AccessibilityNodeInfo text = mUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.a_b)).get(0);

        assertTrue("EditText should support SET_TEXT", text.getActionList()
                .contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT));
        assertEquals("EditText view should support SET_TEXT",
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                text.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT);

        Bundle args = new Bundle();
        String textToSet = getString(R.string.text_input_blah);
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSet);

        assertTrue(text.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));

        getInstrumentation().waitForIdleSync();
        assertTrue("EditText should update on set text",
                TextUtils.equals(textToSet, editText.getText()));
    }

    public void testClickableSpan_shouldWorkFromAccessibilityService() {
        final TextView textView = (TextView) getActivity().findViewById(R.id.text);
        final ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                assertEquals("Clickable span called back on wrong View", textView, widget);
                onClickCallback();
            }
        };
        final SpannableString textWithClickableSpan = new SpannableString(getString(R.string.a_b));
        textWithClickableSpan.setSpan(clickableSpan, 0, 1, 0);
        makeTextViewVisibleAndSetText(textView, textWithClickableSpan);

        ClickableSpan clickableSpanFromA11y
                = findSingleSpanInViewWithText(R.string.a_b, ClickableSpan.class);
        clickableSpanFromA11y.onClick(null);
        assertOnClickCalled();
    }

    public void testUrlSpan_shouldWorkFromAccessibilityService() {
        final TextView textView = (TextView) getActivity().findViewById(R.id.text);
        final String url = "com.android.some.random.url";
        final URLSpan urlSpan = new URLSpan(url) {
            @Override
            public void onClick(View widget) {
                assertEquals("Url span called back on wrong View", textView, widget);
                onClickCallback();
            }
        };
        final SpannableString textWithClickableSpan = new SpannableString(getString(R.string.a_b));
        textWithClickableSpan.setSpan(urlSpan, 0, 1, 0);
        makeTextViewVisibleAndSetText(textView, textWithClickableSpan);

        URLSpan urlSpanFromA11y = findSingleSpanInViewWithText(R.string.a_b, URLSpan.class);
        assertEquals(url, urlSpanFromA11y.getURL());
        urlSpanFromA11y.onClick(null);

        assertOnClickCalled();
    }

    private void onClickCallback() {
        synchronized (mClickableSpanCallbackLock) {
            mClickableSpanCalled.set(true);
            mClickableSpanCallbackLock.notifyAll();
        }
    }

    private void assertOnClickCalled() {
        synchronized (mClickableSpanCallbackLock) {
            long endTime = System.currentTimeMillis() + TIMEOUT_ASYNC_PROCESSING;
            while (!mClickableSpanCalled.get() && (System.currentTimeMillis() < endTime)) {
                try {
                    mClickableSpanCallbackLock.wait(endTime - System.currentTimeMillis());
                } catch (InterruptedException e) {}
            }
        }
        assert(mClickableSpanCalled.get());
    }

    private <T> T findSingleSpanInViewWithText(int stringId, Class<T> type) {
        final AccessibilityNodeInfo text = mUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(stringId)).get(0);
        CharSequence accessibilityTextWithSpan = text.getText();
        assertTrue(accessibilityTextWithSpan instanceof Spanned);

        T spans[] = ((Spanned) accessibilityTextWithSpan)
                .getSpans(0, accessibilityTextWithSpan.length(), type);
        assertEquals(1, spans.length);
        return spans[0];
    }

    private void makeTextViewVisibleAndSetText(final TextView textView, final CharSequence text) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                textView.setVisibility(View.VISIBLE);
                textView.setText(text);
            }
        });
    }
}
