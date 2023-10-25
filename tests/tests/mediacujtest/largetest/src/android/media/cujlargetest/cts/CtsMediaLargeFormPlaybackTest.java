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

package android.media.cujlargetest.cts;

import android.media.cujcommon.cts.CujTestBase;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.LargeTest;

import androidx.media3.common.C;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@LargeTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class CtsMediaLargeFormPlaybackTest extends CujTestBase {

  private static final String MEDIA_DIR = WorkDir.getMediaDirString();

  private static final String WEBM_ELEPHANTDREAM_640x480_VP9_5MIN_URI_STRING =
      MEDIA_DIR + "ElephantsDream_640x480_vp9_5min.webm";
  private static final String WEBM_TEARSOFSTEEL_640X480_VP9_5MIN_URI_STRING =
      MEDIA_DIR + "TearsOfSteel_640x480_vp9_5min.webm";
  private static final String WEBM_BIGBUCKBUNNY_640X480_VP9_5MIN_URI_STRING =
      MEDIA_DIR + "BigBuckBunny_640x480_vp9_5min.webm";
  private static final String MP4_ELEPHANTSDREAM_BIGBUCKBUNNY_CONCAT_1080P_AVC_30MIN_URI_STRING =
      MEDIA_DIR + "ElephantsDream_BigBuckBunny_concat_1080p_avc_30min.mp4";

  private final List<String> mMediaUrls;
  private final long mTimeoutMilliSeconds;
  private final boolean mIsSeekTest;
  private final int mNumOfSeekIteration;
  private final long mSeekStartPosition;
  private final long mSeekTimeUs;

  public CtsMediaLargeFormPlaybackTest(List<String> mediaUrls, long timeoutMilliSeconds,
      boolean isSeekTest, int numOfSeekIteration, long seekStartPosition, long seekTimeUs,
      String testType) {
    super();
    this.mMediaUrls = mediaUrls;
    this.mTimeoutMilliSeconds = timeoutMilliSeconds;
    this.mIsSeekTest = isSeekTest;
    this.mNumOfSeekIteration = numOfSeekIteration;
    this.mSeekStartPosition = seekStartPosition;
    this.mSeekTimeUs = seekTimeUs;
  }

  /**
   * Returns the list of parameters
   */
  @Parameterized.Parameters(name = "{index}_{6}")
  public static Collection<Object[]> input() {
    // mediaUrl, timeoutMilliSeconds, isSeekTest, numOfSeekIteration, seekStartPosition, seekTimeUs
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
        {prepareVP9_640x480_5minVideoList(), 930000, false, C.LENGTH_UNSET, C.LENGTH_UNSET,
            C.LENGTH_UNSET, "VP9_640x480_5min"},
        {prepareVP9_640x480_5minVideoList(), 930000, true, 30, 30000, 10000,
            "VP9_640x480_5min_seekTest"},
        {prepareAvc_1080p_30minVideoList(), 1830000, false, C.LENGTH_UNSET, C.LENGTH_UNSET,
            C.LENGTH_UNSET, "Avc_1080p_30min"},
        {prepareAvc_1080p_30minVideoList(), 1830000, true, 30, 30000, 10000,
            "Avc_1080p_30min_seekTest"},
    }));
    return exhaustiveArgsList;
  }

  public static List<String> prepareVP9_640x480_5minVideoList() {
    List<String> videoInput = Arrays.asList(
        WEBM_ELEPHANTDREAM_640x480_VP9_5MIN_URI_STRING,
        WEBM_TEARSOFSTEEL_640X480_VP9_5MIN_URI_STRING,
        WEBM_BIGBUCKBUNNY_640X480_VP9_5MIN_URI_STRING);
    return videoInput;
  }

  public static List<String> prepareAvc_1080p_30minVideoList() {
    List<String> videoInput = Arrays.asList(
        MP4_ELEPHANTSDREAM_BIGBUCKBUNNY_CONCAT_1080P_AVC_30MIN_URI_STRING);
    return videoInput;
  }

  // Test to Verify video playback with and without seek
  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecInfo#getName",
      "android.media.MediaCodecInfo#getSupportedTypes",
      "android.media.MediaCodecInfo#isSoftwareOnly"})
  @Test
  public void testVideoPlayback() throws Exception {
    play(mMediaUrls, mTimeoutMilliSeconds, mIsSeekTest, mNumOfSeekIteration, mSeekStartPosition,
        mSeekTimeUs);
  }
}
