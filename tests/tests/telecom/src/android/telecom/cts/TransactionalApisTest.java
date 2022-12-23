/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.telecom.cts;

import static android.telecom.CallAttributes.DIRECTION_INCOMING;
import static android.telecom.CallAttributes.DIRECTION_OUTGOING;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallAudioState;
import android.telecom.CallControl;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class tests calls added with the API
 * {@link TelecomManager#addCall(CallAttributes, Executor, OutcomeReceiver, CallEventCallback)}
 * and controlled by {@link CallControl} or {@link CallEventCallback}.
 */
public class TransactionalApisTest extends BaseTelecomTestWithMockServices {

    private static final String TAG = TransactionalApisTest.class.getSimpleName();
    private static final String TEST_NAME_1 = "Alan Turing";
    private static final Uri TEST_URI_1 = Uri.parse("tel:123-TEST");
    private static final String TEST_NAME_2 = "Mike Tyson";
    private static final Uri TEST_URI_2 = Uri.parse("tel:456-TEST");
    private static final String TEL_CLEAN_STUCK_CALLS_CMD = "telecom cleanup-stuck-calls";

    // CallControl
    private static final String SET_ACTIVE = "SetActive";
    private static final String SET_INACTIVE = "SetInactive";
    private static final String REJECT = "Reject";
    private static final String DISCONNECT = "Disconnect";

    // Fail messages
    private static final String FAIL_MSG_CALL_CONTROL_NULL =
            "onResult: callControl object is null";
    private static final String FAIL_MSG_OUTCOME_RECEIVER =
            "failed to receive OutcomeReceiver#onResult";
    private static final String FAIL_MSG_DURING_CLEANUP =
            "exception thrown while cleaning up tests";

    // inner classes
    /**
     * simulates a VoIP app construct of a Call object that accepts every CallEventCallback
     */
    private static class TestVoipCall implements CallEventCallback {
        private static final String TAG = "MyVoipCall";
        private final String mCallId;
        private String mTelecomCallId = "";
        CallControl mCallControl;

        TestVoipCall(String id) {
            mCallId = id;
        }

        public String getTelecomCallId() {
            return mTelecomCallId;
        }

        public void onAddCallControl(@NonNull CallControl callControl) {
            mCallControl = callControl;
            mTelecomCallId = callControl.getCallId().toString();
        }

        @Override
        public void onSetActive(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onSetActive: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onSetInactive(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onSetInactive: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onAnswer(int videoState, @NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onAnswer: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onReject(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onReject: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onDisconnect(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onDisconnect: callId=[%s]", mCallId));
            wasCompleted.accept(Boolean.TRUE);
        }

        @Override
        public void onCallAudioStateChanged(@NonNull CallAudioState callAudioState) {
            Log.i(TAG, String.format("onCallAudioStateChanged: callId=[%s], state=[%s]",
                    mCallId, callAudioState.toString()));
        }

        @Override
        public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
            Log.i(TAG, String.format("onCallStreamingStarted: callId=[%s]", mCallId));
        }

        @Override
        public void onCallStreamingFailed(int reason) {
            Log.i(TAG, String.format("onCallStreamingFailed: callId=[%s], reason=[%s]", mCallId,
                    reason));
        }
    }

    public class LatchedOutcomeReceiver implements OutcomeReceiver<Void, CallException> {
        CountDownLatch mCountDownLatch;

        public LatchedOutcomeReceiver(CountDownLatch latch) {
            mCountDownLatch = latch;
        }

        @Override
        public void onResult(Void result) {
            Log.i(TAG, "latch is counting down");
            mCountDownLatch.countDown();
        }

        @Override
        public void onError(@NonNull CallException error) {
            Log.i(TAG, String.format("onError: code=[%d]", error.getCode()));
            OutcomeReceiver.super.onError(error);
        }
    }

    // Call constants
    public final PhoneAccountHandle HANDLE = TestUtils.TEST_SELF_MANAGED_HANDLE_1;

    public final PhoneAccount ACCOUNT =
            PhoneAccount.builder(HANDLE, TestUtils.ACCOUNT_LABEL)
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                    ).build();

    CallAttributes mOutgoingCallAttributes = new CallAttributes.Builder(HANDLE, DIRECTION_OUTGOING,
            TEST_NAME_1, TEST_URI_1)
            .setCallType(CallAttributes.AUDIO_CALL)
            .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
            .build();

    CallAttributes mIncomingCallAttributes = new CallAttributes.Builder(HANDLE, DIRECTION_INCOMING,
            TEST_NAME_2, TEST_URI_2)
            .setCallType(CallAttributes.AUDIO_CALL)
            .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
            .build();

    private static final String OUTGOING_CALL_ID = "1";
    private static final String INCOMING_CALL_ID = "2";

    private final TestVoipCall mCall1 = new TestVoipCall(OUTGOING_CALL_ID);
    private final TestVoipCall mCall2 = new TestVoipCall(INCOMING_CALL_ID);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) return;
        NewOutgoingCallBroadcastReceiver.reset();
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        mTelecomManager.registerPhoneAccount(ACCOUNT);
        cleanup();
    }

    @Override
    public void tearDown() throws Exception {
        cleanup();
        super.tearDown();
    }

    public void testCallAttributesHelpers() {
        if (!mShouldTestTelecom) {
            return;
        }
        assertFalse(mOutgoingCallAttributes.equals(mIncomingCallAttributes));
    }

    /**
     * Ensure early failure for TelecomManager#addCall whenever a null argument is passed in.
     */
    public void testAddCallWithNullArgument() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            mTelecomManager.addCall(null, Runnable::run, new OutcomeReceiver<>() {
                @Override
                public void onResult(CallControl result) {
                    fail("test should never execute onResult");
                }
            }, mCall1);
            fail("test should have thrown an exception already");
        } catch (Exception e) {
            // test passes
        }
    }

    /**
     * Ensure the state transitions of a successful outgoing call are correct.
     * State Transitions:  New -> * Connecting * -> Active -> Disconnecting -> Disconnected
     */
    public void testAddOutgoingCall() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertNumCalls(getInCallService(), 1);
            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful incoming call are correct.
     * State Transitions:  New -> * Ringing * -> Active -> Disconnecting -> Disconnected
     */
    public void testAddIncomingCall() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertNumCalls(getInCallService(), 1);
            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful incoming call that was rejected
     */
    public void testAddIncomingCallAndReject() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            callControlAction(REJECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful outgoing call are correct.
     * State Transitions:  New -> Connecting  -> Active -> Inactive ->
     *                                                          Disconnecting -> Disconnected
     */
    public void testAddOutgoingCallAndSetInactive() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            // set the call active
            callControlAction(SET_ACTIVE, mCall1);
            assertNumCalls(getInCallService(), 1);
            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());
            // hold call
            callControlAction(SET_INACTIVE, mCall1);
            assertEquals(Call.STATE_HOLDING, getLastAddedCall().getState());
            // disconnect
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallEventCallback#onReject} is being called and destroying the call.
     */
    public void testAddIncomingCallAndRejectWithCallEventCallback() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            Call call = getLastAddedCall();
            call.reject(Call.REJECT_REASON_DECLINED);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallEventCallback#onAnswer(int, OutcomeReceiver)} is being called
     * and setting the call to active.
     */
    public void testAddIncomingCallOnAnswer() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            Call call = getLastAddedCall();
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
            waitUntilConditionIsTrueOrTimeout(new Condition() {
                @Override
                public Object expected() {
                    return Call.STATE_ACTIVE;
                }

                @Override
                public Object actual() {
                    return getCallWithId(mCall1.getTelecomCallId()).getState();
                }
            }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "call not set to active");
            call.disconnect();
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }


    /**
     * Test two transactional sequential calls transition to the correct states.
     */
    public void testCallStatesForTwoLiveTransactionalCalls() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();

            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(SET_ACTIVE, mCall1);
            assertNumCalls(getInCallService(), 1);

            assertEquals(Call.STATE_ACTIVE, getLastAddedCall().getState());

            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall2);
            callControlAction(SET_ACTIVE, mCall2);
            assertNumCalls(getInCallService(), 2);

            Call call1 = getCallWithId(mCall1.getTelecomCallId());
            Call call2 = getCallWithId(mCall2.getTelecomCallId());

            assertEquals(Call.STATE_HOLDING, call1.getState());
            assertEquals(Call.STATE_ACTIVE, call2.getState());

            callControlAction(DISCONNECT, mCall2);
            assertNumCalls(getInCallService(), 1);

            assertEquals(Call.STATE_HOLDING, call1.getState());

            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Test 1 sim call and 1 transactional call
     */
    public void testSimCallAndTransactionalCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        try {
            cleanup();

            registerAndEnableSimPhoneAccount();
            MockConnection simConnection = placeSimCallAndSetActive();
            assertNumCalls(getInCallService(), 1);


            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall2);
            callControlAction(SET_ACTIVE, mCall2);
            assertNumCalls(getInCallService(), 2);


            assertConnectionState(simConnection, Connection.STATE_HOLDING);
            assertEquals(Call.STATE_ACTIVE, getCallWithId(mCall2.getTelecomCallId()).getState());

            callControlAction(DISCONNECT, mCall2);
            assertNumCalls(getInCallService(), 1);

            assertConnectionState(simConnection, Connection.STATE_HOLDING);

            simConnection.onDisconnect();
            assertNumCalls(getInCallService(), 0);

        } finally {
            runWithShellPermissionIdentity(() -> {
                mTelecomManager.unregisterPhoneAccount(
                        TestUtils.TEST_SIM_PHONE_ACCOUNT.getAccountHandle());
            });
            cleanup();
        }
    }

    // waits for the latch to countdown or times out and fails
    public void assertOnResultWasReceived(CountDownLatch latch) {
        Log.i(TAG, "assertOnResultWasReceived: waiting for latch");
        try {
            boolean success = latch.await(5000, TimeUnit.MILLISECONDS);
            if (!success) {
                fail(FAIL_MSG_OUTCOME_RECEIVER);
            }

        } catch (InterruptedException ie) {
            fail(FAIL_MSG_OUTCOME_RECEIVER);
        }
    }

    public String startCallWithAttributesAndVerify(CallAttributes attributes, TestVoipCall call) {
        final CountDownLatch latch = new CountDownLatch(1);

        mTelecomManager.addCall(attributes, Runnable::run, new OutcomeReceiver<>() {
            @Override
            public void onResult(CallControl callControl) {
                Log.i(TAG, "onResult: adding callControl to callObject");

                if (callControl == null) {
                    fail(FAIL_MSG_CALL_CONTROL_NULL);
                }

                call.onAddCallControl(callControl);
                latch.countDown();
            }

            @Override
            public void onError(CallException exception) {
                Log.i(TAG, "testRegisterApp: onError");
            }
        }, call);

        assertOnResultWasReceived(latch);

        return call.mCallControl.getCallId().toString();
    }


    public void callControlAction(String action, TestVoipCall call) {
        final CountDownLatch latch = new CountDownLatch(1);
        final LatchedOutcomeReceiver outcome = new LatchedOutcomeReceiver(latch);

        CallControl callControl = call.mCallControl;
        if (callControl == null) {
            fail("callControl object is null");
            return;
        }

        switch (action) {
            case SET_ACTIVE:
                call.mCallControl.setActive(Runnable::run, outcome);
                break;
            case SET_INACTIVE:
                call.mCallControl.setInactive(Runnable::run, outcome);
                break;
            case DISCONNECT:
                call.mCallControl.disconnect(new DisconnectCause(DisconnectCause.LOCAL),
                        Runnable::run, outcome);
                break;
            case REJECT:
                call.mCallControl.rejectCall(Runnable::run, outcome);
                break;
            default:
                fail("should never reach the default case");
        }

        assertOnResultWasReceived(latch);
    }

    @NonNull
    private Call getLastAddedCall() {
        waitOnInCallService();
        Call lastCall = getInCallService().getLastCall();
        assertNotNull(lastCall);
        return lastCall;
    }

    @NonNull
    private Call getCallWithId(String id) {
        waitOnInCallService();
        Call call = getInCallService().getCallWithId(id);
        assertNotNull(call);
        return call;
    }

    private void registerAndEnableSimPhoneAccount() throws Exception {
        runWithShellPermissionIdentity(() -> {
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SIM_PHONE_ACCOUNT);
        });

        TestUtils.enablePhoneAccount(
                getInstrumentation(), TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);
    }

    private MockConnection placeSimCallAndSetActive() {
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);
        placeAndVerifyCall();
        MockConnection conn = verifyConnectionForOutgoingCall();
        conn.setActive();
        assertConnectionState(conn, Connection.STATE_ACTIVE);
        return conn;
    }

    private void cleanup() {
        try {
            if (mInCallCallbacks.getService() != null) {
                mInCallCallbacks.getService().disconnectAllCalls();
                mInCallCallbacks.getService().clearCallList();
            }
            TestUtils.executeShellCommand(getInstrumentation(), TEL_CLEAN_STUCK_CALLS_CMD);
        } catch (Exception e) {
            Log.i(TAG, FAIL_MSG_DURING_CLEANUP);
        }
    }
}
