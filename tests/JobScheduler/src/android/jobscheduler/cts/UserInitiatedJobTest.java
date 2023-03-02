/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.job.JobInfo.NETWORK_TYPE_ANY;
import static android.jobscheduler.cts.JobThrottlingTest.setTestPackageStandbyBucket;

import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.ScreenUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class UserInitiatedJobTest {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2_000;
    private static final int JOB_ID = UserInitiatedJobTest.class.hashCode();

    private Context mContext;
    private UiDevice mUiDevice;
    private TestAppInterface mTestAppInterface;
    private NetworkingHelper mNetworkingHelper;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
        setTestPackageStandbyBucket(mUiDevice, JobThrottlingTest.Bucket.ACTIVE);
        mNetworkingHelper =
                new NetworkingHelper(InstrumentationRegistry.getInstrumentation(), mContext);
        mNetworkingHelper.setAllNetworksEnabled(true); // all user-initiated jobs require a network
    }

    @After
    public void tearDown() throws Exception {
        mTestAppInterface.cleanup();
        mNetworkingHelper.tearDown();
    }

    @Test
    public void testJobUidState() throws Exception {
        // Go through the notification click/BAL route of scheduling the job so the proc state
        // data comes from being elevated by the running job and not because of the app being
        // in a higher state.
        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(2 * DEFAULT_WAIT_TIMEOUT_MS));
            mTestAppInterface.assertJobUidState(ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND,
                    ActivityManager.PROCESS_CAPABILITY_NETWORK,
                    201 /* ProcessList.PERCEPTIBLE_APP_ADJ + 1 */);
        }
    }

    /** Test that UIJs for the TOP app start immediately and there is no limit on the number. */
    @Test
    public void testTopUiUnlimited() throws Exception {
        final int standardConcurrency = 64;
        final int numUijs = standardConcurrency + 1;
        ScreenUtils.setScreenOn(true);
        mTestAppInterface.startAndKeepTestActivity(true);
        for (int i = 0; i < numUijs; ++i) {
            mTestAppInterface.scheduleJob(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, i,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY
                    ));
        }
        for (int i = 0; i < numUijs; ++i) {
            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(i, DEFAULT_WAIT_TIMEOUT_MS));
        }
    }

    @Test
    public void testSchedulingBal() throws Exception {
        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue(mTestAppInterface
                    .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));
        }
    }

    @Test
    public void testSchedulingBg() throws Exception {
        // Close the activity and turn the screen off so the app isn't considered TOP.
        mTestAppInterface.closeActivity();
        ScreenUtils.setScreenOn(false);
        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
        assertTrue(mTestAppInterface
                .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_FAILURE));
    }

    @Test
    public void testSchedulingTop() throws Exception {
        ScreenUtils.setScreenOn(true);
        mTestAppInterface.startAndKeepTestActivity(true);
        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
        assertTrue(mTestAppInterface
                .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));
    }
}
