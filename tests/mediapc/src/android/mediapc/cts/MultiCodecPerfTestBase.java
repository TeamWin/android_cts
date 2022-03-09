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

import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Pair;

import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiCodecPerfTestBase.class.getSimpleName();
    static final boolean[] boolStates = {true, false};
    static final int REQUIRED_MIN_CONCURRENT_INSTANCES = 6;
    static final int REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9 = 2;
    // allowed tolerance in measured fps vs expected fps in percentage, i.e. codecs achieving fps
    // that is greater than (FPS_TOLERANCE_FACTOR * expectedFps) will be considered as
    // passing the test
    static final double FPS_TOLERANCE_FACTOR = 0.95;
    static ArrayList<String> mMimeList = new ArrayList<>();
    static Map<String, String> mTestFiles = new HashMap<>();
    static Map<String, String> m720pTestFiles = new HashMap<>();

    static {
        mMimeList.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        mMimeList.add(MediaFormat.MIMETYPE_VIDEO_HEVC);

        m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1280x720_3mbps_30fps_avc.mp4");
        m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_1280x720_3mbps_30fps_hevc.mp4");

        // Test VP9 and AV1 as well for Build.VERSION_CODES.S
        if (Utils.isSPerfClass()) {
            mMimeList.add(MediaFormat.MIMETYPE_VIDEO_VP9);
            mMimeList.add(MediaFormat.MIMETYPE_VIDEO_AV1);

            m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_1280x720_3mbps_30fps_vp9.webm");
            m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_1280x720_3mbps_30fps_av1.mp4");
        }
    }

    String mMime;
    String mTestFile;
    final boolean mIsAsync;

    double mMaxFrameRate;

    @Before
    public void isPerformanceClassCandidate() {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();
    }

    public MultiCodecPerfTestBase(String mime, String testFile, boolean isAsync) {
        mMime = mime;
        mTestFile = testFile;
        mIsAsync = isAsync;
    }

    // Returns the list of hardware codecs for given mime
    public static ArrayList<String> getHardwareCodecsForMime(String mime, boolean isEncoder) {
        return selectHardwareCodecs(mime, null, null, isEncoder);
    }

    // Returns the max number of 30 fps instances that the given list of mimeCodecPairs
    // supports. It also checks that the each codec supports 180 fps PerformancePoint.
    public int checkAndGetMaxSupportedInstancesForCodecCombinations(int height, int width,
            ArrayList<Pair<String, String>> mimeCodecPairs) throws IOException {
        int[] maxInstances = new int[mimeCodecPairs.size()];
        int[] maxFrameRates = new int[mimeCodecPairs.size()];
        int[] maxMacroBlockRates = new int[mimeCodecPairs.size()];
        int loopCount = 0;
        for (Pair<String, String> mimeCodecPair : mimeCodecPairs) {
            MediaCodec codec = MediaCodec.createByCodecName(mimeCodecPair.second);
            MediaCodecInfo.CodecCapabilities cap = codec.getCodecInfo()
                    .getCapabilitiesForType(mimeCodecPair.first);
            List<PerformancePoint> pps = cap.getVideoCapabilities().getSupportedPerformancePoints();
            assertTrue(pps.size() > 0);

            boolean hasVP9 = mimeCodecPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
            int requiredFrameRate = getRequiredMinConcurrentInstances(hasVP9) * 30;

            maxInstances[loopCount] = cap.getMaxSupportedInstances();
            PerformancePoint PPRes = new PerformancePoint(width, height, requiredFrameRate);

            maxMacroBlockRates[loopCount] = 0;
            boolean supportsResolutionPerformance = false;
            for (PerformancePoint pp : pps) {
                if (pp.covers(PPRes)) {
                    supportsResolutionPerformance = true;
                    if (pp.getMaxMacroBlockRate() > maxMacroBlockRates[loopCount]) {
                        maxMacroBlockRates[loopCount] = (int) pp.getMaxMacroBlockRate();
                        maxFrameRates[loopCount] = pp.getMaxFrameRate();
                    }
                }
            }
            codec.release();
            if (!supportsResolutionPerformance) {
                Log.e(LOG_TAG,
                        "Codec " + mimeCodecPair.second + " doesn't support " + height + "p/" +
                                requiredFrameRate + " performance point");
                return 0;
            }
            loopCount++;
        }
        Arrays.sort(maxInstances);
        Arrays.sort(maxFrameRates);
        Arrays.sort(maxMacroBlockRates);
        int minOfMaxInstances = maxInstances[0];
        int minOfMaxFrameRates = maxFrameRates[0];
        int minOfMaxMacroBlockRates = maxMacroBlockRates[0];

        // Allow a tolerance in expected frame rate
        mMaxFrameRate = minOfMaxFrameRates * FPS_TOLERANCE_FACTOR;

        // Calculate how many 30fps max instances it can support from it's mMaxFrameRate
        // amd maxMacroBlockRate. (assuming 16x16 macroblocks)
        return Math.min(minOfMaxInstances, Math.min((int) (minOfMaxFrameRates / 30.0),
                (int) (minOfMaxMacroBlockRates / ((width / 16) * (height / 16)) / 30.0)));
    }

    public int getRequiredMinConcurrentInstances(boolean hasVP9) {
        // Below T, VP9 requires 60 fps at 720p and minimum of 2 instances
        if (!Utils.isTPerfClass() && hasVP9) {
            return REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9;
        }
        return REQUIRED_MIN_CONCURRENT_INSTANCES;
    }
}
