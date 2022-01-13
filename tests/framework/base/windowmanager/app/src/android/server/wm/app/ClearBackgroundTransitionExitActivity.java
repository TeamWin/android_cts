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

package android.server.wm.app;

import static android.server.wm.app.Components.BackgroundActivityTransition.TRANSITION_REQUESTED;
import static android.server.wm.app.Components.CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.server.wm.TestJournalProvider;

/**
 * Activity to test that show background for activity transitions works
 */
public class ClearBackgroundTransitionExitActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.background_image);

        // Delay the starting the activity so we don't skip the transition.
        startActivityDelayed();
    }

    private void startActivityDelayed() {
        Runnable r = () -> {
            // Notify the test journal that we are starting the activity transition
            TestJournalProvider.putExtras(
                    getBaseContext(), CLEAR_BACKGROUND_TRANSITION_EXIT_ACTIVITY, bundle -> {
                        bundle.putBoolean(TRANSITION_REQUESTED,
                                true);
                    });
            startActivity(new Intent(
                    ClearBackgroundTransitionExitActivity.this,
                    ClearBackgroundTransitionEnterActivity.class
            ));
        };

        Handler h = new Handler();
        h.postDelayed(r, 1000);
    }
}
