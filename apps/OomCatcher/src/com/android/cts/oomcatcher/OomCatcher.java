/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.oomcatcher;

import android.app.Activity;
import android.os.Bundle;
import android.app.ActivityManager;
import android.content.Context;
import android.content.ComponentCallbacks2;
import android.util.Log;

public class OomCatcher extends Activity implements ComponentCallbacks2 {

    private static final String LOG_TAG = "OomCatcher";

    public void onTrimMemory(int level) {
        switch (level) {
            case TRIM_MEMORY_COMPLETE:
            case TRIM_MEMORY_MODERATE:
            case TRIM_MEMORY_BACKGROUND:
            case TRIM_MEMORY_RUNNING_CRITICAL:
            case TRIM_MEMORY_RUNNING_LOW:
            case TRIM_MEMORY_RUNNING_MODERATE:
                //fallthrough
                onLowMemory();
                break;
            case TRIM_MEMORY_UI_HIDDEN:
            default:
                return;
        }
    }

    public void onLowMemory() {
        Log.i(LOG_TAG, "Low memory detected.");
    }
}
