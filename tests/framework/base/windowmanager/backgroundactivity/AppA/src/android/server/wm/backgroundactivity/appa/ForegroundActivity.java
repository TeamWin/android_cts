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
 * limitations under the License
 */

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivity.LAUNCH_BACKGROUND_ACTIVITY_EXTRA;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivity.LAUNCH_SECOND_BACKGROUND_ACTIVITY_EXTRA;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivity.RELAUNCH_FOREGROUND_ACTIVITY_EXTRA;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Foreground activity that makes AppA as foreground.
 */
public class ForegroundActivity extends Activity {

    private boolean mRelaunch = false;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Intent intent = getIntent();
        mRelaunch = intent.getBooleanExtra(RELAUNCH_FOREGROUND_ACTIVITY_EXTRA, false);

        boolean launchBackground = intent.getBooleanExtra(LAUNCH_BACKGROUND_ACTIVITY_EXTRA, false);
        if (launchBackground) {
            Intent newIntent = new Intent();
            newIntent.setClass(this, BackgroundActivity.class);
            startActivity(newIntent);
        }

        boolean launchSecond = intent.getBooleanExtra(
                LAUNCH_SECOND_BACKGROUND_ACTIVITY_EXTRA, false);
        if (launchSecond) {
            Intent newIntent = new Intent();
            newIntent.setClass(this, SecondBackgroundActivity.class);
            startActivity(newIntent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mRelaunch) {
            mRelaunch = false;
            SystemClock.sleep(50);
            startActivity(getIntent());
        }
    }
}
