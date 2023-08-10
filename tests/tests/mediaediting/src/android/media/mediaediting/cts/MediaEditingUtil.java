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

/** Utilities for Media Editing tests. */
public final class MediaEditingUtil {

  public static final String MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_URI_STRING =
    "sample_with_increasing_timestamps.mp4";

  /** Baseline profile level 3.0 H.264 stream, which should be supported on all devices. */
  public static final String MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING =
    "sample_with_increasing_timestamps_320w_240h.mp4";

  public static final String MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING =
    "bbb_642x642_1mbps_30fps_avc.mp4";

  public static final String MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_URI_STRING =
    "bbb_1920x1080_hevc_main_l40.mp4";

  /** Baseline profile level 3.0 H.265 stream, which should be supported on all devices. */
  public static final String MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING =
    "bbb_720x480_30fps_hevc_main_l3.mp4";

  public static final String MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING =
    "bbb_642x642_768kbps_30fps_hevc.mp4";
}
