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
 * limitations under the License.
 */

package android.jobscheduler.cts;


import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.os.SystemClock;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that have storage constraints.
 */
@TargetApi(26)
public class StorageConstraintTest extends ConstraintTest {
    private static final String TAG = "StorageConstraintTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int STORAGE_JOB_ID = StorageConstraintTest.class.hashCode();

    private JobInfo.Builder mBuilder;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(STORAGE_JOB_ID, kJobServiceComponent);
        String res = SystemUtil.runShellCommand(getInstrumentation(), "cmd activity set-inactive "
                + mContext.getPackageName() + " false");
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(STORAGE_JOB_ID);
        // Put storage service back in to normal operation.
        SystemUtil.runShellCommand(getInstrumentation(), "cmd devicestoragemonitor reset");
    }

    void setStorageState(boolean low) throws Exception {
        String res;
        if (low) {
            res = SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd devicestoragemonitor force-low -f");
        } else {
            res = SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd devicestoragemonitor force-not-low -f");
        }
        int seq = Integer.parseInt(res.trim());
        long startTime = SystemClock.elapsedRealtime();

        // Wait for the storage update to be processed by job scheduler before proceeding.
        int curSeq;
        do {
            curSeq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd jobscheduler get-storage-seq").trim());
            if (curSeq == seq) {
                return;
            }
        } while ((SystemClock.elapsedRealtime()-startTime) < 1000);

        fail("Timed out waiting for job scheduler: expected seq=" + seq + ", cur=" + curSeq);
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device storage is not low, when it is actually not low.
     */
    public void testNotLowConstraintExecutes() throws Exception {
        setStorageState(false);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true).build());

        assertTrue("Job with storage not low constraint did not fire when storage not low.",
                kTestEnvironment.awaitExecution());
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device storage is not low, when it actually is low.
     */
    public void testNotLowConstraintFails() throws Exception {
        setStorageState(true);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true).build());

        assertFalse("Job with storage now low constraint fired while low.",
                kTestEnvironment.awaitExecution());

        // And for good measure, ensure the job runs once storage is okay.
        kTestEnvironment.setExpectedExecutions(1);
        setStorageState(false);
        assertTrue("Job with storage not low constraint did not fire when storage not low.",
                kTestEnvironment.awaitExecution());
    }
}
