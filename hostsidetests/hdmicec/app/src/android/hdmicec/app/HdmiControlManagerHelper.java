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
import android.hardware.hdmi.HdmiClient;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.os.Bundle;
import android.util.Log;

/**
 * A simple activity that can be used to trigger actions using the HdmiControlManager. The actions
 * supported are:
 *
 * <p>1. android.hdmicec.app.OTP: Triggers the OTP
 * <p>   Usage: START_COMMAND -a android.hdmicec.app.OTP
 *
 * <p>2. android.hdmicec.app.VENDOR_CMD: Sends a vendor command with the content of PARAMS in
 * params.
 * <p>   Usage: START_COMMAND -a android.hdmicec.app.VENDOR_CMD -e paramText PARAMS
 *
 * <p>where START_COMMAND is adb shell am start -n
 * "android.hdmicec.app/android.hdmicec.app.HdmiCControlManagerHelper"
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
            case "android.hdmicec.app.VENDOR_CMD":
                String params = getIntent().getStringExtra("paramText");
                if (params == null) {
                    params = "ctstest";
                }
                sendVendorCommand(hdmiControlManager, params);
                break;
            default:
                Log.w(TAG, "Unknown intent!");
        }
    }

    /**
     * Converts ascii characters to a byte array that can be appended to a CEC message as params.
     * For example, "spa" will be converted to {0x73, 0x70, 0x61}
     */
    private byte[] convertStringToHexParams(String rawParams) {
        byte[] params = new byte[rawParams.length()];
        for (int i = 0; i < rawParams.length(); i++) {
            params[i] = (byte) rawParams.charAt(i);
        }
        return params;
    }

    private void sendVendorCommand(HdmiControlManager hdmiControlManager, String paramsString) {
        HdmiClient client = null;
        int deviceType = 0;
        int[] deviceTypes = {
            HdmiDeviceInfo.DEVICE_TV,
            HdmiDeviceInfo.DEVICE_PLAYBACK,
            HdmiDeviceInfo.DEVICE_AUDIO_SYSTEM
        };

        for (int i = 0; i < deviceTypes.length && client == null; i++) {
            client = hdmiControlManager.getClient(deviceTypes[i]);
            deviceType = i;
        }

        if (client == null) {
            Log.e(TAG, "Could not get HdmiClient, not sending vendor command!");
            return;
        }

        byte[] params = convertStringToHexParams(paramsString);
        int target = (deviceType == HdmiDeviceInfo.DEVICE_TV) ? 4 : 0;
        client.sendVendorCommand(target, params, false);
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
