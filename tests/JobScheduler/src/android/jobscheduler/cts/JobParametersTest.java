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

package android.jobscheduler.cts;

import static android.net.ConnectivityDiagnosticsManager.persistableBundleEquals;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;

/**
 * Tests related to JobParameters objects.
 */
public class JobParametersTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = JobParametersTest.class.hashCode();

    public void testClipData() throws Exception {
        final ClipData clipData = ClipData.newPlainText("test", "testText");
        final int grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setClipData(clipData, grantFlags)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        JobParameters params = kTestEnvironment.getLastJobParameters();
        assertEquals(clipData.getItemCount(), params.getClipData().getItemCount());
        assertEquals(clipData.getItemAt(0).getText(), params.getClipData().getItemAt(0).getText());
        assertEquals(grantFlags, params.getClipGrantFlags());
    }

    public void testExtras() throws Exception {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExtras(pb)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        JobParameters params = kTestEnvironment.getLastJobParameters();
        assertTrue(persistableBundleEquals(pb, params.getExtras()));
    }

    public void testForeground() throws Exception {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setForeground(true)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        JobParameters params = kTestEnvironment.getLastJobParameters();
        assertTrue(params.isForegroundJob());

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setForeground(false)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        params = kTestEnvironment.getLastJobParameters();
        assertFalse(params.isForegroundJob());
    }

    public void testJobId() throws Exception {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        JobParameters params = kTestEnvironment.getLastJobParameters();
        assertEquals(JOB_ID, params.getJobId());
    }

    // JobParameters.getNetwork() tested in ConnectivityConstraintTest.

    public void testTransientExtras() throws Exception {
        final Bundle b = new Bundle();
        b.putBoolean("random_bool", true);
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTransientExtras(b)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        JobParameters params = kTestEnvironment.getLastJobParameters();
        assertEquals(b.size(), params.getTransientExtras().size());
        for (String key : b.keySet()) {
            assertEquals(b.get(key), params.getTransientExtras().get(key));
        }
    }

    // JobParameters.getTriggeredContentAuthorities() tested in TriggerContentTest.
    // JobParameters.getTriggeredContentUris() tested in TriggerContentTest.
    // JobParameters.isOverrideDeadlineExpired() tested in TimingConstraintTest.
}
