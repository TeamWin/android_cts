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
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TRANSLUCENT_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.EXTRA_INTENTS;
import static android.server.wm.app.Components.TestActivity.COMMAND_START_ACTIVITIES;
import static android.server.wm.app27.Components.SDK_27_LAUNCHING_ACTIVITY;
import static android.server.wm.second.Components.SECOND_ACTIVITY;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CommandSession.ActivitySession;

import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

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

        mWmState.computeState(testActivity2.getComponentName());

        // Verify Activity was not started.
        assertFalse(mWmState.containsActivity(TEST_ACTIVITY));
        mWmState.assertResumedActivity(
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

        mWmState.computeState(TEST_ACTIVITY);
        mWmState.assertResumedActivity("Test Activity should be started with new task flag",
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
        mWmState.assertResumedActivity("Test Activity should be remained on top and resumed",
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

        mWmState.computeState(TEST_ACTIVITY);
        mWmState.assertResumedActivity("Test Activity should be resumed without older sdk",
                TEST_ACTIVITY);
    }

    /**
     * Assume there are 3 activities (A1, A2, A3) with different task affinities and the same uid.
     * After A1 called {@link Activity#startActivities} to start A2 (with NEW_TASK) and A3, the
     * result should be 2 tasks: [A1] and [A2, A3].
     */
    @Test
    public void testStartActivitiesInNewAndSameTask() {
        final int[] taskIds = startActivitiesAndGetTaskIds(new Intent[] {
                new Intent().setComponent(NO_RELAUNCH_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                new Intent().setComponent(LAUNCHING_ACTIVITY) });

        assertNotEquals("The activity with different task affinity started by flag NEW_TASK"
                + " should be in a different task", taskIds[0], taskIds[1]);
        assertEquals("The activity started without flag NEW_TASK should be put in the same task",
                taskIds[1], taskIds[2]);
    }

    /**
     * Assume there are 3 activities (A1, A2, B1) with default launch mode. The uid of B1 is
     * different from A1 and A2. After A1 called {@link Activity#startActivities} to start B1 and
     * A2, the result should be 3 tasks.
     */
    @Test
    public void testStartActivitiesWithDiffUidNotInSameTask() {
        final int[] taskIds = startActivitiesAndGetTaskIds(new Intent[] {
                new Intent().setComponent(SECOND_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                new Intent().setComponent(LAUNCHING_ACTIVITY) });

        assertNotEquals("The activity in a different application (uid) started by flag NEW_TASK"
                + " should be in a different task", taskIds[0], taskIds[1]);
        assertWithMessage("The last started activity should be in a different task because "
                + SECOND_ACTIVITY + " has a different uid from the source caller")
                        .that(taskIds[2]).isNotIn(Arrays.asList(taskIds[0], taskIds[1]));
    }

    /**
     * Invokes {@link android.app.Activity#startActivities} from {@link #TEST_ACTIVITY} and returns
     * the task id of each started activity (the index 0 will be the caller {@link #TEST_ACTIVITY}).
     */
    private int[] startActivitiesAndGetTaskIds(Intent[] intents) {
        final ActivitySession activity = createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder().setUseInstrumentation());
        final Bundle intentBundle = new Bundle();
        intentBundle.putParcelableArray(EXTRA_INTENTS, intents);
        // The {@link Activity#startActivities} cannot be called from the instrumentation
        // package because the implementation (given by test runner) may be overridden.
        activity.sendCommand(COMMAND_START_ACTIVITIES, intentBundle);

        final int[] taskIds = new int[intents.length + 1];
        // The {@code intents} are started, wait for the last (top) activity to be ready and then
        // verify their task ids.
        mWmState.computeState(intents[intents.length - 1].getComponent());
        taskIds[0] = mWmState.getTaskByActivity(TEST_ACTIVITY).getTaskId();
        for (int i = 0; i < intents.length; i++) {
            taskIds[i + 1] = mWmState.getTaskByActivity(intents[i].getComponent()).getTaskId();
        }
        return taskIds;
    }

    public static class TestActivity2 extends Activity {
    }
}
