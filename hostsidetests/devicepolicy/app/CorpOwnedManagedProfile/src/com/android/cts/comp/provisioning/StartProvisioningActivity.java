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
package com.android.cts.comp.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import com.android.cts.comp.AdminReceiver;

public class StartProvisioningActivity extends Activity {
    private static final int REQUEST_CODE = 1;
    private static final String TAG = "StartProvisionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Reduce flakiness of the test
        // Show activity on top of keyguard
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // Turn on screen to prevent activity being paused by system
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Only provision it if the activity is not re-created
        if (savedInstanceState == null) {
            Intent provisioningIntent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                            AdminReceiver.getComponentName(this))
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
                    // this flag for Corp owned only
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT, true);

            startActivityForResult(provisioningIntent, REQUEST_CODE);
            Log.i(TAG, "Start provisioning intent");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            try {
                SilentProvisioningTestManager.getInstance().notifyProvisioningResult(
                        resultCode == RESULT_OK);
            } catch (InterruptedException e) {
                Log.e(TAG, "notifyProvisioningResult", e);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
