/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediav2.common.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.junit.After;
import org.junit.Assume;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Wrapper class for testing HDR support in video encoder components
 */
public class HDREncoderTestBase extends CodecEncoderTestBase {
    private static final String LOG_TAG = HDREncoderTestBase.class.getSimpleName();

    private ByteBuffer mHdrStaticInfo;
    private Map<Integer, String> mHdrDynamicInfo;

    private MediaMuxer mMuxer;
    private int mTrackID = -1;

    public HDREncoderTestBase(String encoderName, String mediaType, int bitrate, int width,
            int height, RawResource rawResoure, String allTestParams) {
        super(encoderName, mediaType, new int[]{bitrate}, new int[]{width}, new int[]{height},
                rawResoure, allTestParams);
    }

    @After
    public void tearDownHdrEncoderTestBase() {
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }
    }

    protected void enqueueInput(int bufferIndex) {
        if (mHdrDynamicInfo != null && mHdrDynamicInfo.containsKey(mInputCount)) {
            insertHdrDynamicInfo(loadByteArrayFromString(mHdrDynamicInfo.get(mInputCount)));
        }
        super.enqueueInput(bufferIndex);
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        MediaFormat bufferFormat = mCodec.getOutputFormat(bufferIndex);
        if (info.size > 0) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(bufferFormat);
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    public void validateHDRInfo(String hdrStaticInfo, Map<Integer, String> hdrDynamicInfo)
            throws IOException, InterruptedException {
        mHdrStaticInfo = hdrStaticInfo != null
                ? ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfo)) : null;
        mHdrDynamicInfo = hdrDynamicInfo;

        setUpParams(1);

        MediaFormat format = mFormats.get(0);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUVP010);
        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020);
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_ST2084);
        int profile = (mHdrDynamicInfo != null)
                ? Objects.requireNonNull(PROFILE_HDR10_PLUS_MAP.get(mMime),
                        "mediaType : " + mMime + " has no profile supporting HDR10+")[0] :
                Objects.requireNonNull(PROFILE_HDR10_MAP.get(mMime),
                        "mediaType : " + mMime + " has no profile supporting HDR10")[0];
        format.setInteger(MediaFormat.KEY_PROFILE, profile);

        if (mHdrStaticInfo != null) {
            format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, mHdrStaticInfo);
        }
        Assume.assumeTrue(mCodecName + " does not support HDR10/HDR10+ profile " + profile,
                areFormatsSupported(mCodecName, mMime, mFormats));
        Assume.assumeTrue(mCodecName + " does not support color format COLOR_FormatYUVP010",
                hasSupportForColorFormat(mCodecName, mMime, COLOR_FormatYUVP010));

        setUpSource(mActiveRawRes.mFileName);

        int frameLimit = 4;
        if (mHdrDynamicInfo != null) {
            Integer lastHdr10PlusFrame =
                    Collections.max(HDR_DYNAMIC_INFO.entrySet(), Map.Entry.comparingByKey())
                            .getKey();
            frameLimit = lastHdr10PlusFrame + 10;
        }
        int maxNumFrames = mInputData.length
                / (mActiveRawRes.mWidth * mActiveRawRes.mHeight * mActiveRawRes.mBytesPerSample);
        assertTrue("HDR info tests require input file with at least " + frameLimit + " frames. "
                + mActiveRawRes.mFileName + " has " + maxNumFrames + " frames. \n" + mTestConfig
                + mTestEnv, frameLimit <= maxNumFrames);

        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        File tmpFile;
        int muxerFormat;
        if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
            tmpFile = File.createTempFile("tmp10bit", ".webm");
        } else {
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            tmpFile = File.createTempFile("tmp10bit", ".mp4");
        }
        mMuxer = new MediaMuxer(tmpFile.getAbsolutePath(), muxerFormat);
        configureCodec(format, true, true, true);
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        if (mTrackID != -1) {
            mMuxer.stop();
            mTrackID = -1;
        }
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }

        MediaFormat fmt = mCodec.getOutputFormat();

        mCodec.stop();
        mCodec.release();
        if (mHdrStaticInfo != null) {
            // verify if the out fmt contains HDR Static info as expected
            validateHDRInfo(fmt, MediaFormat.KEY_HDR_STATIC_INFO, mHdrStaticInfo);
        }

        // verify if the muxed file contains HDR Dynamic info as expected
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String decoder = codecList.findDecoderForFormat(format);
        assertNotNull("Device advertises support for encoding " + format + " but not decoding it \n"
                + mTestConfig + mTestEnv, decoder);

        HDRDecoderTestBase decoderTest =
                new HDRDecoderTestBase(decoder, mMime, tmpFile.getAbsolutePath(), mAllTestParams);
        decoderTest.validateHDRInfo(hdrStaticInfo, hdrStaticInfo, mHdrDynamicInfo, mHdrDynamicInfo);
        if (HDR_INFO_IN_BITSTREAM_CODECS.contains(mMime)) {
            decoderTest.validateHDRInfo(hdrStaticInfo, null, mHdrDynamicInfo, null);
        }
        tmpFile.delete();
    }
}
