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
package android.app.cts.shortfgstesthelper;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.NOTIFICATION_ID;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.createNotification;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class FgsBase extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle the incoming intent.
        final ShortFgsMessage incoming = ShortFgsHelper.getMessage(intent);

        if (incoming.isSetForeground()) {
            startForeground(NOTIFICATION_ID, createNotification(), incoming.getFgsType());
        }

        // If we reach here, send back the method name.
        ShortFgsHelper.sendBackMethodName(this.getClass(), "onStartCommand", (m) -> {
            m.setServiceStartId(startId);
        });

        return incoming.getStartCommandResult();
    }

    @Override
    public void onDestroy() {
        // Send back the called method name.
        ShortFgsHelper.sendBackMethodName(this.getClass(), "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTimeout(int startId) {
        ShortFgsHelper.sendBackMethodName(this.getClass(), "onTimeout", (m) -> {
            m.setServiceStartId(startId);
        });
    }
}
