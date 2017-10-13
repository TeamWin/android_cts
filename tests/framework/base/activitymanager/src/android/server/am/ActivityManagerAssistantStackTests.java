/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.am.ActivityManagerState.STATE_RESUMED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

/**
 * Build: mmma -j32 cts/tests/framework/base
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.ActivityManagerAssistantStackTests
 */
//@Presubmit b/67706642
public class ActivityManagerAssistantStackTests extends ActivityManagerTestBase {

    private static final String VOICE_INTERACTION_SERVICE = "AssistantVoiceInteractionService";

    private static final String TEST_ACTIVITY = "TestActivity";
    private static final String ANIMATION_TEST_ACTIVITY = "AnimationTestActivity";
    private static final String DOCKED_ACTIVITY = "DockedActivity";
    private static final String ASSISTANT_ACTIVITY = "AssistantActivity";
    private static final String TRANSLUCENT_ASSISTANT_ACTIVITY = "TranslucentAssistantActivity";
    private static final String LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION =
            "LaunchAssistantActivityFromSession";
    private static final String LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK =
            "LaunchAssistantActivityIntoAssistantStack";
    private static final String PIP_ACTIVITY = "PipActivity";

    private static final String EXTRA_ENTER_PIP = "enter_pip";
    private static final String EXTRA_LAUNCH_NEW_TASK = "launch_new_task";
    private static final String EXTRA_FINISH_SELF = "finish_self";
    private static final String EXTRA_IS_TRANSLUCENT = "is_translucent";

    private static final String TEST_ACTIVITY_ACTION_FINISH_SELF =
            "android.server.am.TestActivity.finish_self";

    @Test
    @Presubmit
    public void testLaunchingAssistantActivityIntoAssistantStack() throws Exception {
        // Enable the assistant and launch an assistant activity
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        mAmWmState.waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);

        // Ensure that the activity launched in the fullscreen assistant stack
        assertAssistantStackExists();
        assertTrue("Expected assistant stack to be fullscreen",
                mAmWmState.getAmState().getStackByActivityType(
                        ACTIVITY_TYPE_ASSISTANT).isFullscreen());

