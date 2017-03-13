/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VirtualContainerView extends View {

    private static final String TAG = "VirtualContainerView";

    static final String LABEL_CLASS = "my.readonly.view";
    static final String TEXT_CLASS = "my.editable.view";


    private final ArrayList<Line> mLines = new ArrayList<>();
    private final SparseArray<Item> mItems = new SparseArray<>();
    private final AutofillManager mAfm;

    private Line mFocusedLine;

    private Paint mTextPaint;
    private int mTextHeight;
    private int mTopMargin;
    private int mLeftMargin;
    private int mVerticalGap;
    private int mLineLength;
    private int mFocusedColor;
    private int mUnfocusedColor;
    private boolean mSync;

    public VirtualContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mAfm = context.getSystemService(AutofillManager.class);

        mTextPaint = new Paint();

        mUnfocusedColor = Color.BLACK;
        mFocusedColor = Color.RED;
        mTextPaint.setStyle(Style.FILL);
        mTopMargin = 100;
        mLeftMargin = 100;
        mTextHeight = 90;
        mVerticalGap = 10;

        mLineLength = mTextHeight + mVerticalGap;
        mTextPaint.setTextSize(mTextHeight);
        Log.d(TAG, "Text height: " + mTextHeight);
    }

    @Override
    public void autofillVirtual(int id, AutofillValue value) {
        Log.d(TAG, "autofillVirtual: id=" + id + ", value=" + value);
        final Item item = mItems.get(id);
        if (item == null) {
            Log.w(TAG, "No item for id " + id);
            return;
        }
        if (!item.editable) {
            Log.w(TAG, "Item for id " + id + " is not editable: " + item);
            return;
        }
        item.text = value.getTextValue();
        if (item.listener != null) {
            Log.d(TAG, "Notify listener: " + item.text);
            item.listener.onTextChanged(item.text, 0, 0, 0);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Log.d(TAG, "onDraw: " + mLines.size() + " lines; canvas:" + canvas);
        final float x = mLeftMargin;
        float y = mTopMargin + mLineLength;
        for (int i = 0; i < mLines.size(); i++) {
            final Line line = mLines.get(i);
            Log.v(TAG, "Drawing '" + line + "' at " + x + "x" + y);
            mTextPaint.setColor(line.focused ? mFocusedColor : mUnfocusedColor);
            final String text = line.label.text + ":  [" + line.text.text + "]";
            canvas.drawText(text, x, y, mTextPaint);
            line.setBounds(x, y);
            y += mLineLength;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int y = (int) event.getY();
        Log.d(TAG, "You can touch this: y=" + y + ", range=" + mLineLength + ", top=" + mTopMargin);
        int lowerY = mTopMargin;
        int upperY = -1;
        for (int i = 0; i < mLines.size(); i++) {
            upperY = lowerY + mLineLength;
            final Line line = mLines.get(i);
            Log.d(TAG, "Line " + i + " ranges from " + lowerY + " to " + upperY);
            if (lowerY <= y && y <= upperY) {
                if (mFocusedLine != null) {
                    Log.d(TAG, "Removing focus from " + mFocusedLine);
                    mFocusedLine.changeFocus(false);
                }
                Log.d(TAG, "Changing focus to " + line);
                mFocusedLine = line;
                mFocusedLine.changeFocus(true);
                invalidate();
                break;
            }
            lowerY += mLineLength;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onProvideAutofillVirtualStructure(ViewStructure structure, int flags) {
        Log.d(TAG, "onProvideAutofillVirtualStructure(): flags = " + flags);
        structure.setClassName(getClass().getName());
        final int childrenSize = mItems.size();
        int index = structure.addChildCount(childrenSize);
        final String packageName = getContext().getPackageName();
        final String syncMsg = mSync ? "" : " (async)";
        for (int i = 0; i < childrenSize; i++) {
            final Item item = mItems.valueAt(i);
            Log.d(TAG, "Adding new child" + syncMsg + " at index " + index + ": " + item);
            final ViewStructure child = mSync
                    ? structure.newChildForAutofill(index, item.id, 0)
                    : structure.asyncNewChildForAutofill(index, item.id, 0);
            child.setSanitized(item.sanitized);
            index++;
            final String className = item.editable ? TEXT_CLASS : LABEL_CLASS;
            child.setClassName(className);
            child.setId(1000 + index, packageName, "id", item.resourceId);
            child.setText(item.text);
            child.setAutofillValue(AutofillValue.forText(item.text));
            child.setFocused(item.line.focused);
            if (!mSync) {
                Log.d(TAG, "Commiting virtual child");
                child.asyncCommit();
            }
        }
    }

    Line addLine(String labelId, String label, String textId, String text) {
        final Line line = new Line(labelId, label, textId, text);
        Log.d(TAG, "addLine: " + line);
        mLines.add(line);
        mItems.put(line.label.id, line.label);
        mItems.put(line.text.id, line.text);
        return line;
    }

    void setSync(boolean sync) {
        mSync = sync;
    }

    private static int nextId;

    final class Line {

        private final Item label;
        private final Item text;

        private Rect bounds;

        private boolean focused;

        private Line(String labelId, String label, String textId, String text) {
            this.label = new Item(this, ++nextId, labelId, label, false, true);
            this.text = new Item(this, ++nextId, textId, text, true, false);
        }

        void setBounds(float x, float y) {
            int left = (int) x;
            int right = (int) (x + mTextPaint.getTextSize());
            int top = (int) y;
            int bottom = (int) (y + mTextHeight);
            if (bounds == null) {
                bounds = new Rect(left, top, right, bottom);
            } else {
                bounds.set(left, top, right, bottom);
            }
            Log.d(TAG, "setBounds(" + x + ", " + y + "): " + bounds);
        }

        void changeFocus(boolean focused) {
            // TODO(b/33197203, b/33802548): fix bounds
            Log.d(TAG, "changeFocus() on " + text.id + ": " + focused + " bounds: " + bounds);
            this.focused = focused;
            if (focused) {
                mAfm.notifyVirtualViewEntered(VirtualContainerView.this, text.id, bounds);
            } else {
                mAfm.notifyVirtualViewExited(VirtualContainerView.this, text.id);
            }
        }

        void setTextChangedListener(TextWatcher listener) {
            text.listener = listener;
        }

        @Override
        public String toString() {
            return "Label: " + label + " Text: " + text + " Focused: " + focused;
        }

        final class OneTimeLineWatcher implements TextWatcher {
            private final CountDownLatch latch;
            private final CharSequence expected;

            OneTimeLineWatcher(CharSequence expectedValue) {
                this.expected = expectedValue;
                this.latch = new CountDownLatch(1);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                latch.countDown();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            void assertAutoFilled() throws Exception {
                final boolean set = latch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                assertWithMessage("Timeout (%s ms) on Line %s", FILL_TIMEOUT_MS, label)
                        .that(set).isTrue();
                final String actual = text.text.toString();
                assertWithMessage("Wrong auto-fill value on Line %s", label)
                        .that(actual).isEqualTo(expected.toString());
            }
        }
    }

    private static final class Item {
        private final Line line;
        private final int id;
        private final String resourceId;
        private CharSequence text;
        private final boolean editable;
        private final boolean sanitized;
        private TextWatcher listener;

        Item(Line line, int id, String resourceId, CharSequence text, boolean editable,
                boolean sanitized) {
            this.line = line;
            this.id = id;
            this.resourceId = resourceId;
            this.text = text;
            this.editable = editable;
            this.sanitized = sanitized;
        }

        @Override
        public String toString() {
            return id + "/" + resourceId + ": " + text + (editable ? " (editable)" : " (read-only)"
                    + (sanitized ? " (sanitized)" : " (sensitive"));
        }
    }
}
