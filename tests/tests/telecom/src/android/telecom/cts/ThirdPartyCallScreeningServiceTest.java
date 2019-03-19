/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts;

import static android.telecom.cts.TestUtils.shouldTestTelecom;
import static android.telecom.cts.TestUtils.waitOnAllHandlers;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;

import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.CallIdentification;
import android.telecom.CallScreeningService;
import android.telecom.TelecomManager;
import android.telecom.cts.screeningtestapp.CallScreeningServiceControl;
import android.telecom.cts.screeningtestapp.CtsCallScreeningService;
import android.telecom.cts.screeningtestapp.ICallScreeningControl;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ThirdPartyCallScreeningServiceTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = ThirdPartyCallScreeningServiceTest.class.getSimpleName();
    private static final String TEST_APP_NAME = "CTSCSTest";
    private static final String TEST_APP_PACKAGE = "android.telecom.cts.screeningtestapp";
    private static final String TEST_APP_COMPONENT =
            "android.telecom.cts.screeningtestapp/"
                    + "android.telecom.cts.screeningtestapp.CtsCallScreeningService";
    private static final int ASYNC_TIMEOUT = 10000;
    private static final String ROLE_CALL_SCREENING = "android.app.role.CALL_SCREENING";
    private static final CallIdentification SAMPLE_CALL_ID = new CallIdentification.Builder()
            .setName("Joe's Laundromat")
            .setDescription("1234 Dirtysocks Lane, Cleanville, USA")
            .setDetails("Open 24 hrs; free parking")
            .setPhoto(null)
            .setNuisanceConfidence(CallIdentification.CONFIDENCE_LIKELY_NOT_NUISANCE)
            .build();

    private ICallScreeningControl mCallScreeningControl;
    private RoleManager mRoleManager;
    private String mPreviousCallScreeningPackage;
    private Handler mHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) {
            return;
        }
        mHandler = new Handler(Looper.getMainLooper());
        mRoleManager = (RoleManager) mContext.getSystemService(Context.ROLE_SERVICE);
        setupControlBinder();
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        rememberPreviousCallScreeningApp();
        // Ensure CTS app holds the call screening role.
        addRoleHolder(ROLE_CALL_SCREENING,
                CtsCallScreeningService.class.getPackage().getName());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!mShouldTestTelecom) {
            return;
        }

        if (mCallScreeningControl != null) {
            mCallScreeningControl.reset();
        }

        // Remove the test app from the screening role.
        removeRoleHolder(ROLE_CALL_SCREENING, CtsCallScreeningService.class.getPackage().getName());

        if (!TextUtils.isEmpty(mPreviousCallScreeningPackage)) {
            addRoleHolder(ROLE_CALL_SCREENING, mPreviousCallScreeningPackage);
        }
    }

    /**
     * Verifies that a {@link android.telecom.CallScreeningService} can provide call identification
     * information and that it will be passed on to the {@link android.telecom.InCallService}.
     * Also verifies that the call identification information is logged to the call log as expected.
     * @throws Exception
     */
    public void testProvideCallIdentificationForIncoming() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        // Tell the test app to set its call id info.
        mCallScreeningControl.setProviderCallIdentification(
                SAMPLE_CALL_ID.getName(),
                SAMPLE_CALL_ID.getDescription(),
                SAMPLE_CALL_ID.getDetails(),
                SAMPLE_CALL_ID.getPhoto(),
                SAMPLE_CALL_ID.getNuisanceConfidence());

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        Uri phoneNumber = createRandomTestNumber();
        // Create a new incoming call.
        addAndVerifyNewIncomingCall(phoneNumber, null);

        // Wait for call id to be passed back to InCallService
        assertCallIdentification(SAMPLE_CALL_ID, TEST_APP_NAME, TEST_APP_PACKAGE);

        // Disconnect the call
        mInCallCallbacks.getService().rejectAllCalls();
        assertNumCalls(mInCallCallbacks.getService(), 0);

        // Wait for it to log.
        waitOnAllHandlers(getInstrumentation());

        // Wait for call log update.
        callLogEntryLatch.await();

        // Query the latest entry into the call log and verify the call identification information
        // was logged appropriately
        Cursor callsCursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, null,
                null, null, CallLog.Calls._ID + " DESC limit 1;");
        int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);
        int callTypeIndex = callsCursor.getColumnIndex(CallLog.Calls.TYPE);
        int blockReasonIndex = callsCursor.getColumnIndex(CallLog.Calls.BLOCK_REASON);
        int callIdAppNameIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_APP_NAME);
        int callIdPackageNameIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_PACKAGE_NAME);
        int callIdNameIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_NAME);
        int callIdDescriptionIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_DESCRIPTION);
        int callIdDetailsIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_DETAILS);
        int callIdConfidenceIndex = callsCursor.getColumnIndex(
                CallLog.Calls.CALL_ID_NUISANCE_CONFIDENCE);
        if (callsCursor.moveToNext()) {
            String number = callsCursor.getString(numberIndex);
            int callType = callsCursor.getInt(callTypeIndex);
            int blockReason = callsCursor.getInt(blockReasonIndex);
            String idAppName = callsCursor.getString(callIdAppNameIndex);
            String idPackage = callsCursor.getString(callIdPackageNameIndex);
            String name = callsCursor.getString(callIdNameIndex);
            String description = callsCursor.getString(callIdDescriptionIndex);
            String details = callsCursor.getString(callIdDetailsIndex);
            int confidence = callsCursor.getInt(callIdConfidenceIndex);
            // Make sure nobody sneaky tries to pre-populate the call log and fudge the tests.
            assertEquals(phoneNumber.getSchemeSpecificPart(), number);
            assertEquals(CallLog.Calls.REJECTED_TYPE, callType);
            assertEquals(CallLog.Calls.BLOCK_REASON_NOT_BLOCKED, blockReason);
            assertEquals(TEST_APP_NAME, idAppName);
            assertEquals(TEST_APP_PACKAGE, idPackage);
            assertEquals(SAMPLE_CALL_ID.getName(), name);
            assertEquals(SAMPLE_CALL_ID.getDetails(), details);
            assertEquals(SAMPLE_CALL_ID.getDescription(), description);
            assertEquals(SAMPLE_CALL_ID.getNuisanceConfidence(), confidence);
        } else {
            fail("Call was not logged; bee-boop");
        }
    }

    /**
     * Verifies that a {@link android.telecom.CallScreeningService} can reject an incoming call.
     * Ensures that the system logs the blocked call to the call log.
     *
     * @throws Exception
     */
    public void testRejectCall() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        // Tell the test app to block the call.
        mCallScreeningControl.setCallResponse(true /* shouldDisallowCall */,
                true /* shouldRejectCall */, false /* shouldSilenceCall */, false /* shouldSkipCallLog */,
                true /* shouldSkipNotification */);

        addIncomingAndVerifyBlocked();
    }

    /**
     * Similar to {@link #testRejectCall()}, except the {@link android.telecom.CallScreeningService}
     * tries to skip logging the call to the call log.  We verify that Telecom still logs the call
     * to the call log, retaining the API behavior documented in
     * {@link android.telecom.CallScreeningService#respondToCall(Call.Details, CallScreeningService.CallResponse)}
     * @throws Exception
     */
    public void testRejectCallAndTryToSkipCallLog() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        // Tell the test app to block the call; also try to skip logging the call.
        mCallScreeningControl.setCallResponse(true /* shouldDisallowCall */,
                true /* shouldRejectCall */, false /* shouldSilenceCall */, true /* shouldSkipCallLog */,
                true /* shouldSkipNotification */);

        addIncomingAndVerifyBlocked();
    }

    /**
     * Verifies that a {@link android.telecom.CallScreeningService} set the extra to silence a call.
     * @throws Exception
     */
    public void testIncomingCallHasSilenceExtra() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        // Tell the test app to silence the call.
        mCallScreeningControl.setCallResponse(false /* shouldDisallowCall */,
            false /* shouldRejectCall */, true /* shouldSilenceCall */,
            false /* shouldSkipCallLog */, false /* shouldSkipNotification */);

        addIncomingAndVerifyCallExtraForSilence(true);
    }

    /**
     * Verifies that a {@link android.telecom.CallScreeningService} did not set the extra to silence an incoming call.
     * @throws Exception
     */
    public void testIncomingCallDoesNotHaveHaveSilenceExtra() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        // Tell the test app to not silence the call.
        mCallScreeningControl.setCallResponse(false /* shouldDisallowCall */,
                false /* shouldRejectCall */, false /* shouldSilenceCall */,
                false /* shouldSkipCallLog */, false /* shouldSkipNotification */);

        addIncomingAndVerifyCallExtraForSilence(false);
    }

    public void testProvideCallIdentificationForOutgoing() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        Uri phoneNumber = placeOutgoingCall();

        // Query the latest entry into the call log and verify the call identification information
        // was logged appropriately
        Cursor callsCursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, null,
                null, null, CallLog.Calls._ID + " DESC limit 1;");
        int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);
        int callTypeIndex = callsCursor.getColumnIndex(CallLog.Calls.TYPE);
        int blockReasonIndex = callsCursor.getColumnIndex(CallLog.Calls.BLOCK_REASON);
        int callIdAppNameIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_APP_NAME);
        int callIdPackageNameIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_PACKAGE_NAME);
        int callIdNameIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_NAME);
        int callIdDescriptionIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_DESCRIPTION);
        int callIdDetailsIndex = callsCursor.getColumnIndex(CallLog.Calls.CALL_ID_DETAILS);
        int callIdConfidenceIndex = callsCursor.getColumnIndex(
                CallLog.Calls.CALL_ID_NUISANCE_CONFIDENCE);
        if (callsCursor.moveToNext()) {
            String number = callsCursor.getString(numberIndex);
            int callType = callsCursor.getInt(callTypeIndex);
            int blockReason = callsCursor.getInt(blockReasonIndex);
            String idAppName = callsCursor.getString(callIdAppNameIndex);
            String idPackage = callsCursor.getString(callIdPackageNameIndex);
            String name = callsCursor.getString(callIdNameIndex);
            String description = callsCursor.getString(callIdDescriptionIndex);
            String details = callsCursor.getString(callIdDetailsIndex);
            int confidence = callsCursor.getInt(callIdConfidenceIndex);
            // Make sure nobody sneaky tries to pre-populate the call log and fudge the tests.
            assertEquals(phoneNumber.getSchemeSpecificPart(), number);
            assertEquals(CallLog.Calls.OUTGOING_TYPE, callType);
            assertEquals(CallLog.Calls.BLOCK_REASON_NOT_BLOCKED, blockReason);
            assertEquals(TEST_APP_NAME, idAppName);
            assertEquals(TEST_APP_PACKAGE, idPackage);
            assertEquals(SAMPLE_CALL_ID.getName(), name);
            assertEquals(SAMPLE_CALL_ID.getDetails(), details);
            assertEquals(SAMPLE_CALL_ID.getDescription(), description);
            assertEquals(SAMPLE_CALL_ID.getNuisanceConfidence(), confidence);
        } else {
            fail("Call was not logged; bee-boop");
        }
    }

    /**
     * Verify parceling and unparceling of {@link CallIdentification} information.
     */
    public void testParcelUnparcel() {
        Parcel p = Parcel.obtain();
        SAMPLE_CALL_ID.writeToParcel(p, 0);
        p.setDataPosition(0);
        CallIdentification unparceled = SAMPLE_CALL_ID.CREATOR.createFromParcel(p);
        assertEquals(SAMPLE_CALL_ID, unparceled);
    }

    /**
     * Verifies operation of the {@link TelecomManager#reportNuisanceCallStatus(Uri, boolean)} API
     * for an outgoing call; should not be possible to report as nuisance.
     * @throws Exception
     */
    public void testReportNuisanceInvalid() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        Uri phoneNumber = placeOutgoingCall();

        // Report the call as a nuisance call.
        mTelecomManager.reportNuisanceCallStatus(phoneNumber, true);

        // Block on the control service and wait for the nuisance report; we don't expect one so we
        // will only wait a short time to avoid this test blocking for a long time.
        mCallScreeningControl.waitForNuisanceReport(TestUtils.WAIT_FOR_NUISANCE_REPORT_TIMEOUT_MS);

        // We should not have gotten a response back.
        assertFalse(mCallScreeningControl.isNuisanceReportReceived());
    }

    /**
     * Verifies operation of the {@link TelecomManager#reportNuisanceCallStatus(Uri, boolean)} API
     * for an incoming call.
     * @throws Exception
     */
    public void testReportNuisanceIncoming() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        Uri phoneNumber = addIncoming(true /* disconnectImmediately */);

        // Disconnect the incoming call so it can be logged.
        mInCallCallbacks.getService().disconnectAllCalls();
        assertNumCalls(mInCallCallbacks.getService(), 0);

        // Report the call as a nuisance call.
        mTelecomManager.reportNuisanceCallStatus(phoneNumber, true);

        // Block on the control service and wait for the nuisance report; we will potentially wait
        // longer because we really do expect something here.
        mCallScreeningControl.waitForNuisanceReport(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        // We should have gotten a response back.
        assertTrue(mCallScreeningControl.isNuisanceReportReceived());
    }

    private Uri placeOutgoingCall() throws Exception {
        // Tell the test app to set its call id info.
        mCallScreeningControl.setProviderCallIdentification(
                SAMPLE_CALL_ID.getName(),
                SAMPLE_CALL_ID.getDescription(),
                SAMPLE_CALL_ID.getDetails(),
                SAMPLE_CALL_ID.getPhoto(),
                SAMPLE_CALL_ID.getNuisanceConfidence());

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        Uri phoneNumber = createRandomTestNumber();
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, phoneNumber);
        // Create a new outgoing call.
        placeAndVerifyCall(extras);

        // Wait for call id to be passed back to InCallService
        assertCallIdentification(SAMPLE_CALL_ID, TEST_APP_NAME, TEST_APP_PACKAGE);

        mInCallCallbacks.getService().disconnectAllCalls();
        assertNumCalls(mInCallCallbacks.getService(), 0);

        // Wait for it to log.
        callLogEntryLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        return phoneNumber;
    }

    private Uri addIncoming(boolean disconnectImmediately) throws Exception {
        // Add call through TelecomManager; we can't use the test methods since they assume a call
        // makes it through to the InCallService; this is blocked so it shouldn't.
        Uri testNumber = createRandomTestNumber();

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, testNumber);
        mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);

        // Wait until the new incoming call is processed.
        waitOnAllHandlers(getInstrumentation());

        if (disconnectImmediately) {
            // Disconnect the call
            mInCallCallbacks.getService().disconnectAllCalls();
            assertNumCalls(mInCallCallbacks.getService(), 0);
        }

        // Wait for the content observer to report that we have gotten a new call log entry.
        callLogEntryLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        return testNumber;
    }

    private void addIncomingAndVerifyBlocked() throws Exception {
        Uri testNumber = addIncoming(false);

        // Query the latest entry into the call log.
        Cursor callsCursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, null,
                null, null, CallLog.Calls._ID + " DESC limit 1;");
        int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);
        int callTypeIndex = callsCursor.getColumnIndex(CallLog.Calls.TYPE);
        int blockReasonIndex = callsCursor.getColumnIndex(CallLog.Calls.BLOCK_REASON);
        int callScreeningAppNameIndex = callsCursor.getColumnIndex(
                CallLog.Calls.CALL_SCREENING_APP_NAME);
        int callScreeningCmpNameIndex = callsCursor.getColumnIndex(
                CallLog.Calls.CALL_SCREENING_COMPONENT_NAME);
        if (callsCursor.moveToNext()) {
            String number = callsCursor.getString(numberIndex);
            int callType = callsCursor.getInt(callTypeIndex);
            int blockReason = callsCursor.getInt(blockReasonIndex);
            String screeningAppName = callsCursor.getString(callScreeningAppNameIndex);
            String screeningComponentName = callsCursor.getString(callScreeningCmpNameIndex);
            assertEquals(testNumber.getSchemeSpecificPart(), number);
            assertEquals(CallLog.Calls.BLOCKED_TYPE, callType);
            assertEquals(CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, blockReason);
            assertEquals(TEST_APP_NAME, screeningAppName);
            assertEquals(TEST_APP_COMPONENT, screeningComponentName);
        } else {
            fail("Blocked call was not logged.");
        }
    }

    private void addIncomingAndVerifyCallExtraForSilence(boolean expectedIsSilentRingingExtraSet) throws Exception {
        Uri testNumber = addIncoming(false);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        // Verify that the call extra matches expectation
                        Call call = mInCallCallbacks.getService().getLastCall();
                        return expectedIsSilentRingingExtraSet ==
                                call.getDetails().getExtras().getBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call extra - verification failed, expected the extra " +
                        "EXTRA_SILENT_RINGING_REQUESTED to be set:" + expectedIsSilentRingingExtraSet
        );
    }

    private CountDownLatch getCallLogEntryLatch() {
        CountDownLatch changeLatch = new CountDownLatch(1);
        mContext.getContentResolver().registerContentObserver(
                CallLog.Calls.CONTENT_URI, true,
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        mContext.getContentResolver().unregisterContentObserver(this);
                        changeLatch.countDown();
                        super.onChange(selfChange);
                    }
                });
        return changeLatch;
    }

    /**
     * Sets up a binder used to control the CallScreeningServiceCtsTestApp.
     * This app is a standalone APK so that it can reside in a package name outside of the one the
     * CTS test itself runs in (since that APK is where the CTS InCallService resides).
     * @throws InterruptedException
     */
    private void setupControlBinder() throws InterruptedException {
        Intent bindIntent = new Intent(CallScreeningServiceControl.CONTROL_INTERFACE_ACTION);
        bindIntent.setComponent(CallScreeningServiceControl.CONTROL_INTERFACE_COMPONENT);
        final CountDownLatch bindLatch = new CountDownLatch(1);

        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mCallScreeningControl = ICallScreeningControl.Stub.asInterface(service);
                bindLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mCallScreeningControl = null;
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        bindLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private void assertCallIdentification(final CallIdentification expectedIdentification,
            final String expectedAppName, final String expectedPackageName) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        CallIdentification callIdentification = mInCallCallbacks.getService()
                                .getLastCall()
                                .getDetails()
                                .getCallIdentification();
                        return ((expectedIdentification == null) == (callIdentification == null))
                                && expectedIdentification.getNuisanceConfidence()
                                        == callIdentification.getNuisanceConfidence()
                                && expectedIdentification.getName().equals(
                                        callIdentification.getName())
                                && expectedIdentification.getDescription().equals(
                                        callIdentification.getDescription())
                                && expectedIdentification.getDetails().equals(
                                        callIdentification.getDetails())
                                && expectedAppName.equals(
                                        callIdentification.getCallScreeningAppName())
                                && expectedPackageName.equals(
                                        callIdentification.getCallScreeningPackageName());
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected call Id " + expectedIdentification
        );
    }

    /**
     * Use RoleManager to query the previous call screening app so we can restore it later.
     */
    private void rememberPreviousCallScreeningApp() {
        runWithShellPermissionIdentity(() -> {
            List<String> callScreeningApps = mRoleManager.getRoleHolders(ROLE_CALL_SCREENING);
            if (!callScreeningApps.isEmpty()) {
                mPreviousCallScreeningPackage = callScreeningApps.get(0);
            } else {
                mPreviousCallScreeningPackage = null;
            }
        });
    }

    private void addRoleHolder(String roleName, String packageName)
            throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);

        runWithShellPermissionIdentity(() -> mRoleManager.addRoleHolderAsUser(roleName,
                packageName, 0, user, executor, successful -> {
                    try {
                        queue.put(successful);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));
        boolean result = queue.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }

    private void removeRoleHolder(String roleName, String packageName)
            throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);

        runWithShellPermissionIdentity(() -> mRoleManager.removeRoleHolderAsUser(roleName,
                packageName, 0, user, executor, successful -> {
                    try {
                        queue.put(successful);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));
        boolean result = queue.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }

}
