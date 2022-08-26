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

import android.media.MediaFormat;
import android.os.Build;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * HDR10 Metadata is an aid for a display device to show the content in an optimal manner. It
 * contains the HDR content and mastering device properties that are used by the display device
 * to map the content according to its own color gamut and peak brightness. This information is
 * part of the elementary stream. Generally this information is placed at scene change intervals
 * or even at every frame level. If the encoder is configured with hdr info, then it is
 * expected to place this information in the elementary stream as-is. This test validates the
 * same. The test feeds per-frame or per-scene info at various points and expects the encoder
 * to place the hdr info in the elementary stream at exactly those points
 *
 * Restrict hdr info test for Android T and above
 */
@RunWith(Parameterized.class)
// P010 support was added in Android T, hence limit the following tests to Android T and above
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
public class EncoderHDRInfoTest extends HDREncoderTestBase {
    private static final String LOG_TAG = EncoderHDRInfoTest.class.getSimpleName();

    private String mHDRStaticInfo;
    private Map<Integer, String> mHDRDynamicInfo;

    public EncoderHDRInfoTest(String encoderName, String mediaType, int bitrate,
                              int width, int height, String HDRStaticInfo,
                              Map<Integer, String> HDRDynamicInfo) {
        super(encoderName, mediaType, bitrate, width, height);
        mHDRStaticInfo = HDRStaticInfo;
        mHDRDynamicInfo = HDRDynamicInfo;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final String[] mediaTypes = new String[]{
                MediaFormat.MIMETYPE_VIDEO_AV1,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP9
        };

        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        for (String mediaType : mediaTypes) {
            // mediaType, bitrate, width, height, hdrStaticInfo, hdrDynamicInfo
            exhaustiveArgsList.add(new Object[]{mediaType, 512000, 352, 288, HDR_STATIC_INFO,
                    null});
            exhaustiveArgsList.add(new Object[]{mediaType, 512000, 352, 288, null,
                    HDR_DYNAMIC_INFO});
        }

        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.12/C-6-4"})
    public void testHDRInfo() throws IOException, InterruptedException {
        validateHDRInfo(mHDRStaticInfo, mHDRDynamicInfo);
    }
}
