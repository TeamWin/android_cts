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

package android.hdmicec.app;

import android.app.Activity;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.os.Bundle;
import android.util.Log;

/**
 * A simple activity that can be used to trigger actions using the HdmiControlManager.
 * The action supported is:
 *
 * 1. android.hdmicec.app.OTP: Triggers the OTP
 *    Usage: START_COMMAND -a android.hdmicec.app.OTP
 *
 * where START_COMMAND is
 * adb shell am start -n "android.hdmicec.app/android.hdmicec.app.HdmiCecControlManagerHelper"
 */
public class HdmiControlManagerHelper extends Activity {

    private static final String TAG = HdmiControlManagerHelper.class.getSimpleName();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        HdmiControlManager hdmiControlManager = getSystemService(HdmiControlManager.class);
        if (hdmiControlManager == null) {
            Log.i(TAG, "Failed to get HdmiControlManager");
            return;
        }

        switch (getIntent().getAction()) {
            case "android.hdmicec.app.OTP":
                initiateOtp(hdmiControlManager);
                break;
            default:
                Log.w(TAG, "Unknown intent!");
        }
    }

    private void initiateOtp(HdmiControlManager hdmiControlManager) {
        HdmiPlaybackClient client = hdmiControlManager.getPlaybackClient();
        if (client == null) {
            Log.i(TAG, "Failed to get HdmiPlaybackClient");
            return;
        }

        client.oneTouchPlay((result) -> {
            if (result == HdmiControlManager.RESULT_SUCCESS) {
                Log.i(TAG, "OTP successful");
            } else {
                Log.i(TAG, "OTP failed");
            }
        });
    }
}
