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

package android.server.cts;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

public class TestActivity extends AbstractLifecycleLogActivity {

    private static final String TAG = TestActivity.class.getSimpleName();

    // Sets the fixed orientation (can be one of {@link ActivityInfo.ScreenOrientation}
    private static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";

    // The target activity names for ACTION_START_ACTIVITIES.
    private static final String EXTRA_NAMES = "names";

    // The target activity flags for ACTION_START_ACTIVITIES.
    private static final String EXTRA_FLAGS = "flags";

    // Finishes the activity
    private static final String ACTION_FINISH_SELF = "android.server.cts.TestActivity.finish_self";

    // Calls startActivities with the provided targets.
    private static final String ACTION_START_ACTIVITIES =
            "android.server.cts.TestActivity.start_activities";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_FINISH_SELF.equals(action)) {
                finish();
            } else if (ACTION_START_ACTIVITIES.equals(action)) {
                final int[] flags = intent.getIntArrayExtra(EXTRA_FLAGS);
                final String[] names = intent.getStringArrayExtra(EXTRA_NAMES);
                final Intent[] intents = new Intent[names.length];
                for (int i = 0; i < intents.length; i++) {
                    Log.i(TAG, "Start activities[" + i + "]=" + names[i]
                            + " fl=0x" + Integer.toHexString(flags[i]));
                    intents[i] = new Intent()
                            .setComponent(ComponentName.unflattenFromString(names[i]))
                            .addFlags(flags[i]);
                }
                startActivities(intents);
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
        intentFilter.addAction(ACTION_FINISH_SELF);
        intentFilter.addAction(ACTION_START_ACTIVITIES);
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

    @Override
    protected String getTag() {
        return TAG;
    }
}
