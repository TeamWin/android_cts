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

package android.media.decoder.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.media.cts.Preconditions;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@RunWith(Parameterized.class)
public class HDRDecoderTest extends MediaTestBase {
    private static final String TAG = "HDRDecoderTest";
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final String VP9_HDR_RES = "video_1280x720_vp9_hdr_static_3mbps.mkv";
    private static final String VP9_HDR_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4  86 4c 1d b8 0b 13 3d 42" +
            "40 e8 03 64 00 e8 03 2c  01                     " ;

    private static final String AV1_HDR_RES = "video_1280x720_av1_hdr_static_3mbps.webm";
    private static final String AV1_HDR_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4  86 4c 1d b8 0b 13 3d 42" +
            "40 e8 03 64 00 e8 03 2c  01                     " ;

    // Expected value of MediaFormat.KEY_HDR_STATIC_INFO key.
    // The associated value is a ByteBuffer. This buffer contains the raw contents of the
    // Static Metadata Descriptor (including the descriptor ID) of an HDMI Dynamic Range and
    // Mastering InfoFrame as defined by CTA-861.3.
    // Media frameworks puts the display primaries in RGB order, here we verify the three
    // primaries are indeed in this order and fail otherwise.
    private static final String H265_HDR10_RES = "video_1280x720_hevc_hdr10_static_3mbps.mp4";
    private static final String H265_HDR10_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4  86 4c 1d b8 0b 13 3d 42" +
            "40 e8 03 00 00 e8 03 90  01                     " ;

    private static final String VP9_HDR10PLUS_RES = "video_bikes_hdr10plus.webm";
    private static final String VP9_HDR10PLUS_STATIC_INFO =
            "00 4c 1d b8 0b d0 84 80  3e c0 33 c4 86 12 3d 42" +
            "40 e8 03 32 00 e8 03 c8  00                     " ;
    // TODO: Use some manually extracted metadata for now.
    // MediaExtractor currently doesn't have an API for extracting
    // the dynamic metadata. Get the metadata from extractor when
    // it's supported.
    private static final String[] VP9_HDR10PLUS_DYNAMIC_INFO = new String[] {
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,
    };

    private static final String H265_HDR10PLUS_RES = "video_h265_hdr10plus.mp4";
    private static final String H265_HDR10PLUS_STATIC_INFO =
            "00 4c 1d b8 0b d0 84 80  3e c2 33 c4 86 13 3d 42" +
            "40 e8 03 32 00 e8 03 c8  00                     " ;
    private static final String[] H265_HDR10PLUS_DYNAMIC_INFO = new String[] {
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0f 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0f 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00"
    };

    private DisplayManager mDisplayManager;

    @Parameterized.Parameter(0)
    public String mMediaType;

    @Parameterized.Parameter(1)
    public String mInputFile;

    @Parameterized.Parameter(2)
    public String mHdrStaticInfo;

    @Parameterized.Parameter(3)
    public String[] mHdrDynamicInfo;

    @Parameterized.Parameter(4)
    public boolean mMetaDataInContainer;

    @Parameterized.Parameter(5)
    public String mTestId;

