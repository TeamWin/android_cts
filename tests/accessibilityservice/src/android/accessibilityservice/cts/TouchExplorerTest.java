/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AsyncUtils.await;
import static android.accessibilityservice.cts.utils.GestureUtils.IS_ACTION_DOWN;
import static android.accessibilityservice.cts.utils.GestureUtils.IS_ACTION_MOVE;
import static android.accessibilityservice.cts.utils.GestureUtils.IS_ACTION_UP;
import static android.accessibilityservice.cts.utils.GestureUtils.add;
import static android.accessibilityservice.cts.utils.GestureUtils.ceil;
import static android.accessibilityservice.cts.utils.GestureUtils.click;
import static android.accessibilityservice.cts.utils.GestureUtils.diff;
import static android.accessibilityservice.cts.utils.GestureUtils.dispatchGesture;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTap;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTapAndHold;
import static android.accessibilityservice.cts.utils.GestureUtils.isRawAtPoint;
import static android.accessibilityservice.cts.utils.GestureUtils.multiTap;
import static android.accessibilityservice.cts.utils.GestureUtils.secondFingerMultiTap;
import static android.accessibilityservice.cts.utils.GestureUtils.swipe;
import static android.accessibilityservice.cts.utils.GestureUtils.times;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_HOVER_MOVE;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;

