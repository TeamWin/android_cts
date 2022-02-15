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
import android.os.Bundle;
import android.util.Log;

/**
 * An application to register vendor command listeners. This can be used to test the vendor command
 * listener registration APIs. Actions supported are:
 *
 * <p>
 *
 * <p>1. android.hdmicec.app.VENDOR_LISTENER_WITH_ID: Registers a vendor command listener with
 * vendor ID
 *
 * <p>Usage: <code>START_COMMAND -a android.hdmicec.app.VENDOR_LISTENER_WITH_ID</code>
 *
 * <p>
 *
 * <p>2. android.hdmicec.app.VENDOR_LISTENER_WITHOUT_ID: Registers a vendor command listener without
 * a vendor ID associated with it.
 *
 * <p>Usage: <code>START_COMMAND -a android.hdmicec.app.VENDOR_LISTENER_WITHOUT_ID
 * </code>
 *
 * <p>
 *
 * <p>where START_COMMAND is
 *
 * <p><code>
 * adb shell am start -n "android.hdmicec.app/android.hdmicec.app.HdmiCecVendorCommandListener"
 * </code>
 */
public class HdmiCecVendorCommandListener extends Activity {

    private static final String TAG = HdmiCecVendorCommandListener.class.getSimpleName();
    private static final int VENDOR_ID = 0xBADDAD;
    private HdmiControlManager mHdmiControlManager;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mHdmiControlManager = getSystemService(HdmiControlManager.class);
        if (mHdmiControlManager == null) {
            Log.i(TAG, "Failed to get HdmiControlManager");
            return;
        }
        HdmiClient client = mHdmiControlManager.getClient(HdmiDeviceInfo.DEVICE_TV);
        if (client == null) {
            client = mHdmiControlManager.getClient(HdmiDeviceInfo.DEVICE_PLAYBACK);
            if (client == null) {
                Log.i(TAG, "Failed to get HDMI client");
                return;
            }
        }

        String action = getIntent().getAction();
        switch (action) {
            case "android.hdmicec.app.VENDOR_LISTENER_WITH_ID":
                HdmiControlManager.VendorCommandListener vendorCommandListenerWithId =
                        new VendorCommandTestListener(VENDOR_ID);
                client.setVendorCommandListener(vendorCommandListenerWithId, VENDOR_ID);
                break;
            case "android.hdmicec.app.VENDOR_LISTENER_WITHOUT_ID":
                HdmiControlManager.VendorCommandListener vendorCommandListener =
                        new VendorCommandTestListener();
                client.setVendorCommandListener(vendorCommandListener);
                break;
            default:
                Log.w(TAG, "Unknown intent!" + action);
        }
    }

    private static class VendorCommandTestListener
            implements HdmiControlManager.VendorCommandListener {

        int mVendorId = 0xFFFFFF;

        VendorCommandTestListener(int vendorId) {
            mVendorId = vendorId;
        }

        VendorCommandTestListener() {}

        @Override
        public void onReceived(
                int sourceAddress, int destAddress, byte[] params, boolean hasVendorId) {
            if (hasVendorId) {
                int receivedVendorId =
                        ((params[0] & 0xFF) << 16) + ((params[1] & 0xFF) << 8) + (params[2] & 0xFF);

                if (mVendorId == receivedVendorId) {
                    Log.i(TAG, "Received vendor command with correct vendor ID");
                } else {
                    Log.i(TAG, "Received vendor command with wrong vendor ID");
                }
            } else {
                Log.i(TAG, "Received vendor command without vendor ID");
            }
        }

        @Override
        public void onControlStateChanged(boolean enabled, int reason) {}
    }
}