    @Parameterized.Parameters(name = "{index}({0}_{5})")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR_RES, AV1_HDR_STATIC_INFO, null, false,
                        "static"},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, H265_HDR10_RES, H265_HDR10_STATIC_INFO, null,
                        false, "static"},
                {MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR_RES, VP9_HDR_STATIC_INFO, null, true,
                        "static"},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, H265_HDR10PLUS_RES, H265_HDR10PLUS_STATIC_INFO,
                        H265_HDR10PLUS_DYNAMIC_INFO, false, "dynamic"},
                {MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR10PLUS_RES, VP9_HDR10PLUS_STATIC_INFO,
                        VP9_HDR10PLUS_DYNAMIC_INFO, true, "dynamic"},
        });
        return exhaustiveArgsList;
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    @CddTest(requirements = {"5.3.5/C-3-1", "5.3.7/C-4-1", "5.3.9"})
    @Test
    public void testHdrMetadata() throws Exception {
        AssetFileDescriptor infd = null;
        MediaExtractor extractor = null;
        final boolean dynamic = mHdrDynamicInfo != null;

        Preconditions.assertTestFileExists(MEDIA_DIR + mInputFile);
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(MEDIA_DIR + mInputFile);

            MediaFormat format = null;
            int trackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    trackIndex = i;
                    break;
                }
            }

            assertTrue("Extractor failed to extract video track",
                    format != null && trackIndex >= 0);
            if (mMetaDataInContainer) {
                verifyHdrStaticInfo("Extractor failed to extract static info", format,
                        mHdrStaticInfo);
            }

            extractor.selectTrack(trackIndex);
            Log.v(TAG, "format " + format);

            String mime = format.getString(MediaFormat.KEY_MIME);
            // setting profile and level
            if (MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mime)) {
                if (!dynamic) {
                    assertEquals("Extractor set wrong profile",
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                        format.getInteger(MediaFormat.KEY_PROFILE));
                } else {
                    // Extractor currently doesn't detect HDR10+, set to HDR10+ manually
                    format.setInteger(MediaFormat.KEY_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus);
                }
            } else if (MediaFormat.MIMETYPE_VIDEO_VP9.equals(mime)) {
                // The muxer might not have put VP9 CSD in the mkv, we manually patch
                // it here so that we only test HDR when decoder supports it.
                format.setInteger(MediaFormat.KEY_PROFILE,
                        dynamic ? MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus
                                : MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR);
            } else if (MediaFormat.MIMETYPE_VIDEO_AV1.equals(mime)) {
                // The muxer might not have put AV1 CSD in the webm, we manually patch
                // it here so that we only test HDR when decoder supports it.
                format.setInteger(MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10);
            } else {
                fail("Codec " + mime + " shouldn't be tested with this test!");
            }
            String[] decoderNames = MediaUtils.getDecoderNames(format);

            int numberOfSupportedHdrTypes =
                    mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                            .getSupportedHdrTypes().length;

            if (decoderNames == null || decoderNames.length == 0
                    || numberOfSupportedHdrTypes == 0) {
                MediaUtils.skipTest("No video codecs supports HDR");
                return;
            }

            final Surface surface = getActivity().getSurfaceHolder().getSurface();
            final MediaExtractor finalExtractor = extractor;

            for (String name : decoderNames) {
                Log.d(TAG, "Testing candicate decoder " + name);
                CountDownLatch latch = new CountDownLatch(1);
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

                MediaCodec decoder = MediaCodec.createByCodecName(name);
                decoder.setCallback(new MediaCodec.Callback() {
                    boolean mInputEOS;
                    boolean mOutputReceived;
                    int mInputCount;
                    int mOutputCount;

                    @Override
                    public void onOutputBufferAvailable(
                            MediaCodec codec, int index, BufferInfo info) {
                        if (mOutputReceived) {
                            return;
                        }

                        MediaFormat bufferFormat = codec.getOutputFormat(index);
                        Log.i(TAG, "got output buffer: format " + bufferFormat);

                        verifyHdrStaticInfo("Output buffer has wrong static info",
                                bufferFormat, mHdrStaticInfo);

                        if (!dynamic) {
                            codec.releaseOutputBuffer(index,  true);

                            mOutputReceived = true;
                            latch.countDown();
                        } else {
                            ByteBuffer hdr10plus =
                                    bufferFormat.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO)
                                    ? bufferFormat.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO)
                                    : null;

                            verifyHdrDynamicInfo("Output buffer has wrong hdr10+ info",
                                    bufferFormat, mHdrDynamicInfo[mOutputCount]);

                            codec.releaseOutputBuffer(index,  true);

                            mOutputCount++;
                            if (mOutputCount >= mHdrDynamicInfo.length) {
                                mOutputReceived = true;
                                latch.countDown();
                            }
                        }
                    }

                    @Override
                    public void onInputBufferAvailable(MediaCodec codec, int index) {
                        // keep queuing until input EOS, or first output buffer received.
                        if (mInputEOS || mOutputReceived) {
                            return;
                        }

                        ByteBuffer inputBuffer = codec.getInputBuffer(index);

                        if (finalExtractor.getSampleTrackIndex() == -1) {
                            codec.queueInputBuffer(
                                    index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            mInputEOS = true;
                        } else {
                            int size = finalExtractor.readSampleData(inputBuffer, 0);
                            long timestamp = finalExtractor.getSampleTime();
                            finalExtractor.advance();

                            if (dynamic && mMetaDataInContainer) {
                                final Bundle params = new Bundle();
                                // TODO: extractor currently doesn't extract the dynamic metadata.
                                // Send in the test pattern for now to test the metadata propagation.
                                byte[] info = loadByteArrayFromString(mHdrDynamicInfo[mInputCount]);
                                params.putByteArray(MediaFormat.KEY_HDR10_PLUS_INFO, info);
                                codec.setParameters(params);
                                mInputCount++;
                                if (mInputCount >= mHdrDynamicInfo.length) {
                                    mInputEOS = true;
                                }
                            }
                            codec.queueInputBuffer(index, 0, size, timestamp, 0);
                        }
                    }

                    @Override
                    public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                        Log.e(TAG, "got codec exception", e);
                    }

                    @Override
                    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                        Log.i(TAG, "got output format: " + format);
                        verifyHdrStaticInfo("Output format has wrong static info",
                                format, mHdrStaticInfo);
                    }
                });
                decoder.configure(format, surface, null/*crypto*/, 0/*flags*/);
                decoder.start();
                try {
                    assertTrue(latch.await(2000, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    fail("playback interrupted");
                }
                decoder.stop();
                decoder.release();
            }
        } finally {
            if (extractor != null) {
                extractor.release();
            }
        }
    }

    private void verifyHdrStaticInfo(String reason, MediaFormat format, String pattern) {
        ByteBuffer staticMetadataBuffer = format.containsKey("hdr-static-info") ?
                format.getByteBuffer("hdr-static-info") : null;
        assertTrue(reason + ": empty",
                staticMetadataBuffer != null && staticMetadataBuffer.remaining() > 0);
        assertTrue(reason + ": mismatch",
                Arrays.equals(loadByteArrayFromString(pattern), staticMetadataBuffer.array()));
    }

    private void verifyHdrDynamicInfo(String reason, MediaFormat format, String pattern) {
        ByteBuffer hdr10PlusInfoBuffer = format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO) ?
                format.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO) : null;
        assertTrue(reason + ":empty",
                hdr10PlusInfoBuffer != null && hdr10PlusInfoBuffer.remaining() > 0);
        assertTrue(reason + ": mismatch",
                Arrays.equals(loadByteArrayFromString(pattern), hdr10PlusInfoBuffer.array()));
    }

    // helper to load byte[] from a String
    private byte[] loadByteArrayFromString(final String str) {
        Pattern pattern = Pattern.compile("[0-9a-fA-F]{2}");
        Matcher matcher = pattern.matcher(str);
        // allocate a large enough byte array first
        byte[] tempArray = new byte[str.length() / 2];
        int i = 0;
        while (matcher.find()) {
          tempArray[i++] = (byte)Integer.parseInt(matcher.group(), 16);
        }
        return Arrays.copyOfRange(tempArray, 0, i);
    }

    private static boolean DEBUG_HDR_TO_SDR_PLAY_VIDEO = false;
    private static final String INVALID_HDR_STATIC_INFO =
            "00 00 00 00 00 00 00 00  00 00 00 00 00 00 00 00" +
            "00 00 00 00 00 00 00 00  00                     " ;

    @Test
    @ApiTest(apis = {"android.media.MediaFormat#KEY_COLOR_TRANSFER_REQUEST"})
    public void testHdrToSdr() throws Exception {
        AssetFileDescriptor infd = null;
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        HandlerThread handlerThread = new HandlerThread("MediaCodec callback thread");
        handlerThread.start();
        final boolean dynamic = mHdrDynamicInfo != null;

        Preconditions.assertTestFileExists(MEDIA_DIR + mInputFile);
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(MEDIA_DIR + mInputFile);

            MediaFormat format = null;
            int trackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    trackIndex = i;
                    break;
                }
            }

            extractor.selectTrack(trackIndex);
            Log.v(TAG, "format " + format);

            String mime = format.getString(MediaFormat.KEY_MIME);
            // setting profile and level
            if (MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mime)) {
                if (!dynamic) {
                    assertEquals("Extractor set wrong profile",
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                        format.getInteger(MediaFormat.KEY_PROFILE));
                } else {
                    // Extractor currently doesn't detect HDR10+, set to HDR10+ manually
                    format.setInteger(MediaFormat.KEY_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus);
                }
            } else if (MediaFormat.MIMETYPE_VIDEO_VP9.equals(mime)) {
                // The muxer might not have put VP9 CSD in the mkv, we manually patch
                // it here so that we only test HDR when decoder supports it.
                format.setInteger(MediaFormat.KEY_PROFILE,
                        dynamic ? MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus
                                : MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR);
            } else if (MediaFormat.MIMETYPE_VIDEO_AV1.equals(mime)) {
                // The muxer might not have put AV1 CSD in the webm, we manually patch
                // it here so that we only test HDR when decoder supports it.
                format.setInteger(MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10);
            } else {
                fail("Codec " + mime + " shouldn't be tested with this test!");
            }
            format.setInteger(
                    MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
            String[] decoderNames = MediaUtils.getDecoderNames(format);

            if (decoderNames == null || decoderNames.length == 0) {
                MediaUtils.skipTest("No video codecs supports HDR");
                return;
            }

            final Surface surface = getActivity().getSurfaceHolder().getSurface();
            final MediaExtractor finalExtractor = extractor;

            for (String name : decoderNames) {
                Log.d(TAG, "Testing candicate decoder " + name);
                CountDownLatch latch = new CountDownLatch(1);
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

                decoder = MediaCodec.createByCodecName(name);
                decoder.setCallback(new MediaCodec.Callback() {
                    boolean mInputEOS;
                    boolean mOutputReceived;
                    int mInputCount;
                    int mOutputCount;

                    @Override
                    public void onOutputBufferAvailable(
                            MediaCodec codec, int index, BufferInfo info) {
                        if (mOutputReceived && !DEBUG_HDR_TO_SDR_PLAY_VIDEO) {
                            return;
                        }

                        MediaFormat bufferFormat = codec.getOutputFormat(index);
                        Log.i(TAG, "got output buffer: format " + bufferFormat);

                        assertEquals("unexpected color transfer for the buffer",
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                                bufferFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER, 0));
                        ByteBuffer staticInfo = bufferFormat.getByteBuffer(
                                MediaFormat.KEY_HDR_STATIC_INFO, null);
                        if (staticInfo != null) {
                            assertTrue(
                                    "Buffer should not have a valid static HDR metadata present",
                                    Arrays.equals(loadByteArrayFromString(INVALID_HDR_STATIC_INFO),
                                                  staticInfo.array()));
                        }
                        ByteBuffer hdr10PlusInfo = bufferFormat.getByteBuffer(
                                MediaFormat.KEY_HDR10_PLUS_INFO, null);
                        if (hdr10PlusInfo != null) {
                            assertEquals(
                                    "Buffer should not have a valid dynamic HDR metadata present",
                                    0, hdr10PlusInfo.remaining());
                        }

                        if (!dynamic) {
                            codec.releaseOutputBuffer(index,  true);

                            mOutputReceived = true;
                            latch.countDown();
                        } else {
                            codec.releaseOutputBuffer(index,  true);

                            mOutputCount++;
                            if (mOutputCount >= mHdrDynamicInfo.length) {
                                mOutputReceived = true;
                                latch.countDown();
                            }
                        }
                    }

                    @Override
                    public void onInputBufferAvailable(MediaCodec codec, int index) {
                        // keep queuing until input EOS, or first output buffer received.
                        if (mInputEOS || (mOutputReceived && !DEBUG_HDR_TO_SDR_PLAY_VIDEO)) {
                            return;
                        }

                        ByteBuffer inputBuffer = codec.getInputBuffer(index);

                        if (finalExtractor.getSampleTrackIndex() == -1) {
                            codec.queueInputBuffer(
                                    index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            mInputEOS = true;
                        } else {
                            int size = finalExtractor.readSampleData(inputBuffer, 0);
                            long timestamp = finalExtractor.getSampleTime();
                            finalExtractor.advance();

                            if (dynamic && mMetaDataInContainer) {
                                final Bundle params = new Bundle();
                                // TODO: extractor currently doesn't extract the dynamic metadata.
                                // Send in the test pattern for now to test the metadata propagation.
                                byte[] info = loadByteArrayFromString(mHdrDynamicInfo[mInputCount]);
                                params.putByteArray(MediaFormat.KEY_HDR10_PLUS_INFO, info);
                                codec.setParameters(params);
                                mInputCount++;
                                if (mInputCount >= mHdrDynamicInfo.length) {
                                    mInputEOS = true;
                                }
                            }
                            codec.queueInputBuffer(index, 0, size, timestamp, 0);
                        }
                    }

                    @Override
                    public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                        Log.e(TAG, "got codec exception", e);
                    }

                    @Override
                    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                        Log.i(TAG, "got output format: " + format);
                        ByteBuffer staticInfo = format.getByteBuffer(
                                MediaFormat.KEY_HDR_STATIC_INFO, null);
                        if (staticInfo != null) {
                            assertTrue(
                                    "output format should not have a valid " +
                                    "static HDR metadata present",
                                    Arrays.equals(loadByteArrayFromString(INVALID_HDR_STATIC_INFO),
                                                  staticInfo.array()));
                        }
                    }
                }, new Handler(handlerThread.getLooper()));
                decoder.configure(format, surface, null/*crypto*/, 0/*flags*/);
                int transferRequest = decoder.getInputFormat().getInteger(
                        MediaFormat.KEY_COLOR_TRANSFER_REQUEST, 0);
                if (transferRequest == 0) {
                    Log.i(TAG, name + " does not support HDR to SDR tone mapping");
                    decoder.release();
                    continue;
                }
                assertEquals("unexpected color transfer request value from input format",
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO, transferRequest);
                decoder.start();
                try {
                    assertTrue(latch.await(2000, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    fail("playback interrupted");
                }
                if (DEBUG_HDR_TO_SDR_PLAY_VIDEO) {
                    Thread.sleep(5000);
                }
                decoder.stop();
                decoder.release();
            }
        } finally {
            if (decoder != null) {
                decoder.release();
            }
            if (extractor != null) {
                extractor.release();
            }
            handlerThread.getLooper().quit();
            handlerThread.join();
        }
    }
}
