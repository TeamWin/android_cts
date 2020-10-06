/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.OnReceiveContentCallback.Payload.FLAG_CONVERT_TO_PLAIN_TEXT;
import static android.view.OnReceiveContentCallback.Payload.SOURCE_AUTOFILL;
import static android.view.OnReceiveContentCallback.Payload.SOURCE_CLIPBOARD;
import static android.view.OnReceiveContentCallback.Payload.SOURCE_DRAG_AND_DROP;
import static android.view.OnReceiveContentCallback.Payload.SOURCE_INPUT_METHOD;
import static android.view.OnReceiveContentCallback.Payload.SOURCE_PROCESS_TEXT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import static java.util.Collections.singleton;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.QwertyKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.text.style.UnderlineSpan;
import android.view.DragEvent;
import android.view.OnReceiveContentCallback;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.TextViewOnReceiveContentCallback;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.Objects;
import java.util.Set;

/**
 * Test {@link OnReceiveContentCallback} and its integration with {@link TextView}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextViewOnReceiveContentCallbackTest {
    public static final Uri SAMPLE_CONTENT_URI = Uri.parse("content://com.example/path");
    @Rule
    public ActivityTestRule<TextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewCtsActivity.class);

    private Activity mActivity;
    private TextView mTextView;
    private OnReceiveContentCallback<TextView> mDefaultReceiver;
    private MockReceiverWrapper mMockReceiverWrapper;
    private OnReceiveContentCallback<TextView> mMockReceiver;
    private ClipboardManager mClipboardManager;

    @Before
    public void before() {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mTextView = mActivity.findViewById(R.id.textview_text);
        mDefaultReceiver = new TextViewOnReceiveContentCallback();

        mMockReceiverWrapper = new MockReceiverWrapper();
        mMockReceiver = mMockReceiverWrapper.getMock();

        mClipboardManager = mActivity.getSystemService(ClipboardManager.class);
        mClipboardManager.clearPrimaryClip();

        configureAppTargetSdkToS();
    }

    @After
    public void after() {
        resetTargetSdk();
    }

    // ============================================================================================
    // Tests to verify TextView APIs/accessors/defaults related to OnReceiveContentCallback.
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testTextView_getAndSetOnReceiveContentCallback() throws Exception {
        // Verify that by default the getter returns null.
        assertThat(mTextView.getOnReceiveContentCallback()).isNull();

        // Verify that after setting a custom receiver, the getter returns it.
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);
        assertThat(mTextView.getOnReceiveContentCallback()).isSameInstanceAs(mMockReceiverWrapper);

        // Verify that setting a null receiver clears the previously set custom receiver.
        mTextView.setOnReceiveContentCallback(null);
        assertThat(mTextView.getOnReceiveContentCallback()).isNull();
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_nullEditorInfo() throws Exception {
        initTextViewForEditing("xz", 1);
        try {
            mTextView.onCreateInputConnection(null);
            Assert.fail("Expected exception");
        } catch (NullPointerException expected) {
        }
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Call onCreateInputConnection() and assert that contentMimeTypes is not set when there is
        // no custom receiver configured.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        assertThat(editorInfo.contentMimeTypes).isNull();
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_customReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/plain", "image/png", "video/mp4");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Call onCreateInputConnection() and assert that contentMimeTypes is set from the receiver.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        assertThat(editorInfo.contentMimeTypes).isEqualTo(receiverMimeTypes.toArray(new String[0]));
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verifyNoMoreInteractions(mMockReceiver);
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_customReceiver_oldTargetSdk()
            throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/plain", "image/png", "video/mp4");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Call onCreateInputConnection() and assert that contentMimeTypes is set from the receiver.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        assertThat(editorInfo.contentMimeTypes).isEqualTo(receiverMimeTypes.toArray(new String[0]));
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verifyNoMoreInteractions(mMockReceiver);
    }

    @UiThreadTest
    @Test
    public void testTextView_getAutofillType_noCustomReceiver() throws Exception {
        initTextViewForEditing("", 0);
        assertThat(mTextView.getAutofillType()).isEqualTo(View.AUTOFILL_TYPE_TEXT);
    }

    @UiThreadTest
    @Test
    public void testTextView_getAutofillType_customReceiver() throws Exception {
        initTextViewForEditing("", 0);

        // Setup: Configure the receiver to a mock impl that supports text and images.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Assert that the autofill type returned is still AUTOFILL_TYPE_TEXT.
        assertThat(mTextView.getAutofillType()).isEqualTo(View.AUTOFILL_TYPE_TEXT);
        verifyZeroInteractions(mMockReceiver);
    }

    @UiThreadTest
    @Test
    public void testTextView_getAutofillType_customReceiver_oldTargetSdk() throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("", 0);

        // Setup: Configure the receiver to a mock impl that supports text and images.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Assert that the autofill type returned is still AUTOFILL_TYPE_TEXT.
        assertThat(mTextView.getAutofillType()).isEqualTo(View.AUTOFILL_TYPE_TEXT);
        verifyZeroInteractions(mMockReceiver);
    }

    // ============================================================================================
    // Tests to verify the behavior of TextViewOnReceiveContentCallback.
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testDefaultReceiver_getSupportedMimeTypes() throws Exception {
        assertThat(mDefaultReceiver.getSupportedMimeTypes(mTextView))
                .isEqualTo(singleton("text/*"));
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_text() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "y");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_styledText() throws Exception {
        initTextViewForEditing("xz", 1);

        UnderlineSpan underlineSpan = new UnderlineSpan();
        SpannableStringBuilder ssb = new SpannableStringBuilder("hi world");
        ssb.setSpan(underlineSpan, 3, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ClipData clip = ClipData.newPlainText("test", ssb);

        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xhi worldz", 9);
        int spanStart = mTextView.getEditableText().getSpanStart(underlineSpan);
        assertThat(spanStart).isEqualTo(4);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_text_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "y");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_styledText_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        UnderlineSpan underlineSpan = new UnderlineSpan();
        SpannableStringBuilder ssb = new SpannableStringBuilder("hi world");
        ssb.setSpan(underlineSpan, 3, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ClipData clip = ClipData.newPlainText("test", ssb);

        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("xhi worldz", 9);
        int spanStart = mTextView.getEditableText().getSpanStart(underlineSpan);
        assertThat(spanStart).isEqualTo(-1);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_html() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_html_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("x*y*z", 4);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", "html", null, SAMPLE_CONTENT_URI));
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xhtmlz", 5);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_unsupportedMimeType_convertToPlainText()
            throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", "html", null, SAMPLE_CONTENT_URI));
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD,
                FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("xtextz", 5);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_multipleItemsInClipData() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "ONE");
        clip.addItem(new ClipData.Item("TWO"));
        clip.addItem(new ClipData.Item("THREE"));
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xONE\nTWO\nTHREEz", 14);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_noSelectionPriorToPaste() throws Exception {
        // Set the text and then clear the selection (ie, ensure that nothing is selected and
        // that the cursor is not present).
        initTextViewForEditing("xz", 0);
        Selection.removeSelection(mTextView.getEditableText());
        assertTextAndCursorPosition("xz", -1);

        // Pasting should still work (should just insert the text at the beginning).
        ClipData clip = ClipData.newPlainText("test", "y");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("yxz", 1);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_selectionStartAndEndSwapped() throws Exception {
        initTextViewForEditing("", 0);

        // Set the selection such that "end" is before "start".
        mTextView.setText("hey", BufferType.EDITABLE);
        Selection.setSelection(mTextView.getEditableText(), 3, 1);
        assertTextAndSelection("hey", 3, 1);

        // Pasting should still work (should still successfully overwrite the selection).
        ClipData clip = ClipData.newPlainText("test", "i");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("hi", 2);
    }

    // ============================================================================================
    // Tests to verify that the OnReceiveContentCallback is invoked for all the appropriate user
    // interactions:
    // * Paste from clipboard ("Paste" and "Paste as plain text" actions)
    // * Content insertion from IME
    // * Drag and drop
    // * Autofill
    // * Process text (Intent.ACTION_PROCESS_TEXT)
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testPaste_noCustomReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy text to the clipboard.
        ClipData clip = ClipData.newPlainText("test", "y");
        copyToClipboard(clip);

        // Trigger the "Paste" action. This should execute the default receiver.
        boolean result = triggerContextMenuAction(android.R.id.paste);
        assertThat(result).isTrue();
        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testPaste_customReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy text to the clipboard.
        ClipData clip = ClipData.newPlainText("test", "y");
        copyToClipboard(clip);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/plain");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the "Paste" action and assert that the custom receiver was executed.
        triggerContextMenuAction(android.R.id.paste);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), richContentDataEq(clip, SOURCE_CLIPBOARD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testPaste_customReceiver_unsupportedMimeType() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy a URI to the clipboard with a MIME type that's not supported by the receiver.
        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("y", null, SAMPLE_CONTENT_URI));
        copyToClipboard(clip);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/plain", "video/avi");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the "Paste" action and assert that the custom receiver was not executed.
        triggerContextMenuAction(android.R.id.paste);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testPasteAsPlainText_noCustomReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy HTML to the clipboard.
        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        copyToClipboard(clip);

        // Trigger the "Paste as plain text" action. This should execute the platform paste
        // handling, so the content should be inserted according to whatever behavior is implemented
        // in the OS version that's running.
        boolean result = triggerContextMenuAction(android.R.id.pasteAsPlainText);
        assertThat(result).isTrue();
        assertTextAndCursorPosition("x*y*z", 4);
    }

    @UiThreadTest
    @Test
    public void testPasteAsPlainText_customReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy text to the clipboard.
        ClipData clip = ClipData.newPlainText("test", "y");
        copyToClipboard(clip);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/plain");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the "Paste as plain text" action and assert that the custom receiver was
        // executed.
        triggerContextMenuAction(android.R.id.pasteAsPlainText);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                richContentDataEq(clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Trigger the IME's commitContent() call and assert its outcome.
        boolean result = triggerImeCommitContent("image/png");
        assertThat(result).isFalse();
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_customReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        // Note: We expect 2 calls to getSupportedMimeTypes() -- one from onCreateInputConnection()
        // to populate EditorInfo.contentMimeTypes and one from commitContent().
        triggerImeCommitContent("image/png");
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(2)).getSupportedMimeTypes(eq(mTextView));
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), richContentDataEq(clip, SOURCE_INPUT_METHOD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call and assert that the custom receiver was not
        // executed.
        triggerImeCommitContent("video/mp4");
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(2)).getSupportedMimeTypes(eq(mTextView));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_customReceiver_oldTargetSdk() throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        triggerImeCommitContent("image/png");
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(2)).getSupportedMimeTypes(eq(mTextView));
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), richContentDataEq(clip, SOURCE_INPUT_METHOD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_linkUri() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call with a linkUri and assert receiver extras.
        Uri sampleLinkUri = Uri.parse("http://example.com");
        triggerImeCommitContent("image/png", sampleLinkUri, null);
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                richContentDataEq(clip, SOURCE_INPUT_METHOD, 0, sampleLinkUri, null));
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_opts() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call with opts and assert receiver extras.
        String sampleOptValue = "sampleOptValue";
        triggerImeCommitContent("image/png", null, sampleOptValue);
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                richContentDataEq(clip, SOURCE_INPUT_METHOD, 0, null, sampleOptValue));
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_linkUriAndOpts() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call with a linkUri & opts and assert receiver extras.
        Uri sampleLinkUri = Uri.parse("http://example.com");
        String sampleOptValue = "sampleOptValue";
        triggerImeCommitContent("image/png", sampleLinkUri, sampleOptValue);
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                richContentDataEq(clip, SOURCE_INPUT_METHOD, 0, sampleLinkUri, sampleOptValue));
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 2);

        // Trigger drop event. This should execute the default receiver.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerDropEvent(clip);
        assertTextAndCursorPosition("yxz", 1);
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_customReceiver() throws Exception {
        initTextViewForEditing("xz", 2);
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(singleton("text/*"));
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger drop event and assert that the custom receiver was executed.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerDropEvent(clip);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), richContentDataEq(clip, SOURCE_DRAG_AND_DROP, 0));
        verifyNoMoreInteractions(mMockReceiver);
        // Note: The cursor is moved to the location of the drop before calling the receiver.
        assertTextAndCursorPosition("xz", 0);
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 2);
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(singleton("text/*"));
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger drop event and assert that the custom receiver was not executed.
        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("y", null, SAMPLE_CONTENT_URI));
        triggerDropEvent(clip);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verifyNoMoreInteractions(mMockReceiver);
        // Note: The cursor is moved to the location of the drop before calling the receiver.
        assertTextAndCursorPosition("yxz", 1);
    }

    @UiThreadTest
    @Test
    public void testAutofill_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Trigger autofill. This should execute the default receiver.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerAutofill(clip);
        assertTextAndCursorPosition("y", 1);
    }

    @UiThreadTest
    @Test
    public void testAutofill_customReceiver() throws Exception {
        initTextViewForEditing("xz", 1);
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(singleton("text/*"));
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger autofill and assert that the custom receiver was executed.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerAutofill(clip);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), richContentDataEq(clip, SOURCE_AUTOFILL, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testAutofill_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(singleton("text/*"));
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        // Trigger autofill and assert that the custom receiver was not executed.
        // Note: We expect 2 calls to getSupportedMimeTypes(). The first call is to check whether
        // the custom callback supports the MIME type of the clip. Since it doesn't, the default
        // callback is executed; this calls setText() which triggers onCreateInputConnection()
        // which reads the supported MIME types of the custom callback.
        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("y", null, SAMPLE_CONTENT_URI));
        triggerAutofill(clip);
        verify(mMockReceiver, times(2)).getSupportedMimeTypes(eq(mTextView));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("y", 1);
    }

    @UiThreadTest
    @Test
    public void testAutofill_oldTargetSdk() throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("xz", 1);

        // Try autofill with text. This should fill the field.
        CharSequence text = "abc";
        triggerAutofill(text);
        assertTextAndCursorPosition("abc", 3);

        // Try autofill with a ClipData. This should fill the field.
        ClipData clip = ClipData.newPlainText("test", "xyz");
        triggerAutofill(clip);
        assertTextAndCursorPosition("xyz", 3);
    }

    @UiThreadTest
    @Test
    public void testProcessText_noCustomReceiver() throws Exception {
        initTextViewForEditing("Original text", 0);
        Selection.setSelection(mTextView.getEditableText(), 0, mTextView.getText().length());

        String newText = "Replacement text";
        triggerProcessTextOnActivityResult(newText);
        assertTextAndCursorPosition(newText, newText.length());
    }

    @UiThreadTest
    @Test
    public void testProcessText_customReceiver() throws Exception {
        String originalText = "Original text";
        initTextViewForEditing(originalText, 0);
        Selection.setSelection(mTextView.getEditableText(), 0, originalText.length());
        assertTextAndSelection(originalText, 0, originalText.length());

        Set<String> receiverMimeTypes = Set.of("text/plain");
        when(mMockReceiver.getSupportedMimeTypes(mTextView)).thenReturn(receiverMimeTypes);
        mTextView.setOnReceiveContentCallback(mMockReceiverWrapper);

        String newText = "Replacement text";
        triggerProcessTextOnActivityResult(newText);
        ClipData clip = ClipData.newPlainText("", newText);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes(eq(mTextView));
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), richContentDataEq(clip, SOURCE_PROCESS_TEXT, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndSelection(originalText, 0, originalText.length());
    }


    private void initTextViewForEditing(final String text, final int cursorPosition) {
        mTextView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
        mTextView.setTextIsSelectable(true);
        mTextView.requestFocus();

        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        mTextView.setText(ssb, BufferType.EDITABLE);
        mTextView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        Selection.setSelection(mTextView.getEditableText(), cursorPosition);

        assertWithMessage("TextView should have focus").that(mTextView.hasFocus()).isTrue();
        assertTextAndCursorPosition(text, cursorPosition);
    }

    private void assertTextAndCursorPosition(String expectedText, int cursorPosition) {
        assertTextAndSelection(expectedText, cursorPosition, cursorPosition);
    }

    private void assertTextAndSelection(String expectedText, int start, int end) {
        assertThat(mTextView.getText().toString()).isEqualTo(expectedText);
        int[] expected = new int[]{start, end};
        int[] actual = new int[]{mTextView.getSelectionStart(), mTextView.getSelectionEnd()};
        assertWithMessage("Unexpected selection start/end indexes")
                .that(actual).isEqualTo(expected);
    }

    private void onReceive(final OnReceiveContentCallback<TextView> receiver,
            final ClipData clip, final int source, final int flags) {
        OnReceiveContentCallback.Payload payload =
                new OnReceiveContentCallback.Payload.Builder(clip, source)
                .setFlags(flags)
                .build();
        receiver.onReceiveContent(mTextView, payload);
    }

    private void resetTargetSdk() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
    }

    private void configureAppTargetSdkToR() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.R;
    }

    private void configureAppTargetSdkToS() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.S;
    }

    // This wrapper is used so that we only mock and verify the public callback methods. In addition
    // to the public methods, the OnReceiveContentCallback interface has some hidden default
    // methods; we don't want to mock or assert calls to these helper functions (they are an
    // implementation detail).
    private static class MockReceiverWrapper implements OnReceiveContentCallback<TextView> {
        private final OnReceiveContentCallback<TextView> mMock;

        @SuppressWarnings("unchecked")
        MockReceiverWrapper() {
            this.mMock = Mockito.mock(OnReceiveContentCallback.class);
        }

        public OnReceiveContentCallback<TextView> getMock() {
            return mMock;
        }

        @Override
        public boolean onReceiveContent(TextView view, OnReceiveContentCallback.Payload payload) {
            return mMock.onReceiveContent(view, payload);
        }

        @NonNull
        @Override
        public Set<String> getSupportedMimeTypes(TextView view) {
            return mMock.getSupportedMimeTypes(view);
        }
    }

    private void copyToClipboard(ClipData clip) {
        mClipboardManager.setPrimaryClip(clip);
    }

    private boolean triggerContextMenuAction(final int actionId) {
        return mTextView.onTextContextMenuItem(actionId);
    }

    private boolean triggerImeCommitContent(String mimeType) {
        return triggerImeCommitContent(mimeType, null, null);
    }

    private boolean triggerImeCommitContent(String mimeType, Uri linkUri, String extra) {
        final InputContentInfo contentInfo = new InputContentInfo(
                SAMPLE_CONTENT_URI,
                new ClipDescription("from test", new String[]{mimeType}),
                linkUri);
        final Bundle opts;
        if (extra == null) {
            opts = null;
        } else {
            opts = new Bundle();
            opts.putString(RichContentDataArgumentMatcher.EXTRA_KEY, extra);
        }
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        return ic.commitContent(contentInfo, 0, opts);
    }

    private void triggerAutofill(CharSequence text) {
        mTextView.autofill(AutofillValue.forText(text));
    }

    private void triggerAutofill(ClipData clip) {
        mTextView.autofill(AutofillValue.forRichContent(clip));
    }

    private boolean triggerDropEvent(ClipData clip) {
        DragEvent dropEvent = createDragEvent(DragEvent.ACTION_DROP, mTextView.getX(),
                mTextView.getY(), clip);
        return mTextView.onDragEvent(dropEvent);
    }

    private static DragEvent createDragEvent(int action, float x, float y, ClipData clip) {
        // DragEvent doesn't expose any API for instantiation, so we have to build it from a Parcel.
        Parcel dest = Parcel.obtain();
        dest.writeInt(action);
        dest.writeFloat(x);
        dest.writeFloat(y);
        dest.writeInt(0); // Result
        dest.writeInt(1); // ClipData
        clip.writeToParcel(dest, 0);
        dest.writeInt(1); // ClipDescription
        clip.getDescription().writeToParcel(dest, 0);
        dest.writeInt(0); // IDragAndDropPermissions
        dest.setDataPosition(0);
        return DragEvent.CREATOR.createFromParcel(dest);
    }

    private void triggerProcessTextOnActivityResult(CharSequence replacementText) {
        Intent data = new Intent();
        data.putExtra(Intent.EXTRA_PROCESS_TEXT, replacementText);
        mTextView.onActivityResult(TextView.PROCESS_TEXT_REQUEST_CODE, Activity.RESULT_OK, data);
    }

    private static OnReceiveContentCallback.Payload richContentDataEq(@NonNull ClipData clip,
            int source, int flags) {
        return argThat(new RichContentDataArgumentMatcher(clip, source, flags, null, null));
    }

    private static OnReceiveContentCallback.Payload richContentDataEq(@NonNull ClipData clip,
            int source, int flags, Uri linkUri, String extra) {
        return argThat(new RichContentDataArgumentMatcher(clip, source, flags, linkUri, extra));
    }

    private static class RichContentDataArgumentMatcher implements
            ArgumentMatcher<OnReceiveContentCallback.Payload> {
        public static final String EXTRA_KEY = "testExtra";

        @NonNull private final ClipData mClip;
        private final int mSource;
        private final int mFlags;
        @Nullable private final Uri mLinkUri;
        @Nullable private final String mExtra;

        private RichContentDataArgumentMatcher(@NonNull ClipData clip, int source, int flags,
                @Nullable Uri linkUri, @Nullable String extra) {
            mClip = clip;
            mSource = source;
            mFlags = flags;
            mLinkUri = linkUri;
            mExtra = extra;
        }

        @Override
        public boolean matches(OnReceiveContentCallback.Payload actual) {
            ClipData.Item expectedItem = mClip.getItemAt(0);
            ClipData.Item actualItem = actual.getClip().getItemAt(0);
            return Objects.equals(expectedItem.getText(), actualItem.getText())
                    && Objects.equals(expectedItem.getUri(), actualItem.getUri())
                    && mSource == actual.getSource()
                    && mFlags == actual.getFlags()
                    && Objects.equals(mLinkUri, actual.getLinkUri())
                    && extrasMatch(actual.getExtras());
        }

        private boolean extrasMatch(Bundle actualExtras) {
            if (mExtra == null) {
                return actualExtras == null;
            }
            String actualExtraValue = actualExtras.getString(EXTRA_KEY);
            return Objects.equals(mExtra, actualExtraValue);
        }
    }
}
