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

import static android.media.MediaFormat.PICTURE_TYPE_B;
import static android.media.MediaFormat.PICTURE_TYPE_I;
import static android.media.MediaFormat.PICTURE_TYPE_P;
import static android.media.MediaFormat.PICTURE_TYPE_UNKNOWN;

import android.media.MediaCodec;
import android.media.MediaFormat;

import org.junit.Assert;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * This class contains utility functions that parse compressed bitstream and returns metadata
 * necessary for validation. This is by no means a thorough parser that is capable of parsing all
 * syntax elements of a bitstream. This is designed to handle only the requirements of mediav2
 * test suite.
 * <p>
 * Currently this class hosts utils that can,
 * <ul>
 *     <li>Return frame type of the access units of avc, hevc, av1.</li>
 * </ul>
 */
public class BitStreamUtils {
    static class ParsableBitArray {
        protected final byte[] mData;
        protected final int mOffset;
        protected final int mLimit;
        protected int mCurrByteOffset;
        protected int mCurrBitOffset;

        ParsableBitArray(byte[] data, int offset, int limit) {
            mData = data;
            mOffset = offset;
            mLimit = limit;
            mCurrByteOffset = offset;
            mCurrBitOffset = 0;
        }

        public boolean readBit() {
            if (mCurrByteOffset >= mLimit) {
                throw new ArrayIndexOutOfBoundsException(
                        String.format("Accessing bytes at offset %d, buffer limit %d",
                                mCurrByteOffset, mLimit));
            }
            boolean returnValue = (mData[mCurrByteOffset] & (0x80 >> mCurrBitOffset)) != 0;
            if (++mCurrBitOffset == 8) {
                mCurrBitOffset = 0;
                mCurrByteOffset++;
            }
            return returnValue;
        }

        public int readBits(int numBits) {
            if (numBits > 31) {
                throw new IllegalArgumentException(
                        "left shift overflow exception, reading too many bits at one go");
            }
            int value = 0;
            for (int i = 0; i < numBits; i++) {
                value <<= 1;
                value |= (readBit() ? 1 : 0);
            }
            return value;
        }

        public long readBitsLong(int numBits) {
            if (numBits > 63) {
                throw new IllegalArgumentException(
                        "left shift overflow exception, reading too many bits at one go");
            }
            long value = 0;
            for (int i = 0; i < numBits; i++) {
                value <<= 1;
                value |= (readBit() ? 1 : 0);
            }
            return value;
        }
    }

    static class NalParsableBitArray extends ParsableBitArray {
        NalParsableBitArray(byte[] data, int offset, int limit) {
            super(data, offset, limit);
        }

        @Override
        public boolean readBit() {
            // emulation prevention
            if (mCurrBitOffset == 0 && (mCurrByteOffset - 2 > mOffset)
                    && mData[mCurrByteOffset] == (byte) 0x03
                    && mData[mCurrByteOffset - 1] == (byte) 0x00
                    && mData[mCurrByteOffset - 2] == (byte) 0x00) {
                mCurrByteOffset++;
            }
            return super.readBit();
        }

        public int readUEV() {
            int leadingZeros = 0;
            while (!readBit()) {
                leadingZeros++;
            }
            return (1 << leadingZeros) - 1 + (leadingZeros > 0 ? readBits(leadingZeros) : 0);
        }
    }

    static class ObuParsableBitArray extends ParsableBitArray {
        ObuParsableBitArray(byte[] data, int offset, int limit) {
            super(data, offset, limit);
        }

        public long uvlc() {
            int leadingZeros = 0;
            while (true) {
                boolean done = readBit();
                if (done) {
                    break;
                }
                leadingZeros++;
            }
            if (leadingZeros >= 32) {
                return (1L << 32) - 1;
            }
            int value = readBits(leadingZeros);
            return value + (1L << leadingZeros) - 1;
        }

        public int[] leb128() {
            int value = 0, bytesRead = 0;
            for (int count = 0; count < 8; count++) {
                int leb128_byte = readBits(8);
                value |= (leb128_byte & 0x7f) << (count * 7);
                bytesRead++;
                if ((leb128_byte & 0x80) == 0) break;
            }
            return new int[]{bytesRead, value};
        }
    }

