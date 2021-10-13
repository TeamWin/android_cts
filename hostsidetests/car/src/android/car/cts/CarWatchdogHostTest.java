/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.compatibility.common.util.PollingCheck;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CarWatchdogHostTest extends CarHostJUnit4TestCase {
    /**
     * CarWatchdog app package.
     */
    protected static final String WATCHDOG_APP_PKG = "android.car.cts.watchdog.sharedapp";

    /**
     * CarWatchdog app shared user id.
     */
    protected static final String WATCHDOG_APP_SHARED_USER_ID =
            "android.car.cts.uid.watchdog.sharedapp";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String ACTIVITY_CLASS = APP_PKG + ".CarWatchdogTestActivity";

    /**
     * The command to start a custom performance collection with CarWatchdog.
     */
    private static final String START_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --start_perf --max_duration"
                    + " 600 --interval 1";

    /**
     * The command to stop a custom performance collection in CarWatchdog.
     */
    private static final String STOP_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --stop_perf > /dev/null";

    /**
     * The command to reset I/O overuse counters in the adb shell, which clears any previous
     * stats saved by watchdog.
     */
    private static final String RESET_RESOURCE_OVERUSE_CMD = String.format(
            "dumpsys android.automotive.watchdog.ICarWatchdog/default "
                    + "--reset_resource_overuse_stats %s,%s", APP_PKG, WATCHDOG_APP_SHARED_USER_ID);

    /**
     * The command to get I/O overuse foreground bytes threshold in the adb shell.
     */
    private static final String GET_IO_OVERUSE_FOREGROUNG_BYTES_CMD =
            "cmd car_service watchdog-io-get-3p-foreground-bytes";

    /**
     * The command to set I/O overuse foreground bytes threshold in the adb shell.
     */
    private static final String SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD =
            "cmd car_service watchdog-io-set-3p-foreground-bytes";

    private static final long TWO_HUNDRED_MEGABYTES = 1024 * 1024 * 200;

    private static final Pattern DUMP_PATTERN = Pattern.compile(
            "CarWatchdogTestActivity:\\s(.+)");

    private static final Pattern FOREGROUND_BYTES_PATTERN = Pattern.compile(
            "foregroundModeBytes = (\\d+)");

    private static final long POLL_TIMEOUT_MS = 15_000;

    private long mOriginalForegroundBytes;

    @Before
    public void setUp() throws Exception {
        String foregroundBytesDump = executeCommand(GET_IO_OVERUSE_FOREGROUNG_BYTES_CMD);
        mOriginalForegroundBytes = parseForegroundBytesFromMessage(foregroundBytesDump);
        executeCommand("%s %d", SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD, TWO_HUNDRED_MEGABYTES);
        executeCommand("logcat -c");
        executeCommand(START_CUSTOM_PERF_COLLECTION_CMD);
        executeCommand(RESET_RESOURCE_OVERUSE_CMD);
    }

    @After
    public void tearDown() throws Exception {
        executeCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);
        executeCommand("%s %d", SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD, mOriginalForegroundBytes);
    }

    @Test
    public void testCarWatchdog() throws Exception {
        startMainActivity(APP_PKG);

        long remainingBytes = readForegroundBytesFromActivityDump(APP_PKG);
        sendBytesToKillApp(remainingBytes, APP_PKG);

        remainingBytes = readForegroundBytesFromActivityDump(APP_PKG);
        assertWithMessage("Application exceeded I/O overuse threshold")
                .that(remainingBytes).isEqualTo(0);

        verifyTestAppKilled(APP_PKG);
    }

    @Test
    public void testCarWatchdogWithShareUserId() throws Exception {
        startMainActivity(WATCHDOG_APP_PKG);

        long remainingBytes = readForegroundBytesFromActivityDump(WATCHDOG_APP_PKG);
        sendBytesToKillApp(remainingBytes, WATCHDOG_APP_PKG);

        remainingBytes = readForegroundBytesFromActivityDump(WATCHDOG_APP_PKG);
        assertWithMessage("Application exceeded I/O overuse threshold")
                .that(remainingBytes).isEqualTo(0);

        verifyTestAppKilled(WATCHDOG_APP_PKG);
    }

    private long readForegroundBytesFromActivityDump(String packageName) throws Exception {
        AtomicReference<String> notification = new AtomicReference<>();
        PollingCheck.check("Unable to receive notification", POLL_TIMEOUT_MS, () -> {
            String dump = fetchActivityDumpsys(packageName);
            if (dump.startsWith("INFO") && dump.contains("--Notification--")) {
                notification.set(dump);
                return true;
            }
            return false;
        });

        return parseForegroundBytesFromMessage(notification.get());
    }

    private long parseForegroundBytesFromMessage(String message) throws IllegalArgumentException {
        Matcher m = FOREGROUND_BYTES_PATTERN.matcher(message);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        throw new IllegalArgumentException("Invalid message format: " + message);
    }

    private void verifyTestAppKilled(String packageName) throws Exception {
        PollingCheck.check("Unable to kill application", POLL_TIMEOUT_MS, () -> {
            // Check activity dump for errors. Throws exception on error.
            fetchActivityDumpsys(packageName);
            return !isPackageRunning(packageName);
        });
    }

    private String fetchActivityDumpsys(String packageName) throws Exception {
        String dump = executeCommand("dumpsys activity %s/%s", packageName, ACTIVITY_CLASS);
        Matcher m = DUMP_PATTERN.matcher(dump);
        if (!m.find()) {
            return "";
        }
        String message = Objects.requireNonNull(m.group(1)).trim();
        if (message.startsWith("ERROR")) {
            throw new Exception(message);
        }
        return message;
    }

    private void startMainActivity(String packageName) throws Exception {
        String result = executeCommand("pm clear %s", packageName);
        assertWithMessage("pm clear").that(result.trim()).isEqualTo("Success");

        executeCommand("am start -W -a android.intent.action.MAIN -n %s/%s", packageName,
                ACTIVITY_CLASS);

        assertWithMessage("%s is running", packageName)
                .that(isPackageRunning(packageName)).isTrue();
    }

    private void sendBytesToKillApp(long remainingBytes, String appPkg) throws Exception {
        executeCommand(
                "am start -W -a android.intent.action.MAIN -n %s/%s --el bytes_to_kill %d",
                appPkg, ACTIVITY_CLASS, remainingBytes);
    }
}
