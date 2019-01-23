/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.am.lifecycle;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.am.Components.PipActivity.EXTRA_ENTER_PIP;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_ACTIVITY_RESULT;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_CREATE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_DESTROY;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_PAUSE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_POST_CREATE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESTART;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESUME;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_START;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_STOP;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.PRE_ON_CREATE;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityLifecycleSplitScreenTests
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@Presubmit
@FlakyTest(bugId = 77652261)
public class ActivityLifecycleSplitScreenTests extends ActivityLifecycleClientTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsSplitScreenMultiWindow());
    }

    @Test
    public void testResumedWhenRecreatedFromInNonFocusedStack() throws Exception {
        // Launch first activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());

        // Launch second activity to stop first
        final Activity secondActivity =
                mSecondActivityTestRule.launchActivity(new Intent());

        // Wait for second activity to resume. We must also wait for the first activity to stop
        // so that this event is not included in the logs.
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME),
                state(firstActivity, ON_STOP));

        // Enter split screen
        moveTaskToPrimarySplitScreen(secondActivity.getTaskId());

        // CLear logs so we can capture just the destroy sequence
        getLifecycleLog().clear();

        // Start an activity in separate task (will be placed in secondary stack)
        getLaunchActivityBuilder().execute();

        // Finish top activity
        secondActivity.finish();

        waitAndAssertActivityStates(state(secondActivity, ON_DESTROY));
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        // Verify that the first activity was recreated to resume as it was created before
        // windowing mode was switched
        LifecycleVerifier.assertRecreateAndResumeSequence(FirstActivity.class, getLifecycleLog());
    }

    @Test
    public void testOccludingMovedBetweenStacks() throws Exception {
        // Launch first activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        // Enter split screen
        moveTaskToPrimarySplitScreen(firstActivity.getTaskId());
        waitAndAssertActivityStates(state(firstActivity, ON_PAUSE));

        final ComponentName firstActivityName = getComponentName(FirstActivity.class);
        mAmWmState.computeState(firstActivityName);
        int primarySplitStack = mAmWmState.getAmState().getStackIdByActivity(firstActivityName);

        // Launch second activity to side
        getLifecycleLog().clear();
        final Activity secondActivity = mSecondActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        // Wait for second activity to resume.
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME),
                state(firstActivity, ON_RESUME));
        LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                Arrays.asList(ON_RESUME), "launchToSide");

        // Launch third activity on top of second
        getLifecycleLog().clear();
        final Activity thirdActivity = mThirdActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));
        waitAndAssertActivityStates(state(thirdActivity, ON_RESUME),
                state(secondActivity, ON_STOP));

        // Move occluding third activity to side, it will occlude first now
        getLifecycleLog().clear();
        moveActivityToStack(getComponentName(ThirdActivity.class), primarySplitStack);

        waitAndAssertActivityStates(state(secondActivity, ON_RESUME),
                state(firstActivity, ON_STOP));
        LifecycleVerifier.assertSequence(ThirdActivity.class, getLifecycleLog(), new ArrayList<>(),
                "moveToSide");
        LifecycleVerifier.assertRestartAndResumeSequence(SecondActivity.class, getLifecycleLog());
        LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                Arrays.asList(ON_PAUSE, ON_STOP), "moveToSide");
    }

    @Test
    public void testTranslucentMovedBetweenStacks() throws Exception {
        // Launch first activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        // Enter split screen
        moveTaskToPrimarySplitScreen(firstActivity.getTaskId());
        waitAndAssertActivityStates(state(firstActivity, ON_PAUSE));

        final ComponentName firstActivityName = getComponentName(FirstActivity.class);
        mAmWmState.computeState(firstActivityName);
        int primarySplitStack = mAmWmState.getAmState().getStackIdByActivity(firstActivityName);

        // Launch second activity to side
        getLifecycleLog().clear();
        final Activity secondActivity = mSecondActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        // Wait for second activity to resume.
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME),
                state(firstActivity, ON_RESUME));
        LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                Arrays.asList(ON_RESUME), "launchToSide");

        // Launch translucent activity on top of second
        getLifecycleLog().clear();

        final Activity translucentActivity = mTranslucentActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));
        waitAndAssertActivityStates(state(translucentActivity, ON_RESUME));
        // Second activity should stay resumed, because it's in a separate stack below the
        // translucent activity.
        LifecycleVerifier.assertSequence(SecondActivity.class, getLifecycleLog(),
                new ArrayList<>(), "moveToSide");

        // Move translucent activity to side, it will be on top of the first now
        getLifecycleLog().clear();
        moveActivityToStack(getComponentName(TranslucentActivity.class), primarySplitStack);

        waitAndAssertActivityStates(state(firstActivity, ON_PAUSE));
        LifecycleVerifier.assertSequence(TranslucentActivity.class, getLifecycleLog(),
                new ArrayList<>(), "moveToSide");
        LifecycleVerifier.assertSequence(SecondActivity.class, getLifecycleLog(),
                new ArrayList<>(), "moveToSide");
        LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                Arrays.asList(ON_PAUSE), "moveToSide");
    }

    @Test
    public void testResultInNonFocusedStack() throws Exception {
        // Launch first activity
        final Activity callbackTrackingActivity =
                mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        // Wait for first activity to resume
        waitAndAssertActivityStates(state(callbackTrackingActivity, ON_RESUME));

        // Enter split screen, the activity will be relaunched.
        getLifecycleLog().clear();
        moveTaskToPrimarySplitScreen(callbackTrackingActivity.getTaskId(), true /* showRecents */);
        // Wait for multi-window mode change that will come after activity relaunch and resume.
        waitAndAssertActivityStates(state(callbackTrackingActivity, ON_MULTI_WINDOW_MODE_CHANGED));
        final List<LifecycleLog.ActivityCallback> splitScreenMoveSequence =
                Arrays.asList(ON_PAUSE, ON_STOP, ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START,
                ON_POST_CREATE, ON_RESUME, ON_MULTI_WINDOW_MODE_CHANGED);
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                splitScreenMoveSequence, "moveToPrimarySplitScreen");
        getLifecycleLog().clear();

        // Launch second activity
        // Create an ActivityMonitor that catch ChildActivity and return mock ActivityResult:
        Instrumentation.ActivityMonitor activityMonitor = InstrumentationRegistry
                .getInstrumentation()
                .addMonitor(SecondActivity.class.getName(), null /* activityResult */,
                        false /* block */);

        callbackTrackingActivity.startActivityForResult(
                new Intent(callbackTrackingActivity, SecondActivity.class), 1 /* requestCode */);

        // Wait for the ActivityMonitor to be hit
        final Activity secondActivity = InstrumentationRegistry.getInstrumentation()
                .waitForMonitorWithTimeout(activityMonitor, 5 * 1000);

        // Wait for second activity to resume
        assertNotNull("Second activity should be started", secondActivity);
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME));

        // Verify if the first activity stopped (since it is not currently visible)
        waitAndAssertActivityStates(state(callbackTrackingActivity, ON_STOP));

        // Start an activity in separate task (will be placed in secondary stack)
        final Activity thirdActivity = mThirdActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        // Wait for third activity to resume
        waitAndAssertActivityStates(state(thirdActivity, ON_RESUME));

        // Finish top activity and verify that activity below became focused.
        getLifecycleLog().clear();
        secondActivity.setResult(Activity.RESULT_OK);
        secondActivity.finish();

        // Check that activity was resumed and result was delivered
        waitAndAssertActivityStates(state(callbackTrackingActivity, ON_RESUME));
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                Arrays.asList(ON_RESTART, ON_START, ON_ACTIVITY_RESULT, ON_RESUME), "resume");
    }

    @Test
    public void testResumedWhenRestartedFromInNonFocusedStack() throws Exception {
        // Launch first activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());

        // Wait for first activity to resume
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        // Enter split screen
        getLifecycleLog().clear();
        moveTaskToPrimarySplitScreen(firstActivity.getTaskId(), true /* showRecents */);
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));
        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog(), ON_RESUME);

        // Launch second activity, first become stopped
        final Activity secondActivity =
                mSecondActivityTestRule.launchActivity(new Intent());

        // Wait for second activity to resume and first to stop
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME));
        waitAndAssertActivityStates(state(firstActivity, ON_STOP));

        // Start an activity in separate task (will be placed in secondary stack)
        final Activity newTaskActivity = mThirdActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        waitAndAssertActivityStates(state(newTaskActivity, ON_RESUME));

        getLifecycleLog().clear();

        // Finish top activity
        secondActivity.finish();

        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));
        waitAndAssertActivityStates(state(secondActivity, ON_DESTROY));

        // Verify that the first activity was restarted to resumed state as it was brought back
        // after windowing mode was switched
        LifecycleVerifier.assertRestartAndResumeSequence(FirstActivity.class, getLifecycleLog());
        LifecycleVerifier.assertResumeToDestroySequence(SecondActivity.class, getLifecycleLog());
    }

    @Test
    public void testResumedTranslucentWhenRestartedFromInNonFocusedStack() throws Exception {
        // Launch first activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());

        // Wait for first activity to resume
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        // Enter split screen
        moveTaskToPrimarySplitScreen(firstActivity.getTaskId(), true /* showRecents */);

        // Launch a translucent activity, first become paused
        final Activity translucentActivity =
                mTranslucentActivityTestRule.launchActivity(new Intent());

        // Wait for translucent activity to resume and first to pause
        waitAndAssertActivityStates(state(translucentActivity, ON_RESUME));
        waitAndAssertActivityStates(state(firstActivity, ON_PAUSE));

        // Start an activity in separate task (will be placed in secondary stack)
        final Activity newTaskActivity = mThirdActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        waitAndAssertActivityStates(state(newTaskActivity, ON_RESUME));

        getLifecycleLog().clear();

        // Finish top activity
        translucentActivity.finish();

        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));
        waitAndAssertActivityStates(state(translucentActivity, ON_DESTROY));

        // Verify that the first activity was resumed
        LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                Arrays.asList(ON_RESUME), "resume");
        LifecycleVerifier.assertResumeToDestroySequence(TranslucentActivity.class,
                getLifecycleLog());
    }

    @Test
    public void testLifecycleOnMoveToFromSplitScreenRelaunch() throws Exception {
        // Launch a singleTop activity
        final Activity testActivity =
                mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        // Wait for the activity to resume
        waitAndAssertActivityStates(state(testActivity, ON_RESUME));
        LifecycleVerifier.assertLaunchSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                true /* includeCallbacks */);

        // Enter split screen
        getLifecycleLog().clear();
        setActivityTaskWindowingMode(CALLBACK_TRACKING_ACTIVITY,
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);

        // Wait for the activity to relaunch
        final List<LifecycleLog.ActivityCallback> expectedEnterSequence =
                Arrays.asList(ON_PAUSE, ON_STOP, ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START,
                        ON_POST_CREATE, ON_RESUME, ON_MULTI_WINDOW_MODE_CHANGED);
        waitForActivityTransitions(CallbackTrackingActivity.class, expectedEnterSequence);

        // Verify that the activity was relaunched and received multi-window mode change
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                expectedEnterSequence, "moveToSplitScreen");

        // Exit split-screen
        getLifecycleLog().clear();
        setActivityTaskWindowingMode(CALLBACK_TRACKING_ACTIVITY, WINDOWING_MODE_FULLSCREEN);

        // Wait for the activity to resume
        final List<LifecycleLog.ActivityCallback> expectedExitSequence = expectedEnterSequence;
        waitForActivityTransitions(CallbackTrackingActivity.class, expectedExitSequence);

        // Verify that the activity was relaunched and received multi-window mode change
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                expectedExitSequence, "moveFromSplitScreen");
    }

    @Test
    public void testLifecycleOnMoveToFromSplitScreenNoRelaunch() throws Exception {
        // Launch a singleTop activity
        final Activity testActivity =
                mConfigChangeHandlingActivityTestRule.launchActivity(new Intent());

        // Wait for the activity to resume
        waitAndAssertActivityStates(state(testActivity, ON_RESUME));
        LifecycleVerifier.assertLaunchSequence(ConfigChangeHandlingActivity.class,
                getLifecycleLog(), true /* includeCallbacks */);

        // Enter split screen
        getLifecycleLog().clear();
        setActivityTaskWindowingMode(CONFIG_CHANGE_HANDLING_ACTIVITY,
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);

        // Wait for the activity to receive the change
        waitForActivityTransitions(ConfigChangeHandlingActivity.class,
                Arrays.asList(ON_MULTI_WINDOW_MODE_CHANGED));

        // Verify that the activity was relaunched and received multi-window mode change
        LifecycleVerifier.assertSequence(ConfigChangeHandlingActivity.class, getLifecycleLog(),
                Arrays.asList(ON_MULTI_WINDOW_MODE_CHANGED), "moveToSplitScreen");

        // Exit split-screen
        getLifecycleLog().clear();
        setActivityTaskWindowingMode(CONFIG_CHANGE_HANDLING_ACTIVITY, WINDOWING_MODE_FULLSCREEN);

        // Wait for the activity to receive the change
        waitForActivityTransitions(ConfigChangeHandlingActivity.class,
                Arrays.asList(ON_MULTI_WINDOW_MODE_CHANGED));

        // Verify that the activity was relaunched and received multi-window mode change
        LifecycleVerifier.assertSequence(ConfigChangeHandlingActivity.class, getLifecycleLog(),
                Arrays.asList(ON_MULTI_WINDOW_MODE_CHANGED), "moveFromSplitScreen");
    }
}
