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

import static android.widget.RichContentReceiver.FLAG_CONVERT_TO_PLAIN_TEXT;
import static android.widget.RichContentReceiver.SOURCE_AUTOFILL;
import static android.widget.RichContentReceiver.SOURCE_CLIPBOARD;
import static android.widget.RichContentReceiver.SOURCE_DRAG_AND_DROP;
import static android.widget.RichContentReceiver.SOURCE_INPUT_METHOD;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static java.util.Collections.singleton;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.QwertyKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.text.style.UnderlineSpan;
import android.view.DragEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.widget.RichContentReceiver;
import android.widget.TextView;
import android.widget.TextView.BufferType;

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
import org.mockito.Mockito;

import java.util.Set;

/**
 * Test {@link RichContentReceiver} and its integration with {@link TextView}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextViewRichContentReceiverTest {
    @Rule
    public ActivityTestRule<TextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewCtsActivity.class);

    private Activity mActivity;
    private TextView mTextView;
    private RichContentReceiver<TextView> mDefaultReceiver;
    private MockReceiverWrapper mMockReceiverWrapper;
    private RichContentReceiver<TextView> mMockReceiver;
    private ClipboardManager mClipboardManager;

    @Before
    public void before() {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mTextView = mActivity.findViewById(R.id.textview_text);
        mDefaultReceiver = TextView.DEFAULT_RICH_CONTENT_RECEIVER;

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
    // Tests to verify TextView APIs/accessors/defaults related to RichContentReceiver.
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testTextView_getAndSetRichContentReceiver() throws Exception {
        // Verify that the default receiver is non null.
        assertThat(TextView.DEFAULT_RICH_CONTENT_RECEIVER).isNotNull();

        // Verify that by default the getter returns the default receiver.
        assertThat(mTextView.getRichContentReceiver()).isSameAs(
                TextView.DEFAULT_RICH_CONTENT_RECEIVER);

        // Verify that after setting a custom receiver, the getter returns it.
        mTextView.setRichContentReceiver(mMockReceiverWrapper);
        assertThat(mTextView.getRichContentReceiver()).isSameAs(mMockReceiverWrapper);

        // Verify that setting a null receiver is not allowed.
        try {
            mTextView.setRichContentReceiver(null);
            Assert.fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            assertThat(mTextView.getRichContentReceiver()).isSameAs(mMockReceiverWrapper);
        }
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
    public void testTextView_onCreateInputConnection_defaultReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Call onCreateInputConnection() and assert that contentMimeTypes is set to the MIME types
        // of the default receiver.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        String[] receiverMimeTypes = mDefaultReceiver.getSupportedMimeTypes().toArray(
                new String[0]);
        assertThat(editorInfo.contentMimeTypes).isEqualTo(receiverMimeTypes);
        assertThat(receiverMimeTypes).isEqualTo(new String[]{"text/*"});
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_oldTargetSdk() throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/plain", "image/png", "video/mp4");
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(receiverMimeTypes);
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Call onCreateInputConnection() and assert that contentMimeTypes is not populated with the
        // MIME types of the receiver.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        verifyNoMoreInteractions(mMockReceiver);
        assertThat(editorInfo.contentMimeTypes).isNull();
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_customReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/plain", "image/png", "video/mp4");
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(receiverMimeTypes);
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Call onCreateInputConnection() and assert that contentMimeTypes is set from the receiver.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        verify(mMockReceiver, times(1)).getSupportedMimeTypes();
        verifyNoMoreInteractions(mMockReceiver);
        assertThat(editorInfo.contentMimeTypes).isEqualTo(receiverMimeTypes.toArray(new String[0]));
    }

    @UiThreadTest
    @Test
    public void testTextView_getAutofillType_defaultReceiver() throws Exception {
        initTextViewForEditing("", 0);
        assertThat(mTextView.getAutofillType()).isEqualTo(View.AUTOFILL_TYPE_TEXT);
    }

    @UiThreadTest
    @Test
    public void testTextView_getAutofillType_customReceiver() throws Exception {
        initTextViewForEditing("", 0);

        // Setup: Configure the receiver to a mock impl that supports text and images.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(receiverMimeTypes);
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Assert that the autofill type returned is "rich content".
        assertThat(mTextView.getAutofillType()).isEqualTo(View.AUTOFILL_TYPE_RICH_CONTENT);
        verify(mMockReceiver, times(1)).getSupportedMimeTypes();
        verifyNoMoreInteractions(mMockReceiver);
    }

    @UiThreadTest
    @Test
    public void testTextView_getAutofillType_oldTargetSdk() throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("", 0);

        // Setup: Configure the receiver to a mock impl that supports text and images.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(receiverMimeTypes);
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Assert that the autofill type returned is "text".
        assertThat(mTextView.getAutofillType()).isEqualTo(View.AUTOFILL_TYPE_TEXT);
        verifyNoMoreInteractions(mMockReceiver);
    }

    // ============================================================================================
    // Tests to verify the behavior of TextView.DEFAULT_RICH_CONTENT_RECEIVER.
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testDefaultReceiver_getSupportedMimeTypes() throws Exception {
        assertThat(mDefaultReceiver.getSupportedMimeTypes()).isEqualTo(singleton("text/*"));
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_text() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "y");
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertThat(result).isTrue();
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

        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertThat(result).isTrue();
        assertTextAndCursorPosition("xhi worldz", 9);
        int spanStart = mTextView.getEditableText().getSpanStart(underlineSpan);
        assertThat(spanStart).isEqualTo(4);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_text_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "y");
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD,
                FLAG_CONVERT_TO_PLAIN_TEXT);

        assertThat(result).isTrue();
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

        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD,
                FLAG_CONVERT_TO_PLAIN_TEXT);

        assertThat(result).isTrue();
        assertTextAndCursorPosition("xhi worldz", 9);
        int spanStart = mTextView.getEditableText().getSpanStart(underlineSpan);
        assertThat(spanStart).isEqualTo(-1);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_html() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertThat(result).isTrue();
        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_html_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD,
                FLAG_CONVERT_TO_PLAIN_TEXT);

        assertThat(result).isTrue();
        assertTextAndCursorPosition("x*y*z", 4);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", "html", null, Uri.parse("content://com.example/path")));
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertThat(result).isTrue();
        assertTextAndCursorPosition("xhtmlz", 5);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_unsupportedMimeType_convertToPlainText()
            throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", "html", null, Uri.parse("content://com.example/path")));
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD,
                FLAG_CONVERT_TO_PLAIN_TEXT);

        assertThat(result).isTrue();
        assertTextAndCursorPosition("xtextz", 5);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_multipleItemsInClipData() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "ONE");
        clip.addItem(new ClipData.Item("TWO"));
        clip.addItem(new ClipData.Item("THREE"));
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertThat(result).isTrue();
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
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertThat(result).isTrue();
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
        boolean result = onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertThat(result).isTrue();
        assertTextAndCursorPosition("hi", 2);
    }

    // ============================================================================================
    // Tests to verify that the RichContentReceiver callback is invoked for all the appropriate user
    // interactions:
    // * Paste from clipboard ("Paste" and "Paste as plain text" actions)
    // * Content insertion from IME
    // * Drag and drop
    // * Autofill
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testPaste_defaultReceiver() throws Exception {
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
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger the "Paste" action and assert that the custom receiver was executed.
        triggerContextMenuAction(android.R.id.paste);
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_CLIPBOARD), eq(0));
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
                new ClipData.Item("text", null, Uri.parse("content://com.example/path")));
        copyToClipboard(clip);

        // Setup: Configure the receiver to a mock impl.
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger the "Paste" action and assert that the custom receiver was executed. This
        // confirms that the receiver is invoked (ie, given a chance to handle the content) even if
        // the MIME type of the content is not one of the receiver's supported MIME types.
        triggerContextMenuAction(android.R.id.paste);
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_CLIPBOARD), eq(0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testPasteAsPlainText_defaultReceiver() throws Exception {
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
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger the "Paste as plain text" action and assert that the custom receiver was
        // executed.
        triggerContextMenuAction(android.R.id.pasteAsPlainText);
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class),
                eq(SOURCE_CLIPBOARD), eq(FLAG_CONVERT_TO_PLAIN_TEXT));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_defaultReceiver() throws Exception {
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
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(receiverMimeTypes);
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        triggerImeCommitContent("image/png");
        verify(mMockReceiver, times(1)).getSupportedMimeTypes();
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_INPUT_METHOD), eq(0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(receiverMimeTypes);
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        // This confirms that the receiver is invoked (ie, given a chance to handle the content)
        // even if the MIME type of the content is not one of the receiver's supported MIME types.
        triggerImeCommitContent("video/mp4");
        verify(mMockReceiver, times(1)).getSupportedMimeTypes();
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_INPUT_METHOD), eq(0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_oldTargetSdk() throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        Set<String> receiverMimeTypes = Set.of("text/*", "image/*");
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(receiverMimeTypes);
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        triggerImeCommitContent("image/png");
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_defaultReceiver() throws Exception {
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
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger drop event. This should execute the default receiver.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerDropEvent(clip);
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_DRAG_AND_DROP), eq(0));
        verifyNoMoreInteractions(mMockReceiver);
        // Note: The cursor is moved to the location of the drop before calling the receiver.
        assertTextAndCursorPosition("xz", 0);
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 2);
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(singleton("text/*"));
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger drop event and assert that the custom receiver was executed. This confirms that
        // the receiver is invoked (ie, is given a chance to handle the content) even if the MIME
        // type of the content is not one of the receiver's supported MIME types.
        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", null, Uri.parse("content://com.example/path")));
        triggerDropEvent(clip);
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_DRAG_AND_DROP), eq(0));
        verifyNoMoreInteractions(mMockReceiver);
        // Note: The cursor is moved to the location of the drop before calling the receiver.
        assertTextAndCursorPosition("xz", 0);
    }

    @UiThreadTest
    @Test
    public void testAutofill_defaultReceiver() throws Exception {
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
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger autofill and assert that the custom receiver was executed.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerAutofill(clip);
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_AUTOFILL), eq(0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testAutofill_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);
        when(mMockReceiver.getSupportedMimeTypes()).thenReturn(singleton("text/*"));
        mTextView.setRichContentReceiver(mMockReceiverWrapper);

        // Trigger autofill and assert that the custom receiver was executed. This confirms that the
        // receiver is invoked (ie, is given a chance to handle the content) even if the MIME type
        // of the content is not one of the receiver's supported MIME types.
        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", null, Uri.parse("content://com.example/path")));
        triggerAutofill(clip);
        verify(mMockReceiver, times(1)).onReceive(
                eq(mTextView), any(ClipData.class), eq(SOURCE_AUTOFILL), eq(0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
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

    private boolean onReceive(final RichContentReceiver<TextView> receiver, final ClipData clip,
            final int source, final int flags) {
        return receiver.onReceive(mTextView, clip, source, flags);
    }

    private void resetTargetSdk() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
    }

    private void configureAppTargetSdkToR() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.R;
    }

    // TODO(b/150719306): Currently Build.VERSION_CODES.R is still set to
    //  Build.VERSION_CODES.CUR_DEVELOPMENT so we need this workaround. Once the R version code is
    //  assigned, remove this method.
    private void configureAppTargetSdkToS() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT + 1;
    }

    // This wrapper is used so that we only mock and verify the public callback methods. In addition
    // to the public methods, the RichContentReceiver interface has some hidden default methods;
    // we don't want to mock or assert calls to these helper functions (they are an implementation
    // detail).
    private static class MockReceiverWrapper implements RichContentReceiver<TextView> {
        private final RichContentReceiver<TextView> mMock;

        @SuppressWarnings("unchecked")
        MockReceiverWrapper() {
            this.mMock = Mockito.mock(RichContentReceiver.class);
        }

        public RichContentReceiver<TextView> getMock() {
            return mMock;
        }

        @Override
        public boolean onReceive(TextView view, ClipData clip, int source, int flags) {
            return mMock.onReceive(view, clip, source, flags);
        }

        @Override
        public Set<String> getSupportedMimeTypes() {
            return mMock.getSupportedMimeTypes();
        }
    }

    private void copyToClipboard(ClipData clip) {
        mClipboardManager.setPrimaryClip(clip);
    }

    private boolean triggerContextMenuAction(final int actionId) {
        return mTextView.onTextContextMenuItem(actionId);
    }

    private boolean triggerImeCommitContent(String mimeType) {
        final InputContentInfo contentInfo = new InputContentInfo(
                Uri.parse("content://com.example/path"),
                new ClipDescription("from test", new String[]{mimeType}),
                Uri.parse("https://example.com"));
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        return ic.commitContent(contentInfo, 0, null);
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
}
