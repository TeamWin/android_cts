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

import static android.telecom.CallAttributes.AUDIO_CALL;
import static android.telecom.CallAttributes.DIRECTION_INCOMING;
import static android.telecom.CallAttributes.DIRECTION_OUTGOING;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
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

import com.android.compatibility.common.util.ApiTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class tests calls added with the API
 * {@link TelecomManager#addCall(CallAttributes,
 * Executor,
 * OutcomeReceiver,
 * CallControlCallback,
 * CallEventCallback)
 * }
 * and controlled by {@link CallControl} or {@link CallControlCallback}.
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
    private static final String ANSWER = "Answer";
    private static final String SET_INACTIVE = "SetInactive";
    private static final String DISCONNECT = "Disconnect";

    // Fail messages
    private static final String FAIL_MSG_CALL_CONTROL_NULL =
            "onResult: callControl object is null";
    private static final String FAIL_MSG_OUTCOME_RECEIVER =
            "failed to receive OutcomeReceiver#onResult";
    private static final String FAIL_MSG_DURING_CLEANUP =
            "exception thrown while cleaning up tests";
    private static final String FAIL_MSG_ON_CALL_ENDPOINT_UPDATE =
            "CallEndpoint was not updated at all or in time";
    private static final String FAIL_MSG_ON_AVAILABLE_ENDPOINTS_UPDATE =
            "onAvailable CallEndpoint was not updated at all or in time";
    private static final String FAIL_MSG_ON_MUTE_STATE_CHANGED =
            "Mute state was not updated at all or in time";

    // inner classes

    /**
     * simulates a VoIP app construct of a Call object that accepts every
     * {@link CallControlCallback}
     */
    public class TelecomCtsVoipCall {
        private static final String TAG = "TelecomCtsVoipCall";
        private final String mCallId;
        private String mTelecomCallId = "";
        CallControl mCallControl;
        public boolean mWasOnDisconnectCalled = false;

        TelecomCtsVoipCall(String id) {
            mCallId = id;
            mEvents.mCallId = id;
        }

        public String getTelecomCallId() {
            return mTelecomCallId;
        }

        public void onAddCallControl(@NonNull CallControl callControl) {
            mCallControl = callControl;
            mTelecomCallId = callControl.getCallId().toString();
        }

        public android.telecom.cts.TelecomCtsVoipCall.CallEvent mEvents =
                new android.telecom.cts.TelecomCtsVoipCall.CallEvent();

        public CallControlCallback mHandshakes = new CallControlCallback() {
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
            public void onDisconnect(@NonNull DisconnectCause cause,
                    @NonNull Consumer<Boolean> wasCompleted) {
                Log.i(TAG, String.format("onDisconnect: callId=[%s]", mCallId));
                wasCompleted.accept(Boolean.TRUE);
                mWasOnDisconnectCalled = true;
            }

            @Override
            public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
                Log.i(TAG, String.format("onCallStreamingStarted: callId=[%s]", mCallId));
            }
        };

        public void resetAllCallbackVerifiers() {
            mWasOnDisconnectCalled = false;
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

    private final TelecomCtsVoipCall mCall1 = new TelecomCtsVoipCall(OUTGOING_CALL_ID);
    private final TelecomCtsVoipCall mCall2 = new TelecomCtsVoipCall(INCOMING_CALL_ID);

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

    public void testCallAttributesGetters() {
        if (!mShouldTestTelecom) {
            return;
        }
        // outgoing call
        assertEquals(HANDLE, mOutgoingCallAttributes.getPhoneAccountHandle());
        assertEquals(AUDIO_CALL, mOutgoingCallAttributes.getCallType());
        assertEquals(CallAttributes.SUPPORTS_SET_INACTIVE,
                mOutgoingCallAttributes.getCallCapabilities());
        assertEquals(TEST_NAME_1, mOutgoingCallAttributes.getDisplayName());
        assertEquals(DIRECTION_OUTGOING, mOutgoingCallAttributes.getDirection());
        assertEquals(TEST_URI_1, mOutgoingCallAttributes.getAddress());

        // incoming call
        assertEquals(HANDLE, mIncomingCallAttributes.getPhoneAccountHandle());
        assertEquals(AUDIO_CALL, mIncomingCallAttributes.getCallType());
        assertEquals(CallAttributes.SUPPORTS_SET_INACTIVE,
                mIncomingCallAttributes.getCallCapabilities());
        assertEquals(TEST_NAME_2, mIncomingCallAttributes.getDisplayName());
        assertEquals(DIRECTION_INCOMING, mIncomingCallAttributes.getDirection());
        assertEquals(TEST_URI_2, mIncomingCallAttributes.getAddress());
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
            }, mCall1.mHandshakes, mCall1.mEvents);
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
     * State Transitions:  New -> * Ringing -> Active * -> Disconnecting -> Disconnected
     */
    public void testAddIncomingCallAndSetActive() {
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
     * Ensure the state transitions of a successful incoming call are correct.
     * State Transitions:  New -> * Ringing -> Answered* -> Disconnecting -> Disconnected
     */
    public void testAddIncomingCallAndAnswer() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            callControlAction(ANSWER, mCall1, AUDIO_CALL);
            assertNumCalls(getInCallService(), 1);
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful incoming call are correct.
     * State Transitions:  Created -> Ringing -> Disconnected -> Destroyed
     */
    public void testRejectIncomingCall() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            assertEquals(Call.STATE_RINGING, getLastAddedCall().getState());
            try {
                callControlAction(DISCONNECT, mCall1, DisconnectCause.ERROR);
                fail("testRejectIncomingCall: forced fail b/c IllegalArgumentException not thrown");
            } catch (IllegalArgumentException e) {
                assertNotNull(e);
            }
            callControlAction(DISCONNECT, mCall1, DisconnectCause.REJECTED);
            assertNumCalls(getInCallService(), 0);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure the state transitions of a successful outgoing call are correct.
     * State Transitions:  New -> Connecting  -> Active -> Inactive ->
     * Disconnecting -> Disconnected
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
     * Calls that do not have the {@link CallAttributes#SUPPORTS_SET_INACTIVE} and call
     * {@link CallControl#setInactive(Executor, OutcomeReceiver)} should always result in an
     * OutcomeReceiver#onError with CallException#CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL
     */
    @ApiTest(apis = {"android.telecom.CallException(java.lang.String, int)"})
    public void testCallDoesNotSupportHoldResultsInOnError() {
        if (!mShouldTestTelecom) {
            return;
        }
        final CallAttributes cannotSetInactiveAttributes =
                new CallAttributes.Builder(HANDLE, DIRECTION_OUTGOING,
                        TEST_NAME_1, TEST_URI_1)
                        .setCallCapabilities(CallAttributes.SUPPORTS_STREAM)
                        .build();

        assertEquals(CallAttributes.SUPPORTS_STREAM,
                cannotSetInactiveAttributes.getCallCapabilities());

        CallException cannotSetInactiveException = new CallException("call does not support hold",
                CallException.CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL);
        try {
            cleanup();
            startCallWithAttributesAndVerify(cannotSetInactiveAttributes, mCall1);
            mCall1.mCallControl.setInactive(Runnable::run, new OutcomeReceiver<>() {
                @Override
                public void onResult(Void result) {
                    fail("testCannotSetInactiveExpectFail:"
                            + " onResult should not be called");
                }

                @Override
                public void onError(CallException exception) {
                    assertEquals(cannotSetInactiveException.getCode(),
                            exception.getCode());
                }
            });
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Calling any {@link CallControl} API after calling
     * {@link CallControl#disconnect(DisconnectCause, Executor, OutcomeReceiver)} will always
     * result in OutcomeReceiver#onError.
     */
    public void testUsingCallControlAfterDisconnect() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            callControlAction(DISCONNECT, mCall1);
            assertNumCalls(getInCallService(), 0);

            mCall1.mCallControl.setActive(Runnable::run, new OutcomeReceiver<>() {
                @Override
                public void onResult(Void result) {
                    fail("testUsingCallControlAfterDisconnect:"
                            + " onResult should not be called");
                }

                @Override
                public void onError(CallException exception) {
                }
            });
        } finally {
            cleanup();
        }
    }


    /**
     * Ensure {@link CallControlCallback#onDisconnect(DisconnectCause, Consumer)}
     * is being called and destroying the call.
     */
    public void testAddIncomingCallAndRejectWithCallEventCallback() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            mCall1.resetAllCallbackVerifiers();
            assertFalse(mCall1.mWasOnDisconnectCalled);
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            Call call = getLastAddedCall();
            call.reject(Call.REJECT_REASON_DECLINED);
            assertNumCalls(getInCallService(), 0);
            assertTrue(mCall1.mWasOnDisconnectCalled);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallControlCallback#onAnswer(int, Consumer)} is being called
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
            // conditionally register
            registerSimAccountIfNeeded();
            // start a sim call and set it active
            MockConnection simConnection = placeSimCallAndSetActive();
            // start a Transactional SM call
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall2);
            // set the Transactional call to active
            callControlAction(SET_ACTIVE, mCall2);
            // check call states
            assertConnectionState(simConnection, Connection.STATE_HOLDING);
            assertCallState(getCallWithId(mCall2.getTelecomCallId()), Call.STATE_ACTIVE);
            // disconnect the Transactional call
            callControlAction(DISCONNECT, mCall2);
            // check the call states
            assertConnectionState(simConnection, Connection.STATE_HOLDING);
            // disconnect the sim call
            simConnection.onDisconnect();
        } finally {
            runWithShellPermissionIdentity(() -> {
                mTelecomManager.unregisterPhoneAccount(
                        TestUtils.TEST_SIM_PHONE_ACCOUNT.getAccountHandle());
            });
            cleanup();
        }
    }

    private void registerSimAccountIfNeeded() {
        // if the account is not present
        if (mTelecomManager.getPhoneAccount(TestUtils.TEST_SIM_PHONE_ACCOUNT.getAccountHandle())
                == null) {
            // register
            runWithShellPermissionIdentity(() -> {
                mTelecomManager.registerPhoneAccount(TestUtils.TEST_SIM_PHONE_ACCOUNT);
            });
        }
    }

    /**
     * test {@link CallEventCallback#onCallEndpointChanged(CallEndpoint)} is called and provides a
     * non-null {@link CallEndpoint}.
     */
    public void testOnChangedCallEndpoint() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            assertNull(mCall1.mEvents.getCurrentCallEndpoint());
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            verifyCallEndpointIsNotNull(mCall1);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * test {@link CallEventCallback#onAvailableCallEndpointsChanged(List)} is called and provides a
     * list of non-null {@link CallEndpoint}s.
     */
    public void testOnAvailableCallEndpointsChanged() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            assertNull(mCall1.mEvents.getAvailableEndpoints());
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            verifyOnAvailableEndpointsIsNotNull(mCall1);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * test {@link CallEventCallback#onMuteStateChanged(boolean)} is called properly relays the
     * changes to the audio mute state.
     */
    public void testMuteState() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            mCall1.resetAllCallbackVerifiers();
            assertFalse(mCall1.mEvents.mWasMuteStateChangedCalled);
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            verifyMuteStateCallbackWasCalled(true, mCall1);
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallControl#sendEvent(String, Bundle)} does not throw an exception when given
     * an event without a Bundle value.
     */
    public void testSendCallEvent() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            mCall1.mCallControl.sendEvent(Connection.EVENT_CALL_HOLD_FAILED, new Bundle());
            callControlAction(DISCONNECT, mCall1);
        } finally {
            cleanup();
        }
    }

    /**
     * Ensure {@link CallEventCallback#onEvent(String, Bundle)} is called when an InCallService
     * creates a new event.
     */
    public void testOnCallEvent() {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            cleanup();
            assertFalse(mCall1.mEvents.mWasOnEventCalled);
            startCallWithAttributesAndVerify(mOutgoingCallAttributes, mCall1);
            assertNumCalls(getInCallService(), 1);
            // simulate an InCallService sending a call event
            getLastAddedCall().sendCallEvent(Connection.EVENT_CALL_HOLD_FAILED, new Bundle());
            // wait for the onEvent to be called
            waitUntilConditionIsTrueOrTimeout(
                    new Condition() {
                        @Override
                        public Object expected() {
                            return true;
                        }

                        @Override
                        public Object actual() {
                            return mCall1.mEvents.mWasOnEventCalled;
                        }
                    },
                    WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, "onEvent was never called");

        } finally {
            cleanup();
        }
    }

    /**
     * test {@link CallControl#requestCallEndpointChange(CallEndpoint, Executor, OutcomeReceiver)}
     * can switch {@link CallEndpoint}s if there is another endpoint available.  This test will not
     * request an endpoint change if the device only has a single endpoint.
     */
    public void testRequestCallEndpointChangeViaCallControl() {
        if (!mShouldTestTelecom) {
            return;
        }

        try {
            cleanup();
            startCallWithAttributesAndVerify(mIncomingCallAttributes, mCall1);

            // wait on handlers
            TestUtils.waitOnAllHandlers(getInstrumentation());

            // query the current endpoints
            List<CallEndpoint> endpoints = mCall1.mEvents.getAvailableEndpoints();

            // if another endpoint is available, request a switch
            if ( endpoints != null && endpoints.size() > 1) {
                // verify there is at least one endpoint that is non-null
                verifyCallEndpointIsNotNull(mCall1);
                int startingEndpointType = mCall1.mEvents
                        .getCurrentCallEndpoint().getEndpointType();

                // iterate the other endpoints until an endpoint other than the startingEndpointType
                // is found
                CallEndpoint anotherEndpoint = null;
                for (CallEndpoint endpoint : endpoints) {
                    if (endpoint != null
                            && endpoint.getEndpointType() != startingEndpointType) {
                        anotherEndpoint = endpoint;
                        break;
                    }
                }
                // CallControl#requestCallEndpointChange
                requestAndAssertEndpointChange(mCall1, anotherEndpoint);
                assertNotNull(anotherEndpoint);
                assertEndpointType(getInCallService(), anotherEndpoint.getEndpointType());
                // disconnect the call
                callControlAction(DISCONNECT, mCall1);
            }
        } finally {
            cleanup();
        }
    }

    public void verifyCallEndpointIsNotNull(TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.getCurrentCallEndpoint() != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, FAIL_MSG_ON_CALL_ENDPOINT_UPDATE);
    }

    public void verifyOnAvailableEndpointsIsNotNull(TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.getAvailableEndpoints() != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, FAIL_MSG_ON_AVAILABLE_ENDPOINTS_UPDATE);
    }

    public void verifyMuteStateCallbackWasCalled(boolean expected, TelecomCtsVoipCall call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return call.mEvents.mWasMuteStateChangedCalled;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, FAIL_MSG_ON_MUTE_STATE_CHANGED);
    }

    public void requestAndAssertEndpointChange(TelecomCtsVoipCall call, CallEndpoint endpoint) {
        final CountDownLatch latch = new CountDownLatch(1);
        final android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver outcome =
                new android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver(latch);

        CallControl callControl = call.mCallControl;
        if (callControl == null) {
            fail("callControl object is null");
            return;
        }

        call.mCallControl.requestCallEndpointChange(endpoint, Runnable::run, outcome);

        assertOnResultWasReceived(latch);
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

    public String startCallWithAttributesAndVerify(CallAttributes attributes,
            TelecomCtsVoipCall call) {
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
        }, call.mHandshakes, call.mEvents);

        assertOnResultWasReceived(latch);

        return call.mCallControl.getCallId().toString();
    }

    public void callControlAction(String action, TelecomCtsVoipCall call, Object... objects) {
        final CountDownLatch latch = new CountDownLatch(1);
        final android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver outcome =
                new android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver(latch);
        DisconnectCause disconnectCause = new DisconnectCause(DisconnectCause.LOCAL);

        CallControl callControl = call.mCallControl;
        if (callControl == null) {
            fail("callControl object is null");
            return;
        }

        if (isArgumentAvailable(objects)) {
            disconnectCause = new DisconnectCause((int) objects[0]);
        }

        switch (action) {
            case SET_ACTIVE:
                call.mCallControl.setActive(Runnable::run, outcome);
                break;
            case ANSWER:
                int videoState = AUDIO_CALL;
                if (isArgumentAvailable(objects)) {
                    videoState = (int) objects[0];
                }
                call.mCallControl.answer(videoState, Runnable::run, outcome);
                break;
            case SET_INACTIVE:
                call.mCallControl.setInactive(Runnable::run, outcome);
                break;
            case DISCONNECT:
                call.mCallControl.disconnect(disconnectCause, Runnable::run, outcome);
                break;
            default:
                fail("should never reach the default case");
        }

        assertOnResultWasReceived(latch);
    }

    private boolean isArgumentAvailable(Object... objects) {
        return objects != null && objects.length >= 1;
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

    private MockConnection placeSimCallAndSetActive() {
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_SIM_PHONE_ACCOUNT_HANDLE);
        placeAndVerifyCall(extras);
        MockConnection conn = verifyConnectionForOutgoingCall();
        conn.setActive();
        assertConnectionState(conn, Connection.STATE_ACTIVE);
        return conn;
    }

    private void cleanup() {
        Log.i(TAG, "cleanup: method running");
        try {
            if (mInCallCallbacks.getService() != null) {
                mInCallCallbacks.getService().disconnectAllCalls();
                mInCallCallbacks.getService().clearCallList();
            }
            TestUtils.executeShellCommand(getInstrumentation(), TEL_CLEAN_STUCK_CALLS_CMD);
            mCall1.resetAllCallbackVerifiers();
            mCall2.resetAllCallbackVerifiers();
        } catch (Exception e) {
            Log.i(TAG, FAIL_MSG_DURING_CLEANUP);
        }
    }
}
