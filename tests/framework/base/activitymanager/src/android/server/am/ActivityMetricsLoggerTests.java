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
 * limitations under the License
 */

package android.server.am;

import static android.server.am.Components.REPORT_FULLY_DRAWN_ACTIVITY;
import static android.server.am.Components.TEST_ACTIVITY;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_DELAY_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_DEVICE_UPTIME_SECONDS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_REPORTED_DRAWN;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_REPORTED_DRAWN_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_STARTING_WINDOW_DELAY_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_WINDOWS_DRAWN_DELAY_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.FIELD_CLASS_NAME;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.TYPE_TRANSITION_COLD_LAUNCH;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.TYPE_TRANSITION_REPORTED_DRAWN_NO_BUNDLE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.TYPE_TRANSITION_WARM_LAUNCH;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.ComponentName;
import android.metrics.LogMaker;
import android.metrics.MetricsReader;
import android.os.SystemClock;
import android.support.test.metricshelper.MetricsAsserts;
import android.util.EventLog.Event;

import org.hamcrest.collection.IsIn;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * CTS device tests for {@link com.android.server.am.ActivityMetricsLogger}.
 * Build/Install/Run:
 * atest CtsActivityManagerDeviceTestCases:ActivityMetricsLoggerTests
 */
public class ActivityMetricsLoggerTests extends ActivityManagerTestBase {
    private static final String TAG_AM = "ActivityManager";
    private final MetricsReader mMetricsReader = new MetricsReader();
    private int mPreUptime;
    private LogSeparator mLogSeparator;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mPreUptime = (int) (SystemClock.uptimeMillis() / 1000);
        mMetricsReader.checkpoint(); // clear out old logs
        mLogSeparator = separateLogs(); // add a new separator for logs
    }

    /**
     * Launch an app and verify:
     * - appropriate metrics logs are added
     * - "Displayed activity ..." log is added to logcat
     * - am_activity_launch_time event is generated
     * In all three cases, verify the delay measurements are the same.
     */
    @Test
    public void testAppLaunchIsLogged() {
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(TEST_ACTIVITY)
                .setWaitForLaunched(true)
                .execute();

        LogMaker metricsLog = getMetricsLog(TEST_ACTIVITY, APP_TRANSITION);
        String[] deviceLogs = getDeviceLogsForComponents(mLogSeparator, TAG_AM);
        List<Event> eventLogs = getEventLogsForComponents(mLogSeparator,
                30009 /* AM_ACTIVITY_LAUNCH_TIME */);

        final int postUptime = (int) (SystemClock.uptimeMillis() / 1000);
        assertMetricsLogs(TEST_ACTIVITY, APP_TRANSITION, metricsLog, postUptime);
        final int windowsDrawnDelayMs =
                (int) metricsLog.getTaggedData(APP_TRANSITION_WINDOWS_DRAWN_DELAY_MS);
        final String expectedLog =
                "Displayed " + TEST_ACTIVITY.flattenToShortString()
                        + ": +" + Integer.toString(windowsDrawnDelayMs) + "ms";
        assertLogsContain(deviceLogs, expectedLog);
        assertEventLogsContainsLaunchTime(eventLogs, TEST_ACTIVITY, windowsDrawnDelayMs);
    }

    private void assertMetricsLogs(ComponentName componentName,
            int category, LogMaker log, int postUptime) {
        assertNotNull("did not find the metrics log for: " + componentName
                + " category:" + category, log);
        int startUptime =
                ((Number) log.getTaggedData(APP_TRANSITION_DEVICE_UPTIME_SECONDS)).intValue();
        assertThat("must be either cold or warm launch", log.getType(),
                IsIn.oneOf(TYPE_TRANSITION_COLD_LAUNCH, TYPE_TRANSITION_WARM_LAUNCH));
        assertThat("reported uptime should be after the app was started", startUptime,
                greaterThanOrEqualTo(mPreUptime));
        assertThat("reported uptime should be before assertion time", startUptime,
                lessThanOrEqualTo(postUptime));
        assertNotNull("log should have delay", log.getTaggedData(APP_TRANSITION_DELAY_MS));
        assertEquals("transition should be started because of starting window",
                1 /* APP_TRANSITION_STARTING_WINDOW */, log.getSubtype());
        assertNotNull("log should have starting window delay",
                log.getTaggedData(APP_TRANSITION_STARTING_WINDOW_DELAY_MS));
        assertNotNull("log should have windows drawn delay",
                log.getTaggedData(APP_TRANSITION_WINDOWS_DRAWN_DELAY_MS));
    }

    private void assertEventLogsContainsLaunchTime(List<Event> events, ComponentName componentName,
            int windowsDrawnDelayMs) {
        for (Event event : events) {
            Object[] arr = (Object[]) event.getData();
            assertEquals(4, arr.length);
            final String name = (String) arr[2];
            final int delay = (int) arr[3];
            if (name.equals(componentName.flattenToShortString())) {
                assertEquals("Unexpected windows drawn delay for " + componentName,
                        delay, windowsDrawnDelayMs);
                return;
            }
        }
        fail("Could not find am_activity_launch_time for " + componentName);
    }

    /**
     * Start an activity that reports full drawn and verify:
     * - fully drawn metrics are added to metrics logs
     * - "Fully drawn activity ..." log is added to logcat
     * In both cases verify fully drawn delay measurements are equal.
     * See {@link Activity#reportFullyDrawn()}
     */
    @Test
    public void testAppFullyDrawnReportIsLogged() {
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(REPORT_FULLY_DRAWN_ACTIVITY)
                .setWaitForLaunched(true)
                .execute();

        // Sleep until activity under test has reported drawn (after 500ms)
        SystemClock.sleep(1000);

        LogMaker metricsLog = getMetricsLog(REPORT_FULLY_DRAWN_ACTIVITY,
                APP_TRANSITION_REPORTED_DRAWN);
        String[] deviceLogs = getDeviceLogsForComponents(mLogSeparator, TAG_AM);

        assertNotNull("did not find the metrics log for: " + REPORT_FULLY_DRAWN_ACTIVITY
                + " category:" + APP_TRANSITION_REPORTED_DRAWN, metricsLog);
        assertThat("test activity has a 500ms delay before reporting fully drawn",
                (long) metricsLog.getTaggedData(APP_TRANSITION_REPORTED_DRAWN_MS),
                greaterThanOrEqualTo(500L));
        assertEquals(TYPE_TRANSITION_REPORTED_DRAWN_NO_BUNDLE, metricsLog.getType());

        final long fullyDrawnDelayMs =
                (long) metricsLog.getTaggedData(APP_TRANSITION_REPORTED_DRAWN_MS);
        final String expectedLog =
                "Fully drawn " + REPORT_FULLY_DRAWN_ACTIVITY.flattenToShortString()
                        + ": +" + Long.toString(fullyDrawnDelayMs) + "ms";
        assertLogsContain(deviceLogs, expectedLog);
    }

    /**
     * Launch an activity with wait option and verify that {@link android.app.WaitResult#totalTime}
     * totalTime is set correctly. Make sure the reported value is consistent with value reported to
     * metrics logs.
     */
    @Test
    public void testAppLaunchSetsWaitResultDelayData() {
        final String amStartOutput =
                runShellCommand("am start -S -W " + TEST_ACTIVITY.flattenToShortString());

        LogMaker metricsLog = getMetricsLog(TEST_ACTIVITY, APP_TRANSITION);
        assertNotNull("log should have windows drawn delay", metricsLog);

        final int windowsDrawnDelayMs =
                (int) metricsLog.getTaggedData(APP_TRANSITION_WINDOWS_DRAWN_DELAY_MS);

        assertEquals("Expected a cold launch.", metricsLog.getType(), TYPE_TRANSITION_COLD_LAUNCH);

        assertThat("did not find component in am start output.", amStartOutput,
                containsString(TEST_ACTIVITY.flattenToShortString()));

        assertThat("did not find windows drawn delay time in am start output.", amStartOutput,
                containsString(Integer.toString(windowsDrawnDelayMs)));
    }

    private LogMaker getMetricsLog(ComponentName componentName, int category) {
        final Queue<LogMaker> startLogs = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(category));
        for (LogMaker log : startLogs) {
            final String actualClassName = (String) log.getTaggedData(FIELD_CLASS_NAME);
            final String actualPackageName = log.getPackageName();
            if (componentName.getClassName().equals(actualClassName) &&
                    componentName.getPackageName().equals(actualPackageName)) {
                return log;
            }
        }
        return null;
    }

    private void assertLogsContain(String[] logs, String expectedLog) {
        for (String line : logs) {
            if (line.contains(expectedLog)) {
                return;
            }
        }
        fail("Expected to find '" + expectedLog + "' in " + Arrays.toString(logs));
    }
}
