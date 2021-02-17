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

package android.sensorratepermission.cts.mictoggleoffapi25;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorRatePermissionEventConnectionTestHelper;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.os.IBinder;

import java.util.concurrent.TimeUnit;

/**
 * Helper app to test the cases where two apps register listeners at the same time.
 * It targets API 25 and therefore can have high sampling rates.
 */
public class MainService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Context context = this.getApplicationContext();
            SensorManager sensorManager = context.getSystemService(SensorManager.class);
            Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (sensor != null) {
                TestSensorEnvironment testEnvironment = new TestSensorEnvironment(
                        context,
                        sensor,
                        3000 /* samplingPeriodUs */,
                        (int) TimeUnit.SECONDS.toMicros(5));
                SensorRatePermissionEventConnectionTestHelper eventConnectionTestHelper =
                        new SensorRatePermissionEventConnectionTestHelper(testEnvironment);
                eventConnectionTestHelper.getSensorEvents(true, 1024 /* numOfEvents */);
            }
        } catch (InterruptedException e) {

        }
        stopSelf();
        return START_STICKY;
    }
}