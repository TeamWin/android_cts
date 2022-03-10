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
 * The following test class calculates the maximum number of concurrent encode sessions that it can
 * support by the two hardware (mime - encoder) pair calculated via the
 * CodecCapabilities.getMaxSupportedInstances() and
 * VideoCapabilities.getSupportedPerformancePoints() methods. Splits the maximum supported instances
 * between the two pairs and ensures that all the supported sessions succeed in encoding.
 * Achieved frame rate is not compared as this test runs in byte buffer mode.
 */
@RunWith(Parameterized.class)
public class MultiEncoderPairPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiEncoderPairPerfTest.class.getSimpleName();

    private final Pair<String, String> mFirstPair;
    private final Pair<String, String> mSecondPair;

    public MultiEncoderPairPerfTest(Pair<String, String> firstPair, Pair<String, String> secondPair,
            boolean isAsync) {
        super(null, null, isAsync);
        mFirstPair = firstPair;
        mSecondPair = secondPair;
    }

    // Returns the list of params with two hardware (mime - encoder) pairs in both
    // sync and async modes.
    // Parameters {0}_{1}_{2} -- Pair(Mime EncoderName)_Pair(Mime EncoderName)_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<Pair<String, String>> mimeTypeEncoderPairs = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfEncoders = getHardwareCodecsForMime(mime, true);
            for (String encoder : listOfEncoders) {
                mimeTypeEncoderPairs.add(Pair.create(mime, encoder));
            }
        }
        for (int i = 0; i < mimeTypeEncoderPairs.size(); i++) {
            for (int j = i + 1; j < mimeTypeEncoderPairs.size(); j++) {
                Pair<String, String> pair1 = mimeTypeEncoderPairs.get(i);
                Pair<String, String> pair2 = mimeTypeEncoderPairs.get(j);
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{pair1, pair2, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test calculates the number of 720p 30 fps encoder instances that the given two
     * (mime - encoder) pairs can support. Assigns the same number of instances to the two pairs
     * (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in encoding.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-3,H-1-4")
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());

        boolean hasVP9 = mFirstPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9) ||
                mSecondPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances(hasVP9);
        testCodec(720, 1280, 4000000, requiredMinInstances);
    }

    private void testCodec(int height, int width, int bitrate, int requiredMinInstances)
            throws Exception {
        ArrayList<Pair<String, String>> mimeEncoderPairs = new ArrayList<>();
        mimeEncoderPairs.add(mFirstPair);
        mimeEncoderPairs.add(mSecondPair);
        int maxInstances = checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                mimeEncoderPairs);
        double achievedFrameRate = 0.0;
        if (maxInstances >= requiredMinInstances) {
            int secondPairInstances = maxInstances / 2;
            int firstPairInstances = maxInstances - secondPairInstances;
            ExecutorService pool = Executors.newFixedThreadPool(maxInstances);
            List<Encode> testList = new ArrayList<>();
            for (int i = 0; i < firstPairInstances; i++) {
                testList.add(
                        new Encode(mFirstPair.first, mFirstPair.second, mIsAsync, height, width, 30,
                                bitrate));
            }
            for (int i = 0; i < secondPairInstances; i++) {
                testList.add(
                        new Encode(mSecondPair.first, mSecondPair.second, mIsAsync, height, width,
                                30, bitrate));
            }
            List<Future<Double>> resultList = pool.invokeAll(testList);
            for (Future<Double> result : resultList) {
                achievedFrameRate += result.get();
            }
        }
        if (Utils.isPerfClass()) {
            assertTrue("Encoder pair " + mFirstPair.second + " and " + mSecondPair.second
                    + " unable to support minimum concurrent instances. act/exp: " + maxInstances
                    + "/" + requiredMinInstances, maxInstances >= requiredMinInstances);
            Log.v(LOG_TAG, "Achieved fps: " + achievedFrameRate +
                    "\nAchieved frame rate is not compared as this test runs in byte buffer mode");
        } else {
            int pc = maxInstances >= requiredMinInstances ? Build.VERSION_CODES.R : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",
                    "MultiEncoderPairPerf_" + mFirstPair.second);
            log.addValue("encoders",
                    mFirstPair.first + "_" + mFirstPair.second + "_" + mSecondPair.first + "_"
                            + mSecondPair.second, ResultType.NEUTRAL, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.1/5.1/H-1-3,H-1-4 performance_class", pc, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }
}
