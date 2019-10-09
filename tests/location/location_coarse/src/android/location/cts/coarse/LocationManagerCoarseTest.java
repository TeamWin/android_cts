/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.location.cts.coarse;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;

import static androidx.test.ext.truth.location.LocationSubject.assertThat;

import static com.android.compatibility.common.util.LocationUtils.createLocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.cts.common.LocationListenerCapture;
import android.location.cts.common.LocationPendingIntentCapture;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.LocationUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class LocationManagerCoarseTest {

    public static final String TAG = "LocationManagerCoarseTest";

    private static final long TIMEOUT_MS = 5000;

    private static final String COARSE_TEST_PROVIDER = "coarse_test_provider";
    private static final String FINE_TEST_PROVIDER = "fine_test_provider";

    private Context mContext;
    private LocationManager mManager;

    @Before
    public void setUp() throws Exception {
        LocationUtils.registerMockLocationProvider(InstrumentationRegistry.getInstrumentation(),
                true);

        mContext = ApplicationProvider.getApplicationContext();
        mManager = mContext.getSystemService(LocationManager.class);

        assertNotNull(mManager);

        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }

        mManager.addTestProvider(COARSE_TEST_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_COARSE);
        mManager.setTestProviderEnabled(COARSE_TEST_PROVIDER, true);

        mManager.addTestProvider(FINE_TEST_PROVIDER,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FINE_TEST_PROVIDER, true);
    }

    @After
    public void tearDown() throws Exception {
        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }

        LocationUtils.registerMockLocationProvider(InstrumentationRegistry.getInstrumentation(),
                false);
    }

    @Test
    public void testGetLastKnownLocation() {
        Location loc = createLocation(COARSE_TEST_PROVIDER, 4, 5);
        loc.setExtraLocation(Location.EXTRA_NO_GPS_LOCATION, new Location(loc));

        mManager.setTestProviderLocation(COARSE_TEST_PROVIDER, loc);
        assertThat(mManager.getLastKnownLocation(COARSE_TEST_PROVIDER)).isNearby(loc, 1000);

        try {
            mManager.getLastKnownLocation(FINE_TEST_PROVIDER);
            fail("Should throw SecurityException for " + FINE_TEST_PROVIDER);
        } catch (SecurityException e) {
            // pass
        }
    }

    @Test
    public void testRequestLocationUpdates() throws Exception {
        Location loc = createLocation(COARSE_TEST_PROVIDER, 6, 2);
        loc.setExtraLocation(Location.EXTRA_NO_GPS_LOCATION, new Location(loc));

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(COARSE_TEST_PROVIDER, 0, 0, directExecutor(), capture);
            mManager.setTestProviderLocation(COARSE_TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isNearby(loc, 1000);
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(FINE_TEST_PROVIDER, 0, 0, directExecutor(), capture);
            fail("Should throw SecurityException for " + FINE_TEST_PROVIDER);
        } catch (SecurityException e) {
            // pass
        }
    }

    @Test
    public void testRequestLocationUpdates_PendingIntent() throws Exception {
        Location loc = createLocation(COARSE_TEST_PROVIDER, 6, 4);
        loc.setExtraLocation(Location.EXTRA_NO_GPS_LOCATION, new Location(loc));

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(COARSE_TEST_PROVIDER, 0, 0, capture.getPendingIntent());
            mManager.setTestProviderLocation(COARSE_TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isNearby(loc, 1000);
        }

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(FINE_TEST_PROVIDER, 0, 0, capture.getPendingIntent());
            fail("Should throw SecurityException for " + FINE_TEST_PROVIDER);
        } catch (SecurityException e) {
            // pass
        }
    }

    @Test
    public void testGetProviders() {
        List<String> providers = mManager.getProviders(false);
        assertTrue(providers.contains(COARSE_TEST_PROVIDER));
        assertFalse(providers.contains(FINE_TEST_PROVIDER));
        assertFalse(providers.contains(GPS_PROVIDER));
        assertFalse(providers.contains(PASSIVE_PROVIDER));
    }

    @Test
    public void testGetBestProvider() {
        Criteria criteria = new Criteria();

        String bestProvider = mManager.getBestProvider(criteria, false);
        assertEquals(COARSE_TEST_PROVIDER, bestProvider);

        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        assertNotEquals(COARSE_TEST_PROVIDER, mManager.getBestProvider(criteria, false));
    }

    @Test
    public void testSendExtraCommand() {
        mManager.sendExtraCommand(COARSE_TEST_PROVIDER, "command", null);

        try {
            mManager.sendExtraCommand(FINE_TEST_PROVIDER, "command", null);
            fail("Should throw SecurityException for " + FINE_TEST_PROVIDER);
        } catch (SecurityException expected) {
            // pass
        }
    }

    // TODO: this test should probably not be in the location module
    @Test
    public void testGnssProvidedClock() throws Exception {
        mManager.addTestProvider(GPS_PROVIDER,
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_COARSE);
        mManager.setTestProviderEnabled(GPS_PROVIDER, true);

        Location location = new Location(GPS_PROVIDER);
        long elapsed = SystemClock.elapsedRealtimeNanos();
        location.setLatitude(0);
        location.setLongitude(0);
        location.setAccuracy(0);
        location.setElapsedRealtimeNanos(elapsed);
        location.setTime(1);

        mManager.setTestProviderLocation(GPS_PROVIDER, location);
        assertTrue(SystemClock.currentGnssTimeClock().millis() < 1000);

        location.setTime(java.lang.System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        mManager.setTestProviderLocation(GPS_PROVIDER, location);
        Thread.sleep(200);
        long clockms = SystemClock.currentGnssTimeClock().millis();
        assertTrue(System.currentTimeMillis() - clockms < 1000);
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }
}
