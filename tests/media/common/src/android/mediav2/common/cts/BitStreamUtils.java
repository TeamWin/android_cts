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

import java.nio.ByteBuffer;

/**
 * This class contains utility functions that parse compressed bitstream and returns metadata
 * necessary for validation.
 * <p>
 * Currently this class hosts utils that can,
 * <ul>
 *     <li>Return frame type of the access units of avc, hevc.</li>
 * </ul>
 */
public class BitStreamUtils {
    private static final int NO_NAL_UNIT_FOUND = 0;
    private static final int TRAIL_N = 0;
    private static final int RASL_R = 9;
    private static final int BLA_W_LP = 16;
    private static final int RSV_IRAP_VCL23 = 23;

    static class ParsableBitArray {
        private final byte[] mData;
        private final int mOffset;
        private final int mLimit;
        private int mCurrByteOffset;
        private int mCurrBitOffset;

        ParsableBitArray(byte[] data, int offset, int limit) {
            mData = data;
            mOffset = offset;
            mLimit = limit;
            mCurrByteOffset = offset;
            mCurrBitOffset = 0;
        }

        public boolean readBit() {
            // emulation prevention
            if (mCurrBitOffset == 0 && (mCurrByteOffset - 2 > mOffset)
                    && mData[mCurrByteOffset] == (byte) 0x03
                    && mData[mCurrByteOffset - 1] == (byte) 0x00
                    && mData[mCurrByteOffset - 2] == (byte) 0x00) {
                mCurrByteOffset++;
            }
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

        public int readUEV() {
            int leadingZeros = 0;
            while (!readBit()) {
                leadingZeros++;
            }
            return (1 << leadingZeros) - 1 + (leadingZeros > 0 ? readBits(leadingZeros) : 0);
        }
    }

    private static int getNalUnitStartOffset(byte[] dataArray, int pos, int limit) {
        if ((pos + 3) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                && dataArray[pos + 2] == 1) {
            return 3;
        } else if ((pos + 4) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                && dataArray[pos + 2] == 0 && dataArray[pos + 3] == 1) {
            return 4;
        }
        return NO_NAL_UNIT_FOUND;
    }

    private static int getAvcNalUnitType(byte[] dataArray, int nalUnitOffset) {
        return dataArray[nalUnitOffset] & 0x1F;
    }

    private static int parseAvcCodecSliceNalUnit(byte[] dataArray, int offset, int limit) {
        ParsableBitArray bitArray = new ParsableBitArray(dataArray, offset, limit);

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

    private static int getHevcNalUnitType(byte[] dataArray, int nalUnitOffset) {
        return (dataArray[nalUnitOffset] & 0x7E) >> 1;
    }

    private static int parseHevcSliceSegmentNalUnit(byte[] dataArray, int offset, int limit,
            int nalUnitType) {
        // nal_unit_type values from H.265/HEVC Table 7-1.
        ParsableBitArray bitArray = new ParsableBitArray(dataArray, offset, limit);

        bitArray.readBits(16); // nal_unit_header

        // Parsing slice_segment_header values from H.265/HEVC Table 7.3.6.1
        boolean first_slice_segment = bitArray.readBit(); // first_slice_segment_in_pic_flag
        if (!first_slice_segment) return -1;
        if (nalUnitType >= BLA_W_LP && nalUnitType <= RSV_IRAP_VCL23) {
            bitArray.readBit();  // no_output_of_prior_pics_flag
        }
        bitArray.readUEV(); // slice_pic_parameter_set_id
        // Assumes num_extra_slice_header_bits element of PPS data to be 0
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

    public static int getFrameTypeFromBitStream(String mediaType, ByteBuffer buf,
            MediaCodec.BufferInfo info) {
        int frameType = PICTURE_TYPE_UNKNOWN;
        boolean isAvc = mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC);
        boolean isHevc = mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC);
        if (!(isAvc || isHevc)) return frameType;
        byte[] dataArray = new byte[info.size];
        buf.position(info.offset);
        buf.get(dataArray);
        for (int pos = 0; pos + 3 < info.size; ) {
            int startOffset = BitStreamUtils.getNalUnitStartOffset(dataArray, pos, info.size);
            if (startOffset != NO_NAL_UNIT_FOUND) {
                int nalUnitType;
                if (isAvc) {
                    nalUnitType = getAvcNalUnitType(dataArray, (pos + startOffset));
                    if (nalUnitType == 1 || nalUnitType == 2 || nalUnitType == 5) {
                        frameType = parseAvcCodecSliceNalUnit(dataArray, (pos + startOffset),
                                info.size);
                        break;
                    }
                } else {
                    nalUnitType = getHevcNalUnitType(dataArray, (pos + startOffset));
                    if ((nalUnitType >= TRAIL_N && nalUnitType <= RASL_R)
                            || (nalUnitType >= BLA_W_LP && nalUnitType <= RSV_IRAP_VCL23)) {
                        frameType = parseHevcSliceSegmentNalUnit(dataArray, (pos + startOffset),
                                info.size, nalUnitType);
                        break;
                    }
                }
                pos += startOffset;
            } else {
                pos++;
            }
        }
        return frameType;
    }
}
