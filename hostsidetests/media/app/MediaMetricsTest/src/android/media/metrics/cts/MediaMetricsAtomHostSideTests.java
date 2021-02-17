/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.media.metrics.cts;

import android.content.Context;
import android.media.metrics.MediaMetricsManager;
import android.media.metrics.NetworkEvent;
import android.media.metrics.PlaybackErrorEvent;
import android.media.metrics.PlaybackSession;
import android.media.metrics.PlaybackStateEvent;
import android.media.metrics.TrackChangeEvent;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

public class MediaMetricsAtomHostSideTests {

    @Test
    public void testPlaybackStateEvent() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackStateEvent e =
                new PlaybackStateEvent.Builder()
                        .setTimeSinceCreatedMillis(1763L)
                        .setState(PlaybackStateEvent.STATE_JOINING_FOREGROUND)
                        .build();
        s.reportPlaybackStateEvent(e);
    }

    @Test
    public void testPlaybackErrorEvent() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackErrorEvent e =
                new PlaybackErrorEvent.Builder()
                        .setTimeSinceCreatedMillis(17630000L)
                        .setErrorCode(PlaybackErrorEvent.ERROR_CODE_RUNTIME)
                        .setSubErrorCode(378)
                        .setException(new Exception("test exception"))
                        .build();
        s.reportPlaybackErrorEvent(e);
    }

    @Test
    public void testTrackChangeEvent_text() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        TrackChangeEvent e =
                new TrackChangeEvent.Builder(TrackChangeEvent.TRACK_TYPE_TEXT)
                        .setTimeSinceCreatedMillis(37278L)
                        .setTrackState(TrackChangeEvent.TRACK_STATE_ON)
                        .setTrackChangeReason(TrackChangeEvent.TRACK_CHANGE_REASON_MANUAL)
                        .setContainerMimeType("text/foo")
                        .setSampleMimeType("text/plain")
                        .setCodecName("codec_1")
                        .setBitrate(1024)
                        .setLanguage("EN")
                        .setLanguageRegion("US")
                        .build();
        s.reportTrackChangeEvent(e);
    }

    @Test
    public void testNetworkEvent() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        NetworkEvent e =
                new NetworkEvent.Builder()
                        .setTimeSinceCreatedMillis(3032L)
                        .setNetworkType(NetworkEvent.NETWORK_TYPE_WIFI)
                        .build();
        s.reportNetworkEvent(e);
    }
}
