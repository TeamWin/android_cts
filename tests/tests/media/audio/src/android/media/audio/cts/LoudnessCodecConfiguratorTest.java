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

import static android.media.MediaFormat.KEY_AAC_DRC_EFFECT_TYPE;
import static android.media.MediaFormat.KEY_AAC_DRC_TARGET_REFERENCE_LEVEL;
import static android.media.audio.Flags.FLAG_LOUDNESS_CONFIGURATOR_API;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.LoudnessCodecConfigurator;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class LoudnessCodecConfiguratorTest {
    private static final String TAG = "LoudnessCodecConfiguratorTest";

    private static final String TEST_MEDIA_AUDIO_CODEC_PREFIX = "audio/";
    private static final int TEST_AUDIO_TRACK_SAMPLERATE = 48000;
    private static final int TEST_AUDIO_TRACK_CHANNELS = 2;

    private static final Duration TEST_LOUDNESS_CALLBACK_TIMEOUT = Duration.ofMillis(200);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private LoudnessCodecConfigurator mLcc;

    private AudioTrack mAt;

    private final AtomicInteger mCodecUpdateCallNumber = new AtomicInteger(0);

    private final AtomicReference<Bundle> mLastCodecUpdate = new AtomicReference<>();

    private final class MyLoudnessCodecUpdateListener
            implements LoudnessCodecConfigurator.OnLoudnessCodecUpdateListener {
        @Override
        @NonNull
        public Bundle onLoudnessCodecUpdate(@NonNull MediaCodec mediaCodec,
                                            @NonNull Bundle codecValues) {
            mCodecUpdateCallNumber.incrementAndGet();
            mLastCodecUpdate.set(codecValues);
            return codecValues;
        }
    }

    @Before
    public void setUp() throws Exception {
        mLcc = LoudnessCodecConfigurator.create(Executors.newSingleThreadExecutor(),
                new MyLoudnessCodecUpdateListener());
    }

    @After
    public void tearDown() throws Exception {
        if (mAt != null) {
            mAt.release();
            mAt = null;
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void createNewLoudnessCodecConfigurator_notNull() {
        assertNotNull("LoudnessCodecConfigurator must not be null", mLcc);

        mLcc = LoudnessCodecConfigurator.create();
        assertNotNull("LoudnessCodecConfigurator must not be null", mLcc);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void setAudioTrack_withNoCodecs_noUpdates() throws Exception {
        mAt = createAndStartAudioTrack();

        mLcc.setAudioTrack(mAt);
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(0, mCodecUpdateCallNumber.get());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addUnconfiguredMediaCodec_returnsFalse() throws Exception {
        mAt = createAndStartAudioTrack();

        assertThrows(IllegalArgumentException.class,
                () -> mLcc.addMediaCodec(createMediaCodec(/*configure*/false)));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void setAudioTrack_withRegisteredCodecs_triggersUpdate() throws Exception {
        mAt = createAndStartAudioTrack();

        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.setAudioTrack(mAt);
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(3, mCodecUpdateCallNumber.get());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void getLoudnessCodecParams_returnsCurrentParameters() throws Exception {
        mAt = createAndStartAudioTrack();

        // to make sure the device id is propagated
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertFalse(mLcc.getLoudnessCodecParams(mAt,
                createMediaCodec(/*configure*/true)).isDefinitelyEmpty());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void multipleLcc_setAudioTrack_withRegisteredCodecs_triggersUpdate() throws Exception {
        LoudnessCodecConfigurator lcc2 = LoudnessCodecConfigurator.create(
                Executors.newSingleThreadExecutor(), new MyLoudnessCodecUpdateListener());

        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        lcc2.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.setAudioTrack(createAndStartAudioTrack());
        lcc2.setAudioTrack(createAndStartAudioTrack());
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(3, mCodecUpdateCallNumber.get());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void removeUnregisteredCodec_returnsFalse() throws Exception {
        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        assertThrows(IllegalArgumentException.class,
                () -> mLcc.removeMediaCodec(createMediaCodec(/*configure*/true)));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void setAudioTrack_withRemovedCodecs_noUpdate() throws Exception {
        final MediaCodec mediaCodec = createMediaCodec(/*configure*/true);
        mAt = createAndStartAudioTrack();

        mLcc.addMediaCodec(mediaCodec);
        mLcc.removeMediaCodec(mediaCodec);
        mLcc.setAudioTrack(mAt);
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(0, mCodecUpdateCallNumber.get());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addMediaCodec_afterSetAudioTrack_triggersNewUpdate() throws Exception {
        mAt = createAndStartAudioTrack();

        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.setAudioTrack(mAt);
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(3, mCodecUpdateCallNumber.get());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addMediaCodec_afterRemovingAudioTrack_noSecondUpdate() throws Exception {
        mAt = createAndStartAudioTrack();

        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.setAudioTrack(mAt);
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        mLcc.setAudioTrack(null);
        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(1, mCodecUpdateCallNumber.get());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addMediaCodec_afterReplaceAudioTrack_triggersNewUpdate() throws Exception {
        mAt = createAndStartAudioTrack();

        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.setAudioTrack(mAt);
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        mAt.release();
        mAt = createAndStartAudioTrack();
        mLcc.setAudioTrack(mAt);
        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(3, mCodecUpdateCallNumber.get());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void setAudioTrack_withRegisteredCodec_checkParameters() throws Exception {
        mAt = createAndStartAudioTrack();

        mLcc.addMediaCodec(createMediaCodec(/*configure*/true));
        mLcc.setAudioTrack(mAt);
        Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

        assertEquals(1, mCodecUpdateCallNumber.get());
        Bundle lastUpdate = mLastCodecUpdate.get();
        assertNotNull(lastUpdate);
        assertTrue(lastUpdate.getInt(KEY_AAC_DRC_TARGET_REFERENCE_LEVEL) != 0);
        assertTrue(lastUpdate.getInt(KEY_AAC_DRC_EFFECT_TYPE) != 0);
    }


    private static AudioTrack createAndStartAudioTrack() {
        final int bufferSizeInBytes =
                 TEST_AUDIO_TRACK_SAMPLERATE * TEST_AUDIO_TRACK_CHANNELS * Short.BYTES;
        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        final AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(aa)
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setAudioFormat(new AudioFormat.Builder()
                        .setChannelMask(TEST_AUDIO_TRACK_CHANNELS)
                        .setSampleRate(TEST_AUDIO_TRACK_SAMPLERATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .build();

        // enqueue silence to have a device assigned
        short[] data = new short[bufferSizeInBytes / Short.BYTES];
        track.write(data, 0, data.length, AudioTrack.WRITE_NON_BLOCKING);
        track.play();

        return track;
    }

    /** Creates a decoder for xHE-AAC content. */
    private MediaCodec createMediaCodec(boolean configure) throws Exception {
        AssetFileDescriptor testFd = InstrumentationRegistry.getInstrumentation().getContext()
                .getResources()
                .openRawResourceFd(R.raw.noise_2ch_48khz_tlou_19lufs_anchor_17lufs_mp4);

        MediaExtractor extractor;
        extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        testFd.close();

        assertEquals("wrong number of tracks", 1, extractor.getTrackCount());
        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        assertTrue("not an audio file", mime.startsWith(TEST_MEDIA_AUDIO_CODEC_PREFIX));
        final MediaCodec mediaCodec = MediaCodec.createDecoderByType(mime);

        if (configure) {
            Log.v(TAG, "configuring with " + format);
            mediaCodec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        }

        return mediaCodec;
    }

}
