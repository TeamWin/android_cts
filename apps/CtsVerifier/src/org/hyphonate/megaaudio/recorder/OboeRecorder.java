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
package org.hyphonate.megaaudio.recorder;

import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;

public class OboeRecorder extends Recorder {
    @SuppressWarnings("unused")
    private static final String TAG = OboeRecorder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = false;

    private int mRecorderSubtype;
    private long mNativeRecorder;

    public OboeRecorder(AudioSinkProvider sinkProvider, int subType) {
        super(sinkProvider);

        mRecorderSubtype = subType;
        mNativeRecorder = allocNativeRecorder(
                sinkProvider.allocNativeSink().getNativeObject(), mRecorderSubtype);
    }

    //
    // Attributes
    //
    public int getNumBufferFrames() {
        return getNumBufferFramesN(mNativeRecorder);
    }

    @Override
    public void setInputPreset(int preset) {
        setInputPresetN(mNativeRecorder, preset);
    }

    @Override
    public int getRoutedDeviceId() { return getRoutedDeviceIdN(mNativeRecorder); }

    //
    // State
    //
    @Override
    public boolean isRecording() {
        return isRecordingN(mNativeRecorder);
    }

    @Override
    public int setupStream(int channelCount, int sampleRate,
                           int performanceMode, int sharingMode, int numBufferFrames) {
        if (LOG) {
            Log.i(TAG, "setupStream(chans:" + channelCount + ", rate:" + sampleRate
                    + ", frames:" + numBufferFrames + ")");
        }
        mChannelCount = channelCount;
        mSampleRate = sampleRate;
        return setupStreamN(mNativeRecorder, channelCount, sampleRate,
                performanceMode, sharingMode,
                mRouteDevice == null ? -1 : mRouteDevice.getId());
    }

    @Override
    public int setupStream(int channelCount, int sampleRate, int numBufferFrames) {
        return setupStream(channelCount, sampleRate,
                BuilderBase.PERFORMANCE_MODE_LOWLATENCY,
                BuilderBase.SHARING_MODE_EXCLUSIVE,
                numBufferFrames);
    }

    @Override
    public int teardownStream() {
        int errCode = teardownStreamN(mNativeRecorder);
        mChannelCount = 0;
        mSampleRate = 0;

        return errCode;
    }

    @Override
    public int startStream() {
        return startStreamN(mNativeRecorder, mRecorderSubtype);
    }

    @Override
    public int stopStream() {
        return stopN(mNativeRecorder);
    }

    /**
     * @return See StreamState constants
     */
    public int getStreamState() {
        return getStreamStateN(mNativeRecorder);
    }

    /**
     * @return The last error callback result (these must match Oboe). See Oboe constants
     */
    public int getLastErrorCallbackResult() {
        return getLastErrorCallbackResultN(mNativeRecorder);
    }

    private native long allocNativeRecorder(long nativeSink, int recorderSubtype);

    private native boolean isRecordingN(long nativeRecorder);

    private native int getBufferFrameCountN(long nativeRecorder);
    private native void setInputPresetN(long nativeRecorder, int inputPreset);

    private native int getRoutedDeviceIdN(long nativeRecorder);

    private native int setupStreamN(long nativeRecorder, int channelCount,
                       int sampleRate, int performanceMode, int sharingMode, int routeDeviceId);
    private native int teardownStreamN(long nativeRecorder);

    private native int startStreamN(long nativeRecorder, int recorderSubtype);

    private native int stopN(long nativeRecorder);

    private native int getStreamStateN(long nativeRecorder);
    private native int getLastErrorCallbackResultN(long nativeRecorder);

    private native int getNumBufferFramesN(long nativeRecorder);
    private native int calcMinBufferFramesN(long nativeRecorder);
}
