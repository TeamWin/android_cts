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
import static android.server.wm.WindowManagerState.STATE_STOPPED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState.TaskFragment;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import org.junit.Test;

/**
 * Tests that verify the behavior of split Activity.
 * <p>
 * At the beginning of test, two Activities are launched side-by-side in two adjacent TaskFragments.
 * Then another Activity will be launched with different scenarios. The purpose of this test is to
 * verify the CUJ of split Activity.
 * </p>
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:SplitActivityLifecycleTest
 */
@Presubmit
public class SplitActivityLifecycleTest extends TaskFragmentOrganizerTestBase {
    private Activity mOwnerActivity;
    private IBinder mOwnerToken;
    private final Rect mPrimaryBounds = new Rect();
    private final Rect mSideBounds = new Rect();
    private TaskFragmentRecord mTaskFragA;
    private TaskFragmentRecord mTaskFragB;
    private final ComponentName mActivityA = new ComponentName(mContext, ActivityA.class);
    private final ComponentName mActivityB = new ComponentName(mContext, ActivityB.class);
    private final ComponentName mActivityC = new ComponentName(mContext, ActivityC.class);
    private final Intent mIntent = new Intent().setComponent(mActivityC);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mOwnerActivity = startActivity(ActivityA.class);
        mOwnerToken = getActivityToken(mOwnerActivity);

