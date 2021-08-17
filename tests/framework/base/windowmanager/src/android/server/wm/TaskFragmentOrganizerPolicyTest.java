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

import static android.server.wm.TaskFragmentOrganizerTestBase.getActivityToken;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.TaskFragmentOrganizerTestBase.BasicTaskFragmentOrganizer;
import android.server.wm.WindowContextTests.TestActivity;
import android.window.TaskAppearedInfo;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentOrganizer;
import android.window.TaskOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests that verify the behavior of {@link TaskFragmentOrganizer} policy.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:TaskFragmentOrganizerPolicyTest
 */
@RunWith(AndroidJUnit4.class)
@Presubmit
public class TaskFragmentOrganizerPolicyTest {
    private TaskOrganizer mTaskOrganizer;
    private BasicTaskFragmentOrganizer mTaskFragmentOrganizer;

    @Before
    public void setUp() {
        mTaskFragmentOrganizer = new BasicTaskFragmentOrganizer();
        mTaskFragmentOrganizer.registerOrganizer();
    }

    @After
    public void tearDown() {
        if (mTaskFragmentOrganizer != null) {
            mTaskFragmentOrganizer.unregisterOrganizer();
        }
    }

    /**
     * Verifies whether performing non-TaskFragment
     * {@link android.window.WindowContainerTransaction.HierarchyOp operations} on
     * {@link TaskFragmentOrganizer} without permission throws {@link SecurityException}.
     */
    @Test(expected = SecurityException.class)
    public void testPerformNonTaskFragmentHierarchyOperation_ThrowException() {
        final List<TaskAppearedInfo> taskInfos = new ArrayList<>();
        try {
            // Register TaskOrganizer to obtain Task information.
            NestedShellPermission.run(() -> {
                mTaskOrganizer = new TaskOrganizer();
                taskInfos.addAll(mTaskOrganizer.registerOrganizer());
            });

            // It is expected to throw Security exception when TaskFragmentOrganizer performs a
            // non-TaskFragment hierarchy operation.
            final WindowContainerToken taskToken = taskInfos.get(0).getTaskInfo().getToken();
            final WindowContainerTransaction wct = new WindowContainerTransaction()
                    .reorder(taskToken, true /* opTop */);
            mTaskFragmentOrganizer.applyTransaction(wct);
        } finally {
            if (mTaskOrganizer != null) {
                NestedShellPermission.run(() -> mTaskOrganizer.unregisterOrganizer());
            }
        }
    }

    /**
     * Verifies whether changing property on non-TaskFragment window container without permission
     * throws {@link SecurityException}.
     */
    @Test(expected = SecurityException.class)
    public void testSetPropertyOnNonTaskFragment_ThrowException() {
        final List<TaskAppearedInfo> taskInfos = new ArrayList<>();
        try {
            // Register TaskOrganizer to obtain Task information.
            NestedShellPermission.run(() -> {
                mTaskOrganizer = new TaskOrganizer();
                taskInfos.addAll(mTaskOrganizer.registerOrganizer());
            });

            // It is expected to throw SecurityException when TaskFragmentOrganizer attempts to
            // change the property on non-TaskFragment container.
            final WindowContainerToken taskToken = taskInfos.get(0).getTaskInfo().getToken();
            final WindowContainerTransaction wct = new WindowContainerTransaction()
                    .setBounds(taskToken, new Rect());
            mTaskFragmentOrganizer.applyTransaction(wct);
        } finally {
            if (mTaskOrganizer != null) {
                NestedShellPermission.run(() -> mTaskOrganizer.unregisterOrganizer());
            }
        }
    }

    /**
     * Verifies whether performing TaskFragment
     * {@link android.window.WindowContainerTransaction.HierarchyOp operations} on the TaskFragment
     * which is not organized by given {@link TaskFragmentOrganizer} throws
     * {@link SecurityException}.
     */
    @Test(expected = SecurityException.class)
    public void testPerformOperationsOnNonOrganizedTaskFragment_ThrowException() {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Intent intent = new Intent(instrumentation.getTargetContext(), TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = instrumentation.startActivitySync(intent);

        // Create a TaskFragment with a TaskFragmentOrganizer.
        final IBinder taskFragToken = new Binder();
        final IBinder activityToken = getActivityToken(activity);
        final TaskFragmentCreationParams params = new TaskFragmentCreationParams.Builder(
                mTaskFragmentOrganizer.getOrganizerToken(), taskFragToken, activityToken)
                .build();
        WindowContainerTransaction wct = new WindowContainerTransaction()
                .createTaskFragment(params);
        mTaskFragmentOrganizer.applyTransaction(wct);
        // Wait for TaskFragment's creation to obtain its WindowContainerToken.
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();

        // Create another TaskFragmentOrganizer
        final TaskFragmentOrganizer anotherOrganizer = new TaskFragmentOrganizer(Runnable::run);
        anotherOrganizer.registerOrganizer();
        // Try to perform an operation on the TaskFragment when is organized by the previous
        // TaskFragmentOrganizer.
        wct = new WindowContainerTransaction()
                .deleteTaskFragment(mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken)
                        .getToken());

        // It is expected to throw SecurityException when performing operations on the TaskFragment
        // which is not organized by the same TaskFragmentOrganizer.
        anotherOrganizer.applyTransaction(wct);
    }
}
