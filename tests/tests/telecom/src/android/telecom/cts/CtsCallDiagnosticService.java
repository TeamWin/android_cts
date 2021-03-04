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

import android.content.Intent;
import android.telecom.BluetoothCallQualityReport;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallDiagnosticService;
import android.telecom.DiagnosticCall;
import android.telephony.CallQuality;
import android.telephony.ims.ImsReasonInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CtsCallDiagnosticService extends CallDiagnosticService {
    private static final String LOG_TAG = "CtsCallDiagnosticService";
    private static CtsCallDiagnosticService sCtsCallDiagnosticService;
    private CallAudioState mCallAudioState;
    private BluetoothCallQualityReport mBluetoothCallQualityReport;
    private static CountDownLatch sBindLatch = new CountDownLatch(1);
    private CountDownLatch mChangeLatch = new CountDownLatch(1);
    private CountDownLatch mBluetoothCallQualityReportLatch = new CountDownLatch(1);
    private CountDownLatch mCallAudioStateLatch = new CountDownLatch(1);
    private List<CtsDiagnosticCall> mCalls = new ArrayList<>();

    public static class CtsDiagnosticCall extends DiagnosticCall {
        private Call.Details mCallDetails;
        private int mMessageType;
        private int mMessageValue;
        private CallQuality mCallQuality;
        private CountDownLatch mCallQualityReceivedLatch = new CountDownLatch(1);
        private CountDownLatch mReceivedMessageLatch = new CountDownLatch(1);
        private CountDownLatch mCallDetailsReceivedLatch = new CountDownLatch(1);

        @Override
        public void onCallDetailsChanged(@NonNull Call.Details details) {
            mCallDetails = details;
            mCallDetailsReceivedLatch.countDown();
        }

        @Override
        public void onReceiveDeviceToDeviceMessage(int message, int value) {
            mMessageType = message;
            mMessageValue = value;
            mReceivedMessageLatch.countDown();
        }

        @Nullable
        @Override
        public CharSequence onCallDisconnected(int disconnectCause, int preciseDisconnectCause) {
            return null;
        }

        @Nullable
        @Override
        public CharSequence onCallDisconnected(@NonNull ImsReasonInfo disconnectReason) {
            return null;
        }

        @Override
        public void onCallQualityReceived(@NonNull CallQuality callQuality) {
            mCallQuality = callQuality;
            mReceivedMessageLatch.countDown();
        }

        @NonNull
        @Override
        public Call.Details getCallDetails() {
            return mCallDetails;
        }

        public int getMessageType() {
            return mMessageType;
        }

        public void setMessageType(int messageType) {
            this.mMessageType = messageType;
        }

        public int getMessageValue() {
            return mMessageValue;
        }

        public CountDownLatch getCallQualityReceivedLatch() {
            return mCallQualityReceivedLatch;
        }

        public CountDownLatch getReceivedMessageLatch() {
            return mReceivedMessageLatch;
        }

        public CallQuality getCallQuality() {
            return mCallQuality;
        }

        public CountDownLatch getCallDetailsReceivedLatch() {
            return mCallDetailsReceivedLatch;
        }
    }

    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        Log.i(LOG_TAG, "Service bound.");
        sCtsCallDiagnosticService = this;
        android.os.IBinder binder = super.onBind(intent);
        sBindLatch.countDown();
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(LOG_TAG, "Service unbound");
        sCtsCallDiagnosticService = null;
        return super.onUnbind(intent);
    }

    @NonNull
    @Override
    public DiagnosticCall onInitializeDiagnosticCall(@NonNull Call.Details call) {
        CtsDiagnosticCall diagCall = new CtsDiagnosticCall();
        diagCall.mCallDetails = call;
        mCalls.add(diagCall);
        mChangeLatch.countDown();
        return diagCall;
    }

    @Override
    public void onRemoveDiagnosticCall(@NonNull DiagnosticCall call) {
        Log.i(LOG_TAG, "onRemoveDiagnosticCall: " + call);
        mCalls.remove(call);
        mChangeLatch.countDown();
    }

    @Override
    public void onCallAudioStateChanged(@NonNull CallAudioState audioState) {
        mCallAudioState = audioState;
        mCallAudioStateLatch.countDown();
    }

    @Override
    public void onBluetoothCallQualityReportReceived(
            @NonNull BluetoothCallQualityReport qualityReport) {
        mBluetoothCallQualityReport = qualityReport;
        mBluetoothCallQualityReportLatch.countDown();
    }

    public static CtsCallDiagnosticService getInstance() {
        return sCtsCallDiagnosticService;
    }

    public static CountDownLatch getBindLatch() {
        return sBindLatch;
    }

    public CallAudioState getCallAudioState() {
        return mCallAudioState;
    }

    public CountDownLatch getCallAudioStateLatch() {
        return mCallAudioStateLatch;
    }

    public BluetoothCallQualityReport getBluetoothCallQualityReport() {
        return mBluetoothCallQualityReport;
    }

    public CountDownLatch getCallChangeLatch() {
        CountDownLatch latch = mChangeLatch;
        mChangeLatch = new CountDownLatch(1);
        return latch;
    }

    public CountDownLatch getBluetoothCallQualityReportLatch() {
        return mBluetoothCallQualityReportLatch;
    }

    public List<CtsDiagnosticCall> getCalls() {
        return mCalls;
    }
}
