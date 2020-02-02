/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.cts;

import static android.media.cts.SampleMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.cts.SampleMediaRoute2ProviderService.FEATURE_SPECIAL;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.media.cts.SampleMediaRoute2ProviderService.Proxy;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class MediaRoute2ProviderServiceTest {
    private static final String TAG = "MR2ProviderServiceTest";
    Context mContext;
    private MediaRouter2 mRouter2;
    private Executor mExecutor;

    private static final int TIMEOUT_MS = 5000;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mRouter2 = MediaRouter2.getInstance(mContext);
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws Exception {
        setProxy(null);
    }

    @Test
    public void testOnDiscoveryPreferenceChanged() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        MediaRouter2.RouteCallback routeCallback = new MediaRouter2.RouteCallback();
        MediaRouter2.RouteCallback routeCallback2 = new MediaRouter2.RouteCallback();

        List<String> featuresSample = Collections.singletonList(FEATURE_SAMPLE);
        List<String> featuresSpecial = Collections.singletonList(FEATURE_SPECIAL);

        setProxy(new Proxy() {
            @Override
            public void onDiscoveryPreferenceChanged(RouteDiscoveryPreference preference) {
                List<String> features = preference.getPreferredFeatures();
                if (features.contains(FEATURE_SAMPLE) && features.contains(FEATURE_SPECIAL)
                        && preference.isActiveScan()) {
                    latch.countDown();
                }
                if (latch.getCount() == 0 && !features.contains(FEATURE_SAMPLE)
                    && features.contains(FEATURE_SPECIAL)) {
                    latch2.countDown();
                }
            }
        });

        mRouter2.registerRouteCallback(mExecutor, routeCallback,
                new RouteDiscoveryPreference.Builder(featuresSample, true).build());
        mRouter2.registerRouteCallback(mExecutor, routeCallback2,
                new RouteDiscoveryPreference.Builder(featuresSpecial, true).build());
        try {
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            mRouter2.unregisterRouteCallback(routeCallback);
            assertTrue(latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback2);
        }
    }

    void setProxy(SampleMediaRoute2ProviderService.Proxy proxy) {
        SampleMediaRoute2ProviderService instance = SampleMediaRoute2ProviderService.getInstance();
        if (instance != null) {
            instance.setProxy(proxy);
        }
    }
}