import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.MatcherAssert.assertThat;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.accessibilityservice.cts.AccessibilityGestureDispatchTest.GestureDispatchActivity;
import android.accessibilityservice.cts.utils.EventCapturingClickListener;
import android.accessibilityservice.cts.utils.EventCapturingHoverListener;
import android.accessibilityservice.cts.utils.EventCapturingLongClickListener;
import android.accessibilityservice.cts.utils.EventCapturingTouchListener;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.PointF;
import android.platform.test.annotations.AppModeFull;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * A set of tests for testing touch exploration. Each test dispatches a gesture and checks for the
 * appropriate hover and/or touch events followed by the appropriate accessibility events. Some
 * tests will then check for events from the view.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class TouchExplorerTest {
    // Constants
    private static final float GESTURE_LENGTH_INCHES = 1.0f;
    private static final int SWIPE_TIME_MILLIS = 400;
    private TouchExplorationStubAccessibilityService mService;
    private Instrumentation mInstrumentation;
    private UiAutomation mUiAutomation;
    private boolean mHasTouchscreen;
    private boolean mScreenBigEnough;
    private EventCapturingHoverListener mHoverListener = new EventCapturingHoverListener(false);
    private EventCapturingTouchListener mTouchListener = new EventCapturingTouchListener(false);
    private EventCapturingClickListener mClickListener = new EventCapturingClickListener();
    private EventCapturingLongClickListener mLongClickListener =
            new EventCapturingLongClickListener();

    private ActivityTestRule<GestureDispatchActivity> mActivityRule =
            new ActivityTestRule<>(GestureDispatchActivity.class, false);

    private InstrumentedAccessibilityServiceTestRule<TouchExplorationStubAccessibilityService>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    TouchExplorationStubAccessibilityService.class, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mServiceRule)
            .around(mDumpOnFailureRule);

    Point mCenter; // Center of screen. Gestures all start from this point.
    PointF mTapLocation;
    float mSwipeDistance;
    View mView;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiAutomation = mInstrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        PackageManager pm = mInstrumentation.getContext().getPackageManager();
        mHasTouchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                || pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
        // Find screen size, check that it is big enough for gestures.
        // Gestures will start in the center of the screen, so we need enough horiz/vert space.
        WindowManager windowManager =
                (WindowManager)
                        mInstrumentation.getContext().getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        mCenter = new Point((int) metrics.widthPixels / 2, (int) metrics.heightPixels / 2);
        mTapLocation = new PointF(mCenter);
        mScreenBigEnough = (metrics.widthPixels / (2 * metrics.xdpi) > GESTURE_LENGTH_INCHES);
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        mService = mServiceRule.enableService();
        mView = mActivityRule.getActivity().findViewById(R.id.full_screen_text_view);
        mView.setOnHoverListener(mHoverListener);
        mView.setOnTouchListener(mTouchListener);
        mInstrumentation.runOnMainSync(
                () -> {
                    mSwipeDistance = mView.getWidth() / 4;
                    mView.setOnClickListener(mClickListener);
                    mView.setOnLongClickListener(mLongClickListener);
                });
    }

    /** Test a slow swipe which should initiate touch exploration. */
    @Test
    @AppModeFull
    public void testSlowSwipe_initiatesTouchExploration() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0), SWIPE_TIME_MILLIS));
        mHoverListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_MOVE, ACTION_HOVER_EXIT);
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /** Test a fast swipe which should not initiate touch exploration. */
    @Test
    @AppModeFull
    public void testFastSwipe_doesNotInitiateTouchExploration() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0)));
        mHoverListener.assertNonePropagated();
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_GESTURE_DETECTION_START,
                TYPE_GESTURE_DETECTION_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /**
     * Test a two finger drag. TouchExplorer would perform a drag gesture when two fingers moving
     * in the same direction.
     */
    @Test
    @AppModeFull
    public void testTwoFingerDrag_dispatchesEventsBetweenFingers() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        // A two point moving that are in the same direction can perform a drag gesture by
        // TouchExplorer while one point moving can not perform a drag gesture. We use two swipes
        // to emulate a two finger drag gesture.
        final int twoFingerOffset = (int) mSwipeDistance;
        final PointF dragStart = mTapLocation;
        final PointF dragEnd = add(dragStart, 0, mSwipeDistance);
        final PointF finger1Start = add(dragStart, twoFingerOffset, 0);
        final PointF finger1End = add(finger1Start, 0, mSwipeDistance);
        final PointF finger2Start = add(dragStart, -twoFingerOffset, 0);
        final PointF finger2End = add(finger2Start, 0, mSwipeDistance);
        dispatch(swipe(finger1Start, finger1End, SWIPE_TIME_MILLIS),
                swipe(finger2Start, finger2End, SWIPE_TIME_MILLIS));
        List<MotionEvent> twoFingerPoints = mTouchListener.getRawEvents();

        // Check the drag events performed by a two finger drag. The moving locations would be
        // adjusted to the middle of two fingers.
        final int numEvents = twoFingerPoints.size();
        final int upEventIndex = numEvents - 1;
        final float intervalFraction = ((float) (twoFingerPoints.get(1).getEventTime()
                - twoFingerPoints.get(0).getEventTime())) / SWIPE_TIME_MILLIS;
        for (int i = 0; i < numEvents; i++) {
            MotionEvent moveEvent = twoFingerPoints.get(i);
            float fractionOfDrag = intervalFraction * (i + 1);
            if (i == 0) {
                PointF downPoint = add(finger2Start,
                        ceil(times(fractionOfDrag, diff(dragEnd, dragStart))));
                assertThat(moveEvent,
                        both(IS_ACTION_DOWN).and(isRawAtPoint(downPoint)));
            } else if (i == upEventIndex) {
                assertThat(moveEvent,
                        both(IS_ACTION_UP).and(isRawAtPoint(finger2End)));
            } else {
                PointF intermediatePoint = add(dragStart,
                        ceil(times(fractionOfDrag, diff(dragEnd, dragStart))));
                assertThat(moveEvent,
                        both(IS_ACTION_MOVE).and(isRawAtPoint(intermediatePoint)));
            }
        }
    }

    /** Test a basic single tap which should initiate touch exploration. */
    @Test
    @AppModeFull
    public void testSingleTap_initiatesTouchExploration() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(click(mTapLocation));
        mHoverListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /**
     * Test the case where we execute a "sloppy" double tap, meaning that the second tap isn't
     * exactly in the same location as the first but still within tolerances. It should behave the
     * same as a standard double tap.
     */
    @Test
    @AppModeFull
    public void testSloppyDoubleTapAccessibilityFocus_performsClick() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        syncAccessibilityFocusToInputFocus();
        int slop = ViewConfiguration.get(mInstrumentation.getContext()).getScaledDoubleTapSlop();
        dispatch(multiTap(mTapLocation, 2, slop));
        mHoverListener.assertNonePropagated();
        // The click should not be delivered via touch events in this case.
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_INTERACTION_END,
                TYPE_VIEW_CLICKED);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we want to click on the item that has accessibility focus by using
     * AccessibilityNodeInfo.performAction.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAccessibilityFocus_performsClick() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        syncAccessibilityFocusToInputFocus();
        dispatch(doubleTap(mTapLocation));
        mHoverListener.assertNonePropagated();
        // The click should not be delivered via touch events in this case.
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_INTERACTION_END,
                TYPE_VIEW_CLICKED);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we double tap but there is no  accessibility focus. Nothing should
     * happen.
     */
    @Test
    @AppModeFull
    public void testDoubleTapNoAccessibilityFocus_doesNotPerformClick() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(doubleTap(mTapLocation));
        mHoverListener.assertNonePropagated();
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                 TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        mClickListener.assertNoneClicked();
    }

    /** Test the case where we want to long click on the item that has accessibility focus. */
    @Test
    @AppModeFull
    public void testDoubleTapAndHoldAccessibilityFocus_performsLongClick() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        syncAccessibilityFocusToInputFocus();
        dispatch(doubleTapAndHold(mTapLocation));
        mHoverListener.assertNonePropagated();
        // The click should not be delivered via touch events in this case.
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_VIEW_LONG_CLICKED,
                TYPE_TOUCH_INTERACTION_END);
        mLongClickListener.assertLongClicked(mView);
    }

    /**
     * Test the case where we double tap and hold but there is no accessibility focus.
     * Nothing should happen.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAndHoldNoAccessibilityFocus_doesNotPerformLongClick() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(doubleTap(mTapLocation));
        mHoverListener.assertNonePropagated();
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        mLongClickListener.assertNoneLongClicked();
    }

    /**
     * Test the case where we want to double tap using a second finger while the first finger is
     * touch exploring.
     */
    @Test
    @AppModeFull
    public void testSecondFingerDoubleTapTouchExploring_performsClick() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        syncAccessibilityFocusToInputFocus();
        // hold the first finger for long enough to trigger touch exploration before double-tapping.
        // Touch exploration is triggered after the double tap timeout.
        dispatch(
                secondFingerMultiTap(
                        mTapLocation,
                        add(mTapLocation, mSwipeDistance, 0),
                        2,
                        ViewConfiguration.getDoubleTapTimeout() + 50));
        mHoverListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END,
                TYPE_VIEW_CLICKED);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we want to double tap using a second finger without triggering touch
     * exploration.
     */
    @Test
    @AppModeFull
    public void testSecondFingerDoubleTapNotTouchExploring_performsClick() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        syncAccessibilityFocusToInputFocus();
        // Hold the first finger for less than the double tap timeout which will not trigger touch
        // exploration.
        // Touch exploration is triggered after the double tap timeout.
        dispatch(
                secondFingerMultiTap(
                        mTapLocation,
                        add(mTapLocation, mSwipeDistance, 0),
                        2,
                        ViewConfiguration.getDoubleTapTimeout() / 3));
        mHoverListener.assertNonePropagated();
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_INTERACTION_END,
                TYPE_VIEW_CLICKED);
        mClickListener.assertClicked(mView);
    }

    /**
     * This method tests a three-finger swipe. The gesture will be delegated to the view as-is. This
     * is distinct from dragging, where two fingers are delegated to the view as one finger. Note
     * that because multi-finger gestures are disabled this gesture will not be handled by the
     * gesture detector.
     */
    @Test
    @AppModeFull
    public void testThreeFingerMovement_shouldDelegate() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        // Move three fingers down the screen slowly.
        PointF finger1Start = add(mTapLocation, -mSwipeDistance, 0);
        PointF finger1End = add(mTapLocation, -mSwipeDistance, mSwipeDistance);
        PointF finger2Start = mTapLocation;
        PointF finger2End = add(mTapLocation, 0, mSwipeDistance);
        PointF finger3Start = add(mTapLocation, mSwipeDistance, 0);
        PointF finger3End = add(mTapLocation, mSwipeDistance, mSwipeDistance);
        StrokeDescription swipe1 = swipe(finger1Start, finger1End, SWIPE_TIME_MILLIS);
        StrokeDescription swipe2 = swipe(finger2Start, finger2End, SWIPE_TIME_MILLIS);
        StrokeDescription swipe3 = swipe(finger3Start, finger3End, SWIPE_TIME_MILLIS);
        dispatch(swipe1, swipe2, swipe3);
        mHoverListener.assertNonePropagated();
        mTouchListener.assertPropagated(
                ACTION_DOWN,
                ACTION_POINTER_DOWN,
                ACTION_POINTER_DOWN,
                ACTION_MOVE,
                ACTION_POINTER_UP,
                ACTION_POINTER_UP,
                ACTION_UP);
    }

    /**
     * This method tests the case where two fingers are moving independently. The gesture will be
     * delegated to the view as-is. This is distinct from dragging, where two fingers are delegated
     * to the view as one finger.
     */
    @Test
    @AppModeFull
    public void testTwoFingersMovingIndependently_shouldDelegate() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        // Move two fingers towards eacher slowly.
        PointF finger1Start = add(mTapLocation, -mSwipeDistance, 0);
        PointF finger1End = add(mTapLocation, -10, 0);
        StrokeDescription swipe1 = swipe(finger1Start, finger1End, SWIPE_TIME_MILLIS);
        PointF finger2Start = add(mTapLocation, mSwipeDistance, 0);
        PointF finger2End = add(mTapLocation, 10, 0);
        StrokeDescription swipe2 = swipe(finger2Start, finger2End, SWIPE_TIME_MILLIS);
        dispatch(swipe1, swipe2);
        mHoverListener.assertNonePropagated();
        mTouchListener.assertPropagated(
                ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_MOVE, ACTION_POINTER_UP, ACTION_UP);
    }

    public void dispatch(StrokeDescription firstStroke, StrokeDescription... rest) {
        GestureDescription.Builder builder =
                new GestureDescription.Builder().addStroke(firstStroke);
        for (StrokeDescription stroke : rest) {
            builder.addStroke(stroke);
        }
        dispatch(builder.build());
    }

    public void dispatch(GestureDescription gesture) {
        await(dispatchGesture(mService, gesture));
    }

    /** Set the accessibility focus to the element that has input focus. */
    private void syncAccessibilityFocusToInputFocus() {
        mService.runOnServiceSync(
                () -> {
                    mUiAutomation
                            .getRootInActiveWindow()
                            .findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            .performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                });
    }
}
