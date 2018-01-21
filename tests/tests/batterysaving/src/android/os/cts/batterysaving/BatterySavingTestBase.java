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

import static junit.framework.Assert.fail;

import android.content.Context;
import android.os.BatteryManager;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings.Global;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import org.junit.After;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Predicate;

public class BatterySavingTestBase {
    private static final String TAG = "BatterySavingTestBase";

    public static final boolean DEBUG = true;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @After
    public final void resetDumpsysBatteryAfterTest() throws Exception {
        runDumpsysBatteryReset();
    }

    @FunctionalInterface
    public interface BooleanSupplierWithThrow {
        boolean getAsBoolean() throws Exception;
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

    /** Print an error log and fail. */
    public void failWithLog(String message) {
        Log.e(getLogTag(), message);
        fail(message);
    }

    private static String readAll(ParcelFileDescriptor pfd) {
        try {
            try {
                final StringBuilder ret = new StringBuilder(1024);

                try (BufferedReader r = new BufferedReader(
                        new FileReader(pfd.getFileDescriptor()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        ret.append(line);
                        ret.append("\n");
                    }
                    r.readLine();
                }
                return ret.toString();
            } finally {
                pfd.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Run a command and returns the result. IF resultAsserter is not null, apply it on the output
     * and fail it it returns false.
     */
    public String runCommand(String command, Predicate<String> resultAsserter) {
        debug("Running command: " + command);

        final String result;
        try {
            result = readAll(InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand(command));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        debug("Command output (" + result.length() + " chars):\n" + result);

        if (resultAsserter != null && !resultAsserter.test(result)) {
            failWithLog("Command '" + command + "' failed, output was:\n" + result);
        }
        return result;
    }

    /** Run a command and return the result. */
    public String runCommand(String command) {
        return runCommand(command, null);
    }

    /** Run "cmd settings put" */
    private void putSetting(String scope, String key, String value) {
        // Hmm, technically we should escape a value, but if I do like '1', it won't work. ??
        runCommand("settings put " + scope + " " + key + " " + value,
                (output) -> output.length() == 0);
    }

    /** Run "cmd settings put global" */
    public void putGlobalSetting(String key, String value) {
        putSetting("global", key, value);
    }

    /** Make the target device think it's off charger. */
    public void runDumpsysBatteryUnplug() throws Exception {
        runCommand("dumpsys battery unplug", (output) -> output.length() == 0);
        waitUntil("Device still charging", () -> !getBatteryManager().isCharging());

        Log.d(TAG, "Battery UNPLUGGED");
    }

    /** Reset {@link #runDumpsysBatteryUnplug}.  */
    public void runDumpsysBatteryReset() throws Exception {
        runCommand("dumpsys battery reset", (output) -> output.length() == 0);

        Log.d(TAG, "Battery RESET");
    }

    /** Run "adb shell am make-uid-idle PACKAGE" */
    public void runMakeUidIdle(String packageName) {
        runCommand("am make-uid-idle " + packageName, (output) -> output.length() == 0);
    }

    /** Run "adb shell am kill PACKAGE" */
    public void runKill(String packageName) {
        runCommand("am kill " + packageName, (output) -> output.length() == 0);
    }

    /**
     * Wait until {@code predicate} is satisfied, or fail, with the default timeout.
     */
    public void waitUntil(String message, BooleanSupplierWithThrow predicate) throws Exception {
        waitUntil(message, 0, predicate);
    }

    /**
     * Wait until {@code predicate} is satisfied, or fail, with a given timeout.
     */
    public void waitUntil(String message, int timeoutSecond, BooleanSupplierWithThrow predicate)
            throws Exception {
        if (timeoutSecond <= 0) {
            timeoutSecond = DEFAULT_TIMEOUT_SECONDS;
        }
        final long timeout = SystemClock.uptimeMillis() + timeoutSecond * 1000;
        while (SystemClock.uptimeMillis() < timeout) {
            if (predicate.getAsBoolean()) {
                return; // okay
            }
            Thread.sleep(1000);
        }
        failWithLog("Timeout: " + message);
    }

    public void waitUntilAlarmForceAppStandby(boolean expected) throws Exception {
        waitUntil("Force all apps standby still " + !expected + " (alarm)", () ->
                runCommand("dumpsys alarm").contains("Force all apps standby: " + expected));
    }

    public void waitUntilJobForceAppStandby(boolean expected) throws Exception {
        waitUntil("Force all apps standby still " + !expected + " (job)", () ->
                runCommand("dumpsys jobscheduler").contains("Force all apps standby: " + expected));
    }

    public void waitUntilForceBackgroundCheck(boolean expected) throws Exception {
        waitUntil("Force background check still " + !expected + " (job)", () ->
                runCommand("dumpsys activity").contains("mForceBackgroundCheck=" + expected));
    }

    public static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    public PowerManager getPowerManager() {
        return getContext().getSystemService(PowerManager.class);
    }

    public BatteryManager getBatteryManager() {
        return getContext().getSystemService(BatteryManager.class);
    }

    /**
     * Enable / disable battery saver. Note {@link #runDumpsysBatteryUnplug} must have been
     * executed before enabling BS.
     */
    public void enableBatterySaver(boolean enabled) throws Exception {
        if (enabled) {
            putGlobalSetting(Global.LOW_POWER_MODE, "1");
            waitUntil("Battery saver still off", () -> getPowerManager().isPowerSaveMode());
            waitUntil("Location mode still " + getPowerManager().getLocationPowerSaveMode(),
                    () -> (PowerManager.LOCATION_MODE_NO_CHANGE
                            != getPowerManager().getLocationPowerSaveMode()));
        } else {
            putGlobalSetting(Global.LOW_POWER_MODE, "0");
            waitUntil("Battery saver still on", () -> !getPowerManager().isPowerSaveMode());
            waitUntil("Location mode still " + getPowerManager().getLocationPowerSaveMode(),
                    () -> (PowerManager.LOCATION_MODE_NO_CHANGE
                            == getPowerManager().getLocationPowerSaveMode()));
        }

        Log.d(TAG, "Battery saver turned " + (enabled ? "ON" : "OFF"));
    }
}
