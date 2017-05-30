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
 * limitations under the License
 */

package android.backup.cts.backuprestoreapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import static android.backup.cts.backuprestoreapp.Constants.*;

/**
 * Test activity that verifies SharedPreference restore behavior.
 *
 * Test logic:
 *   1. This activity is launched; it creates a new SharedPreferences instance and writes
 *       a known value to the INT_PREF element's via that instance.  The instance is
 *       kept live.
 *   2. The app is backed up, storing this known value in the backup dataset.
 *   3. Next, the activity is instructed to write a different value to the INT_PREF
 *       shared preferences element.  At this point, the app's current on-disk state
 *       and the live shared preferences instance are in agreement, holding a value
 *       different from that in the backup.
 *   4. The runner triggers a restore for this app.  This will rewrite the shared prefs
 *       file itself with the backed-up content (i.e. different from what was just
 *       committed from this activity).
 *   5. Finally, the runner instructs the activity to compare the value of its existing
 *       shared prefs instance's INT_PREF element with what was previously written.
 *       The test passes if these differ, i.e. if the live shared prefs instance picked
 *       up the newly-restored data.
 */
public class SharedPrefsRestoreTestActivity extends Activity {
    static final String TAG = "SharedPrefsTest";

    SharedPreferences mPrefs;
    int mLastValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);
        mLastValue = 0;

        processLaunchCommand(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        processLaunchCommand(intent);
    }

    private void processLaunchCommand(Intent intent) {
        final String action = intent.getAction();
        Log.i(TAG, "processLaunchCommand: " + action);
        if (INIT_ACTION.equals(action)) {
            // We'll issue a backup after setting this value in shared prefs
            setPrefValue(55);
        } else if (UPDATE_ACTION.equals(action)) {
            // We'll issue a *restore* after setting this value, which will send a broadcast
            // to our receiver to read from the live instance and ensure that the value is
            // different from what was just written.
            setPrefValue(999);
        } else if (TEST_ACTION.equals(action)) {
            final int currentValue = mPrefs.getInt(INT_PREF, mLastValue);
            Log.i(TAG, "Shared prefs changed: " + (currentValue != mLastValue));
        }
    }

    // Write a known value prior to backup
    private void setPrefValue(int value) {
        mLastValue = value;
        mPrefs.edit().putInt(INT_PREF, value).commit();
    }
}
