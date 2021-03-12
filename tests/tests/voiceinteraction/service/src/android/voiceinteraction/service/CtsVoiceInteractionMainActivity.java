/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.voiceinteraction.service;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.voiceinteraction.common.Utils;

/**
 * Uses this activity to trigger different services for testing hotword detection service related
 * functionality.
 *
 * It will trigger the voice interaction service by the service type and also bring the test event
 * into it.
 */
public class CtsVoiceInteractionMainActivity extends Activity {
    static final String TAG = "CtsVoiceInteractionMainActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        int serviceType = intent.getIntExtra(Utils.KEY_SERVICE_TYPE, -1);
        Intent serviceIntent = new Intent();
        if (serviceType == Utils.HOTWORD_DETECTION_SERVICE_NONE) {
            serviceIntent.setComponent(new ComponentName(this, MainInteractionService.class));
        } else if (serviceType == Utils.HOTWORD_DETECTION_SERVICE_BASIC) {
            serviceIntent.setComponent(new ComponentName(this,
                    BasicVoiceInteractionService.class));
        } else {
            Log.w(TAG, "Never here");
            finish();
            return;
        }
        serviceIntent.putExtra(Utils.KEY_TEST_EVENT, intent.getIntExtra(Utils.KEY_TEST_EVENT, -1));
        ComponentName serviceName = startService(serviceIntent);
        Log.i(TAG, "Started service: " + serviceName);
        finish();
    }
}
