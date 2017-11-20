/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.log.LogUtil;

import com.google.common.base.Charsets;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test for statsd
 *
 * Validates reporting of statsd logging based on different events
 */
public class StatsdValidationTest extends ProtoDumpTestCase {
    private static final String TAG = "StatsdValidationTest";

    // TODO: Use a statsd-specific app (temporarily just borrowing the batterystats app)
    private static final String DEVICE_SIDE_TEST_APK = "CtsBatteryStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE
            = "com.android.server.cts.device.batterystats";
    private static final String DEVICE_SIDE_BG_SERVICE_COMPONENT
            = "com.android.server.cts.device.batterystats/.BatteryStatsBackgroundService";
    private static final String DEVICE_SIDE_FG_ACTIVITY_COMPONENT
            = "com.android.server.cts.device.batterystats/.BatteryStatsForegroundActivity";

    // These constants are those in PackageManager.
    public static final String FEATURE_BLUETOOTH_LE = "android.hardware.bluetooth_le";
    public static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";
    public static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    public static final String FEATURE_WIFI = "android.hardware.wifi";

    // Constants from BatteryStatsBgVsFgActions.java (not directly accessible here).
    public static final String KEY_ACTION = "action";
    public static final String ACTION_BLE_SCAN_OPTIMIZED = "action.ble_scan_optimized";
    public static final String ACTION_BLE_SCAN_UNOPTIMIZED = "action.ble_scan_unoptimized";
    public static final String ACTION_GPS = "action.gps";
    public static final String ACTION_JOB_SCHEDULE = "action.jobs";
    public static final String ACTION_SYNC = "action.sync";
    public static final String ACTION_WIFI_SCAN = "action.wifi_scan";

