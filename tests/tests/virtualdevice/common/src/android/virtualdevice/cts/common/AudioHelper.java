/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.virtualdevice.cts.common;

import android.companion.virtual.audio.AudioCapture;
import android.media.AudioFormat;
import android.media.AudioRecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility methods for creating and processing audio data.
 */
public final class AudioHelper {
    /** Tells the activity to play audio for testing. */
    public static final String ACTION_PLAY_AUDIO = "android.virtualdevice.cts.PLAY_AUDIO";

    /** Tells the activity to record audio for testing. */
    public static final String ACTION_RECORD_AUDIO = "android.virtualdevice.cts.RECORD_AUDIO";

    /** Constants of audio config for testing. */
    public static final int FREQUENCY = 264;
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_COUNT = 1;
    public static final int AMPLITUDE = 32767;
    public static final int BUFFER_SIZE_IN_BYTES = 65536;

    public static class CapturedAudio {
        private final int mSamplingRate;
        private final int mChannelCount;
        private final ByteBuffer mCapturedData;

        public CapturedAudio(AudioRecord audioRecord) {
            mSamplingRate = audioRecord.getSampleRate();
            mChannelCount = audioRecord.getChannelCount();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_IN_BYTES).order(
                    ByteOrder.nativeOrder());
            while (true) {
                // Read the first buffer with non-zero data
                byteBuffer.clear();
                int bytesRead = audioRecord.read(byteBuffer, BUFFER_SIZE_IN_BYTES);
                if (bytesRead == 0 || isAllZero(byteBuffer)) {
                    continue;
                }
                mCapturedData = byteBuffer;
                break;
            }
        }

        public CapturedAudio(AudioCapture audioCapture, AudioFormat audioFormat) {
            mSamplingRate = audioFormat.getSampleRate();
            mChannelCount = audioFormat.getChannelCount();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_IN_BYTES).order(
                    ByteOrder.nativeOrder());
            while (true) {
                // Read the first buffer with non-zero data
                byteBuffer.clear();
                int bytesRead = audioCapture.read(byteBuffer, BUFFER_SIZE_IN_BYTES);
                if (bytesRead == 0 || isAllZero(byteBuffer)) {
                    continue;
                }
                mCapturedData = byteBuffer;
                break;
            }
        }

        public double getPowerSpectrum(int frequency) {
            return getCapturedPowerSpectrum(mSamplingRate, mChannelCount, mCapturedData, frequency);
        }
    }

    public static int computeNumSamples(int timeMs, int samplingRate, int channelCount) {
        return (int) ((long) timeMs * samplingRate * channelCount / 1000);
    }

    public static ByteBuffer createAudioData(int samplingRate, int numSamples, int channelCount,
            double signalFrequencyHz, float amplitude) {
        ByteBuffer playBuffer =
                ByteBuffer.allocateDirect(numSamples * 2).order(ByteOrder.nativeOrder());
        final double multiplier = 2f * Math.PI * signalFrequencyHz / samplingRate;
        for (int i = 0; i < numSamples; ) {
            double vDouble = amplitude * Math.sin(multiplier * (i / channelCount));
            short v = (short) vDouble;
            for (int c = 0; c < channelCount; c++) {
                playBuffer.putShort(i * 2, v);
                i++;
            }
        }
        return playBuffer;
    }

    public static double getCapturedPowerSpectrum(
            int samplingFreq, int channelCount, ByteBuffer capturedData,
            int expectedSignalFreq) {
        double power = 0;
        int length = capturedData.remaining() / 2;  // PCM16, so 2 bytes for each
        for (int i = 0; i < channelCount; i++) {
            // Get the power in that channel
            double goertzel = goertzel(
                    expectedSignalFreq,
                    samplingFreq,
                    capturedData,
                    /* offset= */ i,
                    length,
                    channelCount);
            power += goertzel / channelCount;
        }
        return power;
    }

    /**
     * Computes the relative power of a given frequency within a frame of the signal.
     * See: http://en.wikipedia.org/wiki/Goertzel_algorithm
     */
    private static double goertzel(int signalFreq, int samplingFreq,
            ByteBuffer samples, int offset, int length, int stride) {
        final int n = length / stride;
        final double coeff = Math.cos(signalFreq * 2 * Math.PI / samplingFreq) * 2;
        double s1 = 0;
        double s2 = 0;
        double rms = 0;
        for (int i = 0; i < n; i++) {
            double hamming = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (n - 1));
            double x = samples.getShort(i * 2 * stride + offset) * hamming; // apply hamming window
            double s = x + coeff * s1 - s2;
            s2 = s1;
            s1 = s;
            rms += x * x;
        }
        rms = Math.sqrt(rms / n);
        double magnitude = s2 * s2 + s1 * s1 - coeff * s1 * s2;
        return Math.sqrt(magnitude) / n / rms;
    }

    private static boolean isAllZero(ByteBuffer byteBuffer) {
        int position = byteBuffer.position();
        int limit = byteBuffer.limit();
        for (int i = position; i < limit; i += 2) {
            if (byteBuffer.getShort(i) != 0) {
                return false;
            }
        }
        return true;
    }
}