        disableAssistant();
    }

    @Test
    @Presubmit
    public void testAssistantStackZOrder() throws Exception {
        if (!supportsPip() || !supportsSplitScreenMultiWindow()) return;
        // Launch a pinned stack task
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        mAmWmState.waitForValidState(PIP_ACTIVITY, WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);

        // Dock a task
        launchActivity(TEST_ACTIVITY);
        launchActivityInDockStack(DOCKED_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);

        // Enable the assistant and launch an assistant activity, ensure it is on top
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        mAmWmState.waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
        assertAssistantStackExists();

        mAmWmState.assertFrontStack("Pinned stack should be on top.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFocusedStack("Assistant stack should be focused.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);

        disableAssistant();
    }

    @Test
    @Presubmit
    public void testAssistantStackLaunchNewTask() throws Exception {
        enableAssistant();
        assertAssistantStackCanLaunchAndReturnFromNewTask();
        disableAssistant();
    }

    @Test
    @Presubmit
    public void testAssistantStackLaunchNewTaskWithDockedStack() throws Exception {
        if (!supportsSplitScreenMultiWindow()) return;
        // Dock a task
        launchActivity(TEST_ACTIVITY);
        launchActivityInDockStack(DOCKED_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);

        enableAssistant();
        assertAssistantStackCanLaunchAndReturnFromNewTask();
        disableAssistant();
    }

    private void assertAssistantStackCanLaunchAndReturnFromNewTask() throws Exception {
        final boolean inSplitScreenMode = mAmWmState.getAmState().containsStack(
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);

        // Enable the assistant and launch an assistant activity which will launch a new task
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_LAUNCH_NEW_TASK, TEST_ACTIVITY);
        disableAssistant();

        final int expectedWindowingMode = inSplitScreenMode
                ? WINDOWING_MODE_SPLIT_SCREEN_SECONDARY
                : WINDOWING_MODE_FULLSCREEN;
        // Ensure that the fullscreen stack is on top and the test activity is now visible
        mAmWmState.waitForValidState(TEST_ACTIVITY, expectedWindowingMode, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFocusedActivity("TestActivity should be resumed", TEST_ACTIVITY);
        mAmWmState.assertFrontStack("Fullscreen stack should be on top.",
                expectedWindowingMode, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFocusedStack("Fullscreen stack should be focused.",
                expectedWindowingMode, ACTIVITY_TYPE_STANDARD);

        // Now, tell it to finish itself and ensure that the assistant stack is brought back forward
        executeShellCommand("am broadcast -a " + TEST_ACTIVITY_ACTION_FINISH_SELF);
        mAmWmState.waitForFocusedStack(WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
        mAmWmState.assertFrontStackActivityType(
                "Assistant stack should be on top.", ACTIVITY_TYPE_ASSISTANT);
        mAmWmState.assertFocusedStack("Assistant stack should be focused.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
    }

    @Test
    @Presubmit
    public void testAssistantStackFinishToPreviousApp() throws Exception {
        // Launch an assistant activity on top of an existing fullscreen activity, and ensure that
        // the fullscreen activity is still visible and on top after the assistant activity finishes
        launchActivity(TEST_ACTIVITY);
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_FINISH_SELF, "true");
        disableAssistant();
        mAmWmState.waitForValidState(TEST_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mAmWmState.waitForActivityState(TEST_ACTIVITY, STATE_RESUMED);
        mAmWmState.assertFocusedActivity("TestActivity should be resumed", TEST_ACTIVITY);
        mAmWmState.assertFrontStack("Fullscreen stack should be on top.",
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFocusedStack("Fullscreen stack should be focused.",
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    @Presubmit
    public void testDisallowEnterPiPFromAssistantStack() throws Exception {
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_ENTER_PIP, "true");
        disableAssistant();
        mAmWmState.waitForValidStateWithActivityType(ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
        mAmWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    @Presubmit
    public void testTranslucentAssistantActivityStackVisibility() throws Exception {
        enableAssistant();
        // Go home, launch the assistant and check to see that home is visible
        removeStacksInWindowingModes(WINDOWING_MODE_FULLSCREEN,
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        launchHomeActivity();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidStateWithActivityType(
                TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
        assertAssistantStackExists();
        mAmWmState.waitForHomeActivityVisible();
        mAmWmState.assertHomeActivityVisible(true);

        // Launch a fullscreen app and then launch the assistant and check to see that it is
        // also visible
        removeStacksWithActivityTypes(ACTIVITY_TYPE_ASSISTANT);
        launchActivity(TEST_ACTIVITY);
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidStateWithActivityType(
                TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        // Go home, launch assistant, launch app into fullscreen with activity present, and go back.
        // Ensure home is visible.
        removeStacksWithActivityTypes(ACTIVITY_TYPE_ASSISTANT);
        launchHomeActivity();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true), EXTRA_LAUNCH_NEW_TASK,
                TEST_ACTIVITY);
        mAmWmState.waitForValidState(TEST_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertHomeActivityVisible(false);
        pressBackButton();
        mAmWmState.waitForFocusedStack(WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
        assertAssistantStackExists();
        mAmWmState.waitForHomeActivityVisible();
        mAmWmState.assertHomeActivityVisible(true);

        // Launch a fullscreen and docked app and then launch the assistant and check to see that it
        // is also visible
        if (supportsSplitScreenMultiWindow()) {
            removeStacksWithActivityTypes(ACTIVITY_TYPE_ASSISTANT);
            launchActivityInDockStack(DOCKED_ACTIVITY);
            launchActivity(TEST_ACTIVITY);
            mAmWmState.assertContainsStack("Must contain docked stack.",
                    WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
            launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    EXTRA_IS_TRANSLUCENT, String.valueOf(true));
            mAmWmState.waitForValidStateWithActivityType(
                    TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
            assertAssistantStackExists();
            mAmWmState.assertVisibility(DOCKED_ACTIVITY, true);
            mAmWmState.assertVisibility(TEST_ACTIVITY, true);
        }
        disableAssistant();
    }

    @Test
    @Presubmit
    public void testLaunchIntoSameTask() throws Exception {
        enableAssistant();

        // Launch the assistant
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
        mAmWmState.assertFocusedStack("Expected assistant stack focused",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
        assertEquals(1, mAmWmState.getAmState().getStackByActivityType(
                ACTIVITY_TYPE_ASSISTANT).getTasks().size());
        final int taskId = mAmWmState.getAmState().getTaskByActivityName(ASSISTANT_ACTIVITY)
                .mTaskId;

        // Launch a new fullscreen activity
        // Using Animation Test Activity because it is opaque on all devices.
        launchActivity(ANIMATION_TEST_ACTIVITY);
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, false);

        // Launch the assistant again and ensure that it goes into the same task
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
        mAmWmState.assertFocusedStack("Expected assistant stack focused",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
        assertEquals(1, mAmWmState.getAmState().getStackByActivityType(
                ACTIVITY_TYPE_ASSISTANT).getTasks().size());
        assertEquals(taskId,
                mAmWmState.getAmState().getTaskByActivityName(ASSISTANT_ACTIVITY).mTaskId);

        disableAssistant();
    }

    @Test
    public void testPinnedStackWithAssistant() throws Exception {
        if (!supportsPip() || !supportsSplitScreenMultiWindow()) return;

        enableAssistant();

        // Launch a fullscreen activity and a PIP activity, then launch the assistant, and ensure
        // that the test activity is still visible
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidStateWithActivityType(
                TRANSLUCENT_ASSISTANT_ACTIVITY, ACTIVITY_TYPE_ASSISTANT);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(TRANSLUCENT_ASSISTANT_ACTIVITY, true);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        disableAssistant();
    }

    /**
     * Asserts that the assistant stack exists.
     */
    private void assertAssistantStackExists() throws Exception {
        mAmWmState.assertContainsStack("Must contain assistant stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_ASSISTANT);
    }

    /**
     * Sets the system voice interaction service.
     */
    private void enableAssistant() throws Exception {
        executeShellCommand("settings put secure voice_interaction_service " +
                getActivityComponentName(VOICE_INTERACTION_SERVICE));
    }

    /**
     * Resets the system voice interaction service.
     */
    private void disableAssistant() throws Exception {
        executeShellCommand("settings delete secure voice_interaction_service " +
                getActivityComponentName(VOICE_INTERACTION_SERVICE));
    }
}
