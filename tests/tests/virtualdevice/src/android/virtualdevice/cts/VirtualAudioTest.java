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
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.virtualdevice.cts.common.ActivityResultReceiver.EXTRA_POWER_SPECTRUM_AT_FREQUENCY;
import static android.virtualdevice.cts.common.ActivityResultReceiver.EXTRA_POWER_SPECTRUM_NOT_FREQUENCY;
import static android.virtualdevice.cts.common.AudioHelper.ACTION_PLAY_AUDIO;
import static android.virtualdevice.cts.common.AudioHelper.ACTION_RECORD_AUDIO;
import static android.virtualdevice.cts.common.AudioHelper.AMPLITUDE;
import static android.virtualdevice.cts.common.AudioHelper.CHANNEL_COUNT;
import static android.virtualdevice.cts.common.AudioHelper.FREQUENCY;
import static android.virtualdevice.cts.common.AudioHelper.SAMPLE_RATE;
import static android.virtualdevice.cts.util.TestAppHelper.MAIN_ACTIVITY_COMPONENT;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createActivityOptions;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

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
import android.media.AudioTrack;
import android.virtualdevice.cts.common.ActivityResultReceiver;
import android.virtualdevice.cts.common.AudioHelper;
import android.virtualdevice.cts.util.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
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

/**
 * Tests for injection and capturing of audio from streamed apps
 */
@RunWith(AndroidJUnit4.class)
public class VirtualAudioTest {
    /**
     * Captured signal should be mostly single frequency and power of that frequency should be
     * over this much of total power.
     */
    public static final double POWER_THRESHOLD_FOR_PRESENT = 0.4f;

    /**
     * The other signals should have very weak power and should not exceed this value
     */
    public static final double POWER_THRESHOLD_FOR_ABSENT = 0.02f;

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();
    private static final AudioFormat AUDIO_CAPTURE_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(ENCODING_PCM_16BIT)
                    .setChannelMask(CHANNEL_IN_MONO)
                    .build();
    private static final AudioFormat AUDIO_INJECTION_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(ENCODING_PCM_16BIT)
                    .setChannelMask(CHANNEL_OUT_MONO)
                    .build();
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
    @Captor
    private ArgumentCaptor<Intent> mIntentCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        assumeTrue(
                context.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));

        VirtualDeviceManager vdm = context.getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = vdm.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ VIRTUAL_DISPLAY_FLAG_TRUSTED,
                Runnable::run,
                mVirtualDisplayCallback);
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, mAudioConfigurationChangeCallback);
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
    public void audioCapture_shouldCaptureAppPlaybackFrequency() {
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(AUDIO_CAPTURE_FORMAT);
        assertThat(audioCapture).isNotNull();

        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(
                createPlayAudioIntent(), createActivityOptions(mVirtualDisplay));
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onPlaybackConfigChanged(any());

        AudioHelper.CapturedAudio capturedAudio = new AudioHelper.CapturedAudio(audioCapture,
                AUDIO_CAPTURE_FORMAT);
        assertThat(capturedAudio.getPowerSpectrum(FREQUENCY + 100))
                .isLessThan(POWER_THRESHOLD_FOR_ABSENT);
        assertThat(capturedAudio.getPowerSpectrum(FREQUENCY))
                .isGreaterThan(POWER_THRESHOLD_FOR_PRESENT);
    }

    @Test
    public void audioInjection_appShouldRecordInjectedFrequency() {
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(
                AUDIO_INJECTION_FORMAT);
        assertThat(audioInjection).isNotNull();

        ActivityResultReceiver activityResultReceiver = new ActivityResultReceiver(
                getApplicationContext());
        activityResultReceiver.register(mActivityResultCallback);
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(
                createAudioRecordIntent(), createActivityOptions(mVirtualDisplay));

        int numSamples = AudioHelper.computeNumSamples(/* timeMs= */ 1000, SAMPLE_RATE,
                CHANNEL_COUNT);
        ByteBuffer audioData = AudioHelper.createAudioData(
                SAMPLE_RATE, numSamples, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
        int remaining = audioData.remaining();
        while (remaining > 0) {
            remaining -= audioInjection.write(
                    audioData, audioData.remaining(), AudioTrack.WRITE_BLOCKING);
        }
        verify(mActivityResultCallback, timeout(5000)).onActivityResult(
                mIntentCaptor.capture());
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onRecordingConfigChanged(any());

        Intent intent = mIntentCaptor.getValue();
        assertThat(intent).isNotNull();
        double powerSpectrumAtFrequency = intent.getDoubleExtra(EXTRA_POWER_SPECTRUM_AT_FREQUENCY,
                0);
        double powerSpectrumNotFrequency = intent.getDoubleExtra(EXTRA_POWER_SPECTRUM_NOT_FREQUENCY,
                0);
        assertThat(powerSpectrumNotFrequency).isLessThan(POWER_THRESHOLD_FOR_ABSENT);
        assertThat(powerSpectrumAtFrequency).isGreaterThan(POWER_THRESHOLD_FOR_PRESENT);

        activityResultReceiver.unregister();
    }

    private static Intent createPlayAudioIntent() {
        return new Intent(ACTION_PLAY_AUDIO)
                .setComponent(MAIN_ACTIVITY_COMPONENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private static Intent createAudioRecordIntent() {
        return new Intent(ACTION_RECORD_AUDIO)
                .setComponent(MAIN_ACTIVITY_COMPONENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
