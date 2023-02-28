/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.telecom.CallAttributes.DIRECTION_OUTGOING;

import android.net.Uri;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallException;
import android.telecom.PhoneAccount;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CallStreamingTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = CallStreamingTest.class.getSimpleName();
    private static final String TEL_CLEAN_STUCK_CALLS_CMD = "telecom cleanup-stuck-calls";

    public final PhoneAccount ACCOUNT =
            PhoneAccount.builder(TestUtils.TEST_SELF_MANAGED_HANDLE_1, TestUtils.ACCOUNT_LABEL)
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                    ).build();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) {
            return;
        }
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

    public void testStartCallStreaming() {
        if (!mShouldTestTelecom) {
            return;
        }

        TelecomCtsVoipCall call = new TelecomCtsVoipCall("streaming_call");
        final CountDownLatch latch = new CountDownLatch(1);
        CallAttributes attributes = new CallAttributes.Builder(TestUtils.TEST_SELF_MANAGED_HANDLE_1,
                DIRECTION_OUTGOING, "testName", Uri.parse("tel:123-TEST"))
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .build();

        mTelecomManager.addCall(attributes, Runnable::run, new OutcomeReceiver<>() {
            @Override
            public void onResult(CallControl callControl) {
                Log.i(TAG, "onResult: adding callControl to callObject");

                if (callControl == null) {
                    fail("Can't get call control");
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

        final android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver outcome =
                new android.telecom.cts.TelecomCtsVoipCall.LatchedOutcomeReceiver(latch);

        call.mCallControl.startCallStreaming(Runnable::run, outcome);
        assertOnResultWasReceived(outcome.mCountDownLatch);
    }

    public void assertOnResultWasReceived(CountDownLatch latch) {
        Log.i(TAG, "assertOnResultWasReceived: waiting for latch");
        try {
            boolean success = latch.await(5000, TimeUnit.MILLISECONDS);
            if (!success) {
                fail("Outcome received but it's failed.");
            }

        } catch (InterruptedException ie) {
            fail("Failed when trying to receive outcome");
        }
    }

    private void cleanup() {
        Log.i(TAG, "cleanup: method running");
        try {
            if (mInCallCallbacks.getService() != null) {
                mInCallCallbacks.getService().disconnectAllCalls();
                mInCallCallbacks.getService().clearCallList();
            }
            TestUtils.executeShellCommand(getInstrumentation(), TEL_CLEAN_STUCK_CALLS_CMD);
        } catch (Exception e) {
            Log.i(TAG, "Failed when cleanup: " + e);
        }
    }
}
