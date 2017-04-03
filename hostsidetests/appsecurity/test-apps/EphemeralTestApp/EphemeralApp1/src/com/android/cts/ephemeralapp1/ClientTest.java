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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ServiceManager.ServiceNotFoundException;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.android.cts.util.TestResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ClientTest {
    /** Action to start normal test activities */
    private static final String ACTION_START_NORMAL =
            "com.android.cts.ephemeraltest.START_NORMAL";
    /** Action to start normal, exposed test activities */
    private static final String ACTION_START_EXPOSED =
            "com.android.cts.ephemeraltest.START_EXPOSED";
    /** Action to start ephemeral test activities */
    private static final String ACTION_START_EPHEMERAL =
            "com.android.cts.ephemeraltest.START_EPHEMERAL";
    /** Action to start private ephemeral test activities */
    private static final String ACTION_START_EPHEMERAL_PRIVATE =
            "com.android.cts.ephemeraltest.START_EPHEMERAL_PRIVATE";
    /** Action to query for test activities */
    private static final String ACTION_QUERY =
            "com.android.cts.ephemeraltest.QUERY";
    private static final String EXTRA_ACTIVITY_NAME =
            "com.android.cts.ephemeraltest.EXTRA_ACTIVITY_NAME";
    private static final String EXTRA_ACTIVITY_RESULT =
            "com.android.cts.ephemeraltest.EXTRA_ACTIVITY_RESULT";

    /**
     * Intents that we expect the system to expose activities to ephemeral apps to handle.
     */
    private static final Intent[] EXPECTED_EXPOSED_SYSTEM_INTENTS = new Intent[] {
        // Camera
        makeIntent(MediaStore.ACTION_IMAGE_CAPTURE, null, null, null),
        makeIntent(MediaStore.ACTION_VIDEO_CAPTURE, null, null, null),
        // Contacts
        makeIntent(Intent.ACTION_PICK, null, ContactsContract.Contacts.CONTENT_TYPE, null),
        makeIntent(Intent.ACTION_PICK, null,
                ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE, null),
        makeIntent(Intent.ACTION_PICK, null,
                ContactsContract.CommonDataKinds.Email.CONTENT_TYPE, null),
        makeIntent(Intent.ACTION_PICK, null,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_TYPE, null),
        makeIntent(Intent.ACTION_INSERT, null, ContactsContract.Contacts.CONTENT_TYPE, null),
        // Email
        makeIntent(Intent.ACTION_SEND, null, "text/plain", Uri.parse("mailto:")),
        // File Storage
        makeIntent(Intent.ACTION_OPEN_DOCUMENT, Intent.CATEGORY_OPENABLE, "*/*", null),
        makeIntent(Intent.ACTION_OPEN_DOCUMENT, null, "*/*", null),
        makeIntent(Intent.ACTION_GET_CONTENT, Intent.CATEGORY_OPENABLE, "*/*", null),
        makeIntent(Intent.ACTION_GET_CONTENT, null, "*/*", null),
        makeIntent(Intent.ACTION_OPEN_DOCUMENT_TREE, null, null, null),
        makeIntent(Intent.ACTION_CREATE_DOCUMENT, Intent.CATEGORY_OPENABLE, "text/plain", null),
        makeIntent(Intent.ACTION_CREATE_DOCUMENT, null, "text/plain", null),
        // Phone call
        makeIntent(Intent.ACTION_DIAL, null, null, Uri.parse("tel:")),
        // SMS
        makeIntent(Intent.ACTION_SEND, null, "text/plain", Uri.parse("sms:")),
        makeIntent(Intent.ACTION_SEND, null, "text/plain", Uri.parse("smsto:")),
        // Web
        makeIntent(Intent.ACTION_VIEW, null, "text/html", Uri.parse("https://example.com")),
    };

    private BroadcastReceiver mReceiver;
    private final SynchronousQueue<TestResult> mResultQueue = new SynchronousQueue<>();

    @Before
    public void setUp() throws Exception {
        final IntentFilter filter =
                new IntentFilter("com.android.cts.ephemeraltest.START_ACTIVITY");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        mReceiver = new ActivityBroadcastReceiver(mResultQueue);
        InstrumentationRegistry.getContext()
                .registerReceiver(mReceiver, filter, true /*visibleToEmphemeral*/);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getContext().unregisterReceiver(mReceiver);
    }

    @Test
    public void testQuery() throws Exception {
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).activityInfo.packageName,
                    is("com.android.cts.ephemeralapp1"));
            assertThat(resolveInfo.get(0).activityInfo.name,
                    is("com.android.cts.ephemeralapp1.EphemeralActivity"));
            assertThat(resolveInfo.get(0).instantAppAvailable,
                    is(true));
            assertThat(resolveInfo.get(1).activityInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).activityInfo.name,
                    is("com.android.cts.normalapp.ExposedActivity"));
            assertThat(resolveInfo.get(1).instantAppAvailable,
                    is(false));
        }

        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry
                    .getContext().getPackageManager().queryIntentServices(queryIntent, 0 /*flags*/);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).serviceInfo.packageName,
                    is("com.android.cts.ephemeralapp1"));
            assertThat(resolveInfo.get(0).serviceInfo.name,
                    is("com.android.cts.ephemeralapp1.EphemeralService"));
            assertThat(resolveInfo.get(1).serviceInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).serviceInfo.name,
                    is("com.android.cts.normalapp.ExposedService"));
            assertThat(resolveInfo.get(1).instantAppAvailable,
                    is(false));
        }
    }

    @Test
    public void testStartNormal() throws Exception {
        // start the normal activity
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL);
            InstrumentationRegistry
                    .getContext().startActivity(startNormalIntent, null /*options*/);
            final TestResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the normal activity; directed package
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL);
            startNormalIntent.setPackage("com.android.cts.normalapp");
            InstrumentationRegistry
                    .getContext().startActivity(startNormalIntent, null /*options*/);
            final TestResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the normal activity; directed component
        try {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL);
            startNormalIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.NormalActivity"));
            InstrumentationRegistry
                    .getContext().startActivity(startNormalIntent, null /*options*/);
            final TestResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

