/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.mediastress.cts;

import android.media.MediaCodecList;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;

public class HEVCR1080pAacLongPlayerTest extends MediaPlayerStressTest {
    private static final String VIDEO_PATH_MIDDLE = "bbb_full/1920x1080/mp4_libx265_libfaac/";
    private final String[] mMedias = {
        "bbb_full.ffmpeg.1920x1080.mp4.libx265_6500kbps_30fps.libfaac_stereo_128kbps_48000Hz.mp4"
    };

    public void testPlay00() throws Exception {
        if (!isSupported()) {
            return;
        }
        doTestVideoPlaybackLong(0);
    }

    private boolean isSupported() {
        final int CONTEXT_BIT_RATE = 6500000;
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if(info.getName().toLowerCase().equalsIgnoreCase("OMX.google.hevc.decoder")) {
                try {
                    return info.getCapabilitiesForType("video/hevc").getVideoCapabilities().
                       getBitrateRange().contains(CONTEXT_BIT_RATE);
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
        }

        return true;
    }

    @Override
    protected String getFullVideoClipName(int mediaNumber) {
        return VIDEO_TOP_DIR + VIDEO_PATH_MIDDLE + mMedias[mediaNumber];
    }

}
