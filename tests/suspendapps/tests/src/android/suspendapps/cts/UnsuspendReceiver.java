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

import static android.suspendapps.cts.Constants.ACTION_REPORT_PACKAGE_UNSUSPENDED_MANUALLY;
import static android.suspendapps.cts.Constants.EXTRA_RECEIVED_PACKAGE_NAME;
import static android.suspendapps.cts.Constants.PACKAGE_NAME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UnsuspendReceiver extends BroadcastReceiver {
    private static final String TAG = UnsuspendReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case Intent.ACTION_PACKAGE_UNSUSPENDED_MANUALLY:
                final String suspendedPackage = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final Intent reportReceipt = new Intent(ACTION_REPORT_PACKAGE_UNSUSPENDED_MANUALLY)
                        .setPackage(PACKAGE_NAME)
                        .putExtra(EXTRA_RECEIVED_PACKAGE_NAME, suspendedPackage);
                context.sendBroadcast(reportReceipt);
                break;
            default:
                Log.w(TAG, "Unknown action " + intent.getAction());
                break;
        }
    }
}
