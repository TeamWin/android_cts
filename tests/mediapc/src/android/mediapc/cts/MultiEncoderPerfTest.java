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
import android.util.Log;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The following test class validates the maximum number of concurrent encode sessions that it can
 * support by the hardware encoders calculated via the CodecCapabilities.getMaxSupportedInstances()
 * and VideoCapabilities.getSupportedPerformancePoints() methods. And also ensures that the maximum
 * supported sessions succeed in encoding.
 * Achieved frame rate is not compared as this test runs in byte buffer mode.
 */
@RunWith(Parameterized.class)
public class MultiEncoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiEncoderPerfTest.class.getSimpleName();

    private final String mEncoderName;

    public MultiEncoderPerfTest(String mimeType, String encoderName, boolean isAsync) {
        super(mimeType, null, isAsync);
        mEncoderName = encoderName;
    }

    // Returns the params list with the mime and their hardware encoders in
    // both sync and async modes.
    // Parameters {0}_{2}_{3} -- Mime_EncoderName_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfEncoders = getHardwareCodecsForMime(mime, true);
            for (String encoder : listOfEncoders) {
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mime, encoder, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test validates that the encoder can support at least 6 concurrent 720p 30fps
     * encoder instances. Also ensures that all the concurrent sessions succeed in encoding.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-3,H-1-4")
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());

        boolean hasVP9 = mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances(hasVP9);
        testCodec(720, 1280, 4000000, requiredMinInstances);
    }

    private void testCodec(int height, int width, int bitrate, int requiredMinInstances)
            throws Exception {
        ArrayList<Pair<String, String>> mimeEncoderPairs = new ArrayList<>();
        mimeEncoderPairs.add(Pair.create(mMime, mEncoderName));
        int maxInstances = checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                mimeEncoderPairs);
        double achievedFrameRate = 0.0;
        if (maxInstances >= requiredMinInstances) {
            ExecutorService pool = Executors.newFixedThreadPool(maxInstances);
            List<Encode> testList = new ArrayList<>();
            for (int i = 0; i < maxInstances; i++) {
                testList.add(new Encode(mMime, mEncoderName, mIsAsync, height, width, 30, bitrate));
            }
            List<Future<Double>> resultList = pool.invokeAll(testList);
            for (Future<Double> result : resultList) {
                achievedFrameRate += result.get();
            }
        }
        // Achieved frame rate is not compared as this test runs in byte buffer mode.
        if (Utils.isPerfClass()) {
            assertTrue("Encoder " + mEncoderName + " unable to support minimum concurrent " +
                            "instances. act/exp: " + maxInstances + "/" + requiredMinInstances,
                    maxInstances >= requiredMinInstances);
            Log.v(LOG_TAG, "Achieved fps: " + achievedFrameRate +
                    "\nAchieved frame rate is not compared as this test runs in byte buffer mode");
        } else {
            int pc = maxInstances >= requiredMinInstances ? Build.VERSION_CODES.R : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",
                    "MultiEncoderPerf_" + mEncoderName);
            log.addValue("encoder", mMime + "_" + mEncoderName, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.1/5.1/H-1-3,H-1-4 performance_class", pc, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }
}
