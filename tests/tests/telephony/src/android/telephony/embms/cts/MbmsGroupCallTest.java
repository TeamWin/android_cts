/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.telephony.embms.cts;

import android.annotation.Nullable;
import android.telephony.cts.embmstestapp.CtsGroupCallService;
import android.telephony.mbms.GroupCallCallback;
import android.telephony.mbms.MbmsErrors;
import android.telephony.mbms.GroupCall;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MbmsGroupCallTest extends MbmsGroupCallTestBase {
    private class TestGroupCallCallback extends GroupCallCallback {
        private final BlockingQueue<SomeArgs> mErrorCalls = new LinkedBlockingQueue<>();
        private final BlockingQueue<SomeArgs> mGroupCallStateChangedCalls=
                new LinkedBlockingQueue<>();
        private final BlockingQueue<SomeArgs> mBroadcastSignalStrengthUpdatedCalls =
                new LinkedBlockingQueue<>();

        @Override
        public void onError(int errorCode, @Nullable String message) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = errorCode;
            args.arg2 = message;
            mErrorCalls.add(args);
        }

        @Override
        public void onGroupCallStateChanged(int state, int reason) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = state;
            args.arg2 = reason;
            mGroupCallStateChangedCalls.add(args);
        }

        @Override
        public void onBroadcastSignalStrengthUpdated(int signalStrength) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = signalStrength;
            mBroadcastSignalStrengthUpdatedCalls.add(args);
        }

        public SomeArgs waitOnError() {
            try {
                return mErrorCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        public SomeArgs waitOnGroupCallStateChanged() {
            try {
                return mGroupCallStateChangedCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        public SomeArgs waitOnBroadcastSignalStrengthUpdated() {
            try {
                return mBroadcastSignalStrengthUpdatedCalls.poll(
                        ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }
    }

    private static final long TMGI = 568734963245L;
    private static final int[] SAI_ARRAY = new int[]{16, 24, 46, 76};
    private static final int[] FREQUENCY_ARRAY = new int[]{2075, 2050, 1865};

    private TestGroupCallCallback mGroupCallCallback =
            new TestGroupCallCallback();

    public void testStartGroupCall() throws Exception {
        GroupCall groupCall = mGroupCallSession.startGroupCall(mCallbackExecutor,
                TMGI, SAI_ARRAY, FREQUENCY_ARRAY, mGroupCallCallback);
        assertNotNull(groupCall);
        assertEquals(TMGI, groupCall.getTmgi());

        SomeArgs args = mGroupCallCallback.waitOnGroupCallStateChanged();
        assertEquals(GroupCall.STATE_STARTED, args.arg1);
        assertEquals(GroupCall.REASON_BY_USER_REQUEST, args.arg2);

        List<List<Object>> startGroupCallCalls =
                getMiddlewareCalls(CtsGroupCallService.METHOD_START_GROUP_CALL);
        assertEquals(1, startGroupCallCalls.size());
        List<Object> startGroupCallCall = startGroupCallCalls.get(0);
        assertEquals(TMGI, startGroupCallCall.get(2));
        assertArrayEquals(SAI_ARRAY, (int[]) startGroupCallCall.get(3));
        assertArrayEquals(FREQUENCY_ARRAY, (int[]) startGroupCallCall.get(4));
    }

    public void testUpdateGroupCall() throws Exception {
        GroupCall groupCall = mGroupCallSession.startGroupCall(mCallbackExecutor,
                TMGI, SAI_ARRAY, FREQUENCY_ARRAY, mGroupCallCallback);
        int[] newSais = new int[]{16};
        int[] newFreqs = new int[]{2075};
        groupCall.updateGroupCall(newSais, newFreqs);

        List<List<Object>> updateGroupCallCalls =
                getMiddlewareCalls(CtsGroupCallService.METHOD_UPDATE_GROUP_CALL);
        assertEquals(1, updateGroupCallCalls.size());
        List<Object> updateGroupCallCall = updateGroupCallCalls.get(0);
        assertEquals(TMGI, updateGroupCallCall.get(2));
        assertArrayEquals(newSais, (int[]) updateGroupCallCall.get(3));
        assertArrayEquals(newFreqs, (int[]) updateGroupCallCall.get(4));
    }

    public void testStopGroupCall() throws Exception {
        GroupCall groupCall = mGroupCallSession.startGroupCall(mCallbackExecutor,
                TMGI, SAI_ARRAY, FREQUENCY_ARRAY, mGroupCallCallback);
        groupCall.close();
        List<List<Object>> stopGroupCallCalls =
                getMiddlewareCalls(CtsGroupCallService.METHOD_STOP_GROUP_CALL);
        assertEquals(1, stopGroupCallCalls.size());
        assertEquals(TMGI, stopGroupCallCalls.get(0).get(2));
    }

    public void testGroupCallCallbacks() throws Exception {
        mGroupCallSession.startGroupCall(mCallbackExecutor,
                TMGI, SAI_ARRAY, FREQUENCY_ARRAY, mGroupCallCallback);
        mMiddlewareControl.fireErrorOnGroupCall(MbmsErrors.GeneralErrors.ERROR_IN_E911,
                MbmsGroupCallTest.class.getSimpleName());
        SomeArgs groupCallErrorArgs = mGroupCallCallback.waitOnError();
        assertEquals(MbmsErrors.GeneralErrors.ERROR_IN_E911, groupCallErrorArgs.arg1);
        assertEquals(MbmsGroupCallTest.class.getSimpleName(), groupCallErrorArgs.arg2);

        int broadcastSignalStrength = 3;
        mMiddlewareControl.fireBroadcastSignalStrengthUpdated(broadcastSignalStrength);
        assertEquals(broadcastSignalStrength,
                mGroupCallCallback.waitOnBroadcastSignalStrengthUpdated().arg1);
    }

    public void testStartGroupCallFailure() throws Exception {
        mMiddlewareControl.forceErrorCode(
                MbmsErrors.GeneralErrors.ERROR_MIDDLEWARE_TEMPORARILY_UNAVAILABLE);
        mGroupCallSession.startGroupCall(mCallbackExecutor,
                TMGI, SAI_ARRAY, FREQUENCY_ARRAY, mGroupCallCallback);
        assertEquals(MbmsErrors.GeneralErrors.ERROR_MIDDLEWARE_TEMPORARILY_UNAVAILABLE,
                mCallback.waitOnError().arg1);
    }

    private void assertArrayEquals(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("Arrays differ at element " + i
                        + " -- expected: " + Arrays.toString(expected) + "; actual: "
                        + Arrays.toString(actual));
            }
        }
    }
}
