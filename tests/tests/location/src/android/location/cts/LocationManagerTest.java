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

package android.location.cts;

import static android.location.LocationManager.FUSED_PROVIDER;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.KEY_LOCATION_CHANGED;
import static android.location.LocationManager.KEY_PROVIDER_ENABLED;
import static android.location.LocationManager.KEY_PROXIMITY_ENTERING;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;

import static org.junit.Assert.assertNotEquals;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.LocationRequest;
import android.location.OnNmeaMessageListener;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Requires the permissions
 * android.permission.ACCESS_MOCK_LOCATION to mock provider
 * android.permission.ACCESS_COARSE_LOCATION to access network provider
 * android.permission.ACCESS_FINE_LOCATION to access GPS provider
 * android.permission.ACCESS_LOCATION_EXTRA_COMMANDS to send extra commands to GPS provider
 */
public class LocationManagerTest extends BaseMockLocationTest {

    private static final String TAG = "LocationManagerTest";

    private static final long TIMEOUT_MS = 5000;
    private static final long FAILURE_TIMEOUT_MS = 200;

    private static final String TEST_PROVIDER = "test_provider";

    private Context mContext;
    private LocationManager mManager;

    private static Location createLocation(String provider, double latitude, double longitude) {
        Location location = new Location(provider);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(1.0f);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return location;
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }

    private static void assertLocationEquals(Location expected, Location actual) {
        if (expected == actual) {
            return;
        }

        if (expected == null || actual == null) {
            // gives nicer error message
            assertEquals(expected, actual);
            return;
        }

        assertEquals(expected.getProvider(), actual.getProvider());
        assertEquals(expected.getLatitude(), actual.getLatitude());
        assertEquals(expected.getLongitude(), actual.getLongitude());
        assertEquals(expected.getTime(), actual.getTime());
        assertEquals(expected.getElapsedRealtimeNanos(), actual.getElapsedRealtimeNanos());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        mManager = mContext.getSystemService(LocationManager.class);

        assertNotNull(mManager);

        for (String provider : mManager.getAllProviders()) {
            if (PASSIVE_PROVIDER.equals(provider)) {
                continue;
            }
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

    @Override
    protected void tearDown() throws Exception {
        for (String provider : mManager.getAllProviders()) {
            if (PASSIVE_PROVIDER.equals(provider)) {
                continue;
            }
            mManager.removeTestProvider(provider);
        }

        super.tearDown();
    }

    public void testIsLocationEnabled() {
        assertTrue(mManager.isLocationEnabled());
    }

    public void testIsProviderEnabled() {
        assertTrue(mManager.isProviderEnabled(TEST_PROVIDER));

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertFalse(mManager.isProviderEnabled(TEST_PROVIDER));

        mManager.setTestProviderEnabled(TEST_PROVIDER, true);
        assertTrue(mManager.isProviderEnabled(TEST_PROVIDER));

        try {
            mManager.isProviderEnabled(null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testGetLastKnownLocation() {
        Location loc1 = createLocation(TEST_PROVIDER, 6, 5);
        Location loc2 = createLocation(TEST_PROVIDER, 10, 7);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
        assertLocationEquals(loc1, mManager.getLastKnownLocation(TEST_PROVIDER));

        mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
        assertLocationEquals(loc2, mManager.getLastKnownLocation(TEST_PROVIDER));

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertNull(mManager.getLastKnownLocation(TEST_PROVIDER));

        try {
            mManager.getLastKnownLocation(null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testGetCurrentLocation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, 5, 6);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertLocationEquals(loc, capture.getNextLocation(TIMEOUT_MS));
        }

        // TODO: test timeout case

        try {
            mManager.getCurrentLocation((String) null, null, Executors.newSingleThreadExecutor(),
                    (location) -> {
                    });
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testGetCurrentLocation_DirectExecutor() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, 2, 1);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    directExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertLocationEquals(loc, capture.getNextLocation(TIMEOUT_MS));
        }
    }

    public void testGetCurrentLocation_Cancellation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, 1, 2);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    directExecutor(),
                    capture);
            capture.getCancellationSignal().cancel();
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
        }
    }

