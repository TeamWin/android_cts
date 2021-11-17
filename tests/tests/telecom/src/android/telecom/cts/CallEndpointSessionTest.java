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

package android.telecom.cts;

import static android.telecom.CallEndpointSession.ACTIVATION_FAILURE_REJECTED;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.telecom.Call;
import android.telecom.CallEndpoint;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.streamingtestapp.CallStreamingService;
import android.telecom.cts.streamingtestapp.CallStreamingServiceControl;
import android.telecom.cts.streamingtestapp.ICallStreamingServiceControl;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CallEndpointSessionTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = CallEndpointSessionTest.class.getSimpleName();
    private static final CallEndpoint CALL_ENDPOINT = new CallEndpoint(ParcelUuid.fromString(
            UUID.randomUUID().toString()), "cts streaming app",
            CallEndpoint.ENDPOINT_TYPE_TETHERED,
            new ComponentName(CallStreamingService.class.getPackageName(),
                    CallStreamingService.class.getName()));
    private static final Set<CallEndpoint> CALL_ENDPOINT_SET;
    static {
        CALL_ENDPOINT_SET = new HashSet<>();
        CALL_ENDPOINT_SET.add(CALL_ENDPOINT);
    }


    ICallStreamingServiceControl mICallStreamingServiceControl;
    CtsRoleManagerAdapter mCtsRoleManagerAdapter;
    UiAutomation mUiAutomation;

    @Override
    public void setUp() throws Exception {
        if (mShouldTestTelecom) {
            super.setUp();
            mContext = getInstrumentation().getContext();
            mUiAutomation = getInstrumentation().getUiAutomation();
            mCtsRoleManagerAdapter = new CtsRoleManagerAdapter(getInstrumentation());

            // TODO: set streaming call role
            // Do not continue with tests if the Dialer role is not available.

            mUiAutomation.adoptShellPermissionIdentity(
                    "android.permission.CONTROL_INCALL_EXPERIENCE");
            setUpControl();
            mICallStreamingServiceControl.reset();
            NewOutgoingCallBroadcastReceiver.reset();
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            mTelecomManager.registerCallEndpoints(CALL_ENDPOINT_SET);
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            mTelecomManager.unregisterCallEndpoints(CALL_ENDPOINT_SET);
            mUiAutomation.dropShellPermissionIdentity();
            super.tearDown();
        }
    }

    public void testPlaceCallOnEndpoint() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        final Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_START_CALL_ON_ENDPOINT, CALL_ENDPOINT);
        placeAndVerifyCall(extras);
        assertTrue(mICallStreamingServiceControl.waitForBound());
        assertTrue(mICallStreamingServiceControl.waitForActivateRequest());

        mICallStreamingServiceControl.setCallEndpointSessionActivated();
        Call call = mInCallCallbacks.getService().getLastCall();
        assertEquals(CALL_ENDPOINT, call.getDetails().getActiveCallEndpoint());
        mICallStreamingServiceControl.setCallEndpointSessionActivated();
        call.disconnect();
    }

    public void testCallEndpointSessionActivation_pushCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        assertFalse(mICallStreamingServiceControl.waitForBound());
        Call call = mInCallCallbacks.getService().getLastCall();
        call.answer(VideoProfile.STATE_AUDIO_ONLY);

        call.pushCall(CALL_ENDPOINT);
        assertTrue(mICallStreamingServiceControl.waitForActivateRequest());
        mICallStreamingServiceControl.setCallEndpointSessionActivated();
        assertEquals(CALL_ENDPOINT, call.getDetails().getActiveCallEndpoint());
        call.disconnect();
    }

    public void testCallEndpointSessionActivation_answerCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        assertFalse(mICallStreamingServiceControl.waitForBound());
        Call call = mInCallCallbacks.getService().getLastCall();

        call.answerCall(CALL_ENDPOINT, VideoProfile.STATE_RX_ENABLED);
        assertTrue(mICallStreamingServiceControl.waitForActivateRequest());
        mICallStreamingServiceControl.setCallEndpointSessionActivated();
        assertEquals(CALL_ENDPOINT, call.getDetails().getActiveCallEndpoint());
        call.disconnect();
    }

    public void testCallEndpointSessionActivationRejected() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        assertFalse(mICallStreamingServiceControl.waitForBound());
        Call call = mInCallCallbacks.getService().getLastCall();

        call.answerCall(CALL_ENDPOINT, VideoProfile.STATE_RX_ENABLED);
        assertTrue(mICallStreamingServiceControl.waitForActivateRequest());
        mICallStreamingServiceControl.setCallEndpointSessionActivationFailed(
                ACTIVATION_FAILURE_REJECTED);
        mOnAnswerFailedCounter.waitForCount(1);
        Iterator<Object> iterator = Arrays.stream(mOnAnswerFailedCounter.getArgs(0)).iterator();
        assertEquals(CALL_ENDPOINT, (CallEndpoint) iterator.next());
        assertEquals(Call.Callback.ANSWER_FAILED_ENDPOINT_REJECTED,
                (int) iterator.next());
        call.disconnect();
    }

    public void testCallEndpointSessionActivationTimeout() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        assertFalse(mICallStreamingServiceControl.waitForBound());
        Call call = mInCallCallbacks.getService().getLastCall();

        call.answerCall(CALL_ENDPOINT, VideoProfile.STATE_RX_ENABLED);
        assertTrue(mICallStreamingServiceControl.waitForActivateRequest());
        assertTrue(mICallStreamingServiceControl.waitForTimeoutNotification());
        mOnAnswerFailedCounter.waitForCount(1);
        Iterator<Object> iterator = Arrays.stream(mOnAnswerFailedCounter.getArgs(0)).iterator();
        assertEquals(CALL_ENDPOINT, (CallEndpoint) iterator.next());
        assertEquals(Call.Callback.ANSWER_FAILED_ENDPOINT_TIMEOUT,
                (int) iterator.next());
        call.disconnect();
    }

    private void setUpControl() throws InterruptedException {
        Intent bindIntent = new Intent(CallStreamingServiceControl.CONTROL_INTERFACE_ACTION);
        ComponentName controlComponentName =
                ComponentName.createRelative(
                        CallStreamingServiceControl.class.getPackage().getName(),
                        CallStreamingServiceControl.class.getName());

        bindIntent.setComponent(controlComponentName);
        final CountDownLatch bindLatch = new CountDownLatch(1);
        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "Service Connected: " + name);
                mICallStreamingServiceControl =
                        ICallStreamingServiceControl.Stub.asInterface(service);
                bindLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "Service Disconnected: " + name);
                mICallStreamingServiceControl = null;
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        bindLatch.await(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
