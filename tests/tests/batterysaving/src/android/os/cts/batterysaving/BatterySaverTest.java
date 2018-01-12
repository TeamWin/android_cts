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
package android.os.cts.batterysaving;

import static org.junit.Assert.assertFalse;

import android.provider.Settings.Global;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BatterySaverTest extends BatterySavingTestBase {
    @After
    public void tearDown() throws Exception {
        runDumpsysBatteryReset();
    }

    @Test
    public void testActivateBatterySaver() throws Exception {
        assertFalse(getPowerManager().isPowerSaveMode());

        // Unplug the charger.
        runDumpsysBatteryUnplug();

        // Activate battery saver.
        putGlobalSetting(Global.LOW_POWER_MODE, "1");
        waitUntil("Battery saver still off", () -> getPowerManager().isPowerSaveMode());

        // Make sure the job scheduler and the alarm manager are informed.
        waitUntil("Force all apps standby still off (alarm)", () ->
            runCommand("dumpsys alarm").contains("Force all apps standby: true"));
        waitUntil("Force all apps standby still off (job)", () ->
            runCommand("dumpsys jobscheduler").contains("Force all apps standby: true"));

        // Deactivate.
        // To avoid too much churn, let's sleep a little bit before deactivating.
        Thread.sleep(1000);

        putGlobalSetting(Global.LOW_POWER_MODE, "0");
        waitUntil("Battery saver still on", () -> !getPowerManager().isPowerSaveMode());

        // Make sure the job scheduler and the alarm manager are informed.
        waitUntil("Force all apps standby still off (alarm)", () ->
                runCommand("dumpsys alarm").contains("Force all apps standby: false"));
        waitUntil("Force all apps standby still off (job)", () ->
                runCommand("dumpsys jobscheduler").contains("Force all apps standby: false"));
    }
}
