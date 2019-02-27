/*
 * Copyright (C) 2019 The Android Open Source Project
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


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.media.AudioAttributes;
import android.media.AudioAttributes.AttributeUsage;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.projection.MediaProjection;
import android.platform.test.annotations.Presubmit;

import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * Test audio playback capture through MediaProjection.
 *
 * The tests do the following:
 *   - retrieve a MediaProjection through AudioPlaybackCaptureActivity
 *   - play some audio
 *   - use that MediaProjection to record the audio playing
 *   - check that some audio was recorded.
 *
 * Currently the test that some audio was recorded just check that at least one sample is non 0.
 * A better check needs to be used, eg: compare the power spectrum.
 */
public class AudioPlaybackCaptureTest {
    private static final String TAG = "AudioPlaybackCaptureTest";
    private static final int BUFFER_SIZE = 32768; // ~200ms at 44.1k 16b MONO

    private AudioManager mAudioManager;
    private boolean mPlaybackBeforeCapture;
    private int mUid; //< UID of this test
    private AudioPlaybackCaptureActivity mActivity;
    private MediaProjection mMediaProjection;
    @Rule
    public ActivityTestRule<AudioPlaybackCaptureActivity> mActivityRule =
                new ActivityTestRule<>(AudioPlaybackCaptureActivity.class);

    private static class APCTestConfig {
        public @AttributeUsage int[] matchingUsages;
        public @AttributeUsage int[] excludeUsages;
        public int[] matchingUids;
        public int[] excludeUids;
        private AudioPlaybackCaptureConfiguration build(MediaProjection projection)
                throws Exception {
            AudioPlaybackCaptureConfiguration.Builder apccBuilder =
                    new AudioPlaybackCaptureConfiguration.Builder(projection);

            if (matchingUsages != null) {
                for (int usage : matchingUsages) {
                    apccBuilder.addMatchingUsage(new AudioAttributes.Builder()
                            .setUsage(usage)
                            .build());
                }
            }
            if (excludeUsages != null) {
                for (int usage : excludeUsages) {
                    apccBuilder.excludeUsage(new AudioAttributes.Builder()
                            .setUsage(usage)
                            .build());
                }
            }
            if (matchingUids != null) {
                for (int uid : matchingUids) {
                    apccBuilder.addMatchingUid(uid);
                }
            }
            if (excludeUids != null) {
                for (int uid : excludeUids) {
                    apccBuilder.excludeUid(uid);
                }
            }
            return apccBuilder.build();
        }
    };
    private APCTestConfig mAPCTestConfig;

    @Before
    public void setup() throws Exception {
        mPlaybackBeforeCapture = false;
        mAPCTestConfig = new APCTestConfig();
        mActivity = mActivityRule.getActivity();
        mAudioManager = mActivity.getSystemService(AudioManager.class);
        mUid = mActivity.getApplicationInfo().uid;
        mMediaProjection = mActivity.waitForMediaProjection();
    }

    private AudioRecord createPlaybackCaptureRecord(AudioFormat audioFormat) throws Exception {
        AudioPlaybackCaptureConfiguration apcConfig = mAPCTestConfig.build(mMediaProjection);

        AudioRecord audioRecord = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(apcConfig)
                .setAudioFormat(audioFormat)
                .build();
        return audioRecord;
    }

    private MediaPlayer createMediaPlayer(boolean allowCapture, int resid,
                                          @AttributeUsage int usage) {
        MediaPlayer mediaPlayer = MediaPlayer.create(
                mActivity,
                resid,
                new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(usage)
                    .setAllowCapture(allowCapture)
                    .build(),
                mAudioManager.generateAudioSessionId());
        mediaPlayer.setLooping(true);
        return mediaPlayer;
    }

