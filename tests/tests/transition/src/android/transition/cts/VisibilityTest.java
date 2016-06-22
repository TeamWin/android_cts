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
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.View;
import android.view.ViewGroup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        enterScene(R.layout.scene4);
        startTransition(R.layout.scene1);
        assertEquals(1, mListener.endLatch.getCount());
        waitForEnd(400);

        resetListener();
        startTransition(R.layout.scene4);
        assertEquals(1, mListener.endLatch.getCount());
        waitForEnd(400);

        // Now only animate in
        resetListener();
        mVisibilityTransition.setMode(Visibility.MODE_IN);
        startTransition(R.layout.scene1);
        assertEquals(1, mListener.endLatch.getCount());
        waitForEnd(400);

        // No animation since it should only animate in
        resetListener();
        startTransition(R.layout.scene4);
        waitForEnd(0);

        // Now animate out, but no animation should happen since we're animating in.
        resetListener();
        mVisibilityTransition.setMode(Visibility.MODE_OUT);
        startTransition(R.layout.scene1);
        waitForEnd(0);

        // but it should animate out
        resetListener();
        startTransition(R.layout.scene4);
        assertEquals(1, mListener.endLatch.getCount());
        waitForEnd(400);
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

    public void testOnAppear() throws Throwable {
        enterScene(R.layout.scene4);
        AppearTransition transition = new AppearTransition();
        mTransition = transition;
        resetListener();
        startTransition(R.layout.scene5);
        assertTrue(transition.onAppearCalled.await(500, TimeUnit.MILLISECONDS));
        // No need to end the transition since AppearTransition doesn't create
        // any animators.
    }

    public void testOnDisppear() throws Throwable {
        // First, test with overlay
        enterScene(R.layout.scene5);
        DisappearTransition transition = new DisappearTransition(true);
        mTransition = transition;
        resetListener();
        startTransition(R.layout.scene4);
        assertTrue(transition.onDisppearCalled.await(500, TimeUnit.MILLISECONDS));
        // No need to end the transition since DisappearTransition doesn't create
        // any animators.

        // Next test without overlay
        enterScene(R.layout.scene5);
        transition = new DisappearTransition(false);
        mTransition = transition;
        resetListener();
        final View text = getActivity().findViewById(R.id.text);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
                text.setVisibility(View.GONE);
            }
        });
        assertTrue(transition.onDisppearCalled.await(500, TimeUnit.MILLISECONDS));
        // No need to end the transition since DisappearTransition doesn't create
        // any animators.
    }

    static class AppearTransition extends Visibility {
        private View mExpectedView;
        public CountDownLatch onAppearCalled = new CountDownLatch(1);
        @Override
        public Animator onAppear(ViewGroup sceneRoot, TransitionValues startValues,
                int startVisibility, TransitionValues endValues, int endVisibility) {
            assertNotNull(endValues);
            mExpectedView = endValues.view;
            return super.onAppear(sceneRoot, startValues, startVisibility, endValues,
                    endVisibility);
        }

        @Override
        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            assertSame(mExpectedView, view);
            onAppearCalled.countDown();
            return null;
        }
    }

    static class DisappearTransition extends Visibility {
        private View mExpectedView;
        private final boolean mExpectingOverlay;
        public CountDownLatch onDisppearCalled = new CountDownLatch(1);

        public DisappearTransition(boolean expectingOverlay) {
            mExpectingOverlay = expectingOverlay;
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, TransitionValues startValues,
                int startVisibility, TransitionValues endValues, int endVisibility) {
            assertNotNull(startValues);
            if (mExpectingOverlay) {
                assertNull(endValues);
                mExpectedView = null;
            } else {
                assertNotNull(endValues);
                mExpectedView = endValues.view;
            }
            return super.onDisappear(sceneRoot, startValues, startVisibility, endValues,
                    endVisibility);
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            assertNotNull(view);
            if (mExpectedView != null) {
                assertSame(mExpectedView, view);
            }
            onDisppearCalled.countDown();
            return null;
        }
    }
}

