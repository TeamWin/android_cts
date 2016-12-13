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

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.Fade;
import android.transition.Transition.TransitionListener;
import android.view.View;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.transition.TargetTracking;
import com.android.compatibility.common.util.transition.TrackingTransition;
import com.android.compatibility.common.util.transition.TrackingVisibility;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.stream.Collectors;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ActivityTransitionTest extends BaseTransitionTest {
    private TransitionListener mExitListener;
    private TransitionListener mReenterListener;
    private TransitionListener mSharedElementReenterListener;
    private TrackingVisibility mExitTransition;
    private TrackingVisibility mReenterTransition;
    private TrackingTransition mSharedElementReenterTransition;

    @Override
    public void setup() {
        super.setup();
        mExitTransition = new TrackingVisibility();
        mExitListener = mock(TransitionListener.class);
        mExitTransition.addListener(mExitListener);
        mActivity.getWindow().setExitTransition(mExitTransition);

        mReenterTransition = new TrackingVisibility();
        mReenterListener = mock(TransitionListener.class);
        mReenterTransition.addListener(mReenterListener);
        mActivity.getWindow().setReenterTransition(mReenterTransition);

        mSharedElementReenterTransition = new TrackingTransition();
        mSharedElementReenterListener = mock(TransitionListener.class);
        mSharedElementReenterTransition.addListener(mSharedElementReenterListener);
        mActivity.getWindow().setSharedElementReenterTransition(mSharedElementReenterTransition);
    }

    @After
    public void cleanup() {
        if (TargetActivity.sLastCreated != null) {
            mInstrumentation.runOnMainSync(() -> TargetActivity.sLastCreated.finish());
        }
        TargetActivity.sLastCreated = null;
    }

    // Views that are outside the visible area only during the shared element start
    // should not be stripped from the transition.
    @Test
    public void viewsNotStripped() throws Throwable {
        enterScene(R.layout.scene10);
        mInstrumentation.runOnMainSync(() -> {
            View sharedElement = mActivity.findViewById(R.id.blueSquare);
            Bundle options = ActivityOptions.makeSceneTransitionAnimation(mActivity,
                    sharedElement, "holder").toBundle();
            Intent intent = new Intent(mActivity, TargetActivity.class);
            intent.putExtra(TargetActivity.EXTRA_LAYOUT_ID, R.layout.scene12);
            mActivity.startActivity(intent, options);
        });

        TargetActivity targetActivity = waitForTargetActivity();
        verify(targetActivity.enterListener, within(3000)).onTransitionEnd(any());
        verify(mExitListener, times(1)).onTransitionEnd(any());

        // Now check the targets... they should all be there
        assertTargetContains(targetActivity.enterTransition,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);
        assertTargetExcludes(targetActivity.enterTransition, R.id.holder);

        assertTargetContains(targetActivity.sharedElementEnterTransition, R.id.holder);
        assertTargetExcludes(targetActivity.sharedElementEnterTransition,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertTargetContains(mExitTransition, R.id.redSquare, R.id.greenSquare, R.id.yellowSquare);
        assertTargetExcludes(mExitTransition, R.id.blueSquare, R.id.holder);

        assertEquals(View.VISIBLE, targetActivity.findViewById(R.id.redSquare).getVisibility());
        assertEquals(View.VISIBLE, targetActivity.findViewById(R.id.greenSquare).getVisibility());
        assertEquals(View.VISIBLE, targetActivity.findViewById(R.id.holder).getVisibility());

        assertEquals(1, targetActivity.findViewById(R.id.redSquare).getAlpha(), 0.01f);
        assertEquals(1, targetActivity.findViewById(R.id.greenSquare).getAlpha(), 0.01f);
        assertEquals(1, targetActivity.findViewById(R.id.holder).getAlpha(), 0.01f);

        mInstrumentation.runOnMainSync(() -> targetActivity.finishAfterTransition());
        verify(mReenterListener, within(3000)).onTransitionEnd(any());
        verify(mSharedElementReenterListener, within(3000)).onTransitionEnd(any());
        verify(targetActivity.returnListener, times(1)).onTransitionEnd(any());

        // return targets are stripped also
        assertTargetContains(targetActivity.returnTransition,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);
        assertTargetExcludes(targetActivity.returnTransition, R.id.holder);

        assertTargetContains(mReenterTransition,
                R.id.redSquare, R.id.greenSquare, R.id.yellowSquare);
        assertTargetExcludes(mReenterTransition, R.id.blueSquare, R.id.holder);

        assertTargetContains(targetActivity.sharedElementReturnTransition,
                R.id.holder);
        assertTargetExcludes(targetActivity.sharedElementReturnTransition,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertTargetContains(mSharedElementReenterTransition, R.id.blueSquare);
        assertTargetExcludes(mSharedElementReenterTransition,
                R.id.redSquare, R.id.greenSquare, R.id.yellowSquare);

        assertEquals(View.VISIBLE, mActivity.findViewById(R.id.redSquare).getVisibility());
        assertEquals(View.VISIBLE, mActivity.findViewById(R.id.greenSquare).getVisibility());
        assertEquals(View.VISIBLE, mActivity.findViewById(R.id.holder).getVisibility());

        assertEquals(1, mActivity.findViewById(R.id.redSquare).getAlpha(), 0.01f);
        assertEquals(1, mActivity.findViewById(R.id.greenSquare).getAlpha(), 0.01f);
        assertEquals(1, mActivity.findViewById(R.id.holder).getAlpha(), 0.01f);

        TargetActivity.sLastCreated = null;
    }

    // Views that are outside the visible area during initial layout should be stripped from
    // the transition.
    @Test
    public void viewsStripped() throws Throwable {
        enterScene(R.layout.scene13);
        mInstrumentation.runOnMainSync(() -> {
            View sharedElement = mActivity.findViewById(R.id.redSquare);
            Bundle options = ActivityOptions.makeSceneTransitionAnimation(mActivity,
                    sharedElement, "redSquare").toBundle();
            Intent intent = new Intent(mActivity, TargetActivity.class);
            intent.putExtra(TargetActivity.EXTRA_LAYOUT_ID, R.layout.scene13);
            mActivity.startActivity(intent, options);
        });

        TargetActivity targetActivity = waitForTargetActivity();
        verify(targetActivity.enterListener, within(3000)).onTransitionEnd(any());
        verify(mExitListener, times(1)).onTransitionEnd(any());

        // Now check the targets... they should all be stripped
        assertTargetExcludes(targetActivity.enterTransition, R.id.holder,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertTargetExcludes(mExitTransition, R.id.holder,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertTargetContains(targetActivity.sharedElementEnterTransition, R.id.redSquare);
        assertTargetExcludes(targetActivity.sharedElementEnterTransition,
                R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertEquals(View.VISIBLE, targetActivity.findViewById(R.id.redSquare).getVisibility());
        assertEquals(View.VISIBLE, targetActivity.findViewById(R.id.greenSquare).getVisibility());
        assertEquals(View.VISIBLE, targetActivity.findViewById(R.id.holder).getVisibility());

        assertEquals(1, targetActivity.findViewById(R.id.redSquare).getAlpha(), 0.01f);
        assertEquals(1, targetActivity.findViewById(R.id.greenSquare).getAlpha(), 0.01f);
        assertEquals(1, targetActivity.findViewById(R.id.holder).getAlpha(), 0.01f);

        mInstrumentation.runOnMainSync(() -> targetActivity.finishAfterTransition());
        verify(mReenterListener, within(3000)).onTransitionEnd(any());
        verify(mSharedElementReenterListener, within(3000)).onTransitionEnd(any());
        verify(targetActivity.returnListener, times(1)).onTransitionEnd(any());

        // return targets are stripped also
        assertTargetExcludes(targetActivity.returnTransition,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertTargetExcludes(mReenterTransition, R.id.holder,
                R.id.redSquare, R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertTargetContains(targetActivity.sharedElementReturnTransition,
                R.id.redSquare);
        assertTargetExcludes(targetActivity.sharedElementReturnTransition,
                R.id.greenSquare, R.id.blueSquare, R.id.yellowSquare);

        assertTargetContains(mSharedElementReenterTransition, R.id.redSquare);
        assertTargetExcludes(mSharedElementReenterTransition,
                R.id.blueSquare, R.id.greenSquare, R.id.yellowSquare);

        assertEquals(View.VISIBLE, mActivity.findViewById(R.id.greenSquare).getVisibility());
        assertEquals(View.VISIBLE, mActivity.findViewById(R.id.holder).getVisibility());
        assertEquals(View.VISIBLE, mActivity.findViewById(R.id.redSquare).getVisibility());

        assertEquals(1, mActivity.findViewById(R.id.redSquare).getAlpha(), 0.01f);
        assertEquals(1, mActivity.findViewById(R.id.greenSquare).getAlpha(), 0.01f);
        assertEquals(1, mActivity.findViewById(R.id.holder).getAlpha(), 0.01f);

        TargetActivity.sLastCreated = null;
    }

    // When an exit transition takes longer than it takes the activity to cover it (and onStop
    // is called), the exiting views should become visible.
    @Test
    public void earlyExitStop() throws Throwable {
        enterScene(R.layout.scene1);
        final View hello = mActivity.findViewById(R.id.hello);
        final View red = mActivity.findViewById(R.id.redSquare);
        final View green = mActivity.findViewById(R.id.greenSquare);
        mInstrumentation.runOnMainSync(() -> {
            Fade fade = new Fade();
            fade.setDuration(10000);
            fade.addListener(mExitListener);
            mActivity.getWindow().setExitTransition(fade);
            Bundle options = ActivityOptions.makeSceneTransitionAnimation(mActivity).toBundle();
            Intent intent = new Intent(mActivity, TargetActivity.class);
            intent.putExtra(TargetActivity.EXTRA_LAYOUT_ID, R.layout.scene4);
            mActivity.startActivity(intent, options);
        });

        TargetActivity targetActivity = waitForTargetActivity();
        verify(targetActivity.enterListener, within(3000)).onTransitionEnd(any());
        verify(mExitListener, within(3000)).onTransitionEnd(any());

        mInstrumentation.runOnMainSync(() -> {
            // Verify that the exited views have an alpha of 1 and are visible
            assertEquals(1.0f, hello.getAlpha(), 0.01f);
            assertEquals(1.0f, red.getAlpha(), 0.01f);
            assertEquals(1.0f, green.getAlpha(), 0.01f);

            assertEquals(View.VISIBLE, hello.getVisibility());
            assertEquals(View.VISIBLE, red.getVisibility());
            assertEquals(View.VISIBLE, green.getVisibility());
            targetActivity.finish();
        });
    }

    private TargetActivity waitForTargetActivity() {
        PollingCheck.waitFor(() -> TargetActivity.sLastCreated != null);
        // Just make sure that we're not in the middle of running on the UI thread.
        mInstrumentation.runOnMainSync(() -> {});
        return TargetActivity.sLastCreated;
    }

    private Set<Integer> getTargetViewIds(TargetTracking transition) {
        return transition.getTrackedTargets().stream()
                .map(v -> v.getId())
                .collect(Collectors.toSet());
    }

    private void assertTargetContains(TargetTracking transition, int... ids) {
        Set<Integer> targets = getTargetViewIds(transition);
        for (int id : ids) {
            assertTrueWithId(id, "%s was not included from the transition", targets.contains(id));
        }
    }

    private void assertTargetExcludes(TargetTracking transition, int... ids) {
        Set<Integer> targets = getTargetViewIds(transition);
        for (int id : ids) {
            assertTrueWithId(id, "%s was not excluded from the transition", !targets.contains(id));
        }
    }

    private void assertTrueWithId(int id, String message, boolean valueToAssert) {
        if (!valueToAssert) {
            fail(String.format(message, mActivity.getResources().getResourceName(id)));
        }
    }
}
