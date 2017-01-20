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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ClientTest {
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
    private static final String EXTRA_ACTIVITY_RESULT =
            "com.android.cts.ephemeraltest.EXTRA_ACTIVITY_RESULT";

    /**
     * Intents that we expect the system to expose activities to ephemeral apps to handle.
     */
    private static final Intent[] EXPECTED_EXPOSED_SYSTEM_INTENTS = new Intent[] {
        makeIntent(Intent.ACTION_OPEN_DOCUMENT, Intent.CATEGORY_OPENABLE, "*/*"),
        makeIntent(Intent.ACTION_OPEN_DOCUMENT, null, "*/*"),
        makeIntent(Intent.ACTION_GET_CONTENT, Intent.CATEGORY_OPENABLE, "*/*"),
        makeIntent(Intent.ACTION_GET_CONTENT, null, "*/*"),
        makeIntent(Intent.ACTION_OPEN_DOCUMENT_TREE, null, null),
        makeIntent(Intent.ACTION_CREATE_DOCUMENT, Intent.CATEGORY_OPENABLE, "text/plain"),
        makeIntent(Intent.ACTION_CREATE_DOCUMENT, null, "text/plain"),

    };

    private BroadcastReceiver mReceiver;
    private final SynchronousQueue<BroadcastResult> mResultQueue = new SynchronousQueue<>();

    @Before
    public void setUp() throws Exception {
        final IntentFilter filter =
                new IntentFilter("com.android.cts.ephemeraltest.START_ACTIVITY");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        mReceiver = new ActivityBroadcastReceiver(mResultQueue);
        InstrumentationRegistry.getContext().registerReceiver(mReceiver, filter);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getContext().unregisterReceiver(mReceiver);
    }

    @Test
    public void testQuery() throws Exception {
        final Intent queryIntent = new Intent(ACTION_QUERY_ACTIVITY);
        final List<ResolveInfo> resolveInfo = InstrumentationRegistry
                .getContext().getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
        if (resolveInfo == null || resolveInfo.size() == 0) {
            fail("didn't resolve any intents");
        }
        assertThat(resolveInfo.size(), is(2));
        assertThat(resolveInfo.get(0).activityInfo.packageName,
                is("com.android.cts.ephemeralapp1"));
        assertThat(resolveInfo.get(0).activityInfo.name,
                is("com.android.cts.ephemeralapp1.EphemeralActivity"));
        assertThat(resolveInfo.get(1).activityInfo.packageName,
                is("com.android.cts.normalapp"));
        assertThat(resolveInfo.get(1).activityInfo.name,
                is("com.android.cts.normalapp.ExposedActivity"));
    }

    @Test
    public void testStartNormal() throws Exception {
        // start the normal activity
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY);
            InstrumentationRegistry
                    .getContext().startActivity(startNormalIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the normal activity; directed package
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY);
            startNormalIntent.setPackage("com.android.cts.normalapp");
            InstrumentationRegistry
                    .getContext().startActivity(startNormalIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the normal activity; directed component
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY);
            startNormalIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.NormalActivity"));
            InstrumentationRegistry
                    .getContext().startActivity(startNormalIntent, null /*options*/);
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
//            InstrumentationRegistry.getContext().startActivity(startViewIntent, null /*options*/);
//            final BroadcastResult testResult = getResult();
//            assertThat("com.android.cts.normalapp", is(testResult.packageName));
//            assertThat("NormalWebActivity", is(testResult.activityName));
//        }
    }

    @Test
    public void testStartExposed() throws Exception {
        // start the exposed activity
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED_ACTIVITY);
            InstrumentationRegistry
                    .getContext().startActivity(startExposedIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertThat(testResult.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(testResult.activityName,
                    is("ExposedActivity"));
            assertThat(testResult.result,
                    is("PASS"));
        }

        // start the exposed activity; directed package
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED_ACTIVITY);
            startExposedIntent.setPackage("com.android.cts.normalapp");
            InstrumentationRegistry
                    .getContext().startActivity(startExposedIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertThat(testResult.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(testResult.activityName,
                    is("ExposedActivity"));
            assertThat(testResult.result,
                    is("PASS"));
        }

        // start the exposed activity; directed component
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED_ACTIVITY);
            startExposedIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.ExposedActivity"));
            InstrumentationRegistry
                    .getContext().startActivity(startExposedIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertThat(testResult.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(testResult.activityName,
                    is("ExposedActivity"));
            assertThat(testResult.result,
                    is("PASS"));
        }
    }

    @Test
    public void testStartEphemeral() throws Exception {
        // start the ephemeral activity
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY);
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertThat(testResult.packageName,
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.activityName,
                    is("EphemeralActivity"));
        }

        // start the ephemeral activity; directed package
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY);
            startEphemeralIntent.setPackage("com.android.cts.ephemeralapp1");
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertThat(testResult.packageName,
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.activityName,
                    is("EphemeralActivity"));
        }

        // start the ephemeral activity; directed component
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY);
            startEphemeralIntent.setComponent(
                    new ComponentName("com.android.cts.ephemeralapp1",
                            "com.android.cts.ephemeralapp1.EphemeralActivity"));
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final BroadcastResult testResult = getResult();
            assertThat(testResult.packageName,
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.activityName,
                    is("EphemeralActivity"));
        }
    }

    @Test
    public void testExposedSystemActivities() throws Exception {
        for (Intent queryIntent : EXPECTED_EXPOSED_SYSTEM_INTENTS) {
            assertIntentHasExposedActivities(queryIntent);
        }
    }

    private void assertIntentHasExposedActivities(Intent queryIntent) throws Exception {
        final List<ResolveInfo> resolveInfo = InstrumentationRegistry
                .getContext().getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
        if (resolveInfo == null || resolveInfo.size() == 0) {
            fail("No activies found for Intent: " + queryIntent);
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

    private static Intent makeIntent(String action, String category, String mimeType) {
        Intent intent = new Intent(action);
        if (category != null) {
            intent.addCategory(category);
        }
        if (mimeType != null) {
            intent.setType(mimeType);
        }
        return intent;
    }

    private static class BroadcastResult {
        final String packageName;
        final String activityName;
        final String result;

        public BroadcastResult(String packageName, String activityName, String result) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.result = result;
        }

        @Override
        public String toString() {
            return "[pkg=" + packageName
                    + ", activity=" + activityName
                    + ", result=" + result + "]";
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
                                intent.getStringExtra(EXTRA_ACTIVITY_NAME),
                                intent.getStringExtra(EXTRA_ACTIVITY_RESULT)
                                ),
                        5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
