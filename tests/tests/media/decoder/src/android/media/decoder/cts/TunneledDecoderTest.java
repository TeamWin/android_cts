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

package android.media.decoder.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.cts.CodecState;
import android.media.cts.MediaCodecTunneledPlayer;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.net.Uri;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.MediaUtils;
import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Duration;
import java.util.function.Supplier;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@RunWith(AndroidJUnit4.class)
public class TunneledDecoderTest extends MediaTestBase {
    private static final String TAG = "TunneledDecoderTest";

    static final String mInpPrefix = WorkDir.getMediaDirString();

    private MediaCodecTunneledPlayer mMediaCodecPlayer;
    private DynamicConfigDeviceSide dynamicConfig;
    private static final String MODULE_NAME = "CtsMediaDecoderTestCases";
    private static final int SLEEP_TIME_MS = 1000;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        dynamicConfig = new DynamicConfigDeviceSide(MODULE_NAME);
    }

    @After
    @Override
    public void tearDown() {
        // ensure MediaCodecPlayer resources are released even if an exception is thrown.
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
            mMediaCodecPlayer = null;
        }
        super.tearDown();
    }

    /* return true if a particular video feature is supported for the given mimetype */
    private boolean isVideoFeatureSupported(String mimeType, String feature) {
        MediaFormat format = MediaFormat.createVideoFormat( mimeType, 1920, 1080);
        format.setFeatureEnabled(feature, true);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String codecName = mcl.findDecoderForFormat(format);
        return (codecName == null) ? false : true;
    }

    /**
     * Test tunneled video playback mode if supported
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void tunneledVideoPlayback(String mimeType, String videoName) throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        final long durationMs = mMediaCodecPlayer.getDuration();
        final long timeOutMs = System.currentTimeMillis() + durationMs + 5 * 1000; // add 5 sec
        while (!mMediaCodecPlayer.isEnded()) {
            // Log.d(TAG, "currentPosition: " + mMediaCodecPlayer.getCurrentPosition()
            //         + "  duration: " + mMediaCodecPlayer.getDuration());
            assertTrue("Tunneled video playback timeout exceeded",
                    timeOutMs > System.currentTimeMillis());
            Thread.sleep(SLEEP_TIME_MS);
            if (mMediaCodecPlayer.getCurrentPosition() >= mMediaCodecPlayer.getDuration()) {
                Log.d(TAG, "testTunneledVideoPlayback -- current pos = " +
                        mMediaCodecPlayer.getCurrentPosition() +
                        ">= duration = " + mMediaCodecPlayer.getDuration());
                break;
            }
        }
        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test tunneled video playback mode with HEVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPlaybackHevc() throws Exception {
        tunneledVideoPlayback(MediaFormat.MIMETYPE_VIDEO_HEVC,
                    "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test tunneled video playback mode with AVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPlaybackAvc() throws Exception {
        tunneledVideoPlayback(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test tunneled video playback mode with VP9 if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPlaybackVp9() throws Exception {
        tunneledVideoPlayback(MediaFormat.MIMETYPE_VIDEO_VP9,
                    "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test tunneled video playback flush if supported
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledVideoFlush(String mimeType, String videoName) throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        mMediaCodecPlayer.pause();
        mMediaCodecPlayer.flush();
        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test tunneled video playback flush with HEVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoFlushHevc() throws Exception {
        testTunneledVideoFlush(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test tunneled video playback flush with AVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoFlushAvc() throws Exception {
        testTunneledVideoFlush(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test tunneled video playback flush with VP9 if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoFlushVp9() throws Exception {
        testTunneledVideoFlush(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that the first frame is rendered when video peek is on in tunneled mode.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledVideoPeekOn(String mimeType, String videoName) throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        // Setup tunnel mode test media player
        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();
        mMediaCodecPlayer.setVideoPeek(true); // Enable video peek

        // Assert that onFirstTunnelFrameReady is called
        mMediaCodecPlayer.queueOneVideoFrame();
        final int waitTimeMs = 150;
        Thread.sleep(waitTimeMs);
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d milliseconds",
                        waitTimeMs),
                mMediaCodecPlayer.isFirstTunnelFrameReady());
        // Assert that video peek is enabled and working
        assertNotEquals(String.format("First frame not rendered within %d milliseconds",
                        waitTimeMs), CodecState.UNINITIALIZED_TIMESTAMP,
                mMediaCodecPlayer.getCurrentPosition());

        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test that the first frame is rendered when video peek is on for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOnHevc() throws Exception {
        testTunneledVideoPeekOn(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that the first frame is rendered when video peek is on for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOnAvc() throws Exception {
        testTunneledVideoPeekOn(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that the first frame is rendered when video peek is on for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOnVp9() throws Exception {
        testTunneledVideoPeekOn(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }


    /**
     * Test that peek off doesn't render the first frame until turned on in tunneled mode.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledVideoPeekOff(String mimeType, String videoName) throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        // Setup tunnel mode test media player
        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();
        mMediaCodecPlayer.setVideoPeek(false); // Disable video peek

        // Assert that onFirstTunnelFrameReady is called
        mMediaCodecPlayer.queueOneVideoFrame();
        final int waitTimeMsStep1 = 150;
        Thread.sleep(waitTimeMsStep1);
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d milliseconds",
                        waitTimeMsStep1),
                mMediaCodecPlayer.isFirstTunnelFrameReady());
        // Assert that video peek is disabled
        assertEquals("First frame rendered while peek disabled", CodecState.UNINITIALIZED_TIMESTAMP,
                mMediaCodecPlayer.getCurrentPosition());
        mMediaCodecPlayer.setVideoPeek(true); // Reenable video peek
        final int waitTimeMsStep2 = 150;
        Thread.sleep(waitTimeMsStep2);
        // Assert that video peek is enabled
        assertNotEquals(String.format(
                        "First frame not rendered within %d milliseconds while peek enabled",
                        waitTimeMsStep2), CodecState.UNINITIALIZED_TIMESTAMP,
                mMediaCodecPlayer.getCurrentPosition());

        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test that peek off doesn't render the first frame until turned on for HEC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOffHevc() throws Exception {
        testTunneledVideoPeekOff(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that peek off doesn't render the first frame until turned on for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOffAvc() throws Exception {
        testTunneledVideoPeekOff(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that peek off doesn't render the first frame until turned on for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOffVp9() throws Exception {
        testTunneledVideoPeekOff(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

   /**
    * Test that audio timestamps don't progress during audio PTS gaps in tunneled mode.
    */
   private void testTunneledAudioProgressWithPtsGaps(String mimeType, String fileName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);

        mMediaCodecPlayer = new MediaCodecTunneledPlayer(mContext,
                getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        final Uri mediaUri = Uri.fromFile(new File(mInpPrefix, fileName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // Requirement: If the audio presentation timestamp header sent by the app is greater than
        // the current audio clock by less than 100ms, the framePosition returned by
        // AudioTrack#getTimestamp (per get_presentation_position) must not advance for any silent
        // frames rendered to fill the gap.
        // TODO: add link to documentation when available

        // Simulate a PTS gap of 100ms after 30ms
        Thread.sleep(30);
        mMediaCodecPlayer.setAudioTrackOffsetMs(100);

        // Verify that at some point in time in the future, the framePosition stopped advancing.
        // This verifies that when silence was rendered to fill the PTS gap, that the silent frames
        // do not cause framePosition to advance.
        final long ptsGapTimeoutMs = 1000;
        long startTimeMs = System.currentTimeMillis();
        AudioTimestamp currentTimestamp = mMediaCodecPlayer.getTimestamp();
        AudioTimestamp ptsGapTimestamp;
        do {
            assertTrue(String.format("No audio PTS gap after %d milliseconds", ptsGapTimeoutMs),
                    System.currentTimeMillis() - startTimeMs < ptsGapTimeoutMs);
            ptsGapTimestamp = currentTimestamp;
            Thread.sleep(50);
            currentTimestamp = mMediaCodecPlayer.getTimestamp();
        } while (currentTimestamp.framePosition != ptsGapTimestamp.framePosition);

        // Allow the playback to advance past the PTS gap and back to normal operation
        Thread.sleep(500);
        // Simulate the end of playback
        mMediaCodecPlayer.stopWritingToAudioTrack(true);

        // Sleep till framePosition stabilizes, i.e. playback is complete or till max 3 seconds.
        final long endOfPlayackTimeoutMs = 3000;
        startTimeMs = System.currentTimeMillis();
        AudioTimestamp endOfPlaybackTimestamp;
        do {
            assertTrue(String.format("No end of playback after %d milliseconds",
                            endOfPlayackTimeoutMs),
                    System.currentTimeMillis() - startTimeMs < endOfPlayackTimeoutMs);
            endOfPlaybackTimestamp = currentTimestamp;
            Thread.sleep(50);
            currentTimestamp = mMediaCodecPlayer.getTimestamp();
        } while (currentTimestamp.framePosition != endOfPlaybackTimestamp.framePosition);

        // Verify if number of frames written and played are same even if PTS gaps were present
        // in the playback.
        assertEquals("Number of frames written != Number of frames played",
                mMediaCodecPlayer.getAudioFramesWritten(),
                endOfPlaybackTimestamp.framePosition);
    }

    /**
     * Test that audio timestamps don't progress during audio PTS gaps for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPtsGapsHevc() throws Exception {
        testTunneledAudioProgressWithPtsGaps(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio timestamps don't progress during audio PTS gaps for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPtsGapsAvc() throws Exception {
        testTunneledAudioProgressWithPtsGaps(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio timestamps don't progress during audio PTS gaps for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPtsGapsVp9() throws Exception {
        testTunneledAudioProgressWithPtsGaps(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that audio timestamps stop progressing during underrun in tunneled mode.
     */
    private void testTunneledAudioProgressWithUnderrun(String mimeType, String fileName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);

        mMediaCodecPlayer = new MediaCodecTunneledPlayer(mContext,
                getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        final Uri mediaUri = Uri.fromFile(new File(mInpPrefix, fileName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // Stop writing to the AudioTrack after 200 ms.
        Thread.sleep(200);
        mMediaCodecPlayer.stopWritingToAudioTrack(true);

        // Resume writing to the audioTrack after 1 sec. Write only for 200 ms.
        Thread.sleep(1000);
        mMediaCodecPlayer.stopWritingToAudioTrack(false);
        Thread.sleep(200);
        mMediaCodecPlayer.stopWritingToAudioTrack(true);

        // Sleep till framePosition stabilizes, i.e. playback is complete or till max 3 seconds.
        long framePosCurrent = 0;
        int totalSleepMs = 0;
        while (totalSleepMs < 3000
                && framePosCurrent != mMediaCodecPlayer.getTimestamp().framePosition) {
            framePosCurrent = mMediaCodecPlayer.getTimestamp().framePosition;
            Thread.sleep(500);
            totalSleepMs += 500;
        }

        // Verify if number of frames written and played are same. This ensures the
        // framePosition returned by AudioTrack#getTimestamp progresses correctly in case of
        // underrun
        assertEquals("Number of frames written != Number of frames played",
                mMediaCodecPlayer.getAudioFramesWritten(),
                mMediaCodecPlayer.getTimestamp().framePosition);
    }

    /**
     * Test that audio timestamps stop progressing during underrun for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithUnderrunHevc() throws Exception {
        testTunneledAudioProgressWithUnderrun(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio timestamps stop progressing during underrun for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithUnderrunAvc() throws Exception {
        testTunneledAudioProgressWithUnderrun(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio timestamps stop progressing during underrun for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithUnderrunVp9() throws Exception {
        testTunneledAudioProgressWithUnderrun(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test accurate video rendering after a flush in tunneled mode.
     *
     * Test On some devices, queuing content when the player is paused, then triggering a flush, then
     * queuing more content does not behave as expected. The queued content gets lost and the flush
     * is really only applied once playback has resumed.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledAccurateVideoFlush(String mimeType, String videoName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        // Below are some timings used throughout this test.
        //
        // Maximum allowed time between start of playback and first frame displayed
        final long maxAllowedTimeToFirstFrameMs = 500;
        // Maximum allowed time between issuing a pause and the last frame being displayed
        final long maxDrainTimeMs = 200;

        // Setup tunnel mode test media player
        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();
        // Video peek might interfere with the test: we want to ensure that queuing more data during
        // a pause does not cause displaying more video frames, which is precisely what video peek
        // does.
        mMediaCodecPlayer.setVideoPeek(false);

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // Allow some time for playback to commence
        Thread.sleep(500);

        // Pause playback
        mMediaCodecPlayer.pause();

        // Wait for audio to pause
        AudioTimestamp pauseAudioTimestamp;
        {
            AudioTimestamp currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            long startTimeMs = System.currentTimeMillis();
            do {
                // If it takes longer to pause, the UX won't feel responsive to the user
                int audioPauseTimeoutMs = 250;
                assertTrue(String.format("No audio pause after %d milliseconds",
                                audioPauseTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < audioPauseTimeoutMs);
                pauseAudioTimestamp = currentAudioTimestamp;
                Thread.sleep(50);
                currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            } while (currentAudioTimestamp.framePosition != pauseAudioTimestamp.framePosition);
        }
        long pauseAudioSystemTimeMs = pauseAudioTimestamp.nanoTime / 1000 / 1000;

        // Wait for video to pause
        long pauseVideoSystemTimeNs;
        long pauseVideoPositionUs;
        {
            long currentVideoSystemTimeNs = mMediaCodecPlayer.getCurrentRenderedSystemTimeNano();
            long startTimeMs = System.currentTimeMillis();
            do {
                int videoUnderrunTimeoutMs = 2000;
                assertTrue(String.format("No video pause after %d milliseconds",
                                videoUnderrunTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < videoUnderrunTimeoutMs);
                pauseVideoSystemTimeNs = currentVideoSystemTimeNs;
                Thread.sleep(250); // onFrameRendered can get delayed in the Framework
                currentVideoSystemTimeNs = mMediaCodecPlayer.getCurrentRenderedSystemTimeNano();
            } while (currentVideoSystemTimeNs != pauseVideoSystemTimeNs);
            pauseVideoPositionUs = mMediaCodecPlayer.getVideoTimeUs();
        }
        long pauseVideoSystemTimeMs = pauseVideoSystemTimeNs / 1000 / 1000;

        // Video should not continue running for a long period of time after audio pauses
        long pauseVideoToleranceMs = 500;
        assertTrue(String.format(
                        "Video ran %d milliseconds longer than audio (video:%d audio:%d)",
                        pauseVideoToleranceMs, pauseVideoSystemTimeMs, pauseAudioSystemTimeMs),
                pauseVideoSystemTimeMs - pauseAudioSystemTimeMs < pauseVideoToleranceMs);

        // Verify that playback stays paused
        Thread.sleep(500);
        assertEquals(mMediaCodecPlayer.getTimestamp().framePosition, pauseAudioTimestamp.framePosition);
        assertEquals(mMediaCodecPlayer.getCurrentRenderedSystemTimeNano(), pauseVideoSystemTimeNs);
        assertEquals(mMediaCodecPlayer.getVideoTimeUs(), pauseVideoPositionUs);

        // Verify audio and video are roughly in sync when paused
        long framePosition = mMediaCodecPlayer.getTimestamp().framePosition;
        long playbackRateFps = mMediaCodecPlayer.getAudioTrack().getPlaybackRate();
        long pauseAudioPositionMs = pauseAudioTimestamp.framePosition * 1000 / playbackRateFps;
        long pauseVideoPositionMs = pauseVideoPositionUs / 1000;
        long deltaMs = pauseVideoPositionMs - pauseAudioPositionMs;
        assertTrue(String.format(
                        "Video is %d milliseconds out of sync from audio (video:%d audio:%d)",
                        deltaMs, pauseVideoPositionMs, pauseAudioPositionMs),
                deltaMs > -80 && deltaMs < pauseVideoToleranceMs);

        // Flush both audio and video pipelines
        mMediaCodecPlayer.flush();

        // The flush should not cause any frame to be displayed.
        // Wait for the max startup latency to see if one (incorrectly) arrives.
        Thread.sleep(maxAllowedTimeToFirstFrameMs);
        assertEquals("Video frame rendered after flush", mMediaCodecPlayer.getVideoTimeUs(),
                CodecState.UNINITIALIZED_TIMESTAMP);

        // Ensure video peek is disabled before queuing the next frame, otherwise it will
        // automatically be rendered when queued.
        mMediaCodecPlayer.setVideoPeek(false);

        // We rewind to the beginning of the stream (to a key frame) and queue one frame, but
        // pretend like we're seeking 1 second forward in the stream.
        long presentationTimeOffsetUs = pauseVideoPositionUs + 1000 * 1000;
        mMediaCodecPlayer.seekToBeginning(presentationTimeOffsetUs);
        Long queuedVideoTimestamp = mMediaCodecPlayer.queueOneVideoFrame();
        assertNotNull("Failed to queue a video frame", queuedVideoTimestamp);

        // The enqueued frame should not be rendered while we're paused.
        // Wait for the max startup latency to see if it (incorrectly) arrives.
        Thread.sleep(maxAllowedTimeToFirstFrameMs);
        assertEquals("Video frame rendered during pause", mMediaCodecPlayer.getVideoTimeUs(),
                CodecState.UNINITIALIZED_TIMESTAMP);

        // Resume playback
        mMediaCodecPlayer.resume();
        Thread.sleep(maxAllowedTimeToFirstFrameMs);
        // Verify that the first rendered frame was the first queued frame
        ImmutableList<Long> renderedVideoTimestamps =
                mMediaCodecPlayer.getRenderedVideoFrameTimestampList();
        assertFalse(String.format("No frame rendered after resume within %d ms",
                        maxAllowedTimeToFirstFrameMs), renderedVideoTimestamps.isEmpty());
        assertEquals("First rendered video frame does not match first queued video frame",
                renderedVideoTimestamps.get(0), queuedVideoTimestamp);
        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test accurate video rendering after a video MediaCodec flush with HEVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAccurateVideoFlushHevc() throws Exception {
        testTunneledAccurateVideoFlush(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test accurate video rendering after a video MediaCodec flush with AVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAccurateVideoFlushAvc() throws Exception {
        testTunneledAccurateVideoFlush(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test accurate video rendering after a video MediaCodec flush with VP9 if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAccurateVideoFlushVp9() throws Exception {
        testTunneledAccurateVideoFlush(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that audio timestamps stop progressing during pause in tunneled mode.
     */
    private void testTunneledAudioProgressWithPause(String mimeType, String videoName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        long firstVideoPosition = mMediaCodecPlayer.getVideoTimeUs();
        assertNotEquals("onFrameRendered was not called",
                firstVideoPosition, CodecState.UNINITIALIZED_TIMESTAMP);
        AudioTimestamp firstAudioTimestamp = mMediaCodecPlayer.getTimestamp();
        assertNotEquals("Audio timestamp is null", firstAudioTimestamp, null);
        assertNotEquals("Audio timestamp has a zero frame position",
                firstAudioTimestamp.framePosition, 0);

        // Expected stabilization wait is 60ms. We triple to 180ms to prevent flakiness
        // and still test basic functionality.
        final int sleepTimeMs = 180;
        Thread.sleep(sleepTimeMs);
        mMediaCodecPlayer.pause();
        // pause might take some time to ramp volume down.
        Thread.sleep(sleepTimeMs);
        AudioTimestamp audioTimestampAfterPause = mMediaCodecPlayer.getTimestamp();
        // Verify the video has advanced beyond the first position.
        assertTrue(mMediaCodecPlayer.getVideoTimeUs() > firstVideoPosition);
        // Verify that the timestamp has advanced beyond the first timestamp.
        assertTrue(audioTimestampAfterPause.nanoTime > firstAudioTimestamp.nanoTime);

        Thread.sleep(sleepTimeMs);
        // Verify that the timestamp does not advance after pause.
        assertEquals(audioTimestampAfterPause.nanoTime, mMediaCodecPlayer.getTimestamp().nanoTime);
    }


    /**
     * Test that audio timestamps stop progressing during pause for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPauseHevc() throws Exception {
        testTunneledAudioProgressWithPause(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio timestamps stop progressing during pause for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPauseAvc() throws Exception {
        testTunneledAudioProgressWithPause(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio timestamps stop progressing during pause for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPauseVp9() throws Exception {
        testTunneledAudioProgressWithPause(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync in tunneled mode.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void tunneledAudioUnderrun(String mimeType, String videoName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // Keep buffering video content but stop buffering audio content -> audio underrun
        mMediaCodecPlayer.simulateAudioUnderrun(true);

        // Wait for audio underrun
        AudioTimestamp underrunAudioTimestamp;
        {
            AudioTimestamp currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            long startTimeMs = System.currentTimeMillis();
            do {
                int audioUnderrunTimeoutMs = 1000;
                assertTrue(String.format("No audio underrun after %d milliseconds",
                                System.currentTimeMillis() - startTimeMs),
                        System.currentTimeMillis() - startTimeMs < audioUnderrunTimeoutMs);
                underrunAudioTimestamp = currentAudioTimestamp;
                Thread.sleep(50);
                currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            } while (currentAudioTimestamp.framePosition != underrunAudioTimestamp.framePosition);
        }

        // Wait until video playback pauses due to underrunning audio
        long pausedVideoTimeUs = -1;
        {
            long currentVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
            long startTimeMs = System.currentTimeMillis();
            do {
                int videoPauseTimeoutMs = 2000;
                assertTrue(String.format("No video pause after %d milliseconds",
                                videoPauseTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < videoPauseTimeoutMs);
                pausedVideoTimeUs = currentVideoTimeUs;
                Thread.sleep(250); // onFrameRendered messages can get delayed in the Framework
                currentVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
            } while (currentVideoTimeUs != pausedVideoTimeUs);
        }

        // Retrieve index for the video rendered frame at the time of video pausing
        int pausedVideoRenderedTimestampIndex =
                mMediaCodecPlayer.getRenderedVideoFrameTimestampList().size() - 1;

        // Resume audio buffering with a negative offset, in order to simulate a desynchronisation.
        // TODO(b/202710709): Use timestamp relative to last played video frame before pause
        mMediaCodecPlayer.setAudioTrackOffsetMs(-100);
        mMediaCodecPlayer.simulateAudioUnderrun(false);

        // Wait until audio playback resumes
        AudioTimestamp postResumeAudioTimestamp;
        {
            AudioTimestamp previousAudioTimestamp;
            long startTimeMs = System.currentTimeMillis();
            do {
                int audioResumeTimeoutMs = 1000;
                assertTrue(String.format("Audio has not resumed after %d milliseconds",
                                audioResumeTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < audioResumeTimeoutMs);
                previousAudioTimestamp = mMediaCodecPlayer.getTimestamp();
                Thread.sleep(50);
                postResumeAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            } while (postResumeAudioTimestamp.framePosition == previousAudioTimestamp.framePosition);
        }

        // Now that audio playback has resumed, wait until video playback resumes
        {
            // We actually don't care about trying to capture the exact time video resumed, because
            // we can just look at the historical list of rendered video timestamps
            long postResumeVideoTimeUs;
            long previousVideoTimeUs;
            long startTimeMs = System.currentTimeMillis();
            do {
                int videoResumeTimeoutMs = 2000;
                assertTrue(String.format("Video has not resumed after %d milliseconds",
                                videoResumeTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < videoResumeTimeoutMs);
                previousVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
                Thread.sleep(50);
                postResumeVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
            } while (postResumeVideoTimeUs == previousVideoTimeUs);
        }

        // The system time when rendering the first audio frame after the resume
        long playbackRateFps = mMediaCodecPlayer.getAudioTrack().getPlaybackRate();
        long playedFrames = postResumeAudioTimestamp.framePosition
                - underrunAudioTimestamp.framePosition + 1;
        double elapsedTimeNs = playedFrames * (1000.0 * 1000.0 * 1000.0 / playbackRateFps);
        long resumeAudioSystemTimeNs = postResumeAudioTimestamp.nanoTime - (long) elapsedTimeNs;
        long resumeAudioSystemTimeMs = resumeAudioSystemTimeNs / 1000 / 1000;

        // The system time when rendering the first video frame after video playback resumes
        long resumeVideoSystemTimeMs = mMediaCodecPlayer.getRenderedVideoFrameSystemTimeList()
                .get(pausedVideoRenderedTimestampIndex + 1) / 1000 / 1000;

        // Verify that video resumes in a reasonable amount of time after audio resumes
        // Note: Because a -100ms PTS gap is introduced, the video should resume 100ms later
        resumeAudioSystemTimeMs += 100;
        long resumeDeltaMs = resumeVideoSystemTimeMs - resumeAudioSystemTimeMs;
        assertTrue(String.format("Video started %s milliseconds before audio resumed "
                        + "(video:%d audio:%d)", resumeDeltaMs * -1, resumeVideoSystemTimeMs,
                        resumeAudioSystemTimeMs),
                resumeDeltaMs > 0); // video is expected to start after audio resumes
        assertTrue(String.format(
                        "Video started %d milliseconds after audio resumed (video:%d audio:%d)",
                        resumeDeltaMs, resumeVideoSystemTimeMs, resumeAudioSystemTimeMs),
                resumeDeltaMs <= 600); // video starting 300ms after audio is barely noticeable

        // Determine the system time of the audio frame that matches the presentation timestamp of
        // the resumed video frame
        long resumeVideoPresentationTimeUs = mMediaCodecPlayer.getRenderedVideoFrameTimestampList()
                .get(pausedVideoRenderedTimestampIndex + 1);
        long matchingAudioFramePosition = resumeVideoPresentationTimeUs * playbackRateFps / 1000 / 1000;
        playedFrames = matchingAudioFramePosition - postResumeAudioTimestamp.framePosition;
        elapsedTimeNs = playedFrames * (1000.0 * 1000.0 * 1000.0 / playbackRateFps);
        long matchingAudioSystemTimeNs = postResumeAudioTimestamp.nanoTime + (long) elapsedTimeNs;
        long matchingAudioSystemTimeMs = matchingAudioSystemTimeNs / 1000 / 1000;

        // Verify that video and audio are in sync at the time when video resumes
        // Note: Because a -100ms PTS gap is introduced, the video should resume 100ms later
        matchingAudioSystemTimeMs += 100;
        long avSyncOffsetMs =  resumeVideoSystemTimeMs - matchingAudioSystemTimeMs;
        assertTrue(String.format("Video is %d milliseconds out of sync of audio after resuming "
                        + "(video:%d, audio:%d)", avSyncOffsetMs, resumeVideoSystemTimeMs,
                        matchingAudioSystemTimeMs),
                // some leniency in AV sync is required because Android TV STB/OTT OEMs often have
                // to tune for imperfect downstream TVs (that have processing delays on the video)
                // by knowingly producing HDMI output that has audio and video mildly out of sync
                Math.abs(avSyncOffsetMs) <= 80);
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioUnderrunHevc() throws Exception {
        tunneledAudioUnderrun(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioUnderrunAvc() throws Exception {
        tunneledAudioUnderrun(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioUnderrunVp9() throws Exception {
        tunneledAudioUnderrun(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    private void sleepUntil(Supplier<Boolean> supplier, Duration maxWait) throws Exception {
        final long deadLineMs = System.currentTimeMillis() + maxWait.toMillis();
        do {
            Thread.sleep(50);
        } while (!supplier.get() && System.currentTimeMillis() < deadLineMs);
    }
}
