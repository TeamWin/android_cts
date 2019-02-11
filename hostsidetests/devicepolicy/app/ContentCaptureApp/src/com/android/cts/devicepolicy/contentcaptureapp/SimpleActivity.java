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
 * limitations under the License.
 */

package com.android.cts.devicepolicy.contentcaptureapp;

import android.app.Activity;
import android.util.Log;
import android.view.contentcapture.ContentCaptureManager;

public class SimpleActivity extends Activity {

    public static final String TAG = SimpleActivity.class.getSimpleName();

    @Override
    public void onStart() {
        Log.d(TAG, "onStart(): userId=" + android.os.Process.myUserHandle().getIdentifier());
        super.onStart();
        final ContentCaptureManager mgr = getSystemService(ContentCaptureManager.class);
        if (mgr == null) {
            Log.e(TAG, "no manager");
            return;
        }
        final boolean enabled = mgr.isContentCaptureEnabled();
        Log.v(TAG, "enabled: " + enabled);
        setResult(enabled ? 1 : 0);
        finish();
    }
}