        // Initialize test environment by launching Activity A and B side-by-side.
        initializeSplitActivities();
    }

    /** Launch two Activities in two adjacent TaskFragments side-by-side. */
    private void initializeSplitActivities() {
        final Rect activityBounds = mOwnerActivity.getWindowManager().getCurrentWindowMetrics()
                .getBounds();
        activityBounds.splitVertically(mPrimaryBounds, mSideBounds);

        final TaskFragmentCreationParams paramsA = generatePrimaryTaskFragParams();
        final TaskFragmentCreationParams paramsB = generateSideTaskFragParams();
        IBinder taskFragTokenA = paramsA.getFragmentToken();
        IBinder taskFragTokenB = paramsB.getFragmentToken();

        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(paramsA)
                .reparentActivityToTaskFragment(taskFragTokenA, mOwnerToken)
                .createTaskFragment(paramsB)
                .startActivityInTaskFragment(taskFragTokenB, mOwnerToken,
                        new Intent().setComponent(mActivityB), null /* activityOptions */)
                .setAdjacentTaskFragments(taskFragTokenA, taskFragTokenB, null /* params */);

        mTaskFragmentOrganizer.setAppearedCount(2);
        mTaskFragmentOrganizer.applyTransaction(wct);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        final TaskFragmentInfo infoA = mTaskFragmentOrganizer.getTaskFragmentInfo(
                taskFragTokenA);
        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.getTaskFragmentInfo(
                taskFragTokenB);

        assertNotEmptyTaskFragment(infoA, taskFragTokenA, mOwnerToken);
        assertNotEmptyTaskFragment(infoB, taskFragTokenB);

        mTaskFragA = new TaskFragmentRecord(infoA);
        mTaskFragB = new TaskFragmentRecord(infoB);

        waitAndAssertResumedActivity(mActivityA, "Activity A must still be resumed.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must still be resumed.");

        mTaskFragmentOrganizer.resetLatch();
    }

    /**
     * Verifies the behavior to launch Activity in the same TaskFragment as the owner Activity.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch Activity C in the same TaskFragment as Activity A:
     * <pre class="prettyprint">
     * |A|B| -> |C|B|
     * </pre></p>
     */
    @Test
    public void testActivityLaunchInSameSplitTaskFragment() {
        final IBinder taskFragTokenA = mTaskFragA.getTaskFragToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .startActivityInTaskFragment(taskFragTokenA, mOwnerToken, mIntent,
                        null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(wct);

        final TaskFragmentInfo infoA = mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                taskFragTokenA, info -> info.getActivities().size() == 2,
                "getActivities from TaskFragment A must contain 2 activities");

        assertNotEmptyTaskFragment(infoA, taskFragTokenA, mOwnerToken);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STOPPED,
                "Activity A is occluded by Activity C, so it must be stopped.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must be resumed.");

        final TaskFragment taskFragmentA = mWmState.getTaskFragmentByActivity(mActivityA);
        assertWithMessage("TaskFragmentA must contain Activity A and C")
                .that(taskFragmentA.mActivities).containsExactly(mWmState.getActivity(mActivityA),
                mWmState.getActivity(mActivityC));
    }

    /**
     * Verifies the behavior to launch Activity in the adjacent TaskFragment.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch Activity C in the same TaskFragment as Activity B:
     * <pre class="prettyprint">
     * |A|B| -> |A|C|
     * </pre></p>
     */
    @Test
    public void testActivityLaunchInAdjacentSplitTaskFragment() {
        final IBinder taskFragTokenB = mTaskFragB.getTaskFragToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .startActivityInTaskFragment(taskFragTokenB, mOwnerToken, mIntent,
                        null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(wct);

        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                taskFragTokenB, info -> info.getActivities().size() == 2,
                "getActivities from TaskFragment A must contain 2 activities");

        assertNotEmptyTaskFragment(infoB, taskFragTokenB);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertResumedActivity(mActivityA, "Activity A must be resumed.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");

        final TaskFragment taskFragmentB = mWmState.getTaskFragmentByActivity(mActivityB);
        assertWithMessage("TaskFragmentB must contain Activity B and C")
                .that(taskFragmentB.mActivities).containsExactly(mWmState.getActivity(mActivityB),
                mWmState.getActivity(mActivityC));
    }

    /**
     * Verifies the behavior that the Activity instance in bottom TaskFragment calls
     * {@link Context#startActivity(Intent)} to launch another Activity.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, Activity A calls
     * {@link Context#startActivity(Intent)} to launch Activity C. The expected behavior is that
     * Activity C will be launch on top of Activity B as below:
     * <pre class="prettyprint">
     * |A|B| -> |A|C|
     * </pre>
     * The reason is that TaskFragment B has higher z-order than TaskFragment A because we create
     * TaskFragment B later than TaskFragment A.
     * </p>
     */
    @Test
    public void testActivityLaunchFromBottomTaskFragment() {
        mOwnerActivity.startActivity(mIntent);

        final IBinder taskFragTokenB = mTaskFragB.getTaskFragToken();
        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.waitForAndGetTaskFragmentInfo(
                taskFragTokenB, info -> info.getActivities().size() == 2,
                "getActivities from TaskFragment A must contain 2 activities");

        assertNotEmptyTaskFragment(infoB, taskFragTokenB);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertResumedActivity(mActivityA, "Activity A must be resumed.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");

        final TaskFragment taskFragmentB = mWmState.getTaskFragmentByActivity(mActivityB);
        assertWithMessage("TaskFragmentB must contain Activity B and C")
                .that(taskFragmentB.mActivities).containsExactly(mWmState.getActivity(mActivityB),
                mWmState.getActivity(mActivityC));
    }

    /**
     * Verifies the behavior to launch adjacent Activity to the adjacent TaskFragment.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch the Activity C to the adjacent TaskFragment of the secondary
     * TaskFragment, which Activity B is attached to. Then the secondary TaskFragment is shifted to
     * occlude the primary TaskFragment, which Activity A is attached to, and the adjacent
     * TaskFragment, which Activity C is attached to, is occupied the region where the secondary
     * TaskFragment is located. This test is to verify the "shopping mode" scenario.
     * <pre class="prettyprint">
     * |A|B| -> |B|C|
     * </pre></p>
     */
    @Test
    public void testAdjacentActivityLaunchFromSecondarySplitTaskFragment() {
        final IBinder taskFragTokenB = mTaskFragB.getTaskFragToken();
        final TaskFragmentCreationParams paramsC = generateSideTaskFragParams();
        final IBinder taskFragTokenC = paramsC.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                // Move TaskFragment B to the primaryBounds
                .setBounds(mTaskFragB.getToken(), mPrimaryBounds)
                // Create the side TaskFragment for C and launch
                .createTaskFragment(paramsC)
                .startActivityInTaskFragment(taskFragTokenC, mOwnerToken, mIntent,
                        null /* activityOptions */)
                .setAdjacentTaskFragments(taskFragTokenB, taskFragTokenC, null /* options */);

        mTaskFragmentOrganizer.applyTransaction(wct);
        // Wait for the TaskFragment of Activity C to be created.
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();
        // Wait for the TaskFragment of Activity B to be changed.
        mTaskFragmentOrganizer.waitForTaskFragmentInfoChanged();

        final TaskFragmentInfo infoB = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragTokenB);
        final TaskFragmentInfo infoC = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragTokenC);

        assertNotEmptyTaskFragment(infoB, taskFragTokenB);
        assertNotEmptyTaskFragment(infoC, taskFragTokenC);

        mTaskFragB = new TaskFragmentRecord(infoB);
        final TaskFragmentRecord taskFragC = new TaskFragmentRecord(infoC);

        assertThat(mTaskFragB.getBounds()).isEqualTo(mPrimaryBounds);
        assertThat(taskFragC.getBounds()).isEqualTo(mSideBounds);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STOPPED,
                "Activity A is occluded by Activity C, so it must be stopped.");
        waitAndAssertResumedActivity(mActivityB, "Activity B must be resumed.");
    }

    /**
     * Verifies the behavior to launch Activity in expanded TaskFragment.
     * <p>
     * For example, given that Activity A and B are showed side-by-side, this test verifies
     * the behavior to launch Activity C in the TaskFragment which fills the Task bounds of owner
     * Activity:
     * <pre class="prettyprint">
     * |A|B| -> |C|
     * </pre></p>
     */
    @Test
    public void testActivityLaunchInExpandedTaskFragment() {
        final TaskFragmentCreationParams fullScreenParamsC = mTaskFragmentOrganizer
                .generateTaskFragParams(mOwnerToken);
        final IBinder taskFragTokenC = fullScreenParamsC.getFragmentToken();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(fullScreenParamsC)
                .startActivityInTaskFragment(taskFragTokenC, mOwnerToken, mIntent,
                        null /* activityOptions */);

        mTaskFragmentOrganizer.applyTransaction(wct);

        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        assertNotEmptyTaskFragment(mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragTokenC),
                taskFragTokenC);

        waitAndAssertResumedActivity(mActivityC, "Activity C must be resumed.");
        waitAndAssertActivityState(mActivityA, STATE_STOPPED,
                "Activity A is occluded by Activity C, so it must be stopped.");
        waitAndAssertActivityState(mActivityB, STATE_STOPPED,
                "Activity B is occluded by Activity C, so it must be stopped.");
    }

    private TaskFragmentCreationParams generatePrimaryTaskFragParams() {
        return mTaskFragmentOrganizer.generateTaskFragParams(mOwnerToken, mPrimaryBounds,
                WINDOWING_MODE_MULTI_WINDOW);
    }

    private TaskFragmentCreationParams generateSideTaskFragParams() {
        return mTaskFragmentOrganizer.generateTaskFragParams(mOwnerToken, mSideBounds,
                WINDOWING_MODE_MULTI_WINDOW);
    }

    private static class TaskFragmentRecord {
        private final IBinder mTaskFragToken;
        private final Rect mBounds = new Rect();
        private final WindowContainerToken mContainerToken;

        private TaskFragmentRecord(TaskFragmentInfo info) {
            mTaskFragToken = info.getFragmentToken();
            mBounds.set(info.getConfiguration().windowConfiguration.getBounds());
            mContainerToken = info.getToken();
        }

        private IBinder getTaskFragToken() {
            return mTaskFragToken;
        }

        private Rect getBounds() {
            return mBounds;
        }

        private WindowContainerToken getToken() {
            return mContainerToken;
        }
    }

    public static class ActivityA extends FocusableActivity {}
    public static class ActivityB extends FocusableActivity {}
    public static class ActivityC extends FocusableActivity {}
}
