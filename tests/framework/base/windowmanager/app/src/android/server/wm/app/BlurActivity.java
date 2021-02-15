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
 * limitations under the License
 */

package android.server.wm.app;

import static android.server.wm.app.Components.BlurActivity.ACTION_FINISH;
import static android.server.wm.app.Components.BlurActivity.EXTRA_BACKGROUND_BLUR_RADIUS_PX;

import android.app.Activity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.server.wm.app.BroadcastReceiverActivity;


public class BlurActivity extends BroadcastReceiverActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blur_activity);
        getWindow().setBackgroundBlurRadius(
                getIntent().getIntExtra(EXTRA_BACKGROUND_BLUR_RADIUS_PX, 0));
    }
}
