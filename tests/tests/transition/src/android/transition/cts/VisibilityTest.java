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
package android.transition.cts;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class VisibilityTest extends BaseTransitionTest {
    Visibility mVisibilityTransition;

    public VisibilityTest() {
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mVisibilityTransition = (Visibility) mTransition;
    }

    public void testMode() throws Throwable {
        assertEquals(Visibility.MODE_IN | Visibility.MODE_OUT, mVisibilityTransition.getMode());

        // Should animate in and out
        startTransition(R.layout.scene4);
        assertEquals(1, mListener.endLatch.getCount());
        endTransition();
        waitForEnd(100);

        resetListener();
        mVisibilityTransition.setMode(Visibility.MODE_IN);
        startTransition(R.layout.scene1);
        assertEquals(1, mListener.endLatch.getCount());
        endTransition();
        waitForEnd(100);

        resetListener();
        mVisibilityTransition.setMode(Visibility.MODE_OUT); // now it shouldn't do any animation
        startTransition(R.layout.scene1);
        waitForEnd(0);

        // but it should animate out
        resetListener();
        startTransition(R.layout.scene4);
        assertEquals(1, mListener.endLatch.getCount());
        endTransition();
        waitForEnd(100);

        // switch the mode to only animate in
        resetListener();
        mVisibilityTransition.setMode(Visibility.MODE_IN);
        startTransition(R.layout.scene1);
        assertEquals(1, mListener.endLatch.getCount());
        endTransition();
        waitForEnd(100);

        // but it shouldn't animate out
        startTransition(R.layout.scene1);
        waitForEnd(0);
    }

    public void testIsVisible() throws Throwable {
        assertFalse(mVisibilityTransition.isVisible(null));

        enterScene(R.layout.scene1);
        final View redSquare = mActivity.findViewById(R.id.redSquare);
        TransitionValues visibleValues = new TransitionValues();
        visibleValues.view = redSquare;
        mTransition.captureStartValues(visibleValues);

        assertTrue(mVisibilityTransition.isVisible(visibleValues));
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                redSquare.setVisibility(View.INVISIBLE);
            }
        });
        getInstrumentation().waitForIdleSync();
        TransitionValues invisibleValues = new TransitionValues();
        invisibleValues.view = redSquare;
        mTransition.captureStartValues(invisibleValues);
        assertFalse(mVisibilityTransition.isVisible(invisibleValues));

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                redSquare.setVisibility(View.GONE);
            }
        });
        getInstrumentation().waitForIdleSync();
        TransitionValues goneValues = new TransitionValues();
        goneValues.view = redSquare;
        mTransition.captureStartValues(goneValues);
        assertFalse(mVisibilityTransition.isVisible(goneValues));
    }
}

