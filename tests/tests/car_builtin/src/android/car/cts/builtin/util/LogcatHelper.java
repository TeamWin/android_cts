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

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public final class LogcatHelper {

    private LogcatHelper() {}

    /**
     * Asserts if a message appears in logcat messages within given timeout.
     *
     * @param match to find in the logcat messages
     * @param timeout for waiting the message
     */
    public static void assertLogcatMessage(String match, int timeout) {
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
                    return;
                }
                if ((SystemClock.elapsedRealtime() - startTime) > timeout) {
                    fail("match" + match + " was not found, Timeout: " + timeout + " ms");
                }
            }
        } catch (IOException e) {
            fail("match was not found, IO exception: " + e);
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
