/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.media.cts;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.cts.util.CtsAndroidTestCase;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.PlaybackParams;
import android.util.Log;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

// Test the Java AudioTrack low latency related features:
//
// setBufferSizeInFrames()
// getBufferCapacityInFrames()
// ASSUME getMinBufferSize in frames is significantly lower than getBufferCapacityInFrames.
// This gives us room to adjust the sizes.
//
// getUnderrunCount()
// ASSUME normal track will underrun with setBufferSizeInFrames(0).
//
// AudioAttributes.FLAG_LOW_LATENCY
// ASSUME FLAG_LOW_LATENCY reduces output latency by more than 10 msec.
// Warns if not. This can happen if there is no Fast Mixer or if a FastTrack
// is not available.

public class AudioTrackLatencyTest extends CtsAndroidTestCase {
    private String TAG = "AudioTrackLatencyTest";
    private final static long NANOS_PER_MILLISECOND = 1000000L;
    private final static long NANOS_PER_SECOND = NANOS_PER_MILLISECOND * 1000L;

    private void log(String testName, String message) {
        Log.i(TAG, "[" + testName + "] " + message);
    }

    private void logw(String testName, String message) {
        Log.w(TAG, "[" + testName + "] " + message);
    }

    private void loge(String testName, String message) {
        Log.e(TAG, "[" + testName + "] " + message);
    }

