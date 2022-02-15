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

package android.car.cts.builtin.util;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public final class LogcatHelper {

    private static final String TAG = LogcatHelper.class.getSimpleName();

    private static final boolean VERBOSE = false;

    private LogcatHelper() {}

    /**
     * Logcat buffers to search.
     */
    public enum Buffer{
        EVENTS, MAIN, SYSTEM, ALL;
    }

    /**
     * Asserts if a message appears in logcat messages within given timeout. All logcat buffers are
     * searched.
     *
     * @param match to find in the logcat messages
     * @param timeout for waiting the message
     */
    public static void assertLogcatMessage(String match, int timeout) {
        assertLogcatMessage(match, Buffer.ALL, timeout);
    }

    /**
     * Asserts if a message appears in logcat messages within given timeout in the given buffer.
     *
     * @param match to find in the logcat messages
     * @param buffer is logcat buffer to search
     * @param timeout for waiting the message
     */
    public static void assertLogcatMessage(String match, Buffer buffer, int timeout) {
        long startTime = SystemClock.elapsedRealtime();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String command = "logcat -b " + buffer.name().toLowerCase();
        ParcelFileDescriptor output = automation.executeShellCommand(command);
        if (VERBOSE) {
            Log.v(TAG, "ran '" + command + "'; will now look for '" + match + "'");
        }
        FileDescriptor fd = output.getFileDescriptor();
        FileInputStream fileInputStream = new FileInputStream(fd);
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(fileInputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (VERBOSE) {
                    Log.v(TAG, "Checking line '" + line + "'");
                }
                if (line.contains(match)) {
                    if (VERBOSE) {
                        Log.v(TAG, "Found match, returning");
                    }
                    return;
                }
                if ((SystemClock.elapsedRealtime() - startTime) > timeout) {
                    fail("match '" + match + "' was not found, Timeout: " + timeout + " ms");
                }
            }
        } catch (IOException e) {
            fail("match '" + match + "' was not found, IO exception: " + e);
        }
    }

    /**
     * Asserts if a message does not appears in logcat messages within given timeout. If the message
     * appears, then assertion will fail.
     *
     * @param match to find in the logcat messages
     * @param timeout for waiting the message
     */
    public static void assertNoLogcatMessage(String match, int timeout) throws Exception {
        long startTime = SystemClock.elapsedRealtime();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ParcelFileDescriptor output = automation.executeShellCommand("logcat -b all");
        FileDescriptor fd = output.getFileDescriptor();
        FileInputStream fileInputStream = new FileInputStream(fd);
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(fileInputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(match)) {
                    fail("Match was not expected, but found: " + match);
                }
                if ((SystemClock.elapsedRealtime() - startTime) > timeout) {
                    return;
                }
            }
        } catch (IOException e) {
            fail("match was not found, IO exception: " + e);
        }
    }

    /**
     * Clears all logs.
     */
    public static void clearLog() {
        SystemUtil.runShellCommand("logcat -b all -c");
    }
}
