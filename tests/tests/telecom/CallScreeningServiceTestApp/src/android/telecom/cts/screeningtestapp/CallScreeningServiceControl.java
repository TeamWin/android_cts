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

package android.telecom.cts.screeningtestapp;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.IBinder;
import android.telecom.CallIdentification;
import android.telecom.CallScreeningService;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CallScreeningServiceControl extends Service {
    private static final int ASYNC_TIMEOUT = 10000;
    private static final String TAG = CallScreeningServiceControl.class.getSimpleName();
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.screeningtestapp.ACTION_CONTROL_CALL_SCREENING_SERVICE";
    public static final ComponentName CONTROL_INTERFACE_COMPONENT =
            ComponentName.unflattenFromString(
                    "android.telecom.cts.screeningtestapp/.CallScreeningServiceControl");

    private static CallScreeningServiceControl sCallScreeningServiceControl = null;

    private final IBinder mControlInterface =
            new android.telecom.cts.screeningtestapp.ICallScreeningControl.Stub() {
                @Override
                public void reset() {
                    mCallIdentification = null;
                    mNuisanceCallUri = null;
                    mIsNuisanceReportReceived = false;
                    mCallResponse = new CallScreeningService.CallResponse.Builder()
                            .setDisallowCall(false)
                            .setRejectCall(false)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                            .build();
                }

                @Override
                public void setProviderCallIdentification(CharSequence name,
                        CharSequence description, CharSequence details, Icon icon, int confidence) {
                    Log.i(TAG, "setProviderCallIdentification: got test id info");
                    if (TextUtils.isEmpty(name)) {
                        mCallIdentification = null;
                    } else {
                        mCallIdentification = new CallIdentification.Builder()
                                .setName(name)
                                .setDetails(details)
                                .setDescription(description)
                                .setPhoto(icon)
                                .setNuisanceConfidence(confidence)
                                .build();
                    }
                }

                @Override
                public void setCallResponse(boolean shouldDisallowCall,
                        boolean shouldRejectCall,
                        boolean shouldSilenceCall,
                        boolean shouldSkipCallLog,
                        boolean shouldSkipNotification) {
                    Log.i(TAG, "setCallResponse");
                    mCallResponse = new CallScreeningService.CallResponse.Builder()
                            .setSkipNotification(shouldSkipNotification)
                            .setSkipCallLog(shouldSkipCallLog)
                            .setDisallowCall(shouldDisallowCall)
                            .setRejectCall(shouldRejectCall)
                            .setSilenceCall(shouldSilenceCall)
                            .build();
                }

                @Override
                public void waitForNuisanceReport(long timeoutMillis) {
                    try {
                        mNuisanceLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public boolean getIsNuisance() {
                    return mIsNuisanceCall;
                }

                @Override
                public Uri getNuisanceCallHandle() {
                    return mNuisanceCallUri;
                }

                @Override
                public int getNuisanceCallType() {
                    return mNuisanceCallType;
                }

                @Override
                public int getNuisanceCallDuration() {
                    return mNuisanceCallDuration;
                }

                @Override
                public boolean isNuisanceReportReceived() {
                    return mIsNuisanceReportReceived;
                }
            };

    private CallIdentification mCallIdentification = null;
    private CallScreeningService.CallResponse mCallResponse =
            new CallScreeningService.CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
    private CountDownLatch mNuisanceLatch = new CountDownLatch(1);
    private boolean mIsNuisanceCall;
    private int mNuisanceCallType;
    private int mNuisanceCallDuration;
    private Uri mNuisanceCallUri;
    private boolean mIsNuisanceReportReceived = false;

    public static CallScreeningServiceControl getInstance() {
        return sCallScreeningServiceControl;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: returning control interface");
            sCallScreeningServiceControl = this;
            return mControlInterface;
        }
        Log.i(TAG, "onBind: uh oh");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sCallScreeningServiceControl = null;
        return false;
    }

    public CallIdentification getCallIdentification() {
        return mCallIdentification;
    }

    public CallScreeningService.CallResponse getCallResponse() {
        return mCallResponse;
    }

    public void handleNuisanceStatusChanged(Uri handle, int callDuration, int callType,
            boolean isNuisance) {
        mNuisanceCallUri = handle;
        mIsNuisanceCall = isNuisance;
        mNuisanceCallDuration = callDuration;
        mNuisanceCallType = callType;
        mIsNuisanceReportReceived = true;
        Log.i(TAG, "handleNuisanceStatusChanged - got nuisance report");
        mNuisanceLatch.countDown();
    }

}
