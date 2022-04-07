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

import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Display;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static android.media.MediaCodecInfo.CodecCapabilities.*;
import static android.media.MediaCodecInfo.CodecProfileLevel.*;
import static android.mediav2.cts.CodecTestBase.*;
import static android.view.Display.HdrCapabilities.*;
import static org.junit.Assert.*;

@SmallTest
@RunWith(Parameterized.class)
public class CodecInfoTest {
    private static final String LOG_TAG = CodecInfoTest.class.getSimpleName();
    private static final int[] DISPLAY_HDR_TYPES;

    public String mMediaType;
    public String mCodecName;
    public MediaCodecInfo mCodecInfo;

    static {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        DISPLAY_HDR_TYPES =
                displayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                        .getSupportedHdrTypes();
    }

    public CodecInfoTest(String mediaType, String codecName, MediaCodecInfo codecInfo) {
        mMediaType = mediaType;
        mCodecName = codecName;
        mCodecInfo = codecInfo;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final List<Object[]> argsList = new ArrayList<>();
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) {
                continue;
            }
            if (CodecTestBase.codecPrefix != null &&
                    !codecInfo.getName().startsWith(CodecTestBase.codecPrefix)) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                argsList.add(new Object[]{type, codecInfo.getName(), codecInfo});
            }
        }
        return argsList;
    }

    /**
     * Tests if the devices on T or later, if decoder for a mediaType supports HDR profiles then
     * it should be capable of displaying the same
     */
    @Test
    @Ignore("TODO(b/228237404) Enable once display capabilities can be queried at codec2 level")
    public void testHDRDisplayCapabilities() {
        Assume.assumeTrue("Test needs Android 13", IS_AT_LEAST_T);
        Assume.assumeTrue("Test is applicable for video codecs", mMediaType.startsWith("video/"));
        Assume.assumeTrue("Test is applicable for codecs with HDR profiles",
                mProfileHdrMap.containsKey(mMediaType));

        int[] HdrProfiles = mProfileHdrMap.get(mMediaType);
        MediaCodecInfo.CodecCapabilities caps = mCodecInfo.getCapabilitiesForType(mMediaType);

        for (CodecProfileLevel pl : caps.profileLevels) {
            if (IntStream.of(HdrProfiles).anyMatch(x -> x == pl.profile)) {
                if (pl.profile == AV1ProfileMain10 || pl.profile == AVCProfileHigh10 ||
                        pl.profile == HEVCProfileMain10 || pl.profile == VP9Profile2) {
                    assertTrue("Advertises support for HLG technology without HLG display",
                            IntStream.of(DISPLAY_HDR_TYPES).anyMatch(x -> x == HDR_TYPE_HLG));
                } else if (pl.profile == AV1ProfileMain10HDR10 ||
                        pl.profile == HEVCProfileMain10HDR10 || pl.profile == VP9Profile2HDR) {
                    assertTrue(mCodecInfo.getName() + " Advertises support for HDR10 profile " +
                                    pl.profile + " without HDR10 display",
                            IntStream.of(DISPLAY_HDR_TYPES).anyMatch(x -> x == HDR_TYPE_HDR10));
                } else if (pl.profile == AV1ProfileMain10HDR10Plus ||
                        pl.profile == HEVCProfileMain10HDR10Plus ||
                        pl.profile == VP9Profile2HDR10Plus) {
                    assertTrue(mCodecInfo.getName() + " Advertises support for HDR10+ profile " +
                                    pl.profile + " without HDR10+ display",
                            IntStream.of(DISPLAY_HDR_TYPES)
                                    .anyMatch(x -> x == HDR_TYPE_HDR10_PLUS));
                } else {
                    fail("Unhandled HDR profile" + pl.profile + " for type " + mMediaType);
                }
            }
        }
    }

    /**
     * Tests if the device under test has support for necessary color formats.
     * The test only checks if the decoder/encoder is advertising the required color format. It
     * doesn't validate its support.
     */
    @Test
    public void testColorFormatSupport() {
        Assume.assumeTrue("Test is applicable for video codecs", mMediaType.startsWith("video/"));
        MediaCodecInfo.CodecCapabilities caps = mCodecInfo.getCapabilitiesForType(mMediaType);
        assertFalse(mCodecInfo.getName() + " does not support COLOR_FormatYUV420Flexible",
                IntStream.of(caps.colorFormats)
                        .noneMatch(x -> x == COLOR_FormatYUV420Flexible));

        // COLOR_FormatSurface support is an existing requirement, but we did not
        // test for it before T.  We can not retroactively apply the higher standard to
        // devices that are already certified, so only test on T or later devices.
        if (IS_AT_LEAST_T) {
            assertFalse(mCodecInfo.getName() + " does not support COLOR_FormatSurface",
                    IntStream.of(caps.colorFormats)
                            .noneMatch(x -> x == COLOR_FormatSurface));
        }

        // For devices launching with Android T, if a codec supports an HDR profile, it must
        // advertise P010 support
        int[] HdrProfileArray = mProfileHdrMap.get(mMediaType);
        if (FIRST_SDK_IS_AT_LEAST_T && HdrProfileArray != null) {
            for (CodecProfileLevel pl : caps.profileLevels) {
                if (IntStream.of(HdrProfileArray).anyMatch(x -> x == pl.profile)) {
                    assertFalse(mCodecInfo.getName() + " supports HDR profile " + pl.profile + "," +
                                    " but does not support COLOR_FormatYUVP010",
                            IntStream.of(caps.colorFormats)
                                    .noneMatch(x -> x == COLOR_FormatYUVP010));
                }
            }
        }
    }

    /**
     * Tests if a device supports encoding for a given mediaType, then it must support decoding it
     */
    @Test
    public void testDecoderAvailability() {
        Assume.assumeTrue("Test is applicable only for encoders", mCodecInfo.isEncoder());
        Assume.assumeTrue("Test is applicable for video/audio codecs",
                mMediaType.startsWith("video/") || mMediaType.startsWith("audio/"));
        if (selectCodecs(mMediaType, null, null, true).size() > 0) {
            assertTrue("Device advertises support for encoding " + mMediaType +
                            ", but not decoding it",
                    selectCodecs(mMediaType, null, null, false).size() > 0);
        }
    }
}

