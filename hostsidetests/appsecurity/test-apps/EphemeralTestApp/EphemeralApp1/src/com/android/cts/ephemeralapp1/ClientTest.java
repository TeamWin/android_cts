/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.ephemeralapp1;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class ClientTest extends InstrumentationTestCase {
    /** Action to start normal test activities */
    private static final String ACTION_START_NORMAL_ACTIVITY =
            "com.android.cts.ephemeraltest.START_NORMAL";
    /** Action to start normal, exposed test activities */
    private static final String ACTION_START_EXPOSED_ACTIVITY =
            "com.android.cts.ephemeraltest.START_EXPOSED";
    /** Action to start ephemeral test activities */
    private static final String ACTION_START_EPHEMERAL_ACTIVITY =
            "com.android.cts.ephemeraltest.START_EPHEMERAL";
    /** Action to query for test activities */
    private static final String ACTION_QUERY_ACTIVITY =
            "com.android.cts.ephemeraltest.QUERY";
    private static final String EXTRA_ACTIVITY_NAME =
            "com.android.cts.ephemeraltest.EXTRA_ACTIVITY_NAME";

    private BroadcastReceiver mReceiver;
    private final SynchronousQueue<BroadcastResult> mResultQueue = new SynchronousQueue<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final IntentFilter filter =
                new IntentFilter("com.android.cts.ephemeraltest.START_ACTIVITY");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        mReceiver = new ActivityBroadcastReceiver(mResultQueue);
        getInstrumentation().getContext().registerReceiver(mReceiver, filter);
    }

    @Override
    protected void tearDown() throws Exception {
        getInstrumentation().getContext().unregisterReceiver(mReceiver);
        super.tearDown();
    }

    public void testQuery() throws Exception {
        final Intent queryIntent = new Intent(ACTION_QUERY_ACTIVITY);
        final List<ResolveInfo> resolveInfo = getInstrumentation().getContext().getPackageManager()
                .queryIntentActivities(queryIntent, 0 /*flags*/);
        if (resolveInfo == null || resolveInfo.size() == 0) {
            fail("didn't resolve any intents");
        }
        assertEquals(dumpResolvedIntents(resolveInfo), 2, resolveInfo.size());
        assertEquals("com.android.cts.ephemeralapp1",
                resolveInfo.get(0).activityInfo.packageName);
        assertEquals("com.android.cts.ephemeralapp1.EphemeralActivity",
                resolveInfo.get(0).activityInfo.name);
        assertEquals("com.android.cts.normalapp",
                resolveInfo.get(1).activityInfo.packageName);
        assertEquals("com.android.cts.normalapp.ExposedActivity",
                resolveInfo.get(1).activityInfo.name);
    }

    public void testStartNormal() throws Exception {
        // start the normal activity
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY);
            getInstrumentation().getContext().startActivity(startNormalIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the normal activity; directed package
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY);
            startNormalIntent.setPackage("com.android.cts.normalapp");
            getInstrumentation().getContext().startActivity(startNormalIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the normal activity; directed component
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY);
            startNormalIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.NormalActivity"));
            getInstrumentation().getContext().startActivity(startNormalIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

// TODO: Make this work. It's unclear the best way to expose VIEW/BROWSABLE just for launching
//        // start the normal activity; using VIEW/BROWSABLE
//        {
//            final Intent startViewIntent = new Intent(Intent.ACTION_VIEW);
//            startViewIntent.addCategory(Intent.CATEGORY_BROWSABLE);
//            startViewIntent.setData(Uri.parse("https://cts.google.com/normal"));
//            getInstrumentation().getContext().startActivity(startViewIntent, null /*options*/);
//            final BroadcastResult testResult = getResult();
//            assertEquals("com.android.cts.normalapp", testResult.packageName);
//            assertEquals("NormalActivity", testResult.activityName);
//        }
    }

    public void testStartExposed() throws Exception {
        // start the exposed activity
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED_ACTIVITY);
            getInstrumentation().getContext().startActivity(startExposedIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertEquals("com.android.cts.normalapp", testResult.packageName);
            assertEquals("ExposedActivity", testResult.activityName);
        }

        // start the exposed activity; directed package
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED_ACTIVITY);
            startExposedIntent.setPackage("com.android.cts.normalapp");
            getInstrumentation().getContext().startActivity(startExposedIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertEquals("com.android.cts.normalapp", testResult.packageName);
            assertEquals("ExposedActivity", testResult.activityName);
        }

        // start the exposed activity; directed component
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED_ACTIVITY);
            startExposedIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.ExposedActivity"));
            getInstrumentation().getContext().startActivity(startExposedIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertEquals("com.android.cts.normalapp", testResult.packageName);
            assertEquals("ExposedActivity", testResult.activityName);
        }
    }

    public void testStartEphemeral() throws Exception {
        // start the ephemeral activity
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY);
            getInstrumentation().getContext().startActivity(startEphemeralIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertEquals("com.android.cts.ephemeralapp1", testResult.packageName);
            assertEquals("EphemeralActivity", testResult.activityName);
        }

        // start the ephemeral activity; directed package
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY);
            startEphemeralIntent.setPackage("com.android.cts.ephemeralapp1");
            getInstrumentation().getContext().startActivity(startEphemeralIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertEquals("com.android.cts.ephemeralapp1", testResult.packageName);
            assertEquals("EphemeralActivity", testResult.activityName);
        }

        // start the ephemeral activity; directed component
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY);
            startEphemeralIntent.setComponent(
                    new ComponentName("com.android.cts.ephemeralapp1",
                            "com.android.cts.ephemeralapp1.EphemeralActivity"));
            getInstrumentation().getContext().startActivity(startEphemeralIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertEquals("com.android.cts.ephemeralapp1", testResult.packageName);
            assertEquals("EphemeralActivity", testResult.activityName);
        }
    }

    private BroadcastResult getResult() {
        final BroadcastResult result;
        try {
            result = mResultQueue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (result == null) {
            throw new IllegalStateException("Activity didn't receive a Result in 5 seconds");
        }
        return result;
    }

    private String dumpResolvedIntents(List<ResolveInfo> resolveInfo) {
        final StringBuffer sb = new StringBuffer();
        if (resolveInfo == null || resolveInfo.size() == 0) {
            sb.append("No intents");
        } else {
            for (int i = resolveInfo.size() - 1; i >= 0; --i) {
                sb.append("[" + i + "] " + resolveInfo.get(i).toString() + "\n");
            }
        }
        return sb.toString();
    }

    private static class BroadcastResult {
        final String packageName;
        final String activityName;
        public BroadcastResult(String packageName, String activityName) {
            this.packageName = packageName;
            this.activityName = activityName;
        }

        @Override
        public String toString() {
            return "[pkg=" + packageName + ", activity=" + activityName + "]";
        }
    }

    private static class ActivityBroadcastReceiver extends BroadcastReceiver {
        private final SynchronousQueue<BroadcastResult> mQueue;
        public ActivityBroadcastReceiver(SynchronousQueue<BroadcastResult> queue) {
            mQueue = queue;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                mQueue.offer(
                        new BroadcastResult(
                                intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME),
                                intent.getStringExtra(EXTRA_ACTIVITY_NAME)),
                        5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
