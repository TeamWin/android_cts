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

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.transition.Slide;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class SlideEdgeTest {
    private final int mSlideEdge;
    private final String mEdgeName;
    private SimpleTransitionListener mListener;
    private Slide mSlide;

    @Parameterized.Parameters(name = "Slide edge({1})")
    public static Iterable<Object> data() {
        Object[][] slideEdges =  {
                { Gravity.START, "START" },
                { Gravity.END, "END" },
                { Gravity.LEFT, "LEFT" },
                { Gravity.TOP, "TOP" },
                { Gravity.RIGHT, "RIGHT" },
                { Gravity.BOTTOM, "BOTTOM" },
        };
        return Arrays.asList(slideEdges);
    }

    public SlideEdgeTest(int slideEdge, String edgeName) {
        mSlideEdge = slideEdge;
        mSlide = new Slide(slideEdge);
        mListener = new SimpleTransitionListener();
        mSlide.addListener(mListener);
        mEdgeName = edgeName;
    }

    @Rule
    public ActivityTestRule<TransitionActivity> mActivityRule =
            new ActivityTestRule(TransitionActivity.class);

    @Test
    @SmallTest
    public void setSide() {
        assertEquals("Edge not set properly in constructor " + mEdgeName,
                mSlideEdge, mSlide.getSlideEdge());

        Slide slide = new Slide();
        slide.setSlideEdge(mSlideEdge);
        assertEquals("Edge not set properly with setter " + mEdgeName,
                mSlideEdge, slide.getSlideEdge());
    }

    @Test
    @MediumTest
    public void slideOut() throws Throwable {
        mSlide.setSlideEdge(mSlideEdge);
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Activity activity = mActivityRule.getActivity();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.setContentView(R.layout.scene1);
            }
        });
        instrumentation.waitForIdleSync();

        final View redSquare = activity.findViewById(R.id.redSquare);
        final View greenSquare = activity.findViewById(R.id.greenSquare);
        final View hello = activity.findViewById(R.id.hello);
        final ViewGroup sceneRoot = (ViewGroup) activity.findViewById(R.id.holder);

        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                TransitionManager.beginDelayedTransition(sceneRoot, mSlide);
                redSquare.setVisibility(View.INVISIBLE);
                greenSquare.setVisibility(View.INVISIBLE);
                hello.setVisibility(View.INVISIBLE);
            }
        });
        assertTrue(mListener.startLatch.await(1, TimeUnit.SECONDS));
        assertEquals(1, mListener.endLatch.getCount());
        assertEquals(View.VISIBLE, redSquare.getVisibility());
        assertEquals(View.VISIBLE, greenSquare.getVisibility());
        assertEquals(View.VISIBLE, hello.getVisibility());

        float redStartX = redSquare.getTranslationX();
        float redStartY = redSquare.getTranslationY();

        Thread.sleep(200);
        assertTranslation(redSquare);
        assertTranslation(greenSquare);
        assertTranslation(hello);

        final float redMidX = redSquare.getTranslationX();
        final float redMidY = redSquare.getTranslationY();

        switch (mSlideEdge) {
            case Gravity.LEFT:
            case Gravity.START:
                assertTrue("isn't sliding out to left. Expecting " + redStartX + " > " + redMidX,
                        redStartX > redMidX);
                break;
            case Gravity.RIGHT:
            case Gravity.END:
                assertTrue("isn't sliding out to right. Expecting " + redStartX + " < " + redMidX,
                        redStartX < redMidX);
                break;
            case Gravity.TOP:
                assertTrue("isn't sliding out to top. Expecting " + redStartY + " > " + redMidY,
                        redStartY > redSquare.getTranslationY());
                break;
            case Gravity.BOTTOM:
                assertTrue("isn't sliding out to bottom. Expecting " + redStartY + " < " + redMidY,
                        redStartY < redSquare.getTranslationY());
                break;
        }
        assertTrue(mListener.endLatch.await(1, TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();

        assertNoTranslation(redSquare);
        assertNoTranslation(greenSquare);
        assertNoTranslation(hello);
        assertEquals(View.INVISIBLE, redSquare.getVisibility());
        assertEquals(View.INVISIBLE, greenSquare.getVisibility());
        assertEquals(View.INVISIBLE, hello.getVisibility());
    }

    @Test
    @MediumTest
    public void slideIn() throws Throwable {
        mSlide.setSlideEdge(mSlideEdge);
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Activity activity = mActivityRule.getActivity();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.setContentView(R.layout.scene1);
            }
        });
        instrumentation.waitForIdleSync();

        final View redSquare = activity.findViewById(R.id.redSquare);
        final View greenSquare = activity.findViewById(R.id.greenSquare);
        final View hello = activity.findViewById(R.id.hello);
        final ViewGroup sceneRoot = (ViewGroup) activity.findViewById(R.id.holder);

        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                redSquare.setVisibility(View.INVISIBLE);
                greenSquare.setVisibility(View.INVISIBLE);
                hello.setVisibility(View.INVISIBLE);
            }
        });
        instrumentation.waitForIdleSync();

        // now slide in
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                TransitionManager.beginDelayedTransition(sceneRoot, mSlide);
                redSquare.setVisibility(View.VISIBLE);
                greenSquare.setVisibility(View.VISIBLE);
                hello.setVisibility(View.VISIBLE);
            }
        });
        assertTrue(mListener.startLatch.await(1, TimeUnit.SECONDS));

        assertEquals(1, mListener.endLatch.getCount());
        assertEquals(View.VISIBLE, redSquare.getVisibility());
        assertEquals(View.VISIBLE, greenSquare.getVisibility());
        assertEquals(View.VISIBLE, hello.getVisibility());

        final float redStartX = redSquare.getTranslationX();
        final float redStartY = redSquare.getTranslationY();

        Thread.sleep(200);
        assertTranslation(redSquare);
        assertTranslation(greenSquare);
        assertTranslation(hello);
        final float redMidX = redSquare.getTranslationX();
        final float redMidY = redSquare.getTranslationY();

        switch (mSlideEdge) {
            case Gravity.LEFT:
            case Gravity.START:
                assertTrue("isn't sliding in from left. Expecting " + redStartX + " < " + redMidX,
                        redStartX < redMidX);
                break;
            case Gravity.RIGHT:
            case Gravity.END:
                assertTrue("isn't sliding in from right. Expecting " + redStartX + " > " + redMidX,
                        redStartX > redMidX);
                break;
            case Gravity.TOP:
                assertTrue("isn't sliding in from top. Expecting " + redStartY + " < " + redMidY,
                        redStartY < redSquare.getTranslationY());
                break;
            case Gravity.BOTTOM:
                assertTrue("isn't sliding in from bottom. Expecting " + redStartY + " > " + redMidY,
                        redStartY > redSquare.getTranslationY());
                break;
        }
        assertTrue(mListener.endLatch.await(1, TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();

        assertNoTranslation(redSquare);
        assertNoTranslation(greenSquare);
        assertNoTranslation(hello);
        assertEquals(View.VISIBLE, redSquare.getVisibility());
        assertEquals(View.VISIBLE, greenSquare.getVisibility());
        assertEquals(View.VISIBLE, hello.getVisibility());
    }

    private void assertTranslation(View view) {
        switch (mSlide.getSlideEdge()) {
            case Gravity.LEFT:
            case Gravity.START:
                assertTrue(view.getTranslationX() < 0);
                assertEquals(0f, view.getTranslationY(), 0.01f);
                break;
            case Gravity.RIGHT:
            case Gravity.END:
                assertTrue(view.getTranslationX() > 0);
                assertEquals(0f, view.getTranslationY(), 0.01f);
                break;
            case Gravity.TOP:
                assertTrue(view.getTranslationY() < 0);
                assertEquals(0f, view.getTranslationX(), 0.01f);
                break;
            case Gravity.BOTTOM:
                assertTrue(view.getTranslationY() > 0);
                assertEquals(0f, view.getTranslationX(), 0.01f);
                break;
        }
    }

    private void assertNoTranslation(View view) {
        assertEquals(0f, view.getTranslationX(), 0.01f);
        assertEquals(0f, view.getTranslationY(), 0.01f);
    }
}

