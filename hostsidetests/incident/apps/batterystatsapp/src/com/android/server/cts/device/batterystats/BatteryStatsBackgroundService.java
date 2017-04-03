/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts.device.batterystats;

import static com.android.server.cts.device.batterystats.BatteryStatsBgVsFgActions.KEY_ACTION;
import static com.android.server.cts.device.batterystats.BatteryStatsBgVsFgActions.doAction;
import static com.android.server.cts.device.batterystats.BatteryStatsBgVsFgActions.isAppInBackground;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import org.junit.Assert;

/** An service (to be run as a background process) which performs one of a number of actions. */
public class BatteryStatsBackgroundService extends IntentService {
    private static final String TAG = BatteryStatsBackgroundService.class.getSimpleName();

    public BatteryStatsBackgroundService() {
        super(BatteryStatsBackgroundService.class.getName());
    }

    @Override
    public void onHandleIntent(Intent intent) {
        try {
            if (!isAppInBackground(this)) {
                Log.e(TAG, "BackgroundService is not a background process!");
                Assert.fail("Test requires BackgroundService to be in background.");
            }
        } catch(ReflectiveOperationException ex) {
            Log.w(TAG, "Couldn't determine if app is in background. Proceeding with test anyway.");
        }

        doAction(this, intent.getStringExtra(KEY_ACTION));
    }
}
