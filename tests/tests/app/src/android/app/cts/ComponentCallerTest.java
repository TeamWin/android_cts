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
import static android.app.cts.testcomponentcaller.Constants.MODE_FLAGS_TO_CHECK;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_SECURITY_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.SEND_TEST_BROADCAST_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.START_TEST_ACTIVITY_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_ACTIVITY;
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
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link android.app.ComponentCaller#checkContentUriPermission}.
 *
 * To imitate a caller-callee relationship between apps, this test uses a helper app
 * CtsTestComponentCallerApp, which calls the tested API and then sends the relevant results
 * (exceptions, permission results) back to the test via broadcast.
 */
@RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
@RunWith(JUnit4.class)
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

    private static final int[] ALL_MODE_FLAGS = {
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    };

    private static final int[] WRITE_MODE_FLAGS = {
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    };

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
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void
    testCheckContentUriPermission_throwsIfCallerOfApiDoesNotHaveTheSameAccessToContentUri()
            throws Exception {
        for (int modeFlagsToCheck : ALL_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getUriInDataSendBroadcastTestIntent(TestProvider.CONTENT_URI,
                    modeFlagsToCheck);

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertTrue("Should throw a SecurityException because the caller of the API doesn't have"
                    + " the same " + modeFlagsToString(modeFlagsToCheck) + " access to a content"
                    + " URI", TestResults.sIsSecurityExceptionCaught);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_throwsIfContentUriWasNotPassedAtLaunch()
            throws Exception {
        for (int modeFlagsToCheck : ALL_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getSendBroadcastTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                    modeFlagsToCheck);

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertTrue("Should throw an IllegalArgumentException because the supplied content URI"
                    + " was not passed at launch", TestResults.sIsIllegalArgumentExceptionCaught);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void
    testCheckContentUriPermission_returnsCorrectResultEvenIfCallerOfActivityGrantsAndDies()
            throws Exception {
        for (int modeFlagsToCheck : ALL_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getStartActivityTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                    modeFlagsToCheck);

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertEquals("Should return granted with "
                            + modeFlagsToString(modeFlagsToCheck) + " even if the caller of the"
                            + " activity dies",
                    PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_getDataContentUriViaPermission_noPermission()
            throws Exception {
        for (int modeFlagsToCheck : ALL_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getUriInDataSendBroadcastTestIntent(
                    CONTENT_URI_NO_PERMISSION, modeFlagsToCheck);

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertEquals("Should return denied with "
                            + modeFlagsToString(modeFlagsToCheck) + " because we have no access to"
                            + " the content URI",
                    PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_getDataContentUriViaPermission_hasRead()
            throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_getDataContentUriViaPermission_hasReadButNoWrite()
            throws Exception {
        for (int modeFlagsToCheck : WRITE_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getUriInDataSendBroadcastTestIntent(
                    CONTENT_URI_READ_PERMISSION, modeFlagsToCheck);

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertEquals("Should return denied because we don't have the write permission",
                    PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_clipDataContentUri_noPermission() throws Exception {
        for (int modeFlagsToCheck : ALL_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                    modeFlagsToCheck);
            intent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertEquals("Should return denied with "
                            + modeFlagsToString(modeFlagsToCheck) + " because we have no access to"
                            + " the content URI",
                    PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_clipDataContentUri_hasRead() throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_clipDataContentUri_hasReadButNoWrite()
            throws Exception {
        for (int modeFlagsToCheck : WRITE_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                    modeFlagsToCheck);
            intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertEquals("Should return denied because we don't have the write permission",
                    PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.ComponentCaller#checkContentUriPermission"})
    public void testCheckContentUriPermission_contentUriViaGrant() throws Exception {
        for (int modeFlagsToCheck : ALL_MODE_FLAGS) {
            TestResults.reset();

            Intent intent = getUriInDataSendBroadcastTestIntent(TestProvider.CONTENT_URI,
                    modeFlagsToCheck);
            intent.addFlags(modeFlagsToCheck);

            mContext.startActivity(intent);

            assertActivityWasInvoked();
            assertEquals("Should return granted because we granted "
                            + modeFlagsToString(modeFlagsToCheck),
                    PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
        }
    }

    private Intent getSendBroadcastTestIntent(int uriLocationId,
            int modeFlagsToCheck) {
        Intent intent = new Intent();
        intent.setComponent(HELPER_APP_ACTIVITY);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(URI_LOCATION_ID, uriLocationId);
        intent.putExtra(ACTION_ID, SEND_TEST_BROADCAST_ACTION_ID);
        intent.putExtra(MODE_FLAGS_TO_CHECK, modeFlagsToCheck);
        return intent;
    }

    private Intent getUriInDataSendBroadcastTestIntent(Uri uri,
            int modeFlagsToCheck) {
        Intent intent = getSendBroadcastTestIntent(URI_IN_DATA_LOCATION_ID,
                modeFlagsToCheck);
        intent.setAction(Intent.ACTION_ATTACH_DATA);
        intent.setData(uri);
        return intent;
    }

    private Intent getStartActivityTestIntent(int uriLocationId,
            int modeFlagsToCheck) {
        Intent intent = getSendBroadcastTestIntent(uriLocationId, modeFlagsToCheck);
        intent.putExtra(ACTION_ID, START_TEST_ACTIVITY_ACTION_ID);
        return intent;
    }

    private String modeFlagsToString(int modeFlags) {
        return switch (modeFlags) {
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

    public static final class TestActivity extends Activity {
        @Override
        public void onStart() {
            super.onStart();
            TestResults.sReceivedUri = getIntent().getData();
            int modeFlags = getIntent().getIntExtra(MODE_FLAGS_TO_CHECK, -1);
            if (TestResults.sReceivedUri != null) {
                TestResults.sCheckContentUriPermissionRes = getInitialCaller()
                        .checkContentUriPermission(TestResults.sReceivedUri,
                                modeFlags);
            }
            TestResults.sLatch.countDown();
            finish();
        }
    }

    public static final class TestProvider extends ContentProvider {
        private static final String AUTHORITY = "android.app.cts.componentcaller.provider";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

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
}
