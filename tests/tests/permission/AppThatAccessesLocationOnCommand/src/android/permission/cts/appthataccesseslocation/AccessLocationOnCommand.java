/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.permission.cts.appthataccesseslocation;

import static android.location.LocationManager.GPS_PROVIDER;

import android.app.Service;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

public class AccessLocationOnCommand extends Service {
    private static final long DELAY_MILLIS = 100;

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return (new Handler()).postDelayed(
                () -> getSystemService(LocationManager.class).getLastKnownLocation(GPS_PROVIDER),
                DELAY_MILLIS);
    }
}
