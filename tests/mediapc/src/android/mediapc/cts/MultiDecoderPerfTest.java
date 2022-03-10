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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The following test class validates the maximum number of concurrent decode sessions that it can
 * support by the hardware decoders calculated via the CodecCapabilities.getMaxSupportedInstances()
 * and VideoCapabilities.getSupportedPerformancePoints() methods. And also ensures that the maximum
 * supported sessions succeed in decoding with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiDecoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiDecoderPerfTest.class.getSimpleName();

    private final String mDecoderName;

    public MultiDecoderPerfTest(String mimeType, String decoderName, boolean isAsync) {
        super(mimeType, null, isAsync);
        mDecoderName = decoderName;
    }

    // Returns the params list with the mime and corresponding hardware decoders in
    // both sync and async modes.
    // Parameters {0}_{1}_{2} -- Mime_DecoderName_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfDecoders = getHardwareCodecsForMime(mime, false);
            for (String decoder : listOfDecoders) {
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mime, decoder, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test validates that the decoder can support at least 6 concurrent 720p 30fps
     * decoder instances. Also ensures that all the concurrent sessions succeed in decoding
     * with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-1,H-1-2")
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());

        boolean hasVP9 = mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances(hasVP9);
        testCodec(m720pTestFiles, 720, 1280, requiredMinInstances);
    }

    private void testCodec(Map<String, String> testFiles, int height, int width,
            int requiredMinInstances) throws Exception {
        mTestFile = testFiles.get(mMime);
        ArrayList<Pair<String, String>> mimeDecoderPairs = new ArrayList<>();
        mimeDecoderPairs.add(Pair.create(mMime, mDecoderName));
        int maxInstances =
                checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                        mimeDecoderPairs);
        double achievedFrameRate = 0.0;
        if (maxInstances >= requiredMinInstances) {
            ExecutorService pool = Executors.newFixedThreadPool(maxInstances);
            List<Decode> testList = new ArrayList<>();
            for (int i = 0; i < maxInstances; i++) {
                testList.add(new Decode(mMime, mTestFile, mDecoderName, mIsAsync));
            }
            List<Future<Double>> resultList = pool.invokeAll(testList);
            for (Future<Double> result : resultList) {
                achievedFrameRate += result.get();
            }
        }
        if (Utils.isPerfClass()) {
            assertTrue("Decoder " + mDecoderName + " unable to support minimum concurrent " +
                            "instances. act/exp: " + maxInstances + "/" + requiredMinInstances,
                    maxInstances >= requiredMinInstances);
            assertTrue("Unable to achieve the maxFrameRate supported. act/exp: " + achievedFrameRate
                            + "/" + mMaxFrameRate + " for " + maxInstances + " instances.",
                    achievedFrameRate >= mMaxFrameRate);
        } else {
            int pc = maxInstances >= requiredMinInstances && achievedFrameRate >= mMaxFrameRate
                    ? Build.VERSION_CODES.R : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",
                    "MultiDecoderPerf_" + mDecoderName);
            log.addValue("decoders", mMime + "_" + mDecoderName, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.addValue("achieved_framerate", achievedFrameRate, ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);
            log.addValue("expected_framerate", mMaxFrameRate, ResultType.NEUTRAL, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.1/5.1/H-1-1,H-1-2 performance_class", pc, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }
}
