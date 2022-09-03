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

package com.android.cts.verifier.telecom;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.widget.Button;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.R;


/**
 * simple in call Ui for the CallSwitchingAudioTestActivity
 */
@ApiTest(apis = {"android.telecom.InCallService"})
public class CallSwitchingInCallUi extends Activity {
    Button mButton;

    /**
     * onCreate
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in {@link #onSaveInstanceState}.
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.telecom_audio_call_in_call_ui);
        setTurnScreenOn(true);
        setShowWhenLocked(true);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null) {
            km.requestDismissKeyguard(this, null);
        }

        mButton = findViewById(R.id.telecom_in_call_go_back_button);
        if (mButton == null) {
            finish();
            return;
        }

        mButton.setOnClickListener(v -> {
            finish();
        });
    }
}
