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

import android.media.MediaFormat;
import android.os.Build;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The following test class validates the maximum number of concurrent Transcode sessions that
 * it can support by the (mime, decoder - mime, encoder) pair calculated via the
 * CodecCapabilities.getMaxSupportedInstances() and
 * VideoCapabilities.getSupportedPerformancePoints() methods. If maximum instances is odd, create
 * one additional decoder which decodes to surface and render. Also ensures that all the supported
 * sessions succeed in transcoding/decoding with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiTranscoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiTranscoderPerfTest.class.getSimpleName();

    private final Pair<String, String> mDecoderPair;
    private final Pair<String, String> mEncoderPair;

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    public MultiTranscoderPerfTest(Pair<String, String> decoderPair,
            Pair<String, String> encoderPair, boolean isAsync) {
        super(null, null, isAsync);
        mDecoderPair = decoderPair;
        mEncoderPair = encoderPair;
    }

    // Parameters {0}_{1}_{2} -- Pair(Mime DecoderName)_Pair(Mime EncoderName)_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        // Prepares the params list with the supported Hardware decoders/encoders in the device
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<Pair<String, String>> mimeTypeDecoderPairs = new ArrayList<>();
        ArrayList<Pair<String, String>> mimeTypeEncoderPairs = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfDecoders = getHardwareCodecsForMime(mime, false);
            for (String decoder : listOfDecoders) {
                mimeTypeDecoderPairs.add(Pair.create(mime, decoder));
            }
            ArrayList<String> listOfEncoders = getHardwareCodecsForMime(mime, true);
            for (String encoder : listOfEncoders) {
                mimeTypeEncoderPairs.add(Pair.create(mime, encoder));
            }
        }
        for (Pair<String, String> mimeTypeDecoderPair : mimeTypeDecoderPairs) {
            for (Pair<String, String> mimeTypeEncoderPair : mimeTypeEncoderPairs) {
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mimeTypeDecoderPair, mimeTypeEncoderPair, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test calculates the validates number of concurrent 720p Transcode sessions that
     * it can support by the (mime, decoder - mime, encoder) pairs. Creates maxInstances / 2
     * Transcode sessions. If maximum instances is odd, creates one additional decoder which decodes
     * to surface and render. And ensures that all the supported sessions succeed in
     * transcoding/decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-5,H-1-6")
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());

        boolean hasVP9 = mDecoderPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9)
                || mEncoderPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances(hasVP9) / 2;
        testCodec(m720pTestFiles, 720, 1280, requiredMinInstances);
    }

    private void testCodec(Map<String, String> testFiles, int height, int width,
            int requiredMinInstances) throws Exception {
        mTestFiles = testFiles;
        ArrayList<Pair<String, String>> mimeCodecPairs = new ArrayList<>();
        mimeCodecPairs.add(mDecoderPair);
        mimeCodecPairs.add(mEncoderPair);
        int maxInstances =
                checkAndGetMaxSupportedInstancesForCodecCombinations(height, width, mimeCodecPairs);
        double achievedFrameRate = 0.0;
        if (maxInstances >= requiredMinInstances) {
            ExecutorService pool =
                    Executors.newFixedThreadPool(maxInstances / 2 + maxInstances % 2);
            List<Transcode> transcodeList = new ArrayList<>();
            for (int i = 0; i < maxInstances / 2; i++) {
                transcodeList
                        .add(new Transcode(mEncoderPair.first, mTestFiles.get(mDecoderPair.first),
                                mDecoderPair.second, mEncoderPair.second, mIsAsync));
            }
            List<Future<Double>> decodeResultList = null;
            if (maxInstances % 2 == 1) {
                List<DecodeToSurface> decodeList = new ArrayList<>();
                mActivityRule.getActivity().waitTillSurfaceIsCreated();
                Surface surface = mActivityRule.getActivity().getSurface();
                assertTrue("Surface created is null.", surface != null);
                assertTrue("Surface created is invalid.", surface.isValid());
                mActivityRule.getActivity().setScreenParams(width, height, true);
                decodeList.add(new DecodeToSurface(mDecoderPair.first,
                        mTestFiles.get(mDecoderPair.first), mDecoderPair.second, surface,
                        mIsAsync));
                decodeResultList = pool.invokeAll(decodeList);
            }
            List<Future<Double>> transcodeResultList = pool.invokeAll(transcodeList);
            for (Future<Double> result : transcodeResultList) {
                achievedFrameRate += result.get();
            }
            if (decodeResultList != null) {
                for (Future<Double> result : decodeResultList) {
                    achievedFrameRate += result.get();
                }
            }
        }
        if (Utils.isPerfClass()) {
            assertTrue("DecodeMime: " + mDecoderPair.first + ", Decoder " + mDecoderPair.second +
                    ", EncodeMime: " + mEncoderPair.first + ", Encoder: " + mEncoderPair.second +
                    ", unable to support minimum concurrent instances. act/exp: " + maxInstances +
                    "/" + requiredMinInstances, maxInstances >= requiredMinInstances);

            assertTrue("Unable to achieve the maxFrameRate supported. act/exp: " + achievedFrameRate
                            + "/" + mMaxFrameRate / 2 + " for " + maxInstances + " instances.",
                    achievedFrameRate >= mMaxFrameRate / 2);
        } else {
            int pc = maxInstances >= requiredMinInstances && achievedFrameRate >= mMaxFrameRate / 2
                    ? Build.VERSION_CODES.R : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",
                    "MultiTranscoderPairPerf_" + mDecoderPair.second);
            log.addValue("decoders", mDecoderPair.first + "_" + mDecoderPair.second + "_"
                            + mEncoderPair.first + "_" + mEncoderPair.second, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.addValue("achieved_framerate", achievedFrameRate, ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);
            log.addValue("expected_framerate", mMaxFrameRate, ResultType.NEUTRAL, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.1/5.1/H-1-5,H-1-6 performance_class", pc, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }
}
