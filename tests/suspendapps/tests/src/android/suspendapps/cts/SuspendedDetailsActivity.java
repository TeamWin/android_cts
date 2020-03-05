/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.suspendapps.cts;

import static android.suspendapps.cts.Constants.ACTION_REPORT_MORE_DETAILS_ACTIVITY_STARTED;
import static android.suspendapps.cts.Constants.EXTRA_RECEIVED_PACKAGE_NAME;
import static android.suspendapps.cts.Constants.PACKAGE_NAME;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

public class SuspendedDetailsActivity extends Activity {
    private static final String TAG = SuspendedDetailsActivity.class.getSimpleName();

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        final String suspendedPackage =  getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        super.onStart();
        final Intent reportStart = new Intent(ACTION_REPORT_MORE_DETAILS_ACTIVITY_STARTED)
                .putExtra(EXTRA_RECEIVED_PACKAGE_NAME, suspendedPackage)
                .setPackage(PACKAGE_NAME);
        sendBroadcast(reportStart);
        finish();
    }
}
