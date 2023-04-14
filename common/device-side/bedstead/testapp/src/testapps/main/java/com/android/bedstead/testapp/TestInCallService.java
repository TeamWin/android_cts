/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.testapp;

import android.content.Intent;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.InCallService;

import com.android.eventlib.events.services.ServiceBoundEvent;

/**
 * Test {@link InCallService} to receive updates about Call events during tests
 */
public class TestInCallService extends InCallService {

    @Override
    public IBinder onBind(Intent intent) {
        ServiceBoundEvent.logger(this, getClass().getName(), intent).log();
        return super.onBind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        call.disconnect();
    }
}
