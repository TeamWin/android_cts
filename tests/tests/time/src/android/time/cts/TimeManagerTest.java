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

package android.time.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.time.TimeManager;
import android.app.time.TimeZoneCapabilities;
import android.app.time.TimeZoneCapabilitiesAndConfig;
import android.app.time.TimeZoneConfiguration;
import android.content.Context;
import android.location.LocationManager;
import android.os.UserHandle;

import org.junit.Rule;
import org.junit.Test;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests for {@link TimeManager} and associated classes. */
public class TimeManagerTest {

    /**
     * This rule adopts the Shell process permissions, needed because MANAGE_TIME_AND_ZONE_DETECTION
     * is a privileged permission.
     */
    @Rule
    public final AdoptShellPermissionsRule shellPermRule = new AdoptShellPermissionsRule();

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
                        >= TimeZoneCapabilities.CAPABILITY_NOT_APPLICABLE) {
                    assertTrue(timeManager.updateTimeZoneConfiguration(configUpdate));
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
                        >= TimeZoneCapabilities.CAPABILITY_NOT_APPLICABLE) {
                    assertTrue(timeManager.updateTimeZoneConfiguration(configUpdate));
                    expectedListenerTriggerCount++;
                } else {
                    assertFalse(timeManager.updateTimeZoneConfiguration(configUpdate));
                }
            }
            waitForListenerCallbackCount(expectedListenerTriggerCount, listenerTriggerCount);

            // Reset the config to what it was when the test started, if needed.
            if (expectedListenerTriggerCount > 0) {
                assertTrue(timeManager.updateTimeZoneConfiguration(originalConfig));
                expectedListenerTriggerCount++;
            }
            waitForListenerCallbackCount(expectedListenerTriggerCount, listenerTriggerCount);
        } finally {
            // Remove the listener. Required otherwise the fuzzy equality rules of lambdas causes
            // problems for later tests.
            timeManager.removeTimeZoneDetectorListener(listener);

            executor.shutdown();
        }
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
            waitForListenerCallbackCount(0, listenerTriggerCount);

            UserHandle userHandle = android.os.Process.myUserHandle();
            boolean locationEnabled = locationManager.isLocationEnabledForUser(userHandle);

            locationManager.setLocationEnabledForUser(!locationEnabled, userHandle);
            waitForListenerCallbackCount(1, listenerTriggerCount);

            locationManager.setLocationEnabledForUser(locationEnabled, userHandle);
            waitForListenerCallbackCount(2, listenerTriggerCount);
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
}
