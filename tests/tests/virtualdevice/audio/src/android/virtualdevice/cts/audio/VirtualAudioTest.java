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
package android.virtualdevice.cts;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CAPTURE_AUDIO_OUTPUT;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.REAL_GET_TASKS;
import static android.Manifest.permission.WAKE_LOCK;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;
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
import static android.virtualdevice.cts.common.AudioHelper.AMPLITUDE;
import static android.virtualdevice.cts.common.AudioHelper.BUFFER_SIZE_IN_BYTES;
import static android.virtualdevice.cts.common.AudioHelper.BYTE_ARRAY;
import static android.virtualdevice.cts.common.AudioHelper.BYTE_BUFFER;
import static android.virtualdevice.cts.common.AudioHelper.BYTE_VALUE;
import static android.virtualdevice.cts.common.AudioHelper.CHANNEL_COUNT;
import static android.virtualdevice.cts.common.AudioHelper.FLOAT_ARRAY;
import static android.virtualdevice.cts.common.AudioHelper.FLOAT_VALUE;
import static android.virtualdevice.cts.common.AudioHelper.FREQUENCY;
import static android.virtualdevice.cts.common.AudioHelper.NUMBER_OF_SAMPLES;
import static android.virtualdevice.cts.common.AudioHelper.SAMPLE_RATE;
import static android.virtualdevice.cts.common.AudioHelper.SHORT_ARRAY;
import static android.virtualdevice.cts.common.AudioHelper.SHORT_VALUE;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.audio.VirtualAudioDevice.AudioConfigurationChangeCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.ActivityResultReceiver;
import android.virtualdevice.cts.common.AudioHelper;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            REAL_GET_TASKS,
            WAKE_LOCK,
            MODIFY_AUDIO_ROUTING,
            CAPTURE_AUDIO_OUTPUT);
    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private VirtualAudioDevice mVirtualAudioDevice;

    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock
    private AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;
    @Mock
    private ActivityResultReceiver.Callback mActivityResultCallback;
    @Mock
    private AudioActivity.ResultCallback mAudioResultCallback;
    @Captor
    private ArgumentCaptor<Intent> mIntentCaptor;

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
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        assumeFalse("Skipping test: not supported on automotive", isAutomotive());
        // TODO(b/261155110): Re-enable tests once freeform mode is supported in Virtual Display.
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));

        VirtualDeviceManager vdm = context.getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = vdm.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(VIRTUAL_DISPLAY_FLAG_TRUSTED)
                        .build(),
                Runnable::run,
                mVirtualDisplayCallback);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mVirtualAudioDevice != null) {
            mVirtualAudioDevice.close();
        }
    }

    @Test
    public void audioCapture_createCorrectly() {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
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
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
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
    @FlakyTest(bugId = 292966437)
    public void audioCapture_receivesAudioConfigurationChangeCallback() {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, mAudioConfigurationChangeCallback);
        AudioFormat audioFormat = createCaptureFormat(ENCODING_PCM_16BIT);
        mVirtualAudioDevice.startAudioCapture(audioFormat);

        AudioActivity activity = startAudioActivity(mVirtualDisplay);
        activity.playAudio(BYTE_BUFFER);
        verify(mAudioResultCallback, timeout(5000)).onCompleted();
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onPlaybackConfigChanged(any());
        stopAudioActivity(activity);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_receivesAudioConfigurationChangeCallback() {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, mAudioConfigurationChangeCallback);
        AudioFormat audioFormat = createInjectionFormat(ENCODING_PCM_16BIT);
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(audioFormat);

        AudioActivity activity = startAudioActivity(mVirtualDisplay);
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
    @FlakyTest(bugId = 292966437)
    public void audioCapture_readByteBuffer_shouldCaptureAppPlaybackFrequency() {
        runAudioCaptureTest(BYTE_BUFFER, /* readMode= */ -1);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioCapture_readByteBufferBlocking_shouldCaptureAppPlaybackFrequency() {
        runAudioCaptureTest(BYTE_BUFFER, /* readMode= */ READ_BLOCKING);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioCapture_readByteArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(BYTE_ARRAY, /* readMode= */ -1);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioCapture_readByteArrayBlocking_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(BYTE_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioCapture_readShortArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(SHORT_ARRAY, /* readMode= */ -1);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioCapture_readShortArrayBlocking_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(SHORT_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioCapture_readFloatArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(FLOAT_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_writeByteBuffer_appShouldRecordInjectedFrequency() {
        runAudioInjectionTest(BYTE_BUFFER, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 0);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_writeByteBufferWithTimestamp_appShouldRecordInjectedFrequency() {
        runAudioInjectionTest(BYTE_BUFFER, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 50);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_writeByteArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(BYTE_ARRAY, /* writeMode= */ -1, /* timestamp= */ 0);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_writeByteArrayBlocking_appShouldRecordInjectedData() {
        runAudioInjectionTest(BYTE_ARRAY, /* writeMode= */ WRITE_BLOCKING, /* timestamp= */
                0);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_writeShortArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(SHORT_ARRAY, /* writeMode= */ -1, /* timestamp= */ 0);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_writeShortArrayBlocking_appShouldRecordInjectedData() {
        runAudioInjectionTest(SHORT_ARRAY, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 0);
    }

    @Test
    @FlakyTest(bugId = 292966437)
    public void audioInjection_writeFloatArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(FLOAT_ARRAY, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 0);
    }

    private boolean isAutomotive() {
        return getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private void runAudioCaptureTest(@AudioHelper.DataType int dataType, int readMode) {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
        int encoding = dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(
                createCaptureFormat(encoding));

        AudioActivity audioActivity = startAudioActivity(mVirtualDisplay);
        audioActivity.playAudio(dataType);

        AudioHelper.CapturedAudio capturedAudio = null;
        switch (dataType) {
            case BYTE_BUFFER:
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_IN_BYTES).order(
                        ByteOrder.nativeOrder());
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, byteBuffer, readMode);
                assertThat(capturedAudio.getPowerSpectrum(FREQUENCY + 100))
                        .isLessThan(POWER_THRESHOLD_FOR_ABSENT);
                assertThat(capturedAudio.getPowerSpectrum(FREQUENCY))
                        .isGreaterThan(POWER_THRESHOLD_FOR_PRESENT);
                break;
            case BYTE_ARRAY:
                byte[] byteArray = new byte[BUFFER_SIZE_IN_BYTES];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, byteArray, readMode);
                assertThat(capturedAudio.getByteValue()).isEqualTo(BYTE_VALUE);
                break;
            case SHORT_ARRAY:
                short[] shortArray = new short[BUFFER_SIZE_IN_BYTES / 2];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, shortArray, readMode);
                assertThat(capturedAudio.getShortValue()).isEqualTo(SHORT_VALUE);
                break;
            case FLOAT_ARRAY:
                float[] floatArray = new float[BUFFER_SIZE_IN_BYTES / 4];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, floatArray, readMode);
                float roundOffError = Math.abs(capturedAudio.getFloatValue() - FLOAT_VALUE);
                assertThat(roundOffError).isLessThan(0.001f);
                break;
        }

        verify(mAudioResultCallback, timeout(5000)).onCompleted();
        stopAudioActivity(audioActivity);
    }

    private void runAudioInjectionTest(@AudioHelper.DataType int dataType, int writeMode,
            long timestamp) {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
        int encoding = dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(
                createInjectionFormat(encoding));

        AudioActivity audioActivity = startAudioActivity(mVirtualDisplay);
        audioActivity.recordAudio(dataType);

        int remaining;
        switch (dataType) {
            case BYTE_BUFFER:
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
                break;
            case BYTE_ARRAY:
                byte[] byteArray = new byte[NUMBER_OF_SAMPLES];
                for (int i = 0; i < byteArray.length; i++) {
                    byteArray[i] = BYTE_VALUE;
                }
                remaining = byteArray.length;
                while (remaining > 0) {
                    if (writeMode == WRITE_BLOCKING || writeMode == WRITE_NON_BLOCKING) {
                        remaining -= audioInjection.write(byteArray, 0, byteArray.length,
                                writeMode);
                    } else {
                        remaining -= audioInjection.write(byteArray, 0, byteArray.length);
                    }
                }
                break;
            case SHORT_ARRAY:
                short[] shortArray = new short[NUMBER_OF_SAMPLES];
                for (int i = 0; i < shortArray.length; i++) {
                    shortArray[i] = SHORT_VALUE;
                }
                remaining = shortArray.length;
                while (remaining > 0) {
                    if (writeMode == WRITE_BLOCKING || writeMode == WRITE_NON_BLOCKING) {
                        remaining -= audioInjection.write(shortArray, 0, shortArray.length,
                                writeMode);
                    } else {
                        remaining -= audioInjection.write(shortArray, 0, shortArray.length);
                    }
                }
                break;
            case FLOAT_ARRAY:
                float[] floatArray = new float[NUMBER_OF_SAMPLES];
                for (int i = 0; i < floatArray.length; i++) {
                    floatArray[i] = FLOAT_VALUE;
                }
                remaining = floatArray.length;
                while (remaining > 0) {
                    remaining -= audioInjection.write(floatArray, 0, floatArray.length, writeMode);
                }
                break;
        }

        verify(mAudioResultCallback, timeout(5000)).onCompleted();

        switch (dataType) {
            case BYTE_BUFFER:
                assertThat(audioActivity.mPowerSpectrumNotFrequency).isLessThan(
                        POWER_THRESHOLD_FOR_ABSENT);
                assertThat(audioActivity.mPowerSpectrumAtFrequency).isGreaterThan(
                        POWER_THRESHOLD_FOR_PRESENT);
                break;
            case BYTE_ARRAY:
                assertThat(audioActivity.mLastRecordedNonZeroByteValue).isEqualTo(BYTE_VALUE);
                break;
            case SHORT_ARRAY:
                assertThat(audioActivity.mLastRecordedNonZeroShortValue).isEqualTo(SHORT_VALUE);
                break;
            case FLOAT_ARRAY:
                float floatValue = audioActivity.mLastRecordedNonZeroFloatValue;
                float roundOffError = Math.abs(floatValue - FLOAT_VALUE);
                assertThat(roundOffError).isLessThan(0.001f);
                break;
        }

        stopAudioActivity(audioActivity);
    }

    private AudioActivity startAudioActivity(VirtualDisplay virtualDisplay) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Bundle options = createActivityOptions(virtualDisplay.getDisplay().getDisplayId());
        AudioActivity audioActivity = (AudioActivity) instrumentation.startActivitySync(
                new Intent(getApplicationContext(), AudioActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                options);
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
                case BYTE_BUFFER:
                    playAudioFromByteBuffer(audioTrack);
                    break;
                case BYTE_ARRAY:
                    playAudioFromByteArray(audioTrack);
                    break;
                case SHORT_ARRAY:
                    playAudioFromShortArray(audioTrack);
                    break;
                case FLOAT_ARRAY:
                    playAudioFromFloatArray(audioTrack);
                    break;
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
                case BYTE_BUFFER:
                    recordAudioToByteBuffer(audioRecord);
                    break;
                case BYTE_ARRAY:
                    recordAudioToByteArray(audioRecord);
                    break;
                case SHORT_ARRAY:
                    recordAudioToShortArray(audioRecord);
                    break;
                case FLOAT_ARRAY:
                    recordAudioToFloatArray(audioRecord);
                    break;
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
                for (int i = 0; i < audioData.length; i++) {
                    audioData[i] = BYTE_VALUE;
                }

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
                for (int i = 0; i < audioData.length; i++) {
                    audioData[i] = SHORT_VALUE;
                }

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
                for (int i = 0; i < audioData.length; i++) {
                    audioData[i] = FLOAT_VALUE;
                }

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
                for (int i = 0; i < audioData.length; i++) {
                    if (audioData[i] == BYTE_VALUE) {
                        value = audioData[i];
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
                for (int i = 0; i < audioData.length; i++) {
                    if (audioData[i] == SHORT_VALUE) {
                        value = audioData[i];
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
                for (int i = 0; i < audioData.length; i++) {
                    float roundOffDiff = Math.abs(audioData[i] - FLOAT_VALUE);
                    if (roundOffDiff < 0.001f) {
                        value = audioData[i];
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