    private static ByteBuffer readToBuffer(AudioRecord audioRecord, int bufferSize)
            throws Exception {
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, audioRecord.getRecordingState());
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        int retry = 100;
        while (buffer.hasRemaining()) {
            assertNotSame(buffer.remaining() + "/" + bufferSize + "remaining", 0, retry--);
            int written = audioRecord.read(buffer, buffer.remaining());
            assertThat(written).isGreaterThan(0);
            buffer.position(buffer.position() + written);
        }
        buffer.rewind();
        return buffer;
    }

    private static boolean onlySilence(ShortBuffer buffer) {
        boolean onlySilence = true;
        while (buffer.hasRemaining()) {
            onlySilence &= buffer.get() == 0;
        }
        return onlySilence;
    }

    public void testPlaybackCapture(boolean allowCapture,
                                    @AttributeUsage int playbackUsage,
                                    boolean dataPresent) throws Exception {
        MediaPlayer mediaPlayer = createMediaPlayer(allowCapture, R.raw.testwav_16bit_44100hz,
                                                    playbackUsage);
        if (mPlaybackBeforeCapture) {
            mediaPlayer.start();
            Thread.sleep(100); // Make sure the player is actually playing, thus forcing a rerouting
        }

        AudioRecord audioRecord = createPlaybackCaptureRecord(
                new AudioFormat.Builder()
                     .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                     .setSampleRate(44100)
                     .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                     .build());

        audioRecord.startRecording();
        mediaPlayer.start();
        ByteBuffer rawBuffer = readToBuffer(audioRecord, BUFFER_SIZE);
        audioRecord.stop(); // Force an reroute
        mediaPlayer.stop();
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, audioRecord.getRecordingState());
        if (dataPresent) {
            assertFalse("Expected data, but only silence was recorded",
                        onlySilence(rawBuffer.asShortBuffer()));
        } else {
            assertTrue("Expected silence, but some data was recorded",
                       onlySilence(rawBuffer.asShortBuffer()));
        }
    }

    private static final boolean OPT_IN = true;
    private static final boolean OPT_OUT = false;

    private static final boolean EXPECT_DATA = true;
    private static final boolean EXPECT_SILENCE = false;

    private static final @AttributeUsage int[] ALLOWED_USAGES = new int[]{
            AudioAttributes.USAGE_UNKNOWN,
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME
    };
    private static final @AttributeUsage int[] FORBIDEN_USAGES = new int[]{
            AudioAttributes.USAGE_ALARM,
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            AudioAttributes.USAGE_ASSISTANT,
            AudioAttributes.USAGE_NOTIFICATION,
            AudioAttributes.USAGE_VOICE_COMMUNICATION
    };

    @Presubmit
    @Test
    public void testPlaybackCaptureFast() throws Exception {
        mAPCTestConfig.matchingUsages = new int[]{ AudioAttributes.USAGE_MEDIA };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_MEDIA, EXPECT_DATA);
        testPlaybackCapture(OPT_OUT, AudioAttributes.USAGE_MEDIA, EXPECT_SILENCE);
    }

    @Presubmit
    @Test
    public void testPlaybackCaptureRerouting() throws Exception {
        mPlaybackBeforeCapture = true;
        mAPCTestConfig.matchingUsages = new int[]{ AudioAttributes.USAGE_MEDIA };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_MEDIA, EXPECT_DATA);
    }

    @Presubmit
    @Test(expected = IllegalArgumentException.class)
    public void testMatchNothing() throws Exception {
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_UNKNOWN, EXPECT_SILENCE);
    }

    @Presubmit
    @Test(expected = IllegalStateException.class)
    public void testCombineUsages() throws Exception {
        mAPCTestConfig.matchingUsages = new int[]{ AudioAttributes.USAGE_UNKNOWN };
        mAPCTestConfig.excludeUsages = new int[]{ AudioAttributes.USAGE_MEDIA };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_UNKNOWN, EXPECT_SILENCE);
    }

    @Presubmit
    @Test(expected = IllegalStateException.class)
    public void testCombineUid() throws Exception {
        mAPCTestConfig.matchingUids = new int[]{ mUid };
        mAPCTestConfig.excludeUids = new int[]{ 0 };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_UNKNOWN, EXPECT_SILENCE);
    }

    @Test
    public void testCaptureMatchingAllowedUsage() throws Exception {
        for (int usage : ALLOWED_USAGES) {
            mAPCTestConfig.matchingUsages = new int[]{ usage };
            testPlaybackCapture(OPT_IN, usage, EXPECT_DATA);
            testPlaybackCapture(OPT_OUT, usage, EXPECT_SILENCE);

            mAPCTestConfig.matchingUsages = ALLOWED_USAGES;
            testPlaybackCapture(OPT_IN, usage, EXPECT_DATA);
            testPlaybackCapture(OPT_OUT, usage, EXPECT_SILENCE);
        }
    }

    @Test
    public void testCaptureMatchingForbidenUsage() throws Exception {
        for (int usage : FORBIDEN_USAGES) {
            mAPCTestConfig.matchingUsages = new int[]{ usage };
            testPlaybackCapture(OPT_IN, usage, EXPECT_SILENCE);

            mAPCTestConfig.matchingUsages = ALLOWED_USAGES;
            testPlaybackCapture(OPT_IN, usage, EXPECT_SILENCE);
        }
    }

    @Test
    public void testCaptureExcludeUsage() throws Exception {
        for (int usage : ALLOWED_USAGES) {
            mAPCTestConfig.excludeUsages = new int[]{ usage };
            testPlaybackCapture(OPT_IN, usage, EXPECT_SILENCE);

            mAPCTestConfig.excludeUsages = ALLOWED_USAGES;
            testPlaybackCapture(OPT_IN, usage, EXPECT_SILENCE);

            mAPCTestConfig.excludeUsages = FORBIDEN_USAGES;
            testPlaybackCapture(OPT_IN, usage, EXPECT_DATA);
        }
    }

    @Test
    public void testCaptureMatchingUid() throws Exception {
        mAPCTestConfig.matchingUids = new int[]{ mUid };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_GAME, EXPECT_DATA);
        testPlaybackCapture(OPT_OUT, AudioAttributes.USAGE_GAME, EXPECT_SILENCE);
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_VOICE_COMMUNICATION, EXPECT_SILENCE);

        mAPCTestConfig.matchingUids = new int[]{ 0 };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_GAME, EXPECT_SILENCE);
    }

    @Test
    public void testCaptureExcludeUid() throws Exception {
        mAPCTestConfig.excludeUids = new int[]{ 0 };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_GAME, EXPECT_DATA);
        testPlaybackCapture(OPT_OUT, AudioAttributes.USAGE_GAME, EXPECT_SILENCE);
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_VOICE_COMMUNICATION, EXPECT_SILENCE);

        mAPCTestConfig.excludeUids = new int[]{ mUid };
        testPlaybackCapture(OPT_IN, AudioAttributes.USAGE_GAME, EXPECT_SILENCE);
    }
}
