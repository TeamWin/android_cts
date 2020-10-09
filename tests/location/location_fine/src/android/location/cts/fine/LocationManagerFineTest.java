/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.location.cts.fine;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.location.LocationManager.EXTRA_PROVIDER_ENABLED;
import static android.location.LocationManager.EXTRA_PROVIDER_NAME;
import static android.location.LocationManager.FUSED_PROVIDER;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;
import static android.location.LocationManager.PROVIDERS_CHANGED_ACTION;
import static android.os.PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF;
import static android.os.PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF;
import static android.os.PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF;
import static android.provider.Settings.Global.BATTERY_SAVER_CONSTANTS;
import static android.provider.Settings.Global.LOCATION_IGNORE_SETTINGS_PACKAGE_WHITELIST;

import static androidx.test.ext.truth.content.IntentSubject.assertThat;
import static androidx.test.ext.truth.location.LocationSubject.assertThat;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.LocationUtils.createLocation;
import static com.android.compatibility.common.util.SettingsUtils.NAMESPACE_GLOBAL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.GnssAntennaInfo;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.LocationRequest;
import android.location.OnNmeaMessageListener;
import android.location.cts.common.BroadcastCapture;
import android.location.cts.common.GetCurrentLocationCapture;
import android.location.cts.common.LocationListenerCapture;
import android.location.cts.common.LocationPendingIntentCapture;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.LocationUtils;
import com.android.compatibility.common.util.ScreenUtils;
import com.android.compatibility.common.util.ScreenUtils.ScreenResetter;
import com.android.compatibility.common.util.SettingsUtils;
import com.android.compatibility.common.util.SettingsUtils.SettingResetter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class LocationManagerFineTest {

    private static final String TAG = "LocationManagerFineTest";

    private static final long TIMEOUT_MS = 5000;
    private static final long FAILURE_TIMEOUT_MS = 200;

    private static final String TEST_PROVIDER = "test_provider";

    private Random mRandom;
    private Context mContext;
    private LocationManager mManager;

    @Before
    public void setUp() throws Exception {
        LocationUtils.registerMockLocationProvider(getInstrumentation(),
                true);

        long seed = System.currentTimeMillis();
        Log.i(TAG, "location random seed: " + seed);

        mRandom = new Random(seed);
        mContext = ApplicationProvider.getApplicationContext();
        mManager = Objects.requireNonNull(mContext.getSystemService(LocationManager.class));

        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }

        mManager.addTestProvider(TEST_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(TEST_PROVIDER, true);
    }

    @After
    public void tearDown() throws Exception {
        if (mManager != null) {
            for (String provider : mManager.getAllProviders()) {
                mManager.removeTestProvider(provider);
            }
        }

        LocationUtils.registerMockLocationProvider(getInstrumentation(),
                false);
    }

    @Test
    public void testIsLocationEnabled() {
        assertThat(mManager.isLocationEnabled()).isTrue();
    }

    @Test
    public void testIsProviderEnabled() {
        assertThat(mManager.isProviderEnabled(TEST_PROVIDER)).isTrue();

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertThat(mManager.isProviderEnabled(TEST_PROVIDER)).isFalse();

        mManager.setTestProviderEnabled(TEST_PROVIDER, true);
        assertThat(mManager.isProviderEnabled(TEST_PROVIDER)).isTrue();

        try {
            mManager.isProviderEnabled(null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetLastKnownLocation() {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isEqualTo(loc1);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isEqualTo(loc2);

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isNull();

        try {
            mManager.getLastKnownLocation(null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetLastKnownLocation_RemoveProvider() {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
        mManager.removeTestProvider(TEST_PROVIDER);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isNull();
    }

    @Test
    public void testGetCurrentLocation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getLocation(TIMEOUT_MS)).isEqualTo(loc);
        }

        try {
            mManager.getCurrentLocation((String) null, null, Executors.newSingleThreadExecutor(),
                    (location) -> {});
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetCurrentLocation_FreshOldLocation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc);
        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            assertThat(capture.getLocation(TIMEOUT_MS)).isEqualTo(loc);
        }
    }

    @Test
    public void testGetCurrentLocation_DirectExecutor() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Runnable::run, capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getLocation(TIMEOUT_MS)).isEqualTo(loc);
        }
    }

    @Test
    public void testGetCurrentLocation_Cancellation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            capture.getCancellationSignal().cancel();
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.hasLocation(FAILURE_TIMEOUT_MS)).isFalse();
        }
    }

    @Test
    public void testGetCurrentLocation_ProviderDisabled() throws Exception {
        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            assertThat(capture.getLocation(FAILURE_TIMEOUT_MS)).isNull();
        }

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.removeUpdates(capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
        }

        try {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, null, Looper.getMainLooper());
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, null, capture);
            fail("Should throw IllegalArgumentException if executor is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(null, 0, 0, capture, Looper.getMainLooper());
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.removeUpdates((LocationListener) null);
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestLocationUpdates_PendingIntent() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.removeUpdates(capture.getPendingIntent());

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
        }

        try {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, (PendingIntent) null);
            fail("Should throw IllegalArgumentException if pending intent is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(null, 0, 0, capture.getPendingIntent());
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.removeUpdates((PendingIntent) null);
            fail("Should throw IllegalArgumentException if pending intent is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestLocationUpdates_DirectExecutor() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, Runnable::run, capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    public void testRequestLocationUpdates_Looper() throws Exception {
        HandlerThread thread = new HandlerThread("locationTestThread");
        thread.start();
        Looper looper = thread.getLooper();
        try {

            Location loc1 = createLocation(TEST_PROVIDER, mRandom);
            Location loc2 = createLocation(TEST_PROVIDER, mRandom);

            try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
                mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture, looper);

                mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
                assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
                mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
                assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
                mManager.setTestProviderEnabled(TEST_PROVIDER, false);
                assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
                mManager.setTestProviderEnabled(TEST_PROVIDER, true);
                assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);
            }

        } finally {
            looper.quit();
        }
    }

    @Test
    public void testRequestLocationUpdates_Criteria() throws Exception {
        // criteria API will always use the fused provider...
        mManager.addTestProvider(FUSED_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);

        Location loc1 = createLocation(FUSED_PROVIDER, mRandom);
        Location loc2 = createLocation(FUSED_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(0, 0, criteria, Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(FUSED_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(FUSED_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(FUSED_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.removeUpdates(capture);

            mManager.setTestProviderLocation(FUSED_PROVIDER, loc1);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(FUSED_PROVIDER, false);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
        }


        try {
            mManager.requestLocationUpdates(0, 0, criteria, null, Looper.getMainLooper());
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(0, 0, criteria, null, capture);
            fail("Should throw IllegalArgumentException if executor is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(0, 0, null, Executors.newSingleThreadExecutor(), capture);
            fail("Should throw IllegalArgumentException if criteria is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestLocationUpdates_ReplaceRequest() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 1000, 1000, (runnable) -> {}, capture);
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
        }
    }

    @Test
    public void testRequestLocationUpdates_NumUpdates() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(0).setMaxUpdates(1).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_MinUpdateInterval() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(5000).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_MinUpdateDistance() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 0, 0, 10);
        Location loc2 = createLocation(TEST_PROVIDER, 0, 1, 10);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(0).setMinUpdateDistanceMeters(200000).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_BatterySaver_GpsDisabledScreenOff() throws Exception {
        PowerManager powerManager = Objects.requireNonNull(
                mContext.getSystemService(PowerManager.class));

        mManager.addTestProvider(GPS_PROVIDER,
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_HIGH,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(GPS_PROVIDER, true);

        LocationRequest request = new LocationRequest.Builder(0).build();

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext);
             ScreenResetter ignored = new ScreenResetter()) {
            mManager.requestLocationUpdates(GPS_PROVIDER, request,
                    Executors.newSingleThreadExecutor(), capture);
            mManager.requestLocationUpdates(TEST_PROVIDER, request,
                    Executors.newSingleThreadExecutor(), capture);

            SettingsUtils.set(NAMESPACE_GLOBAL, BATTERY_SAVER_CONSTANTS,
                    "gps_mode=1");
            BatteryUtils.runDumpsysBatteryUnplug();
            BatteryUtils.enableBatterySaver(true);
            assertThat(powerManager.getLocationPowerSaveMode()).isEqualTo(
                    LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF);

            // check screen off behavior
            ScreenUtils.setScreenOn(false);
            assertFalse(powerManager.isInteractive());
            Location loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(GPS_PROVIDER, loc);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc);

            // check screen on behavior
            ScreenUtils.setScreenOn(true);
            assertTrue(powerManager.isInteractive());
            loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(GPS_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc);
        } finally {
            BatteryUtils.enableBatterySaver(false);
            BatteryUtils.runDumpsysBatteryReset();
        }
    }

    @Test
    public void testRequestLocationUpdates_BatterySaver_AllDisabledScreenOff() throws Exception {
        PowerManager powerManager = Objects.requireNonNull(
                mContext.getSystemService(PowerManager.class));

        LocationRequest request = new LocationRequest.Builder(0).build();

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext);
             ScreenResetter ignored = new ScreenResetter()) {
            mManager.requestLocationUpdates(TEST_PROVIDER, request,
                    Executors.newSingleThreadExecutor(), capture);

            SettingsUtils.set(NAMESPACE_GLOBAL, BATTERY_SAVER_CONSTANTS,
                    "gps_mode=2");
            BatteryUtils.runDumpsysBatteryUnplug();
            BatteryUtils.enableBatterySaver(true);
            assertThat(powerManager.getLocationPowerSaveMode()).isEqualTo(
                    LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF);

            // check screen off behavior
            ScreenUtils.setScreenOn(false);
            assertFalse(powerManager.isInteractive());
            mManager.setTestProviderLocation(TEST_PROVIDER, createLocation(TEST_PROVIDER, mRandom));
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();

            // check screen on behavior
            ScreenUtils.setScreenOn(true);
            assertTrue(powerManager.isInteractive());
            Location loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc);
        } finally {
            BatteryUtils.enableBatterySaver(false);
            BatteryUtils.runDumpsysBatteryReset();
        }
    }

    @Test
    public void testRequestLocationUpdates_BatterySaver_ThrottleScreenOff() throws Exception {
        PowerManager powerManager = Objects.requireNonNull(
                mContext.getSystemService(PowerManager.class));

        LocationRequest request = new LocationRequest.Builder(0).build();

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext);
             ScreenResetter ignored = new ScreenResetter()) {
            mManager.requestLocationUpdates(TEST_PROVIDER, request,
                    Executors.newSingleThreadExecutor(), capture);

            SettingsUtils.set(NAMESPACE_GLOBAL, BATTERY_SAVER_CONSTANTS,
                    "gps_mode=4");
            BatteryUtils.runDumpsysBatteryUnplug();
            BatteryUtils.enableBatterySaver(true);
            assertThat(powerManager.getLocationPowerSaveMode()).isEqualTo(
                    LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF);

            // check screen off behavior
            ScreenUtils.setScreenOn(false);
            assertFalse(powerManager.isInteractive());
            mManager.setTestProviderLocation(TEST_PROVIDER, createLocation(TEST_PROVIDER, mRandom));
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();

            // check screen on behavior
            ScreenUtils.setScreenOn(true);
            assertTrue(powerManager.isInteractive());
            Location loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc);
        } finally {
            BatteryUtils.enableBatterySaver(false);
            BatteryUtils.runDumpsysBatteryReset();
        }
    }

    @Test
    public void testRequestLocationUpdates_LocationSettingsIgnored() throws Exception {
        try (LocationListenerCapture capture = new LocationListenerCapture(mContext);
             ScreenResetter ignored1 = new ScreenResetter();
             SettingResetter ignored2 = new SettingResetter(NAMESPACE_GLOBAL,
                     LOCATION_IGNORE_SETTINGS_PACKAGE_WHITELIST, mContext.getPackageName())) {

            getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(WRITE_SECURE_SETTINGS);
            try {
                mManager.requestLocationUpdates(
                        TEST_PROVIDER,
                        new LocationRequest.Builder(0)
                                .setLocationSettingsIgnored(true)
                                .build(),
                        Executors.newSingleThreadExecutor(),
                        capture);
            } finally {
                getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }

            // turn off provider
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);

            // enable battery saver throttling
            SettingsUtils.set(NAMESPACE_GLOBAL, BATTERY_SAVER_CONSTANTS,
                    "gps_mode=4");
            BatteryUtils.runDumpsysBatteryUnplug();
            BatteryUtils.enableBatterySaver(true);
            ScreenUtils.setScreenOn(false);

            // test that all restrictions are bypassed
            Location loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isEqualTo(loc);
            loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isEqualTo(loc);
        } finally {
            BatteryUtils.enableBatterySaver(false);
            BatteryUtils.runDumpsysBatteryReset();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't hold ACCESS_LOCATION_EXTRA_COMMANDS permission")
    public void testRequestGpsUpdates_B9758659() throws Exception {
        assumeTrue(hasGpsFeature());

        // test for b/9758659, where the gps provider may reuse network provider positions creating
        // an unnatural feedback loop
        assertThat(mManager.isProviderEnabled(GPS_PROVIDER)).isTrue();

        Location networkLocation = createLocation(NETWORK_PROVIDER, mRandom);

        mManager.addTestProvider(NETWORK_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_COARSE);
        mManager.setTestProviderEnabled(NETWORK_PROVIDER, true);
        mManager.setTestProviderLocation(NETWORK_PROVIDER, networkLocation);

        // reset gps provider to give it a cold start scenario
        mManager.sendExtraCommand(GPS_PROVIDER, "delete_aiding_data", null);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    GPS_PROVIDER,
                    new LocationRequest.Builder(0).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            Location location = capture.getNextLocation(TIMEOUT_MS);
            if (location != null) {
                assertThat(location.distanceTo(networkLocation)).isGreaterThan(1000.0f);
            }
        }
    }

    @Test
    public void testListenProviderEnable_Listener() throws Exception {
        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(false);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(true);

            mManager.removeUpdates(capture);

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testListenProviderEnable_PendingIntent() throws Exception {
        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(false);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(true);

            mManager.removeUpdates(capture.getPendingIntent());

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can only receive whitelisted broadcasts")
    public void testListenProviderEnable_Broadcast() throws Exception {
        try (BroadcastCapture capture = new BroadcastCapture(mContext, PROVIDERS_CHANGED_ACTION)) {
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            Intent broadcast = capture.getNextIntent(TIMEOUT_MS);
            assertThat(broadcast).isNotNull();
            assertThat(broadcast).hasAction(PROVIDERS_CHANGED_ACTION);
            assertThat(broadcast).extras().string(EXTRA_PROVIDER_NAME).isEqualTo(TEST_PROVIDER);
            assertThat(broadcast).extras().bool(EXTRA_PROVIDER_ENABLED).isFalse();

            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            broadcast = capture.getNextIntent(TIMEOUT_MS);
            assertThat(broadcast).isNotNull();
            assertThat(broadcast).hasAction(PROVIDERS_CHANGED_ACTION);
            assertThat(broadcast).extras().string(EXTRA_PROVIDER_NAME).isEqualTo(TEST_PROVIDER);
            assertThat(broadcast).extras().bool(EXTRA_PROVIDER_ENABLED).isTrue();
        }
    }

    @Test
    public void testGetAllProviders() {
        List<String> providers = mManager.getAllProviders();
        if (hasGpsFeature()) {
            assertThat(providers.contains(LocationManager.GPS_PROVIDER)).isTrue();
        }
        assertThat(providers.contains(PASSIVE_PROVIDER)).isTrue();
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();
        assertThat(providers.size()).isEqualTo(new HashSet<>(providers).size());

        mManager.removeTestProvider(TEST_PROVIDER);

        providers = mManager.getAllProviders();
        assertThat(providers.contains(PASSIVE_PROVIDER)).isTrue();
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();
    }

    @Test
    public void testGetProviders() {
        List<String> providers = mManager.getProviders(false);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        providers = mManager.getProviders(true);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);

        providers = mManager.getProviders(false);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        providers = mManager.getProviders(true);
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();
    }

    @Test
    public void testGetProviders_Criteria() {
        Criteria criteria = new Criteria();

        List<String> providers = mManager.getProviders(criteria, false);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        providers = mManager.getProviders(criteria, true);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        criteria.setPowerRequirement(Criteria.POWER_LOW);

        providers = mManager.getProviders(criteria, false);
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();

        providers = mManager.getProviders(criteria, true);
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();
    }

    @Test
    public void testGetBestProvider() {
        List<String> allProviders = mManager.getAllProviders();
        Criteria criteria = new Criteria();

        String bestProvider = mManager.getBestProvider(criteria, false);
        if (allProviders.contains(GPS_PROVIDER)) {
            assertThat(bestProvider).isEqualTo(GPS_PROVIDER);
        } else if (allProviders.contains(NETWORK_PROVIDER)) {
            assertThat(bestProvider).isEqualTo(NETWORK_PROVIDER);
        } else {
            assertThat(bestProvider).isEqualTo(TEST_PROVIDER);
        }

        // the "perfect" provider - this test case only works if there is no real provider on the
        // device with the same "perfect" properties
        mManager.addTestProvider(TEST_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE);

        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        assertThat(mManager.getBestProvider(criteria, false)).isEqualTo(TEST_PROVIDER);

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertThat(mManager.getBestProvider(criteria, true)).isNotEqualTo(TEST_PROVIDER);
    }

    @Test
    public void testGetProvider() {
        LocationProvider provider = mManager.getProvider(TEST_PROVIDER);
        assertThat(provider).isNotNull();
        assertThat(provider.getName()).isEqualTo(TEST_PROVIDER);

        provider = mManager.getProvider(LocationManager.GPS_PROVIDER);
        if (hasGpsFeature()) {
            assertThat(provider).isNotNull();
            assertThat(provider.getName()).isEqualTo(LocationManager.GPS_PROVIDER);
        } else {
            assertThat(provider).isNull();
        }

        try {
            mManager.getProvider(null);
            fail("Should throw IllegalArgumentException when provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't hold ACCESS_LOCATION_EXTRA_COMMANDS permission")
    public void testSendExtraCommand() {
        for (String provider : mManager.getAllProviders()) {
            boolean res = mManager.sendExtraCommand(provider, "dontCrash", null);
            assertThat(res).isTrue();

            try {
                mManager.sendExtraCommand(provider, null, null);
                fail("Should throw IllegalArgumentException if command is null!");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        try {
            mManager.sendExtraCommand(null, "crash", null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testAddTestProvider() {
        // overwriting providers should not crash
        for (String provider : mManager.getAllProviders()) {
            if (PASSIVE_PROVIDER.equals(provider)) {
                continue;
            }

            mManager.addTestProvider(provider, true,
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    Criteria.POWER_MEDIUM,
                    Criteria.ACCURACY_FINE);
            mManager.setTestProviderLocation(provider, createLocation(provider, mRandom));
        }

        try {
            mManager.addTestProvider("passive",
                    true,
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    Criteria.POWER_MEDIUM,
                    Criteria.ACCURACY_FINE);
            fail("Should throw IllegalArgumentException if provider is passive!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.addTestProvider(null,
                    true,
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    Criteria.POWER_MEDIUM,
                    Criteria.ACCURACY_FINE);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetTestProviderEnabled() {
        for (String provider : mManager.getAllProviders()) {
            if (TEST_PROVIDER.equals(provider)) {
                mManager.setTestProviderEnabled(provider, false);
                assertThat(mManager.isProviderEnabled(provider)).isFalse();
                mManager.setTestProviderEnabled(provider, true);
                assertThat(mManager.isProviderEnabled(provider)).isTrue();
            } else {
                try {
                    mManager.setTestProviderEnabled(provider, false);
                    fail("Should throw IllegalArgumentException since " + provider
                            + " is not a test provider!");
                } catch (IllegalArgumentException e) {
                    // expected
                }
            }
        }

        mManager.removeTestProvider(TEST_PROVIDER);
        try {
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            fail("Should throw IllegalArgumentException since " + TEST_PROVIDER
                    + " is not a test provider!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.setTestProviderEnabled(null, false);
            fail("Should throw IllegalArgumentException since provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetTestProviderLocation() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        for (String provider : mManager.getAllProviders()) {
            if (TEST_PROVIDER.equals(provider)) {
                try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
                    mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                            Executors.newSingleThreadExecutor(), capture);
                    mManager.setTestProviderLocation(provider, loc1);

                    Location received = capture.getNextLocation(TIMEOUT_MS);
                    assertThat(received).isEqualTo(loc1);
                    assertThat(received.isFromMockProvider()).isTrue();
                    assertThat(mManager.getLastKnownLocation(provider)).isEqualTo(loc1);

                    mManager.setTestProviderEnabled(provider, false);
                    mManager.setTestProviderLocation(provider, loc2);
                    assertThat(mManager.getLastKnownLocation(provider)).isNull();
                    assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
                }
            } else {
                try {
                    mManager.setTestProviderLocation(provider, loc1);
                    fail("Should throw IllegalArgumentException since " + provider
                            + " is not a test provider!");
                } catch (IllegalArgumentException e) {
                    // expected
                }
            }
        }

        try {
            mManager.setTestProviderLocation(TEST_PROVIDER, null);
            fail("Should throw IllegalArgumentException since location is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        mManager.removeTestProvider(TEST_PROVIDER);
        try {
            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            fail("Should throw IllegalArgumentException since " + TEST_PROVIDER
                    + " is not a test provider!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.setTestProviderLocation(null, loc1);
            fail("Should throw IllegalArgumentException since provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @SuppressWarnings("TryFailThrowable")
    public void testSetTestProviderLocation_B33091107() throws Exception {
        // test for b/33091107, where a malicious app could fool a real provider into providing a
        // mock location that isn't marked as being mock

        List<String> providers = mManager.getAllProviders();
        if (providers.size() <= 2) {
            // can't perform the test without any real providers, and no need to do so since there
            // are no providers a malicious app could fool
            assertThat(providers.contains(TEST_PROVIDER)).isTrue();
            assertThat(providers.contains(PASSIVE_PROVIDER)).isTrue();
            return;
        }

        providers.remove(TEST_PROVIDER);
        providers.remove(PASSIVE_PROVIDER);

        String realProvider = providers.get(0);
        Location loc = createLocation(realProvider, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);

            Location received = capture.getLocation(TIMEOUT_MS);
            assertThat(received).isEqualTo(loc);
            assertThat(received.isFromMockProvider()).isTrue();

            Location realProvideLocation = mManager.getLastKnownLocation(realProvider);
            if (realProvideLocation != null) {
                boolean passed = false;
                try {
                    assertThat(realProvideLocation).isEqualTo(loc);
                } catch (AssertionError e) {
                    passed = true;
                }
                if (!passed) {
                    fail("real provider saw " + TEST_PROVIDER + " location!");
                }
            }
        }
    }

    @Test
    public void testRemoveTestProvider() {
        // removing providers should not crash
        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }
    }

    @Test
    public void testGetGnssYearOfHardware() {
        assumeTrue(hasGpsFeature());
        mManager.getGnssYearOfHardware();
    }

    @Test
    public void testGetGnssHardwareModelName() {
        assumeTrue(hasGpsFeature());

        // model name should be longer than 4 characters
        String gnssHardwareModelName = mManager.getGnssHardwareModelName();

        // Hardware model name was added in HAL 1.1. HAL 1.0 and earlier do not have this set.
        if (gnssHardwareModelName == null) {
            Log.w(TAG, "gnssHardwareModelName is null. Skipping test.");
            return;
        }
        assertThat(gnssHardwareModelName.length()).isGreaterThan(3);
    }

    @Test
    public void testRegisterGnssStatusCallback() {
        GnssStatus.Callback callback = new GnssStatus.Callback() {
        };

        mManager.registerGnssStatusCallback(Executors.newSingleThreadExecutor(), callback);
        mManager.unregisterGnssStatusCallback(callback);
    }

    @Test
    public void testAddNmeaListener() {
        OnNmeaMessageListener listener = (message, timestamp) -> {
        };

        mManager.addNmeaListener(Executors.newSingleThreadExecutor(), listener);
        mManager.removeNmeaListener(listener);
    }

    @Test
    public void testRegisterGnssMeasurementsCallback() {
        GnssMeasurementsEvent.Callback callback = new GnssMeasurementsEvent.Callback() {
        };

        mManager.registerGnssMeasurementsCallback(Executors.newSingleThreadExecutor(), callback);
        mManager.unregisterGnssMeasurementsCallback(callback);
    }

    @Test
    public void testRegisterGnssAntennaInfoCallback() {
        GnssAntennaInfo.Listener listener = (gnssAntennaInfos) -> {};

        mManager.registerAntennaInfoListener(Executors.newSingleThreadExecutor(), listener);
        mManager.unregisterAntennaInfoListener(listener);
    }

    @Test
    public void testRegisterGnssNavigationMessageCallback() {
        GnssNavigationMessage.Callback callback = new GnssNavigationMessage.Callback() {
        };

        mManager.registerGnssNavigationMessageCallback(Executors.newSingleThreadExecutor(), callback);
        mManager.unregisterGnssNavigationMessageCallback(callback);
    }

    private boolean hasGpsFeature() {
        return mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LOCATION_GPS);
    }
}
