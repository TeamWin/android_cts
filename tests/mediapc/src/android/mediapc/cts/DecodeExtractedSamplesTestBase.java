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
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class DecodeExtractedSamplesTestBase extends CodecDecoderTestBase {
    private static final long EACH_FRAME_TIME_INTERVAL_US = 1000000 / 60;

    final String[] mTestFiles;
    final boolean mIsAsync;

    long mFrameDropCount;
    ByteBuffer mBuff;
    ArrayList<MediaCodec.BufferInfo> mBufferInfos;

    private long mMaxPtsUs;
    private long mRenderStartTimeUs;

    DecodeExtractedSamplesTestBase(String mime, String[] testFiles, Surface surface,
            boolean isAsync) {
        super(mime, null);
        mTestFiles = testFiles;
        mSurface = surface;
        mIsAsync = isAsync;
        mMaxPtsUs = 0;
        mFrameDropCount = 0;
        mBufferInfos = new ArrayList<>();
    }

    private MediaFormat createInputList(MediaFormat format, ByteBuffer buffer,
            ArrayList<MediaCodec.BufferInfo> list, int offset, long ptsOffset) {
        if (hasCSD(format)) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.offset = offset;
            bufferInfo.size = 0;
            bufferInfo.presentationTimeUs = 0;
            bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            for (int i = 0; ; i++) {
                String csdKey = "csd-" + i;
                if (format.containsKey(csdKey)) {
                    ByteBuffer csdBuffer = format.getByteBuffer(csdKey);
                    bufferInfo.size += csdBuffer.limit();
                    buffer.put(csdBuffer);
                    format.removeKey(csdKey);
                } else break;
            }
            list.add(bufferInfo);
            offset += bufferInfo.size;
        }
        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.size = mExtractor.readSampleData(buffer, offset);
            if (bufferInfo.size < 0) break;
            bufferInfo.offset = offset;
            bufferInfo.presentationTimeUs = ptsOffset + mExtractor.getSampleTime();
            mMaxPtsUs = Math.max(mMaxPtsUs, bufferInfo.presentationTimeUs);
            int flags = mExtractor.getSampleFlags();
            bufferInfo.flags = 0;
            if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            list.add(bufferInfo);
            mExtractor.advance();
            offset += bufferInfo.size;
        }
        buffer.clear();
        buffer.position(offset);
        return format;
    }

    public ArrayList<MediaFormat> setUpSourceFiles() throws Exception {
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (String file : mTestFiles) {
            formats.add(setUpSource(file));
            mExtractor.release();
        }
        int totalSize = 0;
        for (String srcFile : mTestFiles) {
            File file = new File(mInpPrefix + srcFile);
            totalSize += (int) file.length();
        }
        totalSize <<= 1;
        long ptsOffset = 0;
        int buffOffset = 0;
        mBuff = ByteBuffer.allocate(totalSize);
        for (String file : mTestFiles) {
            formats.add(createInputList(setUpSource(file), mBuff, mBufferInfos, buffOffset,
                    ptsOffset));
            mExtractor.release();
            ptsOffset = mMaxPtsUs + 1000000L;
            buffOffset = (mBufferInfos.get(mBufferInfos.size() - 1).offset) +
                    (mBufferInfos.get(mBufferInfos.size() - 1).size);
        }
        return formats;
    }

    private long getRenderTimeUs(int frameIndex) {
        return mRenderStartTimeUs + frameIndex * EACH_FRAME_TIME_INTERVAL_US;
    }

    @Override
    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        long nowUs = System.nanoTime() / 1000;
        if (mOutputCount == 0) {
            mRenderStartTimeUs = nowUs;
            mCodec.releaseOutputBuffer(bufferIndex, true);
        } else if (nowUs > getRenderTimeUs(mOutputCount + 1)) {
            mFrameDropCount++;
            mCodec.releaseOutputBuffer(bufferIndex, false);
        } else if (nowUs > getRenderTimeUs(mOutputCount)) {
            mCodec.releaseOutputBuffer(bufferIndex, true);
        } else {
            if ((getRenderTimeUs(mOutputCount) - nowUs) > (EACH_FRAME_TIME_INTERVAL_US / 2)) {
                try {
                    Thread.sleep(((getRenderTimeUs(mOutputCount) - nowUs) -
                            (EACH_FRAME_TIME_INTERVAL_US / 2)) / 1000);
                } catch (InterruptedException e) {
                    // Do nothing.
                }
            }
            mCodec.releaseOutputBuffer(bufferIndex, true);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputCount++;
        }
    }
}
