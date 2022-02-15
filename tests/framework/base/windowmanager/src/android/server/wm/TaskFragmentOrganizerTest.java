/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowContextTests.TestActivity;
import android.server.wm.WindowManagerState.Task;
import android.server.wm.WindowManagerState.TaskFragment;
import android.server.wm.jetpack.second.Components;
import android.view.SurfaceControl;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentInfo;
import android.window.TaskFragmentOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests that verify the behavior of {@link TaskFragmentOrganizer}.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:TaskFragmentOrganizerTest
 */
@Presubmit
public class TaskFragmentOrganizerTest extends TaskFragmentOrganizerTestBase {
    private Activity mOwnerActivity;
    private IBinder mOwnerToken;
    private ComponentName mOwnerActivityName;
    private int mOwnerTaskId;
    private final ComponentName mLaunchingActivity = new ComponentName(mContext,
            WindowMetricsActivityTests.MetricsActivity.class);

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mOwnerActivity = startActivity(TestActivity.class);
        mOwnerToken = getActivityToken(mOwnerActivity);
        mOwnerActivityName = mOwnerActivity.getComponentName();
        mOwnerTaskId = mOwnerActivity.getTaskId();
    }

    /**
     * Verifies the behavior of
     * {@link WindowContainerTransaction#createTaskFragment(TaskFragmentCreationParams)} to create
     * TaskFragment.
     */
    @Test
    public void testCreateTaskFragment() {
        mWmState.computeState(mOwnerActivityName);
        Task parentTask = mWmState.getRootTask(mOwnerActivity.getTaskId());
        final int originalTaskFragCount = parentTask.getTaskFragments().size();

        final IBinder taskFragToken = new Binder();
        final Rect bounds = new Rect(0, 0, 1000, 1000);
        final int windowingMode = WINDOWING_MODE_MULTI_WINDOW;
        final TaskFragmentCreationParams params = new TaskFragmentCreationParams.Builder(
                mTaskFragmentOrganizer.getOrganizerToken(), taskFragToken, mOwnerToken)
                .setInitialBounds(bounds)
                .setWindowingMode(windowingMode)
                .build();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(params);
        mTaskFragmentOrganizer.applyTransaction(wct);

        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        final TaskFragmentInfo info = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);

        assertEmptyTaskFragment(info, taskFragToken);
        assertThat(info.getConfiguration().windowConfiguration.getBounds()).isEqualTo(bounds);
        assertThat(info.getWindowingMode()).isEqualTo(windowingMode);

        mWmState.computeState(mOwnerActivityName);
        parentTask = mWmState.getRootTask(mOwnerActivity.getTaskId());
        final int curTaskFragCount = parentTask.getTaskFragments().size();

        assertWithMessage("There must be a TaskFragment created under Task#"
                + mOwnerTaskId).that(curTaskFragCount - originalTaskFragCount)
                .isEqualTo(1);
    }

    /**
     * Verifies the behavior of
     * {@link WindowContainerTransaction#reparentActivityToTaskFragment(IBinder, IBinder)} to
     * reparent {@link Activity} to TaskFragment.
     */
    @Test
    public void testReparentActivity() {
        mWmState.computeState(mOwnerActivityName);

        final TaskFragmentCreationParams params = generateTaskFragCreationParams();
        final IBinder taskFragToken = params.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(params)
                .reparentActivityToTaskFragment(taskFragToken, mOwnerToken);
        mTaskFragmentOrganizer.applyTransaction(wct);

        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        assertNotEmptyTaskFragment(mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken),
                taskFragToken, mOwnerToken);

        mWmState.waitForActivityState(mOwnerActivityName, WindowManagerState.STATE_RESUMED);

        final Task parentTask = mWmState.getTaskByActivity(mOwnerActivityName);
        final TaskFragment taskFragment = mWmState.getTaskFragmentByActivity(mOwnerActivityName);

        // Assert window hierarchy must be as follows
        // - owner Activity's Task (parentTask)
        //   - taskFragment
        //     - owner Activity
        assertWindowHierarchy(parentTask, taskFragment, mWmState.getActivity(mOwnerActivityName));
    }

    /**
     * Verifies the behavior of
     * {@link WindowContainerTransaction#startActivityInTaskFragment(IBinder, IBinder, Intent,
     * Bundle)} to start Activity in TaskFragment without creating new Task.
     */
    @Test
    @Ignore("b/197364677")
    public void testStartActivityInTaskFragment_reuseTask() {
        final TaskFragmentCreationParams params = generateTaskFragCreationParams();
        final IBinder taskFragToken = params.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(params)
                .startActivityInTaskFragment(taskFragToken, mOwnerToken,
                        new Intent().setComponent(mLaunchingActivity), null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(wct);

        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        TaskFragmentInfo info = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
        assertNotEmptyTaskFragment(info, taskFragToken);

        mWmState.waitForActivityState(mLaunchingActivity, WindowManagerState.STATE_RESUMED);

        Task parentTask = mWmState.getRootTask(mOwnerActivity.getTaskId());
        TaskFragment taskFragment = mWmState.getTaskFragmentByActivity(mLaunchingActivity);

        // Assert window hierarchy must be as follows
        // - owner Activity's Task (parentTask)
        //   - taskFragment
        //     - LAUNCHING_ACTIVITY
        //   - owner Activity
        assertWindowHierarchy(parentTask, taskFragment, mWmState.getActivity(mLaunchingActivity));
        assertWindowHierarchy(parentTask, mWmState.getActivity(mOwnerActivityName));
        assertWithMessage("The owner Activity's Task must be reused as"
                + " the launching Activity's Task.").that(parentTask)
                .isEqualTo(mWmState.getTaskByActivity(mLaunchingActivity));
    }

    /**
     * Verifies the behavior of
     * {@link WindowContainerTransaction#startActivityInTaskFragment(IBinder, IBinder, Intent,
     * Bundle)} to start Activity on new created Task.
     */
    @Test
    @Ignore("b/197364677")
    public void testStartActivityInTaskFragment_createNewTask() {
        final TaskFragmentCreationParams params = generateTaskFragCreationParams();
        final IBinder taskFragToken = params.getFragmentToken();
        final Intent intent = new Intent()
                .setComponent(mLaunchingActivity)
                .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(params)
                .startActivityInTaskFragment(taskFragToken, mOwnerToken, intent,
                        null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(wct);

        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        TaskFragmentInfo info = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
        assertNotEmptyTaskFragment(info, taskFragToken);

        mWmState.waitForActivityState(mLaunchingActivity, WindowManagerState.STATE_RESUMED);

        Task parentTask = mWmState.getRootTask(mOwnerActivity.getTaskId());
        TaskFragment taskFragment = mWmState.getTaskFragmentByActivity(mLaunchingActivity);
        Task childTask = mWmState.getTaskByActivity(mLaunchingActivity);

        // Assert window hierarchy must be as follows
        // - owner Activity's Task (parentTask)
        //   - taskFragment
        //     - new created Task
        //       - LAUNCHING_ACTIVITY
        //   - owner Activity
        assertWindowHierarchy(parentTask, taskFragment, childTask,
                mWmState.getActivity(mLaunchingActivity));
        assertWindowHierarchy(parentTask, mWmState.getActivity(mOwnerActivityName));
    }

    /**
     * Verifies the behavior of
     * {@link WindowContainerTransaction#deleteTaskFragment(WindowContainerToken)} to remove
     * the organized TaskFragment.
     */
    @Test
    public void testDeleteTaskFragment() {
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(null);
        final IBinder taskFragToken = taskFragmentInfo.getFragmentToken();
        assertEmptyTaskFragment(taskFragmentInfo, taskFragmentInfo.getFragmentToken());

        mWmState.computeState(mOwnerActivityName);
        final int originalTaskFragCount = mWmState.getRootTask(mOwnerTaskId).getTaskFragments()
                .size();

        WindowContainerTransaction wct = new WindowContainerTransaction()
                .deleteTaskFragment(taskFragmentInfo.getToken());
        mTaskFragmentOrganizer.applyTransaction(wct);

        mTaskFragmentOrganizer.waitForTaskFragmentRemoved();

        assertEmptyTaskFragment(mTaskFragmentOrganizer.getRemovedTaskFragmentInfo(taskFragToken),
                taskFragToken);

        mWmState.computeState(mOwnerActivityName);
        final int currTaskFragCount = mWmState.getRootTask(mOwnerTaskId).getTaskFragments().size();
        assertWithMessage("TaskFragment with token " + taskFragToken + " must be"
                + " removed.").that(originalTaskFragCount - currTaskFragCount).isEqualTo(1);
    }

    /**
     * Verifies the visibility of an activity behind a TaskFragment that has the same
     * bounds of the host Task.
     */
    @Test
    public void testActivityVisibilityBehindTaskFragment() {
        // Start an activity and reparent it to a TaskFragment.
        final Activity embeddedActivity =
                startActivity(WindowMetricsActivityTests.MetricsActivity.class);
        final IBinder embeddedActivityToken = getActivityToken(embeddedActivity);
        final TaskFragmentCreationParams params = generateTaskFragCreationParams();
        final IBinder taskFragToken = params.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(params)
                .reparentActivityToTaskFragment(taskFragToken, embeddedActivityToken);
        mTaskFragmentOrganizer.applyTransaction(wct);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();
        // The activity below must be occluded and stopped.
        waitAndAssertActivityState(mOwnerActivityName, WindowManagerState.STATE_STOPPED,
                "Activity must be stopped");

        // Finishing the top activity and remain the TaskFragment on top. The next top activity
        // must be resumed.
        embeddedActivity.finish();
        waitAndAssertResumedActivity(mOwnerActivityName, "Activity must be resumed");
    }

    /**
     * Verifies the that config changing transactions are allowed for task fragments embedded by the
     * same UID.
     */
    @Test
    public void testTaskFragmentConfigChange_allowedTrustedMode_sameUid() {
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(mLaunchingActivity);

        List<WindowContainerTransaction> transactions = createConfigChangingTransactions(
                taskFragmentInfo);
        for (WindowContainerTransaction wct : transactions) {
            mTaskFragmentOrganizer.applyTransaction(wct);
        }
    }

    /**
     * Verifies the that config changing transactions are not allowed for task fragments embedded in
     * untrusted mode.
     */
    @Test
    public void testTaskFragmentConfigChange_disallowedUntrustedMode() {
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(
                Components.SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        List<WindowContainerTransaction> transactions = createConfigChangingTransactions(
                taskFragmentInfo);
        for (WindowContainerTransaction wct : transactions) {
            boolean exceptionTriggered = false;
            try {
                mTaskFragmentOrganizer.applyTransaction(wct);
            } catch (SecurityException e) {
                exceptionTriggered = true;
            }
            assertTrue(exceptionTriggered);
        }
    }

    /**
     * Builds, runs and waits for completion of task fragment creation transaction.
     * @param componentName name of the activity to launch in the TF, or {@code null} if none.
     * @return token of the created task fragment.
     */
    private TaskFragmentInfo createTaskFragment(@Nullable ComponentName componentName) {
        final TaskFragmentCreationParams params = generateTaskFragCreationParams();
        final IBinder taskFragToken = params.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(params);
        if (componentName != null) {
            wct.startActivityInTaskFragment(taskFragToken, mOwnerToken,
                    new Intent().setComponent(componentName), null /* activityOptions */);
        }
        mTaskFragmentOrganizer.applyTransaction(wct);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        if (componentName != null) {
            mWmState.waitForActivityState(componentName, WindowManagerState.STATE_RESUMED);
        }

        return mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);
    }

    private List<WindowContainerTransaction> createConfigChangingTransactions(
            @NonNull TaskFragmentInfo taskFragmentInfo) {
        final WindowContainerToken wct = taskFragmentInfo.getToken();
        List<WindowContainerTransaction> transactionList = new ArrayList<>();

        // Transaction that puts the task fragment outside of parent bounds
        Task parentTask = mWmState.getRootTask(mOwnerActivity.getTaskId());
        Rect outOfBoundsRect = new Rect(parentTask.mBounds);
        outOfBoundsRect.offset(10, 10);
        transactionList.add(new WindowContainerTransaction()
                .setBounds(wct, outOfBoundsRect));

        // SurfaceControl transactions
        transactionList.add(new WindowContainerTransaction()
                .setBoundsChangeTransaction(wct, new SurfaceControl.Transaction()));

        return transactionList;
    }

    @NonNull
    private TaskFragmentCreationParams generateTaskFragCreationParams() {
        return mTaskFragmentOrganizer.generateTaskFragParams(mOwnerToken);
    }
}
