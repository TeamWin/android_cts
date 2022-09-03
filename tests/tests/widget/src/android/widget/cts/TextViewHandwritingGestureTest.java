/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.DeleteGesture;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InsertGesture;
import android.view.inputmethod.SelectGesture;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.function.IntConsumer;

@RunWith(AndroidJUnit4.class)
public class TextViewHandwritingGestureTest {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 1000;
    private static final String DEFAULT_TEXT = ""
            // Line 0 (characters 0 to 10)
            + "XXX X XXX\n"
            // Line 1 (characters 11 to 15)
            + "XXXXX";
    // All characters used in DEFAULT_TEXT have width 10em ('X' and ' ') in the test font used.
    // Font size is set to 1f, so that 10em is 10px.
    private static final float CHAR_WIDTH_PX = 10;
    private static final String INSERT_TEXT = "insert";
    private static final String FALLBACK_TEXT = "fallback";

    private EditText mEditText;
    private int[] mLocationOnScreen;

    private int mResult = InputConnection.HANDWRITING_GESTURE_RESULT_UNKNOWN;
    private final IntConsumer mResultConsumer = value -> mResult = value;

    @Rule
    public ActivityTestRule<TextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewCtsActivity.class);

    @Before
    public void setup() {
        // The test font includes the following characters:
        // U+0020 ( ): 10em
        // U+0058 (X): 10em
        Typeface typeface = Typeface.createFromAsset(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets(),
                "LayoutTestFont.ttf");

        mEditText = new EditText(mActivityRule.getActivity());
        mEditText.setTypeface(typeface);
        // Make 1em equal to 1px.
        // Then all characters used in DEFAULT_TEXT ('X' and ' ') will have width 10px.
        mEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.0f);
        mEditText.setLineSpacing(/* add= */ 20f, /* mult= */ 1f);
        mEditText.setText(DEFAULT_TEXT);

        mEditText.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        mEditText.layout(0, 0, WIDTH, HEIGHT);
        mEditText.setLayoutParams(new ViewGroup.LayoutParams(WIDTH, HEIGHT));

        mLocationOnScreen = mEditText.getLocationOnScreen();
        mLocationOnScreen[0] += mEditText.getTotalPaddingLeft();
        mLocationOnScreen[1] += mEditText.getTotalPaddingTop();
    }

    @Test
    @ApiTest(apis = "android.widget.TextView#onCreateInputConnection")
    public void onCreateInputConnection_reportsSupportedGestures() {
        EditorInfo editorInfo = new EditorInfo();
        mEditText.onCreateInputConnection(editorInfo);

        List<Class<? extends HandwritingGesture>> gestures =
                editorInfo.getSupportedHandwritingGestures();
        assertThat(gestures).containsAtLeast(
                SelectGesture.class, DeleteGesture.class, InsertGesture.class);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_character() {
        float char1HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char2HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char1HorizontalCenter - 1f, char2HorizontalCenter + 1f] covers the
        // centers of characters 1 and 2.
        RectF area = new RectF(
                char1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertSelectGesturePerformed(1, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_word() {
        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of words 1 and 2.
        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertSelectGesturePerformed(4, 9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_betweenWords_shouldFallback() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertFallbackTextInserted(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_noFallback_shouldFail() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(
                area, HandwritingGesture.GRANULARITY_WORD, /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_character() {
        float char1HorizontalCenter = 1.5f * CHAR_WIDTH_PX;
        float char2HorizontalCenter = 2.5f * CHAR_WIDTH_PX;
        // Horizontal range [char1HorizontalCenter - 1f, char2HorizontalCenter + 1f] covers
        // characters 1 and 2.
        RectF area = new RectF(
                char1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertDeleteGesturePerformed(1, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_word() {
        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter - 1f, word2HorizontalCenter + 1f] covers the
        // centers of words 1 and 2.
        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertDeleteGesturePerformed(4, 9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_betweenWords_shouldFallback() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertFallbackTextInserted(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_noFallback_shouldFail() {
        mEditText.setSelection(2);

        // Word 1 spans from offset 4 to offset 5
        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        // Word 2 spans from offset 6 to offset 9
        float word2HorizontalCenter = (6 + 9) / 2f * CHAR_WIDTH_PX;
        // Horizontal range [word1HorizontalCenter + 1f, word2HorizontalCenter - 1f] does not cover
        // the centers of any words since it is between the centers of these consecutive words.
        RectF area = new RectF(
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineTop(0),
                word2HorizontalCenter - 1f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(
                area, HandwritingGesture.GRANULARITY_WORD, /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_firstLine() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        performInsertGesture(
                new PointF(3 * CHAR_WIDTH_PX + 2f, mEditText.getLayout().getLineTop(0) + 1f));

        assertInsertGesturePerformed(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_secondLine() {
        // The point is closest to offset 3 with horizontal position 3 * CHAR_WIDTH_PX.
        performInsertGesture(
                new PointF(3 * CHAR_WIDTH_PX - 4f, mEditText.getLayout().getLineTop(1) + 1f));

        assertInsertGesturePerformed(13);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_aboveFirstLine_shouldFallback() {
        mEditText.setSelection(3);

        performInsertGesture(new PointF(32f, mEditText.getLayout().getLineTop(0) - 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_betweenLines_shouldFallback() {
        mEditText.setSelection(4);

        // Due to the additional line spacing, this point is in the space between the two lines.
        performInsertGesture(new PointF(32f, mEditText.getLayout().getLineTop(1) - 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 4);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_belowLastLine_shouldFallback() {
        mEditText.setSelection(11);

        performInsertGesture(new PointF(32f, mEditText.getLayout().getLineBottom(1) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 11);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_leftOfLine_shouldFallback() {
        mEditText.setSelection(7);

        performInsertGesture(new PointF(-1f, mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 7);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_rightOfLine_shouldFallback() {
        mEditText.setSelection(6);

        // The first line has 9 characters, so it ends at horizontal position 9 * CHAR_WIDTH_PX.
        performInsertGesture(
                new PointF(9 * CHAR_WIDTH_PX + 5f, mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 6);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_noFallback_shouldFail() {
        mEditText.setSelection(6);

        performInsertGesture(
                new PointF(-1f, mEditText.getLayout().getLineTop(0) - 1f),
                /* setFallbackText= */ false);

        assertGestureFailure(/* initialCursorPosition= */ 6);
    }

    private void performSelectGesture(RectF area, int granularity) {
        performSelectGesture(area, granularity, /* setFallbackText= */ true);
    }

    private void performSelectGesture(RectF area, int granularity, boolean setFallbackText) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new SelectGesture.Builder()
                .setSelectionArea(area)
                .setGranularity(granularity)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void assertSelectGesturePerformed(int start, int end) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        // Check that the text has not changed.
        assertThat(mEditText.getText().toString()).isEqualTo(DEFAULT_TEXT);
        assertThat(mEditText.getSelectionStart()).isEqualTo(start);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(end);
    }

    private void performDeleteGesture(RectF area, int granularity) {
        performDeleteGesture(area, granularity, /* setFallbackText= */ true);
    }

    private void performDeleteGesture(RectF area, int granularity, boolean setFallbackText) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new DeleteGesture.Builder()
                .setDeletionArea(area)
                .setGranularity(granularity)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void assertDeleteGesturePerformed(int start, int end) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        assertThat(mEditText.getText().toString())
                .isEqualTo(DEFAULT_TEXT.substring(0, start) + DEFAULT_TEXT.substring(end));
        assertThat(mEditText.getSelectionStart()).isEqualTo(start);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(start);
    }

    private void performInsertGesture(PointF point) {
        performInsertGesture(point, /* setFallbackText= */ true);
    }

    private void performInsertGesture(PointF point, boolean setFallbackText) {
        point.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new InsertGesture.Builder()
                .setInsertionPoint(point)
                .setTextToInsert(INSERT_TEXT)
                .setFallbackText(setFallbackText ? FALLBACK_TEXT : null)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void assertInsertGesturePerformed(int offset) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, offset) + INSERT_TEXT + DEFAULT_TEXT.substring(offset));
        assertThat(mEditText.getSelectionStart()).isEqualTo(offset + INSERT_TEXT.length());
        assertThat(mEditText.getSelectionEnd()).isEqualTo(offset + INSERT_TEXT.length());
    }

    private void assertFallbackTextInserted(int initialCursorPosition) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_FALLBACK);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, initialCursorPosition)
                        + FALLBACK_TEXT
                        + DEFAULT_TEXT.substring(initialCursorPosition));
        assertThat(mEditText.getSelectionStart())
                .isEqualTo(initialCursorPosition + FALLBACK_TEXT.length());
        assertThat(mEditText.getSelectionEnd())
                .isEqualTo(initialCursorPosition + FALLBACK_TEXT.length());
    }

    private void assertGestureFailure(int initialCursorPosition) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_FAILED);
        assertThat(mEditText.getText().toString()).isEqualTo(DEFAULT_TEXT);
        assertThat(mEditText.getSelectionStart()).isEqualTo(initialCursorPosition);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(initialCursorPosition);
    }
}
