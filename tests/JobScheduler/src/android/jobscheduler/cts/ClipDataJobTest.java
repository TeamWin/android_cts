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
import android.content.ClipData;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that grant permissions through
 * ClipData.
 */
@TargetApi(26)
public class ClipDataJobTest extends ConstraintTest {
    private static final String TAG = "ClipDataJobTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int CLIP_DATA_JOB_ID = ClipDataJobTest.class.hashCode();

    static final String MY_PACKAGE = "android.jobscheduler.cts";

    static final String JOBPERM_PACKAGE = "android.jobscheduler.cts.jobperm";
    static final String JOBPERM_AUTHORITY = "android.jobscheduler.cts.jobperm.provider";
    static final String JOBPERM_PERM = "android.jobscheduler.cts.jobperm.perm";

    private JobInfo.Builder mBuilder;
    private Uri mFirstUri;
    private Bundle mFirstUriBundle;
    private ClipData mClipData;
    private ContentProviderClient mProvider;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(CLIP_DATA_JOB_ID, kJobServiceComponent);
        mFirstUri = Uri.parse("content://" + JOBPERM_AUTHORITY + "/protected/foo");
        mFirstUriBundle = new Bundle();
        mFirstUriBundle.putParcelable("uri", mFirstUri);
        mClipData = new ClipData("JobPerm", new String[] { "application/*" },
                new ClipData.Item(mFirstUri));
        mProvider = getContext().getContentResolver().acquireContentProviderClient(mFirstUri);
        String res = SystemUtil.runShellCommand(getInstrumentation(), "cmd activity set-inactive "
                + mContext.getPackageName() + " false");
    }

    @Override
    public void tearDown() throws Exception {
        mProvider.close();
        mJobScheduler.cancel(CLIP_DATA_JOB_ID);
        // Put storage service back in to normal operation.
        SystemUtil.runShellCommand(getInstrumentation(), "cmd devicestoragemonitor reset");
    }

    // Note we are just using storage state as a way to control when the job gets executed.
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

    void waitPermissionRevoke(Uri uri, int access, long timeout) {
        long startTime = SystemClock.elapsedRealtime();
        while (getContext().checkUriPermission(uri, Process.myPid(), Process.myUid(), access)
                 != PackageManager.PERMISSION_GRANTED) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            if ((SystemClock.elapsedRealtime()-startTime) >= timeout) {
                fail("Timed out waiting for permission revoke");
            }
        }
    }

    /**
     * Test basic granting of URI permissions associated with jobs.
     */
    public void testClipDataGrant() throws Exception {
        // Start out with storage low, so job is enqueued but not executed yet.
        setStorageState(true);

        // We need to get a permission grant so that we can grant it to ourself.
        mProvider.call("grant", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Schedule the job, the system should now also be holding a URI grant for us.
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true)
                .setClipData(mClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());

        // Remove the explicit grant, we should still have a grant due to the job.
        mProvider.call("revoke", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Now allow the job to run and wait for it.
        setStorageState(false);
        assertTrue("Job with storage not low constraint did not fire when storage not low.",
                kTestEnvironment.awaitExecution());

        // Make sure the job still had the permission granted.
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckRead());
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckWrite());

        // And wait for everything to be cleaned up.
        waitPermissionRevoke(mFirstUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 5000);
    }

    /**
     * Test that we correctly fail when trying to grant permissions to things we don't
     * have access to.
     */
    public void testClipDataGrant_Failed() throws Exception {
        try {
            mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true)
                    .setClipData(mClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());
        } catch (SecurityException e) {
            return;
        }

        fail("Security exception not thrown");
    }

    /**
     * Test basic granting of URI permissions associated with jobs and are correctly
     * retained when rescheduling the job.
     */
    public void testClipDataGrantReschedule() throws Exception {
        // We need to get a permission grant so that we can grant it to ourself.
        mProvider.call("grant", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Schedule the job, the system should now also be holding a URI grant for us.
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setMinimumLatency(60*60*1000)
                .setClipData(mClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());

        // Remove the explicit grant, we should still have a grant due to the job.
        mProvider.call("revoke", MY_PACKAGE, mFirstUriBundle);
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                getContext().checkUriPermission(mFirstUri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        // Now reschedule the job to have it happen right now.
        mJobScheduler.schedule(mBuilder.setMinimumLatency(0)
                .setClipData(mClipData, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION).build());
        assertTrue("Job with storage not low constraint did not fire when storage not low.",
                kTestEnvironment.awaitExecution());

        // Make sure the job still had the permission granted.
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckRead());
        assertEquals(PackageManager.PERMISSION_GRANTED, kTestEnvironment.getLastPermCheckWrite());

        // And wait for everything to be cleaned up.
        waitPermissionRevoke(mFirstUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 5000);
    }
}
