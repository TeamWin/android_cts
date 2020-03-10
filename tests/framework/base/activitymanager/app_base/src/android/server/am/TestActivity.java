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

package android.server.am;

import static android.server.am.Components.TestActivity.EXTRA_FIXED_ORIENTATION;
import static android.server.am.Components.TestActivity.EXTRA_INTENTS;
import static android.server.am.Components.TestActivity.TEST_ACTIVITY_ACTION_FINISH_SELF;
import static android.server.am.Components.TestActivity.TEST_ACTIVITY_ACTION_START_ACTIVITIES;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcelable;

import java.util.Arrays;

public class TestActivity extends AbstractLifecycleLogActivity {

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TEST_ACTIVITY_ACTION_FINISH_SELF.equals(action)) {
                finish();
            } else if (TEST_ACTIVITY_ACTION_START_ACTIVITIES.equals(action)) {
                final Parcelable[] intents = intent.getParcelableArrayExtra(EXTRA_INTENTS);
                startActivities(Arrays.copyOf(intents, intents.length, Intent[].class));
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set the fixed orientation if requested
        if (getIntent().hasExtra(EXTRA_FIXED_ORIENTATION)) {
            final int ori = Integer.parseInt(getIntent().getStringExtra(EXTRA_FIXED_ORIENTATION));
            setRequestedOrientation(ori);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TEST_ACTIVITY_ACTION_FINISH_SELF);
        intentFilter.addAction(TEST_ACTIVITY_ACTION_START_ACTIVITIES);
        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Configuration config = getResources().getConfiguration();
        dumpDisplaySize(config);
        dumpConfiguration(config);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dumpDisplaySize(newConfig);
        dumpConfiguration(newConfig);
    }
}
