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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FocusFinderTest {
    private FocusFinder mFocusFinder;
    private ViewGroup mLayout;
    private Button mTopLeft;
    private Button mTopRight;
    private Button mBottomLeft;
    private Button mBottomRight;

    @Rule
    public ActivityTestRule<FocusFinderCtsActivity> mActivityRule =
            new ActivityTestRule<>(FocusFinderCtsActivity.class);

    @Before
    public void setup() {
        FocusFinderCtsActivity activity = mActivityRule.getActivity();

        mFocusFinder = FocusFinder.getInstance();
        mLayout = activity.layout;
        mTopLeft = activity.topLeftButton;
        mTopRight = activity.topRightButton;
        mBottomLeft = activity.bottomLeftButton;
        mBottomRight = activity.bottomRightButton;
        mTopLeft.setNextFocusLeftId(View.NO_ID);
        mTopRight.setNextFocusLeftId(View.NO_ID);
        mBottomLeft.setNextFocusLeftId(View.NO_ID);
        mBottomRight.setNextFocusLeftId(View.NO_ID);
    }

    @Test
    public void testGetInstance() {
        assertNotNull(mFocusFinder);
    }

    @Test
    public void testFindNextFocus() {
        /*
         * Go clockwise around the buttons from the top left searching for focus.
         *
         * +---+---+
         * | 1 | 2 |
         * +---+---+
         * | 3 | 4 |
         * +---+---+
         */
        verifyNextFocus(mTopLeft, View.FOCUS_RIGHT, mTopRight);
        verifyNextFocus(mTopRight, View.FOCUS_DOWN, mBottomRight);
        verifyNextFocus(mBottomRight, View.FOCUS_LEFT, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_UP, mTopLeft);

        verifyNextFocus(null, View.FOCUS_RIGHT, mTopLeft);
        verifyNextFocus(null, View.FOCUS_DOWN, mTopLeft);
        verifyNextFocus(null, View.FOCUS_LEFT, mBottomRight);
        verifyNextFocus(null, View.FOCUS_UP, mBottomRight);
    }

    private void verifyNextFocus(View currentFocus, int direction, View expectedNextFocus) {
        View actualNextFocus = mFocusFinder.findNextFocus(mLayout, currentFocus, direction);
        assertEquals(expectedNextFocus, actualNextFocus);
    }

    @Test
    public void testFindNextFocusFromRect() {
        /*
         * Create a small rectangle on the border between the top left and top right buttons.
         *
         * +---+---+
         * |  [ ]  |
         * +---+---+
         * |   |   |
         * +---+---+
         */
        Rect rect = new Rect();
        mTopLeft.getDrawingRect(rect);
        rect.offset(mTopLeft.getWidth() / 2, 0);
        rect.inset(mTopLeft.getWidth() / 4, mTopLeft.getHeight() / 4);

        verifytNextFocusFromRect(rect, View.FOCUS_LEFT, mTopLeft);
        verifytNextFocusFromRect(rect, View.FOCUS_RIGHT, mTopRight);

        /*
         * Create a small rectangle on the border between the top left and bottom left buttons.
         *
         * +---+---+
         * |   |   |
         * +[ ]+---+
         * |   |   |
         * +---+---+
         */
        mTopLeft.getDrawingRect(rect);
        rect.offset(0, mTopRight.getHeight() / 2);
        rect.inset(mTopLeft.getWidth() / 4, mTopLeft.getHeight() / 4);

        verifytNextFocusFromRect(rect, View.FOCUS_UP, mTopLeft);
        verifytNextFocusFromRect(rect, View.FOCUS_DOWN, mBottomLeft);
    }

    private void verifytNextFocusFromRect(Rect rect, int direction, View expectedNextFocus) {
        View actualNextFocus = mFocusFinder.findNextFocusFromRect(mLayout, rect, direction);
        assertEquals(expectedNextFocus, actualNextFocus);
    }

    @Test
    public void testFindNearestTouchable() {
        /*
         * Table layout with two rows and coordinates are relative to those parent rows.
         * Lines outside the box signify touch points used in the tests.
         *      |
         *   +---+---+
         *   | 1 | 2 |--
         *   +---+---+
         * --| 3 | 4 |
         *   +---+---+
         *         |
         */

        // 1
        int x = mTopLeft.getWidth() / 2 - 5;
        int y = 0;
        int[] deltas = new int[2];
        View view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_DOWN, deltas);
        assertEquals(mTopLeft, view);
        assertEquals(0, deltas[0]);
        assertEquals(0, deltas[1]);

        // 2
        deltas = new int[2];
        x = mTopRight.getRight();
        y = mTopRight.getBottom() / 2;
        view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_LEFT, deltas);
        assertEquals(mTopRight, view);
        assertEquals(-1, deltas[0]);
        assertEquals(0, deltas[1]);

        // 3
        deltas = new int[2];
        x = 0;
        y = mTopLeft.getBottom() + mBottomLeft.getHeight() / 2;
        view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_RIGHT, deltas);
        assertEquals(mBottomLeft, view);
        assertEquals(0, deltas[0]);
        assertEquals(0, deltas[1]);

        // 4
        deltas = new int[2];
        x = mBottomRight.getRight();
        y = mTopRight.getBottom() + mBottomRight.getBottom();
        view = mFocusFinder.findNearestTouchable(mLayout, x, y, View.FOCUS_UP, deltas);
        assertEquals(mBottomRight, view);
        assertEquals(0, deltas[0]);
        assertEquals(-1, deltas[1]);
    }

    @Test
    public void testFindNextAndPrevFocusAvoidingChain() {
        mBottomRight.setNextFocusForwardId(mBottomLeft.getId());
        mBottomLeft.setNextFocusForwardId(mTopRight.getId());
        // Follow the chain
        verifyNextFocus(mBottomRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mTopRight, View.FOCUS_BACKWARD, mBottomLeft);
        verifyNextFocus(mBottomLeft, View.FOCUS_BACKWARD, mBottomRight);

        // Now go to the one not in the chain
        verifyNextFocus(mTopRight, View.FOCUS_FORWARD, mTopLeft);
        verifyNextFocus(mBottomRight, View.FOCUS_BACKWARD, mTopLeft);

        // Now go back to the top of the chain
        verifyNextFocus(mTopLeft, View.FOCUS_FORWARD, mBottomRight);
        verifyNextFocus(mTopLeft, View.FOCUS_BACKWARD, mTopRight);

        // Now make the chain a circle -- this is the pathological case
        mTopRight.setNextFocusForwardId(mBottomRight.getId());
        // Fall back to the next one in a chain.
        verifyNextFocus(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mTopLeft, View.FOCUS_BACKWARD, mBottomRight);

        //Now do branching focus changes
        mTopRight.setNextFocusForwardId(View.NO_ID);
        mBottomRight.setNextFocusForwardId(mTopRight.getId());
        verifyNextFocus(mBottomRight, View.FOCUS_FORWARD, mTopRight);
        verifyNextFocus(mBottomLeft, View.FOCUS_FORWARD, mTopRight);
        // From the tail, it jumps out of the chain
        verifyNextFocus(mTopRight, View.FOCUS_FORWARD, mTopLeft);

        // Back from the head of a tree goes out of the tree
        // We don't know which is the head of the focus chain since it is branching.
        View prevFocus1 = mFocusFinder.findNextFocus(mLayout, mBottomLeft, View.FOCUS_BACKWARD);
        View prevFocus2 = mFocusFinder.findNextFocus(mLayout, mBottomRight, View.FOCUS_BACKWARD);
        assertTrue(prevFocus1 == mTopLeft || prevFocus2 == mTopLeft);

        // From outside, it chooses an arbitrary head of the chain
        View nextFocus = mFocusFinder.findNextFocus(mLayout, mTopLeft, View.FOCUS_FORWARD);
        assertTrue(nextFocus == mBottomRight || nextFocus == mBottomLeft);

        // Going back from the tail of the split chain, it chooses an arbitrary head
        nextFocus = mFocusFinder.findNextFocus(mLayout, mTopRight, View.FOCUS_BACKWARD);
        assertTrue(nextFocus == mBottomRight || nextFocus == mBottomLeft);
    }

    // Tests for finding new cluster don't look at geometrical properties of the views. For them,
    // only tab order is important, which is mTopLeft, mTopRight, mBottomLeft. mBottomRight isn't
    // used.
    private void verifyNextCluster(View currentCluster, int direction, View expectedNextCluster) {
        View actualNextCluster = mFocusFinder.findNextKeyboardNavigationCluster(
                mLayout, currentCluster, direction);
        assertEquals(expectedNextCluster, actualNextCluster);
    }

    @Test
    public void testNoClusters() {
        // No views are marked as clusters, so next cluster is always null.
        verifyNextCluster(mTopRight, View.FOCUS_FORWARD, null);
        verifyNextCluster(mTopRight, View.FOCUS_BACKWARD, null);
    }

    @Test
    public void testFindNextCluster() {
        // Cluster navigation from all possible starting points in all directions.
        mTopLeft.setKeyboardNavigationCluster(true);
        mTopRight.setKeyboardNavigationCluster(true);
        mBottomLeft.setKeyboardNavigationCluster(true);

        verifyNextCluster(null, View.FOCUS_FORWARD, mTopLeft);
        verifyNextCluster(mTopLeft, View.FOCUS_FORWARD, mTopRight);
        verifyNextCluster(mTopRight, View.FOCUS_FORWARD, mBottomLeft);
        verifyNextCluster(mBottomLeft, View.FOCUS_FORWARD, mLayout);
        verifyNextCluster(mBottomRight, View.FOCUS_FORWARD, mLayout);

        verifyNextCluster(null, View.FOCUS_BACKWARD, mBottomLeft);
        verifyNextCluster(mTopLeft, View.FOCUS_BACKWARD, mLayout);
        verifyNextCluster(mTopRight, View.FOCUS_BACKWARD, mTopLeft);
        verifyNextCluster(mBottomLeft, View.FOCUS_BACKWARD, mTopRight);
        verifyNextCluster(mBottomRight, View.FOCUS_BACKWARD, mLayout);
    }
}
