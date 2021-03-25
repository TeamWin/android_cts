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
import android.media.MediaFormat;
import android.view.Surface;

import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

import static android.mediapc.cts.FrameDropTestBase.DECODE_30S;
import static android.mediapc.cts.FrameDropTestBase.MAX_FRAME_DROP_FOR_30S;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class FrameDropTest extends FrameDropTestBase {
    private static final String LOG_TAG = FrameDropTest.class.getSimpleName();

    public FrameDropTest(String mimeType, String decoderName, boolean isAsync) {
        super(mimeType, decoderName, isAsync);
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        return prepareArgumentsList(null);
    }

    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodeToSurface() throws Exception {
        DecodeToSurfaceFrameDrop decodeToSurfaceFrameDrop = new DecodeToSurfaceFrameDrop(mMime,
                m1080pTestFiles.get(mMime), mDecoderName, mSurface, mIsAsync);
        decodeToSurfaceFrameDrop.doDecodeAndCalculateFrameDrop();
    }
}

class DecodeToSurfaceFrameDrop extends DecodeExtractedSamplesTestBase {
    private final String mDecoderName;

    private long mBasePts;
    private long mMaxPts;
    private long mDecodeStartTimeMs;
    private int mSampleIndex;

    DecodeToSurfaceFrameDrop(String mime, String testFile, String decoderName, Surface surface,
            boolean isAsync) {
        super(mime, new String[]{testFile}, surface, isAsync);
        mDecoderName = decoderName;
        mBasePts = 0;
        mMaxPts = 0;
        mSampleIndex = 0;
    }

    public void doDecodeAndCalculateFrameDrop() throws Exception {
        ArrayList<MediaFormat> formats = setUpSourceFiles();
        mCodec = MediaCodec.createByCodecName(mDecoderName);
        configureCodec(formats.get(0), mIsAsync, false, false);
        mCodec.start();
        mDecodeStartTimeMs = System.currentTimeMillis();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        assertTrue("FrameDrop count for mime: " + mMime + " decoder: " + mDecoderName +
                " is not as expected. act/exp: " + mFrameDropCount + "/" + MAX_FRAME_DROP_FOR_30S,
                mFrameDropCount <= MAX_FRAME_DROP_FOR_30S);
    }

    @Override
    void enqueueInput(int bufferIndex) {
        if (mSampleIndex == mBufferInfos.size()) {
            enqueueEOS(bufferIndex);
        } else {
            MediaCodec.BufferInfo info = mBufferInfos.get(mSampleIndex++);
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                ByteBuffer dstBuf = mCodec.getInputBuffer(bufferIndex);
                dstBuf.put(mBuff.array(), info.offset, info.size);
                mInputCount++;
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mSawInputEOS = true;
            }
            long pts = info.presentationTimeUs;
            mMaxPts = Math.max(mMaxPts, mBasePts + pts);
            mCodec.queueInputBuffer(bufferIndex, 0, info.size, mBasePts + pts, info.flags);
            if (mSampleIndex == mBufferInfos.size() &&
                    // Decode for at least 30s
                    (System.currentTimeMillis() - mDecodeStartTimeMs < DECODE_30S)) {
                mSampleIndex = 0;
                mBasePts = mMaxPts + 1000000L;
            }
        }
    }
}
