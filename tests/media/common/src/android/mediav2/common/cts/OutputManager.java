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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.Image;
import android.media.MediaCodec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.CRC32;

/**
 * Class to store the output received from mediacodec components. The dequeueOutput() call sends
 * compressed/decoded bytes of data and their corresponding timestamp information. This is stored
 * in memory and outPtsList fields of this class. For video decoders, the decoded information can
 * be overwhelming as it is uncompressed YUV. For them we compute the CRC32 checksum of the
 * output image and buffer and store it instead.
 */
public class OutputManager {
    private static final String LOG_TAG = OutputManager.class.getSimpleName();
    private byte[] mMemory;
    private int mMemIndex;
    private final CRC32 mCrc32UsingImage;
    private final CRC32 mCrc32UsingBuffer;
    private final ArrayList<Long> mInpPtsList;
    private final ArrayList<Long> mOutPtsList;
    private final StringBuilder mErrorLogs;

    public OutputManager() {
        mMemory = new byte[1024];
        mMemIndex = 0;
        mCrc32UsingImage = new CRC32();
        mCrc32UsingBuffer = new CRC32();
        mInpPtsList = new ArrayList<>();
        mOutPtsList = new ArrayList<>();
        mErrorLogs = new StringBuilder(
                "##################       Error Details         ####################\n");
    }

    public void saveInPTS(long pts) {
        // Add only unique timeStamp, discarding any duplicate frame / non-display frame
        if (!mInpPtsList.contains(pts)) {
            mInpPtsList.add(pts);
        }
    }

    public void saveOutPTS(long pts) {
        mOutPtsList.add(pts);
    }

    public boolean isPtsStrictlyIncreasing(long lastPts) {
        boolean res = true;
        for (int i = 0; i < mOutPtsList.size(); i++) {
            if (lastPts < mOutPtsList.get(i)) {
                lastPts = mOutPtsList.get(i);
            } else {
                mErrorLogs.append("Timestamp values are not strictly increasing. \n");
                mErrorLogs.append("Frame indices around which timestamp values decreased :- \n");
                for (int j = Math.max(0, i - 3); j < Math.min(mOutPtsList.size(), i + 3); j++) {
                    if (j == 0) {
                        mErrorLogs.append(String.format("pts of frame idx -1 is %d \n", lastPts));
                    }
                    mErrorLogs.append(String.format("pts of frame idx %d is %d \n", j,
                            mOutPtsList.get(j)));
                }
                res = false;
                break;
            }
        }
        return res;
    }

    static boolean arePtsListsIdentical(ArrayList<Long> refList, ArrayList<Long> testList,
            StringBuilder msg) {
        boolean res = true;
        if (refList.size() != testList.size()) {
            msg.append("Reference and test timestamps list sizes are not identical \n");
            msg.append(String.format("reference pts list size is %d \n", refList.size()));
            msg.append(String.format("test pts list size is %d \n", testList.size()));
            res = false;
        }
        if (!res || !refList.equals(testList)) {
            res = false;
            ArrayList<Long> refCopyList = new ArrayList<>(refList);
            ArrayList<Long> testCopyList = new ArrayList<>(testList);
            refCopyList.removeAll(testList);
            testCopyList.removeAll(refList);
            if (refCopyList.size() != 0) {
                msg.append("Some of the frame/access-units present in ref list are not present "
                        + "in test list. Possibly due to frame drops. \n");
                msg.append("List of timestamps that are dropped by the component :- \n");
                msg.append("pts :- [[ ");
                for (int i = 0; i < refCopyList.size(); i++) {
                    msg.append(String.format("{ %d us }, ", refCopyList.get(i)));
                }
                msg.append(" ]]\n");
            }
            if (testCopyList.size() != 0) {
                msg.append("Test list contains frame/access-units that are not present in"
                        + " ref list, Possible due to duplicate transmissions. \n");
                msg.append("List of timestamps that are additionally present in test list"
                        + " are :- \n");
                msg.append("pts :- [[ ");
                for (int i = 0; i < testCopyList.size(); i++) {
                    msg.append(String.format("{ %d us }, ", testCopyList.get(i)));
                }
                msg.append(" ]]\n");
            }
        }
        return res;
    }

