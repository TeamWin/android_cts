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

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

@SmallTest
public class ProgressBarTest extends ActivityInstrumentationTestCase2<ProgressBarCtsActivity> {
    private Instrumentation mInstrumentation;
    private ProgressBarCtsActivity mActivity;
    private ProgressBar mProgressBar;
    private ProgressBar mProgressBarHorizontal;

    public ProgressBarTest() {
        super("android.widget.cts", ProgressBarCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mProgressBar = (ProgressBar) mActivity.findViewById(R.id.progress);
        mProgressBarHorizontal = (ProgressBar) mActivity.findViewById(R.id.progress_horizontal);
    }

    public void testConstructor() {
        new ProgressBar(mActivity);

        new ProgressBar(mActivity, null);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyle);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleHorizontal);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleInverse);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleLarge);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleLargeInverse);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleSmall);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleSmallInverse);

        new ProgressBar(mActivity, null, android.R.attr.progressBarStyleSmallTitle);

        new ProgressBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_ProgressBar);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Horizontal);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Large);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Large_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Small);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Small_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_ProgressBar_Small_Title);

        new ProgressBar(mActivity, null, 0, android.R.style.Widget_Material_Light_ProgressBar);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Horizontal);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Large);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Large_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Small);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Small_Inverse);

        new ProgressBar(mActivity, null, 0,
                android.R.style.Widget_Material_Light_ProgressBar_Small_Title);
    }

    @UiThreadTest
    public void testSetIndeterminate() {
        assertTrue(mProgressBar.isIndeterminate());

        mProgressBar.setIndeterminate(true);
        assertTrue(mProgressBar.isIndeterminate());

        mProgressBar.setIndeterminate(false);
        // because default is Indeterminate only progressBar, can't change the status
        assertTrue(mProgressBar.isIndeterminate());

        assertFalse(mProgressBarHorizontal.isIndeterminate());

        mProgressBarHorizontal.setIndeterminate(true);
        assertTrue(mProgressBarHorizontal.isIndeterminate());

        mProgressBarHorizontal.setIndeterminate(false);
        assertFalse(mProgressBarHorizontal.isIndeterminate());
    }

    @UiThreadTest
    public void testAccessIndeterminateDrawable() {
        // set IndeterminateDrawable
        // normal value
        MockDrawable mockDrawable = new MockDrawable();
        mProgressBar.setIndeterminateDrawable(mockDrawable);
        assertSame(mockDrawable, mProgressBar.getIndeterminateDrawable());
        assertFalse(mockDrawable.hasCalledDraw());
        mProgressBar.draw(new Canvas());
        assertTrue(mockDrawable.hasCalledDraw());

        // exceptional value
        mProgressBar.setIndeterminateDrawable(null);
        assertNull(mProgressBar.getIndeterminateDrawable());
    }

    @UiThreadTest
    public void testAccessProgressDrawable() {
        // set ProgressDrawable
        // normal value
        MockDrawable mockDrawable = new MockDrawable();
        mProgressBarHorizontal.setProgressDrawable(mockDrawable);
        assertSame(mockDrawable, mProgressBarHorizontal.getProgressDrawable());
        assertFalse(mockDrawable.hasCalledDraw());
        mProgressBarHorizontal.draw(new Canvas());
        assertTrue(mockDrawable.hasCalledDraw());

        // exceptional value
        mProgressBarHorizontal.setProgressDrawable(null);
        assertNull(mProgressBarHorizontal.getProgressDrawable());
    }

    @UiThreadTest
    public void testAccessProgress() {
        assertEquals(0, mProgressBarHorizontal.getProgress());

        final int maxProgress = mProgressBarHorizontal.getMax();
        // set Progress
        // normal value
        mProgressBarHorizontal.setProgress(maxProgress >> 1);
        assertEquals(maxProgress >> 1, mProgressBarHorizontal.getProgress());

        // exceptional values
        mProgressBarHorizontal.setProgress(-1);
        assertEquals(0, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(maxProgress + 1);
        assertEquals(maxProgress, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(Integer.MAX_VALUE);
        assertEquals(maxProgress, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(0, true);
        assertEquals(0, mProgressBarHorizontal.getProgress());

        // when in indeterminate mode
        mProgressBarHorizontal.setIndeterminate(true);
        mProgressBarHorizontal.setProgress(maxProgress >> 1);
        assertEquals(0, mProgressBarHorizontal.getProgress());
    }

    @UiThreadTest
    public void testAccessSecondaryProgress() {
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());

        final int maxProgress = mProgressBarHorizontal.getMax();
        // set SecondaryProgress
        // normal value
        mProgressBarHorizontal.setSecondaryProgress(maxProgress >> 1);
        assertEquals(maxProgress >> 1, mProgressBarHorizontal.getSecondaryProgress());

        // exceptional value
        mProgressBarHorizontal.setSecondaryProgress(-1);
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.setSecondaryProgress(maxProgress + 1);
        assertEquals(maxProgress, mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.setSecondaryProgress(Integer.MAX_VALUE);
        assertEquals(maxProgress, mProgressBarHorizontal.getSecondaryProgress());

        // when in indeterminate mode
        mProgressBarHorizontal.setIndeterminate(true);
        mProgressBarHorizontal.setSecondaryProgress(maxProgress >> 1);
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());
    }

    @UiThreadTest
    public void testIncrementProgressBy() {
        // normal value
        int increment = 1;
        int oldProgress = mProgressBarHorizontal.getProgress();
        mProgressBarHorizontal.incrementProgressBy(increment);
        assertEquals(oldProgress + increment, mProgressBarHorizontal.getProgress());

        increment = mProgressBarHorizontal.getMax() >> 1;
        oldProgress = mProgressBarHorizontal.getProgress();
        mProgressBarHorizontal.incrementProgressBy(increment);
        assertEquals(oldProgress + increment, mProgressBarHorizontal.getProgress());

        // exceptional values
        mProgressBarHorizontal.setProgress(0);
        mProgressBarHorizontal.incrementProgressBy(Integer.MAX_VALUE);
        assertEquals(mProgressBarHorizontal.getMax(), mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setProgress(0);
        mProgressBarHorizontal.incrementProgressBy(Integer.MIN_VALUE);
        assertEquals(0, mProgressBarHorizontal.getProgress());
    }

    @UiThreadTest
    public void testIncrementSecondaryProgressBy() {
        // normal value
        int increment = 1;
        int oldSecondaryProgress = mProgressBarHorizontal.getSecondaryProgress();
        mProgressBarHorizontal.incrementSecondaryProgressBy(increment);
        assertEquals(oldSecondaryProgress + increment,
                mProgressBarHorizontal.getSecondaryProgress());

        increment = mProgressBarHorizontal.getMax() >> 1;
        oldSecondaryProgress = mProgressBarHorizontal.getSecondaryProgress();
        mProgressBarHorizontal.incrementSecondaryProgressBy(increment);
        assertEquals(oldSecondaryProgress + increment,
                mProgressBarHorizontal.getSecondaryProgress());

        // exceptional values
        mProgressBarHorizontal.setSecondaryProgress(0);
        mProgressBarHorizontal.incrementSecondaryProgressBy(Integer.MAX_VALUE);
        assertEquals(mProgressBarHorizontal.getMax(),
                mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.setSecondaryProgress(0);
        mProgressBarHorizontal.incrementSecondaryProgressBy(Integer.MIN_VALUE);
        assertEquals(0, mProgressBarHorizontal.getSecondaryProgress());
    }

    @UiThreadTest
    public void testAccessInterpolator() {
        // default should be LinearInterpolator
        assertTrue(mProgressBar.getInterpolator() instanceof LinearInterpolator);

        Interpolator interpolator = new AccelerateDecelerateInterpolator();
        mProgressBar.setInterpolator(interpolator);
        assertEquals(interpolator, mProgressBar.getInterpolator());

        mProgressBar.setInterpolator(mActivity, android.R.anim.accelerate_interpolator);
        assertTrue(mProgressBar.getInterpolator() instanceof AccelerateInterpolator);
    }

    @UiThreadTest
    public void testSetVisibility() {
        // set visibility
        // normal value
        int visibility = View.VISIBLE;
        mProgressBarHorizontal.setVisibility(visibility);
        assertEquals(visibility, mProgressBarHorizontal.getVisibility());

        visibility = View.GONE;
        mProgressBarHorizontal.setVisibility(visibility);
        assertEquals(visibility, mProgressBarHorizontal.getVisibility());

        // exceptional value
        visibility = 0xfffffff5; // -11
        int mask = 0x0000000C; // View.VISIBILITY_MASK
        int expected = (mProgressBarHorizontal.getVisibility() & ~mask) | (visibility & mask);
        mProgressBarHorizontal.setVisibility(visibility);
        assertEquals(expected, mProgressBarHorizontal.getVisibility());

        visibility = 0x7fffffff; // Integer.MAX_VALUE;
        expected = (mProgressBarHorizontal.getVisibility() & ~mask) | (visibility & mask);
        mProgressBarHorizontal.setVisibility(Integer.MAX_VALUE);
        assertEquals(expected, mProgressBarHorizontal.getVisibility());
    }

    @UiThreadTest
    public void testInvalidateDrawable() {
        MockProgressBar mockProgressBar = new MockProgressBar(mActivity);

        MockDrawable mockDrawable1 = new MockDrawable();
        MockDrawable mockDrawable2 = new MockDrawable();
        mockProgressBar.setBackgroundDrawable(mockDrawable1);

        mockProgressBar.invalidateDrawable(mockDrawable1);
        assertTrue(mockProgressBar.hasCalledInvalidate());

        mockProgressBar.reset();
        mockProgressBar.invalidateDrawable(mockDrawable2);
        assertFalse(mockProgressBar.hasCalledInvalidate());

        mockProgressBar.setIndeterminateDrawable(mockDrawable1);
        mockProgressBar.setProgressDrawable(mockDrawable2);
    }

    @UiThreadTest
    public void testPostInvalidate() {
        MockProgressBar mockProgressBar = new MockProgressBar(mActivity);
        mockProgressBar.postInvalidate();
    }

    @UiThreadTest
    public void testAccessMax() {
        // set Progress
        int progress = 10;
        mProgressBarHorizontal.setProgress(progress);

        // normal value
        int max = progress + 1;
        mProgressBarHorizontal.setMax(max);
        assertEquals(max, mProgressBarHorizontal.getMax());
        assertEquals(progress, mProgressBarHorizontal.getProgress());

        max = progress - 1;
        mProgressBarHorizontal.setMax(max);
        assertEquals(max, mProgressBarHorizontal.getMax());
        assertEquals(max, mProgressBarHorizontal.getProgress());

        // exceptional values
        mProgressBarHorizontal.setMax(-1);
        assertEquals(0, mProgressBarHorizontal.getMax());
        assertEquals(0, mProgressBarHorizontal.getProgress());

        mProgressBarHorizontal.setMax(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, mProgressBarHorizontal.getMax());
        assertEquals(0, mProgressBarHorizontal.getProgress());
    }

    public void testOnDraw() {
        // Do not test, it's controlled by View. Implementation details
    }

    @UiThreadTest
    public void testProgressTint() {
        ProgressBar tintedProgressBar = (ProgressBar) mActivity.findViewById(R.id.progress_tint);

        assertEquals("Progress tint inflated correctly",
                Color.WHITE, tintedProgressBar.getProgressTintList().getDefaultColor());
        assertEquals("Progress tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getProgressTintMode());

        assertEquals("Progress background tint inflated correctly",
                Color.WHITE, tintedProgressBar.getProgressBackgroundTintList().getDefaultColor());
        assertEquals("Progress background tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getProgressBackgroundTintMode());

        assertEquals("Secondary progress tint inflated correctly",
                Color.WHITE, tintedProgressBar.getSecondaryProgressTintList().getDefaultColor());
        assertEquals("Secondary progress tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getSecondaryProgressTintMode());

        MockDrawable progress = new MockDrawable();

        mProgressBar.setProgressDrawable(progress);
        assertFalse("No progress tint applied by default", progress.hasCalledSetTint());

        mProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        assertFalse("Progress background tint not applied when layer missing",
                progress.hasCalledSetTint());

        mProgressBar.setSecondaryProgressTintList(ColorStateList.valueOf(Color.WHITE));
        assertFalse("Secondary progress tint not applied when layer missing",
                progress.hasCalledSetTint());

        mProgressBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        assertTrue("Progress tint applied when setProgressTintList() called after setProgress()",
                progress.hasCalledSetTint());

        mProgressBar.setProgressBackgroundTintMode(PorterDuff.Mode.DST_OVER);
        assertEquals(PorterDuff.Mode.DST_OVER, mProgressBar.getProgressBackgroundTintMode());

        mProgressBar.setSecondaryProgressTintMode(PorterDuff.Mode.DST_IN);
        assertEquals(PorterDuff.Mode.DST_IN, mProgressBar.getSecondaryProgressTintMode());

        mProgressBar.setProgressTintMode(PorterDuff.Mode.DST_ATOP);
        assertEquals(PorterDuff.Mode.DST_ATOP, mProgressBar.getProgressTintMode());

        progress.reset();
        mProgressBar.setProgressDrawable(null);
        mProgressBar.setProgressDrawable(progress);
        assertTrue("Progress tint applied when setProgressTintList() called before setProgress()",
                progress.hasCalledSetTint());
    }

    @UiThreadTest
    public void testIndeterminateTint() {
        ProgressBar tintedProgressBar =
                (ProgressBar) mActivity.findViewById(R.id.indeterminate_tint);

        assertEquals("Indeterminate tint inflated correctly",
                Color.WHITE, tintedProgressBar.getIndeterminateTintList().getDefaultColor());
        assertEquals("Indeterminate tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedProgressBar.getIndeterminateTintMode());

        MockDrawable indeterminate = new MockDrawable();

        mProgressBar.setIndeterminateDrawable(indeterminate);
        assertFalse("No indeterminate tint applied by default", indeterminate.hasCalledSetTint());

        mProgressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        assertTrue("Indeterminate tint applied when setIndeterminateTintList() called after "
                + "setIndeterminate()", indeterminate.hasCalledSetTint());

        mProgressBar.setIndeterminateTintMode(PorterDuff.Mode.LIGHTEN);
        assertEquals(PorterDuff.Mode.LIGHTEN, mProgressBar.getIndeterminateTintMode());

        indeterminate.reset();
        mProgressBar.setIndeterminateDrawable(null);
        mProgressBar.setIndeterminateDrawable(indeterminate);
        assertTrue("Indeterminate tint applied when setIndeterminateTintList() called before "
                + "setIndeterminate()", indeterminate.hasCalledSetTint());
    }

    private class MockDrawable extends Drawable {
        private boolean mCalledDraw = false;
        private boolean mCalledSetTint = false;

        @Override
        public void draw(Canvas canvas) {
            mCalledDraw = true;
        }

        @Override
        public int getOpacity() {
            return 0;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public void setTintList(ColorStateList tint) {
            super.setTintList(tint);
            mCalledSetTint = true;
        }

        public boolean hasCalledSetTint() {
            return mCalledSetTint;
        }

        public boolean hasCalledDraw() {
            return mCalledDraw;
        }

        public void reset() {
            mCalledDraw = false;
            mCalledSetTint = false;
        }

    }

    public void testOnMeasure() {
        // onMeasure() is implementation details, do NOT test
    }

    public void testOnSizeChange() {
        // onSizeChanged() is implementation details, do NOT test
    }

    @UiThreadTest
    public void testVerifyDrawable() {
        MockProgressBar mockProgressBar = new MockProgressBar(mActivity);
        assertTrue(mockProgressBar.verifyDrawable(null));

        Drawable d1 = mActivity.getResources().getDrawable(R.drawable.blue);
        Drawable d2 = mActivity.getResources().getDrawable(R.drawable.red);
        Drawable d3 = mActivity.getResources().getDrawable(R.drawable.yellow);

        mockProgressBar.setBackgroundDrawable(d1);
        assertTrue(mockProgressBar.verifyDrawable(null));
        assertTrue(mockProgressBar.verifyDrawable(d1));
        assertFalse(mockProgressBar.verifyDrawable(d2));
        assertFalse(mockProgressBar.verifyDrawable(d3));

        mockProgressBar.setIndeterminateDrawable(d2);
        assertTrue(mockProgressBar.verifyDrawable(null));
        assertTrue(mockProgressBar.verifyDrawable(d1));
        assertTrue(mockProgressBar.verifyDrawable(d2));
        assertFalse(mockProgressBar.verifyDrawable(d3));

        mockProgressBar.setProgressDrawable(d3);
        assertFalse(mockProgressBar.verifyDrawable(null));
        assertTrue(mockProgressBar.verifyDrawable(d1));
        assertTrue(mockProgressBar.verifyDrawable(d2));
        assertTrue(mockProgressBar.verifyDrawable(d3));
    }

    public void testDrawableStateChanged() {
        // drawableStateChanged() is implementation details, do NOT test
    }

    @UiThreadTest
    public void testOnSaveAndRestoreInstanceState() {
        int oldProgress = 1;
        int oldSecondaryProgress = mProgressBarHorizontal.getMax() - 1;
        mProgressBarHorizontal.setProgress(oldProgress);
        mProgressBarHorizontal.setSecondaryProgress(oldSecondaryProgress);
        assertEquals(oldProgress, mProgressBarHorizontal.getProgress());
        assertEquals(oldSecondaryProgress, mProgressBarHorizontal.getSecondaryProgress());

        Parcelable state = mProgressBarHorizontal.onSaveInstanceState();

        int newProgress = 2;
        int newSecondaryProgress = mProgressBarHorizontal.getMax() - 2;
        mProgressBarHorizontal.setProgress(newProgress);
        mProgressBarHorizontal.setSecondaryProgress(newSecondaryProgress);
        assertEquals(newProgress, mProgressBarHorizontal.getProgress());
        assertEquals(newSecondaryProgress, mProgressBarHorizontal.getSecondaryProgress());

        mProgressBarHorizontal.onRestoreInstanceState(state);
        assertEquals(oldProgress, mProgressBarHorizontal.getProgress());
        assertEquals(oldSecondaryProgress, mProgressBarHorizontal.getSecondaryProgress());
    }

    /*
     * Mock class for ProgressBar to test protected methods
     */
    private class MockProgressBar extends ProgressBar {
        private boolean mCalledInvalidate = false;

        /**
         * @param context
         */
        public MockProgressBar(Context context) {
            super(context);
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return super.verifyDrawable(who);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
        }

        @Override
        protected synchronized void onMeasure(int widthMeasureSpec,
                int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected synchronized void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
        }

        public void invalidate(int l, int t, int r, int b) {
            mCalledInvalidate = true;
            super.invalidate(l, t, r, b);
        }

        public void invalidate() {
            mCalledInvalidate = true;
            super.invalidate();
        }

        public boolean hasCalledInvalidate() {
            return mCalledInvalidate;
        }

        public void reset() {
            mCalledInvalidate = false;
        }
    }
}
