/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import static org.junit.Assert.assertTrue;

import android.media.MediaCodecInfo;
import android.mediapc.cts.common.Utils;
import android.os.Build;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * The following test class validates the frame drops of AdaptivePlayback for the hardware decoders
 * under the load condition (Transcode + Audio Playback).
 */
@RunWith(Parameterized.class)
public class AdaptivePlaybackFrameDropTest extends FrameDropTestBase {
    private static final String LOG_TAG = AdaptivePlaybackFrameDropTest.class.getSimpleName();

    public AdaptivePlaybackFrameDropTest(String mimeType, String decoderName, boolean isAsync) {
        super(mimeType, decoderName, isAsync);
    }

    // Returns the list of parameters with mimeTypes and their hardware decoders supporting the
    // AdaptivePlayback feature combining with sync and async modes.
    // Parameters {0}_{1}_{2} -- Mime_DecoderName_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        return prepareArgumentsList(new String[]{
                MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback});
    }

    private void testAdaptivePlaybackFrameDrop(int frameRate) throws Exception {
        String[] testFiles = frameRate == 30 ?
                new String[]{m1080p30FpsTestFiles.get(mMime), m540p30FpsTestFiles.get(mMime)} :
                new String[]{m1080p60FpsTestFiles.get(mMime), m540p60FpsTestFiles.get(mMime)};
        PlaybackFrameDrop playbackFrameDrop = new PlaybackFrameDrop(mMime, mDecoderName, testFiles,
                mSurface, frameRate, mIsAsync);
        int frameDropCount = playbackFrameDrop.getFrameDropCount();
        if (Utils.isPerfClass()) {
            assertTrue("Adaptive Playback FrameDrop count for mime: " + mMime + ", decoder: "
                    + mDecoderName + ", FrameRate: " + frameRate
                    + ", is not as expected. act/exp: " + frameDropCount + "/"
                    + MAX_FRAME_DROP_FOR_30S, frameDropCount <= MAX_FRAME_DROP_FOR_30S);
        } else {
            int pc = getAchievedPerfClass(frameRate, frameDropCount);
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",
                    "AdaptiveFrameDrop_" + mDecoderName);
            log.addValue("decoder", mDecoderName, ResultType.NEUTRAL, ResultUnit.NONE);
            log.addValue("adaptive_frame_drops_for_30sec", frameDropCount, ResultType.LOWER_BETTER,
                    ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.1/5.3/H-1-2 performance_class", pc, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    /**
     * This test validates that the Adaptive Playback of 1920x1080 and 960x540 resolution
     * assets of 3 seconds duration each at 30 fps for R perf class,
     * playing alternatively, for at least 30 seconds worth of frames or for 31 seconds of elapsed
     * time, must not drop more than 3 frames for R perf class.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.3/H-1-2")
    public void test30Fps() throws Exception {
        Assume.assumeTrue("Test is limited to R performance class devices or devices that do not " +
                        "advertise performance class",
                Utils.isRPerfClass() || !Utils.isPerfClass());
        testAdaptivePlaybackFrameDrop(30);
    }

    /**
     * This test validates that the Adaptive Playback of 1920x1080 and 960x540 resolution
     * assets of 3 seconds duration each at 60 fps for S or T perf class,
     * playing alternatively, for at least 30 seconds worth of frames or for 31 seconds of elapsed
     * time, must not drop more than 6 frames for S perf class / 3 frames for T perf class .
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.3/H-1-2")
    public void test60Fps() throws Exception {
        Assume.assumeTrue("Test is limited to S/T performance class devices or devices that do " +
                        "not advertise performance class",
                Utils.isSPerfClass() || Utils.isTPerfClass() || !Utils.isPerfClass());
        testAdaptivePlaybackFrameDrop(60);
    }
}
