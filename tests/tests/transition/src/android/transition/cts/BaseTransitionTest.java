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

import static org.junit.Assert.assertTrue;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Rule;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public abstract class BaseTransitionTest {
    protected Instrumentation mInstrumentation;
    protected TransitionActivity mActivity;
    protected FrameLayout mSceneRoot;
    public float mAnimatedValue;
    protected ArrayList<View> mTargets = new ArrayList<>();
    protected Transition mTransition;
    protected SimpleTransitionListener mListener;

    @Rule
    public ActivityTestRule<TransitionActivity> mActivityRule =
            new ActivityTestRule<>(TransitionActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.setInTouchMode(false);
        mActivity = mActivityRule.getActivity();
        mSceneRoot = (FrameLayout) mActivity.findViewById(R.id.container);
        mTargets.clear();
        mTransition = new TestTransition();
        mListener = new SimpleTransitionListener();
        mTransition.addListener(mListener);
    }

    protected void waitForStart() throws InterruptedException {
        waitForStart(mListener);
    }

    protected void waitForStart(SimpleTransitionListener listener) throws InterruptedException {
        assertTrue(listener.startLatch.await(4000, TimeUnit.MILLISECONDS));
    }

    protected void waitForEnd(long waitMillis) throws InterruptedException {
        waitForEnd(mListener, waitMillis);
        mInstrumentation.waitForIdleSync();
    }

    protected static void waitForEnd(SimpleTransitionListener listener, long waitMillis)
            throws InterruptedException {
        listener.endLatch.await(waitMillis, TimeUnit.MILLISECONDS);
    }

    protected View loadLayout(final int layout) throws Throwable {
        View[] root = new View[1];

        mActivityRule.runOnUiThread(
                () -> root[0] = mActivity.getLayoutInflater().inflate(layout, mSceneRoot, false));

        return root[0];
    }

    protected Scene loadScene(final View layout) throws Throwable {
        final Scene[] scene = new Scene[1];
        mActivityRule.runOnUiThread(() -> scene[0] = new Scene(mSceneRoot, layout));

        return scene[0];
    }

    protected Scene loadScene(final int layoutId) throws Throwable {
        final Scene scene[] = new Scene[1];
        mActivityRule.runOnUiThread(
                () -> scene[0] = Scene.getSceneForLayout(mSceneRoot, layoutId, mActivity));
        return scene[0];
    }

    protected void startTransition(final int layoutId) throws Throwable {
        startTransition(loadScene(layoutId));
    }

    protected void startTransition(final Scene scene) throws Throwable {
        mActivityRule.runOnUiThread(() -> TransitionManager.go(scene, mTransition));
        waitForStart();
    }

    protected void endTransition() throws Throwable {
        mActivityRule.runOnUiThread(() -> TransitionManager.endTransitions(mSceneRoot));
    }

    protected void enterScene(final int layoutId) throws Throwable {
        enterScene(loadScene(layoutId));
    }

    protected void enterScene(final Scene scene) throws Throwable {
        mActivityRule.runOnUiThread(scene::enter);
        mInstrumentation.waitForIdleSync();
    }

    protected void exitScene(final Scene scene) throws Throwable {
        mActivityRule.runOnUiThread(scene::exit);
        mInstrumentation.waitForIdleSync();
    }

    protected void resetListener() {
        mTransition.removeListener(mListener);
        mListener = new SimpleTransitionListener();
        mTransition.addListener(mListener);
    }

    public class TestTransition extends Visibility {

        public TestTransition() {
        }

        @Override
        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            mTargets.add(endValues.view);
            return ObjectAnimator.ofFloat(BaseTransitionTest.this, "mAnimatedValue", 0, 1);
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            mTargets.add(startValues.view);
            return ObjectAnimator.ofFloat(BaseTransitionTest.this, "mAnimatedValue", 1, 0);
        }
    }
}
