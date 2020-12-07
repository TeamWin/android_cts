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
package org.hyphonate.megaaudio.player;

public class OboePlayer extends Player {
    boolean mPlaying;

    private int mPlayerSubtype;
    private long mNativePlayer;

    public OboePlayer(AudioSourceProvider sourceProvider, int playerSubtype) {
        super(sourceProvider);

        mPlayerSubtype = playerSubtype;
        mNativePlayer = allocNativePlayer(
                mSourceProvider.getNativeSource().getNativeObject(), mPlayerSubtype);
    }

    @Override
    public int getNumBufferFrames() {
        return getNumBufferFramesN(mNativePlayer);
    }

    @Override
    public int getRoutedDeviceId() {
        return getRoutedDeviceIdN(mNativePlayer);
    }

    @Override
    public boolean isPlaying() { return mPlaying; }

    @Override
    public boolean setupStream(int channelCount, int sampleRate, int numBurstFrames) {
        mChannelCount = channelCount;
        mSampleRate = sampleRate;
        return setupStreamN(
                mNativePlayer, channelCount, sampleRate,
                mRouteDevice == null ? -1 : mRouteDevice.getId());
    }

    @Override
    public void teardownStream() {
        teardownStreamN(mNativePlayer);

        mChannelCount = 0;
        mSampleRate = 0;
    }

    @Override
    public boolean startStream() {
        return startStreamN(mNativePlayer, mPlayerSubtype);
    }

    @Override
    public void stopStream() {
        mPlaying = false;

        stopN(mNativePlayer);
    }

    private native long allocNativePlayer(long nativeSource, int playerSubtype);

    private native boolean setupStreamN(long nativePlayer, int channelCount, int sampleRate, int routeDeviceId);
    private native void teardownStreamN(long nativePlayer);

    private native boolean startStreamN(long nativePlayer, int playerSubtype);
    private native boolean stopN(long nativePlayer);

    private native int getBufferFrameCountN(long mNativePlayer);
    private native int getNumBufferFramesN(long nativePlayer);

    private native int getRoutedDeviceIdN(long nativePlayer);
}
