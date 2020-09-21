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
package org.hyphonate.megaaudio.common;

import android.media.AudioFormat;

public abstract class StreamBase {
    /*
     * Stream attributes
     */
    protected int mChannelCount;
    protected int mSampleRate;

    public int getChannelCount() { return mChannelCount; }
    public int getSampleRate() { return mSampleRate; }

    public abstract int getNumBufferFrames();

    // the thread on which the underlying Android AudioTrack/AudioRecord will run
    protected Thread mStreamThread = null;

    //
    // Attributes
    //

    //
    // Sample Format Utils
    //
    /**
     * @param encoding An Android ENCODING_ constant for audio data.
     * @return The size in BYTES of samples encoded as specified.
     */
    public static int sampleSizeInBytes(int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_16BIT:
                return 2;

            case AudioFormat.ENCODING_PCM_FLOAT:
                return 4;

            default:
                return 0;
        }
    }

    /**
     * @param numChannels   The number of channels in a FRAME of audio data.
     * @return  The size in BYTES of a FRAME of audio data encoded as specified.
     */
    public static int calcFrameSizeInBytes(int numChannels) {
        return sampleSizeInBytes(AudioFormat.ENCODING_PCM_FLOAT) * numChannels;
    }

    //
    // State
    //

    /**
     * @param channelCount  The number of channels of audio data to be streamed.
     * @param sampleRate    The stream sample rate
     * @param numFrames     The number of frames of audio data in the stream's buffer.
     * @return              True if the stream is successfully initialized.
     */
    public abstract boolean setupAudioStream(int channelCount, int sampleRate, int numFrames);

    public abstract void teardownAudioStream();

    /**
     * Starts playback on an open stream player. (@see open() method above).
     * @return <code>true</code> if playback/recording starts.
     */
    public abstract boolean startStream();

    /**
     * Stops playback.
     * May not stop the stream immediately. i.e. does not stop until the next audio callback
     * from the underlying system.
     */
    public abstract void stopStream();

    //
    // Thread stuff
    //
    /**
     * Joins the record thread to ensure that the stream is stopped.
     */
    protected void waitForStreamThreadToExit() {
        try {
            if (mStreamThread != null) {
                mStreamThread.join();
                mStreamThread = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //
    // Utility
    //
    /**
     * @param chanCount The number of channels for which to generate an index mask.
     * @return  A channel index mask corresponding to the supplied channel count.
     *
     * @note The generated index mask has active channels from 0 to chanCount - 1
     */
    public static int channelCountToIndexMask(int chanCount) {
        return  (1 << chanCount) - 1;
    }
}
