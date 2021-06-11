/*
 * Copyright (C) 2021 The Android Open Source Project
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
/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.ScrollCaptureTarget;
import android.view.View;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

/**
 * Tests of {@link ScrollCaptureTarget}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ScrollCaptureTargetTest {
    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    MockView mTargetView;
    MockScrollCaptureCallback mCallback;

    @Before
    public void setUp() {
        mTargetView = new MockView(InstrumentationRegistry.getInstrumentation().getTargetContext());
        mCallback = new MockScrollCaptureCallback();
    }

    @Test
    public void testConstructor_requiresNonNullTarget() {
        mExpectedException.expect(NullPointerException.class);
        new ScrollCaptureTarget(null, new Rect(1, 2, 3, 4), new Point(5, 6), mCallback);
    }

    @Test
    public void testConstructor_requiresNonNullVisibleRect() {
        mExpectedException.expect(NullPointerException.class);
        new ScrollCaptureTarget(mTargetView, null, new Point(5, 6), mCallback);
    }

    @Test
    public void testConstructor_requiresNonNullPosition() {
        mExpectedException.expect(NullPointerException.class);
        new ScrollCaptureTarget(mTargetView, new Rect(1, 2, 3, 4), null, mCallback);
    }

    @Test
    public void testConstructor_requiresNonNullCallback() {
        mExpectedException.expect(NullPointerException.class);
        new ScrollCaptureTarget(mTargetView, new Rect(1, 2, 3, 4), new Point(5, 6), null);
    }

    @Test
    public void testScrollBounds() {
        mTargetView.setLeftTopRightBottom(0, 0, 10, 10);

        ScrollCaptureTarget target = new ScrollCaptureTarget(mTargetView,
                new Rect(0, 0, 10, 10), new Point(5, 5), mCallback);

        // null until populated during search
        assertNull(target.getScrollBounds());

        // populated during scroll capture search
        target.setScrollBounds(new Rect(-5, -5, 15, 15));
        // value stored is the intersection of the provided value and view's size
        assertEquals(new Rect(0, 0, 10, 10), target.getScrollBounds());

        // empty rect is valid as opt-out
        target.setScrollBounds(new Rect());
        assertTrue("getScrollBounds() should return an empty rect",
                target.getScrollBounds().isEmpty());

        // as is null
        target.setScrollBounds(null);
        assertNull(target.getScrollBounds());
    }

    @Test
    public void testContainingView() {
        mTargetView.setLeftTopRightBottom(1, 2, 3, 4);

        ScrollCaptureTarget target = new ScrollCaptureTarget(mTargetView,
                new Rect(1, 2, 3, 4), new Point(5, 6), mCallback);
        assertEquals(mTargetView, target.getContainingView());
    }

    @Test
    public void testLocalVisibleRect() {
        mTargetView.setLeftTopRightBottom(1, 2, 3, 4);

        ScrollCaptureTarget target = new ScrollCaptureTarget(mTargetView,
                new Rect(1, 2, 3, 4), new Point(5, 6), mCallback);
        assertEquals(new Rect(1, 2, 3, 4), target.getLocalVisibleRect());
    }

    @Test
    public void testPositionInWindow() {
        mTargetView.setLeftTopRightBottom(1, 2, 3, 4);

        ScrollCaptureTarget target = new ScrollCaptureTarget(mTargetView,
                new Rect(1, 2, 3, 4), new Point(5, 6), mCallback);
        assertEquals(new Point(5, 6), target.getPositionInWindow());

        mTargetView.setLeftTopRightBottom(100, 100, 200, 200);

        target.updatePositionInWindow();
        assertEquals(new Point(100, 100), target.getPositionInWindow());
    }

    @Test
    public void testCallback() {
        mTargetView.setLeftTopRightBottom(1, 2, 3, 4);

        ScrollCaptureTarget target = new ScrollCaptureTarget(mTargetView,
                new Rect(1, 2, 3, 4), new Point(5, 6), mCallback);
        assertEquals(mCallback, target.getCallback());
    }

    @Test
    public void testHint() {
        mTargetView.setScrollCaptureHint(View.SCROLL_CAPTURE_HINT_INCLUDE);
        mTargetView.setLeftTopRightBottom(1, 2, 3, 4);

        ScrollCaptureTarget target = new ScrollCaptureTarget(mTargetView,
                new Rect(1, 2, 3, 4), new Point(5, 6), mCallback);
        assertEquals(View.SCROLL_CAPTURE_HINT_INCLUDE, target.getHint());
    }
}
