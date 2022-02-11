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

import static android.Manifest.permission.HDMI_CEC;

import android.app.Activity;
import android.content.Context;
import android.hardware.hdmi.HdmiClient;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiTvClient;
import android.util.Log;
import android.view.KeyEvent;


import java.util.concurrent.TimeUnit;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A simple class that can be used to trigger actions using the HdmiControlManager.
 */
@RunWith(AndroidJUnit4.class)
public final class HdmiControlManagerHelper {
    private static final String LOGICAL_ADDR = "ARG_LOGICAL_ADDR";
    private static final String TAG = HdmiControlManagerHelper.class.getSimpleName();
    HdmiControlManager mHdmiControlManager;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                HDMI_CEC);

        mHdmiControlManager = context.getSystemService(HdmiControlManager.class);
        if (mHdmiControlManager == null) {
            Log.i(TAG, "Failed to get HdmiControlManager");
            return;
        }
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void deviceSelect() throws InterruptedException {
        final String param = InstrumentationRegistry.getArguments().getString(LOGICAL_ADDR);
        int logicalAddress = Integer.parseInt(param);
        HdmiTvClient client = mHdmiControlManager.getTvClient();
        if (client == null) {
            Log.e(TAG, "Failed to get the TV client");
            return;
        }

        client.deviceSelect(
                logicalAddress,
                (result) -> {
                    if (result == HdmiControlManager.RESULT_SUCCESS) {
                        Log.i(TAG, "Selected device with logical address " + logicalAddress);
                    } else {
                        Log.i(
                                TAG,
                                "Could not select device with logical address " + logicalAddress);
                    }
                });
    }

    @Test
    public void interruptedLongPress() throws InterruptedException {
        HdmiClient client = mHdmiControlManager.getPlaybackClient();
        if (client == null) {
            client = mHdmiControlManager.getTvClient();
        }

        if (client == null) {
            Log.i(TAG, "Could not get a TV/Playback client, cannot send key event");
            return;
        }

        try {
            for (int i = 0; i < 5; i++) {
                client.sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP, true);
                TimeUnit.MILLISECONDS.sleep(450);
            }
            client.sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP, false);
            // Sleep for 500ms more
            TimeUnit.MILLISECONDS.sleep(500);
            client.sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, true);
        } catch (InterruptedException ie) {
            Log.w(TAG, "Interrupted between keyevents, could not send all keyevents!");
        }
    }
}
