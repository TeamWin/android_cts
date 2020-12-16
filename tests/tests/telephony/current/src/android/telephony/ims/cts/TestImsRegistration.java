/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.telephony.ims.stub.ImsRegistrationImplBase;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestImsRegistration extends ImsRegistrationImplBase {

    public static class NetworkRegistrationInfo {
        public final int sipCode;
        public final String sipReason;
        NetworkRegistrationInfo(int code, String reason) {
            sipCode = code;
            sipReason = reason;
        }
    }

    private final LinkedBlockingQueue<NetworkRegistrationInfo> mPendingFullRegistrationRequests =
            new LinkedBlockingQueue<>();

    @Override
    public void triggerFullNetworkRegistration(int sipCode, String sipReason) {
        mPendingFullRegistrationRequests.offer(new NetworkRegistrationInfo(sipCode, sipReason));
    }

    public NetworkRegistrationInfo getNextFullNetworkRegRequest(int timeoutMs) throws Exception {
        return mPendingFullRegistrationRequests.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
