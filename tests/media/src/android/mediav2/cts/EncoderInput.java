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

package android.mediav2.cts;

import android.graphics.ImageFormat;
import android.media.AudioFormat;
import android.mediav2.common.cts.RawResource;

/**
 * Class containing encoder input resources.
 */
public class EncoderInput {
    private static final String mInpPrefix = WorkDir.getMediaDirString();

    // files are in WorkDir.getMediaDirString();
    private static final RawResource INPUT_VIDEO_FILE =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "bbb_cif_yuv420p_30fps.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(1)
                    .setColorFormat(ImageFormat.YUV_420_888)
                    .build();
    private static final RawResource INPUT_VIDEO_FILE_HBD =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "cosmat_cif_24fps_yuv420p16le.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(2)
                    .setColorFormat(ImageFormat.YCBCR_P010)
                    .build();

    /* Note: The mSampleRate and mChannelCount fields of RawResource are not used by the tests
    the way mWidth and mHeight are used. mWidth and mHeight is used by fillImage() to select a
    portion of the frame or duplicate the frame as tiles depending on testWidth and testHeight.
    Ideally mSampleRate and mChannelCount information should be used to resample and perform
    channel-conversion basing on testSampleRate and testChannelCount. Instead the test considers
    the resource file to be of testSampleRate and testChannelCount. */
    private static final RawResource INPUT_AUDIO_FILE =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "bbb_2ch_44kHz_s16le.raw", true)
                    .setSampleRate(44100)
                    .setChannelCount(2)
                    .setBytesPerSample(2)
                    .setAudioEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
    private static final RawResource INPUT_AUDIO_FILE_HBD =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "audio/sd_2ch_48kHz_f32le.raw", true)
                    .setSampleRate(48000)
                    .setChannelCount(2)
                    .setBytesPerSample(4)
                    .setAudioEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build();

    public static RawResource getRawResource(String mediaType, boolean isHighBitDepth) {
        if (mediaType.startsWith("audio/")) {
            return isHighBitDepth ? INPUT_AUDIO_FILE_HBD : INPUT_AUDIO_FILE;
        } else {
            return isHighBitDepth ? INPUT_VIDEO_FILE_HBD : INPUT_VIDEO_FILE;
        }
    }
}