    public void testSetBufferSize() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetBufferSize";
        final int TEST_SR = 44100;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);

        // -------- test --------------
        // Initial values
        int bufferCapacity = track.getBufferCapacityInFrames();
        int initialBufferSize = track.getBufferSizeInFrames();
        assertTrue(TEST_NAME, bufferCapacity > 0);
        assertTrue(TEST_NAME, initialBufferSize > 0);
        assertTrue(TEST_NAME, initialBufferSize <= bufferCapacity);

        // set to various values
        int resultNegative = track.setBufferSizeInFrames(-1);
        assertEquals(TEST_NAME + ": negative size", AudioTrack.ERROR_BAD_VALUE, resultNegative);
        assertEquals(TEST_NAME + ": should be unchanged",
                initialBufferSize, track.getBufferSizeInFrames());

        int resultZero = track.setBufferSizeInFrames(0);
        assertTrue(TEST_NAME + ": zero size OK", resultZero > 0);
        assertTrue(TEST_NAME + ": zero size < original", resultZero < initialBufferSize);
        assertEquals(TEST_NAME + ": should match resultZero",
                resultZero, track.getBufferSizeInFrames());

        int resultMax = track.setBufferSizeInFrames(Integer.MAX_VALUE);
        assertTrue(TEST_NAME + ": set MAX_VALUE, >", resultMax > resultZero);
        assertTrue(TEST_NAME + ": set MAX_VALUE, <=", resultMax <= bufferCapacity);
        assertEquals(TEST_NAME + ": should match resultMax",
                resultMax, track.getBufferSizeInFrames());

        int resultMiddle = track.setBufferSizeInFrames(bufferCapacity / 2);
        assertTrue(TEST_NAME + ": set middle, >", resultMiddle > resultZero);
        assertTrue(TEST_NAME + ": set middle, <=", resultMiddle < resultMax);
        assertEquals(TEST_NAME + ": should match resultMiddle",
                resultMiddle, track.getBufferSizeInFrames());

        // -------- tear down --------------
        track.release();
    }

    // Create a track with or without FLAG_LOW_LATENCY
    private AudioTrack createCustomAudioTrack(boolean lowLatency) {
        final String TEST_NAME = "createCustomAudioTrack";
        final int TEST_SR = 48000;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_MUSIC;

        // Start with buffer twice as large as needed.
        int bufferSizeBytes = 2 * AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                .setContentType(TEST_CONTENT_TYPE);
        if (lowLatency) {
            attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY);
        }
        AudioAttributes attributes = attributesBuilder.build();

        // Do not specify the sample rate so we get the optimal rate.
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(TEST_FORMAT)
                .setChannelMask(TEST_CONF)
                .build();
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSizeBytes)
                .build();

        assertTrue(track != null);
        log(TEST_NAME, "Track sample rate = " + track.getSampleRate() + " Hz");
        return track;
    }


    private int checkOutputLowLatency(boolean lowLatency) throws Exception {
        // constants for test
        final String TEST_NAME = "checkOutputLowLatency";
        final int TEST_SAMPLES_PER_FRAME = 2;
        final int TEST_BYTES_PER_SAMPLE = 2;
        final int TEST_NUM_SECONDS = 4;
        final int TEST_FRAMES_PER_BUFFER = 128;
        final double TEST_AMPLITUDE = 0.5;

        final short[] data = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BUFFER,
                TEST_SAMPLES_PER_FRAME, 1, TEST_AMPLITUDE);

        // -------- initialization --------------
        AudioTrack track = createCustomAudioTrack(lowLatency);
        assertTrue(TEST_NAME + " actual SR", track.getSampleRate() > 0);

        // -------- test --------------
        // Play some audio for a few seconds.
        int numSeconds = TEST_NUM_SECONDS;
        int numBuffers = numSeconds * track.getSampleRate() / TEST_FRAMES_PER_BUFFER;
        long framesWritten = 0;
        boolean isPlaying = false;
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
            framesWritten += TEST_FRAMES_PER_BUFFER;
            // prime the buffer a bit before playing
            if (!isPlaying) {
                track.play();
                isPlaying = true;
            }
        }

        // Estimate the latency from the timestamp.
        long timeWritten = System.nanoTime();
        AudioTimestamp timestamp = new AudioTimestamp();
        boolean result = track.getTimestamp(timestamp);
        // FIXME failing LOW_LATENCY case! b/26413951
        assertTrue(TEST_NAME + " did not get a timestamp, lowLatency = "
                + lowLatency, result);

        // Calculate when the last frame written is going to be rendered.
        long framesPending = framesWritten - timestamp.framePosition;
        long timeDelta = framesPending * NANOS_PER_SECOND / track.getSampleRate();
        long timePresented = timestamp.nanoTime + timeDelta;
        long latencyNanos = timePresented - timeWritten;
        int latencyMillis = (int) (latencyNanos / NANOS_PER_MILLISECOND);
        assertTrue(TEST_NAME + " got latencyMillis <= 0 == "
                + latencyMillis, latencyMillis > 0);

        // -------- cleanup --------------
        track.release();

        return latencyMillis;
    }

    // Compare output latency with and without FLAG_LOW_LATENCY.
    public void testOutputLowLatency() throws Exception {
        final String TEST_NAME = "testOutputLowLatency";

        int highLatencyMillis = checkOutputLowLatency(false);
        log(TEST_NAME, "High latency = " + highLatencyMillis + " msec");

        int lowLatencyMillis = checkOutputLowLatency(true);
        log(TEST_NAME, "Low latency = " + lowLatencyMillis + " msec");

        // We are not guaranteed to get a FAST track. Some platforms
        // do not even have a FastMixer. So just warn and not fail.
        if (highLatencyMillis <= (lowLatencyMillis + 10)) {
            logw(TEST_NAME, "high latency should be much higher, "
                    + highLatencyMillis
                    + " vs " + lowLatencyMillis);
        }
    }

    // Verify that no underruns when buffer is >= getMinBufferSize().
    // Verify that we get underruns with buffer at smallest possible size.
    public void testGetUnderrunCount() throws Exception {
        // constants for test
        final String TEST_NAME = "testGetUnderrunCount";
        final int TEST_SR = 44100;
        final int TEST_SAMPLES_PER_FRAME = 2;
        final int TEST_BYTES_PER_SAMPLE = 2;
        final int TEST_NUM_SECONDS = 2;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_FRAMES_PER_BUFFER = 256;
        final int TEST_FRAMES_PER_BLIP = TEST_SR / 8;
        final int TEST_CYCLES_PER_BLIP = 700 * TEST_FRAMES_PER_BLIP / TEST_SR;
        final double TEST_AMPLITUDE = 0.5;

        final short[] data = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BUFFER,
                TEST_SAMPLES_PER_FRAME, 1, TEST_AMPLITUDE);
        final short[] blip = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BLIP,
                TEST_SAMPLES_PER_FRAME, TEST_CYCLES_PER_BLIP, TEST_AMPLITUDE);

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        // Start with buffer twice as large as needed.
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize * 2, TEST_MODE);

        // -------- test --------------
        // Initial values
        int bufferCapacity = track.getBufferCapacityInFrames();
        int initialBufferSize = track.getBufferSizeInFrames();
        int minBuffSizeInFrames = minBuffSize / (TEST_SAMPLES_PER_FRAME * TEST_BYTES_PER_SAMPLE);
        assertTrue(TEST_NAME, bufferCapacity > 0);
        assertTrue(TEST_NAME, initialBufferSize > 0);
        assertTrue(TEST_NAME, initialBufferSize <= bufferCapacity);

        // Play with initial size.
        int underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": initially no underruns", 0, underrunCount1);

        // Prime the buffer.
        while (track.write(data, 0, data.length) == data.length);

        // Start playing
        track.play();
        int numBuffers = TEST_SR / TEST_FRAMES_PER_BUFFER;
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        int underrunCountBase = track.getUnderrunCount();
        int numSeconds = TEST_NUM_SECONDS;
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": no more underruns after initial",
                underrunCountBase, underrunCount1);

        // Play with getMinBufferSize() size.
        int resultMin = track.setBufferSizeInFrames(minBuffSizeInFrames);
        assertTrue(TEST_NAME + ": set minBuff, >", resultMin > 0);
        assertTrue(TEST_NAME + ": set minBuff, <=", resultMin <= initialBufferSize);
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        track.write(blip, 0, blip.length);
        underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": no more underruns at min", underrunCountBase, underrunCount1);

        // Play with ridiculously small size. We want to get underruns so we know that an app
        // can get to the edge of underrunning.
        int resultZero = track.setBufferSizeInFrames(0);
        assertTrue(TEST_NAME + ": zero size OK", resultZero > 0);
        assertTrue(TEST_NAME + ": zero size < original", resultZero < initialBufferSize);
        numSeconds = TEST_NUM_SECONDS / 2; // cuz test takes longer when underflowing
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        // Play for a few seconds or until we get some new underruns.
        for (int i = 0; (i < numBuffers) && ((underrunCount1 - underrunCountBase) < 10); i++) {
            track.write(data, 0, data.length);
            underrunCount1 = track.getUnderrunCount();
        }
        assertTrue(TEST_NAME + ": underruns at zero", underrunCount1 > underrunCountBase);
        int underrunCount2 = underrunCount1;
        // Play for a few seconds or until we get some new underruns.
        for (int i = 0; (i < numBuffers) && ((underrunCount2 - underrunCount1) < 10); i++) {
            track.write(data, 0, data.length);
            underrunCount2 = track.getUnderrunCount();
        }
        assertTrue(TEST_NAME + ": underruns still accumulating", underrunCount2 > underrunCount1);

        // Restore buffer to good size
        numSeconds = TEST_NUM_SECONDS;
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        int resultMax = track.setBufferSizeInFrames(bufferCapacity);
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        // Should have stopped by now.
        underrunCount1 = track.getUnderrunCount();
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        // Counts should match.
        underrunCount2 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": underruns should stop happening",
                underrunCount1, underrunCount2);

        // -------- tear down --------------
        track.release();
    }

    // Verify that we get underruns if we stop writing to the buffer.
    public void testGetUnderrunCountSleep() throws Exception {
        // constants for test
        final String TEST_NAME = "testGetUnderrunCountSleep";
        final int TEST_SR = 48000;
        final int TEST_SAMPLES_PER_FRAME = 2;
        final int TEST_BYTES_PER_SAMPLE = 2;
        final int TEST_NUM_SECONDS = 2;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_FRAMES_PER_BUFFER = 256;
        final int TEST_FRAMES_PER_BLIP = TEST_SR / 8;
        final int TEST_CYCLES_PER_BLIP = 700 * TEST_FRAMES_PER_BLIP / TEST_SR;
        final double TEST_AMPLITUDE = 0.5;

        final short[] data = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BUFFER,
                TEST_SAMPLES_PER_FRAME, 1, TEST_AMPLITUDE);
        final short[] blip = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BLIP,
                TEST_SAMPLES_PER_FRAME, TEST_CYCLES_PER_BLIP, TEST_AMPLITUDE);

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        // Start with buffer twice as large as needed.
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize * 2, TEST_MODE);

        // -------- test --------------
        // Initial values
        int minBuffSizeInFrames = minBuffSize / (TEST_SAMPLES_PER_FRAME * TEST_BYTES_PER_SAMPLE);

        int underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": initially no underruns", 0, underrunCount1);

        // Prime the buffer.
        while (track.write(data, 0, data.length) == data.length);

        // Start playing
        track.play();
        int numBuffers = TEST_SR / TEST_FRAMES_PER_BUFFER;
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        int underrunCountBase = track.getUnderrunCount();
        int numSeconds = TEST_NUM_SECONDS;
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": no more underruns after initial",
                underrunCountBase, underrunCount1);

        // Sleep and force underruns.
        track.write(blip, 0, blip.length);
        for (int i = 0; i < 10; i++) {
            track.write(data, 0, data.length);
            Thread.sleep(500);  // ========================= SLEEP! ===========
        }
        track.write(blip, 0, blip.length);
        underrunCount1 = track.getUnderrunCount();
        assertTrue(TEST_NAME + ": expect underruns after sleep, #ur="
                + underrunCount1,
                underrunCountBase < underrunCount1);

        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }

        // Should have stopped by now.
        underrunCount1 = track.getUnderrunCount();
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        // Counts should match.
        int underrunCount2 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": underruns should stop happening",
                underrunCount1, underrunCount2);

        // -------- tear down --------------
        track.release();
    }
}
