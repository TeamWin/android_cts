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

package android.telecom.cts.apps;

import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.DisconnectCause;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

public class TransactionalCall {
    private static final String TAG = TransactionalCall.class.getSimpleName();
    private String mId = "";
    private boolean mIsOutgoingCall = false;

    public boolean isOutgoingCall() {
        return mIsOutgoingCall;
    }

    public TransactionalCall(CallAttributes callAttributes) {
        mIsOutgoingCall = callAttributes.getDirection() == CallAttributes.DIRECTION_OUTGOING;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public CallControl getCallControl() {
        return mCallControl;
    }

    public void setCallControl(CallControl callControl) {
        mCallControl = callControl;
    }

    public void setCallControlAndId(CallControl callControl) {
        mCallControl = callControl;
        mId = callControl.getCallId().toString();
        Log.i(TAG, String.format("setCallControlAndId: id=[%s], cc=[%s]", mId, callControl));
    }

    private CallControl mCallControl;

    public CallControlCallback mHandshakes = new CallControlCallback() {
        @Override
        public void onSetActive(@NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
        }

        @Override
        public void onSetInactive(@NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
        }

        @Override
        public void onAnswer(int videoState, @NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
        }

        @Override
        public void onDisconnect(@NonNull DisconnectCause cause,
                @NonNull Consumer<Boolean> wasCompleted) {
            wasCompleted.accept(true);
        }

        @Override
        public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
        }
    };

    public TransactionalCallEvents mEvents = new TransactionalCallEvents();
}
