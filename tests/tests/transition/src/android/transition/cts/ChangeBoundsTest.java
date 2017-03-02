/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.transition.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.content.res.Resources;
import android.graphics.Rect;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.util.TypedValue;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ChangeBoundsTest extends BaseTransitionTest {
    private static final int SMALL_SQUARE_SIZE_DP = 10;
    private static final int LARGE_SQUARE_SIZE_DP = 30;
    private static final int SMALL_OFFSET_DP = 2;

    ChangeBounds mChangeBounds;

    @Override
    @Before
    public void setup() {
        super.setup();
        resetChangeBoundsTransition();
    }

    private void resetChangeBoundsTransition() {
        mListener = mock(Transition.TransitionListener.class);
        mChangeBounds = new ChangeBounds();
        mChangeBounds.setDuration(400);
        mChangeBounds.addListener(mListener);
        mTransition = mChangeBounds;
    }

    @Test
    public void testBasicChangeBounds() throws Throwable {
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before checking intermediate values:
        Thread.sleep(150);
        validateNormalIntermediate();
        waitForEnd(400);

        validateInScene6();
    }

    @Test
    public void testResizeClip() throws Throwable {
        assertEquals(false, mChangeBounds.getResizeClip());
        mChangeBounds.setResizeClip(true);
        assertEquals(true, mChangeBounds.getResizeClip());
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before checking intermediate values:
        Thread.sleep(150);
        validateClippedIntermediate();
        waitForEnd(400);

        validateInScene6();
    }

    @Test
    public void testResizeClipSmaller() throws Throwable {
        mChangeBounds.setResizeClip(true);
        enterScene(R.layout.scene6);

        validateInScene6();

        startTransition(R.layout.scene1);

        // now delay for at least a few frames before checking intermediate values:
        Thread.sleep(150);
        validateClippedIntermediate();
        waitForEnd(400);

        validateInScene1();
    }

    @Test
    public void testInterruptSameDestination() throws Throwable {
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before interrupting the transition
        Thread.sleep(150);
        resetChangeBoundsTransition();
        startTransition(R.layout.scene6);

        assertFalse(isRestartingAnimation());
        waitForEnd(500);
        validateInScene6();
    }

    @Test
    public void testInterruptSameDestinationResizeClip() throws Throwable {
        mChangeBounds.setResizeClip(true);
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before interrupting the transition
        Thread.sleep(150);

        resetChangeBoundsTransition();
        mChangeBounds.setResizeClip(true);
        startTransition(R.layout.scene6);

        assertFalse(isRestartingAnimation());
        assertFalse(isRestartingClip());
        waitForEnd(500);
        validateInScene6();
    }

    @Test
    public void testInterruptWithReverse() throws Throwable {
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before reversing
        Thread.sleep(150);
        // reverse the transition back to scene1
        resetChangeBoundsTransition();
        startTransition(R.layout.scene1);

        assertFalse(isRestartingAnimation());
        waitForEnd(500);
        validateInScene1();
    }

    @Test
    public void testInterruptWithReverseResizeClip() throws Throwable {
        mChangeBounds.setResizeClip(true);
        enterScene(R.layout.scene1);

        validateInScene1();

        startTransition(R.layout.scene6);

        // now delay for at least a few frames before reversing
        Thread.sleep(150);

        // reverse the transition back to scene1
        resetChangeBoundsTransition();
        mChangeBounds.setResizeClip(true);
        startTransition(R.layout.scene1);

        assertFalse(isRestartingAnimation());
        assertFalse(isRestartingClip());
        waitForEnd(500);
        validateInScene1();
    }

    private boolean isRestartingAnimation() {
        View red = mActivity.findViewById(R.id.redSquare);
        View green = mActivity.findViewById(R.id.greenSquare);
        Resources resources = mActivity.getResources();
        float closestDistance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SMALL_OFFSET_DP, resources.getDisplayMetrics());
        return red.getTop() < closestDistance || green.getTop() < closestDistance;
    }

    private boolean isRestartingClip() {
        Resources resources = mActivity.getResources();
        float smallDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SMALL_SQUARE_SIZE_DP + SMALL_OFFSET_DP, resources.getDisplayMetrics());
        float largeDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LARGE_SQUARE_SIZE_DP - SMALL_OFFSET_DP, resources.getDisplayMetrics());

        View red = mActivity.findViewById(R.id.redSquare);
        Rect redClip = red.getClipBounds();
        View green = mActivity.findViewById(R.id.greenSquare);
        Rect greenClip = green.getClipBounds();
        return redClip == null || redClip.width() < smallDim || redClip.width() > largeDim ||
                greenClip == null || greenClip.width() < smallDim || greenClip.width() > largeDim;
    }

    private void validateInScene1() {
        validateViewPlacement(R.id.redSquare, R.id.greenSquare, SMALL_SQUARE_SIZE_DP);
    }

    private void validateInScene6() {
        validateViewPlacement(R.id.greenSquare, R.id.redSquare, LARGE_SQUARE_SIZE_DP);
    }

    private void validateViewPlacement(int topViewResource, int bottomViewResource, int dim) {
        Resources resources = mActivity.getResources();
        float expectedDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dim,
                resources.getDisplayMetrics());
        View aboveSquare = mActivity.findViewById(topViewResource);
        assertEquals(0, aboveSquare.getLeft());
        assertEquals(0, aboveSquare.getTop());
        assertTrue(aboveSquare.getRight() != 0);
        final int aboveSquareBottom = aboveSquare.getBottom();
        assertTrue(aboveSquareBottom != 0);

        View belowSquare = mActivity.findViewById(bottomViewResource);
        assertEquals(0, belowSquare.getLeft());
        assertWithinAPixel(aboveSquareBottom, belowSquare.getTop());
        assertWithinAPixel(aboveSquareBottom + aboveSquare.getHeight(),
                belowSquare.getBottom());
        assertWithinAPixel(aboveSquare.getRight(), belowSquare.getRight());

        assertWithinAPixel(expectedDim, aboveSquare.getHeight());
        assertWithinAPixel(expectedDim, aboveSquare.getWidth());
        assertWithinAPixel(expectedDim, belowSquare.getHeight());
        assertWithinAPixel(expectedDim, belowSquare.getWidth());

        assertNull(aboveSquare.getClipBounds());
        assertNull(belowSquare.getClipBounds());
    }

    private void validateIntermediatePosition() {
        Resources resources = mActivity.getResources();
        float smallDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SMALL_SQUARE_SIZE_DP, resources.getDisplayMetrics());
        float largeDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LARGE_SQUARE_SIZE_DP, resources.getDisplayMetrics());

        View redSquare = mActivity.findViewById(R.id.redSquare);
        View greenSquare = mActivity.findViewById(R.id.greenSquare);
        assertTrue(redSquare.getTop() != 0);
        assertTrue(greenSquare.getTop() != 0);
        assertNotWithinAPixel(smallDim, redSquare.getTop());
        assertNotWithinAPixel(largeDim, redSquare.getTop());
        assertNotWithinAPixel(smallDim, greenSquare.getTop());
        assertNotWithinAPixel(largeDim, greenSquare.getTop());
    }

    private void validateClippedIntermediate() {
        validateIntermediatePosition();
        Resources resources = mActivity.getResources();
        float largeDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LARGE_SQUARE_SIZE_DP, resources.getDisplayMetrics());
        View redSquare = mActivity.findViewById(R.id.redSquare);
        View greenSquare = mActivity.findViewById(R.id.greenSquare);

        assertWithinAPixel(largeDim, redSquare.getWidth());
        assertWithinAPixel(largeDim, redSquare.getHeight());
        assertWithinAPixel(largeDim, greenSquare.getWidth());
        assertWithinAPixel(largeDim, greenSquare.getHeight());

        assertNotNull(redSquare.getClipBounds());
        assertNotNull(greenSquare.getClipBounds());
    }

    private void validateNormalIntermediate() {
        validateIntermediatePosition();
        Resources resources = mActivity.getResources();
        float smallDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                SMALL_SQUARE_SIZE_DP, resources.getDisplayMetrics());
        float largeDim = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LARGE_SQUARE_SIZE_DP, resources.getDisplayMetrics());
        View redSquare = mActivity.findViewById(R.id.redSquare);
        View greenSquare = mActivity.findViewById(R.id.greenSquare);
        assertNotWithinAPixel(smallDim, redSquare.getWidth());
        assertNotWithinAPixel(smallDim, redSquare.getHeight());
        assertNotWithinAPixel(largeDim, redSquare.getWidth());
        assertNotWithinAPixel(largeDim, redSquare.getHeight());

        assertNotWithinAPixel(smallDim, greenSquare.getWidth());
        assertNotWithinAPixel(smallDim, greenSquare.getHeight());
        assertNotWithinAPixel(largeDim, greenSquare.getWidth());
        assertNotWithinAPixel(largeDim, greenSquare.getHeight());

        assertNull(redSquare.getClipBounds());
        assertNull(greenSquare.getClipBounds());
    }

    private static boolean isWithinAPixel(float expectedDim, int dim) {
        return (Math.abs(dim - expectedDim) <= 1);
    }

    private static void assertWithinAPixel(float expectedDim, int dim) {
        assertTrue("Expected dimension to be within one pixel of "
                + expectedDim + ", but was " + dim, isWithinAPixel(expectedDim, dim));
    }

    private static void assertNotWithinAPixel(float expectedDim, int dim) {
        assertTrue("Expected dimension to not be within one pixel of "
                + expectedDim + ", but was " + dim, !isWithinAPixel(expectedDim, dim));
    }
}

