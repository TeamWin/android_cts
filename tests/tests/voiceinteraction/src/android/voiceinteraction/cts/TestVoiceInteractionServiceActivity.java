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

package android.voiceinteraction.cts;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.voiceinteraction.common.Utils;

public class TestVoiceInteractionServiceActivity extends Activity {
    static final String TAG = "TestVoiceInteractionServiceActivity";

    void triggerHotwordDetectionServiceTest(int serviceType) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.START_TEST_VOICE_INTERACTION");
        intent.setComponent(new ComponentName(Utils.SERVICE_PACKAGE_NAME,
                Utils.SERVICE_PACKAGE_CTS_VOICE_INTERACTION_MAIN_ACTIVITY_NAME));
        intent.putExtra(Utils.KEY_SERVICE_TYPE, serviceType);
        intent.putExtra(Utils.KEY_TEST_EVENT, Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST);
        Log.i(TAG, "triggerHotwordDetectionServiceTest: " + intent);
        startActivity(intent);
    }
}
