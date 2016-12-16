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

package com.android.cts.normalapp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

public class ExposedActivity extends Activity {
    private static final String ACTION_START_ACTIVITY =
            "com.android.cts.ephemeraltest.START_ACTIVITY";
    private static final String EXTRA_ACTIVITY_NAME =
            "com.android.cts.ephemeraltest.EXTRA_ACTIVITY_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent broadcastIntent = new Intent(ACTION_START_ACTIVITY);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, "com.android.cts.normalapp");
        broadcastIntent.putExtra(EXTRA_ACTIVITY_NAME, "ExposedActivity");
        sendBroadcast(broadcastIntent);

        finish();
    }
}
