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
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
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

    private JobParameters mParams;

    ArrayList<Intent> mReceivedWork = new ArrayList<Intent>();

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (TestEnvironment.getTestEnvironment().getExpectedWork() != null) {
            TestEnvironment.getTestEnvironment().notifyExecution(mParams, 0, 0, mReceivedWork,
                    null);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Created test service.");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Test job executing: " + params.getJobId());
        mParams = params;

        int permCheckRead = PackageManager.PERMISSION_DENIED;
        int permCheckWrite = PackageManager.PERMISSION_DENIED;
        ClipData clip = params.getClipData();
        if (clip != null) {
            permCheckRead = checkUriPermission(clip.getItemAt(0).getUri(), Process.myPid(),
                    Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            permCheckWrite = checkUriPermission(clip.getItemAt(0).getUri(), Process.myPid(),
                    Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        TestWorkItem[] expectedWork = TestEnvironment.getTestEnvironment().getExpectedWork();
        if (expectedWork != null) {
            try {
                if (TestEnvironment.getTestEnvironment().awaitDoWork()) {
                    TestEnvironment.getTestEnvironment().notifyExecution(params, permCheckRead,
                            permCheckWrite, null, "Spent too long waiting to start executing work");
                    return false;
                }
            } catch (InterruptedException e) {
                TestEnvironment.getTestEnvironment().notifyExecution(params, permCheckRead,
                        permCheckWrite, null, "Failed waiting for work: " + e);
                return false;
            }
            JobWorkItem work;
            int index = 0;
            while ((work = params.dequeueWork()) != null) {
                Log.i(TAG, "Received work #" + index + ": " + work.getIntent());
                mReceivedWork.add(work.getIntent());

                if (index < expectedWork.length) {
                    TestWorkItem expected = expectedWork[index];
                    int grantFlags = work.getIntent().getFlags();
                    if (expected.requireUrisGranted != null) {
                        for (int ui = 0; ui < expected.requireUrisGranted.length; ui++) {
                            if ((grantFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                                if (checkUriPermission(expected.requireUrisGranted[ui],
                                        Process.myPid(), Process.myUid(),
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        != PackageManager.PERMISSION_GRANTED) {
                                    TestEnvironment.getTestEnvironment().notifyExecution(params,
                                            permCheckRead, permCheckWrite, null,
                                            "Expected read permission but not granted: "
                                                    + expected.requireUrisGranted[ui]
                                                    + " @ #" + index);
                                    return false;
                                }
                            }
                            if ((grantFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                                if (checkUriPermission(expected.requireUrisGranted[ui],
                                        Process.myPid(), Process.myUid(),
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        != PackageManager.PERMISSION_GRANTED) {
                                    TestEnvironment.getTestEnvironment().notifyExecution(params,
                                            permCheckRead, permCheckWrite, null,
                                            "Expected write permission but not granted: "
                                                    + expected.requireUrisGranted[ui]
                                                    + " @ #" + index);
                                    return false;
                                }
                            }
                        }
                    }
                    if (expected.requireUrisNotGranted != null) {
                        // XXX note no delay here, current impl will have fully revoked the
                        // permission by the time we return from completing the last work.
                        for (int ui = 0; ui < expected.requireUrisNotGranted.length; ui++) {
                            if ((grantFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                                if (checkUriPermission(expected.requireUrisNotGranted[ui],
                                        Process.myPid(), Process.myUid(),
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        != PackageManager.PERMISSION_DENIED) {
                                    TestEnvironment.getTestEnvironment().notifyExecution(params,
                                            permCheckRead, permCheckWrite, null,
                                            "Not expected read permission but granted: "
                                                    + expected.requireUrisNotGranted[ui]
                                                    + " @ #" + index);
                                    return false;
                                }
                            }
                            if ((grantFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                                if (checkUriPermission(expected.requireUrisNotGranted[ui],
                                        Process.myPid(), Process.myUid(),
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        != PackageManager.PERMISSION_DENIED) {
                                    TestEnvironment.getTestEnvironment().notifyExecution(params,
                                            permCheckRead, permCheckWrite, null,
                                            "Not expected write permission but granted: "
                                                    + expected.requireUrisNotGranted[ui]
                                                    + " @ #" + index);
                                    return false;
                                }
                            }
                        }
                    }
                }

                params.completeWork(work);

                if (index < expectedWork.length) {
                    TestWorkItem expected = expectedWork[index];
                    if (expected.subitems != null) {
                        final TestWorkItem[] sub = expected.subitems;
                        final JobInfo ji = expected.jobInfo;
                        final JobScheduler js = (JobScheduler) getSystemService(
                                Context.JOB_SCHEDULER_SERVICE);
                        for (int subi = 0; subi < sub.length; subi++) {
                            js.enqueue(ji, new JobWorkItem(sub[subi].intent));
                        }
                    }
                }

                index++;
            }
            Log.i(TAG, "Done with all work at #" + index);
            // We don't notifyExecution here because we want to make sure the job properly
            // stops itself.
            return true;
        } else {
            TestEnvironment.getTestEnvironment().notifyExecution(params, permCheckRead,
                    permCheckWrite, null, null);
            return false;  // No work to do.
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    public static final class TestWorkItem {
        public final Intent intent;
        public final JobInfo jobInfo;
        public final TestWorkItem[] subitems;
        public final Uri[] requireUrisGranted;
        public final Uri[] requireUrisNotGranted;

        public TestWorkItem(Intent _intent) {
            intent = _intent;
            jobInfo = null;
            subitems = null;
            requireUrisGranted = null;
            requireUrisNotGranted = null;
        }

        public TestWorkItem(Intent _intent, JobInfo _jobInfo, TestWorkItem[] _subitems) {
            intent = _intent;
            jobInfo = _jobInfo;
            subitems = _subitems;
            requireUrisGranted = null;
            requireUrisNotGranted = null;
        }

        public TestWorkItem(Intent _intent, Uri[] _requireUrisGranted,
                Uri[] _requireUrisNotGranted) {
            intent = _intent;
            jobInfo = null;
            subitems = null;
            requireUrisGranted = _requireUrisGranted;
            requireUrisNotGranted = _requireUrisNotGranted;
        }
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
        private CountDownLatch mDoWorkLatch;
        private TestWorkItem[] mExpectedWork;
        private JobParameters mExecutedJobParameters;
        private int mExecutedPermCheckRead;
        private int mExecutedPermCheckWrite;
        private ArrayList<Intent> mExecutedReceivedWork;
        private String mExecutedErrorMessage;

        public static TestEnvironment getTestEnvironment() {
            if (kTestEnvironment == null) {
                kTestEnvironment = new TestEnvironment();
            }
            return kTestEnvironment;
        }

        public TestWorkItem[] getExpectedWork() {
            return mExpectedWork;
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

        public ArrayList<Intent> getLastReceivedWork() {
            return mExecutedReceivedWork;
        }

        public String getLastErrorMessage() {
            return mExecutedErrorMessage;
        }

        /**
         * Block the test thread, waiting on the JobScheduler to execute some previously scheduled
         * job on this service.
         */
        public boolean awaitExecution() throws InterruptedException {
            final boolean executed = mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (getLastErrorMessage() != null) {
                Assert.fail(getLastErrorMessage());
            }
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

        public boolean awaitDoWork() throws InterruptedException {
            return !mDoWorkLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        private void notifyExecution(JobParameters params, int permCheckRead, int permCheckWrite,
                ArrayList<Intent> receivedWork, String errorMsg) {
            //Log.d(TAG, "Job executed:" + params.getJobId());
            mExecutedJobParameters = params;
            mExecutedPermCheckRead = permCheckRead;
            mExecutedPermCheckWrite = permCheckWrite;
            mExecutedReceivedWork = receivedWork;
            mExecutedErrorMessage = errorMsg;
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

        public void setExpectedWork(TestWorkItem[] work) {
            mExpectedWork = work;
            mDoWorkLatch = new CountDownLatch(1);
        }

        public void readyToWork() {
            mDoWorkLatch.countDown();
        }

        /** Called in each testCase#setup */
        public void setUp() {
            mLatch = null;
            mExecutedJobParameters = null;
        }

    }
}