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
 * distributed under the License is distributed on an "AS IS" BASIS
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the Licnse.
 */

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.os.Build;

import com.google.common.base.Preconditions;

import org.junit.rules.TestName;

import java.util.HashSet;
import java.util.Set;

/**
 * Logs a set of measurements and results for defined performance class requirements.
 */
public class PerformanceClassEvaluator {
    private static final String TAG = PerformanceClassEvaluator.class.getSimpleName();

    private final String mTestName;
    private Set<Requirement> mRequirements;

    public PerformanceClassEvaluator(TestName testName) {
        Preconditions.checkNotNull(testName);
        this.mTestName = testName.getMethodName();
        this.mRequirements = new HashSet<Requirement>();
    }

    // used for requirements [7.1.1.1/H-1-1], [7.1.1.1/H-2-1]
    public static class ResolutionRequirement extends Requirement {
        private static final String TAG = ResolutionRequirement.class.getSimpleName();

        private ResolutionRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setLongResolution(int longResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.LONG_RESOLUTION, longResolution);
        }

        public void setShortResolution(int shortResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.SHORT_RESOLUTION, shortResolution);
        }

        /**
         * [7.1.1.1/H-1-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_1_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.LONG_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.R, 1920)
                .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.SHORT_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.R, 1080)
                .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_1_1, long_resolution,
                short_resolution);
        }

        /**
         * [7.1.1.1/H-2-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_2_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.LONG_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.S, 1920)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1920)
                .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.SHORT_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.S, 1080)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_2_1, long_resolution,
                short_resolution);
        }
    }

    // used for requirements [7.1.1.3/H-1-1], [7.1.1.3/H-2-1]
    public static class DensityRequirement extends Requirement {
        private static final String TAG = DensityRequirement.class.getSimpleName();

        private DensityRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setDisplayDensity(int displayDensity) {
            this.<Integer>setMeasuredValue(RequirementConstants.DISPLAY_DENSITY, displayDensity);
        }

        /**
         * [7.1.1.3/H-1-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_1_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.DISPLAY_DENSITY)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.R, 400)
                .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_1_1, display_density);
        }

        /**
         * [7.1.1.3/H-2-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_2_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.DISPLAY_DENSITY)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.S, 400)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 400)
                .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_2_1, display_density);
        }
    }

    // used for requirements [7.6.1/H-1-1], [7.6.1/H-2-1]
    public static class MemoryRequirement extends Requirement {
        private static final String TAG = MemoryRequirement.class.getSimpleName();

        private MemoryRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setPhysicalMemory(long physicalMemory) {
            this.<Long>setMeasuredValue(RequirementConstants.PHYSICAL_MEMORY, physicalMemory);
        }

        /**
         * [7.6.1/H-1-1] MUST have at least 6 GB of physical memory.
         */
        public static MemoryRequirement createR7_6_1__H_1_1() {
            RequiredMeasurement<Long> physical_memory = RequiredMeasurement
                .<Long>builder()
                .setId(RequirementConstants.PHYSICAL_MEMORY)
                .setPredicate(RequirementConstants.LONG_GTE)
                // Media performance requires 6 GB minimum RAM, but keeping the following to 5 GB
                // as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
                .addRequiredValue(Build.VERSION_CODES.R, 5L * 1024L)
                .build();

            return new MemoryRequirement(RequirementConstants.R7_6_1__H_1_1, physical_memory);
        }

        /**
         * [7.6.1/H-2-1] MUST have at least 6/8 GB of physical memory.
         */
        public static MemoryRequirement createR7_6_1__H_2_1() {
            RequiredMeasurement<Long> physical_memory = RequiredMeasurement
                .<Long>builder()
                .setId(RequirementConstants.PHYSICAL_MEMORY)
                .setPredicate(RequirementConstants.LONG_GTE)
                // Media performance requires 6/8 GB minimum RAM, but keeping the following to
                // 5/7 GB as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
                .addRequiredValue(Build.VERSION_CODES.S, 5L * 1024L)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 7L * 1024L)
                .build();

            return new MemoryRequirement(RequirementConstants.R7_6_1__H_2_1, physical_memory);
        }
    }

    public static class CodecInitLatencyRequirement extends Requirement {

        private static final String TAG = CodecInitLatencyRequirement.class.getSimpleName();

        private CodecInitLatencyRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setCodecInitLatencyMs(long codecInitLatencyMs) {
            this.setMeasuredValue(RequirementConstants.CODEC_INIT_LATENCY, codecInitLatencyMs);
        }

        /**
         * [2.2.7.1/5.1/H-1-7] MUST have a codec initialization latency of 65(R) / 50(S) / 40(T)
         * ms or less for a 1080p or smaller video encoding session for all hardware video
         * encoders when under load. Load here is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs together with the 1080p
         * audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_7() {
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                    .setPredicate(RequirementConstants.LONG_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 65L)
                    .addRequiredValue(Build.VERSION_CODES.S, 50L)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 40L)
                    .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_7,
                codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-8] MUST have a codec initialization latency of 50(R) / 40(S) / 30(T)
         * ms or less for a 128 kbps or lower bitrate audio encoding session for all audio
         * encoders when under load. Load here is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs together with the 1080p
         * audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_8() {
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                    .setPredicate(RequirementConstants.LONG_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 50L)
                    .addRequiredValue(Build.VERSION_CODES.S, 40L)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 30L)
                    .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_8,
                codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-12] Codec initialization latency of 40ms or less for a 1080p or
         * smaller video decoding session for all hardware video encoders when under load. Load
         * here is defined as a concurrent 1080p to 720p video-only transcoding session using
         * hardware video codecs together with the 1080p audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_12() {
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                    .setPredicate(RequirementConstants.LONG_LTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 40L)
                    .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_12,
                    codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-13] Codec initialization latency of 30ms or less for a 128kbps or
         * lower bitrate audio decoding session for all audio encoders when under load. Load here
         * is defined as a concurrent 1080p to 720p video-only transcoding session using hardware
         * video codecs together with the 1080p audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_13() {
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                    .setPredicate(RequirementConstants.LONG_LTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 30L)
                    .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_13,
                    codec_init_latency);
        }
    }

    // used for requirements [2.2.7.1/5.3/H-1-1], [2.2.7.1/5.3/H-1-2]
    public static class FrameDropRequirement extends Requirement {
        private static final String TAG = FrameDropRequirement.class.getSimpleName();

        private FrameDropRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setFramesDropped(int framesDropped) {
            this.setMeasuredValue(RequirementConstants.FRAMES_DROPPED, framesDropped);
        }

        public void setFrameRate(double frameRate) {
            this.setMeasuredValue(RequirementConstants.FRAME_RATE, frameRate);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 1 frames in 10 seconds (i.e less than 0.333
         * percent frame drop) for a 1080p 30 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128 kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_R() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.FRAMES_DROPPED)
                .setPredicate(RequirementConstants.INTEGER_LTE)
                // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                .addRequiredValue(Build.VERSION_CODES.R, 3)
                .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                .<Double>builder()
                .setId(RequirementConstants.FRAME_RATE)
                .setPredicate(RequirementConstants.DOUBLE_EQ)
                .addRequiredValue(Build.VERSION_CODES.R, 30.0)
                .build();

            return new FrameDropRequirement(RequirementConstants.R5_3__H_1_1, frameDropped,
                frameRate);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 1 frame in 10 seconds during a video
         * resolution change in a 30 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_R() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.FRAMES_DROPPED)
                .setPredicate(RequirementConstants.INTEGER_LTE)
                // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                .addRequiredValue(Build.VERSION_CODES.R, 3)
                .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                .<Double>builder()
                .setId(RequirementConstants.FRAME_RATE)
                .setPredicate(RequirementConstants.DOUBLE_EQ)
                .addRequiredValue(Build.VERSION_CODES.R, 30.0)
                .build();

            return new FrameDropRequirement(RequirementConstants.R5_3__H_1_2, frameDropped,
                frameRate);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 2(S) / 1(T) frames in 10 seconds for a
         * 1080p 60 fps video session under load. Load is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs, as well as a 128 kbps AAC
         * audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_ST() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.FRAMES_DROPPED)
                .setPredicate(RequirementConstants.INTEGER_LTE)
                // MUST NOT drop more than 2 frame in 10 seconds so 6 frames for 30 seconds
                .addRequiredValue(Build.VERSION_CODES.S, 6)
                // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 3)
                .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                .<Double>builder()
                .setId(RequirementConstants.FRAME_RATE)
                .setPredicate(RequirementConstants.DOUBLE_EQ)
                .addRequiredValue(Build.VERSION_CODES.S, 60.0)
                .build();

            return new FrameDropRequirement(RequirementConstants.R5_3__H_1_1, frameDropped,
                frameRate);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 2(S) / 1(T) frames in 10 seconds during a
         * video resolution change in a 60 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_ST() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.FRAMES_DROPPED)
                .setPredicate(RequirementConstants.INTEGER_LTE)
                // MUST NOT drop more than 2 frame in 10 seconds so 6 frames for 30 seconds
                .addRequiredValue(Build.VERSION_CODES.S, 6)
                // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 3)
                .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                .<Double>builder()
                .setId(RequirementConstants.FRAME_RATE)
                .setPredicate(RequirementConstants.DOUBLE_EQ)
                .addRequiredValue(Build.VERSION_CODES.S, 60.0)
                .build();

            return new FrameDropRequirement(RequirementConstants.R5_3__H_1_2, frameDropped,
                frameRate);
        }
    }

    public static class VideoCodecRequirement extends Requirement {
        private static final String TAG = VideoCodecRequirement.class.getSimpleName();

        private VideoCodecRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setAv1DecoderReq(boolean av1DecoderReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.AV1_DEC_REQ, av1DecoderReqSatisfied);
        }

        public void set4kHwDecoders(int num4kHwDecoders) {
            this.setMeasuredValue(RequirementConstants.NUM_4k_HW_DEC, num4kHwDecoders);
        }

        public void set4kHwEncoders(int num4kHwEncoders) {
            this.setMeasuredValue(RequirementConstants.NUM_4k_HW_ENC, num4kHwEncoders);
        }

        /**
         * [2.2.7.1/5.1/H-1-15] Must have at least 1 HW video decoder supporting 4K60
         */
        public static VideoCodecRequirement createR4k60HwDecoder() {
            RequiredMeasurement<Integer> requirement = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.NUM_4k_HW_DEC)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_15, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-16] Must have at least 1 HW video encoder supporting 4K60
         */
        public static VideoCodecRequirement createR4k60HwEncoder() {
            RequiredMeasurement<Integer> requirement = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.NUM_4k_HW_ENC)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_16, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-14] AV1 Hardware decoder: Main 10, Level 4.1, Film Grain
         */
        public static VideoCodecRequirement createRAV1DecoderReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                .<Boolean>builder()
                .setId(RequirementConstants.AV1_DEC_REQ)
                .setPredicate(RequirementConstants.BOOLEAN_EQ)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_14, requirement);
        }
    }

    // TODO(b/218771970): Add cdd annotation
    // used for requirements [?]
    public static class SecureCodecRequirement extends Requirement {
        private static final String TAG = SecureCodecRequirement.class.getSimpleName();

        private SecureCodecRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setSecureReqSatisfied(boolean secureReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.SECURE_REQ_SATISFIED, secureReqSatisfied);
        }

        public void setWidevineSupported(boolean isWidevineSupported) {
            this.setMeasuredValue(RequirementConstants.WIDEWINE_SUPPORT, isWidevineSupported);
        }

        public void setWidevineL1Supported(boolean isL1Supported) {
            this.setMeasuredValue(RequirementConstants.WIDEWINE_L1, isL1Supported);
        }

        public void setWidevineL1Tier3Supported(boolean isL1Tier3Supported) {
            this.setMeasuredValue(RequirementConstants.WIDEWINE_L1_TIER3, isL1Tier3Supported);
        }

        public void setOemCrypto17Plus(boolean isOemCrypto17Plus) {
            this.setMeasuredValue(RequirementConstants.OEM_CRYPTO_17_PLUS, isOemCrypto17Plus);
        }

        public void setWidevineCdm17Plus(boolean isWidevineCdm17Plus) {
            this.setMeasuredValue(RequirementConstants.WIDEWINE_CDM_17_PLUS, isWidevineCdm17Plus);
        }

        /**
         * [?] Support for Widevine L1 Tier 3, WidevineCdmVersion >= 17, OemCryptoVersion >= 17
         */
        public static SecureCodecRequirement createRWidevineSupport() {
            RequiredMeasurement<Boolean> widevineSupport = RequiredMeasurement
                .<Boolean>builder()
                .setId(RequirementConstants.WIDEWINE_SUPPORT)
                .setPredicate(RequirementConstants.BOOLEAN_EQ)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                .build();

            RequiredMeasurement<Boolean> widevineL1 =
                RequiredMeasurement.<Boolean>builder().setId(RequirementConstants.WIDEWINE_L1)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .build();

            RequiredMeasurement<Boolean> widevineL1Tier3 =
                RequiredMeasurement.<Boolean>builder().setId(RequirementConstants.WIDEWINE_L1_TIER3)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .build();

            RequiredMeasurement<Boolean> oemCryptoReq = RequiredMeasurement.<Boolean>builder()
                .setId(RequirementConstants.OEM_CRYPTO_17_PLUS)
                .setPredicate(RequirementConstants.BOOLEAN_EQ)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                .build();

            RequiredMeasurement<Boolean> widevineCdmReq = RequiredMeasurement.<Boolean>builder()
                .setId(RequirementConstants.WIDEWINE_CDM_17_PLUS)
                .setPredicate(RequirementConstants.BOOLEAN_EQ)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                .build();

            return new SecureCodecRequirement(RequirementConstants.RTBD, widevineSupport,
                widevineL1, widevineL1Tier3, oemCryptoReq, widevineCdmReq);
        }

        /**
         * [2.2.7.1/5.7/H-1-1] Must support secure decoder when a corresponding AVC/VP9/HEVC or AV1
         * hardware decoder is available
         */
        public static SecureCodecRequirement createRSecureDecodeSupport() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                .<Boolean>builder()
                .setId(RequirementConstants.SECURE_REQ_SATISFIED)
                .setPredicate(RequirementConstants.BOOLEAN_EQ)
                .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                .build();

            return new SecureCodecRequirement(RequirementConstants.R5_7__H_1_1, requirement);
        }
    }

    private <R extends Requirement> R addRequirement(R req) {
        if (!this.mRequirements.add(req)) {
            throw new IllegalStateException("Requirement " + req.id() + " already added");
        }
        return req;
    }

    public ResolutionRequirement addR7_1_1_1__H_1_1() {
        return this.<ResolutionRequirement>addRequirement(
            ResolutionRequirement.createR7_1_1_1__H_1_1());
    }

    public DensityRequirement addR7_1_1_3__H_1_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_1_1());
    }

    public MemoryRequirement addR7_6_1__H_1_1() {
        return this.<MemoryRequirement>addRequirement(MemoryRequirement.createR7_6_1__H_1_1());
    }

    public ResolutionRequirement addR7_1_1_1__H_2_1() {
        return this.<ResolutionRequirement>addRequirement(
            ResolutionRequirement.createR7_1_1_1__H_2_1());
    }

    public DensityRequirement addR7_1_1_3__H_2_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_2_1());
    }

    public MemoryRequirement addR7_6_1__H_2_1() {
        return this.<MemoryRequirement>addRequirement(MemoryRequirement.createR7_6_1__H_2_1());
    }

    public FrameDropRequirement addR5_3__H_1_1_R() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_R());
    }

    public FrameDropRequirement addR5_3__H_1_2_R() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_R());
    }

    public FrameDropRequirement addR5_3__H_1_1_ST() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_ST());
    }

    public FrameDropRequirement addR5_3__H_1_2_ST() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_ST());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_7() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_7());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_8() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_8());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_12() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_12());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_13() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_13());
    }

    public VideoCodecRequirement addR4k60HwEncoder() {
        return this.addRequirement(VideoCodecRequirement.createR4k60HwEncoder());
    }

    public VideoCodecRequirement addR4k60HwDecoder() {
        return this.addRequirement(VideoCodecRequirement.createR4k60HwDecoder());
    }

    public VideoCodecRequirement addRAV1DecoderReq() {
        return this.addRequirement(VideoCodecRequirement.createRAV1DecoderReq());
    }

    public SecureCodecRequirement addRSecureDecodeSupport() {
        return this.addRequirement(SecureCodecRequirement.createRSecureDecodeSupport());
    }

    public SecureCodecRequirement addRWidevineSupport() {
        return this.addRequirement(SecureCodecRequirement.createRWidevineSupport());
    }

    public void submitAndCheck() {
        boolean perfClassMet = true;
        for (Requirement req: this.mRequirements) {
            perfClassMet &= req.writeLogAndCheck(this.mTestName);
        }

        // check performance class
        assumeTrue("Build.VERSION.MEDIA_PERFORMANCE_CLASS is not declared", Utils.isPerfClass());
        assertThat(perfClassMet).isTrue();

        this.mRequirements.clear(); // makes sure report isn't submitted twice
    }
}
