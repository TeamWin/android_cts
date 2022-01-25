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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

public class CallStreamingServiceControl extends Service {
    public static final String TAG = CallStreamingServiceControl.class.getSimpleName();
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.streamingtestapp.ACTION_CONTROL_CALL_STREAMING_SERVICE";

    private static CallStreamingServiceControl sCallStreamingServiceControl = null;
    private CountDownLatch mBindingLatch = new CountDownLatch(1);

    private final IBinder mControlInterface =
            new android.telecom.cts.streamingtestapp.ICallStreamingServiceControl.Stub() {
                @Override
                public void reset() {
                    CallStreamingService.reset();
                }

                @Override
                public void setCallEndpointSessionActivated() {
                    CallStreamingService.setCallEndpointSessionActivated();
                }

                @Override
                public void setCallEndpointSessionActivationFailed(int reason) {
                    CallStreamingService.setCallEndpointSessionActivationFailed(reason);
                }

                @Override
                public void setCallEndpointSessionDeactivated() {
                    CallStreamingService.setCallEndpointSessionDeactivated();
                }

                @Override
                public boolean waitForBound() {
                    try {
                        return CallStreamingService.waitForBound();
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                public boolean waitForActivateRequest() {
                    try {
                        return CallStreamingService.waitForActivateRequest();
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                public boolean waitForTimeoutNotification() {
                    try {
                        return CallStreamingService.waitForTimeoutNotification();
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                public boolean waitForDeactivation() {
                    try {
                        return CallStreamingService.waitForDeactivation();
                    } catch (Exception e) {
                        return false;
                    }
                }
            };

    public static CallStreamingServiceControl getInstance() {
        return sCallStreamingServiceControl;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: returning control interface");
            sCallStreamingServiceControl = this;
            return mControlInterface;
        }
        Log.i(TAG, "onBind: uh oh");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sCallStreamingServiceControl = null;
        return true;
    }

    public void onStreamingServiceBound() {
        mBindingLatch.countDown();
    }
}
