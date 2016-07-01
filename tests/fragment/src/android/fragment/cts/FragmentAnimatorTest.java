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
package android.fragment.cts;

import static junit.framework.Assert.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.Instrumentation;
import android.os.Debug;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FragmentAnimatorTest {
    // These are pretend resource IDs for animators. We don't need real ones since we
    // load them by overriding onCreateAnimator
    private final static int ENTER = 1;
    private final static int EXIT = 2;
    private final static int POP_ENTER = 3;
    private final static int POP_EXIT = 4;

    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<FragmentTestActivity>(FragmentTestActivity.class);

    private Instrumentation mInstrumentation;

    @Before
    public void setupContainer() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        FragmentTestUtil.setContentView(mActivityRule, R.layout.simple_container);
    }

    // Ensure that adding and popping a Fragment uses the enter and popExit animators
    @Test
    public void addAnimators() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final AnimatorFragment fragment = new AnimatorFragment();
        fm.beginTransaction()
                .setCustomAnimations(ENTER, EXIT, POP_ENTER, POP_EXIT)
                .add(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertEnterPopExit(fragment);
    }

    // Ensure that removing and popping a Fragment uses the exit and popEnter animators
    @Test
    public void removeAnimators() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final AnimatorFragment fragment = new AnimatorFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment, "1").commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        fm.beginTransaction()
                .setCustomAnimations(ENTER, EXIT, POP_ENTER, POP_EXIT)
                .remove(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertExitPopEnter(fragment);
    }

    // Ensure that showing and popping a Fragment uses the enter and popExit animators
    @Test
    public void showAnimators() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final AnimatorFragment fragment = new AnimatorFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).hide(fragment).commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        fm.beginTransaction()
                .setCustomAnimations(ENTER, EXIT, POP_ENTER, POP_EXIT)
                .show(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertEnterPopExit(fragment);
    }

    // Ensure that hiding and popping a Fragment uses the exit and popEnter animators
    @Test
    public void hideAnimators() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final AnimatorFragment fragment = new AnimatorFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment, "1").commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        fm.beginTransaction()
                .setCustomAnimations(ENTER, EXIT, POP_ENTER, POP_EXIT)
                .hide(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertExitPopEnter(fragment);
    }

    // Ensure that attaching and popping a Fragment uses the enter and popExit animators
    @Test
    public void attachAnimators() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final AnimatorFragment fragment = new AnimatorFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).detach(fragment).commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        fm.beginTransaction()
                .setCustomAnimations(ENTER, EXIT, POP_ENTER, POP_EXIT)
                .attach(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertEnterPopExit(fragment);
    }

    // Ensure that detaching and popping a Fragment uses the exit and popEnter animators
    @Test
    public void detachAnimators() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final AnimatorFragment fragment = new AnimatorFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment, "1").commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        fm.beginTransaction()
                .setCustomAnimations(ENTER, EXIT, POP_ENTER, POP_EXIT)
                .detach(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertExitPopEnter(fragment);
    }

    // Replace should exit the existing fragments and enter the added fragment, then
    // popping should popExit the removed fragment and popEnter the added fragments
    @Test
    public void replaceAnimators() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final AnimatorFragment fragment1 = new AnimatorFragment();
        final AnimatorFragment fragment2 = new AnimatorFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment1, "1")
                .add(R.id.fragmentContainer, fragment2, "2")
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        final AnimatorFragment fragment3 = new AnimatorFragment();
        fm.beginTransaction()
                .setCustomAnimations(ENTER, EXIT, POP_ENTER, POP_EXIT)
                .replace(R.id.fragmentContainer, fragment3)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertFragmentAnimation(fragment1, 1, false, EXIT);
        assertFragmentAnimation(fragment2, 1, false, EXIT);
        assertFragmentAnimation(fragment3, 1, true, ENTER);

        mInstrumentation.waitForIdleSync();

        fm.popBackStack();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertFragmentAnimation(fragment3, 2, false, POP_EXIT);
        final AnimatorFragment replacement1 = (AnimatorFragment) fm.findFragmentByTag("1");
        final AnimatorFragment replacement2 = (AnimatorFragment) fm.findFragmentByTag("1");
        int expectedAnimations = replacement1 == fragment1 ? 2 : 1;
        assertFragmentAnimation(replacement1, expectedAnimations, true, POP_ENTER);
        assertFragmentAnimation(replacement2, expectedAnimations, true, POP_ENTER);
    }

    private void assertEnterPopExit(AnimatorFragment fragment) throws Throwable {
        assertFragmentAnimation(fragment, 1, true, ENTER);
        mInstrumentation.waitForIdleSync();

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        fm.popBackStack();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        assertFragmentAnimation(fragment, 2, false, POP_EXIT);
    }

    private void assertExitPopEnter(AnimatorFragment fragment) throws Throwable {
        assertFragmentAnimation(fragment, 1, false, EXIT);
        mInstrumentation.waitForIdleSync();

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        fm.popBackStack();
        FragmentTestUtil.executePendingTransactions(mActivityRule);

        AnimatorFragment replacement = (AnimatorFragment) fm.findFragmentByTag("1");

        boolean isSameFragment = replacement == fragment;
        int expectedAnimators = isSameFragment ? 2 : 1;
        assertFragmentAnimation(replacement, expectedAnimators, true, POP_ENTER);
    }

    private void assertFragmentAnimation(AnimatorFragment fragment, int numAnimators,
            boolean isEnter, int animatorResourceId) throws InterruptedException {
        assertEquals(numAnimators, fragment.numAnimators);
        assertEquals(isEnter, fragment.enter);
        assertEquals(animatorResourceId, fragment.resourceId);
        assertNotNull(fragment.animator);
        assertTrue(fragment.wasStarted);
        assertTrue(fragment.endLatch.await(100, TimeUnit.MILLISECONDS));
    }

    public static class AnimatorFragment extends StrictViewFragment {
        int numAnimators;
        Animator animator;
        boolean enter;
        int resourceId;
        boolean wasStarted;
        CountDownLatch endLatch;

        @Override
        public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
            if (nextAnim == 0) {
                return null;
            }
            this.numAnimators++;
            this.wasStarted = false;
            this.animator = ValueAnimator.ofFloat(0, 1).setDuration(1);
            this.endLatch = new CountDownLatch(1);
            this.animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    wasStarted = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    endLatch.countDown();
                }
            });
            this.resourceId = nextAnim;
            this.enter = enter;
            return this.animator;
        }
    }
}
