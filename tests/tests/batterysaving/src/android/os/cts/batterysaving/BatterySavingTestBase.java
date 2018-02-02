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

import static com.android.compatibility.common.util.BatteryUtils.runDumpsysBatteryReset;
import static com.android.compatibility.common.util.BatteryUtils.turnOnScreen;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.TestUtils.waitUntil;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import org.junit.After;
import org.junit.Before;

public class BatterySavingTestBase {
    private static final String TAG = "BatterySavingTestBase";

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public static final boolean DEBUG = true;

    @Before
    public final void resetDumpsysBatteryBeforeTest() throws Exception {
        turnOnScreen(true);
    }

    @After
    public final void resetDumpsysBatteryAfterTest() throws Exception {
        runDumpsysBatteryReset();
        turnOnScreen(true);
    }

    public String getLogTag() {
        return TAG;
    }

    /** Print a debug log on logcat. */
    public void debug(String message) {
        if (DEBUG || Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(getLogTag(), message);
        }
    }

    public void waitUntilAlarmForceAppStandby(boolean expected) throws Exception {
        waitUntil("Force all apps standby still " + !expected + " (alarm)", () ->
                runShellCommand("dumpsys alarm").contains("Force all apps standby: " + expected));
    }

    public void waitUntilJobForceAppStandby(boolean expected) throws Exception {
        waitUntil("Force all apps standby still " + !expected + " (job)", () ->
                runShellCommand("dumpsys jobscheduler")
                        .contains("Force all apps standby: " + expected));
    }

    public void waitUntilForceBackgroundCheck(boolean expected) throws Exception {
        waitUntil("Force background check still " + !expected + " (job)", () ->
                runShellCommand("dumpsys activity").contains("mForceBackgroundCheck=" + expected));
    }

    public static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    public PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    public PowerManager getPowerManager() {
        return getContext().getSystemService(PowerManager.class);
    }

    public BatteryManager getBatteryManager() {
        return getContext().getSystemService(BatteryManager.class);
    }
}
