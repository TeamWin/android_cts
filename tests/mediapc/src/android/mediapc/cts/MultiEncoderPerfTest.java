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

import android.util.Pair;

import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MultiEncoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiEncoderPerfTest.class.getSimpleName();

    private final String mEncoderName;

    public MultiEncoderPerfTest(String mimeType, String encoderName, boolean isAsync) {
        super(mimeType, null, isAsync);
        mEncoderName = encoderName;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        // Prepares the params list with the supported Hardware encoders in the device
        final List<Object[]> argsList = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfEncoders = getHardwareCodecsFor720p(mime, true);
            for (String encoder : listOfEncoders) {
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mime, encoder, isAsync});
                }
            }
        }
        return argsList;
    }

    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void test720p() throws Exception {
        ArrayList<Pair<String, String>> mimeEncoderPairs = new ArrayList<>();
        mimeEncoderPairs.add(Pair.create(mMime, mEncoderName));
        int maxInstances = checkAndGetMaxSupportedInstancesFor720p(mimeEncoderPairs);
        assertTrue("Encoder " + mEncoderName + " unable to support minimum concurrent " +
                "instances. act/exp: " + maxInstances + "/" + REQUIRED_MIN_CONCURRENT_INSTANCES,
                maxInstances >= REQUIRED_MIN_CONCURRENT_INSTANCES);
        ExecutorService pool = Executors.newFixedThreadPool(maxInstances);
        List<Encode> testList = new ArrayList<>();
        for (int i = 0; i < maxInstances; i++) {
            testList.add(new Encode(mMime, mEncoderName, mIsAsync));
        }
        List<Future<Double>> resultList = pool.invokeAll(testList);
        double achievedFrameRate = 0.0;
        for (Future<Double> result : resultList) {
            achievedFrameRate += result.get();
        }
        // Achieved frame rate is not compared as this test runs in byte buffer mode.
    }
}
