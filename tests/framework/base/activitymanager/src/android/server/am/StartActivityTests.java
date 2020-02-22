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

package android.server.am;

import static android.server.am.Components.LAUNCHING_ACTIVITY;
import static android.server.am.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.am.Components.TEST_ACTIVITY;
import static android.server.am.Components.TestActivity.EXTRA_INTENTS;
import static android.server.am.Components.TestActivity.TEST_ACTIVITY_ACTION_START_ACTIVITIES;
import static android.server.am.app27.Components.SDK_27_LAUNCHING_ACTIVITY;
import static android.server.am.second.Components.SECOND_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:StartActivityTests
 */
@Presubmit
@FlakyTest
public class StartActivityTests extends ActivityManagerTestBase {

    @Rule
    public final ActivityTestRule<TestActivity2> mTestActivity2Rule =
            new ActivityTestRule<>(TestActivity2.class);

    /**
     * Ensures {@link Activity} can only be launched from an {@link Activity}
     * {@link android.content.Context}.
     */
    @Test
    public void testStartActivityContexts() throws Exception {
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

    /**
     * Ensures you can start an {@link Activity} from a non {@link Activity}
     * {@link android.content.Context} when the target sdk is between N and O Mr1.
     * @throws Exception
     */
    @Test
    public void testLegacyStartActivityFromNonActivityContext() throws Exception {
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                .setLaunchingActivity(SDK_27_LAUNCHING_ACTIVITY)
                .setUseApplicationContext(true)
                .execute();

        mAmWmState.computeState(TEST_ACTIVITY);
        mAmWmState.assertResumedActivity("Test Activity should be resumed without older sdk",
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
        assertFalse("The last started activity should be in a different task because "
                + SECOND_ACTIVITY + " has a different uid from the source caller",
                Arrays.asList(taskIds[0], taskIds[1]).contains(taskIds[2]));
    }

    /**
     * Invokes {@link android.app.Activity#startActivities} from {@link #TEST_ACTIVITY} and returns
     * the task id of each started activity (the index 0 will be the caller {@link #TEST_ACTIVITY}).
     */
    private int[] startActivitiesAndGetTaskIds(Intent[] intents) {
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                .setUseInstrumentation().execute();
        // The {@link Activity#startActivities} cannot be called from the instrumentation
        // package because the implementation (given by test runner) may be overridden.
        final Intent intent = new Intent(TEST_ACTIVITY_ACTION_START_ACTIVITIES);
        intent.putExtra(EXTRA_INTENTS, intents);
        mContext.sendBroadcast(intent);

        final int[] taskIds = new int[intents.length + 1];
        // The {@code intents} are started, wait for the last (top) activity to be ready and then
        // verify their task ids.
        mAmWmState.computeState(intents[intents.length - 1].getComponent());
        final ActivityManagerState amState = mAmWmState.getAmState();
        taskIds[0] = amState.getTaskByActivity(TEST_ACTIVITY).mTaskId;
        for (int i = 0; i < intents.length; i++) {
            taskIds[i + 1] = amState.getTaskByActivity(intents[i].getComponent()).mTaskId;
        }
        return taskIds;
    }

    public static class TestActivity2 extends Activity {
    }
}
