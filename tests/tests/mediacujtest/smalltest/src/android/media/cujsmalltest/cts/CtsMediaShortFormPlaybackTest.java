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

package android.media.cujsmalltest.cts;

import android.media.cujcommon.cts.CujTestBase;
import android.platform.test.annotations.LargeTest;
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
public class CtsMediaShortFormPlaybackTest extends CujTestBase {

  private static final String MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerJoyrides_720p_hevc_15s";
  private static final String MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerMeltdowns_720p_hevc_15s";
  private static final String MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerBlazes_720p_hevc_15s";
  private static final String MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerEscapes_720p_hevc_15s";

  private final List<String> mMediaUrls;
  private final long mTimeoutMilliSeconds;

  public CtsMediaShortFormPlaybackTest(List<String> mediaUrls, long timeoutMilliSeconds,
      String testType) {
    super();
    this.mMediaUrls = mediaUrls;
    this.mTimeoutMilliSeconds = timeoutMilliSeconds;
  }

  @Parameterized.Parameters(name = "{index}_{2}")
  public static Collection<Object[]> input() {
    // mediaUrl, timeoutMilliSeconds, testId
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
        {prepareHevc_720p_15secVideoList(), 330000, "Hevc_720p_15sec"},
    }));
    return exhaustiveArgsList;
  }

  public static List<String> prepareHevc_720p_15secVideoList() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING);
    return videoInput;
  }

  // Test to Verify video playback time
  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecInfo#getName",
      "android.media.MediaCodecInfo#getSupportedTypes",
      "android.media.MediaCodecInfo#isSoftwareOnly"})
  @Test
  public void testVideoPlayback() throws Exception {
    play(mMediaUrls, mTimeoutMilliSeconds);
  }
}
