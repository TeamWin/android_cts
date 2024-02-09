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

package android.content.cts;

import static android.content.cts.contenturitestapp.IContentUriTestService.URI_NO_PERMISSION_ID;
import static android.content.cts.contenturitestapp.IContentUriTestService.URI_READ_PERMISSION_ID;
import static android.content.cts.contenturitestapp.IContentUriTestService.URI_WRITE_PERMISSION_ID;
import static android.content.cts.contenturitestapp.IContentUriTestService.URI_READ_WRITE_PERMISSION_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.cts.contenturitestapp.IContentUriTestService;
import android.net.Uri;
import android.os.IBinder;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests to verify Activity Manifest attribute
 * {@link android.R.attr#requireContentUriPermissionFromCaller} works as intended. Depending on the
 * attribute value and the passed content URIs, activity starts should be allowed or blocked by a
 * {@link SecurityException}.
 *
 * <p>Before all the test methods are run, {@link #classSetUp()} retrieves a list of content URIs
 * that it has read, write, read and write, and no access to. It does so by binding to TestService
 * in {@link android.content.cts.contenturitestapp}, which returns such list, and then unbinds from
 * it.
 *
 * <p>Each test in this class does the following:
 * <ol>
 *     <li>Depending on the tested scenario, the test creates an intent with a content URI in
 *         {@link Intent#getData()} and {@link Intent#getClipData()}.
 *     <li>Then the test tries to start an activity in
 *         {@link android.content.cts.contenturitestapp} that has a specific attribute value.
 *     <li>If the activity start is supposed to be blocked, the test catches a
 *         {@link SecurityException} and asserts that it was caught.
 *     <li>If the activity start is supposed to be allowed, the test asserts that the activity was
 *         invoked via a broadcast sent from the activity.
 * </ol>
 */
@RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
@RunWith(JUnit4.class)
public class ActivityRequireContentUriPermissionFromCallerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    // Test activities that are invoked
    private static final String TEST_PACKAGE = "android.content.cts.contenturitestapp";
    private static final ComponentName TEST_NONE_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$NoneContentUriActivity");
    private static final ComponentName TEST_READ_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$ReadContentUriActivity");
    private static final ComponentName TEST_WRITE_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$WriteContentUriActivity");
    private static final ComponentName TEST_READ_OR_WRITE_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$ReadOrWriteContentUriActivity");
    private static final ComponentName TEST_READ_AND_WRITE_ACTIVITY = new ComponentName(
            TEST_PACKAGE, TEST_PACKAGE + ".TestActivities$ReadAndWriteContentUriActivity");

    private static final String TEST_RECEIVER_ACTION =
            "android.content.cts.REQUIRE_CONTENT_URI_TEST_RECEIVER_ACTION";

    private static CountDownLatch sLatch;
    private TestReceiver mReceiver;

    // TestService details
    private static final int SERVICE_TIMEOUT = 15000;
    private static final String PKG_TEST_SERVICE = "android.content.cts.contenturitestapp";
    private static final String CLS_TEST_SERVICE = PKG_TEST_SERVICE + ".TestService";
    private static final ComponentName COMPONENT_CONTENT_URI_TEST_SERVICE =
            new ComponentName(PKG_TEST_SERVICE, CLS_TEST_SERVICE);

    private static IContentUriTestService sContentUriTestService;
    private static ServiceConnection sContentUriServiceConnection;

    // modeFlags equivalent constants
    private static final int NO_PERMISSION = 0;
    private static final int READ_PERMISSION = Intent.FLAG_GRANT_READ_URI_PERMISSION;
    private static final int WRITE_PERMISSION = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private static final int READ_AND_WRITE_PERMISSION = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private static Uri[] sUrisFromService;

    /**
     * Retrieves and saves Uris from
     * {@code android.content.cts.contenturitestapp.TestService#getContentUrisForManifest} into
     * {@link #sUrisFromService}. The Uris in the
     * array represent Uris this test has read, write, read and write, and no access to.
     */
    @BeforeClass
    public static void classSetUp() throws Exception {
        setUpContentUriTestServiceConnection();
        sUrisFromService = sContentUriTestService.getContentUrisForManifest();
        sContext.unbindService(sContentUriServiceConnection);
    }

    @Before
    public void testSetUp() throws Exception {
        resetCountDownLatch();
        mReceiver = new TestReceiver();
        IntentFilter filter = new IntentFilter(TEST_RECEIVER_ACTION);
        sContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() {
        sContext.unregisterReceiver(mReceiver);
    }

    @Test
    public void testNone_allModeFlags_doNotThrow() throws Exception {
        for (int modeFlags : new int[] {
                NO_PERMISSION,
                READ_PERMISSION,
                WRITE_PERMISSION,
                READ_AND_WRITE_PERMISSION
        }) {
            internalTest(TEST_NONE_ACTIVITY, modeFlags, /* assertThrow */ false, /* isOr */ false);
        }
    }

    @Test
    public void testRead_readModeFlags_doNotThrow() throws Exception {
        for (int modeFlags : new int[] { READ_PERMISSION, READ_AND_WRITE_PERMISSION }) {
            internalTest(TEST_READ_ACTIVITY, modeFlags, /* assertThrow */ false, /* isOr */ false);
        }
    }

    @Test
    public void testRead_noneAndWriteModeFlags_throw() throws Exception {
        for (int modeFlags : new int[]{ NO_PERMISSION, WRITE_PERMISSION }) {
            internalTest(TEST_READ_ACTIVITY, modeFlags, /* assertThrow */ true, /* isOr */ false);
        }
    }

    @Test
    public void testWrite_writeModeFlags_doNotThrow() throws Exception {
        for (int modeFlags : new int[] { WRITE_PERMISSION, READ_AND_WRITE_PERMISSION }) {
            internalTest(TEST_WRITE_ACTIVITY, modeFlags, /* assertThrow */ false, /* isOr */ false);
        }
    }

    @Test
    public void testWrite_noneAndReadModeFlags_throw() throws Exception {
        for (int modeFlags : new int[] { NO_PERMISSION, READ_PERMISSION }) {
            internalTest(TEST_WRITE_ACTIVITY, modeFlags, /* assertThrow */ true, /* isOr */ false);
        }
    }

    @Test
    public void testReadOrWrite_allButNoneModeFlags_doNotThrow() throws Exception {
        for (int modeFlags : new int[] {
                READ_PERMISSION,
                WRITE_PERMISSION,
                READ_AND_WRITE_PERMISSION
        }) {
            internalTest(TEST_READ_OR_WRITE_ACTIVITY, modeFlags, /* assertThrow */ false,
                    /* isOr */ true);
        }
    }

    @Test
    public void testReadOrWrite_noneModeFlags_throws() throws Exception {
        internalTest(TEST_READ_OR_WRITE_ACTIVITY, NO_PERMISSION, /* assertThrow */ true,
                /* isOr */ true);
    }

    @Test
    public void testReadAndWrite_readAndWriteModeFlags_doesNotThrow() throws Exception {
        internalTest(TEST_READ_AND_WRITE_ACTIVITY, READ_AND_WRITE_PERMISSION,
                /* assertThrow */ false, /* isOr */ false);
    }

    @Test
    public void testReadAndWrite_allButReadAndWriteModeFlags_throw() throws Exception {
        for (int modeFlags : new int[] { NO_PERMISSION, READ_PERMISSION, WRITE_PERMISSION }) {
            internalTest(TEST_READ_AND_WRITE_ACTIVITY, modeFlags, /* assertThrow */ true,
                    /* isOr */ false);
        }
    }

    private void internalTest(ComponentName component, int modeFlags, boolean assertThrow,
            boolean isOr) throws Exception {
        internalTest(component, modeFlags, assertThrow, isOr, /* uriInDataOtherwiseInClip */ true);
        internalTest(component, modeFlags, assertThrow, isOr, /* uriInDataOtherwiseInClip */ false);
    }

    private void internalTest(ComponentName component, int modeFlags, boolean assertThrow,
            boolean isOr, boolean uriInDataOtherwiseInClip) throws Exception {
        resetCountDownLatch();

        Intent intent = getTestIntent(component, modeFlags, uriInDataOtherwiseInClip);

        boolean securityExceptionCaught = false;
        try {
            sContext.startActivity(intent);
        } catch (SecurityException e) {
            securityExceptionCaught = true;
        }

        String errorMsg = getErrorMessage(modeFlags, assertThrow, isOr);
        assertEquals(errorMsg, securityExceptionCaught, assertThrow);

        if (!assertThrow) {
            assertActivityWasInvoked();
        }
    }

    private String getErrorMessage(int modeFlags, boolean assertThrow, boolean isOr) {
        String requiredModeFlags = modeFlagsToString(modeFlags, assertThrow, isOr);
        if (assertThrow) {
            return "Should throw a SecurityException because we don't have " + requiredModeFlags
                    + " access to the content URI";
        } else {
            return "Should not throw a SecurityException because we have " + requiredModeFlags
                    + " access to the content URI";
        }
    }

    private String modeFlagsToString(int modeFlags, boolean assertThrow, boolean isOr) {
        return switch (modeFlags) {
            case NO_PERMISSION -> assertThrow ? "any" : "no";
            case READ_PERMISSION -> "read";
            case WRITE_PERMISSION -> "write";
            case READ_AND_WRITE_PERMISSION -> isOr ? "read or write" : "read and write";
            default -> throw new RuntimeException("Invalid modeFlags");
        };
    }

    private Intent getTestIntent(ComponentName component, int modeFlags,
            boolean uriInDataOtherwiseInClip) {
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        Uri uri = getContentUriFromService(modeFlags);
        if (uriInDataOtherwiseInClip) {
            intent.setData(uri);
        } else {
            intent.setClipData(ClipData.newRawUri("", uri));
        }
        return intent;
    }

    private Uri getContentUriFromService(int modeFlags) {
        int id = switch (modeFlags) {
            case NO_PERMISSION -> URI_NO_PERMISSION_ID;
            case READ_PERMISSION -> URI_READ_PERMISSION_ID;
            case WRITE_PERMISSION -> URI_WRITE_PERMISSION_ID;
            case READ_AND_WRITE_PERMISSION -> URI_READ_WRITE_PERMISSION_ID;
            default -> throw new RuntimeException("Invalid modeFlags");
        };
        return sUrisFromService[id];
    }

    private void assertActivityWasInvoked() throws Exception {
        assertTrue("Activity was not invoked by the timeout",
                sLatch.await(10, TimeUnit.SECONDS));
    }

    private void resetCountDownLatch() {
        sLatch = new CountDownLatch(1);
    }

    private static void setUpContentUriTestServiceConnection() {
        sContentUriServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                sContentUriTestService = IContentUriTestService.Stub.asInterface(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                sContentUriTestService = null;
            }
        };

        Intent intent = new Intent();
        intent.setComponent(COMPONENT_CONTENT_URI_TEST_SERVICE);
        assertTrue(sContext.bindService(intent, sContentUriServiceConnection,
                Service.BIND_AUTO_CREATE));

        new PollingCheck(SERVICE_TIMEOUT) {
            protected boolean check() {
                return sContentUriTestService != null;
            }
        }.run();
    }

    public static final class TestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            sLatch.countDown();
        }
    }
}
