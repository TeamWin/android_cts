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

import android.graphics.ImageFormat;

import com.android.compatibility.common.util.Preconditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Utility class for video encoder tests to validate the encoded output.
 * <p>
 * The class computes the PSNR between encoders output and input. As the input to an encoder can
 * be raw yuv buffer or the output of a decoder that is connected to the encoder, the test
 * accepts YUV as well as compressed streams for validation.
 * <p>
 * Before validation, the class checks if the input and output have same width, height and bitdepth.
 */
public class CompareStreams {
    private static final String LOG_TAG = CompareStreams.class.getSimpleName();

    private VideoErrorManager mStatistics;

    private final ArrayList<String> mTmpFiles = new ArrayList<>();

    public CompareStreams(String refMediaType, String refFile, String testMediaType,
            String testFile, boolean allowRefResize, boolean allowRefLoopBack)
            throws IOException, InterruptedException {
        DecodeStreamToYuv ref = new DecodeStreamToYuv(refMediaType, refFile);
        RawResource refYuv = ref.getDecodedYuv();
        mTmpFiles.add(refYuv.mFileName);

        DecodeStreamToYuv test = new DecodeStreamToYuv(testMediaType, testFile);
        RawResource testYuv = test.getDecodedYuv();
        mTmpFiles.add(testYuv.mFileName);

        init(refYuv, testYuv, allowRefResize, allowRefLoopBack);
    }

    public CompareStreams(RawResource refYuv, String testMediaType, String testFile,
            boolean allowRefResize, boolean allowRefLoopBack)
            throws IOException, InterruptedException {
        DecodeStreamToYuv test = new DecodeStreamToYuv(testMediaType, testFile);
        RawResource testYuv = test.getDecodedYuv();
        mTmpFiles.add(testYuv.mFileName);

        init(refYuv, testYuv, allowRefResize, allowRefLoopBack);
    }

    private void init(RawResource refYuv, RawResource testYuv, boolean allowRefResize,
            boolean allowRefLoopBack) throws IOException {
        if (refYuv.mBytesPerSample != testYuv.mBytesPerSample) {
            String msg = String.format(
                    "Reference file bytesPerSample and Test file bytesPerSample are not same. "
                            + "Reference bytesPerSample : %d, Test bytesPerSample : %d",
                    refYuv.mBytesPerSample, testYuv.mBytesPerSample);
            cleanUp();
            throw new IllegalArgumentException(msg);
        }
        RawResource refYuvResized;
        if (refYuv.mHeight == testYuv.mHeight && refYuv.mWidth == testYuv.mWidth) {
            refYuvResized = refYuv;
        } else {
            if (allowRefResize) {
                refYuvResized = readAndResizeInputRawYUV(refYuv, testYuv.mWidth, testYuv.mHeight,
                        testYuv.mBytesPerSample);
                mTmpFiles.add(refYuvResized.mFileName);
            } else {
                String msg = String.format(
                        "Reference file attributes and Test file attributes are not same. "
                                + "Reference width : %d, height : %d, bytesPerSample : %d, Test "
                                + "width : %d, height : %d, bytesPerSample : %d",
                        refYuv.mWidth, refYuv.mHeight, refYuv.mBytesPerSample, testYuv.mWidth,
                        testYuv.mHeight, testYuv.mBytesPerSample);
                cleanUp();
                throw new IllegalArgumentException(msg);
            }
        }
        mStatistics = new VideoErrorManager(refYuvResized, testYuv, allowRefLoopBack);
    }

    private RawResource readAndResizeInputRawYUV(RawResource res, int width, int height,
            int bytesPerSample) throws IOException {
        Preconditions.assertTestFileExists(res.mFileName);
        FileInputStream fInp = new FileInputStream(res.mFileName);
        int fileOffset = 0;
        int frameSize = res.mWidth * res.mHeight * bytesPerSample * 3 / 2;
        byte[] inputData = new byte[frameSize];
        int size = (int) new File(res.mFileName).length();
        File tmp = File.createTempFile("ref" + LOG_TAG, ".yuv");
        while (fileOffset + frameSize <= size) {
            fInp.read(inputData);
            fillAndWriteByteBuffer(width, height, bytesPerSample, res.mWidth, res.mHeight,
                    inputData, tmp);
            fileOffset += frameSize;
        }
        return new RawResource.Builder()
                .setFileName(tmp.getAbsolutePath(), false)
                .setDimension(width, height)
                .setBytesPerSample(bytesPerSample)
                .setColorFormat(ImageFormat.UNKNOWN)
                .build();
    }

    private void fillAndWriteByteBuffer(int frameWidth, int frameHeight, int bytesPerSample,
            int inpFrameWidth, int inpFrameHeight, byte[] inputData, File outFile)
            throws IOException {
        byte[] outputData = new byte[((3 * frameWidth * frameHeight * bytesPerSample) / 2)];
        int outOffset = 0;
        int inOffset = 0;
        for (int plane = 0; plane < 3; plane++) {
            int width, height, tileWidth, tileHeight;
            if (plane != 0) {
                width = frameWidth / 2;
                height = frameHeight / 2;
                tileWidth = inpFrameWidth / 2;
                tileHeight = inpFrameHeight / 2;
            } else {
                width = frameWidth;
                height = frameHeight;
                tileWidth = inpFrameWidth;
                tileHeight = inpFrameHeight;
            }
            for (int k = 0; k < height; k += tileHeight) {
                int rowsToCopy = Math.min(height - k, tileHeight);
                for (int j = 0; j < rowsToCopy; j++) {
                    for (int i = 0; i < width; i += tileWidth) {
                        int colsToCopy = Math.min(width - i, tileWidth);
                        System.arraycopy(inputData,
                                inOffset + j * tileWidth * bytesPerSample,
                                outputData,
                                outOffset + (k + j) * width * bytesPerSample + i * bytesPerSample,
                                colsToCopy * bytesPerSample);
                    }
                }
            }
            outOffset += width * height * bytesPerSample;
            inOffset += tileWidth * tileHeight * bytesPerSample;
        }
        try (FileOutputStream os = new FileOutputStream(outFile, true)) {
            os.write(outputData);
        }
    }

    public void cleanUp() {
        for (String tmpFile : mTmpFiles) {
            File tmp = new File(tmpFile);
            if (tmp.exists()) tmp.delete();
        }
        mTmpFiles.clear();
    }

    /**
     * @see VideoErrorManager#getGlobalPSNR()
     */
    public double[] getGlobalPSNR() throws IOException {
        return mStatistics.getGlobalPSNR();
    }

    /**
     * @see VideoErrorManager#getMinimumPSNR()
     */
    public double[] getMinimumPSNR() throws IOException {
        return mStatistics.getMinimumPSNR();
    }

    /**
     * @see VideoErrorManager#getFramesPSNR()
     */
    public ArrayList<double[]> getFramesPSNR() throws IOException {
        return mStatistics.getFramesPSNR();
    }

    /**
     * @see VideoErrorManager#getAvgPSNR()
     */
    public double[] getAvgPSNR() throws IOException {
        return mStatistics.getAvgPSNR();
    }
}
