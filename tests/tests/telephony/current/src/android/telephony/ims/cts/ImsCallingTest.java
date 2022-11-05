/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.telephony.ims.cts;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * CTS tests for ImsCall .
 */
@RunWith(AndroidJUnit4.class)
public class ImsCallingTest extends ImsCallingBase {

    protected static final String LOG_TAG = "CtsImsCallingTest";

    private Call mCall1 = null;
    private Call mCall2 = null;
    private TestImsCallSessionImpl mCallSession1 = null;
    private TestImsCallSessionImpl mCallSession2 = null;

    static {
        initializeLatches();
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            return;
        }

        beforeAllTestsBase();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        afterAllTestsBase();
    }

    @Before
    public void beforeTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TelephonyManager tm = (TelephonyManager) InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            fail("This test requires that there is a SIM in the device!");
        }
        // Correctness check: ensure that the subscription hasn't changed between tests.
        int[] subs = SubscriptionManager.getSubId(sTestSlot);

        if (subs == null) {
            fail("This test requires there is an active subscription in slot " + sTestSlot);
        }
        boolean isFound = false;
        for (int sub : subs) {
            isFound |= (sTestSub == sub);
        }
        if (!isFound) {
            fail("Invalid state found: the test subscription in slot " + sTestSlot + " changed "
                    + "during this test.");
        }
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
        }

        //Set the untracked CountDownLatches which are reseted in ServiceCallBack
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }

        if (sServiceConnector != null && sIsBound) {
            TestImsService imsService = sServiceConnector.getCarrierService();
            sServiceConnector.disconnectCarrierImsService();
            sIsBound = false;
            imsService.waitForExecutorFinish();
        }
    }

    @Test
    public void testOutGoingCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallStartFailed() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        TestMmTelFeature mmtelfeatue = sServiceConnector.getCarrierService()
                                .getMmTelFeature();
                        return (mmtelfeatue.isCallSessionCreated()) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "CallSession Created");

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();
        assertNotNull("Unable to get callSession, its null", callSession);
        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_FAILED);

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testIncomingCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        Bundle extras = new Bundle();
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        if (call.getDetails().getState() == Call.STATE_RINGING) {
            callingTestLatchCountdown(LATCH_WAIT, 5000);
            call.answer(0);
        }

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        callSession.terminateIncomingCall();

        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallForExecutor() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sServiceConnector.setExecutorTestType(true);
        bindImsService();

        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallHoldResume() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        // Put on hold
        call.hold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        // Put on resume
        call.unhold();
        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallHoldFailure() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);
        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_HOLD_FAILED);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        call.hold();
        assertTrue("call is not in Active State", (call.getDetails().getState()
                == call.STATE_ACTIVE));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }


    @Test
    public void testOutGoingCallResumeFailure() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        // Put on hold
        call.hold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));

        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        call.unhold();
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        assertTrue("Call is not in Hold State", (call.getDetails().getState()
                == call.STATE_HOLDING));


        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingIncomingMultiCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl moCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID",  String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
                mtCall.answer(0);
            }
        }

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        TestImsCallSessionImpl mtCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        isCallActive(mtCall, mtCallSession);
        assertTrue("Call is not in Active State", (mtCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        mtCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        moCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(moCall, moCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        waitForUnboundService();
    }

    @Test
    public void testOutGoingIncomingMultiCallAcceptTerminate() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl moCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID",  String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        TestImsCallSessionImpl mtCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        // do not generate an auto hold response here, need to simulate a timing issue.
        moCallSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_HOLD_NO_RESPONSE);

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
                mtCall.answer(0);
            }
        }

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
        // simulate user hanging up the MT call at the same time as accept.
        mtCallSession.terminateIncomingCall();
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // then send hold response, which should be reversed, since MT call was disconnected.
        moCallSession.sendHoldResponse();

        // MO call should move back to active.
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        moCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(moCall, moCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallSwap() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();

        // Swap the call
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        mCall1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall2.getDetails().getState()
                == Call.STATE_HOLDING));
        isCallActive(mCall1, mCallSession1);

        // After successful call swap disconnect the call
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        //Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mCall2.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true : false;
                    }
                }, WAIT_FOR_CALL_STATE_ACTIVE, "Call in Active State");

        isCallActive(mCall2, mCallSession2);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        resetCallSessionObjects();
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallSwapFail() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();

        mCallSession1.addTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        // Swap the call
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        mCall1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        // Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mCall2.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true : false;
                    }
                }, WAIT_FOR_CALL_STATE_ACTIVE, "Call in Active State");

        isCallActive(mCall2, mCallSession2);
        mCallSession1.removeTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // Wait till second call is in active state
        isCallActive(mCall1, mCallSession1);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        resetCallSessionObjects();
        waitForUnboundService();
    }

    @Test
    public void testConferenceCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        Log.i(LOG_TAG, "testConference ");
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();
        addConferenceCall(mCall1, mCall2);

        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        assertTrue("Conference call is not added", mServiceCallBack.getService()
                .getConferenceCallCount() > 0);

        Call conferenceCall = mServiceCallBack.getService().getLastConferenceCall();
        assertNotNull("Unable to add conference call, its null", conferenceCall);

        ConferenceHelper confHelper = sServiceConnector.getCarrierService().getMmTelFeature()
                .getConferenceHelper();

        TestImsCallSessionImpl confcallSession = confHelper.getConferenceSession();
        assertTrue("Conference call is not Active", confcallSession.isInCall());

        //Verify mCall1 and mCall2 disconnected after conference Merge success
        assertParticiapantDisconnected(mCall1);
        assertParticiapantDisconnected(mCall2);

        //Verify conference participant connections are connected.
        assertParticiapantAddedToConference(2);

        //Disconnect the conference call.
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        conferenceCall.disconnect();

        //Verify conference participant connections are disconnected.
        assertParticiapantAddedToConference(0);
        isCallDisconnected(conferenceCall, confcallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        resetCallSessionObjects();
        waitForUnboundService();
    }

    @Test
    public void testConferenceCallFailure() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        Log.i(LOG_TAG, "testConferenceCallFailure ");
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();
        mCallSession2.addTestType(TestImsCallSessionImpl.TEST_TYPE_CONFERENCE_FAILED);
        addConferenceCall(mCall1, mCall2);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
        //Verify foreground call is in Active state after merge failed.
        assertTrue("Call is not in Active State", (mCall2.getDetails().getState()
                == Call.STATE_ACTIVE));
        //Verify background call is in Hold state after merge failed.
        assertTrue("Call is not in Holding State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        //Wait till background call is in active state
        isCallActive(mCall1, mCallSession1);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        resetCallSessionObjects();
        waitForUnboundService();
    }

    void addConferenceCall(Call call1, Call call2) {
        InCallServiceStateValidator inCallService = mServiceCallBack.getService();
        int currentConfCallCount = 0;
        if (inCallService != null) {
            currentConfCallCount = inCallService.getConferenceCallCount();
        }

        // Verify that the calls have each other on their conferenceable list before proceeding
        List<Call> callConfList = new ArrayList<>();
        callConfList.add(call2);
        assertCallConferenceableList(call1, callConfList);

        callConfList.clear();
        callConfList.add(call1);
        assertCallConferenceableList(call2, callConfList);

        call2.conference(call1);
    }

    void assertCallConferenceableList(final Call call, final List<Call> conferenceableList) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return conferenceableList;
                    }

                    @Override
                    public Object actual() {
                        return call.getConferenceableCalls();
                    }
                }, WAIT_FOR_CONDITION,
                        "Call: " + call + " does not have the correct conferenceable call list."
        );
    }

    private void assertParticiapantDisconnected(Call call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return ((call.getState() == Call.STATE_DISCONNECTED)) ? true : false;
                    }
                }, WAIT_FOR_CALL_DISCONNECT, "Call Disconnected");
    }

    private void assertParticiapantAddedToConference(int count) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mParticipantCount == count) ? true : false;
                    }
                }, WAIT_FOR_CALL_CONNECT, "Call Added");
    }

    private void addOutgoingCalls() throws Exception {
        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        // Place first outgoing call
        final Uri imsUri1 = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter),
                null);
        Bundle extras1 = new Bundle();

        telecomManager.placeCall(imsUri1, extras1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        mCall1 = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        mCallSession1 = sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(mCall1, mCallSession1);
        assertTrue("Call is not in Active State", (mCall1.getDetails().getState()
                == Call.STATE_ACTIVE));

        // Place second outgoing call
        final Uri imsUri2 = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter),
                null);
        Bundle extras2 = new Bundle();

        telecomManager.placeCall(imsUri2, extras2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        mCall2 = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        //Wait till the object of TestImsCallSessionImpl for second call created.
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        TestMmTelFeature mmtelfeatue = sServiceConnector.getCarrierService()
                                .getMmTelFeature();
                        return (mmtelfeatue.getImsCallsession() != mCallSession1) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "CallSession Created");

        mCallSession2 = sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(mCall2, mCallSession2);
        assertTrue("Call is not in Active State", (mCall2.getDetails().getState()
                == Call.STATE_ACTIVE));
    }

    private void resetCallSessionObjects() {
        mCall1 = mCall2 = null;
        mCallSession1 = mCallSession2 = null;
        ConferenceHelper confHelper = sServiceConnector.getCarrierService().getMmTelFeature()
                .getConferenceHelper();
        if (confHelper != null) {
            confHelper.clearSessions();
        }
    }
}
