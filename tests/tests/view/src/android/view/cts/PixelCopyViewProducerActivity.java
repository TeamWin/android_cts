/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.view.cts;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver.OnDrawListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PixelCopyViewProducerActivity extends Activity implements OnDrawListener {
    private View mContent;
    private CountDownLatch mFence = new CountDownLatch(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContent = new ColoredGrid(this);
        setContentView(mContent);
        mContent.getViewTreeObserver().addOnDrawListener(this);
    }

    private static final class ColoredGrid extends View {
        private Paint mPaint = new Paint();
        private Rect mRect = new Rect();

        public ColoredGrid(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            mRect.set(0, 0, cx, cy);
            mPaint.setColor(Color.RED);
            canvas.drawRect(mRect, mPaint);

            mRect.set(cx, 0, getWidth(), cy);
            mPaint.setColor(Color.GREEN);
            canvas.drawRect(mRect, mPaint);

            mRect.set(0, cy, cx, getHeight());
            mPaint.setColor(Color.BLUE);
            canvas.drawRect(mRect, mPaint);

            mRect.set(cx, cy, getWidth(), getHeight());
            mPaint.setColor(Color.BLACK);
            canvas.drawRect(mRect, mPaint);
        }
    }

    @Override
    public void onDraw() {
        mContent.post(() -> {
            mContent.getViewTreeObserver().removeOnDrawListener(PixelCopyViewProducerActivity.this);
            mFence.countDown();
        });
    }

    public void waitForFirstDrawCompleted(int timeout, TimeUnit unit)
            throws InterruptedException {
        mFence.await(timeout, unit);
    }
}
