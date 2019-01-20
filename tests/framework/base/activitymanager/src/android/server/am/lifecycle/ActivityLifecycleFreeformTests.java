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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.am.ActivityManagerState.STATE_PAUSED;
import static android.server.am.ActivityManagerState.STATE_RESUMED;
import static android.server.am.ActivityManagerState.STATE_STOPPED;
import static android.server.am.ComponentNameUtils.getWindowName;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESUME;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
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

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityLifecycleFreeformTests
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@Presubmit
@FlakyTest(bugId = 77652261)
public class ActivityLifecycleFreeformTests extends ActivityLifecycleClientTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsFreeform());
    }

    @Test
    public void testLaunchInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        final Bundle bundle = launchOptions.toBundle();

        // Launch an activity in freeform
        final Intent firstIntent =
                new Intent(InstrumentationRegistry.getContext(), FirstActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(firstIntent, bundle);

        // Wait and assert resume
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED,
                "Activity should be resumed after launch");
        LifecycleVerifier.assertLaunchSequence(FirstActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                true /* includeCallbacks */);
    }

    @Test
    public void testMultiLaunchInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        final Bundle bundle = launchOptions.toBundle();

        // Launch three activities in freeform
        final Intent firstIntent =
                new Intent(InstrumentationRegistry.getContext(), FirstActivity.class)
                        .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(firstIntent, bundle);

        final Intent secondIntent =
                new Intent(InstrumentationRegistry.getContext(), SecondActivity.class)
                        .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(secondIntent, bundle);

        final Intent thirdIntent =
                new Intent(InstrumentationRegistry.getContext(), ThirdActivity.class)
                        .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(thirdIntent, bundle);

        // Wait for resume
        final String message = "Activity should be resumed after launch";
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED, message);
        waitAndAssertActivityState(getComponentName(SecondActivity.class), STATE_RESUMED, message);
        waitAndAssertActivityState(getComponentName(ThirdActivity.class), STATE_RESUMED, message);

        // Assert lifecycle
        LifecycleVerifier.assertLaunchSequence(FirstActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(SecondActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(ThirdActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                true /* includeCallbacks */);
    }

    @Test
    public void testLaunchOccludingInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        final Bundle bundle = launchOptions.toBundle();

        // Launch two activities in freeform in the same task
        final Intent firstIntent =
                new Intent(InstrumentationRegistry.getContext(), FirstActivity.class)
                        .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(firstIntent, bundle);

        final Activity secondActivity = mSecondActivityTestRule.launchActivity(new Intent());

        final Intent thirdIntent =
                new Intent(InstrumentationRegistry.getContext(), ThirdActivity.class)
                        .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(thirdIntent, bundle);

        // Wait for valid states
        final String stopMessage = "Activity should be stopped after being covered above";
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_STOPPED,
                stopMessage);
        final String message = "Activity should be resumed after launch";
        waitAndAssertActivityState(getComponentName(SecondActivity.class), STATE_RESUMED, message);
        waitAndAssertActivityState(getComponentName(ThirdActivity.class), STATE_RESUMED, message);

        // Assert lifecycle
        LifecycleVerifier.assertLaunchAndStopSequence(FirstActivity.class, getLifecycleLog());
        LifecycleVerifier.assertLaunchSequence(SecondActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(ThirdActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                true /* includeCallbacks */);

        // Finish the activity that was occluding the first one
        getLifecycleLog().clear();
        secondActivity.finish();

        // Wait and assert the lifecycle
        mAmWmState.waitForWithAmState(
                (state) -> !state.containsActivity(getComponentName(SecondActivity.class)),
                "Waiting for activity to be removed");
        mAmWmState.waitForWithWmState(
                (state) -> !state.containsWindow(
                        getWindowName(getComponentName(SecondActivity.class))),
                "Waiting for activity window to be gone");
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED,
                "Activity must be resumed after occluding finished");

        assertFalse("Activity must be destroyed",
                mAmWmState.getAmState().containsActivity(getComponentName(SecondActivity.class)));
        assertFalse("Activity must be destroyed",
                mAmWmState.getWmState().containsWindow(
                        getWindowName(getComponentName(SecondActivity.class))));
        LifecycleVerifier.assertRestartAndResumeSequence(FirstActivity.class, getLifecycleLog());
        LifecycleVerifier.assertResumeToDestroySequence(SecondActivity.class, getLifecycleLog());
        LifecycleVerifier.assertSequence(ThirdActivity.class, getLifecycleLog(), new ArrayList<>(),
                "finishInOtherStack");
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                new ArrayList<>(), "finishInOtherStack");
    }

    @Test
    public void testLaunchTranslucentInFreeform() throws Exception {
        // Launch a fullscreen activity, mainly to prevent setting pending due to task switching.
        mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        final Bundle bundle = launchOptions.toBundle();

        // Launch two activities in freeform in the same task
        final Intent firstIntent =
                new Intent(InstrumentationRegistry.getContext(), FirstActivity.class)
                        .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(firstIntent, bundle);

        final Activity transparentActivity = mTranslucentActivityTestRule
                .launchActivity(new Intent());

        final Intent thirdIntent =
                new Intent(InstrumentationRegistry.getContext(), ThirdActivity.class)
                        .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(thirdIntent, bundle);

        // Wait for valid states
        final String pauseMessage = "Activity should be stopped after transparent launch above";
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_PAUSED,
                pauseMessage);
        final String message = "Activity should be resumed after launch";
        waitAndAssertActivityState(getComponentName(TranslucentActivity.class), STATE_RESUMED,
                message);
        waitAndAssertActivityState(getComponentName(ThirdActivity.class), STATE_RESUMED, message);

        // Assert lifecycle
        LifecycleVerifier.assertLaunchAndPauseSequence(FirstActivity.class, getLifecycleLog());
        LifecycleVerifier.assertLaunchSequence(TranslucentActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(ThirdActivity.class, getLifecycleLog(),
                false /* includeCallbacks */);
        LifecycleVerifier.assertLaunchSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                true /* includeCallbacks */);

        // Finish the activity that was occluding the first one
        getLifecycleLog().clear();
        transparentActivity.finish();

        // Wait and assert the lifecycle
        mAmWmState.waitForWithAmState(
                (state) -> !state.containsActivity(getComponentName(TranslucentActivity.class)),
                "Waiting for activity to be removed");
        mAmWmState.waitForWithWmState(
                (state) -> !state.containsWindow(
                        getWindowName(getComponentName(TranslucentActivity.class))),
                "Waiting for activity window to be gone");
        waitAndAssertActivityState(getComponentName(FirstActivity.class), STATE_RESUMED,
                "Activity must be resumed after occluding finished");


        assertFalse("Activity must be destroyed",
                mAmWmState.getAmState().containsActivity(
                        getComponentName(TranslucentActivity.class)));
        assertFalse("Activity must be destroyed",
                mAmWmState.getWmState().containsWindow(
                        getWindowName(getComponentName(TranslucentActivity.class))));
        LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                Arrays.asList(ON_RESUME), "finishTranslucentOnTop");
        LifecycleVerifier.assertResumeToDestroySequence(TranslucentActivity.class,
                getLifecycleLog());
        LifecycleVerifier.assertSequence(ThirdActivity.class, getLifecycleLog(), new ArrayList<>(),
                "finishInOtherStack");
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                new ArrayList<>(), "finishInOtherStack");
    }
}
