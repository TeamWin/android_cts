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
package com.android.cts.deviceowner;

import static android.location.LocationManager.EXTRA_PROVIDER_NAME;
import static android.location.LocationManager.MODE_CHANGED_ACTION;
import static android.location.LocationManager.PROVIDERS_CHANGED_ACTION;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.LocationManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.LocationUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link DevicePolicyManager#requestSetLocationProviderAllowed}.
 */
public class RequestSetLocationProviderAllowedTest extends BaseDeviceOwnerTest {

    private static final long TIMEOUT_MS = 5000;

    private static final String TEST_PROVIDER = "test_provider";

    private LocationManager mLocationManager;

    private boolean mLocationEnabled;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // allow us to add mock location providers
        LocationUtils.registerMockLocationProvider(mInstrumentation, /*enable=*/true);

        mLocationManager = mContext.getSystemService(LocationManager.class);

        mLocationEnabled = mLocationManager.isLocationEnabled();
        if (!mLocationEnabled) {
            setLocationEnabledAndAssert(true);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (!mLocationEnabled) {
            setLocationEnabledAndAssert(false);
        }

        LocationUtils.registerMockLocationProvider(mInstrumentation, /*enable=*/false);
        super.tearDown();
    }

    public void testRequestSetLocationProviderAllowedTest() throws Exception {
        mLocationManager.addTestProvider(TEST_PROVIDER,
                /* requiresNetwork=*/ true,
                /* requiresSatellite=*/ false,
                /* requiresCell=*/ true,
                /* hasMonetaryCost=*/ false,
                /* supportsAltitude=*/ false,
                /* supportsSpeed=*/ false,
                /* supportsBearing=*/ false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        try {
            mLocationManager.setTestProviderEnabled(TEST_PROVIDER, false);

            requestProviderAllowedAndAssert(TEST_PROVIDER, true);
            requestProviderAllowedAndAssert(TEST_PROVIDER, false);
        } finally {
            mLocationManager.removeTestProvider(TEST_PROVIDER);
        }
    }

    private void requestProviderAllowedAndAssert(String provider, boolean allowed) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (provider.equals(intent.getStringExtra(EXTRA_PROVIDER_NAME))) {
                    latch.countDown();
                }
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(PROVIDERS_CHANGED_ACTION));

        try {
            mDevicePolicyManager.requestSetLocationProviderAllowed(getWho(), provider, allowed);
            assertTrue("timed out waiting for provider change broadcast",
                    latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals(allowed, mLocationManager.isProviderEnabled(provider));
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private void setLocationEnabledAndAssert(boolean enabled) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(MODE_CHANGED_ACTION));

        try {
            mDevicePolicyManager.setLocationEnabled(getWho(), enabled);
            assertTrue("timed out waiting for location mode change broadcast",
                    latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }
}
