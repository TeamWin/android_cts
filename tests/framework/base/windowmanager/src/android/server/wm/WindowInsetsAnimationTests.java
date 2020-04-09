/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm;

import static android.graphics.Insets.NONE;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowInsets.Type.systemBars;
import static android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.withSettings;

import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowInsetsAnimationTests.AnimCallback.AnimationStep;
import android.util.ArraySet;
import android.view.View;
import android.view.View.OnApplyWindowInsetsListener;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowInsetsAnimation.Bounds;
import android.view.WindowInsetsAnimation.Callback;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import androidx.test.filters.FlakyTest;

/**
 * Test whether {@link WindowInsetsAnimation.Callback} are properly dispatched to views.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowInsetsAnimationTests
 */
@Presubmit
public class WindowInsetsAnimationTests extends WindowManagerTestBase {

    TestActivity mActivity;
    View mRootView;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mActivity = startActivity(TestActivity.class);
        mRootView = mActivity.getWindow().getDecorView();
    }

    @Test
    public void testAnimationCallbacksHide() {
        WindowInsets before = mActivity.mLastWindowInsets;

        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().hide(systemBars()));

        waitForOrFail("Waiting until animation done", () -> mActivity.mCallback.animationDone);

        commonAnimationAssertions(mActivity, before, false /* show */, systemBars());
    }

    @Test
    public void testAnimationCallbacksShow() {
        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().hide(systemBars()));

        waitForOrFail("Waiting until animation done", () -> mActivity.mCallback.animationDone);
        mActivity.mCallback.animationDone = false;

        WindowInsets before = mActivity.mLastWindowInsets;

        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().show(systemBars()));

        waitForOrFail("Waiting until animation done", () -> mActivity.mCallback.animationDone);

        commonAnimationAssertions(mActivity, before, true /* show */, systemBars());
    }

    @Test
    public void testImeAnimationCallbacksShowAndHide() {
        WindowInsets before = mActivity.mLastWindowInsets;
        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().show(ime()));

        waitForOrFail("Waiting until animation done", () -> mActivity.mCallback.animationDone);
        commonAnimationAssertions(mActivity, before, true /* show */, ime());
        mActivity.mCallback.animationDone = false;

        before = mActivity.mLastWindowInsets;

        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().hide(ime()));

        waitForOrFail("Waiting until animation done", () -> mActivity.mCallback.animationDone);

        commonAnimationAssertions(mActivity, before, false /* show */, ime());
    }

    @Test
    @FlakyTest(detail = "Promote once confirmed non-flaky")
    public void testAnimationCallbacks_overlapping() {
        WindowInsets before = mActivity.mLastWindowInsets;

        MultiAnimCallback callbackInner = new MultiAnimCallback();
        MultiAnimCallback callback = mock(MultiAnimCallback.class,
                withSettings()
                        .spiedInstance(callbackInner)
                        .defaultAnswer(CALLS_REAL_METHODS)
                        .verboseLogging());
        mActivity.mView.setWindowInsetsAnimationCallback(callback);
        callback.startRunnable = () -> mRootView.postDelayed(
                () -> mRootView.getWindowInsetsController().hide(statusBars()), 50);

        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().hide(navigationBars()));

        waitForOrFail("Waiting until animation done", () -> callback.animationDone);

        WindowInsets after = mActivity.mLastWindowInsets;

        InOrder inOrder = inOrder(callback, mActivity.mListener);

        inOrder.verify(callback).onPrepare(eq(callback.navBarAnim));

        inOrder.verify(mActivity.mListener).onApplyWindowInsets(any(), argThat(
                argument -> NONE.equals(argument.getInsets(navigationBars()))
                        && !NONE.equals(argument.getInsets(statusBars()))));

        inOrder.verify(callback).onStart(eq(callback.navBarAnim), argThat(
                argument -> argument.getLowerBound().equals(NONE)
                        && argument.getUpperBound().equals(before.getInsets(navigationBars()))));

        inOrder.verify(callback).onPrepare(eq(callback.statusBarAnim));
        inOrder.verify(mActivity.mListener).onApplyWindowInsets(
                any(), eq(mActivity.mLastWindowInsets));

        inOrder.verify(callback).onStart(eq(callback.statusBarAnim), argThat(
                argument -> argument.getLowerBound().equals(NONE)
                        && argument.getUpperBound().equals(before.getInsets(statusBars()))));

        inOrder.verify(callback).onEnd(eq(callback.navBarAnim));
        inOrder.verify(callback).onEnd(eq(callback.statusBarAnim));

        assertAnimationSteps(callback.navAnimSteps, false /* showAnimation */);
        assertAnimationSteps(callback.statusAnimSteps, false /* showAnimation */);

        assertEquals(before.getInsets(navigationBars()),
                callback.navAnimSteps.get(0).insets.getInsets(navigationBars()));
        assertEquals(after.getInsets(navigationBars()),
                callback.navAnimSteps.get(callback.navAnimSteps.size() - 1).insets
                        .getInsets(navigationBars()));

        assertEquals(before.getInsets(statusBars()),
                callback.statusAnimSteps.get(0).insets.getInsets(statusBars()));
        assertEquals(after.getInsets(statusBars()),
                callback.statusAnimSteps.get(callback.statusAnimSteps.size() - 1).insets
                        .getInsets(statusBars()));
    }

    @Test
    @FlakyTest(detail = "Promote once confirmed non-flaky")
    public void testAnimationCallbacks_overlapping_opposite() {
        WindowInsets before = mActivity.mLastWindowInsets;

        MultiAnimCallback callbackInner = new MultiAnimCallback();
        MultiAnimCallback callback = mock(MultiAnimCallback.class,
                withSettings()
                        .spiedInstance(callbackInner)
                        .defaultAnswer(CALLS_REAL_METHODS)
                        .verboseLogging());
        mActivity.mView.setWindowInsetsAnimationCallback(callback);

        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().hide(navigationBars()));
        getInstrumentation().runOnMainSync(
                () -> mRootView.getWindowInsetsController().show(ime()));

        waitForOrFail("Waiting until animation done", () -> callback.animationDone);

        WindowInsets after = mActivity.mLastWindowInsets;

        InOrder inOrder = inOrder(callback, mActivity.mListener);

        inOrder.verify(callback).onPrepare(eq(callback.navBarAnim));

        inOrder.verify(mActivity.mListener).onApplyWindowInsets(any(), argThat(
                argument -> NONE.equals(argument.getInsets(navigationBars()))
                        && NONE.equals(argument.getInsets(ime()))));

        inOrder.verify(callback).onStart(eq(callback.navBarAnim), argThat(
                argument -> argument.getLowerBound().equals(NONE)
                        && argument.getUpperBound().equals(before.getInsets(navigationBars()))));

        inOrder.verify(callback).onPrepare(eq(callback.imeAnim));
        inOrder.verify(mActivity.mListener).onApplyWindowInsets(
                any(), eq(mActivity.mLastWindowInsets));

        inOrder.verify(callback).onStart(eq(callback.imeAnim), argThat(
                argument -> argument.getLowerBound().equals(NONE)
                        && !argument.getUpperBound().equals(NONE)));

        inOrder.verify(callback).onEnd(eq(callback.navBarAnim));
        inOrder.verify(callback).onEnd(eq(callback.imeAnim));

        assertAnimationSteps(callback.navAnimSteps, false /* showAnimation */);
        assertAnimationSteps(callback.imeAnimSteps, false /* showAnimation */);

        assertEquals(before.getInsets(navigationBars()),
                callback.navAnimSteps.get(0).insets.getInsets(navigationBars()));
        assertEquals(after.getInsets(navigationBars()),
                callback.navAnimSteps.get(callback.navAnimSteps.size() - 1).insets
                        .getInsets(navigationBars()));

        assertEquals(before.getInsets(ime()),
                callback.imeAnimSteps.get(0).insets.getInsets(ime()));
        assertEquals(after.getInsets(ime()),
                callback.imeAnimSteps.get(callback.imeAnimSteps.size() - 1).insets
                        .getInsets(ime()));
    }

    @Test
    public void testAnimationCallbacks_consumedByDecor() {
        getInstrumentation().runOnMainSync(() -> {
            mActivity.getWindow().setDecorFitsSystemWindows(true);
            mRootView.getWindowInsetsController().hide(systemBars());
        });

        getWmState().waitFor(state -> !state.isWindowVisible("StatusBar"),
                "Waiting for status bar to be hidden");
        assertFalse(getWmState().isWindowVisible("StatusBar"));

        verifyZeroInteractions(mActivity.mCallback);
    }

    @Test
    public void testAnimationCallbacks_childDoesntGetCallback() {
        WindowInsetsAnimation.Callback childCallback = mock(WindowInsetsAnimation.Callback.class);

        getInstrumentation().runOnMainSync(() -> {
            mActivity.mChild.setWindowInsetsAnimationCallback(childCallback);
            mRootView.getWindowInsetsController().hide(systemBars());
        });

        waitForOrFail("Waiting until animation done", () -> mActivity.mCallback.animationDone);

        verifyZeroInteractions(childCallback);
    }

    @Test
    public void testAnimationCallbacks_childInsetting() {
        WindowInsets before = mActivity.mLastWindowInsets;

        boolean[] done = new boolean[1];
        WindowInsetsAnimation.Callback childCallback = mock(WindowInsetsAnimation.Callback.class);
        WindowInsetsAnimation.Callback callback = new Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {

            @Override
            public Bounds onStart(WindowInsetsAnimation animation, Bounds bounds) {
                return bounds.inset(before.getInsets(navigationBars()));
            }

            @Override
            public WindowInsets onProgress(WindowInsets insets,
                    List<WindowInsetsAnimation> runningAnimations) {
                return insets.inset(insets.getInsets(navigationBars()));
            }

            @Override
            public void onEnd(WindowInsetsAnimation animation) {
                done[0] = true;
            }
        };

        getInstrumentation().runOnMainSync(() -> {
            mActivity.mView.setWindowInsetsAnimationCallback(callback);
            mActivity.mChild.setWindowInsetsAnimationCallback(childCallback);
            mRootView.getWindowInsetsController().hide(systemBars());
        });

        waitForOrFail("Waiting until animation done", () -> done[0]);

        verify(childCallback).onStart(any(), argThat(
                bounds -> bounds.getUpperBound().equals(before.getInsets(statusBars()))));
        verify(childCallback, atLeastOnce()).onProgress(argThat(
                insets -> NONE.equals(insets.getInsets(navigationBars()))), any());
    }

    private void commonAnimationAssertions(TestActivity activity, WindowInsets before,
            boolean show, int types) {

        AnimCallback callback = activity.mCallback;

        InOrder inOrder = inOrder(activity.mCallback, activity.mListener);

        WindowInsets after = activity.mLastWindowInsets;
        inOrder.verify(callback).onPrepare(eq(callback.lastAnimation));
        inOrder.verify(activity.mListener).onApplyWindowInsets(any(), any());

        inOrder.verify(callback).onStart(eq(callback.lastAnimation), argThat(
                argument -> argument.getLowerBound().equals(NONE)
                        && argument.getUpperBound().equals(show
                                ? after.getInsets(types)
                                : before.getInsets(types))));

        inOrder.verify(callback, atLeast(2)).onProgress(any(), argThat(
                argument -> argument.size() == 1 && argument.get(0) == callback.lastAnimation));
        inOrder.verify(callback).onEnd(eq(callback.lastAnimation));

        if ((types & systemBars()) != 0) {
            assertTrue((callback.lastAnimation.getTypeMask() & systemBars()) != 0);
        }
        if ((types & ime()) != 0) {
            assertTrue((callback.lastAnimation.getTypeMask() & ime()) != 0);
        }
        assertTrue(callback.lastAnimation.getDurationMillis() > 0);
        assertNotNull(callback.lastAnimation.getInterpolator());
        assertBeforeAfterState(callback.animationSteps, before, after);
        assertAnimationSteps(callback.animationSteps, show /* increasing */);
    }

    private void assertBeforeAfterState(ArrayList<AnimationStep> steps, WindowInsets before,
            WindowInsets after) {
        assertEquals(before, steps.get(0).insets);
        assertEquals(after, steps.get(steps.size() - 1).insets);
    }

    private void assertAnimationSteps(ArrayList<AnimationStep> steps, boolean showAnimation) {
        assertTrue(steps.size() >= 2);
        assertEquals(0f, steps.get(0).fraction, 0f);
        assertEquals(0f, steps.get(0).interpolatedFraction, 0f);
        assertEquals(1f, steps.get(steps.size() - 1).fraction, 0f);
        assertEquals(1f, steps.get(steps.size() - 1).interpolatedFraction, 0f);
        if (showAnimation) {
            assertEquals(1f, steps.get(steps.size() - 1).alpha, 0f);
        } else {
            assertEquals(1f, steps.get(0).alpha, 0f);
        }

        assertListElements(steps, step -> step.fraction,
                (current, next) -> next >= current);
        assertListElements(steps, step -> step.interpolatedFraction,
                (current, next) -> next >= current);
        assertListElements(steps, step -> step.alpha, alpha -> alpha >= 0f);
        assertListElements(steps, step -> step.insets, compareInsets(systemBars(), showAnimation));
    }

    private BiPredicate<WindowInsets, WindowInsets> compareInsets(int types,
            boolean showAnimation) {
        if (showAnimation) {
            return (current, next) ->
                    next.getInsets(types).left >= current.getInsets(types).left
                            && next.getInsets(types).top >= current.getInsets(types).top
                            && next.getInsets(types).right >= current.getInsets(types).right
                            && next.getInsets(types).bottom >= current.getInsets(types).bottom;
        } else {
            return (current, next) ->
                    next.getInsets(types).left <= current.getInsets(types).left
                            && next.getInsets(types).top <= current.getInsets(types).top
                            && next.getInsets(types).right <= current.getInsets(types).right
                            && next.getInsets(types).bottom <= current.getInsets(types).bottom;
        }
    }

    private <T, V> void assertListElements(ArrayList<T> list, Function<T, V> getter,
            Predicate<V> predicate) {
        for (int i = 0; i <= list.size() - 1; i++) {
            V value = getter.apply(list.get(i));
            assertTrue("Predicate.test failed i=" + i + " value=" + value, predicate.test(value));
        }
    }

    private <T, V> void assertListElements(ArrayList<T> list, Function<T, V> getter,
            BiPredicate<V, V> comparator) {
        for (int i = 0; i <= list.size() - 2; i++) {
            V current = getter.apply(list.get(i));
            V next = getter.apply(list.get(i + 1));
            assertTrue(comparator.test(current, next));
        }
    }

    public static class AnimCallback extends WindowInsetsAnimation.Callback {

        public static class AnimationStep {

            AnimationStep(WindowInsets insets, float fraction, float interpolatedFraction,
                    float alpha) {
                this.insets = insets;
                this.fraction = fraction;
                this.interpolatedFraction = interpolatedFraction;
                this.alpha = alpha;
            }

            WindowInsets insets;
            float fraction;
            float interpolatedFraction;
            float alpha;
        }

        WindowInsetsAnimation lastAnimation;
        volatile boolean animationDone;
        final ArrayList<AnimationStep> animationSteps = new ArrayList<>();

        public AnimCallback(int dispatchMode) {
            super(dispatchMode);
        }

        @Override
        public void onPrepare(WindowInsetsAnimation animation) {
            animationSteps.clear();
            lastAnimation = animation;
        }

        @Override
        public Bounds onStart(WindowInsetsAnimation animation, Bounds bounds) {
            return bounds;
        }

        @Override
        public WindowInsets onProgress(WindowInsets insets,
                List<WindowInsetsAnimation> runningAnimations) {
            animationSteps.add(new AnimationStep(insets, lastAnimation.getFraction(),
                    lastAnimation.getInterpolatedFraction(), lastAnimation.getAlpha()));
            return WindowInsets.CONSUMED;
        }

        @Override
        public void onEnd(WindowInsetsAnimation animation) {
            animationDone = true;
        }
    }

    public static class MultiAnimCallback extends WindowInsetsAnimation.Callback {

        WindowInsetsAnimation statusBarAnim;
        WindowInsetsAnimation navBarAnim;
        WindowInsetsAnimation imeAnim;
        volatile boolean animationDone;
        final ArrayList<AnimationStep> statusAnimSteps = new ArrayList<>();
        final ArrayList<AnimationStep> navAnimSteps = new ArrayList<>();
        final ArrayList<AnimationStep> imeAnimSteps = new ArrayList<>();
        Runnable startRunnable;
        final ArraySet<WindowInsetsAnimation> runningAnims = new ArraySet<>();

        public MultiAnimCallback() {
            super(DISPATCH_MODE_STOP);
        }

        @Override
        public void onPrepare(WindowInsetsAnimation animation) {
            if ((animation.getTypeMask() & statusBars()) != 0) {
                statusBarAnim = animation;
            }
            if ((animation.getTypeMask() & navigationBars()) != 0) {
                navBarAnim = animation;
            }
            if ((animation.getTypeMask() & ime()) != 0) {
                imeAnim = animation;
            }
        }

        @Override
        public Bounds onStart(WindowInsetsAnimation animation, Bounds bounds) {
            if (startRunnable != null) {
                startRunnable.run();
            }
            runningAnims.add(animation);
            return bounds;
        }

        @Override
        public WindowInsets onProgress(WindowInsets insets,
                List<WindowInsetsAnimation> runningAnimations) {
            if (statusBarAnim != null) {
                statusAnimSteps.add(new AnimationStep(insets, statusBarAnim.getFraction(),
                        statusBarAnim.getInterpolatedFraction(), statusBarAnim.getAlpha()));
            }
            if (navBarAnim != null) {
                navAnimSteps.add(new AnimationStep(insets, navBarAnim.getFraction(),
                        navBarAnim.getInterpolatedFraction(), navBarAnim.getAlpha()));
            }
            if (imeAnim != null) {
                imeAnimSteps.add(new AnimationStep(insets, imeAnim.getFraction(),
                        imeAnim.getInterpolatedFraction(), imeAnim.getAlpha()));
            }

            assertEquals(runningAnims.size(), runningAnimations.size());
            for (int i = runningAnimations.size() - 1; i >= 0; i--) {
                Assert.assertNotEquals(-1, runningAnims.indexOf(runningAnimations.get(i)));
            }

            return WindowInsets.CONSUMED;
        }

        @Override
        public void onEnd(WindowInsetsAnimation animation) {
            runningAnims.remove(animation);
            if (runningAnims.isEmpty()) {
                animationDone = true;
            }
        }
    }

    public static class TestActivity extends FocusableActivity {

        AnimCallback mCallback = spy(new AnimCallback(Callback.DISPATCH_MODE_STOP));
        WindowInsets mLastWindowInsets;

        OnApplyWindowInsetsListener mListener;
        LinearLayout mView;
        View mChild;
        EditText mEditor;

        public class InsetsListener implements OnApplyWindowInsetsListener {

            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                mLastWindowInsets = insets;
                return WindowInsets.CONSUMED;
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mListener = spy(new InsetsListener());
            mView = new LinearLayout(this);
            mView.setWindowInsetsAnimationCallback(mCallback);
            mView.setOnApplyWindowInsetsListener(mListener);
            mChild = new TextView(this);
            mEditor = new EditText(this);
            mView.addView(mChild);

            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            setContentView(mView);
            mEditor.requestFocus();
        }
    }
}
