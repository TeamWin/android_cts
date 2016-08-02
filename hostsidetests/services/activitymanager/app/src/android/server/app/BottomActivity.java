/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.server.app;

import android.os.Bundle;
import android.util.Log;

public class BottomActivity extends AbstractLifecycleLogActivity {

    private static final String TAG = BottomActivity.class.getSimpleName();

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final boolean useWallpaper = getIntent().getBooleanExtra("USE_WALLPAPER", false);
        if (useWallpaper) {
            setTheme(R.style.WallpaperTheme);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // TODO: delayed stopping is for simulating a case where resume happens before
        // activityStopped() is received by AM, and the transition starts without going
        // through fully stopped state (see b/30255354). This sample is however not
        // failing exactly as in b/30255354. This needs to be resolved first, otherwise
        // there is no point enabling the slow stop tests (as they always pass).
        final int stopDelay = getIntent().getIntExtra("STOP_DELAY", 0);
        if (stopDelay > 0) {
            try {
                Thread.sleep(stopDelay);
            } catch(InterruptedException e) {}
        }
    }
}
