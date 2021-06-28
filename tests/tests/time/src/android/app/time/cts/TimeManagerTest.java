/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.time.cts;

import static android.provider.DeviceConfig.NAMESPACE_SYSTEM_TIME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.app.time.Capabilities;
import android.app.time.ExternalTimeSuggestion;
import android.app.time.TimeManager;
import android.app.time.TimeZoneCapabilities;
import android.app.time.TimeZoneCapabilitiesAndConfig;
import android.app.time.TimeZoneConfiguration;
import android.content.Context;
import android.location.LocationManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.google.common.io.ByteStreams;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** In-process tests for {@link TimeManager} and associated classes. */
public class TimeManagerTest {

    /**
     * This rule adopts the Shell process permissions, needed because MANAGE_TIME_AND_ZONE_DETECTION
     * and SUGGEST_EXTERNAL_TIME required by {@link TimeManager} are privileged permissions.
     */
    @Rule
    public final AdoptShellPermissionsRule shellPermRule = new AdoptShellPermissionsRule();

    private String mOldDeviceConfigSyncDisabledMode;

    @Before
    public void before() throws Exception {
        // This anticipates a future state where a generally applied target preparer may disable
        // device_config sync for all CTS tests: only suspend syncing if it isn't already suspended,
        // and only resume it if this test suspended it.
        mOldDeviceConfigSyncDisabledMode = DeviceConfigShellCommand.getSyncDisabled();
        DeviceConfigShellCommand.setSyncDisabled(
                DeviceConfigShellCommand.SYNC_DISABLED_MODE_UNTIL_REBOOT);
    }

    @After
    public void after() throws Exception {
        DeviceConfigShellCommand.resetToDefaults(
                DeviceConfigShellCommand.RESET_MODE_TRUSTED_DEFAULTS, NAMESPACE_SYSTEM_TIME);
        // Turn syncing back on if this test disabled it.
        DeviceConfigShellCommand.setSyncDisabled(mOldDeviceConfigSyncDisabledMode);
    }

    /**
     * Registers a {@link android.app.time.TimeManager.TimeZoneDetectorListener}, makes changes
     * to the configuration and checks that the listener is called.
     */
    @Test
    public void testManageConfiguration() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        int expectedListenerTriggerCount = 0;
        AtomicInteger listenerTriggerCount = new AtomicInteger(0);
        TimeManager.TimeZoneDetectorListener listener = listenerTriggerCount::incrementAndGet;

        TimeManager timeManager = context.getSystemService(TimeManager.class);
        assertNotNull(timeManager);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            timeManager.addTimeZoneDetectorListener(executor, listener);
            waitForListenerCallbackCount(expectedListenerTriggerCount, listenerTriggerCount);

            TimeZoneCapabilitiesAndConfig capabilitiesAndConfig =
                    timeManager.getTimeZoneCapabilitiesAndConfig();
            waitForListenerCallbackCount(expectedListenerTriggerCount, listenerTriggerCount);

            TimeZoneCapabilities capabilities = capabilitiesAndConfig.getCapabilities();
            TimeZoneConfiguration originalConfig = capabilitiesAndConfig.getConfiguration();

            // Toggle the auto-detection enabled if capabilities allow or try (but expect to fail)
            // if not.
            {
                boolean newAutoDetectionEnabledValue = !originalConfig.isAutoDetectionEnabled();
                TimeZoneConfiguration configUpdate = new TimeZoneConfiguration.Builder()
                        .setAutoDetectionEnabled(newAutoDetectionEnabledValue)
                        .build();
                if (capabilities.getConfigureAutoDetectionEnabledCapability()
                        >= Capabilities.CAPABILITY_NOT_APPLICABLE) {
                    assertTrue(timeManager.updateTimeZoneConfiguration(configUpdate));
                    expectedListenerTriggerCount++;
                    waitForListenerCallbackCount(
                            expectedListenerTriggerCount, listenerTriggerCount);

                    // Reset the config to what it was when the test started.
                    TimeZoneConfiguration resetConfigUpdate = new TimeZoneConfiguration.Builder()
                            .setAutoDetectionEnabled(!newAutoDetectionEnabledValue)
                            .build();
                    assertTrue(timeManager.updateTimeZoneConfiguration(resetConfigUpdate));
                    expectedListenerTriggerCount++;
                } else {
                    assertFalse(timeManager.updateTimeZoneConfiguration(configUpdate));
                }
            }
            waitForListenerCallbackCount(expectedListenerTriggerCount, listenerTriggerCount);