    public boolean isOutPtsListIdenticalToInpPtsList(boolean requireSorting) {
        Collections.sort(mInpPtsList);
        if (requireSorting) {
            Collections.sort(mOutPtsList);
        }
        return arePtsListsIdentical(mInpPtsList, mOutPtsList, mErrorLogs);
    }

    public int getOutStreamSize() {
        return mMemIndex;
    }

    public void checksum(ByteBuffer buf, int size) {
        checksum(buf, size, 0, 0, 0, 0);
    }

    public void checksum(ByteBuffer buf, int size, int width, int height, int stride,
            int bytesPerSample) {
        int cap = buf.capacity();
        assertTrue("checksum() params are invalid: size = " + size + " cap = " + cap,
                size > 0 && size <= cap);
        if (buf.hasArray()) {
            if (width > 0 && height > 0 && stride > 0 && bytesPerSample > 0) {
                int offset = buf.position() + buf.arrayOffset();
                byte[] bb = new byte[width * height * bytesPerSample];
                for (int i = 0; i < height; ++i) {
                    System.arraycopy(buf.array(), offset, bb, i * width * bytesPerSample,
                            width * bytesPerSample);
                    offset += stride;
                }
                mCrc32UsingBuffer.update(bb, 0, width * height * bytesPerSample);
            } else {
                mCrc32UsingBuffer.update(buf.array(), buf.position() + buf.arrayOffset(), size);
            }
        } else if (width > 0 && height > 0 && stride > 0 && bytesPerSample > 0) {
            // Checksum only the Y plane
            int pos = buf.position();
            int offset = pos;
            byte[] bb = new byte[width * height * bytesPerSample];
            for (int i = 0; i < height; ++i) {
                buf.position(offset);
                buf.get(bb, i * width * bytesPerSample, width * bytesPerSample);
                offset += stride;
            }
            mCrc32UsingBuffer.update(bb, 0, width * height * bytesPerSample);
            buf.position(pos);
        } else {
            int pos = buf.position();
            final int rdsize = Math.min(4096, size);
            byte[] bb = new byte[rdsize];
            int chk;
            for (int i = 0; i < size; i += chk) {
                chk = Math.min(rdsize, size - i);
                buf.get(bb, 0, chk);
                mCrc32UsingBuffer.update(bb, 0, chk);
            }
            buf.position(pos);
        }
    }