    public static final String KEY_REQUEST_CODE = "request_code";
    public static final String BG_VS_FG_TAG = "BatteryStatsBgVsFgActions";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Uninstall to clear the history in case it's still on the device.
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
        super.tearDown();
    }

    protected void testScreenOn() throws Exception {
        getDevice().executeShellCommand("input keyevent KEYCODE_WAKEUP");
        getDevice().executeShellCommand("wm dismiss-keyguard");
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    protected void testScreenOff() throws Exception {
        getDevice().executeShellCommand("input keyevent KEYCODE_SLEEP");
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    public void testAlarms() throws Exception {
        installTestApp();
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsAlarmTest", "testAlarms");
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    public void testWakeLock() throws Exception {
        installTestApp();
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWakeLockTests",
                "testHoldShortWakeLock");
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    private void startSimpleActivity() throws Exception {
        getDevice().executeShellCommand(
                "am start -n com.android.server.cts.device.batterystats/.SimpleActivity");
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    public void testUnoptimizedBleScans() throws Exception {
        if (!hasFeature(FEATURE_BLUETOOTH_LE, true)) return;
        installTestApp();
        executeBackground(ACTION_BLE_SCAN_UNOPTIMIZED, 40_000);
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }


    public void testOptimizedBleScans() throws Exception {
        if (!hasFeature(FEATURE_BLUETOOTH_LE, true)) return;
        installTestApp();
        executeBackground(ACTION_BLE_SCAN_OPTIMIZED, 40_000);
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    public void testGpsUpdates() throws Exception {
        if (!hasFeature(FEATURE_LOCATION_GPS, true)) return;
        installTestApp();
        // Whitelist this app against background location request throttling
        getDevice().executeShellCommand(String.format(
                "settings put global location_background_throttle_package_whitelist %s",
                DEVICE_SIDE_TEST_PACKAGE));

        executeBackground(ACTION_GPS, 60_000);
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    public void testJob() throws Exception {
        installTestApp();
        executeBackground(ACTION_JOB_SCHEDULE, 60_000);
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    public void testSync() throws Exception {
        installTestApp();
        executeBackground(ACTION_SYNC, 60_000);
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput.
    }

    public void testWifiScan() throws Exception {
        if (!hasFeature(FEATURE_WIFI, true)) return;
        installTestApp();
        // Whitelist this app against background wifi scan throttling
        getDevice().executeShellCommand(String.format(
                "settings put global wifi_scan_background_throttle_package_whitelist %s",
                DEVICE_SIDE_TEST_PACKAGE));

        executeBackground(ACTION_WIFI_SCAN, 120_000);
        // TODO: assertTrue() on something pulled from statsd report using getStatsOutput
    }

    private void installTestApp() throws Exception {
        installPackage(DEVICE_SIDE_TEST_APK, true);
    }

    /**
     * TODO: Update this.
     */
    private int getStatsOutput() throws Exception {
        int uid = getUid();
        String statsdOutput = getDevice().executeShellCommand(
                /* TODO: */"dumpsys batterystats --checkin");
        // TODO: Extract something of interest from statsdOutput using the function's arguments.
        return 1;
    }

    // TODO: All the following code is entirely taken verbatim from BatteryStatsValidationTest.
    //       If we end up needing it, we should refactor the code so that they share these commands.

    private int getUid() throws Exception {
        String uidLine = getDevice().executeShellCommand("cmd package list packages -U "
                + DEVICE_SIDE_TEST_PACKAGE);
        String[] uidLineParts = uidLine.split(":");
        // 3rd entry is package uid
        assertTrue(uidLineParts.length > 2);
        int uid = Integer.parseInt(uidLineParts[2].trim());
        assertTrue(uid > 10000);
        return uid;
    }

    /**
     * Runs a (background) service to perform the given action, and waits for
     * the device to report that the action has finished (via a logcat message) before returning.
     * @param actionValue one of the constants in BatteryStatsBgVsFgActions indicating the desired
     *                    action to perform.
     * @param maxTimeMs max time to wait (in ms) for action to report that it has completed.
     * @return A string, representing a random integer, assigned to this particular request for the
     *                     device to perform the given action. This value can be used to receive
     *                     communications via logcat from the device about this action.
     */
    private String executeBackground(String actionValue, int maxTimeMs) throws Exception {
        String requestCode = executeBackground(actionValue);
        String searchString = getCompletedActionString(actionValue, requestCode);
        checkLogcatForText(BG_VS_FG_TAG, searchString, maxTimeMs);
        return requestCode;
    }

    /**
     * Runs a (background) service to perform the given action.
     * @param actionValue one of the constants in BatteryStatsBgVsFgActions indicating the desired
     *                    action to perform.
     * @return A string, representing a random integer, assigned to this particular request for the
      *                     device to perform the given action. This value can be used to receive
      *                     communications via logcat from the device about this action.
     */
    private String executeBackground(String actionValue) throws Exception {
        allowBackgroundServices();
        String requestCode = Integer.toString(new Random().nextInt());
        getDevice().executeShellCommand(String.format(
                "am startservice -n '%s' -e %s %s -e %s %s",
                DEVICE_SIDE_BG_SERVICE_COMPONENT,
                KEY_ACTION, actionValue,
                KEY_REQUEST_CODE, requestCode));
        return requestCode;
    }

    /** Required to successfully start a background service from adb in O. */
    private void allowBackgroundServices() throws Exception {
        getDevice().executeShellCommand(String.format(
                "cmd deviceidle tempwhitelist %s", DEVICE_SIDE_TEST_PACKAGE));
    }

    /**
     * Runs an activity (in the foreground) to perform the given action, and waits
     * for the device to report that the action has finished (via a logcat message) before returning.
     * @param actionValue one of the constants in BatteryStatsBgVsFgActions indicating the desired
     *                    action to perform.
     * @param maxTimeMs max time to wait (in ms) for action to report that it has completed.
     * @return A string, representing a random integer, assigned to this particular request for the
     *                     device to perform the given action. This value can be used to receive
     *                     communications via logcat from the device about this action.
     */
    private String executeForeground(String actionValue, int maxTimeMs) throws Exception {
        String requestCode = executeForeground(actionValue);
        String searchString = getCompletedActionString(actionValue, requestCode);
        checkLogcatForText(BG_VS_FG_TAG, searchString, maxTimeMs);
        return requestCode;
    }

    /**
     * Runs an activity (in the foreground) to perform the given action.
     * @param actionValue one of the constants in BatteryStatsBgVsFgActions indicating the desired
     *                    action to perform.
     * @return A string, representing a random integer, assigned to this particular request for the
     *                     device to perform the given action. This value can be used to receive
     *                     communications via logcat from the device about this action.
     */
    private String executeForeground(String actionValue) throws Exception {
        String requestCode = Integer.toString(new Random().nextInt());
        getDevice().executeShellCommand(String.format(
                "am start -n '%s' -e %s %s -e %s %s",
                DEVICE_SIDE_FG_ACTIVITY_COMPONENT,
                KEY_ACTION, actionValue,
                KEY_REQUEST_CODE, requestCode));
        return requestCode;
    }

    /**
     * The string that will be printed in the logcat when the action completes. This needs to be
     * identical to {@link com.android.server.cts.device.batterystats.BatteryStatsBgVsFgActions#tellHostActionFinished}.
     */
    private String getCompletedActionString(String actionValue, String requestCode) {
        return String.format("Completed performing %s for request %s", actionValue, requestCode);
    }

    /**
    * Runs logcat and waits (for a maximumum of maxTimeMs) until the desired text is displayed with
    * the given tag.
    * Logcat is not cleared, so make sure that text is unique (won't get false hits from old data).
    * Note that, in practice, the actual max wait time seems to be about 10s longer than maxTimeMs.
    */
    private void checkLogcatForText(String logcatTag, String text, int maxTimeMs) {
        IShellOutputReceiver receiver = new IShellOutputReceiver() {
            private final StringBuilder mOutputBuffer = new StringBuilder();
            private final AtomicBoolean mIsCanceled = new AtomicBoolean(false);

            @Override
            public void addOutput(byte[] data, int offset, int length) {
                if (!isCancelled()) {
                    synchronized (mOutputBuffer) {
                        String s = new String(data, offset, length, Charsets.UTF_8);
                        mOutputBuffer.append(s);
                        if (checkBufferForText()) {
                            mIsCanceled.set(true);
                        }
                    }
                }
            }

            private boolean checkBufferForText() {
                if (mOutputBuffer.indexOf(text) > -1) {
                    return true;
                } else {
                    // delete all old data (except the last few chars) since they don't contain text
                    // (presumably large chunks of data will be added at a time, so this is
                    // sufficiently efficient.)
                    int newStart = mOutputBuffer.length() - text.length();
                    if (newStart > 0) {
                        mOutputBuffer.delete(0, newStart);
                    }
                    return false;
                }
            }

            @Override
            public boolean isCancelled() {
                return mIsCanceled.get();
            }

            @Override
            public void flush() {
            }
        };

        try {
            // Wait for at most maxTimeMs for logcat to display the desired text.
            getDevice().executeShellCommand(String.format("logcat -s %s -e '%s'", logcatTag, text),
                    receiver, maxTimeMs, TimeUnit.MILLISECONDS, 0);
        } catch (com.android.tradefed.device.DeviceNotAvailableException e) {
            System.err.println(e);
        }
    }

    /**
     * Determines if the device has the given feature.
     * Prints a warning if its value differs from requiredAnswer.
     */
    private boolean hasFeature(String featureName, boolean requiredAnswer) throws Exception {
        final String features = getDevice().executeShellCommand("pm list features");
        boolean hasIt = features.contains(featureName);
        if (hasIt != requiredAnswer) {
            LogUtil.CLog.w("Device does " + (requiredAnswer ? "not " : "") + "have feature "
                    + featureName);
        }
        return hasIt;
    }
}
