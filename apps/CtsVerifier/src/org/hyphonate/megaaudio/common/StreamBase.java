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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.util.Log;

// For initialization
import org.hyphonate.megaaudio.player.JavaSourceProxy;

public abstract class StreamBase {
    @SuppressWarnings("unused")
    private static final String TAG = StreamBase.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = false;

    static {
        Log.i(TAG, "Loading MegaAudio Library...");
        try {
            System.loadLibrary("megaaudio_jni");
            JavaSourceProxy.initN();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading MegaAudio JNI library");
            Log.e(TAG, "e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }

    //
    // Error Codes
    // These values must be kept in sync with the equivalent symbols in
    // megaaudio/common/Streambase.h
    //
    public static final int OK = 0;
    public static final int ERROR_UNKNOWN = -1;
    public static final int ERROR_UNSUPPORTED = -2;
    public static final int ERROR_INVALID_STATE = -3;

    //
    // System Attributes
    //
    //
    private static int sSystemBurstFrames = 512;
    private static int sSystemSampleRate = 48000;

    // Stream attributes
    //
    protected int mChannelCount;
    protected int mSampleRate;

    // Routing
    protected AudioDeviceInfo mRouteDevice;

    // the thread on which the underlying Android AudioTrack/AudioRecord will run
    protected Thread mStreamThread = null;

    //
    // Initialization
    //

    /**
     * Performs initialization. MUST be called before any Streams are created.
     * @param context
     */
    public static void setup(Context context) {
        calcNumBurstFrames(context);
        calcSystemSampleRate(context);
    }

    //
    // Attributes
    //
    public int getChannelCount() { return mChannelCount; }
    public int getSampleRate() { return mSampleRate; }

    /**
     * Gets the system-specified burst-size in frames. This should be called by the
     * app in initialization before calling getSystemBurstFrames() (below).
     * @return the system-specified burst size in frames.
     */
    public static int calcNumBurstFrames(Context context) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        String text = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        sSystemBurstFrames = Integer.parseInt(text);
        if (LOG) {
            Log.i(TAG, "sSystemBurstFrames:" + sSystemBurstFrames);
        }
        return sSystemBurstFrames;
    }

    /**
     * @return the system-specified burst size in frames.
     */
    public static int getSystemBurstFrames() {
        return sSystemBurstFrames;
    }

    /**
     * Gets the system-speficied preferred sample rate for audio. This should be called by the
     *      * app in initialization before calling getSystemSampleRate() (below).
     * @return the system preferred sample rate
     */
    public static int calcSystemSampleRate(Context context) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        String text = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        return sSystemSampleRate = Integer.parseInt(text);
    }

    /**
     * @return the system preferred sample rate
     */
    public static int getSystemSampleRate() {
        return sSystemSampleRate;
    }

    // Routing
    public void setRouteDevice(AudioDeviceInfo routeDevice) {
        mRouteDevice = routeDevice;
    }

    public static final int ROUTED_DEVICE_ID_INVALID = -1;
    public abstract int getRoutedDeviceId();

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

    //
    // State
    //
    /**
     * @param channelCount  The number of channels of audio data to be streamed.
     * @param sampleRate    The stream sample rate
     * @param numFrames     The number of frames of audio data in the stream's buffer.
     * @return              ERROR_NONE if successful, otherwise an error code
     */
    public abstract int setupStream(int channelCount, int sampleRate, int numFrames);

    public abstract int teardownStream();

    /**
     * Starts playback on an open stream player. (@see open() method above).
     * @return              ERROR_NONE if successful, otherwise an error code
     */
    public abstract int startStream();

    /**
     * Stops playback.
     * May not stop the stream immediately. i.e. does not stop until the next audio callback
     * from the underlying system.
     * @return              ERROR_NONE if successful, otherwise an error code
     */
    public abstract int stopStream();

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
     * note: The generated index mask has active channels from 0 to chanCount - 1
     */
    public static int channelCountToIndexMask(int chanCount) {
        return  (1 << chanCount) - 1;
    }

    private static int[] sOutMasks =
            {   -1,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER,
                AudioFormat.CHANNEL_OUT_QUAD
            };

    /**
     *
     * @param chanCount The number of channels for which to generate a postional mask.
     * @return the corresponding channel position mask
     * note: This mapping is not well defined, but may be needed to get a fast path in the Java API
     */
    public static int channelCountToOutPositionMask(int chanCount) {
        return chanCount <= 4 ? sOutMasks[chanCount] : AudioFormat.CHANNEL_OUT_STEREO;
    }
}