    public void checksum(Image image) {
        int format = image.getFormat();
        assertTrue("unexpected image format",
                format == ImageFormat.YUV_420_888 || format == ImageFormat.YCBCR_P010);
        int bytesPerSample = (ImageFormat.getBitsPerPixel(format) * 2) / (8 * 3);  // YUV420

        Rect cropRect = image.getCropRect();
        int imageWidth = cropRect.width();
        int imageHeight = cropRect.height();
        assertTrue("unexpected image dimensions", imageWidth > 0 && imageHeight > 0);

        int imageLeft = cropRect.left;
        int imageTop = cropRect.top;
        Image.Plane[] planes = image.getPlanes();
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width, height, rowStride, pixelStride, x, y, left, top;
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            if (i == 0) {
                assertEquals(bytesPerSample, pixelStride);
                width = imageWidth;
                height = imageHeight;
                left = imageLeft;
                top = imageTop;
            } else {
                width = imageWidth / 2;
                height = imageHeight / 2;
                left = imageLeft / 2;
                top = imageTop / 2;
            }
            int cropOffset = (left * pixelStride) + top * rowStride;
            // local contiguous pixel buffer
            byte[] bb = new byte[width * height * bytesPerSample];

            if (buf.hasArray()) {
                byte[] b = buf.array();
                int offs = buf.arrayOffset() + cropOffset;
                if (pixelStride == bytesPerSample) {
                    for (y = 0; y < height; ++y) {
                        System.arraycopy(b, offs + y * rowStride, bb, y * width * bytesPerSample,
                                width * bytesPerSample);
                    }
                } else {
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        int lineOffset = offs + y * rowStride;
                        for (x = 0; x < width; ++x) {
                            for (int bytePos = 0; bytePos < bytesPerSample; ++bytePos) {
                                bb[y * width * bytesPerSample + x * bytesPerSample + bytePos] =
                                        b[lineOffset + x * pixelStride + bytePos];
                            }
                        }
                    }
                }
            } else { // almost always ends up here due to direct buffers
                int base = buf.position();
                int pos = base + cropOffset;
                if (pixelStride == bytesPerSample) {
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        buf.get(bb, y * width * bytesPerSample, width * bytesPerSample);
                    }
                } else {
                    // local line buffer
                    byte[] lb = new byte[rowStride];
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        // we're only guaranteed to have pixelStride * (width - 1) +
                        // bytesPerSample bytes
                        buf.get(lb, 0, pixelStride * (width - 1) + bytesPerSample);
                        for (x = 0; x < width; ++x) {
                            for (int bytePos = 0; bytePos < bytesPerSample; ++bytePos) {
                                bb[y * width * bytesPerSample + x * bytesPerSample + bytePos] =
                                        lb[x * pixelStride + bytePos];
                            }
                        }
                    }
                }
                buf.position(base);
            }
            mCrc32UsingImage.update(bb, 0, width * height * bytesPerSample);
        }
    }

    public void saveToMemory(ByteBuffer buf, MediaCodec.BufferInfo info) {
        if (mMemIndex + info.size >= mMemory.length) {
            mMemory = Arrays.copyOf(mMemory, mMemIndex + info.size);
        }
        buf.position(info.offset);
        buf.get(mMemory, mMemIndex, info.size);
        mMemIndex += info.size;
    }

    void position(int index) {
        if (index < 0 || index >= mMemory.length) index = 0;
        mMemIndex = index;
    }

    public ByteBuffer getBuffer() {
        return ByteBuffer.wrap(mMemory);
    }

    public void reset() {
        position(0);
        mCrc32UsingImage.reset();
        mCrc32UsingBuffer.reset();
        mInpPtsList.clear();
        mOutPtsList.clear();
        mErrorLogs.setLength(0);
        mErrorLogs.append("##################       Error Details         ####################\n");
    }

    public float getRmsError(Object refObject, int audioFormat) {
        double totalErrorSquared = 0;
        double avgErrorSquared;
        int bytesPerSample = AudioFormat.getBytesPerSample(audioFormat);
        if (refObject instanceof float[]) {
            if (audioFormat != AudioFormat.ENCODING_PCM_FLOAT) return Float.MAX_VALUE;
            float[] refData = (float[]) refObject;
            if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
            float[] floatData = new float[refData.length];
            ByteBuffer.wrap(mMemory, 0, mMemIndex).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    .get(floatData);
            for (int i = 0; i < refData.length; i++) {
                float d = floatData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof int[]) {
            int[] refData = (int[]) refObject;
            int[] intData;
            if (audioFormat == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
                if (refData.length != (mMemIndex / bytesPerSample)) return Float.MAX_VALUE;
                intData = new int[refData.length];
                for (int i = 0, j = 0; i < mMemIndex; i += 3, j++) {
                    intData[j] = mMemory[j] | (mMemory[j + 1] << 8) | (mMemory[j + 2] << 16);
                }
            } else if (audioFormat == AudioFormat.ENCODING_PCM_32BIT) {
                if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
                intData = new int[refData.length];
                ByteBuffer.wrap(mMemory, 0, mMemIndex).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                        .get(intData);
            } else {
                return Float.MAX_VALUE;
            }
            for (int i = 0; i < intData.length; i++) {
                float d = intData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof short[]) {
            short[] refData = (short[]) refObject;
            if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
            if (audioFormat != AudioFormat.ENCODING_PCM_16BIT) return Float.MAX_VALUE;
            short[] shortData = new short[refData.length];
            ByteBuffer.wrap(mMemory, 0, mMemIndex).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .get(shortData);
            for (int i = 0; i < shortData.length; i++) {
                float d = shortData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof byte[]) {
            byte[] refData = (byte[]) refObject;
            if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
            if (audioFormat != AudioFormat.ENCODING_PCM_8BIT) return Float.MAX_VALUE;
            byte[] byteData = new byte[refData.length];
            ByteBuffer.wrap(mMemory, 0, mMemIndex).get(byteData);
            for (int i = 0; i < byteData.length; i++) {
                float d = byteData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else {
            return Float.MAX_VALUE;
        }
        return (float) Math.sqrt(avgErrorSquared);
    }

    public long getCheckSumImage() {
        return mCrc32UsingImage.getValue();
    }

    public long getCheckSumBuffer() {
        return mCrc32UsingBuffer.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;

        if (!this.equalsInterlaced(o)) return false;
        return arePtsListsIdentical(mOutPtsList, that.mOutPtsList, that.mErrorLogs);
    }

    // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
    // produce multiple progressive frames?) For now, do not verify timestamps.
    public boolean equalsInterlaced(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;
        boolean isEqual = true;
        if (mCrc32UsingImage.getValue() != that.mCrc32UsingImage.getValue()) {
            isEqual = false;
            that.mErrorLogs.append("CRC32 checksums computed for image buffers received from "
                    + "getOutputImage() do not match between ref and test runs. \n");
            that.mErrorLogs.append(String.format("Ref CRC32 checksum value is %d \n",
                    mCrc32UsingImage.getValue()));
            that.mErrorLogs.append(String.format("Test CRC32 checksum value is %d \n",
                    that.mCrc32UsingImage.getValue()));
        }
        if (mCrc32UsingBuffer.getValue() != that.mCrc32UsingBuffer.getValue()) {
            isEqual = false;
            that.mErrorLogs.append("CRC32 checksums computed for byte buffers received from "
                    + "getOutputBuffer() do not match between ref and test runs. \n");
            that.mErrorLogs.append(String.format("Ref CRC32 checksum value is %d \n",
                    mCrc32UsingBuffer.getValue()));
            that.mErrorLogs.append(String.format("Test CRC32 checksum value is %d \n",
                    that.mCrc32UsingBuffer.getValue()));
            if (mMemIndex == that.mMemIndex) {
                int count = 0;
                StringBuilder msg = new StringBuilder();
                for (int i = 0; i < mMemIndex; i++) {
                    if (mMemory[i] != that.mMemory[i]) {
                        count++;
                        msg.append(String.format("At offset %d, ref buffer val is %x and test "
                                + "buffer val is %x \n", i, mMemory[i], that.mMemory[i]));
                        if (count == 20) {
                            msg.append("stopping after 20 mismatches, ...\n");
                            break;
                        }
                    }
                }
                if (count != 0) {
                    that.mErrorLogs.append("Ref and Test outputs are not identical \n");
                    that.mErrorLogs.append(msg);
                }
            } else {
                that.mErrorLogs.append("CRC32 byte buffer checksums are different because ref and"
                        + " test output sizes are not identical \n");
                that.mErrorLogs.append(String.format("Ref output buffer size %d \n", mMemIndex));
                that.mErrorLogs.append(String.format("Test output buffer size %d \n",
                        that.mMemIndex));
            }
        }
        return isEqual;
    }

    public String getErrMsg() {
        return mErrorLogs.toString();
    }
}