    public abstract static class ParserBase {
        protected byte[] mData;
        protected int mOffset;
        protected int mLimit;

        protected void set(byte[] data, int offset, int limit) {
            mData = data;
            mOffset = offset;
            mLimit = limit;
        }

        public abstract int getFrameType();
    }

    static class AvcParser extends ParserBase {
        private static final int NO_NAL_UNIT_FOUND = -1;

        private int getNalUnitStartOffset(byte[] dataArray, int start, int limit) {
            for (int pos = start; pos + 3 < limit; pos++) {
                if ((pos + 3) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 1) {
                    return pos + 3;
                } else if ((pos + 4) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 0 && dataArray[pos + 3] == 1) {
                    return pos + 4;
                }
            }
            return NO_NAL_UNIT_FOUND;
        }

        private static int getNalUnitType(byte[] dataArray, int nalUnitOffset) {
            return dataArray[nalUnitOffset] & 0x1F;
        }

        @Override
        public int getFrameType() {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return PICTURE_TYPE_UNKNOWN;
                int nalUnitType = getNalUnitType(mData, offset);
                if (nalUnitType == 1 || nalUnitType == 2 || nalUnitType == 5) {  // coded slice
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    bitArray.readBits(8); // forbidden zero bit + nal_ref_idc + nal_unit_type
                    bitArray.readUEV(); // first_mb_in_slice
                    int sliceType = bitArray.readUEV();
                    if (sliceType % 5 == 0) {
                        return PICTURE_TYPE_P;
                    } else if (sliceType % 5 == 1) {
                        return PICTURE_TYPE_B;
                    } else if (sliceType % 5 == 2) {
                        return PICTURE_TYPE_I;
                    } else {
                        return PICTURE_TYPE_UNKNOWN;
                    }
                }
                pos = offset;
            }
            return PICTURE_TYPE_UNKNOWN;
        }
    }

    static class HevcParser extends ParserBase {
        private static final int NO_NAL_UNIT_FOUND = -1;
        private static final int TRAIL_N = 0;
        private static final int RASL_R = 9;
        private static final int BLA_W_LP = 16;
        private static final int RSV_IRAP_VCL23 = 23;

        private int getNalUnitStartOffset(byte[] dataArray, int start, int limit) {
            for (int pos = start; pos + 3 < limit; pos++) {
                if ((pos + 3) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 1) {
                    return pos + 3;
                } else if ((pos + 4) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 0 && dataArray[pos + 3] == 1) {
                    return pos + 4;
                }
            }
            return NO_NAL_UNIT_FOUND;
        }

        private static int getNalUnitType(byte[] dataArray, int nalUnitOffset) {
            return (dataArray[nalUnitOffset] & 0x7E) >> 1;
        }

        @Override
        public int getFrameType() {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return PICTURE_TYPE_UNKNOWN;
                int nalUnitType = getNalUnitType(mData, offset);
                if ((nalUnitType >= TRAIL_N && nalUnitType <= RASL_R) || (nalUnitType >= BLA_W_LP
                        && nalUnitType <= RSV_IRAP_VCL23)) { // codec slice
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    bitArray.readBits(16); // nal_unit_header

                    // Parsing slice_segment_header values from H.265/HEVC Table 7.3.6.1
                    boolean firstSliceSegmentInPicFlag = bitArray.readBit();
                    if (!firstSliceSegmentInPicFlag) return PICTURE_TYPE_UNKNOWN;
                    if (nalUnitType >= BLA_W_LP && nalUnitType <= RSV_IRAP_VCL23) {
                        bitArray.readBit();  // no_output_of_prior_pics_flag
                    }
                    bitArray.readUEV(); // slice_pic_parameter_set_id
                    // FIXME: Assumes num_extra_slice_header_bits element of PPS data to be 0
                    int sliceType = bitArray.readUEV();
                    if (sliceType == 0) {
                        return PICTURE_TYPE_B;
                    } else if (sliceType == 1) {
                        return PICTURE_TYPE_P;
                    } else if (sliceType == 2) {
                        return PICTURE_TYPE_I;
                    } else {
                        return PICTURE_TYPE_UNKNOWN;
                    }
                }
                pos = offset;
            }
            return PICTURE_TYPE_UNKNOWN;
        }
    }

    static class Av1Parser extends ParserBase {
        private static final int OBU_SEQUENCE_HEADER = 1;
        private static final int OBU_FRAME_HEADER = 3;
        private static final int OBU_FRAME = 6;
        private static final int OBP_KEY_FRAME = 0;
        private static final int OBP_INTER_FRAME = 1;
        private static final int OBP_INTRA_ONLY_FRAME = 2;
        private static final int OBP_SWITCH_FRAME = 3;
        private static final int NUM_REF_FRAMES = 8;

        static class ObuInfo {
            private int mObuType;
            private int mTemporalId;
            private int mSpatialId;
            private int mHeaderSize;
            private int mSizeFieldSize;
            private int mDataSize;

            int getTotalObuSize() {
                return mHeaderSize + mSizeFieldSize + mDataSize;
            }

            int getObuDataOffset() {
                return mHeaderSize + mSizeFieldSize;
            }
        }

        static class SeqHeaderObu {
            public int seqProfile;
            public int reducedStillPictureHeader;
            public final int[] seqLevelIdx = new int[32];
            public int timingInfoPresentFlag;
            public int equalPictureInterval;
            public int decoderModelInfoPresentFlag;
            public int bufferDelayLengthMinus1;
            public int bufferRemovalTimeLengthMinus1;
            public int framePresentationTimeLengthMinus1;
            public int initialDisplayDelayPresentFlag;
            public int operatingPointsCntMinus1;
            public final int[] operatingPointIdc = new int[32];
            public final int[] seqTier = new int[32];
            public final int[] decoderModelPresentForThisOp = new int[32];
            public int frameIdNumbersPresentFlag;
            public int deltaFrameIdLengthMinus2;
            public int additionalFrameIdLengthMinus1;
            public int seqForceScreenContentTools;
            public int seqForceIntegerMv;
            public int orderHintBits;
            public int enableHighBitDepth;
        }

        static class FrameHeaderObu {
            public int frameType;
            public int showFrame;
            public int showExistingFrame;
            public int errorResilientMode;
        }

        private final SeqHeaderObu mSeqHeader = new SeqHeaderObu();
        private final int[] mRefFrameType = new int[NUM_REF_FRAMES];

        // obu header size, obu size field, obu size
        private ObuInfo parseObuHeader(byte[] dataArray, int pos, int limit) {
            int obuHeaderSize = 1;
            ObuInfo obuDetails = new ObuInfo();
            ObuParsableBitArray bitArray = new ObuParsableBitArray(dataArray, pos, limit);
            bitArray.readBits(1);  // forbidden bit
            obuDetails.mObuType = bitArray.readBits(4); // obu type
            int extensionFlag = bitArray.readBits(1);
            int hasSizeField = bitArray.readBits(1);
            Assert.assertEquals(1, hasSizeField);
            bitArray.readBits(1); // reserved 1bit
            if (extensionFlag == 1) {
                obuDetails.mTemporalId = bitArray.readBits(3);
                obuDetails.mSpatialId = bitArray.readBits(2);
                bitArray.readBits(3);
                obuHeaderSize++;
            }
            int[] obuSizeInfo = bitArray.leb128();
            obuDetails.mHeaderSize = obuHeaderSize;
            obuDetails.mSizeFieldSize = obuSizeInfo[0];
            obuDetails.mDataSize = obuSizeInfo[1];
            return obuDetails;
        }

        private void parseSequenceHeader(ObuParsableBitArray bitArray) {
            mSeqHeader.seqProfile = bitArray.readBits(3);
            bitArray.readBits(1); // still picture
            mSeqHeader.reducedStillPictureHeader = bitArray.readBits(1);
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                mSeqHeader.seqLevelIdx[0] = bitArray.readBits(5);
            } else {
                mSeqHeader.timingInfoPresentFlag = bitArray.readBits(1);
                if (mSeqHeader.timingInfoPresentFlag == 1) {
                    bitArray.readBitsLong(32); // num_units_in_display_tick
                    bitArray.readBitsLong(32); // time_scale
                    mSeqHeader.equalPictureInterval = bitArray.readBits(1);
                    if (mSeqHeader.equalPictureInterval == 1) {
                        bitArray.uvlc(); // num_ticks_per_picture_minus_1
                    }
                    mSeqHeader.decoderModelInfoPresentFlag = bitArray.readBits(1);
                    if (mSeqHeader.decoderModelInfoPresentFlag == 1) {
                        mSeqHeader.bufferDelayLengthMinus1 = bitArray.readBits(5);
                        bitArray.readBitsLong(32); // num_units_in_decoding_tick
                        mSeqHeader.bufferRemovalTimeLengthMinus1 = bitArray.readBits(5);
                        mSeqHeader.framePresentationTimeLengthMinus1 = bitArray.readBits(5);
                    }
                } else {
                    mSeqHeader.decoderModelInfoPresentFlag = 0;
                }
                mSeqHeader.initialDisplayDelayPresentFlag = bitArray.readBits(1);
                mSeqHeader.operatingPointsCntMinus1 = bitArray.readBits(5);
                for (int i = 0; i <= mSeqHeader.operatingPointsCntMinus1; i++) {
                    mSeqHeader.operatingPointIdc[i] = bitArray.readBits(12);
                    mSeqHeader.seqLevelIdx[i] = bitArray.readBits(5);
                    if (mSeqHeader.seqLevelIdx[i] > 7) {
                        mSeqHeader.seqTier[i] = bitArray.readBits(1);
                    }
                    if (mSeqHeader.decoderModelInfoPresentFlag == 1) {
                        mSeqHeader.decoderModelPresentForThisOp[i] = bitArray.readBits(1);
                        if (mSeqHeader.decoderModelPresentForThisOp[i] == 1) {
                            int n = mSeqHeader.bufferDelayLengthMinus1 + 1;
                            bitArray.readBits(n); // decoder_buffer_delay
                            bitArray.readBits(n); // encoder_buffer_delay
                            bitArray.readBits(1); // low_delay_mode_flag
                        }
                    } else {
                        mSeqHeader.decoderModelPresentForThisOp[i] = 0;
                    }
                    if (mSeqHeader.initialDisplayDelayPresentFlag == 1) {
                        if (bitArray.readBits(1) == 1) {
                            bitArray.readBits(4); // initial_display_delay_minus_1
                        }
                    }
                }
            }
            int frameWidthBitsMinus1 = bitArray.readBits(4);
            int frameHeightBitsMinus1 = bitArray.readBits(4);
            bitArray.readBits(frameWidthBitsMinus1 + 1); // max_frame_width_minus_1
            bitArray.readBits(frameHeightBitsMinus1 + 1); // max_frame_height_minus_1
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                mSeqHeader.frameIdNumbersPresentFlag = 0;
            } else {
                mSeqHeader.frameIdNumbersPresentFlag = bitArray.readBits(1);
            }
            if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                mSeqHeader.deltaFrameIdLengthMinus2 = bitArray.readBits(4);
                mSeqHeader.additionalFrameIdLengthMinus1 = bitArray.readBits(3);
            }
            bitArray.readBits(1); // use_128x128_superblock
            bitArray.readBits(1); // enable_filter_intra
            bitArray.readBits(1); // enable_intra_edge_filter
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                mSeqHeader.seqForceScreenContentTools = 2;
                mSeqHeader.seqForceIntegerMv = 2;
                mSeqHeader.orderHintBits = 0;
            } else {
                bitArray.readBits(1); // enable_interintra_compound
                bitArray.readBits(1); // enable_masked_compound
                bitArray.readBits(1); // enable_warped_motion
                bitArray.readBits(1); // enable_dual_filter
                int enableOrderHint = bitArray.readBits(1);
                if (enableOrderHint == 1) {
                    bitArray.readBits(1); // enable_jnt_comp
                    bitArray.readBits(1); // enable_ref_frame_mvs
                }
                int seqChooseScreenContentTools = bitArray.readBits(1);
                if (seqChooseScreenContentTools == 1) {
                    mSeqHeader.seqForceScreenContentTools = 2;
                } else {
                    mSeqHeader.seqForceScreenContentTools = bitArray.readBits(1);
                }
                if (mSeqHeader.seqForceScreenContentTools > 0) {
                    int seqChooseIntegerMv = bitArray.readBits(1);
                    if (seqChooseIntegerMv == 1) {
                        mSeqHeader.seqForceIntegerMv = 2;
                    } else {
                        mSeqHeader.seqForceIntegerMv = bitArray.readBits(1);
                    }
                } else {
                    mSeqHeader.seqForceIntegerMv = 2;
                }
                if (enableOrderHint == 1) {
                    int orderHintBitsMinus1 = bitArray.readBits(3);
                    mSeqHeader.orderHintBits = orderHintBitsMinus1 + 1;
                } else {
                    mSeqHeader.orderHintBits = 0;
                }
            }
            bitArray.readBits(1); // enable super res
            bitArray.readBits(1); // enable cdef
            bitArray.readBits(1); // enable restoration
            mSeqHeader.enableHighBitDepth = bitArray.readBits(1);
        }

        private FrameHeaderObu parseFrameHeader(ObuParsableBitArray bitArray, int temporalId,
                int spatialId) {
            FrameHeaderObu frameHeader = new FrameHeaderObu();
            int idLen = 0;
            if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                idLen = mSeqHeader.additionalFrameIdLengthMinus1
                        + mSeqHeader.deltaFrameIdLengthMinus2 + 3;
            }
            int allFrames = (1 << NUM_REF_FRAMES) - 1;
            int refreshFrameFlags = 0;
            int frameIsIntra;
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                frameHeader.frameType = OBP_KEY_FRAME;
                frameIsIntra = 1;
                frameHeader.showFrame = 1;
            } else {
                frameHeader.showExistingFrame = bitArray.readBits(1);
                if (frameHeader.showExistingFrame == 1) {
                    int frameToShowMapIdx = bitArray.readBits(3);
                    if (mSeqHeader.decoderModelInfoPresentFlag == 1
                            && mSeqHeader.equalPictureInterval == 0) {
                        int n = mSeqHeader.framePresentationTimeLengthMinus1 + 1;
                        bitArray.readBits(n); // frame_presentation_time
                    }
                    if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                        bitArray.readBits(idLen); // display_frame_id
                    }
                    frameHeader.frameType = mRefFrameType[frameToShowMapIdx];
                    if (frameHeader.frameType == OBP_KEY_FRAME) {
                        refreshFrameFlags = allFrames;
                    }
                    return frameHeader;
                }
                frameHeader.frameType = bitArray.readBits(2);
                frameIsIntra = (frameHeader.frameType == OBP_INTRA_ONLY_FRAME
                        || frameHeader.frameType == OBP_KEY_FRAME) ? 1 : 0;
                frameHeader.showFrame = bitArray.readBits(1);
                if (frameHeader.showFrame == 1 && mSeqHeader.decoderModelInfoPresentFlag == 1
                        && mSeqHeader.equalPictureInterval == 0) {
                    int n = mSeqHeader.framePresentationTimeLengthMinus1 + 1;
                    bitArray.readBits(n); // frame_presentation_time
                }
                if (frameHeader.showFrame == 0) {
                    bitArray.readBits(1); // showable_frame
                }
                if (frameHeader.frameType == OBP_SWITCH_FRAME || (
                        frameHeader.frameType == OBP_KEY_FRAME && frameHeader.showFrame == 1)) {
                    frameHeader.errorResilientMode = 1;
                } else {
                    frameHeader.errorResilientMode = bitArray.readBits(1);
                }
            }
            bitArray.readBits(1); // disable_cdf_update
            int allowScreenContentTools;
            if (mSeqHeader.seqForceScreenContentTools == 2) {
                allowScreenContentTools = bitArray.readBits(1);
            } else {
                allowScreenContentTools = mSeqHeader.seqForceScreenContentTools;
            }
            if (allowScreenContentTools == 1 && mSeqHeader.seqForceIntegerMv == 2) {
                bitArray.readBits(1); // force_integer_mv
            }
            if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                bitArray.readBits(idLen); // current_frame_id
            }
            if (frameHeader.frameType != OBP_SWITCH_FRAME
                    && mSeqHeader.reducedStillPictureHeader == 0) {
                bitArray.readBits(1); // frame_size_override_flag
            }
            bitArray.readBits(mSeqHeader.orderHintBits); // order_hint
            if (frameIsIntra != 1 && frameHeader.errorResilientMode == 0) {
                bitArray.readBits(3); // primary_ref_frame
            }

            if (mSeqHeader.decoderModelInfoPresentFlag == 1) {
                int bufferRemovalTimePresentFlag = bitArray.readBits(1);
                if (bufferRemovalTimePresentFlag == 1) {
                    for (int i = 0; i <= mSeqHeader.operatingPointsCntMinus1; i++) {
                        if (mSeqHeader.decoderModelPresentForThisOp[i] == 1) {
                            int opPtIdc = mSeqHeader.operatingPointIdc[i];
                            boolean inTemporalLayer = ((opPtIdc >> temporalId) & 1) == 1;
                            boolean inSpatialLayer = ((opPtIdc >> (spatialId + 8)) & 1) == 1;
                            if (opPtIdc == 0 || (inTemporalLayer && inSpatialLayer)) {
                                int n = mSeqHeader.bufferRemovalTimeLengthMinus1 + 1;
                                bitArray.readBits(n); // buffer_removal_time
                            }
                        }
                    }
                }
            }

            if (frameHeader.frameType == OBP_SWITCH_FRAME || (frameHeader.frameType == OBP_KEY_FRAME
                    && frameHeader.showFrame == 1)) {
                refreshFrameFlags = allFrames;
            } else {
                refreshFrameFlags = bitArray.readBits(8);
            }

            for (int i = 0; i < 8; i++) {
                if ((refreshFrameFlags >> i & 1) == 1) {
                    mRefFrameType[i] = frameHeader.frameType;
                }
            }
            return frameHeader;
        }

        @Override
        public int getFrameType() {
            ArrayList<FrameHeaderObu> headers = new ArrayList();
            for (int pos = mOffset; pos < mLimit; ) {
                ObuInfo obuDetails = parseObuHeader(mData, pos, mLimit);
                ObuParsableBitArray bitArray =
                        new ObuParsableBitArray(mData, pos + obuDetails.getObuDataOffset(),
                                pos + obuDetails.getTotalObuSize());
                if (obuDetails.mObuType == OBU_SEQUENCE_HEADER) {
                    parseSequenceHeader(bitArray);
                } else if (obuDetails.mObuType == OBU_FRAME_HEADER
                        || obuDetails.mObuType == OBU_FRAME) {
                    FrameHeaderObu frameHeader = parseFrameHeader(bitArray, obuDetails.mTemporalId,
                            obuDetails.mSpatialId);
                    headers.add(frameHeader);
                }
                pos += obuDetails.getTotalObuSize();
            }
            for (FrameHeaderObu frameHeader : headers) {
                if (frameHeader.showFrame == 1 || frameHeader.showExistingFrame == 1) {
                    if (frameHeader.frameType == OBP_KEY_FRAME
                            || frameHeader.frameType == OBP_INTRA_ONLY_FRAME) {
                        return PICTURE_TYPE_I;
                    } else if (frameHeader.frameType == OBP_INTER_FRAME) {
                        return PICTURE_TYPE_P;
                    }
                    return PICTURE_TYPE_UNKNOWN;
                }
            }
            return PICTURE_TYPE_UNKNOWN;
        }
    }

    public static ParserBase getParserObject(String mediaType) {
        switch (mediaType) {
            case MediaFormat.MIMETYPE_VIDEO_AVC:
                return new AvcParser();
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
                return new HevcParser();
            case MediaFormat.MIMETYPE_VIDEO_AV1:
                return new Av1Parser();
        }
        return null;
    }

    public static int getFrameTypeFromBitStream(ByteBuffer buf, MediaCodec.BufferInfo info,
            ParserBase o) {
        byte[] dataArray = new byte[info.size];
        buf.position(info.offset);
        buf.get(dataArray);
        o.set(dataArray, 0, info.size);
        return o.getFrameType();
    }
}