    public void testGetCurrentLocation_ProviderDisabled() throws Exception {
        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    directExecutor(),
                    capture);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
        }

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    directExecutor(),
                    capture);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
        }
    }

    public void testRequestLocationUpdates() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 1, 4);
        Location loc2 = createLocation(TEST_PROVIDER, 2, 5);

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertLocationEquals(loc2, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertEquals(Boolean.FALSE, capture.getNextProviderChange(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertEquals(Boolean.TRUE, capture.getNextProviderChange(TIMEOUT_MS));

            mManager.removeUpdates(capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertNull(capture.getNextProviderChange(FAILURE_TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertNull(capture.getNextProviderChange(FAILURE_TIMEOUT_MS));
        }

        try {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, (LocationListener) null);
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, null, capture);
            fail("Should throw IllegalArgumentException if executor is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(null, 0, 0, capture);
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

    public void testRequestLocationUpdates_PendingIntent() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 1, 4);
        Location loc2 = createLocation(TEST_PROVIDER, 2, 5);

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertLocationEquals(loc2, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertEquals(Boolean.FALSE, capture.getNextProviderChange(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertEquals(Boolean.TRUE, capture.getNextProviderChange(TIMEOUT_MS));

            mManager.removeUpdates(capture.getPendingIntent());

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertNull(capture.getNextProviderChange(FAILURE_TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertNull(capture.getNextProviderChange(FAILURE_TIMEOUT_MS));
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

    public void testRequestLocationUpdates_DirectExecutor() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 1, 4);
        Location loc2 = createLocation(TEST_PROVIDER, 2, 5);

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, directExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertLocationEquals(loc2, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertEquals(Boolean.FALSE, capture.getNextProviderChange(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertEquals(Boolean.TRUE, capture.getNextProviderChange(TIMEOUT_MS));
        }
    }

    public void testRequestLocationUpdates_Looper() throws Exception {
        HandlerThread thread = new HandlerThread("locationTestThread");
        thread.start();
        Looper looper = thread.getLooper();
        try {

            Location loc1 = createLocation(TEST_PROVIDER, 1, 4);
            Location loc2 = createLocation(TEST_PROVIDER, 2, 5);

            try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
                mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture, looper);

                mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
                assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
                mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
                assertLocationEquals(loc2, capture.getNextLocation(TIMEOUT_MS));
                mManager.setTestProviderEnabled(TEST_PROVIDER, false);
                assertEquals(Boolean.FALSE, capture.getNextProviderChange(TIMEOUT_MS));
                mManager.setTestProviderEnabled(TEST_PROVIDER, true);
                assertEquals(Boolean.TRUE, capture.getNextProviderChange(TIMEOUT_MS));
            }

        } finally {
            looper.quit();
        }
    }

    public void testRequestLocationUpdates_Criteria() throws Exception {
        // make the test provider the "perfect" provider
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

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);

        Location loc1 = createLocation(TEST_PROVIDER, 1, 4);
        Location loc2 = createLocation(TEST_PROVIDER, 2, 5);

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(0, 0, criteria, directExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertLocationEquals(loc2, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertEquals(Boolean.FALSE, capture.getNextProviderChange(TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertEquals(Boolean.TRUE, capture.getNextProviderChange(TIMEOUT_MS));

            mManager.removeUpdates(capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertNull(capture.getNextProviderChange(FAILURE_TIMEOUT_MS));
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertNull(capture.getNextProviderChange(FAILURE_TIMEOUT_MS));
        }


        try {
            mManager.requestLocationUpdates(0, 0, criteria, null, Looper.getMainLooper());
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(0, 0, criteria, null, capture);
            fail("Should throw IllegalArgumentException if executor is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(0, 0, null, directExecutor(), capture);
            fail("Should throw IllegalArgumentException if criteria is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testRequestLocationUpdates_ReplaceRequest() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 1, 4);
        Location loc2 = createLocation(TEST_PROVIDER, 2, 5);

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 1000, 1000, directExecutor(), capture);
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, directExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertLocationEquals(loc2, capture.getNextLocation(TIMEOUT_MS));
        }
    }

    public void testRequestLocationUpdates_NumUpdates() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 10, 3);
        Location loc2 = createLocation(TEST_PROVIDER, 2, 8);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(TEST_PROVIDER, 0, 0,
                false);
        request.setNumUpdates(1);

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(request, directExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
        }
    }

    public void testRequestLocationUpdates_MinTime() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 0, 0);
        Location loc2 = createLocation(TEST_PROVIDER, 1, 1);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(TEST_PROVIDER, 5000,
                0, false);

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(request, directExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
        }
    }

    public void testRequestLocationUpdates_MinDistance() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 0, 0);
        Location loc2 = createLocation(TEST_PROVIDER, 0, 1);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(TEST_PROVIDER, 0,
                10000, false);

        try (LocationListenerCapture capture = new LocationListenerCapture(mManager)) {
            mManager.requestLocationUpdates(request, directExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertLocationEquals(loc1, capture.getNextLocation(TIMEOUT_MS));
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertNull(capture.getNextLocation(FAILURE_TIMEOUT_MS));
        }
    }

    public void testGetAllProviders() {
        List<String> providers = mManager.getAllProviders();
        if (hasGpsFeature()) {
            assertTrue(providers.contains(LocationManager.GPS_PROVIDER));
        }
        assertTrue(providers.contains(PASSIVE_PROVIDER));
        assertTrue(providers.contains(TEST_PROVIDER));

        mManager.removeTestProvider(TEST_PROVIDER);

        providers = mManager.getAllProviders();
        assertTrue(providers.contains(PASSIVE_PROVIDER));
        assertFalse(providers.contains(TEST_PROVIDER));
    }

    public void testGetProviders() {
        List<String> providers = mManager.getProviders(false);
        assertTrue(providers.contains(TEST_PROVIDER));

        providers = mManager.getProviders(true);
        assertTrue(providers.contains(TEST_PROVIDER));

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);

        providers = mManager.getProviders(false);
        assertTrue(providers.contains(TEST_PROVIDER));

        providers = mManager.getProviders(true);
        assertFalse(providers.contains(TEST_PROVIDER));
    }

    public void testGetProviders_Criteria() {
        Criteria criteria = new Criteria();

        List<String> providers = mManager.getProviders(criteria, false);
        assertTrue(providers.contains(TEST_PROVIDER));

        providers = mManager.getProviders(criteria, true);
        assertTrue(providers.contains(TEST_PROVIDER));

        criteria.setPowerRequirement(Criteria.POWER_LOW);

        providers = mManager.getProviders(criteria, false);
        assertFalse(providers.contains(TEST_PROVIDER));

        providers = mManager.getProviders(criteria, true);
        assertFalse(providers.contains(TEST_PROVIDER));
    }

    public void testGetBestProvider() {
        List<String> allProviders = mManager.getAllProviders();
        Criteria criteria = new Criteria();

        String bestProvider = mManager.getBestProvider(criteria, false);
        if (allProviders.contains(GPS_PROVIDER)) {
            assertEquals(GPS_PROVIDER, bestProvider);
        } else if (allProviders.contains(NETWORK_PROVIDER)) {
            assertEquals(NETWORK_PROVIDER, bestProvider);
        } else {
            assertEquals(TEST_PROVIDER, bestProvider);
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
        assertEquals(TEST_PROVIDER, mManager.getBestProvider(criteria, false));

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertNotEquals(TEST_PROVIDER, mManager.getBestProvider(criteria, true));
    }

    public void testGetProvider() {
        LocationProvider provider = mManager.getProvider(TEST_PROVIDER);
        assertNotNull(provider);
        assertEquals(TEST_PROVIDER, provider.getName());

        provider = mManager.getProvider(LocationManager.GPS_PROVIDER);
        if (hasGpsFeature()) {
            assertNotNull(provider);
            assertEquals(LocationManager.GPS_PROVIDER, provider.getName());
        } else {
            assertNull(provider);
        }

        try {
            mManager.getProvider(null);
            fail("Should throw IllegalArgumentException when provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testSendExtraCommand() {
        for (String provider : mManager.getAllProviders()) {
            boolean res = mManager.sendExtraCommand(provider, "dontCrash", null);
            assertTrue(res);

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
            mManager.setTestProviderLocation(provider, createLocation(provider, 0, 0));
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

    public void testSetTestProviderLocation() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 1, 2);
        Location loc2 = createLocation(TEST_PROVIDER, 2, 1);

        for (String provider : mManager.getAllProviders()) {
            if (TEST_PROVIDER.equals(provider)) {
                try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
                    mManager.getCurrentLocation(provider, capture.getCancellationSignal(),
                            directExecutor(), capture);
                    mManager.setTestProviderLocation(provider, loc1);

                    Location received = capture.getNextLocation(TIMEOUT_MS);
                    assertLocationEquals(loc1, received);
                    assertTrue(received.isFromMockProvider());
                    assertLocationEquals(loc1, mManager.getLastKnownLocation(provider));

                    mManager.setTestProviderEnabled(provider, false);
                    mManager.setTestProviderLocation(provider, loc2);
                    assertNull(mManager.getLastKnownLocation(provider));
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

    public void testSetTestProviderLocation_B33091107() throws Exception {
        // test for b/33091107, where a malicious app could fool a real provider into providing a
        // mock location that isn't marked as being mock

        List<String> providers = mManager.getAllProviders();
        if (providers.size() <= 2) {
            // can't perform the test without any real providers, and no need to do so since there
            // are no providers a malicious app could fool
            assertTrue(providers.contains(TEST_PROVIDER));
            assertTrue(providers.contains(PASSIVE_PROVIDER));
            return;
        }

        providers.remove(TEST_PROVIDER);
        providers.remove(PASSIVE_PROVIDER);

        String realProvider = providers.get(0);
        Location loc = createLocation(realProvider, 2, 2);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    directExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);

            Location received = capture.getNextLocation(TIMEOUT_MS);
            assertLocationEquals(loc, received);
            assertTrue(received.isFromMockProvider());

            Location realProvideLocation = mManager.getLastKnownLocation(realProvider);
            if (realProvideLocation != null) {
                try {
                    assertLocationEquals(loc, realProvideLocation);
                    fail("real provider saw " + TEST_PROVIDER + " location!");
                } catch (AssertionError e) {
                    // pass
                }
            }
        }
    }

    public void testRemoveTestProvider() {
        // removing providers should not crash
        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }
    }

    public void testAddProximityAlert() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }

        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER, createLocation(FUSED_PROVIDER, 30, 30));

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mManager.addProximityAlert(0, 0, 1000, -1, capture.getPendingIntent());

            // adding a proximity alert is asynchronous for no good reason, so we have to wait and
            // hope the alert is added in the mean time.
            Thread.sleep(500);

            mManager.setTestProviderLocation(FUSED_PROVIDER, createLocation(FUSED_PROVIDER, 0, 0));
            assertEquals(Boolean.TRUE, capture.getNextProximityChange(TIMEOUT_MS));

            mManager.setTestProviderLocation(FUSED_PROVIDER,
                    createLocation(FUSED_PROVIDER, 30, 30));
            assertEquals(Boolean.FALSE, capture.getNextProximityChange(TIMEOUT_MS));
        }

        try {
            mManager.addProximityAlert(0, 0, 1000, -1, null);
            fail("Should throw IllegalArgumentException if pending intent is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            try {
                mManager.addProximityAlert(0, 0, 0, -1, capture.getPendingIntent());
                fail("Should throw IllegalArgumentException if radius == 0!");
            } catch (IllegalArgumentException e) {
                // expected
            }

            try {
                mManager.addProximityAlert(0, 0, -1, -1, capture.getPendingIntent());
                fail("Should throw IllegalArgumentException if radius < 0!");
            } catch (IllegalArgumentException e) {
                // expected
            }

            try {
                mManager.addProximityAlert(1000, 1000, 1000, -1, capture.getPendingIntent());
                fail("Should throw IllegalArgumentException if lat/lon are illegal!");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    public void testAddProximityAlert_StartProximate() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }

        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER, createLocation(FUSED_PROVIDER, 0, 0));

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mManager.addProximityAlert(0, 0, 1000, -1, capture.getPendingIntent());
            assertEquals(Boolean.TRUE, capture.getNextProximityChange(TIMEOUT_MS));
        }
    }

    public void testAddProximityAlert_Expires() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }

        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER, createLocation(FUSED_PROVIDER, 30, 30));

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mManager.addProximityAlert(0, 0, 1000, 1, capture.getPendingIntent());

            // adding a proximity alert is asynchronous for no good reason, so we have to wait and
            // hope the alert is added in the mean time.
            Thread.sleep(500);

            mManager.setTestProviderLocation(FUSED_PROVIDER, createLocation(FUSED_PROVIDER, 0, 0));
            assertNull(capture.getNextProximityChange(FAILURE_TIMEOUT_MS));
        }
    }

    public void testGetGnssYearOfHardware() {
        mManager.getGnssYearOfHardware();
    }

    public void testGetGnssHardwareModelName() {
        mManager.getGnssHardwareModelName();
    }

    public void testRegisterGnssStatusCallback() {
        GnssStatus.Callback callback = new GnssStatus.Callback() {
        };

        mManager.registerGnssStatusCallback(directExecutor(), callback);
        mManager.unregisterGnssStatusCallback(callback);
    }

    public void testAddNmeaListener() {
        OnNmeaMessageListener listener = (message, timestamp) -> {
        };

        mManager.addNmeaListener(directExecutor(), listener);
        mManager.removeNmeaListener(listener);
    }

    public void testRegisterGnssMeasurementsCallback() {
        GnssMeasurementsEvent.Callback callback = new GnssMeasurementsEvent.Callback() {
        };

        mManager.registerGnssMeasurementsCallback(directExecutor(), callback);
        mManager.unregisterGnssMeasurementsCallback(callback);
    }

    public void testRegisterGnssNavigationMessageCallback() {
        GnssNavigationMessage.Callback callback = new GnssNavigationMessage.Callback() {
        };

        mManager.registerGnssNavigationMessageCallback(directExecutor(), callback);
        mManager.unregisterGnssNavigationMessageCallback(callback);
    }

    private boolean hasGpsFeature() {
        return mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LOCATION_GPS);
    }

    private boolean isNotSystemUser() {
        return !mContext.getSystemService(UserManager.class).isSystemUser();
    }

    private static class LocationListenerCapture implements LocationListener, AutoCloseable {

        private final LocationManager mLocationManager;
        private final LinkedBlockingQueue<Location> mLocations;
        private final LinkedBlockingQueue<Boolean> mProviderChanges;

        public LocationListenerCapture(LocationManager locationManager) {
            mLocationManager = locationManager;
            mLocations = new LinkedBlockingQueue<>();
            mProviderChanges = new LinkedBlockingQueue<>();
        }

        public Location getNextLocation(long timeoutMs) throws InterruptedException {
            return mLocations.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        public Boolean getNextProviderChange(long timeoutMs) throws InterruptedException {
            return mProviderChanges.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onLocationChanged(Location location) {
            mLocations.add(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
            mProviderChanges.add(true);
        }

        @Override
        public void onProviderDisabled(String provider) {
            mProviderChanges.add(false);
        }

        @Override
        public void close() {
            mLocationManager.removeUpdates(this);
        }
    }

    private static class LocationPendingIntentCapture extends BroadcastReceiver implements
            AutoCloseable {

        private static final String ACTION = "android.location.cts.LOCATION_BROADCAST";
        private static final AtomicInteger sRequestCode = new AtomicInteger(0);

        private final Context mContext;
        private final LocationManager mLocationManager;
        private final PendingIntent mPendingIntent;
        private final LinkedBlockingQueue<Location> mLocations;
        private final LinkedBlockingQueue<Boolean> mProviderChanges;

        public LocationPendingIntentCapture(Context context) {
            mContext = context;
            mLocationManager = context.getSystemService(LocationManager.class);
            mPendingIntent = PendingIntent.getBroadcast(context, sRequestCode.getAndIncrement(),
                    new Intent(ACTION).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_CANCEL_CURRENT);
            mLocations = new LinkedBlockingQueue<>();
            mProviderChanges = new LinkedBlockingQueue<>();

            context.registerReceiver(this, new IntentFilter(ACTION));
        }

        public PendingIntent getPendingIntent() {
            return mPendingIntent;
        }

        /**
         * May not be called from the main thread. Tests do not run on the main thread so this
         * generally shouldn't be a problem.
         */
        public Location getNextLocation(long timeoutMs) throws InterruptedException {
            Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
            return mLocations.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        /**
         * May not be called from the main thread. Tests do not run on the main thread so this
         * generally shouldn't be a problem.
         */
        public Boolean getNextProviderChange(long timeoutMs) throws InterruptedException {
            Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
            return mProviderChanges.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            mLocationManager.removeUpdates(mPendingIntent);
            mContext.unregisterReceiver(this);
            mPendingIntent.cancel();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(KEY_PROVIDER_ENABLED)) {
                mProviderChanges.add(intent.getBooleanExtra(KEY_PROVIDER_ENABLED, false));
            } else if (intent.hasExtra(KEY_LOCATION_CHANGED)) {
                mLocations.add(intent.getParcelableExtra(KEY_LOCATION_CHANGED));
            }
        }
    }

    private static class ProximityPendingIntentCapture extends BroadcastReceiver implements
            AutoCloseable {

        private static final String ACTION = "android.location.cts.LOCATION_BROADCAST";
        private static final AtomicInteger sRequestCode = new AtomicInteger(0);

        private final Context mContext;
        private final LocationManager mLocationManager;
        private final PendingIntent mPendingIntent;
        private final LinkedBlockingQueue<Boolean> mProximityChanges;

        public ProximityPendingIntentCapture(Context context) {
            mContext = context;
            mLocationManager = context.getSystemService(LocationManager.class);
            mPendingIntent = PendingIntent.getBroadcast(context, sRequestCode.getAndIncrement(),
                    new Intent(ACTION).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_CANCEL_CURRENT);
            mProximityChanges = new LinkedBlockingQueue<>();

            context.registerReceiver(this, new IntentFilter(ACTION));
        }

        public PendingIntent getPendingIntent() {
            return mPendingIntent;
        }

        /**
         * May not be called from the main thread. Tests do not run on the main thread so this
         * generally shouldn't be a problem.
         */
        public Boolean getNextProximityChange(long timeoutMs) throws InterruptedException {
            Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
            return mProximityChanges.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            mLocationManager.removeProximityAlert(mPendingIntent);
            mContext.unregisterReceiver(this);
            mPendingIntent.cancel();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(KEY_PROXIMITY_ENTERING)) {
                mProximityChanges.add(intent.getBooleanExtra(KEY_PROXIMITY_ENTERING, false));
            }
        }
    }

    private static class GetCurrentLocationCapture implements Consumer<Location>, AutoCloseable {

        private final CancellationSignal mCancellationSignal;
        private final LinkedBlockingQueue<Location> locations;

        public GetCurrentLocationCapture() {
            locations = new LinkedBlockingQueue<>();
            mCancellationSignal = new CancellationSignal();
        }

        public CancellationSignal getCancellationSignal() {
            return mCancellationSignal;
        }

        public Location getNextLocation(long timeoutMs) throws InterruptedException {
            return locations.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void accept(Location location) {
            locations.add(location);
        }

        @Override
        public void close() {
            mCancellationSignal.cancel();
        }
    }
}
