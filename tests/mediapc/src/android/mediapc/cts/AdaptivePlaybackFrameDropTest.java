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

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class AdaptivePlaybackFrameDropTest extends FrameDropTestBase {
    private static final String LOG_TAG = AdaptivePlaybackFrameDropTest.class.getSimpleName();

    public AdaptivePlaybackFrameDropTest(String mimeType, String decoderName, boolean isAsync) {
        super(mimeType, decoderName, isAsync);
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        return prepareArgumentsList(new String[]{
                MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback});
    }

    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptivePlaybackFrameDrop() throws Exception {
        for (int i = 0; i < 5; i++) {
            AdaptivePlayback adaptivePlayback = new AdaptivePlayback(mMime,
                    new String[]{m1080pTestFiles.get(mMime), m540pTestFiles.get(mMime),
                            m1080pTestFiles.get(mMime)},
                    mDecoderName, mSurface, mIsAsync);
            adaptivePlayback.doAdaptivePlaybackAndCalculateFrameDrop();
        }
    }
}

class AdaptivePlayback extends DecodeExtractedSamplesTestBase {
    private final String mDecoderName;

    AdaptivePlayback(String mime, String[] testFiles, String decoderName, Surface surface,
            boolean isAsync) {
        super(mime, testFiles, surface, isAsync);
        mDecoderName = decoderName;
    }

    public void doAdaptivePlaybackAndCalculateFrameDrop() throws Exception {
        ArrayList<MediaFormat> formats = setUpSourceFiles();
        mCodec = MediaCodec.createByCodecName(mDecoderName);
        configureCodec(formats.get(0), mIsAsync, false, false);
        mCodec.start();
        doWork(mBuff, mBufferInfos);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        assertTrue("FrameDrop count for mime: " + mMime + " decoder: " + mDecoderName +
                " is not as expected. act/exp: " + mFrameDropCount + "/0", mFrameDropCount == 0);
    }
}
