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
package android.virtualdevice.cts.audio;

import static android.Manifest.permission.CAPTURE_AUDIO_OUTPUT;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioFormat.ENCODING_PCM_FLOAT;
import static android.media.AudioRecord.READ_BLOCKING;
import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static android.media.AudioRecord.RECORDSTATE_STOPPED;
import static android.media.AudioTrack.PLAYSTATE_PLAYING;
import static android.media.AudioTrack.PLAYSTATE_STOPPED;
import static android.media.AudioTrack.WRITE_BLOCKING;
import static android.media.AudioTrack.WRITE_NON_BLOCKING;
import static android.virtualdevice.cts.audio.AudioHelper.AMPLITUDE;
import static android.virtualdevice.cts.audio.AudioHelper.BUFFER_SIZE_IN_BYTES;
import static android.virtualdevice.cts.audio.AudioHelper.BYTE_ARRAY;
import static android.virtualdevice.cts.audio.AudioHelper.BYTE_BUFFER;
import static android.virtualdevice.cts.audio.AudioHelper.BYTE_VALUE;
import static android.virtualdevice.cts.audio.AudioHelper.CHANNEL_COUNT;
import static android.virtualdevice.cts.audio.AudioHelper.FLOAT_ARRAY;
import static android.virtualdevice.cts.audio.AudioHelper.FLOAT_VALUE;
import static android.virtualdevice.cts.audio.AudioHelper.FREQUENCY;
import static android.virtualdevice.cts.audio.AudioHelper.NUMBER_OF_SAMPLES;
import static android.virtualdevice.cts.audio.AudioHelper.SAMPLE_RATE;
import static android.virtualdevice.cts.audio.AudioHelper.SHORT_ARRAY;
import static android.virtualdevice.cts.audio.AudioHelper.SHORT_VALUE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.audio.VirtualAudioDevice.AudioConfigurationChangeCallback;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Tests for injection and capturing of audio from streamed apps
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualAudioTest {
    /**
     * Captured signal should be mostly single frequency and power of that frequency should be over
     * this much of total power.
     */
    public static final double POWER_THRESHOLD_FOR_PRESENT = 0.4f;

    /**
     * The other signals should have very weak power and should not exceed this value
     */
    public static final double POWER_THRESHOLD_FOR_ABSENT = 0.02f;

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            MODIFY_AUDIO_ROUTING, CAPTURE_AUDIO_OUTPUT);

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private VirtualAudioDevice mVirtualAudioDevice;

    @Mock
    private AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;
    @Mock
    private AudioActivity.ResultCallback mAudioResultCallback;

    private static AudioFormat createCaptureFormat(int encoding) {
        return new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(encoding)
                .setChannelMask(CHANNEL_IN_MONO)
                .build();
    }

    private static AudioFormat createInjectionFormat(int encoding) {
        return new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(encoding)
                .setChannelMask(CHANNEL_IN_MONO)
                .build();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        mVirtualDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(
                mVirtualDevice, VirtualDeviceRule.TRUSTED_VIRTUAL_DISPLAY_CONFIG);
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, Runnable::run, mAudioConfigurationChangeCallback);
    }

    @Test
    public void audioCapture_createCorrectly() {
        AudioFormat audioFormat = createCaptureFormat(ENCODING_PCM_16BIT);
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(audioFormat);
        assertThat(audioCapture).isNotNull();
        assertThat(audioCapture.getFormat()).isEqualTo(audioFormat);
        assertThat(mVirtualAudioDevice.getAudioCapture()).isEqualTo(audioCapture);

        audioCapture.startRecording();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_RECORDING);
        audioCapture.stop();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_STOPPED);
    }

    @Test
    public void audioInjection_createCorrectly() {
        AudioFormat audioFormat = createInjectionFormat(ENCODING_PCM_16BIT);
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(audioFormat);
        assertThat(audioInjection).isNotNull();
        assertThat(audioInjection.getFormat()).isEqualTo(audioFormat);
        assertThat(mVirtualAudioDevice.getAudioInjection()).isEqualTo(audioInjection);

        audioInjection.play();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_PLAYING);
        audioInjection.stop();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_STOPPED);
    }

    @Test
    public void audioInjection_createWithNull() {
        assertThrows(NullPointerException.class, () -> mVirtualDevice.createVirtualAudioDevice(
                null, /* executor= */ null, /* callback= */ null));
    }

    @Test
    public void audioCapture_receivesAudioConfigurationChangeCallback() {
        AudioFormat audioFormat = createCaptureFormat(ENCODING_PCM_16BIT);
        mVirtualAudioDevice.startAudioCapture(audioFormat);

        AudioActivity activity = startAudioActivity();
        activity.playAudio(BYTE_BUFFER);
        verify(mAudioResultCallback, timeout(5000)).onCompleted();
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onPlaybackConfigChanged(any());
        stopAudioActivity(activity);
    }

    @Test
    public void audioInjection_receivesAudioConfigurationChangeCallback() {
        AudioFormat audioFormat = createInjectionFormat(ENCODING_PCM_16BIT);
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(audioFormat);

        AudioActivity activity = startAudioActivity();
        activity.recordAudio(BYTE_BUFFER);

        ByteBuffer byteBuffer = AudioHelper.createAudioData(
                SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
        int remaining = byteBuffer.remaining();
        while (remaining > 0) {
            remaining -= audioInjection.write(byteBuffer, byteBuffer.remaining(), WRITE_BLOCKING);
        }

        verify(mAudioResultCallback, timeout(5000)).onCompleted();
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onRecordingConfigChanged(any());
        stopAudioActivity(activity);
    }

    @Test
    public void audioCapture_readByteBuffer_shouldCaptureAppPlaybackFrequency() {
        runAudioCaptureTest(BYTE_BUFFER, /* readMode= */ -1);
    }

    @Test
    public void audioCapture_readByteBufferBlocking_shouldCaptureAppPlaybackFrequency() {
        runAudioCaptureTest(BYTE_BUFFER, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioCapture_readByteArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(BYTE_ARRAY, /* readMode= */ -1);
    }

    @Test
    public void audioCapture_readByteArrayBlocking_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(BYTE_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioCapture_readShortArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(SHORT_ARRAY, /* readMode= */ -1);
    }

    @Test
    public void audioCapture_readShortArrayBlocking_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(SHORT_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioCapture_readFloatArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(FLOAT_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioInjection_writeByteBuffer_appShouldRecordInjectedFrequency() {
        runAudioInjectionTest(BYTE_BUFFER, /* writeMode= */ WRITE_BLOCKING, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeByteBufferWithTimestamp_appShouldRecordInjectedFrequency() {
        runAudioInjectionTest(BYTE_BUFFER, /* writeMode= */ WRITE_BLOCKING, /* timestamp= */ 50);
    }

    @Test
    public void audioInjection_writeByteArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(BYTE_ARRAY, /* writeMode= */ -1, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeByteArrayBlocking_appShouldRecordInjectedData() {
        runAudioInjectionTest(BYTE_ARRAY, /* writeMode= */ WRITE_BLOCKING, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeShortArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(SHORT_ARRAY, /* writeMode= */ -1, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeShortArrayBlocking_appShouldRecordInjectedData() {
        runAudioInjectionTest(SHORT_ARRAY, /* writeMode= */ WRITE_BLOCKING, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeFloatArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(FLOAT_ARRAY, /* writeMode= */ WRITE_BLOCKING, /* timestamp= */ 0);
    }

    private void runAudioCaptureTest(@AudioHelper.DataType int dataType, int readMode) {
        // Automotive has its own audio policies that don't play well with the VDM-created ones.
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        int encoding = dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(
                createCaptureFormat(encoding));

        AudioActivity audioActivity = startAudioActivity();
        audioActivity.playAudio(dataType);

        AudioHelper.CapturedAudio capturedAudio;
        switch (dataType) {
            case BYTE_BUFFER -> {
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_IN_BYTES).order(
                        ByteOrder.nativeOrder());
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, byteBuffer, readMode);
                assertThat(capturedAudio.getPowerSpectrum(FREQUENCY + 100))
                        .isLessThan(POWER_THRESHOLD_FOR_ABSENT);
                assertThat(capturedAudio.getPowerSpectrum(FREQUENCY))
                        .isGreaterThan(POWER_THRESHOLD_FOR_PRESENT);
            }
            case BYTE_ARRAY -> {
                byte[] byteArray = new byte[BUFFER_SIZE_IN_BYTES];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, byteArray, readMode);
                assertThat(capturedAudio.getByteValue()).isEqualTo(BYTE_VALUE);
            }
            case SHORT_ARRAY -> {
                short[] shortArray = new short[BUFFER_SIZE_IN_BYTES / 2];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, shortArray, readMode);
                assertThat(capturedAudio.getShortValue()).isEqualTo(SHORT_VALUE);
            }
            case FLOAT_ARRAY -> {
                float[] floatArray = new float[BUFFER_SIZE_IN_BYTES / 4];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, floatArray, readMode);
                float roundOffError = Math.abs(capturedAudio.getFloatValue() - FLOAT_VALUE);
                assertThat(roundOffError).isLessThan(0.001f);
            }
        }

        verify(mAudioResultCallback, timeout(5000)).onCompleted();
        stopAudioActivity(audioActivity);
    }

    private void runAudioInjectionTest(@AudioHelper.DataType int dataType, int writeMode,
            long timestamp) {
        int encoding = dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(
                createInjectionFormat(encoding));

        AudioActivity audioActivity = startAudioActivity();
        audioActivity.recordAudio(dataType);

        int remaining;
        switch (dataType) {
            case BYTE_BUFFER -> {
                ByteBuffer byteBuffer = AudioHelper.createAudioData(
                        SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
                remaining = byteBuffer.remaining();
                while (remaining > 0) {
                    if (timestamp != 0) {
                        remaining -= audioInjection.write(byteBuffer, byteBuffer.remaining(),
                                writeMode, timestamp);
                    } else {
                        remaining -= audioInjection.write(byteBuffer, byteBuffer.remaining(),
                                writeMode);
                    }
                }
            }
            case BYTE_ARRAY -> {
                byte[] byteArray = new byte[NUMBER_OF_SAMPLES];
                Arrays.fill(byteArray, BYTE_VALUE);
                remaining = byteArray.length;
                while (remaining > 0) {
                    if (writeMode == WRITE_BLOCKING || writeMode == WRITE_NON_BLOCKING) {
                        remaining -= audioInjection.write(byteArray, 0, byteArray.length,
                                writeMode);
                    } else {
                        remaining -= audioInjection.write(byteArray, 0, byteArray.length);
                    }
                }
            }
            case SHORT_ARRAY -> {
                short[] shortArray = new short[NUMBER_OF_SAMPLES];
                Arrays.fill(shortArray, SHORT_VALUE);
                remaining = shortArray.length;
                while (remaining > 0) {
                    if (writeMode == WRITE_BLOCKING || writeMode == WRITE_NON_BLOCKING) {
                        remaining -= audioInjection.write(shortArray, 0, shortArray.length,
                                writeMode);
                    } else {
                        remaining -= audioInjection.write(shortArray, 0, shortArray.length);
                    }
                }
            }
            case FLOAT_ARRAY -> {
                float[] floatArray = new float[NUMBER_OF_SAMPLES];
                Arrays.fill(floatArray, FLOAT_VALUE);
                remaining = floatArray.length;
                while (remaining > 0) {
                    remaining -= audioInjection.write(floatArray, 0, floatArray.length, writeMode);
                }
            }
        }

        verify(mAudioResultCallback, timeout(5000)).onCompleted();

        switch (dataType) {
            case BYTE_BUFFER -> {
                assertThat(audioActivity.mPowerSpectrumNotFrequency).isLessThan(
                        POWER_THRESHOLD_FOR_ABSENT);
                assertThat(audioActivity.mPowerSpectrumAtFrequency).isGreaterThan(
                        POWER_THRESHOLD_FOR_PRESENT);
            }
            case BYTE_ARRAY -> assertThat(audioActivity.mLastRecordedNonZeroByteValue).isEqualTo(
                    BYTE_VALUE);
            case SHORT_ARRAY -> assertThat(audioActivity.mLastRecordedNonZeroShortValue).isEqualTo(
                    SHORT_VALUE);
            case FLOAT_ARRAY -> {
                float floatValue = audioActivity.mLastRecordedNonZeroFloatValue;
                float roundOffError = Math.abs(floatValue - FLOAT_VALUE);
                assertThat(roundOffError).isLessThan(0.001f);
            }
        }

        stopAudioActivity(audioActivity);
    }

    private AudioActivity startAudioActivity() {
        AudioActivity audioActivity = mVirtualDeviceRule.startActivityOnDisplaySync(
                mVirtualDisplay, AudioActivity.class);
        audioActivity.registerResultCallback(mAudioResultCallback);
        return audioActivity;
    }

    private void stopAudioActivity(AudioActivity activity) {
        activity.clear();
        activity.finish();
    }

    public static class AudioActivity extends Activity {

        float mLastRecordedNonZeroFloatValue;
        short mLastRecordedNonZeroShortValue;
        byte mLastRecordedNonZeroByteValue;
        double mPowerSpectrumNotFrequency;
        double mPowerSpectrumAtFrequency;
        private ResultCallback mCallback;

        void registerResultCallback(ResultCallback callback) {
            mCallback = callback;
        }

        void clear() {
            mCallback = null;
        }

        void playAudio(int dataType) {
            int playEncoding =
                    dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
            int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_MONO,
                    playEncoding);
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    CHANNEL_OUT_MONO, playEncoding, bufferSize, AudioTrack.MODE_STREAM);
            audioTrack.play();
            switch (dataType) {
                case BYTE_BUFFER -> playAudioFromByteBuffer(audioTrack);
                case BYTE_ARRAY -> playAudioFromByteArray(audioTrack);
                case SHORT_ARRAY -> playAudioFromShortArray(audioTrack);
                case FLOAT_ARRAY -> playAudioFromFloatArray(audioTrack);
            }
        }

        void recordAudio(int dataType) {
            int recordEncoding =
                    dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, recordEncoding, BUFFER_SIZE_IN_BYTES);
            audioRecord.startRecording();
            switch (dataType) {
                case BYTE_BUFFER -> recordAudioToByteBuffer(audioRecord);
                case BYTE_ARRAY -> recordAudioToByteArray(audioRecord);
                case SHORT_ARRAY -> recordAudioToShortArray(audioRecord);
                case FLOAT_ARRAY -> recordAudioToFloatArray(audioRecord);
            }
        }

        private void playAudioFromByteBuffer(AudioTrack audioTrack) {
            // Write to the audio track asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                ByteBuffer audioData = AudioHelper.createAudioData(
                        SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);

                int remainingSamples = NUMBER_OF_SAMPLES;
                while (remainingSamples > 0) {
                    remainingSamples -= audioTrack.write(audioData, audioData.remaining(),
                            AudioTrack.WRITE_BLOCKING);
                }
                audioTrack.release();

                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        private void playAudioFromByteArray(AudioTrack audioTrack) {
            // Write to the audio track asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                byte[] audioData = new byte[NUMBER_OF_SAMPLES];
                Arrays.fill(audioData, BYTE_VALUE);

                int remainingSamples = audioData.length;
                while (remainingSamples > 0) {
                    remainingSamples -= audioTrack.write(audioData, 0, audioData.length);
                }
                audioTrack.release();

                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        private void playAudioFromShortArray(AudioTrack audioTrack) {
            // Write to the audio track asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                short[] audioData = new short[NUMBER_OF_SAMPLES];
                Arrays.fill(audioData, SHORT_VALUE);

                int remainingSamples = audioData.length;
                while (remainingSamples > 0) {
                    remainingSamples -= audioTrack.write(audioData, 0, audioData.length);
                }
                audioTrack.release();

                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        private void playAudioFromFloatArray(AudioTrack audioTrack) {
            // Write to the audio track asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                float[] audioData = new float[NUMBER_OF_SAMPLES];
                Arrays.fill(audioData, FLOAT_VALUE);

                int remainingSamples = audioData.length;
                while (remainingSamples > 0) {
                    remainingSamples -= audioTrack.write(audioData, 0, audioData.length,
                            AudioTrack.WRITE_BLOCKING);
                }
                audioTrack.release();

                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        private void recordAudioToByteBuffer(AudioRecord audioRecord) {
            // Read from the audio record asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                AudioHelper.CapturedAudio capturedAudio = new AudioHelper.CapturedAudio(
                        audioRecord);
                double powerSpectrumNotFrequency = capturedAudio.getPowerSpectrum(FREQUENCY + 100);
                double powerSpectrumAtFrequency = capturedAudio.getPowerSpectrum(FREQUENCY);
                audioRecord.release();

                mPowerSpectrumNotFrequency = powerSpectrumNotFrequency;
                mPowerSpectrumAtFrequency = powerSpectrumAtFrequency;
                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        private void recordAudioToByteArray(AudioRecord audioRecord) {
            // Read from the audio record asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                byte[] audioData = new byte[BUFFER_SIZE_IN_BYTES];
                while (true) {
                    int bytesRead = audioRecord.read(audioData, 0, audioData.length);
                    if (bytesRead == 0) {
                        continue;
                    }
                    break;
                }
                byte value = 0;
                for (byte audioDatum : audioData) {
                    if (audioDatum == BYTE_VALUE) {
                        value = audioDatum;
                        break;
                    }
                }
                audioRecord.release();

                mLastRecordedNonZeroByteValue = value;
                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        private void recordAudioToShortArray(AudioRecord audioRecord) {
            // Read from the audio record asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                short[] audioData = new short[BUFFER_SIZE_IN_BYTES / 2];
                while (true) {
                    int bytesRead = audioRecord.read(audioData, 0, audioData.length);
                    if (bytesRead == 0) {
                        continue;
                    }
                    break;
                }
                short value = 0;
                for (short audioDatum : audioData) {
                    if (audioDatum == SHORT_VALUE) {
                        value = audioDatum;
                        break;
                    }
                }
                audioRecord.release();

                mLastRecordedNonZeroShortValue = value;
                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        private void recordAudioToFloatArray(AudioRecord audioRecord) {
            // Read from the audio record asynchronously to avoid ANRs.
            Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
                float[] audioData = new float[BUFFER_SIZE_IN_BYTES / 4];
                while (true) {
                    int bytesRead = audioRecord.read(audioData, 0, audioData.length, READ_BLOCKING);
                    if (bytesRead == 0) {
                        continue;
                    }
                    break;
                }
                float value = 0f;
                for (float audioDatum : audioData) {
                    float roundOffDiff = Math.abs(audioDatum - FLOAT_VALUE);
                    if (roundOffDiff < 0.001f) {
                        value = audioDatum;
                        break;
                    }
                }
                audioRecord.release();

                mLastRecordedNonZeroFloatValue = value;
                if (mCallback != null) {
                    mCallback.onCompleted();
                }
            });
        }

        interface ResultCallback {
            void onCompleted();
        }
    }
}
