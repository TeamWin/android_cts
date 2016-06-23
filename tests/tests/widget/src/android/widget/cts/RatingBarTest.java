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
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.RatingBar;

import static org.mockito.Mockito.*;

/**
 * Test {@link RatingBar}.
 */
@SmallTest
public class RatingBarTest extends ActivityInstrumentationTestCase2<RatingBarCtsActivity> {
    private Instrumentation mInstrumentation;
    private RatingBarCtsActivity mActivity;
    private RatingBar mRatingBar;

    public RatingBarTest() {
        super("android.widget.cts", RatingBarCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mRatingBar = (RatingBar) mActivity.findViewById(R.id.ratingbar_constructor);
    }

    public void testConstructor() {
        new RatingBar(mActivity);
        new RatingBar(mActivity, null);
        new RatingBar(mActivity, null, android.R.attr.ratingBarStyle);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_RatingBar);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_RatingBar_Indicator);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_RatingBar_Small);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_RatingBar);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_RatingBar_Indicator);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_RatingBar_Small);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_Light_RatingBar);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_Light_RatingBar_Indicator);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_Light_RatingBar_Small);
    }

    public void testAttributesFromLayout() {
        assertFalse(mRatingBar.isIndicator());
        assertEquals(50, mRatingBar.getNumStars());
        assertEquals(1.2f, mRatingBar.getRating());
        assertEquals(0.2f, mRatingBar.getStepSize());
    }

    public void testAccessOnRatingBarChangeListener() {
        final RatingBar.OnRatingBarChangeListener listener =
                mock(RatingBar.OnRatingBarChangeListener.class);
        mRatingBar.setOnRatingBarChangeListener(listener);
        assertSame(listener, mRatingBar.getOnRatingBarChangeListener());
        verifyZeroInteractions(listener);

        // normal value
        mInstrumentation.runOnMainSync(() -> mRatingBar.setRating(2.2f));
        verify(listener, times(1)).onRatingChanged(mRatingBar, 2.2f, false);

        // exceptional value
        mRatingBar.setOnRatingBarChangeListener(null);
        assertNull(mRatingBar.getOnRatingBarChangeListener());
        mRatingBar.setRating(1.2f);
        verifyNoMoreInteractions(listener);
    }

    public void testAccessIndicator() {
        mInstrumentation.runOnMainSync(() -> mRatingBar.setIsIndicator(true));
        assertTrue(mRatingBar.isIndicator());

        mInstrumentation.runOnMainSync(() -> mRatingBar.setIsIndicator(false));
        assertFalse(mRatingBar.isIndicator());
    }

    public void testAccessNumStars() {
        // set NumStars
        // normal value
        mInstrumentation.runOnMainSync(() -> mRatingBar.setNumStars(20));
        assertEquals(20, mRatingBar.getNumStars());

        // invalid value - the currently set one stays
        mInstrumentation.runOnMainSync(() -> mRatingBar.setNumStars(-10));
        assertEquals(20, mRatingBar.getNumStars());

        mInstrumentation.runOnMainSync(() -> mRatingBar.setNumStars(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, mRatingBar.getNumStars());
    }

    public void testAccessRating() {
        // set Rating
        // normal value
        mInstrumentation.runOnMainSync(() -> mRatingBar.setRating(2.0f));
        assertEquals(2.0f, mRatingBar.getRating());

        // exceptional value
        mInstrumentation.runOnMainSync(() -> mRatingBar.setRating(-2.0f));
        assertEquals(0f, mRatingBar.getRating());

        mInstrumentation.runOnMainSync(() -> mRatingBar.setRating(Float.MAX_VALUE));
        assertEquals((float) mRatingBar.getNumStars(), mRatingBar.getRating());
    }

    public void testSetMax() {
        // normal value
        mInstrumentation.runOnMainSync(() -> mRatingBar.setMax(10));
        assertEquals(10, mRatingBar.getMax());

        mInstrumentation.runOnMainSync(() -> mRatingBar.setProgress(10));

        // exceptional values
        mInstrumentation.runOnMainSync(() -> mRatingBar.setMax(-10));
        assertEquals(10, mRatingBar.getMax());
        assertEquals(10, mRatingBar.getProgress());

        mInstrumentation.runOnMainSync(() -> mRatingBar.setMax(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, mRatingBar.getMax());
    }

    public void testAccessStepSize() {
        // normal value
        mInstrumentation.runOnMainSync(() -> mRatingBar.setStepSize(1.5f));
        final float expectedMax = mRatingBar.getNumStars() / mRatingBar.getStepSize();
        final float expectedProgress = expectedMax / mRatingBar.getMax() * mRatingBar.getProgress();
        assertEquals((int) expectedMax, mRatingBar.getMax());
        assertEquals((int) expectedProgress, mRatingBar.getProgress());
        assertEquals((float) mRatingBar.getNumStars() / (int) (mRatingBar.getNumStars() / 1.5f),
                mRatingBar.getStepSize());

        final int currentMax = mRatingBar.getMax();
        final int currentProgress = mRatingBar.getProgress();
        final float currentStepSize = mRatingBar.getStepSize();
        // exceptional value
        mInstrumentation.runOnMainSync(() -> mRatingBar.setStepSize(-1.5f));
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize());

        mInstrumentation.runOnMainSync(() -> mRatingBar.setStepSize(0f));
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize());

        mInstrumentation.runOnMainSync(
                () -> mRatingBar.setStepSize(mRatingBar.getNumStars() + 0.1f));
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize());

        mInstrumentation.runOnMainSync(() -> mRatingBar.setStepSize(Float.MAX_VALUE));
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize());
    }
}
