/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.cts;

import static android.app.cts.testcomponentcaller.Constants.ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_URI;
import static android.app.cts.testcomponentcaller.Constants.IS_NEW_INTENT;
import static android.app.cts.testcomponentcaller.Constants.MODE_FLAGS_TO_CHECK;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_SECURITY_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.SEND_TEST_BROADCAST_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.START_TEST_ACTIVITY_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_INITIAL_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_PACKAGE;
import static android.app.cts.testcomponentcaller.Constants.INVALID_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_CLIP_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.TEST_RECEIVER_ACTION;
import static android.app.cts.testcomponentcaller.Constants.NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ComponentCaller;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for {@link android.app.ComponentCaller#checkContentUriPermission}.
 *
 * To imitate a caller-callee relationship between apps, this test uses a helper app
 * CtsTestComponentCallerApp, which calls the tested API and then sends the relevant results
 * (exceptions, permission results) back to the test via broadcast.
 */
@RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
@RunWith(TestParameterInjector.class)
public class ComponentCallerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    /** This test package doesn't have access to this content URI */
    private static final Uri CONTENT_URI_NO_PERMISSION =
            Uri.parse("content://" + HELPER_APP_PACKAGE + ".provider");

    /** This test package has read access to this content URI via a permission */
    private static final Uri CONTENT_URI_READ_PERMISSION =
            Uri.parse("content://" + HELPER_APP_PACKAGE + ".provider/path");

    private Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private TestReceiver mReceiver;

    @Before
    public final void setUp() {
        TestResults.reset();
        mReceiver = new TestReceiver();
        IntentFilter filter = new IntentFilter(TEST_RECEIVER_ACTION);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @After
    public final void tearDown() {
        mContext.unregisterReceiver(mReceiver);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_throwsIfCallerOfApiDoesNotHaveTheSameAccessToContentUri(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw a SecurityException because the caller of the API doesn't have the"
                        + " same " + modeFlagsToString(modeFlagsToCheck) + " access to a content"
                        + " URI",
                TestResults.sIsSecurityExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_throwsIfContentUriWasNotPassedAtLaunch(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException because the supplied content URI was"
                        + " not passed at launch", TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_returnsCorrectResultEvenIfCallerOfActivityGrantsAndDies(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getStartActivityTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted with " + modeFlagsToString(modeFlagsToCheck) + " even"
                        + " if the caller of the activity dies",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_getDataContentUriViaPermission_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_NO_PERMISSION, modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with "
                        + modeFlagsToString(modeFlagsToCheck) + " because we have no access to"
                        + " the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_getDataContentUriViaPermission_hasRead()
            throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_READ_PERMISSION,
                ModeFlags.READ, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_getDataContentUriViaPermission_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_clipDataContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with "
                        + modeFlagsToString(modeFlagsToCheck) + " because we have no access to"
                        + " the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_clipDataContentUri_hasRead()
            throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_clipDataContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityInitialCaller_checkContentUriPermission_contentUriViaGrant(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.addFlags(modeFlagsToCheck.mValue);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we granted "
                        + modeFlagsToString(modeFlagsToCheck),
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_throwsIfCallerOfApiDoesNotHaveTheSameAccessToContentUri(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is not supposed to throw a
        // SecurityException. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(HELPER_APP_URI, modeFlagsToCheck,
                newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertTrue("Should throw a SecurityException because the new intent caller of the API"
                        + "doesn't have the same " + modeFlagsToString(modeFlagsToCheck)
                        + " access to a content URI",
                TestResults.sIsSecurityExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_throwsIfContentUriWasNotPassedAtLaunch(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is not supposed to throw an
        // IllegalArgumentException. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(HELPER_APP_URI, modeFlagsToCheck,
                newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException because the supplied content URI was"
                        + " not passed at launch",
                TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_returnsCorrectResultEvenIfCallerOfActivityGrantsAndDies(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getStartActivityTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getStartActivityTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted with " + modeFlagsToString(modeFlagsToCheck) + " even"
                        + " if the caller of the activity dies",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_getDataContentUriViaPermission_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_NO_PERMISSION, modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_getDataContentUriViaPermission_hasRead(
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_NO_PERMISSION, ModeFlags.READ, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_READ_PERMISSION,
                ModeFlags.READ,  newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_getDataContentUriViaPermission_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, ModeFlags.READ, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_clipDataContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with "
                        + modeFlagsToString(modeFlagsToCheck) + " because we have no access to"
                        + " the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_clipDataContentUri_hasRead(
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        newIntent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_clipDataContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityNewIntentCaller_checkContentUriPermission_contentUriViaGrant(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_NO_PERMISSION,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.addFlags(modeFlagsToCheck.mValue);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we granted "
                        + modeFlagsToString(modeFlagsToCheck),
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    private Intent getSendBroadcastTestIntent(int uriLocationId, ModeFlags modeFlagsToCheck,
            ComponentName component) {
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(URI_LOCATION_ID, uriLocationId);
        intent.putExtra(ACTION_ID, SEND_TEST_BROADCAST_ACTION_ID);
        intent.putExtra(MODE_FLAGS_TO_CHECK, modeFlagsToCheck.mValue);
        intent.putExtra(IS_NEW_INTENT, isNewIntentComponent(component));
        return intent;
    }

    private Intent getUriInDataSendBroadcastTestIntent(Uri uri, ModeFlags modeFlagsToCheck,
            ComponentName component) {
        Intent intent = getSendBroadcastTestIntent(URI_IN_DATA_LOCATION_ID,
                modeFlagsToCheck, component);
        intent.setAction(Intent.ACTION_ATTACH_DATA);
        intent.setData(uri);
        return intent;
    }

    private Intent getStartActivityTestIntent(int uriLocationId, ModeFlags modeFlagsToCheck,
            ComponentName component) {
        Intent intent = getSendBroadcastTestIntent(uriLocationId, modeFlagsToCheck, component);
        intent.putExtra(ACTION_ID, START_TEST_ACTIVITY_ACTION_ID);
        return intent;
    }

    private String modeFlagsToString(ModeFlags modeFlags) {
        return switch (modeFlags.mValue) {
            case Intent.FLAG_GRANT_READ_URI_PERMISSION -> "read";
            case Intent.FLAG_GRANT_WRITE_URI_PERMISSION -> "write";
            case Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION ->
                    "read and write";
            default -> throw new RuntimeException("Invalid modeFlags");
        };
    }

    private void assertActivityWasInvoked() throws Exception {
        assertTrue("Activity was not invoked by the timeout",
                TestResults.sLatch.await(10, TimeUnit.SECONDS));
    }

    private boolean isNewIntentComponent(ComponentName component) {
        return component.equals(HELPER_APP_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY)
                || component.equals(HELPER_APP_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY);
    }

    /** Results for each test. Use {@link #reset()} to reset all results. */
    private static final class TestResults {
        static CountDownLatch sLatch;
        static boolean sIsSecurityExceptionCaught;
        static boolean sIsIllegalArgumentExceptionCaught;
        static Uri sReceivedUri;
        static int sCheckContentUriPermissionRes;

        static void reset() {
            sLatch = new CountDownLatch(1);
            sIsSecurityExceptionCaught = false;
            sIsIllegalArgumentExceptionCaught = false;
            sReceivedUri = null;
            sCheckContentUriPermissionRes = INVALID_PERMISSION_RESULT;
        }
    }

    public static final class TestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            TestResults.sIsSecurityExceptionCaught =
                    intent.getBooleanExtra(EXTRA_SECURITY_EXCEPTION_CAUGHT, false);
            TestResults.sIsIllegalArgumentExceptionCaught =
                    intent.getBooleanExtra(EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT, false);
            TestResults.sCheckContentUriPermissionRes =
                    intent.getIntExtra(EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT,
                            INVALID_PERMISSION_RESULT);
            TestResults.sLatch.countDown();
        }
    }

    public static class InitialCallerTestActivity extends Activity {
        private static final String TAG = "InitialCallerTestActivity";
        @Override
        public void onStart() {
            super.onStart();
            Log.i(TAG, "onStart");
            performTest(getIntent(), getInitialCaller());
        }

        protected void performTest(Intent intent, ComponentCaller caller) {
            TestResults.sReceivedUri = intent.getData();
            int modeFlags = intent.getIntExtra(MODE_FLAGS_TO_CHECK, -1);
            boolean isNewIntent = intent.getBooleanExtra(IS_NEW_INTENT, false);
            if (TestResults.sReceivedUri != null) {
                TestResults.sCheckContentUriPermissionRes = caller
                        .checkContentUriPermission(TestResults.sReceivedUri,
                                modeFlags);
            }
            TestResults.sLatch.countDown();
            if (!isNewIntent) {
                finish();
            }
        }
    }

    public static final class NewIntentGetCurrentCallerTestActivity
            extends InitialCallerTestActivity {
        private static final String TAG = "NewIntentGetCurrentCallerTestActivity";
        @Override
        public void onNewIntent(Intent intent) {
            Log.i(TAG, "onNewIntent");
            performTest(intent, getCurrentCaller());
            finish();
        }
    }

    public static final class NewIntentOverloadCallerTestActivity
            extends InitialCallerTestActivity {
        private static final String TAG = "NewIntentOverloadCallerTestActivity";
        @Override
        public void onNewIntent(Intent intent, ComponentCaller caller) {
            Log.i(TAG, "onNewIntent");
            performTest(intent, caller);
            finish();
        }
    }

    public static final class TestProvider extends ContentProvider {
        private static final AtomicInteger sId = new AtomicInteger(1);
        private static final String AUTHORITY = "android.app.cts.componentcaller.provider";
        private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

        public static Uri getContentUri() {
            return ContentUris.withAppendedId(CONTENT_URI, sId.getAndIncrement());
        }
        @Override
        public boolean onCreate() {
            return false;
        }

        @Nullable
        @Override
        public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                @Nullable String selection, @Nullable String[] selectionArgs,
                            @Nullable String sortOrder) {
            return null;
        }

        @Nullable
        @Override
        public String getType(@NonNull Uri uri) {
            return null;
        }

        @Nullable
        @Override
        public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
            return null;
        }

        @Override
        public int delete(@NonNull Uri uri, @Nullable String selection,
                @Nullable String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, @Nullable ContentValues values,
                @Nullable String selection, @Nullable String[] selectionArgs) {
            return 0;
        }
    }

    public enum ModeFlags {
        READ(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        WRITE(Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        READ_AND_WRITE(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        final int mValue;

        ModeFlags(int value) {
            this.mValue = value;
        }
    }

    private static final class WriteModeFlagsProvider implements TestParameterValuesProvider {
        @Override
        public List<ModeFlags> provideValues() {
            return ImmutableList.of(ModeFlags.WRITE, ModeFlags.READ_AND_WRITE);
        }
    }

    public enum NewIntentCallerActivity {
        GET_CURRENT_CALLER(HELPER_APP_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY),
        OVERLOAD_CALLER(HELPER_APP_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY);

        final ComponentName mComponent;

        NewIntentCallerActivity(ComponentName component) {
            this.mComponent = component;
        }
    }
}
