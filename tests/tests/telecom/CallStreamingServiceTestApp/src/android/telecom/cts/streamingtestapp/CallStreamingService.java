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

package android.telecom.cts.streamingtestapp;

import android.content.Intent;
import android.telecom.CallEndpoint;
import android.telecom.CallEndpointCallback;
import android.telecom.CallEndpointSession;
import android.telecom.cts.MockInCallService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CallStreamingService extends MockInCallService {
    private static final String TAG = CallStreamingService.class.getSimpleName();

    private static CountDownLatch sServiceBoundLatch = new CountDownLatch(1);
    private static CountDownLatch sSessionActivated = new CountDownLatch(1);
    private static CountDownLatch sSessionDeactivated = new CountDownLatch(1);
    private static CountDownLatch sSessionTimedout = new CountDownLatch(1);
    private static CallEndpointSession sSession;
    private static CallEndpoint sCallEndpoint;
    private static final Object sLock = new Object();

    private static CallEndpointCallback sCallEndpointCallback = new CallEndpointCallback() {
        @Override
        public void onCallEndpointSessionActivationTimeout() {
            synchronized (sLock) {
                Log.d(TAG, "onCallEndpointSessionActivationTimeout");
                sSessionTimedout.countDown();
            }
        }

        @Override
        public void onCallEndpointSessionDeactivated() {
            synchronized (sLock) {
                Log.d(TAG, "onCallEndpointSessionDeactivated");
                sSessionDeactivated.countDown();
            }
        }
    };

    /**
     * Used to bind a call
     * @param intent
     * @return
     */
    @Override
    public android.os.IBinder onBind(Intent intent) {
        long olderState = sServiceBoundLatch.getCount();
        sServiceBoundLatch.countDown();
        Log.d(TAG, "Streaming Service on bind, " + olderState + " -> " + sServiceBoundLatch);
        return super.onBind(intent);
    }

    @Override
    public CallEndpointCallback onCallEndpointActivationRequested(CallEndpoint endpoint,
            CallEndpointSession session) {
        synchronized (sLock) {
            Log.i(TAG, "onCallEndpointActivationRequested");
            sSession = session;
            sCallEndpoint = endpoint;
            sSessionActivated.countDown();
            return sCallEndpointCallback;
        }
    }

    public static void setCallEndpointSessionActivated() {
        synchronized (sLock) {
            if (sSession != null) {
                sSession.setCallEndpointSessionActivated();
            } else {
                Log.d(TAG, "setCallEndpointSessionActivated called without available session");
            }
        }
    }

    public static void setCallEndpointSessionActivationFailed(int reason) {
        synchronized (sLock) {
            if (sSession != null) {
                sSession.setCallEndpointSessionActivationFailed(reason);
            } else {
                Log.d(TAG,
                        "setCallEndpointSessionActivationFailed called without available session");
            }
        }
    }

    public static void setCallEndpointSessionDeactivated() {
        synchronized (sLock) {
            if (sSession != null) {
                sSession.setCallEndpointSessionDeactivated();
            } else {
                Log.d(TAG, "setCallEndpointSessionDeactivated called without available session");
            }
        }
    }

    public static void reset() {
        synchronized (sLock) {
            sServiceBoundLatch = new CountDownLatch(1);
            sSessionActivated = new CountDownLatch(1);
            sSessionDeactivated = new CountDownLatch(1);
            sSessionTimedout = new CountDownLatch(1);
            sSession = null;
            sCallEndpoint = null;
        }
    }

    public static boolean waitForBound() throws Exception {
        return sServiceBoundLatch.await(5000L, TimeUnit.MILLISECONDS);
    }

    public static boolean waitForActivateRequest() throws Exception {
        return sSessionActivated.await(5000L, TimeUnit.MILLISECONDS);
    }

    public static boolean waitForTimeoutNotification() throws Exception {
        return sSessionTimedout.await(6000L, TimeUnit.MILLISECONDS);
    }

    public static boolean waitForDeactivation() throws Exception {
        return sSessionDeactivated.await(5000L, TimeUnit.MILLISECONDS);
    }
}
