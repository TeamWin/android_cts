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

package android.server.wm;

import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
import static android.server.wm.ActivityManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TRANSLUCENT_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.EXTRA_INTENTS;
import static android.server.wm.app.Components.TestActivity.COMMAND_START_ACTIVITIES;
import static android.server.wm.app27.Components.SDK_27_LAUNCHING_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CommandSession.ActivitySession;
import android.server.wm.CommandSession.ActivitySessionClient;

import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:StartActivityTests
 */
@Presubmit
public class StartActivityTests extends ActivityManagerTestBase {

    @Rule
    public final ActivityTestRule<TestActivity2> mTestActivity2Rule =
            new ActivityTestRule<>(TestActivity2.class, false /* initialTouchMode */,
                    false /* launchActivity */);

    /**
     * Ensures {@link Activity} can only be launched from an {@link Activity}
     * {@link android.content.Context}.
     */
    @Test
    public void testStartActivityContexts() {
        // Launch Activity from application context.
        getLaunchActivityBuilder()
                .setTargetActivity(TEST_ACTIVITY)
                .setUseApplicationContext(true)
                .setSuppressExceptions(true)
                .execute();

        // Launch second Activity from Activity Context to ensure previous Activity has launched.
        final Activity testActivity2 = mTestActivity2Rule.launchActivity(null);

        mAmWmState.computeState(testActivity2.getComponentName());

        // Verify Activity was not started.
        assertFalse(mAmWmState.getAmState().containsActivity(TEST_ACTIVITY));
        mAmWmState.assertResumedActivity(
                "Activity launched from activity context should be present",
                testActivity2.getComponentName());
    }

    /**
     * Ensures you can start an {@link Activity} from a non {@link Activity}
     * {@link android.content.Context} with the {@code FLAG_ACTIVITY_NEW_TASK}.
     */
    @Test
    public void testStartActivityNewTask() throws Exception {
        // Launch Activity from application context.
        getLaunchActivityBuilder()
                .setTargetActivity(TEST_ACTIVITY)
                .setUseApplicationContext(true)
                .setSuppressExceptions(true)
                .setNewTask(true)
                .execute();

        mAmWmState.computeState(TEST_ACTIVITY);
        mAmWmState.assertResumedActivity("Test Activity should be started with new task flag",
                TEST_ACTIVITY);
    }

    @Test
    public void testStartActivityTaskLaunchBehind() {
        // launch an activity
        getLaunchActivityBuilder()
                .setTargetActivity(TEST_ACTIVITY)
                .setUseInstrumentation()
                .setNewTask(true)
                .execute();

        // launch an activity behind
        getLaunchActivityBuilder()
                .setTargetActivity(TRANSLUCENT_ACTIVITY)
                .setUseInstrumentation()
                .setIntentFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
                .setNewTask(true)
                .setLaunchTaskBehind(true)
                .execute();

        waitAndAssertActivityState(TRANSLUCENT_ACTIVITY, STATE_STOPPED,
                "Activity should be stopped");
        mAmWmState.assertResumedActivity("Test Activity should be remained on top and resumed",
                TEST_ACTIVITY);
    }

    /**
     * Ensures you can start an {@link Activity} from a non {@link Activity}
     * {@link android.content.Context} when the target sdk is between N and O Mr1.
     * @throws Exception
     */
    @Test
    public void testLegacyStartActivityFromNonActivityContext() {
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                .setLaunchingActivity(SDK_27_LAUNCHING_ACTIVITY)
                .setUseApplicationContext(true)
                .execute();

        mAmWmState.computeState(TEST_ACTIVITY);
        mAmWmState.assertResumedActivity("Test Activity should be resumed without older sdk",
                TEST_ACTIVITY);
    }

    /**
     * <pre>
     * Assume there are 3 activities (X, Y, Z) have different task affinities:
     * 1. Activity X started.
     * 2. X launches 2 activities (Y with NEW_TASK, Z) by {@link Activity#startActivities}.
     * Expect the result should be 2 tasks: [X] and [Y, Z].
     * </pre>
     */
    @Test
    public void testStartActivitiesInNewAndSameTask() {
        final ActivitySession activity = ActivitySessionClient.create().startActivity(
                getLaunchActivityBuilder().setUseInstrumentation()
                        .setTargetActivity(TEST_ACTIVITY));

        final Intent[] intents = {
                new Intent().setComponent(NO_RELAUNCH_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                new Intent().setComponent(LAUNCHING_ACTIVITY)
        };

        final Bundle intentBundle = new Bundle();
        intentBundle.putParcelableArray(EXTRA_INTENTS, intents);
        // The {@link Activity#startActivities} cannot be called from the instrumentation
        // package because the implementation (given by test runner) may be overridden.
        activity.sendCommand(COMMAND_START_ACTIVITIES, intentBundle);

        // The {@code intents} are started, wait for the last (top) activity to be ready and then
        // verify their task ids.
        mAmWmState.computeState(intents[1].getComponent());
        final ActivityManagerState amState = mAmWmState.getAmState();
        final int callerTaskId = amState.getTaskByActivity(TEST_ACTIVITY).getTaskId();
        final int i0TaskId = amState.getTaskByActivity(intents[0].getComponent()).getTaskId();
        final int i1TaskId = amState.getTaskByActivity(intents[1].getComponent()).getTaskId();

        assertNotEquals("The activities started by startActivities() should have a different task"
                + " from their caller activity", callerTaskId, i0TaskId);
        assertEquals("The activities started by startActivities() should be put in the same task",
                i0TaskId, i1TaskId);
    }

    public static class TestActivity2 extends Activity {
    }
}