// TODO: Ideally we should have a test for this. However, it shows a disambig between the
//       the normal app and chrome; for which there is no easy solution.
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

        // We don't attempt to start the service since it will merely return and not
        // provide any feedback. The alternative is to wait for the broadcast timeout
        // but it's silly to artificially slow down CTS. We'll rely on queryIntentService
        // to check whether or not the service is actually exposed
    }

    @Test
    public void testStartExposed() throws Exception {
        // start the exposed activity
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED);
            InstrumentationRegistry
                    .getContext().startActivity(startExposedIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("ExposedActivity"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getEphemeralPackageInfoExposed(),
                    is(true));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start the exposed activity; directed package
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED);
            startExposedIntent.setPackage("com.android.cts.normalapp");
            InstrumentationRegistry
                    .getContext().startActivity(startExposedIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("ExposedActivity"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getEphemeralPackageInfoExposed(),
                    is(true));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start the exposed activity; directed component
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED);
            startExposedIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.ExposedActivity"));
            InstrumentationRegistry
                    .getContext().startActivity(startExposedIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("ExposedActivity"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getEphemeralPackageInfoExposed(),
                    is(true));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start the exposed service; directed package
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED);
            startExposedIntent.setPackage("com.android.cts.normalapp");
            InstrumentationRegistry.getContext().startService(startExposedIntent);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("ExposedService"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getEphemeralPackageInfoExposed(),
                    is(true));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start the exposed service; directed component
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED);
            startExposedIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.ExposedService"));
            InstrumentationRegistry.getContext().startService(startExposedIntent);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("ExposedService"));
            assertThat(testResult.getMethodName(),
                    is("onStartCommand"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getEphemeralPackageInfoExposed(),
                    is(true));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // bind to the exposed service; directed package
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED);
            startExposedIntent.setPackage("com.android.cts.normalapp");
            final TestServiceConnection connection = new TestServiceConnection();
            try {
                assertThat(InstrumentationRegistry.getContext().bindService(
                        startExposedIntent, connection, Context.BIND_AUTO_CREATE /*flags*/),
                        is(true));
                final TestResult testResult = getResult();
                assertThat(testResult.getPackageName(),
                        is("com.android.cts.normalapp"));
                assertThat(testResult.getComponentName(),
                        is("ExposedService"));
                assertThat(testResult.getMethodName(),
                        is("onBind"));
                assertThat(testResult.getStatus(),
                        is("PASS"));
                assertThat(testResult.getEphemeralPackageInfoExposed(),
                        is(true));
                assertThat(testResult.getException(),
                        is(nullValue()));
            } finally {
                InstrumentationRegistry.getContext().unbindService(connection);
            }
        }

        // bind to the exposed service; directed component
        {
            final Intent startExposedIntent = new Intent(ACTION_START_EXPOSED);
            startExposedIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.ExposedService"));
            final TestServiceConnection connection = new TestServiceConnection();
            try {
                assertThat(InstrumentationRegistry.getContext().bindService(
                        startExposedIntent, connection, Context.BIND_AUTO_CREATE /*flags*/),
                        is(true));
                final TestResult testResult = getResult();
                assertThat(testResult.getPackageName(),
                        is("com.android.cts.normalapp"));
                assertThat(testResult.getComponentName(),
                        is("ExposedService"));
                assertThat(testResult.getMethodName(),
                        is("onBind"));
                assertThat(testResult.getStatus(),
                        is("PASS"));
                assertThat(testResult.getException(),
                        is(nullValue()));
            } finally {
                InstrumentationRegistry.getContext().unbindService(connection);
            }
        }
    }

    @Test
    public void testStartEphemeral() throws Exception {
        // start the ephemeral activity
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL);
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.getComponentName(),
                    is("EphemeralActivity"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start the ephemeral activity; directed package
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL);
            startEphemeralIntent.setPackage("com.android.cts.ephemeralapp1");
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.getComponentName(),
                    is("EphemeralActivity"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start the ephemeral activity; directed component
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL);
            startEphemeralIntent.setComponent(
                    new ComponentName("com.android.cts.ephemeralapp1",
                            "com.android.cts.ephemeralapp1.EphemeralActivity"));
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.getComponentName(),
                    is("EphemeralActivity"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start a private ephemeral activity
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_PRIVATE);
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.getComponentName(),
                    is("EphemeralActivity2"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start a private ephemeral activity; directed package
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_PRIVATE);
            startEphemeralIntent.setPackage("com.android.cts.ephemeralapp1");
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.getComponentName(),
                    is("EphemeralActivity2"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start a private ephemeral activity; directed component
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_PRIVATE);
            startEphemeralIntent.setComponent(
                    new ComponentName("com.android.cts.ephemeralapp1",
                            "com.android.cts.ephemeralapp1.EphemeralActivity2"));
            InstrumentationRegistry
                    .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.getComponentName(),
                    is("EphemeralActivity2"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start a private ephemeral activity; directed component
        {
            final Intent startEphemeralIntent = new Intent();
            startEphemeralIntent.setComponent(
                    new ComponentName("com.android.cts.ephemeralapp1",
                            "com.android.cts.ephemeralapp1.EphemeralActivity3"));
            InstrumentationRegistry
            .getContext().startActivity(startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.ephemeralapp1"));
            assertThat(testResult.getComponentName(),
                    is("EphemeralActivity3"));
            assertThat(testResult.getStatus(),
                    is("PASS"));
            assertThat(testResult.getException(),
                    is(nullValue()));
        }

        // start the ephemeral service; directed package
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL);
            startEphemeralIntent.setPackage("com.android.cts.ephemeralapp1");
            try {
                InstrumentationRegistry.getContext().startService(startEphemeralIntent);
                final TestResult testResult = getResult();
                assertThat(testResult.getPackageName(),
                        is("com.android.cts.ephemeralapp1"));
                assertThat(testResult.getComponentName(),
                        is("EphemeralService"));
                assertThat(testResult.getMethodName(),
                        is("onStartCommand"));
                assertThat(testResult.getStatus(),
                        is("PASS"));
                assertThat(testResult.getException(),
                        is(nullValue()));
            } finally {
                InstrumentationRegistry.getContext().stopService(startEphemeralIntent);
            }
        }

        // start the ephemeral service; directed component
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL);
            startEphemeralIntent.setComponent(
                    new ComponentName("com.android.cts.ephemeralapp1",
                            "com.android.cts.ephemeralapp1.EphemeralService"));
            try {
                assertThat(InstrumentationRegistry.getContext().startService(startEphemeralIntent),
                        is(notNullValue()));
                final TestResult testResult = getResult();
                assertThat(testResult.getPackageName(),
                        is("com.android.cts.ephemeralapp1"));
                assertThat(testResult.getComponentName(),
                        is("EphemeralService"));
                assertThat(testResult.getMethodName(),
                        is("onStartCommand"));
                assertThat(testResult.getStatus(),
                        is("PASS"));
                assertThat(testResult.getException(),
                        is(nullValue()));
            } finally {
                InstrumentationRegistry.getContext().stopService(startEphemeralIntent);
            }
        }

        // bind to the ephemeral service; directed package
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL);
            startEphemeralIntent.setPackage("com.android.cts.ephemeralapp1");
            final TestServiceConnection connection = new TestServiceConnection();
            try {
                assertThat(InstrumentationRegistry.getContext().bindService(
                        startEphemeralIntent, connection, Context.BIND_AUTO_CREATE /*flags*/),
                        is(true));
                final TestResult testResult = getResult();
                assertThat(testResult.getPackageName(),
                        is("com.android.cts.ephemeralapp1"));
                assertThat(testResult.getComponentName(),
                        is("EphemeralService"));
                assertThat(testResult.getMethodName(),
                        is("onBind"));
                assertThat(testResult.getStatus(),
                        is("PASS"));
                assertThat(testResult.getException(),
                        is(nullValue()));
            } finally {
                InstrumentationRegistry.getContext().unbindService(connection);
            }
        }

        // bind to the ephemeral service; directed component
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL);
            startEphemeralIntent.setComponent(
                    new ComponentName("com.android.cts.ephemeralapp1",
                            "com.android.cts.ephemeralapp1.EphemeralService"));
            final TestServiceConnection connection = new TestServiceConnection();
            try {
                assertThat(InstrumentationRegistry.getContext().bindService(
                        startEphemeralIntent, connection, Context.BIND_AUTO_CREATE /*flags*/),
                        is(true));
                final TestResult testResult = getResult();
                assertThat(testResult.getPackageName(),
                        is("com.android.cts.ephemeralapp1"));
                assertThat(testResult.getComponentName(),
                        is("EphemeralService"));
                assertThat(testResult.getMethodName(),
                        is("onBind"));
                assertThat(testResult.getStatus(),
                        is("PASS"));
                assertThat(testResult.getException(),
                        is(nullValue()));
            } finally {
                InstrumentationRegistry.getContext().unbindService(connection);
            }
        }
    }

    @Test
    public void testBuildSerialUnknown() throws Exception {
        assertThat(Build.SERIAL, is(Build.UNKNOWN));
    }

    @Test
    public void testPackageInfo() throws Exception {
        PackageInfo info;
        // Test own package info.
        info = InstrumentationRegistry.getContext().getPackageManager()
                .getPackageInfo("com.android.cts.ephemeralapp1", 0);
        assertThat(info.packageName,
                is("com.android.cts.ephemeralapp1"));

        // Test exposed app package info.
        info = InstrumentationRegistry.getContext().getPackageManager()
                .getPackageInfo("com.android.cts.normalapp", 0);
        assertThat(info.packageName,
                is("com.android.cts.normalapp"));

        // Test unexposed app package info not accessible.
        try {
            info = InstrumentationRegistry.getContext().getPackageManager()
                    .getPackageInfo("com.android.cts.unexposedapp", 0);
            fail("Instant apps should not be able to access PackageInfo for an app that does not" +
                    " expose itself to Instant Apps.");
        } catch (PackageManager.NameNotFoundException expected) {
        }
        // Test Instant App (with visibleToInstantApp components) still isn't accessible.
        try {
            info = InstrumentationRegistry.getContext().getPackageManager()
                    .getPackageInfo("com.android.cts.ephemeralapp2", 0);
            fail("Instant apps should not be able to access PackageInfo for another Instant App.");
        } catch (PackageManager.NameNotFoundException expected) {
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

    private TestResult getResult() {
        final TestResult result;
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

    private static Intent makeIntent(String action, String category, String mimeType, Uri data) {
        Intent intent = new Intent(action);
        if (category != null) {
            intent.addCategory(category);
        }
        if (mimeType != null) {
            intent.setType(mimeType);
        }
        if (data != null) {
            intent.setData(data);
        }
        return intent;
    }

    private static class ActivityBroadcastReceiver extends BroadcastReceiver {
        private final SynchronousQueue<TestResult> mQueue;
        public ActivityBroadcastReceiver(SynchronousQueue<TestResult> queue) {
            mQueue = queue;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                mQueue.offer(intent.getParcelableExtra(TestResult.EXTRA_TEST_RESULT),
                        5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class TestServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }
}
