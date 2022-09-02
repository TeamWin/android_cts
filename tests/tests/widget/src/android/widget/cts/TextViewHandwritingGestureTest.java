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

@RunWith(AndroidJUnit4.class)
public class TextViewHandwritingGestureTest {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 1000;
    private static final String DEFAULT_TEXT = ""
            // Line 0 has horizontal positions [0, 10, 20, 30, 40, 50, 60, 70, 80, 90]
            + "XXX X XXX\n"
            // Line 1 has horizontal positions [0, 10, 20, 30, 40]
            + "XXXXX";
    private static final String INSERT_TEXT = "insert";
    private static final String FALLBACK_TEXT = "fallback";

    private EditText mEditText;
    private int[] mLocationOnScreen;

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
        // Make 1 em equal to 1 pixel.
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
        // Character 1 on line 0 has center 15.
        // Character 2 on line 0 has center 25.
        RectF area = new RectF(
                14f,
                mEditText.getLayout().getLineTop(0),
                26f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertSelectGesturePerformed(1, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_word() {
        // Word 1 (offset 4 to 5) on line 0 has center 45.
        // Word 2 (offset 6 to 9) on line 0 has center 75.
        RectF area = new RectF(
                44f,
                mEditText.getLayout().getLineTop(0),
                76f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertSelectGesturePerformed(4, 9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performSelectGesture_betweenWords_shouldFallback() {
        mEditText.setSelection(2);

        // Word 1 (offset 4 to 5) on line 0 has center 45.
        // Word 2 (offset 6 to 9) on line 0 has center 75.
        RectF area = new RectF(
                46f,
                mEditText.getLayout().getLineTop(0),
                74f,
                mEditText.getLayout().getLineBottom(0));
        performSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertFallbackTextInserted(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_character() {
        // Character 1 on line 0 has center 15.
        // Character 2 on line 0 has center 25.
        RectF area = new RectF(
                14f,
                mEditText.getLayout().getLineTop(0),
                26f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertDeleteGesturePerformed(1, 3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_word() {
        // Word 1 (offset 4 to 5) on line 0 has center 45.
        // Word 2 (offset 6 to 9) on line 0 has center 75.
        RectF area = new RectF(
                44f,
                mEditText.getLayout().getLineTop(0),
                76f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertDeleteGesturePerformed(4, 9);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performDeleteGesture_betweenWords_shouldFallback() {
        mEditText.setSelection(2);

        // Word 1 (offset 4 to 5) on line 0 has center 45.
        // Word 2 (offset 6 to 9) on line 0 has center 75.
        RectF area = new RectF(
                46f,
                mEditText.getLayout().getLineTop(0),
                74f,
                mEditText.getLayout().getLineBottom(0));
        performDeleteGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertFallbackTextInserted(/* initialCursorPosition= */ 2);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_firstLine() {
        // Closest to offset 3 with horizontal position 30.
        performInsertGesture(new PointF(32f, mEditText.getLayout().getLineTop(0) + 1f));

        assertInsertGesturePerformed(3);
    }

    @Test
    @ApiTest(apis = "android.view.inputmethod.InputConnection#performHandwritingGesture")
    public void performInsertGesture_secondLine() {
        // Closest to offset 13 with horizontal position 30.
        performInsertGesture(new PointF(26f, mEditText.getLayout().getLineTop(1) + 1f));

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

        performInsertGesture(new PointF(101f, mEditText.getLayout().getLineTop(0) + 1f));

        assertFallbackTextInserted(/* initialCursorPosition= */ 6);
    }

    private void performSelectGesture(RectF area, int granularity) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new SelectGesture.Builder()
                .setSelectionArea(area)
                .setGranularity(granularity)
                .setFallbackText(FALLBACK_TEXT)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, null, null);
    }

    private void assertSelectGesturePerformed(int start, int end) {
        // Check that the text has not changed.
        assertThat(mEditText.getText().toString()).isEqualTo(DEFAULT_TEXT);
        assertThat(mEditText.getSelectionStart()).isEqualTo(start);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(end);
    }

    private void performDeleteGesture(RectF area, int granularity) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new DeleteGesture.Builder()
                .setDeletionArea(area)
                .setGranularity(granularity)
                .setFallbackText(FALLBACK_TEXT)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, null, null);
    }

    private void assertDeleteGesturePerformed(int start, int end) {
        assertThat(mEditText.getText().toString())
                .isEqualTo(DEFAULT_TEXT.substring(0, start) + DEFAULT_TEXT.substring(end));
        assertThat(mEditText.getSelectionStart()).isEqualTo(start);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(start);
    }

    private void performInsertGesture(PointF point) {
        point.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new InsertGesture.Builder()
                .setInsertionPoint(point)
                .setTextToInsert(INSERT_TEXT)
                .setFallbackText(FALLBACK_TEXT)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, null, null);
    }

    private void assertInsertGesturePerformed(int offset) {
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, offset) + INSERT_TEXT + DEFAULT_TEXT.substring(offset));
        assertThat(mEditText.getSelectionStart()).isEqualTo(offset + INSERT_TEXT.length());
        assertThat(mEditText.getSelectionEnd()).isEqualTo(offset + INSERT_TEXT.length());
    }

    private void assertFallbackTextInserted(int initialCursorPosition) {
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, initialCursorPosition)
                        + FALLBACK_TEXT
                        + DEFAULT_TEXT.substring(initialCursorPosition));
        assertThat(mEditText.getSelectionStart())
                .isEqualTo(initialCursorPosition + FALLBACK_TEXT.length());
        assertThat(mEditText.getSelectionEnd())
                .isEqualTo(initialCursorPosition + FALLBACK_TEXT.length());
    }
}
