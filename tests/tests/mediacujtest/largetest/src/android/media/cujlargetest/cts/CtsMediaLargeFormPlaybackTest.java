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
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.PlatinumTest;
import com.android.compatibility.common.util.ApiTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@LargeTest
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

  public CtsMediaLargeFormPlaybackTest(List<String> mediaUrls, long timeoutMilliSeconds,
      String testType) {
    super();
    this.mMediaUrls = mediaUrls;
    this.mTimeoutMilliSeconds = timeoutMilliSeconds;
  }

  @Parameterized.Parameters(name = "{index}_{2}")
  public static Collection<Object[]> input() {
    // mediaUrl, timeoutMilliSeconds, testId
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
        {prepareVP9_640x480_5minVideoList(), 930000, "VP9_640x480_5min"},
        {prepareAvc_1080p_30minVideoList(), 1830000, "Avc_1080p_30min"},
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

  // Test to Verify video playback time
  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecInfo#getName",
      "android.media.MediaCodecInfo#getSupportedTypes",
      "android.media.MediaCodecInfo#isSoftwareOnly"})
  @PlatinumTest(focusArea = "media")
  @Test
  public void testVideoPlayback() throws Exception {
    play(mMediaUrls, mTimeoutMilliSeconds);
  }
}
