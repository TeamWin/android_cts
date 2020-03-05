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

package android.suspendapps.cts;

import static android.suspendapps.cts.Constants.TEST_APP_PACKAGE_NAME;
import static android.suspendapps.cts.SuspendTestUtils.addAndAssertProfileOwner;
import static android.suspendapps.cts.SuspendTestUtils.createSingleKeyBundle;
import static android.suspendapps.cts.SuspendTestUtils.removeDeviceAdmin;
import static android.suspendapps.cts.SuspendTestUtils.requestDpmAction;
import static com.android.suspendapps.suspendtestapp.SuspendTestReceiver.ACTION_REPORT_MY_PACKAGE_SUSPENDED;
import static com.android.suspendapps.suspendtestapp.SuspendTestReceiver.ACTION_REPORT_MY_PACKAGE_UNSUSPENDED;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.ACTION_SUSPEND;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.ACTION_UNSUSPEND;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class DualSuspendTests {
    private Context mContext;
    private Handler mReceiverHandler;
    private AppCommunicationReceiver mAppCommsReceiver;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mReceiverHandler = new Handler(Looper.getMainLooper());
        mAppCommsReceiver = new AppCommunicationReceiver(mContext);
        assumeTrue("Skipping test that requires device admin",
                FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        addAndAssertProfileOwner();
    }

    private boolean setSuspendViaDPM(boolean suspend) throws Exception {
        return requestDpmAction(suspend ? ACTION_SUSPEND : ACTION_UNSUSPEND,
                createSingleKeyBundle(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME),
                mReceiverHandler);
    }

    @Test
    public void testMyPackageSuspended() throws Exception {
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MY_PACKAGE_SUSPENDED);
        SuspendTestUtils.suspend(null, null, null);
        Intent receivedIntent = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive intent from app for first suspend", receivedIntent);
        assertEquals(ACTION_REPORT_MY_PACKAGE_SUSPENDED, receivedIntent.getAction());
        assertTrue("Suspend via dpm failed", setSuspendViaDPM(true));
        receivedIntent = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive intent from app for second suspend", receivedIntent);
        assertEquals(ACTION_REPORT_MY_PACKAGE_SUSPENDED, receivedIntent.getAction());
    }

    @Test
    public void testMyPackageUnsuspended() throws Exception {
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MY_PACKAGE_UNSUSPENDED);
        SuspendTestUtils.suspend(null, null, null);
        assertTrue("Suspend via dpm failed", setSuspendViaDPM(true));
        mAppCommsReceiver.drainPendingBroadcasts();
        SuspendTestUtils.unsuspendAll();
        Intent receivedIntent = mAppCommsReceiver.pollForIntent(5);
        if (receivedIntent != null) {
            fail("Unexpected intent " + receivedIntent.getAction() + " received");
        }
        assertTrue("Unsuspend via dpm failed", setSuspendViaDPM(false));
        receivedIntent = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive intent after second unsuspend", receivedIntent);
        assertEquals(ACTION_REPORT_MY_PACKAGE_UNSUSPENDED, receivedIntent.getAction());
    }

    @Test
    public void testIsPackageSuspended() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        assertFalse(pm.isPackageSuspended(TEST_APP_PACKAGE_NAME));
        SuspendTestUtils.suspend(null, null, null);
        assertTrue("Suspend via dpm failed", setSuspendViaDPM(true));
        assertTrue("Package should be suspended by both",
                pm.isPackageSuspended(TEST_APP_PACKAGE_NAME));
        SuspendTestUtils.unsuspendAll();
        assertTrue("Package should be suspended by dpm",
                pm.isPackageSuspended(TEST_APP_PACKAGE_NAME));
        SuspendTestUtils.suspend(null, null, null);
        assertTrue("Unsuspend via dpm failed", setSuspendViaDPM(false));
        assertTrue("Package should be suspended by shell",
                pm.isPackageSuspended(TEST_APP_PACKAGE_NAME));
        SuspendTestUtils.unsuspendAll();
        assertFalse("Package should be suspended by neither",
                pm.isPackageSuspended(TEST_APP_PACKAGE_NAME));
    }

    @After
    public void tearDown() throws Exception {
        mAppCommsReceiver.unregister();
        SuspendTestUtils.unsuspendAll();
        setSuspendViaDPM(false);
        removeDeviceAdmin();
    }
}
