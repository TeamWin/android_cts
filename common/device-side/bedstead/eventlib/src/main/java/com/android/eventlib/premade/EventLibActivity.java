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

package com.android.eventlib.premade;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.android.eventlib.events.activities.ActivityCreatedEvent;

/**
 * An {@link Activity} which logs events for all lifecycle events.
 */
public class EventLibActivity extends Activity {

    private String mOverrideActivityClassName;

    public void setOverrideActivityClassName(String overrideActivityClassName) {
        mOverrideActivityClassName = overrideActivityClassName;
    }

    /** Log a {@link ActivityCreatedEvent}. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logOnCreate(savedInstanceState, /* persistentState= */ null);
    }

    /** Log a {@link ActivityCreatedEvent}. */
    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        logOnCreate(savedInstanceState, persistentState);
    }

    private void logOnCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        ActivityCreatedEvent.ActivityCreatedEventLogger logger =
                ActivityCreatedEvent.logger(this, savedInstanceState)
                        .setPersistentState(persistentState);

        if (mOverrideActivityClassName != null) {
            logger.setActivity(mOverrideActivityClassName);
        }

        logger.log();
    }

    @Override
    protected void onStart() {
        // TODO(scottjonathan): Add log
        super.onStart();
    }

    @Override
    protected void onRestart() {
        // TODO(scottjonathan): Add log
        super.onRestart();
    }

    @Override
    protected void onResume() {
        // TODO(scottjonathan): Add log
        super.onResume();
    }

    @Override
    protected void onPause() {
        // TODO(scottjonathan): Add log
        super.onPause();
    }

    @Override
    protected void onStop() {
        // TODO(scottjonathan): Add log
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // TODO(scottjonathan): Add log
        super.onDestroy();
    }
}
