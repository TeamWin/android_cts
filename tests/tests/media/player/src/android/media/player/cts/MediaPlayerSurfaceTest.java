/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.media.player.cts;

import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.test.ActivityInstrumentationTestCase2;

import androidx.test.filters.SmallTest;

/**
 */
@Presubmit
@SmallTest
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
public class MediaPlayerSurfaceTest extends ActivityInstrumentationTestCase2<MediaPlayerSurfaceStubActivity> {

    public MediaPlayerSurfaceTest() {
        super("android.media.player.cts", MediaPlayerSurfaceStubActivity.class);
    }

    public void testSetSurface() throws Exception {
        Bundle extras = new Bundle();
        MediaPlayerSurfaceStubActivity activity = launchActivity("android.media.player.cts",
                MediaPlayerSurfaceStubActivity.class, extras);
        activity.playVideo();
        activity.finish();
    }
}
