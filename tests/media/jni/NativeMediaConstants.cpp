/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "NativeMediaConstants.h"

/* TODO(b/153592281)
 * Note: constants used by the native media tests but not available in media ndk api
 */
const char* AMEDIA_MIMETYPE_VIDEO_VP8 = "video/x-vnd.on2.vp8";
const char* AMEDIA_MIMETYPE_VIDEO_VP9 = "video/x-vnd.on2.vp9";
const char* AMEDIA_MIMETYPE_VIDEO_AVC = "video/avc";
const char* AMEDIA_MIMETYPE_VIDEO_HEVC = "video/hevc";
const char* AMEDIA_MIMETYPE_VIDEO_MPEG4 = "video/mp4v-es";
const char* AMEDIA_MIMETYPE_VIDEO_H263 = "video/3gpp";

const char* AMEDIA_MIMETYPE_AUDIO_AMR_NB = "audio/3gpp";
const char* AMEDIA_MIMETYPE_AUDIO_AMR_WB = "audio/amr-wb";
const char* AMEDIA_MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
const char* AMEDIA_MIMETYPE_AUDIO_VORBIS = "audio/vorbis";
const char* AMEDIA_MIMETYPE_AUDIO_OPUS = "audio/opus";

/* TODO(b/153592281) */
const char* TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME = "request-sync";
const char* TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE = "video-bitrate";
const char* TBD_AMEDIACODEC_PARAMETER_KEY_MAX_B_FRAMES = "max-bframes";
const char* TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE = "bitrate-mode";
