/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.audio.cts;

import static android.media.audio.Flags.FLAG_LOUDNESS_CONFIGURATOR_API;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.LoudnessCodecConfigurator;
import android.media.MediaCodec;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class LoudnessCodecConfiguratorTest {
    private static final String TAG = "LoudnessCodecConfiguratorTest";

    private static final String TEST_MEDIA_CODEC_MIME = "video/mp4v-es";
    private static final int TEST_AUDIO_TRACK_BUFFER_SIZE = 2048;
    private static final int TEST_AUDIO_TRACK_SAMPLERATE = 48000;
    private static final int TEST_AUDIO_TRACK_CHANNELS = 2;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private LoudnessCodecConfigurator mLcc;

    private static final class MyLoudnessCodecUpdateListener
            implements LoudnessCodecConfigurator.OnLoudnessCodecUpdateListener {
        @Override
        @NonNull
        public Bundle onLoudnessCodecUpdate(@NonNull MediaCodec mediaCodec,
                                            @NonNull Bundle codecValues) {
            return codecValues;
        }
    }
    private final MyLoudnessCodecUpdateListener mListener = new MyLoudnessCodecUpdateListener();

    @Before
    public void setUp() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        AudioManager audioManager = context.getSystemService(AudioManager.class);

        if (audioManager != null) {
            mLcc = audioManager.createLoudnessCodecConfigurator();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void createNewLoudnessCodecConfigurator_notNull() {
        assertNotNull("LoudnessCodecConfigurator shouldn't be null", mLcc);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void startCodecUpdatesWithNoAudioTrack_returnsFalse() throws Exception {
        mLcc.addMediaCodec(MediaCodec.createDecoderByType(TEST_MEDIA_CODEC_MIME));

        assertFalse(mLcc.startLoudnessCodecUpdates());
        assertFalse(mLcc.startLoudnessCodecUpdates(Executors.newSingleThreadExecutor(), mListener));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void startCodecUpdatesWithNoMediaCodecs_returnsFalse() {
        final AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().build())
                .setBufferSizeInBytes(TEST_AUDIO_TRACK_BUFFER_SIZE)
                .setAudioFormat(new AudioFormat.Builder()
                        .setChannelMask(TEST_AUDIO_TRACK_CHANNELS)
                        .setSampleRate(TEST_AUDIO_TRACK_SAMPLERATE).build())
                .build();
        mLcc.setAudioTrack(track);

        assertFalse(mLcc.startLoudnessCodecUpdates());
        assertFalse(mLcc.startLoudnessCodecUpdates(Executors.newSingleThreadExecutor(), mListener));
    }

}
