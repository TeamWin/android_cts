/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.media.tv.cts;

import android.media.tv.TvTrackInfo;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.os.Parcelables;

import static android.media.tv.cts.TvTrackInfoSubject.assertThat;

import org.junit.Test;

/**
 * Test {@link android.media.tv.TvTrackInfo}.
 */
public class TvTrackInfoTest {

    @Test
    public void testAudioTrackInfoOp() {
        if (!Utils.hasTvInputFramework(ApplicationProvider.getApplicationContext())) {
            return;
        }
        final Bundle bundle = new Bundle();
        final TvTrackInfo info = new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, "id_audio")
                .setAudioChannelCount(2)
                .setAudioSampleRate(48000)
                .setEncoding("test_encoding")
                .setLanguage("eng")
                .setExtra(bundle)
                .build();
        assertThat(info).hasType(TvTrackInfo.TYPE_AUDIO);
        assertThat(info).hasId("id_audio");
        assertThat(info).hasAudioChannelCount(2);
        assertThat(info).hasAudioSampleRate(48000);
        assertThat(info).hasEncoding("test_encoding");
        assertThat(info).hasLanguage("eng");
        assertThat(info).extra().isEmpty();
        assertThat(info).hasContentDescription(0);
        assertThat(info).recreatesEqual(TvTrackInfo.CREATOR);
        TvTrackInfo copy = Parcelables.forceParcel(info, TvTrackInfo.CREATOR);
        assertThat(copy).extra().isEmpty();
    }

    @Test
    public void testVideoTrackInfoOp() {
        if (!Utils.hasTvInputFramework(ApplicationProvider.getApplicationContext())) {
            return;
        }
        final Bundle bundle = new Bundle();
        bundle.putBoolean("testTrue", true);
        final TvTrackInfo info = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, "id_video")
                .setEncoding("test_encoding")
                .setVideoWidth(1920)
                .setVideoHeight(1080)
                .setVideoFrameRate(29.97f)
                .setVideoPixelAspectRatio(1.0f)
                .setVideoActiveFormatDescription((byte) 8)
                .setLanguage("eng")
                .setExtra(bundle)
                .build();
        assertThat(info).hasType(TvTrackInfo.TYPE_VIDEO);
        assertThat(info).hasId("id_video");
        assertThat(info).hasEncoding("test_encoding");
        assertThat(info).hasVideoWidth(1920);
        assertThat(info).hasVideoHeight(1080);
        assertThat(info).hasVideoFrameRate(29.97f);
        assertThat(info).hasVideoPixelAspectRatio(1.0f);
        assertThat(info).hasVideoActiveFormatDescription((byte) 8);
        assertThat(info).hasLanguage("eng");
        assertThat(info).extra().bool("testTrue").isTrue();
        assertThat(info).hasContentDescription(0);
        assertThat(info).recreatesEqual(TvTrackInfo.CREATOR);
        TvTrackInfo copy = Parcelables.forceParcel(info, TvTrackInfo.CREATOR);
        assertThat(copy).extra().bool("testTrue").isTrue();
    }

    @Test
    public void testSubtitleTrackInfoOp() {
        if (!Utils.hasTvInputFramework(ApplicationProvider.getApplicationContext())) {
            return;
        }
        final Bundle bundle = new Bundle();
        bundle.putBoolean("testTrue", true);
        final TvTrackInfo info = new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "id_subtitle")
                .setLanguage("eng")
                .setEncoding("test_encoding")
                .setExtra(bundle)
                .build();
        assertThat(info).hasType(TvTrackInfo.TYPE_SUBTITLE);
        assertThat(info).hasId("id_subtitle");
        assertThat(info).hasEncoding("test_encoding");
        assertThat(info).hasLanguage("eng");
        assertThat(info).extra().bool("testTrue").isTrue();
        assertThat(info).hasContentDescription(0);
        assertThat(info).recreatesEqual(TvTrackInfo.CREATOR);
        TvTrackInfo copy = Parcelables.forceParcel(info, TvTrackInfo.CREATOR);
        assertThat(copy).extra().bool("testTrue").isTrue();
    }
}
