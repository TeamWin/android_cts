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

package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.FEATURES_ALL;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_1_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_1_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_1_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_2_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_2_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_2_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_3_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_3_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_3_3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.platform.test.annotations.LargeTest;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
public class MediaRouter2Test {
    private static final int TIMEOUT_MS = 5_000;
    private static final int WAIT_MS = 2_000;

    Context mContext;
    private Executor mExecutor;
    private MediaRouter2 mRouter2;
    private MediaRouter2.RouteCallback mRouterDummyCallback = new MediaRouter2.RouteCallback() {};

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mRouter2 = MediaRouter2.getInstance(mContext);

        MediaRouter2TestActivity.startActivity(mContext);

        // In order to make the system bind to the test service,
        // set a non-empty discovery preference while app is in foreground.
        List<String> features = new ArrayList<>();
        features.add("A test feature");
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, /*activeScan=*/ false).build();
        mRouter2.registerRouteCallback(mExecutor, mRouterDummyCallback, preference);
    }

    @Test
    public void dontDedupeByDefault() throws Exception {
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, /*activeScan=*/ true).build();
        Map<String, MediaRoute2Info> routes =
                waitAndGetRoutes(preference, Set.of(ROUTE_ID_1_1, ROUTE_ID_2_1, ROUTE_ID_3_1));

        assertTrue(routes.containsKey(ROUTE_ID_1_1));
        assertTrue(routes.containsKey(ROUTE_ID_1_2));
        assertTrue(routes.containsKey(ROUTE_ID_1_3));
        assertTrue(routes.containsKey(ROUTE_ID_2_1));
        assertTrue(routes.containsKey(ROUTE_ID_2_2));
        assertTrue(routes.containsKey(ROUTE_ID_2_3));
        assertTrue(routes.containsKey(ROUTE_ID_3_1));
        assertTrue(routes.containsKey(ROUTE_ID_3_2));
        assertTrue(routes.containsKey(ROUTE_ID_3_3));
    }

    @Test
    public void setDeduplicationPackageOrder1() throws Exception {
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, /*activeScan=*/ true)
                        .setDeduplicationPackageOrder(
                                List.of(
                                        MEDIA_ROUTER_PROVIDER_1_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_2_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_3_PACKAGE))
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitAndGetRoutes(preference, Set.of(ROUTE_ID_1_1, ROUTE_ID_2_1, ROUTE_ID_3_1));

        assertTrue(routes.containsKey(ROUTE_ID_1_1));
        assertTrue(routes.containsKey(ROUTE_ID_1_2));
        assertTrue(routes.containsKey(ROUTE_ID_1_3));
        assertTrue(routes.containsKey(ROUTE_ID_2_1));
        assertFalse(routes.containsKey(ROUTE_ID_2_2));
        assertTrue(routes.containsKey(ROUTE_ID_2_3));
        assertTrue(routes.containsKey(ROUTE_ID_3_1));
        assertFalse(routes.containsKey(ROUTE_ID_3_2));
        assertFalse(routes.containsKey(ROUTE_ID_3_3));
    }

    @Test
    public void setDeduplicationPackageOrder2() throws Exception {
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, /*activeScan=*/ true)
                        .setDeduplicationPackageOrder(
                                List.of(
                                        MEDIA_ROUTER_PROVIDER_3_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_2_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_1_PACKAGE))
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitAndGetRoutes(preference, Set.of(ROUTE_ID_1_1, ROUTE_ID_2_1, ROUTE_ID_3_1));

        assertTrue(routes.containsKey(ROUTE_ID_1_1));
        assertFalse(routes.containsKey(ROUTE_ID_1_2));
        assertFalse(routes.containsKey(ROUTE_ID_1_3));
        assertTrue(routes.containsKey(ROUTE_ID_2_1));
        assertFalse(routes.containsKey(ROUTE_ID_2_2));
        assertFalse(routes.containsKey(ROUTE_ID_2_3));
        assertTrue(routes.containsKey(ROUTE_ID_3_1));
        assertTrue(routes.containsKey(ROUTE_ID_3_2));
        assertTrue(routes.containsKey(ROUTE_ID_3_3));
    }

    @Test
    public void setDeduplicationPackageOrder3() throws Exception {
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, /*activeScan=*/ true)
                        .setDeduplicationPackageOrder(
                                List.of(
                                        MEDIA_ROUTER_PROVIDER_2_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_3_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_1_PACKAGE))
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitAndGetRoutes(preference, Set.of(ROUTE_ID_1_1, ROUTE_ID_2_1, ROUTE_ID_3_1));

        assertTrue(routes.containsKey(ROUTE_ID_1_1));
        assertFalse(routes.containsKey(ROUTE_ID_1_2));
        assertFalse(routes.containsKey(ROUTE_ID_1_3));
        assertTrue(routes.containsKey(ROUTE_ID_2_1));
        assertTrue(routes.containsKey(ROUTE_ID_2_2));
        assertTrue(routes.containsKey(ROUTE_ID_2_3));
        assertTrue(routes.containsKey(ROUTE_ID_3_1));
        assertTrue(routes.containsKey(ROUTE_ID_3_2));
        assertFalse(routes.containsKey(ROUTE_ID_3_3));
    }

    @Test
    public void testRouteCallbacks() throws Exception {
        Set<String> addedRouteIds = new HashSet<>();
        Set<String> removedRouteIds = new HashSet<>();

        AtomicReference<CountDownLatch> addLatchRef = new AtomicReference<>();
        AtomicReference<CountDownLatch> removeLatchRef = new AtomicReference<>();

        addLatchRef.set(new CountDownLatch(1));
        removeLatchRef.set(new CountDownLatch(1));

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, /*activeScan=*/ true)
                        .setAllowedPackages(List.of(MEDIA_ROUTER_PROVIDER_1_PACKAGE))
                        .setDeduplicationPackageOrder(
                                List.of(
                                        MEDIA_ROUTER_PROVIDER_2_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_3_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_1_PACKAGE))
                        .build();
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesAdded(List<MediaRoute2Info> routes) {
                        for (MediaRoute2Info route : routes) {
                            if (!route.isSystemRoute()) {
                                addedRouteIds.add(route.getOriginalId());
                            }
                        }
                        addLatchRef.get().countDown();
                    }

                    @Override
                    public void onRoutesRemoved(List<MediaRoute2Info> routes) {
                        for (MediaRoute2Info route : routes) {
                            removedRouteIds.add(route.getOriginalId());
                        }
                        removeLatchRef.get().countDown();
                    }
                };
        mRouter2.registerRouteCallback(mExecutor, routeCallback, preference);
        assertTrue(addLatchRef.get().await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(removeLatchRef.get().await(WAIT_MS, TimeUnit.MILLISECONDS));
        assertEquals(Set.of(ROUTE_ID_1_1, ROUTE_ID_1_2, ROUTE_ID_1_3), addedRouteIds);

        addLatchRef.set(new CountDownLatch(1));
        removeLatchRef.set(new CountDownLatch(1));
        RouteDiscoveryPreference preference2 =
                new RouteDiscoveryPreference.Builder(preference)
                        .setAllowedPackages(
                                List.of(
                                        MEDIA_ROUTER_PROVIDER_1_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_2_PACKAGE))
                        .build();

        addedRouteIds.clear();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, preference2);
        assertTrue(addLatchRef.get().await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(removeLatchRef.get().await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(Set.of(ROUTE_ID_2_1, ROUTE_ID_2_2, ROUTE_ID_2_3), addedRouteIds);
        assertEquals(Set.of(ROUTE_ID_1_2), removedRouteIds);

        addLatchRef.set(new CountDownLatch(1));
        removeLatchRef.set(new CountDownLatch(1));
        RouteDiscoveryPreference preference3 =
                new RouteDiscoveryPreference.Builder(preference)
                        .setAllowedPackages(
                                List.of(
                                        MEDIA_ROUTER_PROVIDER_1_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_2_PACKAGE,
                                        MEDIA_ROUTER_PROVIDER_3_PACKAGE))
                        .build();

        addedRouteIds.clear();
        removedRouteIds.clear();

        mRouter2.registerRouteCallback(mExecutor, routeCallback, preference3);
        assertTrue(addLatchRef.get().await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(removeLatchRef.get().await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(Set.of(ROUTE_ID_3_1, ROUTE_ID_3_2), addedRouteIds);
        assertEquals(Set.of(ROUTE_ID_1_3), removedRouteIds);
    }

    // It returns original route id -> route for convenience
    private static Map<String, MediaRoute2Info> createRouteMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> routeMap = new HashMap<>();
        for (MediaRoute2Info route : routes) {
            routeMap.put(route.getOriginalId(), route);
        }
        return routeMap;
    }

    private Map<String, MediaRoute2Info> waitAndGetRoutes(
            RouteDiscoveryPreference preference, Set<String> expectedRouteIds) throws Exception {
        CountDownLatch latch = new CountDownLatch(expectedRouteIds.size());

        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesAdded(List<MediaRoute2Info> routes) {
                        for (MediaRoute2Info route : routes) {
                            if (!route.isSystemRoute()
                                    && expectedRouteIds.contains(route.getOriginalId())) {
                                latch.countDown();
                            }
                        }
                    }
                };

        mRouter2.registerRouteCallback(mExecutor, routeCallback, preference);
        try {
            latch.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return createRouteMap(mRouter2.getRoutes());
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }
}
