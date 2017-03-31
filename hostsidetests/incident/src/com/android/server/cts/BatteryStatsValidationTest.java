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

import com.android.tradefed.log.LogUtil;

/**
 * Test for "dumpsys batterystats -c
 *
 * Validates reporting of battery stats based on different events
 */
public class BatteryStatsValidationTest extends ProtoDumpTestCase {
    private static final String TAG = "BatteryStatsValidationTest";

    private static final String DEVICE_SIDE_TEST_APK = "CtsBatteryStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE
            = "com.android.server.cts.device.batterystats";
    private static final String DEVICE_SIDE_JOB_COMPONENT
            = "com.android.server.cts.device.batterystats/.SimpleJobService";
    private static final String DEVICE_SIDE_SYNC_COMPONENT
            = "com.android.server.cts.device.batterystats.provider/"
            + "com.android.server.cts.device.batterystats";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Uninstall to clear the history in case it's still on the device.
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
    }

    /** Smallest possible HTTP header. */
    private static final int MIN_HTTP_HEADER_BYTES = 26;

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);

        batteryOffScreenOn();
        super.tearDown();
    }

    protected void batteryOnScreenOff() throws Exception {
        getDevice().executeShellCommand("dumpsys battery unplug");
        getDevice().executeShellCommand("dumpsys batterystats enable pretend-screen-off");
    }

    protected void batteryOffScreenOn() throws Exception {
        getDevice().executeShellCommand("dumpsys battery reset");
        getDevice().executeShellCommand("dumpsys batterystats disable pretend-screen-off");
    }

    public void testAlarms() throws Exception {
        batteryOnScreenOff();

        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsAlarmTest", "testAlarms");

        assertValueRange("wua", "*walarm*:com.android.server.cts.device.batterystats.ALARM",
                5, 3, 3);

        batteryOffScreenOn();
    }

    public void testWakeLockDuration() throws Exception {
        batteryOnScreenOff();

        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWakeLockTests",
                "testHoldShortWakeLock");

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWakeLockTests",
                "testHoldLongWakeLock");

        assertValueRange("wl", "BSShortWakeLock", 14, (long) (500 * 0.9), 500 * 2);
        assertValueRange("wl", "BSLongWakeLock", 14, (long) (3000 * 0.9), 3000 * 2);

        batteryOffScreenOn();
    }

    public void testServiceForegroundDuration() throws Exception {
        batteryOnScreenOff();
        installPackage(DEVICE_SIDE_TEST_APK, true);

        getDevice().executeShellCommand(
                "am start -n com.android.server.cts.device.batterystats/.SimpleActivity");
        assertValueRange("st", "", 5, 0, 0); // No foreground service time before test
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsProcessStateTests",
                "testForegroundService");
        assertValueRange("st", "", 5, (long) (2000 * 0.8), 4000);

        batteryOffScreenOn();
    }

    public void testWifiScans() throws Exception {
        batteryOnScreenOff();
        installPackage(DEVICE_SIDE_TEST_APK, true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWifiScanTests",
                "testBackgroundScan");
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWifiScanTests",
                "testForegroundScan");

        assertValueRange("wfl", "", 7, 2, 2);
        batteryOffScreenOn();
    }

    /**
     * Tests whether the on-battery realtime and total realtime values
     * are properly updated in battery stats.
     */
    public void testRealtime() throws Exception {
        batteryOnScreenOff();
        long startingValueRealtime = getLongValue(0, "bt", "", 7);
        long startingValueBatteryRealtime = getLongValue(0, "bt", "", 5);
        // After going on battery
        Thread.sleep(2000);
        batteryOffScreenOn();
        // After going off battery
        Thread.sleep(2000);

        long currentValueRealtime = getLongValue(0, "bt", "", 7);
        long currentValueBatteryRealtime = getLongValue(0, "bt", "", 5);

        // Total realtime increase should be 4000ms at least
        assertTrue(currentValueRealtime >= startingValueRealtime + 4000);
        // But not too much more
        assertTrue(currentValueRealtime < startingValueRealtime + 6000);
        // Battery on realtime should be more than 2000 but less than 4000
        assertTrue(currentValueBatteryRealtime >= startingValueBatteryRealtime + 2000);
        assertTrue(currentValueBatteryRealtime < startingValueBatteryRealtime + 4000);
    }

    /**
     * Tests the total duration reported for jobs run on the job scheduler.
     */
    public void testJobDuration() throws Exception {
        batteryOnScreenOff();

        installPackage(DEVICE_SIDE_TEST_APK, true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsJobDurationTests",
                "testJobDuration");

        // Should be approximately 3000 ms. Use 0.8x and 2x as the lower and upper
        // bounds to account for possible errors due to thread scheduling and cpu load.
        assertValueRange("jb", DEVICE_SIDE_JOB_COMPONENT, 5, (long) (3000 * 0.8), 3000 * 2);
        batteryOffScreenOn();
    }

    /**
     * Tests the total duration and # of syncs reported for sync activities.
     */
    public void testSyncs() throws Exception {
        batteryOnScreenOff();

        installPackage(DEVICE_SIDE_TEST_APK, true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsSyncTest", "testRunSyncs");

        // First, check the count, which should be 10.
        // (It could be 11, if the initial sync actually happened before getting cancelled.)
        assertValueRange("sy", DEVICE_SIDE_SYNC_COMPONENT, 6, 10L, 11L);

        // Should be approximately, but at least 10 seconds. Use 2x as the upper
        // bounds to account for possible errors due to thread scheduling and cpu load.
        assertValueRange("sy", DEVICE_SIDE_SYNC_COMPONENT, 5, 10000, 10000 * 2);
    }

    /**
     * Tests the total bytes reported for downloading over wifi.
     */
    public void testWifiDownload() throws Exception {
        batteryOnScreenOff();
        installPackage(DEVICE_SIDE_TEST_APK, true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWifiTransferTests",
                "testForegroundDownload");
        long foregroundBytes = getDownloadedBytes();
        assertTrue(foregroundBytes > 0);
        long min = foregroundBytes + MIN_HTTP_HEADER_BYTES;
        long max = foregroundBytes + (16 * 1024); // Add some fuzzing.
        assertValueRange("nt", "", 6, min, max); // wifi_bytes_rx
        assertValueRange("nt", "", 11, 1, 40); // wifi_bytes_tx

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWifiTransferTests",
                "testBackgroundDownload");
        long backgroundBytes = getDownloadedBytes();
        assertTrue(backgroundBytes > 0);
        min += backgroundBytes + MIN_HTTP_HEADER_BYTES;
        max += backgroundBytes + (16 * 1024);
        assertValueRange("nt", "", 6, min, max); // wifi_bytes_rx
        assertValueRange("nt", "", 11, 2, 80); // wifi_bytes_tx

        batteryOffScreenOn();
    }

    /**
     * Tests the total bytes reported for uploading over wifi.
     */
    public void testWifiUpload() throws Exception {
        batteryOnScreenOff();
        installPackage(DEVICE_SIDE_TEST_APK, true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWifiTransferTests",
                "testForegroundUpload");
        int min = MIN_HTTP_HEADER_BYTES + (2 * 1024);
        int max = min + (6 * 1024); // Add some fuzzing.
        assertValueRange("nt", "", 7, min, max); // wifi_bytes_tx

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".BatteryStatsWifiTransferTests",
                "testBackgroundUpload");
        assertValueRange("nt", "", 7, min * 2, max * 2); // wifi_bytes_tx

        batteryOffScreenOn();
    }

    /**
     * Verifies that the recorded time for the specified tag and name in the test package
     * is within the specified range.
     */
    private void assertValueRange(String tag, String optionalAfterTag,
            int index, long min, long max) throws Exception {
        String uidLine = getDevice().executeShellCommand("cmd package list packages -U "
                + DEVICE_SIDE_TEST_PACKAGE);
        String[] uidLineParts = uidLine.split(":");
        // 3rd entry is package uid
        assertTrue(uidLineParts.length > 2);
        int uid = Integer.parseInt(uidLineParts[2].trim());
        assertTrue(uid > 10000);

        long value = getLongValue(uid, tag, optionalAfterTag, index);

        assertTrue("Value " + value + " is less than min " + min, value >= min);
        assertTrue("Value " + value + " is greater than max " + max, value <= max);
    }

    /**
     * Returns a particular long value from a line matched by uid, tag and the optionalAfterTag.
     */
    private long getLongValue(int uid, String tag, String optionalAfterTag, int index)
            throws Exception {
        String dumpsys = getDevice().executeShellCommand("dumpsys batterystats --checkin");
        String[] lines = dumpsys.split("\n");
        long value = 0;
        if (optionalAfterTag == null) {
            optionalAfterTag = "";
        }
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];
            if (line.contains(uid + ",l," + tag + "," + optionalAfterTag)
                    || (!optionalAfterTag.equals("") &&
                        line.contains(uid + ",l," + tag + ",\"" + optionalAfterTag))) {
                String[] wlParts = line.split(",");
                value = Long.parseLong(wlParts[index]);
            }
        }
        return value;
    }

    /**
     * Returns the bytes downloaded for the wifi transfer download tests.
     */
    private long getDownloadedBytes() throws Exception {
        String log = getDevice().executeShellCommand(
                "logcat -d -s BatteryStatsWifiTransferTests -e '\\d+'");
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String[] parts = lines[i].split(":");
            String num = parts[parts.length - 1].trim();
            if (num.matches("\\d+")) {
                return Integer.parseInt(num);
            }
        }
        return 0;
    }
}
