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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

/**
 * Implementation of abstract Recorder class implemented for the Android Java-based audio record
 * API, i.e. AudioRecord.
 */
public class JavaRecorder extends Recorder {
    @SuppressWarnings("unused") private static String TAG = JavaRecorder.class.getSimpleName();
    @SuppressWarnings("unused") private static final boolean LOG = true;

    /** The buffer to receive the recorder samples */
    private float[] mRecorderBuffer;

    /** The number of FRAMES of audio data in the record buffer */
    private int mNumBuffFrames;

    // Recording state
    /** <code>true</code> if currently recording audio data */
    private boolean mRecording = false;

    /* The AudioRecord for recording the audio stream */
    private AudioRecord mAudioRecord = null;

    private AudioSink mAudioSink;

    /**
     * The listener to receive notifications of recording events
     * @see {@link JavaSinkHandler}
     */
    private JavaSinkHandler mListener = null;

    public JavaRecorder(AudioSinkProvider sinkProvider) {
        super(sinkProvider);
    }

    /** The buff to receive the recorder samples */
    public float[] getFloatBuffer() { return mRecorderBuffer; }

    // JavaRecorder-specific extension
    public AudioRecord getAudioRecord() { return mAudioRecord; }

    /*
     * State
     */
    @Override
    public boolean isRecording() {
        return mRecording;
    }

    @Override
    public boolean setupAudioStream(int channelCount, int sampleRate, int numBurstFrames) {
        if (LOG) {
            Log.i(TAG, "setupAudioStream(chans:" + channelCount + ", rate:" + sampleRate +
                    ", frames:" + numBurstFrames);
        }
        mChannelCount = channelCount;
        mSampleRate = sampleRate;

        try {
            int frameSize = calcFrameSizeInBytes(mChannelCount);

            mAudioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(mSampleRate)
                            .setChannelIndexMask(StreamBase.channelCountToIndexMask(mChannelCount))
                            .build())
                    .setBufferSizeInBytes(numBurstFrames * frameSize)
                    .build();

            mNumBuffFrames = mAudioRecord.getBufferSizeInFrames();

            mRecorderBuffer = new float[mNumBuffFrames * mChannelCount];

            if (mSinkProvider == null) {
                mSinkProvider = new NopAudioSinkProvider();
            }
            mAudioSink = mSinkProvider.getJavaSink();
            mListener = new JavaSinkHandler(this, mAudioSink, Looper.getMainLooper());
            return true;
        } catch (UnsupportedOperationException ex) {
            if (LOG) {
                Log.i(TAG, "Couldn't open AudioRecord: " + ex);
            }
            mAudioRecord = null;
            mNumBuffFrames = 0;
            mRecorderBuffer = null;

            return false;
        }
    }

    @Override
    public void teardownAudioStream() {
        stopStream();

        waitForStreamThreadToExit();

        if (mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;
        }

        mChannelCount = 0;
        mSampleRate = 0;
    }

    @Override
    public boolean startStream() {

        // Routing
//        mAudioRecord.setPreferredDevice(mRoutingDevice);
//
        if (mListener != null) {
            mListener.sendEmptyMessage(JavaSinkHandler.MSG_START);
        }

        try {
            mAudioRecord.startRecording();
        } catch (IllegalStateException ex) {
            Log.e(TAG, "startRecording exception: " + ex);
        }

        waitForStreamThreadToExit(); // just to be sure.

        mStreamThread = new Thread(new RecorderRunnable(), "JavaRecorder Thread");
        mRecording = true;
        mStreamThread.start();

        return true;
    }

    /**
     * Marks the stream for stopping on the next callback from the underlying system.
     *
     * Returns immediately, though a call to AudioSource.push() may be in progress.
     */
    @Override
    public void stopStream() {
        mRecording = false;
    }

    // @Override
    // Used in JavaSinkHandler
    public float[] getDataBuffer() {
        return mRecorderBuffer;
        // System.arraycopy(mRecorderBuffer, 0, buffer, 0, mNumBuffFrames * mChannelCount);
    }

    @Override
    public int getNumBufferFrames() {
        return mNumBuffFrames;
    }

    /*
     * Recorder Thread
     */
    /**
     * Implements the <code>run</code> method for the record thread.
     * Starts the AudioRecord, then continuously reads audio data
     * until the flag <code>mRecording</code> is set to false (in the stop() method).
     */
    private class RecorderRunnable implements Runnable {
        @Override
        public void run() {
            final int numBurstSamples = mNumBuffFrames * mChannelCount;
            int numReadSamples = 0;
            while (mRecording) {
                numReadSamples = mAudioRecord.read(
                        mRecorderBuffer, 0, numBurstSamples, AudioRecord.READ_BLOCKING);

                if (numReadSamples < 0) {
                    // error
                    if (LOG) {
                        Log.e(TAG, "AudioRecord write error: " + numReadSamples);
                    }
                    stopStream();
                } else if (numReadSamples < numBurstSamples) {
                    // got less than requested?
                    if (LOG) {
                        Log.e(TAG, "AudioRecord Underflow: " + numReadSamples +
                                " vs. " + numBurstSamples);
                    }
                    stopStream();
                }

                if (mListener != null) {
                    // TODO: on error or underrun we may be send bogus data.
                    mListener.sendEmptyMessage(JavaSinkHandler.MSG_BUFFER_FILL);
                }
            }

            if (mListener != null) {
                // TODO: on error or underrun we may be send bogus data.
                Message message = new Message();
                message.what = JavaSinkHandler.MSG_STOP;
                message.arg1 = numReadSamples;
                mListener.sendMessage(message);
            }
            mAudioRecord.stop();
        }
    }
}
