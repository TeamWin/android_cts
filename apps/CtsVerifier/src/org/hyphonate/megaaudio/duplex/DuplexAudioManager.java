/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.duplex;

import android.media.AudioDeviceInfo;
import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.Player;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.recorder.AudioSink;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;

public class DuplexAudioManager {
    private static final String TAG = DuplexAudioManager.class.getSimpleName();

    // Player
    private int mNumPlayerChannels = 2;
    private int mPlayerSampleRate = 48000;
    private int mNumPlayerBufferFrames;

    private Player mPlayer;
    private AudioSourceProvider mSourceProvider;
    private AudioDeviceInfo mPlayerSelectedDevice;

    // Recorder
    private int mNumRecorderChannels = 2;
    private int mRecorderSampleRate = 48000;
    private int mNumRecorderBufferFrames;

    private Recorder mRecorder;
    private AudioSinkProvider mSinkProvider;
    private AudioDeviceInfo mRecorderSelectedDevice;

    public DuplexAudioManager(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
        mSourceProvider = sourceProvider;
        mSinkProvider = sinkProvider;
    }

    public boolean setupStreams(int playerType, int recorderType) {
        // Recorder
        if ((recorderType & BuilderBase.TYPE_MASK) != BuilderBase.TYPE_NONE) {
            try {
                mRecorder = new RecorderBuilder()
                        .setRecorderType(recorderType)
                        .setAudioSinkProvider(mSinkProvider)
                        .build();
//                if (mSelectedPreset != -1) {
//                    mRecorder!!.setInputPreset(mSelectedPreset);
//                }
                mRecorder.setRouteDevice(mRecorderSelectedDevice);
                if (!mRecorder.setupStream(
                        mNumRecorderChannels, mRecorderSampleRate, mNumRecorderBufferFrames)) {
                    Log.e(TAG, "Recorder setupStream() failed");
                    return false;
                }
                mNumRecorderBufferFrames = mRecorder.getNumBufferFrames();
            } catch (RecorderBuilder.BadStateException ex) {
                Log.e(TAG, "Recorder - BadStateException" + ex);
                return false;
            }
        }

        // Player
        if ((playerType & BuilderBase.TYPE_MASK) != BuilderBase.TYPE_NONE) {
            try {
                mNumPlayerBufferFrames =
                        Player.calcMinBufferFrames(mNumPlayerChannels, mPlayerSampleRate);
                mPlayer = new PlayerBuilder()
                        .setPlayerType(playerType)
                        .setSourceProvider(mSourceProvider)
                        .build();
                mPlayer.setRouteDevice(mPlayerSelectedDevice);
                if (!mPlayer.setupStream(mNumPlayerChannels, mPlayerSampleRate, mNumPlayerBufferFrames)) {
                    Log.e(TAG, "Player - setupStream() failed");
                    return false;
                }
            } catch (PlayerBuilder.BadStateException ex) {
                Log.e(TAG, "Player - BadStateException" + ex);
                return false;
            }
        }

        return true;
    }

    public boolean start() {
        if (mRecorder != null && !mRecorder.startStream()) {
            return false;
        }

        if (mPlayer != null && !mPlayer.startStream()) {
            return false;
        }

        return true;
    }

    public void stop() {
        if (mPlayer != null) {
            mPlayer.stopStream();
        }

        if (mRecorder != null) {
            mRecorder.stopStream();
        }
    }

    public int getNumPlayerBufferFrames() {
        return mPlayer != null ? mPlayer.getNumBufferFrames() : 0;
    }

    public int getNumRecorderBufferFrames() {
        return mRecorder != null ? mRecorder.getNumBufferFrames() : 0;
    }
}
