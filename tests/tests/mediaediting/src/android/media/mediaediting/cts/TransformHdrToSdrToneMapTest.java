/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.mediaediting.cts;

import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus;
import static android.media.mediaediting.cts.FileUtil.assertFileHasColorTransfer;

import static androidx.media3.common.util.Assertions.checkNotNull;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.media.MediaFormat;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.transformer.TransformationException;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Instrumentation tests for transform HDR to SDR ToneMapping for given inputs.
 */
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public final class TransformHdrToSdrToneMapTest {
  private static final String LOG_TAG = TransformHdrToSdrToneMapTest.class.getSimpleName();
  private static final String MEDIA_DIR = WorkDir.getMediaDirString();
  private static final HashMap<String, int[]> PROFILE_HDR_MAP = new HashMap<>();
  private static final int[] HEVC_HDR_PROFILES = new int[] {HEVCProfileMain10,
      HEVCProfileMain10HDR10, HEVCProfileMain10HDR10Plus};
  private static final int[] AVC_HDR_PROFILES = new int[] {AVCProfileHigh10};
  static {
    PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_HDR_PROFILES);
    PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HDR_PROFILES);
  }

  private final String mediaType;
  private final String testFile;
  private final String testId;

  public TransformHdrToSdrToneMapTest(String mediaType, String testFile, String testId) {
    this.mediaType = mediaType;
    this.testFile = testFile;
    this.testId = testId;
  }

  @Parameterized.Parameters(name = "{index}_{2}")
  public static Collection<Object[]> input() {
    // mediaType, clip, width, height, frameRate
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][] {
        // H264
        {MimeTypes.VIDEO_H264, MediaEditingUtil.MKV_ASSET_H264_340W_280H_10BIT, "340x280_24fps"},
        {MimeTypes.VIDEO_H264, MediaEditingUtil.MKV_ASSET_H264_520W_390H_10BIT, "520x390_24fps"},
        {MimeTypes.VIDEO_H264, MediaEditingUtil.MKV_ASSET_H264_640W_360H_10BIT, "640x360_24fps"},
        {MimeTypes.VIDEO_H264, MediaEditingUtil.MKV_ASSET_H264_800W_640H_10BIT, "800x640_24fps"},
        {MimeTypes.VIDEO_H264, MediaEditingUtil.MKV_ASSET_H264_1280W_720H_10BIT, "1280x720_24fps"},
        // Hevc
        {MimeTypes.VIDEO_H265, MediaEditingUtil.MKV_ASSET_HEVC_340W_280H_5S_10BIT, "340x280_24fps"},
        {MimeTypes.VIDEO_H265, MediaEditingUtil.MKV_ASSET_HEVC_520W_390H_5S_10BIT, "520x390_24fps"},
        {MimeTypes.VIDEO_H265, MediaEditingUtil.MKV_ASSET_HEVC_640W_360H_5S_10BIT, "640x360_24fps"},
        {MimeTypes.VIDEO_H265, MediaEditingUtil.MKV_ASSET_HEVC_800W_640H_5S_10BIT, "800x640_24fps"},
        {MimeTypes.VIDEO_H265, MediaEditingUtil.MKV_ASSET_HEVC_1280W_720H_5S_10BIT,
            "1280x720_24fps"},
    }));
    return prepareParamList(exhaustiveArgsList);
  }

  public static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
    List<Object[]> argsList = new ArrayList<>();

    for (Object[] arg : exhaustiveArgsList) {
      String codec = arg[0].toString();
      // Trim the mime baseType with slash.
      int lastIndex = codec.lastIndexOf('/');
      if (lastIndex != -1) {
        codec = codec.substring(lastIndex + 1);
      }
      String testID = String.format("transformHdrToSdr_%s_%s_10bit", codec, arg[2]);
      arg[2] = testID;
      argsList.add(arg);
    }
    return argsList;
  }

  private static Transformer createTransformer(Context context) {
    return new Transformer.Builder(context)
        .setTransformationRequest(
            new TransformationRequest.Builder()
                .setEnableRequestSdrToneMapping(true)
                .build())
        .addListener(
            new Transformer.Listener() {
              @Override
              public void onFallbackApplied(
                  @NonNull MediaItem inputMediaItem,
                  @NonNull TransformationRequest originalTransformationRequest,
                  @NonNull TransformationRequest fallbackTransformationRequest) {
                // Tone mapping flag shouldn't change in fallback when tone mapping is requested.
                assertThat(originalTransformationRequest.enableRequestSdrToneMapping)
                    .isEqualTo(fallbackTransformationRequest.enableRequestSdrToneMapping);
              }
            })
        .build();
  }

  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecList#findDecoderForFormat",
      "android.media.MediaFormat#createVideoFormat",
      "android.media.MediaFormat#KEY_COLOR_TRANSFER_REQUEST"})
  @Test
  public void transformHdrToSdrTest() throws Exception {
    Preconditions.assertTestFileExists(MEDIA_DIR + testFile);
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer = createTransformer(context);
    TransformationTestResult transformationTestResult = null;
    try {
      transformationTestResult = new TransformerAndroidTestRunner.Builder(context, transformer)
          .build()
          .run(testId, MediaItem.fromUri(Uri.parse(MEDIA_DIR + testFile)));
    } catch (TransformationException exception) {
      Log.i(LOG_TAG, checkNotNull(exception.getCause()).toString());
      Assume.assumeTrue("Skipping transformHdrToSdrToneMapTest for " + testId
              + "encoding / decoding not supported",
          !(exception.errorCode == TransformationException.ERROR_CODE_HDR_ENCODING_UNSUPPORTED
              || exception.errorCode
              == TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED));
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isAnyOf(
              TransformationException.ERROR_CODE_HDR_ENCODING_UNSUPPORTED,
              TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
      return;
    }
    assertFileHasColorTransfer(transformationTestResult.filePath, C.COLOR_TRANSFER_SDR);
    int profile = MediaEditingUtil.getMuxedOutputProfile(transformationTestResult.filePath);
    int[] profileArray = PROFILE_HDR_MAP.get(mediaType);
    assertNotNull("Expected value to be not null", profileArray);
    assertFalse(testId + " must not contain HDR profile after tone mapping",
        IntStream.of(profileArray).anyMatch(x -> x == profile));
  }
}
