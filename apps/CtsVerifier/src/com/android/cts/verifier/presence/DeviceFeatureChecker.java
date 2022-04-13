/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.verifier.presence;


import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

/** Checks if a device supports BLE, UWB, or NAN feature. */
public class DeviceFeatureChecker {

    /** Checks if device supports UWB and passes calling test automatically if not. */
    public static void checkUwbFeature(Context context, View passButton, String toastMessage) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB)) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
            finishUnsupportedTestActivity(context, passButton);
            Log.e(context.getClass().getName(),
                    "Device does not support UWB, automatically passing test");
        }
    }

    /** Finishes an unsupported test activity and automatically passes the test. */
    private static void finishUnsupportedTestActivity(Context context, View passButton) {
        passButton.performClick();
        Activity activity = (Activity) (context);
        activity.finish();
    }
}
