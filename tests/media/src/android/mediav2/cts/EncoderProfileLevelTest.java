/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.media.MediaCodecInfo.CodecProfileLevel.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;
import android.util.Pair;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * EncoderProfileLevelTest validates the profile, level information advertised by the component
 * in its codec capabilities. The test sets profile and level keys in media format and uses it
 * during encoder configuration. Upon successful configuration, frames are queued for encoding
 * (byte buffer mode) and the encoded output (bitstream) is expected to contain the same profile
 * and level information that was used during configure.
 * <p>
 * NOTE: The test configures profile, level information basing on standard guidelines, not
 * arbitrarily so encoders ARE expected to place these values in the bitstream as-is.
 * <p>
 * The test additionally checks if the output format returned by component contains same profile
 * and level information. Having output format contain this information is useful during muxing
 * <p>
 * As per cdd, if a device contains an encoder capable of encoding a profile/level combination
 * then it should contain a decoder capable of decoding the same profile/level combination. This
 * is verified.
 * <p>
 * If device implementations support encoding in a media type, then as per cdd they are expected to
 * handle certain profile and level configurations. This is verified as well.
 */
@RunWith(Parameterized.class)
public class EncoderProfileLevelTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = EncoderProfileLevelTest.class.getSimpleName();
    private static final HashMap<String, Pair<int[], Integer>> PROFILE_LEVEL_CDD = new HashMap<>();

    public EncoderProfileLevelTest(String encoder, String mediaType,
            EncoderConfigParams[] encCfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, encCfgParams, allTestParams);
    }

    private static EncoderConfigParams[] getVideoEncoderCfgParams(String mediaType, int bitRate,
            int width, int height, int frameRate, int colorFormat, int maxBframe, int[] profiles) {
        ArrayList<EncoderConfigParams> cfgParams = new ArrayList<>();
        for (int profile : profiles) {
            int level = getMinLevel(mediaType, width, height, frameRate, bitRate, profile);
            if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                    && maxBframe != 0
                    && (profile == AVCProfileBaseline
                    || profile == AVCProfileConstrainedBaseline)) {
                continue;
            }
            cfgParams.add(new EncoderConfigParams.Builder(mediaType)
                    .setBitRate(bitRate)
                    .setWidth(width)
                    .setHeight(height)
                    .setFrameRate(frameRate)
                    .setMaxBFrames(maxBframe)
                    .setProfile(profile)
                    .setLevel(level)
                    .setColorFormat(colorFormat)
                    .build());
        }
        return cfgParams.toArray(new EncoderConfigParams[0]);
    }

    private static EncoderConfigParams[] getAudioEncoderCfgParams(String mediaType, int bitRate,
            int sampleRate, int channelCount, int[] profiles) {
        EncoderConfigParams[] cfgParams = new EncoderConfigParams[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            cfgParams[i] = new EncoderConfigParams.Builder(mediaType)
                    .setBitRate(bitRate)
                    .setSampleRate(sampleRate)
                    .setChannelCount(channelCount)
                    .setProfile(profiles[i])
                    .build();
        }
        return cfgParams;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{3})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = true;
        final Object[][] exhaustiveArgsList = new Object[][]{
                // Audio - CodecMediaType, bit-rate, sample rate, channel count
                {MediaFormat.MIMETYPE_AUDIO_AAC, 64000, 48000, 1, -1},
                {MediaFormat.MIMETYPE_AUDIO_AAC, 128000, 48000, 2, -1},
                // Video - CodecMediaType, bit-rate, height, width, frame-rate
                // TODO (b/151423508)
                /*{MediaFormat.MIMETYPE_VIDEO_AVC, 64000, 176, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 128000, 176, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 192000, 352, 288, 7},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 384000, 352, 288, 15},*/
                {MediaFormat.MIMETYPE_VIDEO_AVC, 768000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 2000000, 352, 288, 30},
                // TODO (b/151423508)
                /*{MediaFormat.MIMETYPE_VIDEO_AVC, 4000000, 352, 576, 25},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 4000000, 720, 576, 12},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 10000000, 720, 576, 25},*/
                {MediaFormat.MIMETYPE_VIDEO_AVC, 14000000, 1280, 720, 30},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 20000000, 1280, 1024, 42},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 20000000, 2048, 1024, 30},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 50000000, 2048, 1024, 30},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 50000000, 2048, 1080, 60},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 135000000, 3672, 1536, 25},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 4096, 2304, 25},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 4096, 2304, 50},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 8192, 4320, 30},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 480000000, 8192, 4320, 60},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 800000000, 8192, 4320, 120},

                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 4000000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 15000000, 720, 576, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 60000000, 1440, 1088, 60},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 80000000, 1920, 1088, 60},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 80000000, 1920, 1088, 60},

                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 64000, 176, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 64000, 176, 144, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 128000, 176, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 128000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 384000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 4000000, 640, 480, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 8000000, 720, 576, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 12000000, 1280, 720, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 128000, 176, 144, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 384000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 768000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 1500000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 3000000, 704, 576, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 8000000, 720, 576, 30},

                {MediaFormat.MIMETYPE_VIDEO_VP9, 200000, 256, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 8000000, 384, 192, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1800000, 480, 256, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 3600000, 640, 384, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 7200000, 1080, 512, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 12000000, 1280, 768, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 18000000, 2048, 1088, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 30000000, 2048, 1088, 60},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 60000000, 4096, 2176, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 120000000, 4096, 2176, 60},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 180000000, 4096, 2176, 120},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 180000000, 8192, 4352, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 240000000, 8192, 4352, 60},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 480000000, 8192, 4352, 120},

                {MediaFormat.MIMETYPE_VIDEO_H263, 64000, 176, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_H263, 128000, 176, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_H263, 128000, 176, 144, 30},
                {MediaFormat.MIMETYPE_VIDEO_H263, 128000, 352, 288, 15},
                {MediaFormat.MIMETYPE_VIDEO_H263, 384000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_H263, 2048000, 352, 288, 30},
                {MediaFormat.MIMETYPE_VIDEO_H263, 4096000, 352, 240, 60},
                {MediaFormat.MIMETYPE_VIDEO_H263, 4096000, 352, 288, 50},
                {MediaFormat.MIMETYPE_VIDEO_H263, 8192000, 720, 240, 60},
                {MediaFormat.MIMETYPE_VIDEO_H263, 8192000, 720, 288, 50},
                {MediaFormat.MIMETYPE_VIDEO_H263, 16384000, 720, 480, 60},
                {MediaFormat.MIMETYPE_VIDEO_H263, 16384000, 720, 576, 50},

                {MediaFormat.MIMETYPE_VIDEO_HEVC, 128000, 176, 144, 15},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1500000, 352, 288, 30},
                // TODO (b/152576008) - Limit HEVC Encoder test to 512x512
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 3000000, 512, 512, 30},
                //{MediaFormat.MIMETYPE_VIDEO_HEVC, 3000000, 640, 360, 30},
                //{MediaFormat.MIMETYPE_VIDEO_HEVC, 6000000, 960, 540, 30},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 10000000, 1280, 720, 33},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 12000000, 2048, 1080, 30},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 2048, 1080, 60},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 25000000, 4096, 2160, 30},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 40000000, 4096, 2160, 60},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 60000000, 4096, 2160, 120},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 60000000, 8192, 4320, 30},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 120000000, 8192, 4320, 60},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 240000000, 8192, 4320, 120},

                {MediaFormat.MIMETYPE_VIDEO_AV1, 1500000, 426, 240, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 3000000, 640, 360, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 6000000, 854, 480, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 10000000, 1280, 720, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 12000000, 1920, 1080, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 20000000, 1920, 1080, 60},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 30000000, 3840, 2160, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 40000000, 3840, 2160, 60},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 60000000, 3840, 2160, 120},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 60000000, 7680, 4320, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 100000000, 7680, 4320, 60},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 160000000, 7680, 4320, 120},

                {MediaFormat.MIMETYPE_VIDEO_VP8, 512000, 176, 144, 20},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 512000, 480, 360, 20},
        };
        final List<Object[]> argsList = new ArrayList<>();
        final int[] maxBFrames = {0, 2};
        for (Object[] arg : exhaustiveArgsList) {
            final String mediaType = (String) arg[0];
            boolean isVideo = mediaType.startsWith("video/");
            final int br = (int) arg[1];
            final int param1 = (int) arg[2];
            final int param2 = (int) arg[3];
            final int fps = (int) arg[4];
            Object[] testArgs = new Object[3];
            testArgs[0] = arg[0];
            if (isVideo) {
                for (int maxBframe : maxBFrames) {
                    if (maxBframe != 0) {
                        if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                                && !mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                            continue;
                        }
                    }
                    testArgs[1] = getVideoEncoderCfgParams(mediaType, br, param1, param2, fps,
                            COLOR_FormatYUV420Flexible, maxBframe,
                            Objects.requireNonNull(PROFILE_SDR_MAP.get(mediaType)));
                    testArgs[2] = String.format("%dkbps_%dx%d_%dfps_%s_%d-bframes", br / 1000,
                            param1, param2, fps, colorFormatToString(COLOR_FormatYUV420Flexible, 8),
                            maxBframe);
                    argsList.add(testArgs);
                }
            } else {
                testArgs[1] = getAudioEncoderCfgParams(mediaType, br, param1, param2,
                        Objects.requireNonNull(PROFILE_SDR_MAP.get(mediaType)));
                testArgs[2] = String.format("%dkbps_%dkHz_%dch", br / 1000, param1 / 1000, param2);
                argsList.add(testArgs);
            }

            // P010 support was added in Android T, hence limit the following tests to Android
            // T and above
            if (IS_AT_LEAST_T && PROFILE_HLG_MAP.get(mediaType) != null) {
                for (int maxBframe : maxBFrames) {
                    if (maxBframe != 0) {
                        if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)
                                && !mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                            continue;
                        }
                    }
                    testArgs = new Object[3];
                    testArgs[0] = arg[0];
                    testArgs[1] = getVideoEncoderCfgParams(mediaType, br, param1, param2, fps,
                            COLOR_FormatYUVP010, maxBframe,
                            Objects.requireNonNull(PROFILE_HLG_MAP.get(mediaType)));
                    testArgs[2] = String.format("%dkbps_%dx%d_%dfps_%s_%d-bframes", br / 1000,
                            param1, param2, fps, colorFormatToString(COLOR_FormatYUVP010, 10),
                            maxBframe);
                    argsList.add(testArgs);
                }
            }
        }
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, false);
    }

    static {
        PROFILE_LEVEL_CDD.put(MediaFormat.MIMETYPE_AUDIO_AAC,
                new Pair<>(new int[]{AACObjectLC, AACObjectHE, AACObjectELD}, -1));
        PROFILE_LEVEL_CDD.put(MediaFormat.MIMETYPE_VIDEO_H263,
                new Pair<>(new int[]{H263ProfileBaseline}, H263Level45));
        PROFILE_LEVEL_CDD.put(MediaFormat.MIMETYPE_VIDEO_AVC,
                new Pair<>(new int[]{AVCProfileBaseline}, AVCLevel3));
        PROFILE_LEVEL_CDD.put(MediaFormat.MIMETYPE_VIDEO_HEVC,
                new Pair<>(new int[]{HEVCProfileMain}, HEVCMainTierLevel3));
        PROFILE_LEVEL_CDD.put(MediaFormat.MIMETYPE_VIDEO_VP8,
                new Pair<>(new int[]{VP8ProfileMain}, VP8Level_Version0));
        PROFILE_LEVEL_CDD.put(MediaFormat.MIMETYPE_VIDEO_VP9,
                new Pair<>(new int[]{VP9Profile0}, VP9Level3));
    }

    private static int getMinLevel(String mediaType, int width, int height, int frameRate,
            int bitrate, int profile) {
        switch (mediaType) {
            case MediaFormat.MIMETYPE_VIDEO_AVC:
                return getMinLevelAVC(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
                return getMinLevelHEVC(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_H263:
                return getMinLevelH263(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_MPEG2:
                return getMinLevelMPEG2(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_MPEG4:
                return getMinLevelMPEG4(width, height, frameRate, bitrate, profile);
            // complex features disabled in VP8 Level/Version 0
            case MediaFormat.MIMETYPE_VIDEO_VP8:
                return VP8Level_Version0;
            case MediaFormat.MIMETYPE_VIDEO_VP9:
                return getMinLevelVP9(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_AV1:
                return getMinLevelAV1(width, height, frameRate, bitrate);
            default:
                return -1;
        }
    }

    private static int getMinLevelAVC(int width, int height, int frameRate, int bitrate) {
        class LevelLimitAVC {
            private LevelLimitAVC(int level, int mbsPerSec, long mbs, int bitrate) {
                this.level = level;
                this.mbsPerSec = mbsPerSec;
                this.mbs = mbs;
                this.bitrate = bitrate;
            }

            private final int level;
            private final int mbsPerSec;
            private final long mbs;
            private final int bitrate;
        }
        LevelLimitAVC[] limitsAVC = {
                new LevelLimitAVC(AVCLevel1, 1485, 99, 64000),
                new LevelLimitAVC(AVCLevel1b, 1485, 99, 128000),
                new LevelLimitAVC(AVCLevel11, 3000, 396, 192000),
                new LevelLimitAVC(AVCLevel12, 6000, 396, 384000),
                new LevelLimitAVC(AVCLevel13, 11880, 396, 768000),
                new LevelLimitAVC(AVCLevel2, 11880, 396, 2000000),
                new LevelLimitAVC(AVCLevel21, 19800, 792, 4000000),
                new LevelLimitAVC(AVCLevel22, 20250, 1620, 4000000),
                new LevelLimitAVC(AVCLevel3, 40500, 1620, 10000000),
                new LevelLimitAVC(AVCLevel31, 108000, 3600, 14000000),
                new LevelLimitAVC(AVCLevel32, 216000, 5120, 20000000),
                new LevelLimitAVC(AVCLevel4, 245760, 8192, 20000000),
                new LevelLimitAVC(AVCLevel41, 245760, 8192, 50000000),
                new LevelLimitAVC(AVCLevel42, 522240, 8704, 50000000),
                new LevelLimitAVC(AVCLevel5, 589824, 22080, 135000000),
                new LevelLimitAVC(AVCLevel51, 983040, 36864, 240000000),
                new LevelLimitAVC(AVCLevel52, 2073600, 36864, 240000000),
                new LevelLimitAVC(AVCLevel6, 4177920, 139264, 240000000),
                new LevelLimitAVC(AVCLevel61, 8355840, 139264, 480000000),
                new LevelLimitAVC(AVCLevel62, 16711680, 139264, 800000000),
        };
        int mbs = ((width + 15) / 16) * ((height + 15) / 16);
        float mbsPerSec = mbs * frameRate;
        for (LevelLimitAVC levelLimitsAVC : limitsAVC) {
            if (mbs <= levelLimitsAVC.mbs && mbsPerSec <= levelLimitsAVC.mbsPerSec
                    && bitrate <= levelLimitsAVC.bitrate) {
                return levelLimitsAVC.level;
            }
        }
        // if none of the levels suffice, select the highest level
        return AVCLevel62;
    }

    private static int getMinLevelHEVC(int width, int height, int frameRate, int bitrate) {
        class LevelLimitHEVC {
            private LevelLimitHEVC(int level, int frameRate, long samples, int bitrate) {
                this.level = level;
                this.frameRate = frameRate;
                this.samples = samples;
                this.bitrate = bitrate;
            }

            private final int level;
            private final int frameRate;
            private final long samples;
            private final int bitrate;
        }
        LevelLimitHEVC[] limitsHEVC = {
                new LevelLimitHEVC(HEVCMainTierLevel1, 15, 36864, 128000),
                new LevelLimitHEVC(HEVCMainTierLevel2, 30, 122880, 1500000),
                new LevelLimitHEVC(HEVCMainTierLevel21, 30, 245760, 3000000),
                new LevelLimitHEVC(HEVCMainTierLevel3, 30, 552960, 6000000),
                new LevelLimitHEVC(HEVCMainTierLevel31, 30, 983040, 10000000),
                new LevelLimitHEVC(HEVCMainTierLevel4, 30, 2228224, 12000000),
                new LevelLimitHEVC(HEVCHighTierLevel4, 30, 2228224, 30000000),
                new LevelLimitHEVC(HEVCMainTierLevel41, 60, 2228224, 20000000),
                new LevelLimitHEVC(HEVCHighTierLevel41, 60, 2228224, 50000000),
                new LevelLimitHEVC(HEVCMainTierLevel5, 30, 8912896, 25000000),
                new LevelLimitHEVC(HEVCHighTierLevel5, 30, 8912896, 100000000),
                new LevelLimitHEVC(HEVCMainTierLevel51, 60, 8912896, 40000000),
                new LevelLimitHEVC(HEVCHighTierLevel51, 60, 8912896, 160000000),
                new LevelLimitHEVC(HEVCMainTierLevel52, 120, 8912896, 60000000),
                new LevelLimitHEVC(HEVCHighTierLevel52, 120, 8912896, 240000000),
                new LevelLimitHEVC(HEVCMainTierLevel6, 30, 35651584, 60000000),
                new LevelLimitHEVC(HEVCHighTierLevel6, 30, 35651584, 240000000),
                new LevelLimitHEVC(HEVCMainTierLevel61, 60, 35651584, 120000000),
                new LevelLimitHEVC(HEVCHighTierLevel61, 60, 35651584, 480000000),
                new LevelLimitHEVC(HEVCMainTierLevel62, 120, 35651584, 240000000),
                new LevelLimitHEVC(HEVCHighTierLevel62, 120, 35651584, 800000000),
        };
        int samples = width * height;
        for (LevelLimitHEVC levelLimitsHEVC : limitsHEVC) {
            if (samples <= levelLimitsHEVC.samples && frameRate <= levelLimitsHEVC.frameRate
                    && bitrate <= levelLimitsHEVC.bitrate) {
                return levelLimitsHEVC.level;
            }
        }
        // if none of the levels suffice, select the highest level
        return HEVCHighTierLevel62;
    }

    private static int getMinLevelH263(int width, int height, int frameRate, int bitrate) {
        class LevelLimitH263 {
            private LevelLimitH263(int level, int height, int width, int frameRate,
                    int bitrate) {
                this.level = level;
                this.height = height;
                this.width = width;
                this.frameRate = frameRate;
                this.bitrate = bitrate;
            }

            private final int level;
            private final int height;
            private final int width;
            private final int frameRate;
            private final int bitrate;
        }
        LevelLimitH263[] limitsH263 = {
                new LevelLimitH263(H263Level10, 176, 144, 15, 64000),
                new LevelLimitH263(H263Level45, 176, 144, 15, 128000),
                new LevelLimitH263(H263Level20, 176, 144, 30, 128000),
                new LevelLimitH263(H263Level20, 352, 288, 15, 128000),
                new LevelLimitH263(H263Level30, 352, 288, 30, 384000),
                new LevelLimitH263(H263Level40, 352, 288, 30, 2048000),
                new LevelLimitH263(H263Level50, 352, 240, 60, 4096000),
                new LevelLimitH263(H263Level50, 352, 288, 50, 4096000),
                new LevelLimitH263(H263Level60, 720, 240, 60, 8192000),
                new LevelLimitH263(H263Level60, 720, 288, 50, 8192000),
                new LevelLimitH263(H263Level70, 720, 480, 60, 16384000),
                new LevelLimitH263(H263Level70, 720, 576, 50, 16384000),
        };
        for (LevelLimitH263 levelLimitsH263 : limitsH263) {
            if (height <= levelLimitsH263.height && width <= levelLimitsH263.width &&
                    frameRate <= levelLimitsH263.frameRate && bitrate <= levelLimitsH263.bitrate) {
                return levelLimitsH263.level;
            }
        }
        // if none of the levels suffice, select the highest level
        return H263Level70;
    }

    private static int getMinLevelVP9(int width, int height, int frameRate, int bitrate) {
        class LevelLimitVP9 {
            private LevelLimitVP9(int level, long sampleRate, int size, int breadth,
                    int bitrate) {
                this.level = level;
                this.sampleRate = sampleRate;
                this.size = size;
                this.breadth = breadth;
                this.bitrate = bitrate;
            }

            private final int level;
            private final long sampleRate;
            private final int size;
            private final int breadth;
            private final int bitrate;
        }
        LevelLimitVP9[] limitsVP9 = {
                new LevelLimitVP9(VP9Level1, 829440, 36864, 512, 200000),
                new LevelLimitVP9(VP9Level11, 2764800, 73728, 768, 800000),
                new LevelLimitVP9(VP9Level2, 4608000, 122880, 960, 1800000),
                new LevelLimitVP9(VP9Level21, 9216000, 245760, 1344, 3600000),
                new LevelLimitVP9(VP9Level3, 20736000, 552960, 2048, 7200000),
                new LevelLimitVP9(VP9Level31, 36864000, 983040, 2752, 12000000),
                new LevelLimitVP9(VP9Level4, 83558400, 2228224, 4160, 18000000),
                new LevelLimitVP9(VP9Level41, 160432128, 2228224, 4160, 30000000),
                new LevelLimitVP9(VP9Level5, 311951360, 8912896, 8384, 60000000),
                new LevelLimitVP9(VP9Level51, 588251136, 8912896, 8384, 120000000),
                new LevelLimitVP9(VP9Level52, 1176502272, 8912896, 8384, 180000000),
                new LevelLimitVP9(VP9Level6, 1176502272, 35651584, 16832, 180000000),
                new LevelLimitVP9(VP9Level61, 2353004544L, 35651584, 16832, 240000000),
                new LevelLimitVP9(VP9Level62, 4706009088L, 35651584, 16832, 480000000),
        };
        int size = width * height;
        int sampleRate = size * frameRate;
        int breadth = Math.max(width, height);
        for (LevelLimitVP9 levelLimitsVP9 : limitsVP9) {
            if (sampleRate <= levelLimitsVP9.sampleRate && size <= levelLimitsVP9.size &&
                    breadth <= levelLimitsVP9.breadth && bitrate <= levelLimitsVP9.bitrate) {
                return levelLimitsVP9.level;
            }
        }
        // if none of the levels suffice, select the highest level
        return VP9Level62;
    }

    private static int getMinLevelMPEG2(int width, int height, int frameRate, int bitrate) {
        class LevelLimitMPEG2 {
            private LevelLimitMPEG2(int level, long sampleRate, int width, int height,
                    int frameRate, int bitrate) {
                this.level = level;
                this.sampleRate = sampleRate;
                this.width = width;
                this.height = height;
                this.frameRate = frameRate;
                this.bitrate = bitrate;
            }

            private final int level;
            private final long sampleRate;
            private final int width;
            private final int height;
            private final int frameRate;
            private final int bitrate;
        }
        // main profile limits, higher profiles will also support selected level
        LevelLimitMPEG2[] limitsMPEG2 = {
                new LevelLimitMPEG2(MPEG2LevelLL, 3041280, 352, 288, 30, 4000000),
                new LevelLimitMPEG2(MPEG2LevelML, 10368000, 720, 576, 30, 15000000),
                new LevelLimitMPEG2(MPEG2LevelH14, 47001600, 1440, 1088, 60, 60000000),
                new LevelLimitMPEG2(MPEG2LevelHL, 62668800, 1920, 1088, 60, 80000000),
                new LevelLimitMPEG2(MPEG2LevelHP, 125337600, 1920, 1088, 60, 80000000),
        };
        int size = width * height;
        int sampleRate = size * frameRate;
        for (LevelLimitMPEG2 levelLimitsMPEG2 : limitsMPEG2) {
            if (sampleRate <= levelLimitsMPEG2.sampleRate && width <= levelLimitsMPEG2.width &&
                    height <= levelLimitsMPEG2.height && frameRate <= levelLimitsMPEG2.frameRate &&
                    bitrate <= levelLimitsMPEG2.bitrate) {
                return levelLimitsMPEG2.level;
            }
        }
        // if none of the levels suffice, select the highest level
        return MPEG2LevelHP;
    }

    private static int getMinLevelMPEG4(int width, int height, int frameRate, int bitrate,
            int profile) {
        class LevelLimitMPEG4 {
            private LevelLimitMPEG4(int profile, int level, long sampleRate, int width,
                    int height, int frameRate, int bitrate) {
                this.profile = profile;
                this.level = level;
                this.sampleRate = sampleRate;
                this.width = width;
                this.height = height;
                this.frameRate = frameRate;
                this.bitrate = bitrate;
            }

            private final int profile;
            private final int level;
            private final long sampleRate;
            private final int width;
            private final int height;
            private final int frameRate;
            private final int bitrate;
        }
        // simple profile limits, higher profiles will also support selected level
        LevelLimitMPEG4[] limitsMPEG4 = {
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level0, 380160, 176, 144, 15, 64000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level1, 380160, 176, 144, 30, 64000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level0b, 380160, 176, 144, 15, 128000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level2, 1520640, 352, 288, 30, 128000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level3, 3041280, 352, 288, 30, 384000),
                new LevelLimitMPEG4(
                        MPEG4ProfileSimple, MPEG4Level4a, 9216000, 640, 480, 30, 4000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileSimple, MPEG4Level5, 10368000, 720, 576, 30, 8000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileSimple, MPEG4Level6, 27648000, 1280, 720, 30, 12000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level1, 760320, 176, 144, 30, 128000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level2, 1520640, 352, 288, 30, 384000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level3, 3041280, 352, 288, 30, 768000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level3b, 3041280, 352, 288, 30, 1500000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level4, 3041280, 704, 576, 30, 3000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level5, 3041280, 720, 576, 30, 8000000),
        };
        int size = width * height;
        int sampleRate = size * frameRate;
        for (LevelLimitMPEG4 levelLimitsMPEG4 : limitsMPEG4) {
            if (((profile & (MPEG4ProfileAdvancedSimple | MPEG4ProfileSimple)) != 0) &&
                    profile != levelLimitsMPEG4.profile) continue;
            if (sampleRate <= levelLimitsMPEG4.sampleRate && width <= levelLimitsMPEG4.width &&
                    height <= levelLimitsMPEG4.height && frameRate <= levelLimitsMPEG4.frameRate &&
                    bitrate <= levelLimitsMPEG4.bitrate) {
                return levelLimitsMPEG4.level;
            }
        }
        // if none of the levels suffice, select the highest level
        return MPEG4Level6;
    }

    private static int getMinLevelAV1(int width, int height, int frameRate, int bitrate) {
        class LevelLimitAV1 {
            private LevelLimitAV1(int level, int size, int width, int height, long sampleRate,
                    int bitrate) {
                this.level = level;
                this.size = size;
                this.width = width;
                this.height = height;
                this.sampleRate = sampleRate;
                this.bitrate = bitrate;
            }

            private final int level;
            private final int size;
            private final int width;
            private final int height;
            private final long sampleRate;
            private final int bitrate;
        }
        // taking bitrate from main profile, will also be supported by high profile
        LevelLimitAV1[] limitsAV1 = {
                new LevelLimitAV1(AV1Level2, 147456, 2048, 1152, 4423680, 1500000),
                new LevelLimitAV1(AV1Level21, 278784, 2816, 1584, 8363520, 3000000),
                new LevelLimitAV1(AV1Level3, 665856, 4352, 2448, 19975680, 6000000),
                new LevelLimitAV1(AV1Level31, 1065024, 5504, 3096, 31950720, 10000000),
                new LevelLimitAV1(AV1Level4, 2359296, 6144, 3456, 70778880, 12000000),
                new LevelLimitAV1(AV1Level41, 2359296, 6144, 3456, 141557760, 20000000),
                new LevelLimitAV1(AV1Level5, 8912896, 8192, 4352, 267386880, 30000000),
                new LevelLimitAV1(AV1Level51, 8912896, 8192, 4352, 534773760, 40000000),
                new LevelLimitAV1(AV1Level52, 8912896, 8192, 4352, 1069547520, 60000000),
                new LevelLimitAV1(AV1Level53, 8912896, 8192, 4352, 1069547520, 60000000),
                new LevelLimitAV1(AV1Level6, 35651584, 16384, 8704, 1069547520, 60000000),
                new LevelLimitAV1(AV1Level61, 35651584, 16384, 8704, 2139095040, 100000000),
                new LevelLimitAV1(AV1Level62, 35651584, 16384, 8704, 4278190080L, 160000000),
                new LevelLimitAV1(AV1Level63, 35651584, 16384, 8704, 4278190080L, 160000000),
        };
        int size = width * height;
        int sampleRate = size * frameRate;
        for (LevelLimitAV1 levelLimitsAV1 : limitsAV1) {
            if (size <= levelLimitsAV1.size && width <= levelLimitsAV1.width &&
                    height <= levelLimitsAV1.height && sampleRate <= levelLimitsAV1.sampleRate &&
                    bitrate <= levelLimitsAV1.bitrate) {
                return levelLimitsAV1.level;
            }
        }
        // if none of the levels suffice or high profile, select the highest level
        return AV1Level73;
    }

    private int getAacProfile(MediaFormat format) {
        int aacProfile = format.getInteger(MediaFormat.KEY_AAC_PROFILE, -1);
        int profile = format.getInteger(MediaFormat.KEY_PROFILE, -1);

        if (aacProfile != -1 && profile != -1) {
            assertEquals(String.format("aac-profile :- %d and profile :- %d are different.",
                    aacProfile, profile), aacProfile, profile);
            return aacProfile;
        } else if (aacProfile != -1) {
            return aacProfile;
        } else if (profile != -1) {
            return profile;
        } else {
            Log.e(LOG_TAG, "format doesn't contain either KEY_AAC_PROFILE or KEY_PROFILE");
            return -1;
        }
    }

    @Override
    public boolean isFormatSimilar(MediaFormat inpFormat, MediaFormat outFormat) {
        if (!super.isFormatSimilar(inpFormat, outFormat)) {
            Log.e(LOG_TAG, "Basic channel-rate/resolution comparisons failed");
            return false;
        }
        String inpMediaType = inpFormat.getString(MediaFormat.KEY_MIME);
        String outMediaType = outFormat.getString(MediaFormat.KEY_MIME);
        assertEquals(String.format("input mediaType :- %s and output mediaType :- %s are "
                        + "different.", inpMediaType, outMediaType), inpMediaType, outMediaType);
        if (outMediaType.startsWith("audio/")) {
            if (outFormat.getString(MediaFormat.KEY_MIME).equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                int inputProfileKey, outputProfileKey;
                outputProfileKey = getAacProfile(outFormat);
                inputProfileKey = getAacProfile(inpFormat);
                if (outputProfileKey != inputProfileKey) {
                    Log.e(LOG_TAG, "aac-profile in output " + outputProfileKey +
                            " doesn't match configured input " + inputProfileKey);
                    return false;
                }
            }
        } else if (outMediaType.startsWith("video/")) {
            if (!outFormat.containsKey(MediaFormat.KEY_PROFILE)) {
                Log.e(LOG_TAG, "Output format doesn't contain profile key");
                //TODO (b/151398466)
                if (true) return true;
                return false;
            }
            if (!outFormat.containsKey(MediaFormat.KEY_LEVEL)) {
                Log.e(LOG_TAG, "Output format doesn't contain level key");
                //TODO (b/151398466)
                if (true) return true;
                return false;
            }
            if (!inpFormat.containsKey(MediaFormat.KEY_PROFILE)) {
                Log.e(LOG_TAG, "Input format doesn't contain profile key");
                return false;
            }
            if (!inpFormat.containsKey(MediaFormat.KEY_LEVEL)) {
                Log.e(LOG_TAG, "Input format doesn't contain level key");
                return false;
            }
            if (outFormat.getInteger(MediaFormat.KEY_PROFILE)
                    != inpFormat.getInteger(MediaFormat.KEY_PROFILE)) {
                Log.e(LOG_TAG, "profile in output doesn't match configured input");
                return false;
            }
            if (outFormat.getInteger(MediaFormat.KEY_LEVEL)
                    != inpFormat.getInteger(MediaFormat.KEY_LEVEL)) {
                Log.e(LOG_TAG, "level key in output doesn't match configured input");
                return false;
            }
        } else {
            Log.w(LOG_TAG, "non media mediaType:" + outMediaType);
        }
        return true;
    }

    /**
     * @see EncoderProfileLevelTest
     * Besides the above, the test muxes the encoder output in all supported container formats
     * and checks if muxers and extractors on device are signalling profile/level information
     * correctly
     */
    @CddTest(requirements = {"2.2.2/5.1/H-0-3", "2.2.2/5.1/H-0-4", "2.2.2/5.1/H-0-5", "5/C-0-3",
            "5.2.1/C-1-1", "5.2.2/C-2-1", "5.2.3/C-2-1", "5.2.4/C-1-2",
            "5.2.5/C-1-1"})
    @ApiTest(apis = {"android.media.MediaFormat#KEY_PROFILE",
            "android.media.MediaFormat#KEY_AAC_PROFILE",
            "android.media.MediaFormat#KEY_LEVEL"})
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testValidateProfileLevel() throws IOException, InterruptedException {
        if (mEncCfgParams[0].mInputBitDepth != 8) {
            Assume.assumeTrue(mCodecName + " doesn't support " + colorFormatToString(
                            mEncCfgParams[0].mColorFormat, mEncCfgParams[0].mInputBitDepth),
                    hasSupportForColorFormat(mCodecName, mMediaType,
                                             mEncCfgParams[0].mColorFormat));
        }
        boolean cddSupportedMediaType = PROFILE_LEVEL_CDD.get(mMediaType) != null;
        int[] profileCdd = new int[0];
        int levelCdd = 0;
        if (cddSupportedMediaType) {
            Pair<int[], Integer> cddProfileLevel = PROFILE_LEVEL_CDD.get(mMediaType);
            profileCdd = cddProfileLevel.first;
            levelCdd = cddProfileLevel.second;
        }

        {
            mActiveRawRes = EncoderInput.getRawResource(mEncCfgParams[0]);
            assertNotNull("no raw resource found for testing config : "
                    + mActiveEncCfg + mTestConfig + mTestEnv, mActiveRawRes);
            setUpSource(mActiveRawRes.mFileName);
            mSaveToMem = true;
            mOutputBuff = new OutputManager();
            mCodec = MediaCodec.createByCodecName(mCodecName);
            MediaCodecInfo.CodecCapabilities codecCapabilities =
                    mCodec.getCodecInfo().getCapabilitiesForType(mMediaType);
            for (EncoderConfigParams cfg : mEncCfgParams) {
                mActiveEncCfg = cfg;
                MediaFormat format = cfg.getFormat();
                if (!codecCapabilities.isFormatSupported(format)) {
                    if (cddSupportedMediaType) {
                        boolean shallSupportProfileLevel = false;
                        if (mIsAudio) {
                            for (int cddProfile : profileCdd) {
                                if (cfg.mProfile == cddProfile) {
                                    shallSupportProfileLevel = true;
                                    break;
                                }
                            }
                        } else if (cfg.mProfile == profileCdd[0] && cfg.mLevel <= levelCdd) {
                            shallSupportProfileLevel = true;
                        }
                        if (shallSupportProfileLevel) {
                            ArrayList<MediaFormat> formats = new ArrayList<>();
                            formats.add(format);
                            assertFalse(String.format("No components present on the device supports"
                                    + " cdd required profile:- %d, level:- %d, encode format:- %s",
                                    cfg.mProfile, cfg.mLevel, format),
                                    selectCodecs(mMediaType, formats, null, false).isEmpty());
                        }
                        Log.d(LOG_TAG, mCodecName + " doesn't support format: " + format);
                    }
                    continue;
                }

                // Verify if device has an equivalent decoder for the current format
                {
                    ArrayList<MediaFormat> formatList = new ArrayList<>();
                    formatList.add(format);
                    assertTrue("Device advertises support for encoding " + format
                                    + " but cannot decode it. \n" + mTestConfig + mTestEnv,
                            selectCodecs(mMediaType, formatList, null, false).size() > 0);
                }

                mOutputBuff.reset();
                configureCodec(format, false, true, true);
                mCodec.start();
                doWork(5);
                queueEOS();
                waitForAllOutputs();
                MediaFormat outFormat = mCodec.getOutputFormat();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();

                // TODO (b/151398466)
                if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                    Assume.assumeTrue("neither KEY_AAC_PROFILE nor KEY_PROFILE are present",
                            outFormat.containsKey(MediaFormat.KEY_AAC_PROFILE) ||
                                    outFormat.containsKey(MediaFormat.KEY_PROFILE));
                } else {
                    Assume.assumeTrue("KEY_PROFILE not present",
                            outFormat.containsKey(MediaFormat.KEY_PROFILE));
                    Assume.assumeTrue(outFormat.containsKey(MediaFormat.KEY_LEVEL));
                }
                // TODO (b/166300446) avc mediaType fails validation
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                    Log.w(LOG_TAG, "Skip validation for mediaType = " + mMediaType);
                    continue;
                }
                // TODO (b/166305723) hevc mediaType fails validation
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                    Log.w(LOG_TAG, "Skip validation for mediaType = " + mMediaType);
                    continue;
                }
                // TODO (b/166300448) h263 and mpeg4 mediaTypes fails validation
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_H263)
                        || mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
                    Log.w(LOG_TAG, "Skip validation for mediaType = " + mMediaType);
                    continue;
                }
                // TODO (b/184889671) aac for profile AACObjectHE fails validation
                // TODO (b/184890155) aac for profile AACObjectLD, AACObjectELD fails validation
                if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                    if (cfg.mProfile == AACObjectHE || cfg.mProfile == AACObjectELD
                            || cfg.mProfile == AACObjectLD) {
                        Log.w(LOG_TAG, "Skip validation for mediaType = " + mMediaType
                                + " profile " + cfg.mProfile);
                        continue;
                    }
                }
                String msg = String.format("Configured input format and received output format are "
                        + "not similar. \nConfigured Input format is :- %s \nReceived Output "
                        + "format is :- %s \n", format, outFormat);
                assertTrue(msg + mTestConfig + mTestEnv, isFormatSimilar(format, outFormat));

                for (int muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_FIRST;
                     muxerFormat <= MediaMuxer.OutputFormat.MUXER_OUTPUT_LAST; muxerFormat++) {
                    if (!isMediaTypeContainerPairValid(mMediaType, muxerFormat)) continue;
                    ByteBuffer mBuff = mOutputBuff.getBuffer();
                    String tmpPath = getTempFilePath((cfg.mInputBitDepth == 10) ? "10bit" : "");
                    muxOutput(tmpPath, muxerFormat, outFormat, mBuff, mInfoList);
                    MediaExtractor extractor = new MediaExtractor();
                    extractor.setDataSource(tmpPath);
                    assertEquals("Should be only 1 track \n" + mTestConfig + mTestEnv, 1,
                            extractor.getTrackCount());
                    MediaFormat extractedFormat = extractor.getTrackFormat(0);
                    if (!isFormatSimilar(outFormat, extractedFormat)) {
                        msg = " Input format and extracted format are not similar. "
                                + "\n Muxer input format :- " + outFormat
                                + "\n Extracted format :- " + extractedFormat
                                + "\n Muxer writer :- " + muxerFormat + "\n" + mTestConfig
                                + mTestEnv;
                        fail(msg);
                    }
                    extractor.release();
                    new File(tmpPath).delete();
                }
            }
            mCodec.release();
        }
    }
}
