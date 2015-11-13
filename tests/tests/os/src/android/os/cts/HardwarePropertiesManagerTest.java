/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.os.cts;

import android.content.Context;
import android.os.CpuUsageInfo;
import android.os.HardwarePropertiesManager;
import android.os.SystemClock;
import android.test.AndroidTestCase;

import java.lang.Math;

public class HardwarePropertiesManagerTest extends AndroidTestCase {
    public static final int MAX_FAN_SPEED = 20000;
    public static final int MAX_DEVICE_TEMP = 200;
    public static final int MONITORING_ITERATION_NUMBER = 10;

    // Time between checks in milliseconds.
    public static final long SLEEP_TIME = 10;

    private void checkFanSpeed(float speed) {
        assertTrue(speed >= 0 && speed < MAX_FAN_SPEED);
    }

    private void checkDeviceTemp(float temp) {
        assertTrue(Math.abs(temp) < MAX_DEVICE_TEMP);
    }

    private void checkCpuUsageInfo(CpuUsageInfo info) {
        assertTrue(info.getActive() >= 0 && info.getTotal() >= 0 && info.getTotal() >= info.getActive());
    }

    private void checkFanSpeeds(float[] fanSpeeds) {
        for (float speed : fanSpeeds) {
            checkFanSpeed(speed);
        }
    }

    private void checkTemps(float[] temps) {
        for (float temp : temps) {
            checkDeviceTemp(temp);
        }
    }

    private void checkCpuUsages(CpuUsageInfo[] cpuUsages) {
        for (CpuUsageInfo info : cpuUsages) {
            checkCpuUsageInfo(info);
        }
    }

    // Check validity of new array of fan speeds:
    // the number of fans should be the same.
    private void checkFanSpeeds(float[] speeds, float[] oldSpeeds) {
        assertEquals(speeds.length, oldSpeeds.length);
    }

    // Check validity of new array of device temperatures:
    // the number of entries should be the same.
    private void checkDeviceTemps(float[] temps, float[] oldTemps) {
        assertEquals(temps.length, oldTemps.length);
    }

    // Check validity of new array of cpu usages:
    // The number of CPUs should be the same and total/active time should not decrease.
    private void checkCpuUsages(CpuUsageInfo[] infos,
                                CpuUsageInfo[] oldInfos) {
        assertEquals(infos.length, oldInfos.length);
        for (int i = 0; i < infos.length; ++i) {
            assertTrue(oldInfos[i].getActive() <= infos[i].getActive() &&
                    oldInfos[i].getTotal() <= infos[i].getTotal());
        }
    }

    /**
     * test points:
     * 1. Get fan speeds, device temperatures and CPU usage information.
     * 2. Check for validity.
     * 3. Sleep.
     * 4. Do it 10 times and compare with old ones.
     */
    public void testHardwarePropertiesManager() throws InterruptedException {
        HardwarePropertiesManager hm = (HardwarePropertiesManager) getContext().getSystemService(
            Context.HARDWARE_PROPERTIES_SERVICE);

        float[] oldFanSpeeds = hm.getFanSpeeds();
        float[] oldCpuTemps = hm.getDeviceTemperatures(
            HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU);
        float[] oldGpuTemps = hm.getDeviceTemperatures(
            HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU);
        float[] oldBatteryTemps = hm.getDeviceTemperatures(
            HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY);
        CpuUsageInfo[] oldCpuUsages = hm.getCpuUsages();

        checkFanSpeeds(oldFanSpeeds);
        checkTemps(oldCpuTemps);
        checkTemps(oldGpuTemps);
        checkTemps(oldBatteryTemps);
        checkCpuUsages(oldCpuUsages);

        for (int i = 0; i < MONITORING_ITERATION_NUMBER; i++) {
            Thread.sleep(SLEEP_TIME);

            float[] fanSpeeds = hm.getFanSpeeds();
            float[] cpuTemps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU);
            float[] gpuTemps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU);
            float[] batteryTemps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY);
            CpuUsageInfo[] cpuUsages = hm.getCpuUsages();

            checkFanSpeeds(fanSpeeds);
            checkTemps(cpuTemps);
            checkTemps(gpuTemps);
            checkTemps(batteryTemps);
            checkCpuUsages(cpuUsages);

            checkFanSpeeds(fanSpeeds, oldFanSpeeds);
            checkDeviceTemps(cpuTemps, oldCpuTemps);
            checkDeviceTemps(gpuTemps, oldGpuTemps);
            checkDeviceTemps(batteryTemps, oldBatteryTemps);
            checkCpuUsages(cpuUsages, oldCpuUsages);

            oldFanSpeeds = fanSpeeds;
            oldCpuTemps = cpuTemps;
            oldGpuTemps = gpuTemps;
            oldBatteryTemps = batteryTemps;
            oldCpuUsages = cpuUsages;
        }
    }
}
