/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.util.Range;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.DateTimeException;

@RunWith(AndroidJUnit4.class)
public class SystemClockSntpTest {
    // Mocked server response. Refer to SntpClientTest.
    private static final String MOCKED_NTP_RESPONSE =
            "240206ec"
                    + "00000165"
                    + "000000b2"
                    + "ddfd4729"
                    + "d9ca9446820a5000"
                    + "d9ca9451938a3771"
                    + "d9ca945194bd3fff"
                    + "d9ca945194bd4001";
    private static long MOCKED_NTP_TIMESTAMP = 1444943313585L; // 2015-10-15 21:08:33 UTC
    private static long TEST_NTP_TIMEOUT_MILLIS = 300L;

    private SntpTestServer mServer;

    @After
    public void tearDown() {
        // Restore NTP server configurations.
        executeShellCommand("cmd network_time_update_service set_server_config");
    }

    @Test
    public void testCurrentNetworkTimeClock() throws Exception {
        // Start a local SNTP test server. But does not setup a fake response.
        // So the server will not reply to any request.
        runWithShellPermissionIdentity(() -> mServer = new SntpTestServer());

        // Write test server address into settings.
        executeShellCommand(
                "cmd network_time_update_service set_server_config --hostname "
                        + mServer.getAddress().getHostAddress()
                        + " --port " + mServer.getPort()
                        + " --timeout_millis " + TEST_NTP_TIMEOUT_MILLIS);

        // Clear current NTP value and verify it throws exception.
        executeShellCommand("cmd network_time_update_service clear_time");

        // Verify the case where the device hasn't made an NTP request yet.
        assertThrows(DateTimeException.class, () -> SystemClock.currentNetworkTimeClock().millis());

        // Trigger NtpTrustedTime refresh with the new command.
        executeShellCommandAndAssertOutput(
                "cmd network_time_update_service force_refresh", "false");

        // Verify the returned clock throws since there is still no previous NTP fix.
        assertThrows(DateTimeException.class, () -> SystemClock.currentNetworkTimeClock().millis());

        // Setup fake responses (Refer to SntpClientTest). And trigger NTP refresh.
        mServer.setServerReply(HexEncoding.decode(MOCKED_NTP_RESPONSE));

        // After force_refresh, network_time_update_service should have associated
        // MOCKED_NTP_TIMESTAMP with an elapsedRealtime() value between
        // beforeRefreshElapsedMillis and afterRefreshElapsedMillis.
        final long beforeRefreshElapsedMillis = SystemClock.elapsedRealtime();
        executeShellCommandAndAssertOutput("cmd network_time_update_service force_refresh", "true");
        final long afterRefreshElapsedMillis = SystemClock.elapsedRealtime();

        // Request the current Unix epoch time. Assert value of SystemClock#currentNetworkTimeClock.
        assertCurrentNetworkTimeClockInBounds(MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis,
                afterRefreshElapsedMillis);

        // Simulate some time passing and verify that SystemClock returns an updated time
        // using the same NTP signal.
        final long PASSED_DURATION_MS = 100L;
        Thread.sleep(PASSED_DURATION_MS);

        // Request the current Unix epoch time again. Verify that SystemClock returns an
        // updated time using the same NTP signal.
        assertCurrentNetworkTimeClockInBounds(MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis,
                afterRefreshElapsedMillis);

        // Remove fake server response and trigger NTP refresh to simulate a failed refresh.
        mServer.setServerReply(null);
        executeShellCommandAndAssertOutput(
                "cmd network_time_update_service force_refresh", "false");

        // Verify that SystemClock still returns an updated time using the same NTP signal.
        assertCurrentNetworkTimeClockInBounds(MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis,
                afterRefreshElapsedMillis);
    }

    private static void executeShellCommand(String command) {
        executeShellCommandAndAssertOutput(command, null);
    }

    private static void executeShellCommandAndAssertOutput(
            String command, String expectedOutput) {
        final String trimmedResult = runShellCommand(command).trim();
        if (expectedOutput != null) {
            assertEquals(expectedOutput, trimmedResult);
        }
    }

    private static void runWithShellPermissionIdentity(ThrowingRunnable command)
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            command.run();
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /** Verify the given value is in range [lower, upper] */
    private static void assertInRange(String tag, long value, long lower, long upper) {
        final Range range = new Range(lower, upper);
        assertTrue(tag + ": " + value + " is not within range [" + lower + ", " + upper + "]",
                range.contains(value));
    }

    private static void assertCurrentNetworkTimeClockInBounds(long expectedTimestamp,
            long beforeRefreshElapsedMillis, long afterRefreshElapsedMillis) {
        final long beforeQueryElapsedMillis = SystemClock.elapsedRealtime();
        final long networkEpochMillis = SystemClock.currentNetworkTimeClock().millis();
        final long afterQueryElapsedMillis = SystemClock.elapsedRealtime();

        // Calculate the lower/upper bound base on the elapsed time of refreshing.
        final long lowerBoundNetworkEpochMillis =
                expectedTimestamp + (beforeQueryElapsedMillis - afterRefreshElapsedMillis);
        final long upperBoundNetworkEpochMillis =
                expectedTimestamp + (afterQueryElapsedMillis - beforeRefreshElapsedMillis);
        assertInRange("Network time", networkEpochMillis, lowerBoundNetworkEpochMillis,
                upperBoundNetworkEpochMillis);
    }
}
