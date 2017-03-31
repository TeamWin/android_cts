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

package com.android.cts.verifier.telecom;

import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;

/**
 * An implementation of the {@link android.telecom.Connection} class used by the
 * {@link CtsConnectionService}.
 */
public class CtsConnection extends Connection {
    /**
     * Listener used to inform the CtsVerifier app of changes to a connection.
     */
    public static abstract class Listener {
        void onDestroyed(CtsConnection connection) { };
        void onDisconnect(CtsConnection connection) { };
        void onHold(CtsConnection connection) { };
        void onUnhold(CtsConnection connection) { };
        void onAnswer(CtsConnection connection, int videoState) { };
        void onReject(CtsConnection connection) { };
        void onShowIncomingCallUi(CtsConnection connection) { };
    }

    private final boolean mIsIncomingCall;
    private final Listener mListener;

    public CtsConnection(boolean isIncomingCall, Listener listener) {
        mIsIncomingCall = isIncomingCall;
        mListener = listener;
    }

    public boolean isIncomingCall() {
        return mIsIncomingCall;
    }

    @Override
    public void onDisconnect() {
        setDisconnectedAndDestroy(new DisconnectCause(DisconnectCause.LOCAL));

        if (mListener != null) {
            mListener.onDisconnect(this);
        }
    }


    @Override
    public void onHold() {
        setOnHold();

        if (mListener != null) {
            mListener.onHold(this);
        }
    }

    @Override
    public void onUnhold() {
        setActive();

        if (mListener != null) {
            mListener.onUnhold(this);
        }
    }

    @Override
    public void onAnswer(int videoState) {
        setVideoState(videoState);
        setActive();

        if (mListener != null) {
            mListener.onAnswer(this, videoState);
        }
    }

    @Override
    public void onAnswer() {
        onAnswer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @Override
    public void onReject() {
        setDisconnectedAndDestroy(new DisconnectCause(DisconnectCause.REJECTED));

        if (mListener != null) {
            mListener.onReject(this);
        }
    }

    @Override
    public void onShowIncomingCallUi() {
        if (mListener != null) {
            mListener.onShowIncomingCallUi(this);
        }
    }

    private void setDisconnectedAndDestroy(DisconnectCause cause) {
        setDisconnected(cause);
        destroy();

        if (mListener != null) {
            mListener.onDestroyed(this);
        }
    }
}
