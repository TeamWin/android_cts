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
import android.media.metrics.PlaybackSession;
import android.media.metrics.PlaybackStateEvent;

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
}
