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
import android.media.cujcommon.cts.CujTestParam;
import android.media.cujcommon.cts.PlayerListener;
import android.platform.test.annotations.PlatinumTest;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
  private static final String MP4_FORBIGGERJOYRIDES_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerJoyrides_480p_hevc_5s";
  private static final String MP4_FORBIGGERMELTDOWN_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerMeltdowns_480p_hevc_5s";
  private static final String MP4_FORBIGGERBLAZEA_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerBlazes_480p_hevc_5s";
  private static final String MP4_FORBIGGERESCAPES_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerEscapes_480p_hevc_5s";
  private static final String MP4_FORBIGGERMELTDOWN_ASSET_1080P_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerMeltdowns_1080p_avc_5s";
  private static final String MP4_ELEPHANTSDREAM_ASSET_1080P_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ElephantsDream_1080p_avc_5s";
  private static final String MP4_BIGBUCKBUNNY_ASSET_1080P_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/BigBuckBunny_1080p_avc_5s";
  private static final String MP4_ELEPHANTSDREAM_ASSET_360x640_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ElephantsDream_360x640_avc_5s";
  private static final String MP4_BIGBUCKBUNNY_ASSET_360x640_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/BigBuckBunny_360x640_avc_5s";

  CujTestParam mCujTestParam;
  private final String mTestType;

  public CtsMediaShortFormPlaybackTest(CujTestParam cujTestParam, String testType) {
    super(cujTestParam.playerListener());
    mCujTestParam = cujTestParam;
    this.mTestType = testType;
  }

  /**
   * Returns the list of parameters
   */
  @Parameterized.Parameters(name = "{index}_{1}")
  public static Collection<Object[]> input() {
    // CujTestParam, testId
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15secVideoList())
            .setTimeoutMilliSeconds(330000)
            .setPlayerListener(PlayerListener.createListenerForPlaybackTest()).build(),
            "Hevc_720p_15sec"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15secVideoList())
            .setTimeoutMilliSeconds(330000)
            .setPlayerListener(PlayerListener.createListenerForSeekTest(1, 5000, 9000)).build(),
            "Hevc_720p_15sec_seekTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_480p_5secVideoList())
            .setTimeoutMilliSeconds(60000)
            .setPlayerListener(PlayerListener.createListenerForOrientationTest(3000)).build(),
            "Hevc_480p_5sec_OrientationTest"},
        {CujTestParam.builder().setMediaUrls(prepareAvc_1080p_5secVideoList())
            .setTimeoutMilliSeconds(60000)
            .setPlayerListener(PlayerListener.createListenerForOrientationTest(3000)).build(),
            "Avc_1080p_5sec_OrientationTest"},
        {CujTestParam.builder().setMediaUrls(prepareAvc_360x640_5secVideoList())
            .setTimeoutMilliSeconds(60000)
            .setPlayerListener(PlayerListener.createListenerForOrientationTest(3000)).build(),
            "Avc_360x640_5sec_OrientationTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_480p_15secVideoListForScrollTest())
            .setTimeoutMilliSeconds(90000)
            .setPlayerListener(PlayerListener.createListenerForScrollTest(2, 5000)).build(),
            "Avc_360x640_15sec_ScrollTest"},
    }));
    return exhaustiveArgsList;
  }

  /**
   * Prepare Hevc 720p 15sec video list.
   */
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

  /**
   * Prepare Hevc 480p 15sec video list.
   */
  public static List<String> prepareHevc_480p_5secVideoList() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERJOYRIDES_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_480P_HEVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Avc 1080p 15sec video list.
   */
  public static List<String> prepareAvc_1080p_5secVideoList() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERMELTDOWN_ASSET_1080P_AVC_URI_STRING,
        MP4_ELEPHANTSDREAM_ASSET_1080P_AVC_URI_STRING,
        MP4_BIGBUCKBUNNY_ASSET_1080P_AVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Avc 360x480 video list.
   */
  public static List<String> prepareAvc_360x640_5secVideoList() {
    List<String> videoInput = Arrays.asList(
        MP4_BIGBUCKBUNNY_ASSET_360x640_AVC_URI_STRING,
        MP4_ELEPHANTSDREAM_ASSET_360x640_AVC_URI_STRING,
        MP4_BIGBUCKBUNNY_ASSET_360x640_AVC_URI_STRING,
        MP4_ELEPHANTSDREAM_ASSET_360x640_AVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Hevc 480p video list for Scroll Test.
   */
  public static List<String> prepareHevc_480p_15secVideoListForScrollTest() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERBLAZEA_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_480P_HEVC_URI_STRING);
    return videoInput;
  }

  // Test to Verify video playback with and without seek
  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecInfo#getName",
      "android.media.MediaCodecInfo#getSupportedTypes",
      "android.media.MediaCodecInfo#isSoftwareOnly"})
  @Test
  @PlatinumTest(focusArea = "media")
  public void testVideoPlayback() throws Exception {
    if (mCujTestParam.playerListener().isOrientationTest()) {
      Assume.assumeTrue("Skipping " + mTestType + " as device doesn't support orientation.",
          supportOrientationRequest(mActivity));
    }
    play(mCujTestParam.mediaUrls(), mCujTestParam.timeoutMilliSeconds());
  }
}