            // Toggle the geo-detection enabled if capabilities allow or try (but expect to fail)
            // if not.
            {
                boolean newGeoDetectionEnabledValue = !originalConfig.isGeoDetectionEnabled();
                TimeZoneConfiguration configUpdate = new TimeZoneConfiguration.Builder()
                        .setGeoDetectionEnabled(newGeoDetectionEnabledValue)
                        .build();
                if (capabilities.getConfigureGeoDetectionEnabledCapability()
                        >= Capabilities.CAPABILITY_NOT_APPLICABLE) {
                    assertTrue(timeManager.updateTimeZoneConfiguration(configUpdate));
                    expectedListenerTriggerCount++;
                    waitForListenerCallbackCount(
                            expectedListenerTriggerCount, listenerTriggerCount);

                    // Reset the config to what it was when the test started.
                    TimeZoneConfiguration resetConfigUpdate = new TimeZoneConfiguration.Builder()
                            .setGeoDetectionEnabled(!newGeoDetectionEnabledValue)
                            .build();
                    assertTrue(timeManager.updateTimeZoneConfiguration(resetConfigUpdate));
                    expectedListenerTriggerCount++;
                } else {
                    assertFalse(timeManager.updateTimeZoneConfiguration(configUpdate));
                }
            }
            waitForListenerCallbackCount(expectedListenerTriggerCount, listenerTriggerCount);
        } finally {
            // Remove the listener. Required otherwise the fuzzy equality rules of lambdas causes
            // problems for later tests.
            timeManager.removeTimeZoneDetectorListener(listener);

            executor.shutdown();
        }
    }

    @Test
    public void externalSuggestions() throws Exception {
        long startCurrentTimeMillis = System.currentTimeMillis();
        long elapsedRealtimeMillis = SystemClock.elapsedRealtime();

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        TimeManager timeManager = context.getSystemService(TimeManager.class);
        assertNotNull(timeManager);

        // Set the time detector to only use ORIGIN_NETWORK. The important aspect is that it isn't
        // ORIGIN_EXTERNAL, and so suggestions from external should be ignored.
        DeviceConfig.setProperty(NAMESPACE_SYSTEM_TIME,
                TimeDetectorServerFlags.KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE,
                TimeDetectorServerFlags.ORIGIN_NETWORK,
                /*makeDefault=*/false);
        sleepForAsyncOperation();

        long suggestion1Millis =
                Instant.ofEpochMilli(startCurrentTimeMillis).plus(1, ChronoUnit.DAYS).toEpochMilli();
        ExternalTimeSuggestion futureTimeSuggestion1 =
                new ExternalTimeSuggestion(elapsedRealtimeMillis, suggestion1Millis);
        long suggestion2Millis =
                Instant.ofEpochMilli(suggestion1Millis).plus(1, ChronoUnit.DAYS).toEpochMilli();
        ExternalTimeSuggestion futureTimeSuggestion2 =
                new ExternalTimeSuggestion(elapsedRealtimeMillis, suggestion2Millis);

        // Suggest a change. It shouldn't be used.
        timeManager.suggestExternalTime(futureTimeSuggestion1);
        sleepForAsyncOperation();

        // The suggestion should have been ignored so the system clock should not have advanced.
        assertTrue(System.currentTimeMillis() < suggestion1Millis);

        // Set the time detector to only use ORIGIN_EXTERNAL.
        // The suggestion should have been stored and acted upon when the origin list changes.
        DeviceConfig.setProperty(NAMESPACE_SYSTEM_TIME,
                TimeDetectorServerFlags.KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE,
                TimeDetectorServerFlags.ORIGIN_EXTERNAL,
                /*makeDefault=*/false);
        sleepForAsyncOperation();
        assertTrue(System.currentTimeMillis() >= suggestion1Millis);

        // Suggest a change. It should be used.
        timeManager.suggestExternalTime(futureTimeSuggestion2);
        sleepForAsyncOperation();
        assertTrue(System.currentTimeMillis() >= suggestion2Millis);

        // Now do our best to return the device to its original state.
        ExternalTimeSuggestion originalTimeSuggestion =
                new ExternalTimeSuggestion(elapsedRealtimeMillis, startCurrentTimeMillis);
        timeManager.suggestExternalTime(
                originalTimeSuggestion);
        sleepForAsyncOperation();

        DeviceConfigShellCommand.resetToDefaults(
                DeviceConfigShellCommand.RESET_MODE_TRUSTED_DEFAULTS, NAMESPACE_SYSTEM_TIME);
    }

    /**
     * Registers a {@link android.app.time.TimeManager.TimeZoneDetectorListener}, makes changes
     * to the "location enabled" setting and checks that the listener is called.
     */
    @Test
    public void testLocationManagerAffectsCapabilities() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        AtomicInteger listenerTriggerCount = new AtomicInteger(0);
        TimeManager.TimeZoneDetectorListener listener = listenerTriggerCount::incrementAndGet;

        TimeManager timeManager = context.getSystemService(TimeManager.class);
        assertNotNull(timeManager);

        LocationManager locationManager = context.getSystemService(LocationManager.class);
        assertNotNull(locationManager);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            timeManager.addTimeZoneDetectorListener(executor, listener);

            // This test does not use waitForListenerCallbackCount() because changing the location
            // enabled setting triggers more than one callback when there's a work profile, so it's
            // easier just to sleep and confirm >= 1 callbacks have been received.
            sleepForAsyncOperation();
            int newCallbackCount = listenerTriggerCount.get();
            assertEquals(0, newCallbackCount);
            int previousCallbackCount = 0;

            UserHandle userHandle = android.os.Process.myUserHandle();
            boolean locationEnabled = locationManager.isLocationEnabledForUser(userHandle);

            locationManager.setLocationEnabledForUser(!locationEnabled, userHandle);
            sleepForAsyncOperation();
            newCallbackCount = listenerTriggerCount.get();
            assertTrue(newCallbackCount > previousCallbackCount);
            previousCallbackCount = newCallbackCount;

            locationManager.setLocationEnabledForUser(locationEnabled, userHandle);
            sleepForAsyncOperation();
            newCallbackCount = listenerTriggerCount.get();
            assertTrue(newCallbackCount > previousCallbackCount);
        } finally {
            // Remove the listener. Required otherwise the fuzzy equality rules of lambdas causes
            // problems for later tests.
            timeManager.removeTimeZoneDetectorListener(listener);

            executor.shutdown();
        }
    }

    private static void waitForListenerCallbackCount(
            int expectedValue, AtomicInteger actualValue) throws Exception {
        // Busy waits up to 30 seconds for the count to reach the expected value.
        final long busyWaitMillis = 30000;
        long targetTimeMillis = System.currentTimeMillis() + busyWaitMillis;
        while (expectedValue != actualValue.get()
                && System.currentTimeMillis() < targetTimeMillis) {
            Thread.sleep(250);
        }
        assertEquals(expectedValue, actualValue.get());
    }

    /**
     * A class for interacting with the {@link DeviceConfig} service via the command line. Some
     * behavior it supports is not available via the Android @SystemApi.
     * See {@link com.android.providers.settings.DeviceConfigService} for the shell command
     * implementation details.
     */
    private static class DeviceConfigShellCommand {

        static final String RESET_MODE_TRUSTED_DEFAULTS = "trusted_defaults";
        static final String SYNC_DISABLED_MODE_NONE = "none";
        static final String SYNC_DISABLED_MODE_UNTIL_REBOOT = "until_reboot";

        private static final String SHELL_CMD_PREFIX = "cmd device_config ";

        private DeviceConfigShellCommand() {}

        static String getSyncDisabled() throws Exception {
            String cmd = SHELL_CMD_PREFIX + "get_sync_disabled_for_tests";
            String result = executeShellCommandInternal(cmd).trim();
            return result;
        }

        static void setSyncDisabled(String syncDisabledMode) throws Exception {
            String cmd = String.format(
                    SHELL_CMD_PREFIX + "set_sync_disabled_for_tests %s", syncDisabledMode);
            executeShellCommandInternal(cmd);
        }

        static void resetToDefaults(String resetMode, String namespace) throws Exception {
            String cmd = String.format(SHELL_CMD_PREFIX + "reset %s %s", resetMode, namespace);
            executeShellCommandInternal(cmd);
        }

        private static String executeShellCommandInternal(String cmd) throws IOException {
            UiAutomation uiAutomation =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation();
            try (FileInputStream output = new FileInputStream(
                    uiAutomation.executeShellCommand(cmd).getFileDescriptor())) {
                return new String(ByteStreams.toByteArray(output));
            }
        }
    }

    /**
     * device_config service flags and values for controlling the time_detector service.
     */
    private interface TimeDetectorServerFlags {
        /**
         * See {@link
         * com.android.server.timedetector.ServerFlags#KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE}
         */
        String KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE =
                "time_detector_origin_priorities_override";

        /**
         * See {@link com.android.server.timedetector.TimeDetectorStrategy#ORIGIN_NETWORK}.
         */
        String ORIGIN_NETWORK = "network";

        /**
         * See {@link com.android.server.timedetector.TimeDetectorStrategy#ORIGIN_EXTERNAL}.
         */
        String ORIGIN_EXTERNAL = "external";
    }

    /**
     * Sleeps for a length of time sufficient to allow async operations to complete. Many time
     * manager APIs are or could be asynchronous and deal with time, so there are no practical
     * alternatives.
     */
    private static void sleepForAsyncOperation() throws Exception{
        Thread.sleep(5_000);
    }
}
