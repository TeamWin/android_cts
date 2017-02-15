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
 * limitations under the License.
 */

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

/**
 * A helper activity that executes commands sent from CtsVerifier in the primary user to the managed
 * profile in COMP mode.
 *
 * Note: We have to use a dummy activity because cross-profile intents only work for activities.
 */
public class CompHelperActivity extends Activity {

    public static final String TAG = "CompHelperActivity";

    // Set always-on VPN.
    public static final String ACTION_SET_ALWAYS_ON_VPN
            = "com.android.cts.verifier.managedprovisioning.COMP_SET_ALWAYS_ON_VPN";
    // Set the number of login failures after which the managed profile is wiped.
    public static final String ACTION_SET_MAXIMUM_PASSWORD_ATTEMPTS
            = "com.android.cts.verifier.managedprovisioning.COMP_SET_MAXIMUM_PASSWORD_ATTEMPTS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ComponentName admin = CompDeviceAdminTestReceiver.getReceiverComponentName();
        final DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(
                Context.DEVICE_POLICY_SERVICE);

        final String action = getIntent().getAction();
        if (ACTION_SET_ALWAYS_ON_VPN.equals(action)) {
            try {
                dpm.setAlwaysOnVpnPackage(admin, getPackageName(), false /* lockdownEnabled */);
            } catch (Exception e) {
                Log.e(TAG, "Unable to set always-on VPN", e);
            }
        } else if (ACTION_SET_MAXIMUM_PASSWORD_ATTEMPTS.equals(action)) {
            dpm.setMaximumFailedPasswordsForWipe(admin, 100);
        }
        finish();
    }
}
