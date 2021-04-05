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
public class MultiTranscoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiTranscoderPerfTest.class.getSimpleName();

    private final String mDecoderName;
    private final String mEncoderName;

    public MultiTranscoderPerfTest(String mimeType, String testFile, String decoderName,
            String encoderName, boolean isAsync) {
        super(mimeType, testFile,isAsync);
        mDecoderName = decoderName;
        mEncoderName = encoderName;
    }

    @Parameterized.Parameters(name = "{index}({0}_{2}_{3}_{4})")
    public static Collection<Object[]> inputParams() {
        // Prepares the params list with the supported Hardware decoders/encoders in the device
        final List<Object[]> argsList = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfDecoders = getHardwareCodecsFor720p(mime, false);
            ArrayList<String> listOfEncoders = getHardwareCodecsFor720p(mime, true);
            for (String decoder : listOfDecoders) {
                for (String encoder : listOfEncoders) {
                    for (boolean isAsync : boolStates) {
                        argsList.add(new Object[]{mime, mTestFiles.get(mime), decoder, encoder,
                                isAsync});
                    }
                }
            }
        }
        return argsList;
    }

    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void test720p() throws Exception {
        ArrayList<Pair<String, String>> mimeCodecPairs = new ArrayList<>();
        mimeCodecPairs.add(Pair.create(mMime, mDecoderName));
        mimeCodecPairs.add(Pair.create(mMime, mEncoderName));
        int maxInstances = checkAndGetMaxSupportedInstancesFor720p(mimeCodecPairs);
        assertTrue("Decoder " + mDecoderName + " ,Encoder " + mEncoderName +
                " unable to support minimum concurrent instances. act/exp: " + maxInstances + "/" +
                (REQUIRED_MIN_CONCURRENT_INSTANCES / 2),
                maxInstances >= (REQUIRED_MIN_CONCURRENT_INSTANCES / 2));
        ExecutorService pool = Executors.newFixedThreadPool(maxInstances / 2);
        List<Transcode> testList = new ArrayList<>();
        for (int i = 0; i < maxInstances / 2; i++) {
            testList.add(new Transcode(mMime, mTestFile, mDecoderName, mEncoderName, mIsAsync));
        }
        List<Future<Double>> resultList = pool.invokeAll(testList);
        double achievedFrameRate = 0.0;
        for (Future<Double> result : resultList) {
            achievedFrameRate += result.get();
        }
        assertTrue("Unable to achieve the maxFrameRate supported. act/exp: " + achievedFrameRate
                + "/" + mMaxFrameRate / 2, achievedFrameRate >= mMaxFrameRate / 2);
    }
}
