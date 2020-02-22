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

package android.server.cts;

import java.util.Arrays;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test \
 *      CtsServicesHostTestCases android.server.cts.StartActivityTests
 */
public class StartActivityTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_ACTION_START_ACTIVITIES =
            "android.server.cts.TestActivity.start_activities";
    private static final String TEST_ACTIVITY = "android.server.cts/.TestActivity";
    private static final String NO_RELAUNCH_ACTIVITY = "android.server.cts/.NoRelaunchActivity";
    private static final String LAUNCHING_ACTIVITY = "android.server.cts/.LaunchingActivity";
    private static final String SECOND_ACTIVITY = "android.server.cts.second/.SecondActivity";
    private static final String FLAG_ACTIVITY_NEW_TASK = "0x10000000";

    /**
     * Assume there are 3 activities (A1, A2, A3) with different task affinities and the same uid.
     * After A1 called {@link Activity#startActivities} to start A2 (with NEW_TASK) and A3, the
     * result should be 2 tasks: [A1] and [A2, A3].
     */
    public void testStartActivitiesInNewAndSameTask() throws Exception {
        final int[] taskIds = startActivitiesAndGetTaskIds(
                new String[] { NO_RELAUNCH_ACTIVITY, LAUNCHING_ACTIVITY },
                new String[] { FLAG_ACTIVITY_NEW_TASK, "0" });

        assertNotSame("The activity with different task affinity started by flag NEW_TASK"
                + " should be in a different task", taskIds[0], taskIds[1]);
        assertEquals("The activity started without flag NEW_TASK should be put in the same task",
                taskIds[1], taskIds[2]);
    }

    /**
     * Assume there are 3 activities (A1, A2, B1) with default launch mode. The uid of B1 is
     * different from A1 and A2. After A1 called {@link Activity#startActivities} to start B1 and
     * A2, the result should be 3 tasks.
     */
    public void testStartActivitiesWithDiffUidNotInSameTask() throws Exception {
        final int[] taskIds = startActivitiesAndGetTaskIds(
                new String[] { SECOND_ACTIVITY, LAUNCHING_ACTIVITY },
                new String[] { FLAG_ACTIVITY_NEW_TASK, "0" });

        assertNotSame("The activity in a different application (uid) started by flag NEW_TASK"
                + " should be in a different task", taskIds[0], taskIds[1]);
        assertFalse("The last started activity should be in a different task because "
                + SECOND_ACTIVITY + " has a different uid from the source caller",
                Arrays.asList(taskIds[0], taskIds[1]).contains(taskIds[2]));
    }

    /**
     * Invokes {@link android.app.Activity#startActivities} from {@link #TEST_ACTIVITY} and returns
     * the task id of each started activity (the index 0 will be the caller {@link #TEST_ACTIVITY}).
     */
    private int[] startActivitiesAndGetTaskIds(String[] activityNames, String[] activityFlags)
            throws Exception {
        launchActivity(TEST_ACTIVITY);
        executeShellCommand("am broadcast -a " + TEST_ACTIVITY_ACTION_START_ACTIVITIES
                + " --esa names " + String.join(",", activityNames)
                + " --eia flags " + String.join(",", activityFlags));

        final int[] taskIds = new int[activityNames.length + 1];
        // The activities are started, wait for the last (top) activity to be ready and then verify
        // their task ids.
        mAmWmState.computeState(mDevice, new String[] { activityNames[activityNames.length - 1] });
        final ActivityManagerState amState = mAmWmState.getAmState();
        taskIds[0] = amState.getTaskByActivityName(TEST_ACTIVITY).mTaskId;
        for (int i = 0; i < activityNames.length; i++) {
            taskIds[i + 1] = amState.getTaskByActivityName(activityNames[i]).mTaskId;
        }
        return taskIds;
    }
}
