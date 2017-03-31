/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.jobscheduler;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handles callback from the framework {@link android.app.job.JobScheduler}. The behaviour of this
 * class is configured through the static
 * {@link TestEnvironment}.
 */
@TargetApi(21)
public class MockJobService extends JobService {
    private static final String TAG = "MockJobService";

    /** Wait this long before timing out the test. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 30000L; // 30 seconds.

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "Created test service.");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Test job executing: " + params.getJobId());

        int permCheckRead = PackageManager.PERMISSION_DENIED;
        int permCheckWrite = PackageManager.PERMISSION_DENIED;
        ClipData clip = params.getClipData();
        if (clip != null) {
            permCheckRead = checkUriPermission(clip.getItemAt(0).getUri(), Process.myPid(),
                    Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            permCheckWrite = checkUriPermission(clip.getItemAt(0).getUri(), Process.myPid(),
                    Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        TestEnvironment.getTestEnvironment().notifyExecution(params, permCheckRead, permCheckWrite);
        return false;  // No work to do.
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     * Configures the expected behaviour for each test. This object is shared across consecutive
     * tests, so to clear state each test is responsible for calling
     * {@link TestEnvironment#setUp()}.
     */
    public static final class TestEnvironment {

        private static TestEnvironment kTestEnvironment;
        //public static final int INVALID_JOB_ID = -1;

        private CountDownLatch mLatch;
        private JobParameters mExecutedJobParameters;
        private int mExecutedPermCheckRead;
        private int mExecutedPermCheckWrite;

        public static TestEnvironment getTestEnvironment() {
            if (kTestEnvironment == null) {
                kTestEnvironment = new TestEnvironment();
            }
            return kTestEnvironment;
        }

        public JobParameters getLastJobParameters() {
            return mExecutedJobParameters;
        }

        public int getLastPermCheckRead() {
            return mExecutedPermCheckRead;
        }

        public int getLastPermCheckWrite() {
            return mExecutedPermCheckWrite;
        }

        /**
         * Block the test thread, waiting on the JobScheduler to execute some previously scheduled
         * job on this service.
         */
        public boolean awaitExecution() throws InterruptedException {
            final boolean executed = mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return executed;
        }

        /**
         * Block the test thread, expecting to timeout but still listening to ensure that no jobs
         * land in the interim.
         * @return True if the latch timed out waiting on an execution.
         */
        public boolean awaitTimeout() throws InterruptedException {
            return !mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        private void notifyExecution(JobParameters params, int permCheckRead, int permCheckWrite) {
            //Log.d(TAG, "Job executed:" + params.getJobId());
            mExecutedJobParameters = params;
            mExecutedPermCheckRead = permCheckRead;
            mExecutedPermCheckWrite = permCheckWrite;
            mLatch.countDown();
        }

        public void setExpectedExecutions(int numExecutions) {
            // For no executions expected, set count to 1 so we can still block for the timeout.
            if (numExecutions == 0) {
                mLatch = new CountDownLatch(1);
            } else {
                mLatch = new CountDownLatch(numExecutions);
            }
        }

        /** Called in each testCase#setup */
        public void setUp() {
            mLatch = null;
            mExecutedJobParameters = null;
        }

    }
}