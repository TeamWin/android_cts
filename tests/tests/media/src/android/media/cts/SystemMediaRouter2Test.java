/*
 * Copyright 2021 The Android Open Source Project
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

import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL;
import static android.media.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.cts.StubMediaRoute2ProviderService.ROUTE_ID3_SESSION_CREATION_FAILED;
import static android.media.cts.StubMediaRoute2ProviderService.ROUTE_ID_VARIABLE_VOLUME;
import static android.media.cts.StubMediaRoute2ProviderService.ROUTE_NAME2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.RouteCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.MediaRouter2.TransferCallback;
import android.media.MediaRouter2Manager;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.LargeTest;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "The system should be able to bind to StubMediaRoute2ProviderService")
@LargeTest
@NonMediaMainlineTest
public class SystemMediaRouter2Test {
    private static final String TAG = "SystemMR2Test";
    Context mContext;
    private MediaRouter2 mSystemRouter2ForCts;
    private MediaRouter2 mAppRouter2;

    private Executor mExecutor;
    private StubMediaRoute2ProviderService mService;

    private static final int TIMEOUT_MS = 5000;
    private static final int WAIT_MS = 2000;

    private RouteCallback mAppRouterPlaceHolderCallback = new RouteCallback() {};

    private final List<RouteCallback> mRouteCallbacks = new ArrayList<>();
    private final List<TransferCallback> mTransferCallbacks = new ArrayList<>();

    public static final List<String> FEATURES_ALL = new ArrayList();
    public static final List<String> FEATURES_SPECIAL = new ArrayList();

    static {
        FEATURES_ALL.add(FEATURE_SAMPLE);
        FEATURES_ALL.add(FEATURE_SPECIAL);
        FEATURES_ALL.add(FEATURE_LIVE_AUDIO);

        FEATURES_SPECIAL.add(FEATURE_SPECIAL);
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mExecutor = Executors.newSingleThreadExecutor();
        MediaRouter2TestActivity.startActivity(mContext);

        mSystemRouter2ForCts = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        mSystemRouter2ForCts.startScan();

        mAppRouter2 = MediaRouter2.getInstance(mContext);
        // In order to make the system bind to the test service,
        // set a non-empty discovery preference.
        List<String> features = new ArrayList<>();
        features.add("A test feature");
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, false).build();
        mRouteCallbacks.add(mAppRouterPlaceHolderCallback);
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback, preference);

        new PollingCheck(TIMEOUT_MS) {
            @Override
            protected boolean check() {
                StubMediaRoute2ProviderService service =
                        StubMediaRoute2ProviderService.getInstance();
                if (service != null) {
                    mService = service;
                    return true;
                }
                return false;
            }
        }.run();
        mService.initializeRoutes();
        mService.publishRoutes();
    }

    @After
    public void tearDown() throws Exception {
        mSystemRouter2ForCts.stopScan();

        MediaRouter2TestActivity.finishActivity();
        if (mService != null) {
            mService.clear();
            mService = null;
        }

        // order matters (callbacks should be cleared at the last)
        releaseAllSessions();
        // unregister callbacks
        clearCallbacks();
    }

    @Test
    public void testGetInstanceWithInvalidPackageName() {
        assertNull(MediaRouter2.getInstance(mContext, "com.non.existent.package.name"));
    }

    @Test
    public void testGetInstanceReturnsSameInstance() {
        assertSame(mSystemRouter2ForCts,
                MediaRouter2.getInstance(mContext, mContext.getPackageName()));
    }

    @Test
    public void testGetClientPackageName() {
        assertEquals(mContext.getPackageName(), mSystemRouter2ForCts.getClientPackageName());
    }

    @Test
    public void testGetSystemController() {
        RoutingController controller = mSystemRouter2ForCts.getSystemController();
        assertNotNull(controller);
        // getSystemController() should always return the same instance.
        assertSame(controller, mSystemRouter2ForCts.getSystemController());
    }

    @Test
    public void testGetControllerReturnsNullForUnknownId() {
        assertNull(mSystemRouter2ForCts.getController("nonExistentControllerId"));
    }

    @Test
    public void testGetAllRoutes() throws Exception {
        waitAndGetRoutes(FEATURE_SPECIAL);

        // Regardless of whether the app router registered its preference,
        // getAllRoutes() will return all the routes.
        boolean routeFound = false;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getAllRoutes()) {
            if (route.getFeatures().contains(FEATURE_SPECIAL)) {
                routeFound = true;
                break;
            }
        }
        assertTrue(routeFound);
    }

    @Test
    public void testGetRoutes() throws Exception {
        // Since the app router haven't registered any preference yet,
        // only the system routes will come out after creation.
        assertTrue(mSystemRouter2ForCts.getRoutes().isEmpty());

        waitAndGetRoutes(FEATURE_SPECIAL);

        boolean routeFound = false;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getRoutes()) {
            if (route.getFeatures().contains(FEATURE_SPECIAL)) {
                routeFound = true;
                break;
            }
        }
        assertTrue(routeFound);
    }

    @Test
    public void testRouteCallbackOnRoutesAdded() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        MediaRoute2Info routeToAdd = new MediaRoute2Info.Builder("testRouteId", "testRouteName")
                .addFeature(FEATURE_SAMPLE)
                .build();

        CountDownLatch addedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(routeToAdd.getOriginalId())
                            && route.getName().equals(routeToAdd.getName())) {
                        addedLatch.countDown();
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mService.addRoute(routeToAdd);
        assertTrue(addedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRouteCallbackOnRoutesRemoved() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        waitAndGetRoutes(FEATURE_SAMPLE);

        CountDownLatch removedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesRemoved(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(ROUTE_ID2)
                            && route.getName().equals(ROUTE_NAME2)) {
                        removedLatch.countDown();
                        break;
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mService.removeRoute(ROUTE_ID2);
        assertTrue(removedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSetRouteVolumeAndRouteCallbackOnRoutesChanged() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        waitAndGetRoutes(FEATURE_SAMPLE);

        MediaRoute2Info routeToChangeVolume = null;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getAllRoutes()) {
            if (TextUtils.equals(ROUTE_ID_VARIABLE_VOLUME, route.getOriginalId())) {
                routeToChangeVolume = route;
                break;
            }
        }
        assertNotNull(routeToChangeVolume);

        int targetVolume = routeToChangeVolume.getVolume() + 1;
        CountDownLatch changedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesChanged(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(ROUTE_ID_VARIABLE_VOLUME)
                            && route.getVolume() == targetVolume) {
                        changedLatch.countDown();
                        break;
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mSystemRouter2ForCts.setRouteVolume(routeToChangeVolume, targetVolume);
        assertTrue(changedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRouteCallbackOnPreferredFeaturesChanged() throws Exception {
        String testFeature = "testFeature";
        List<String> testFeatures = new ArrayList<>();
        testFeatures.add(testFeature);

        CountDownLatch featuresChangedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onPreferredFeaturesChanged(List<String> preferredFeatures) {
                if (preferredFeatures.contains(testFeature)) {
                    featuresChangedLatch.countDown();
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(testFeatures, true).build());
        assertTrue(featuresChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testTransferToSuccess() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertNotNull(route);

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertEquals(mSystemRouter2ForCts.getSystemController(), oldController);
                assertTrue(createRouteMap(newController.getSelectedRoutes()).containsKey(
                        ROUTE_ID1));
                controllers.add(newController);
                successLatch.countDown();
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertTrue(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // onSessionCreationFailed should not be called.
            assertFalse(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testTransferToFailure() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID3_SESSION_CREATION_FAILED);
        assertNotNull(route);

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                controllers.add(newController);
                successLatch.countDown();
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                assertEquals(route, requestedRoute);
                failureLatch.countDown();
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertTrue(failureLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // onTransfer should not be called.
            assertFalse(successLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }


    @Test
    public void testTransferToTwice() throws Exception {
        final CountDownLatch successLatch1 = new CountDownLatch(1);
        final CountDownLatch successLatch2 = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final CountDownLatch stopLatch = new CountDownLatch(1);
        final CountDownLatch onReleaseSessionLatch = new CountDownLatch(1);

        final List<RoutingController> createdControllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                createdControllers.add(newController);
                if (successLatch1.getCount() > 0) {
                    successLatch1.countDown();
                } else {
                    successLatch2.countDown();
                }
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }

            @Override
            public void onStop(RoutingController controller) {
                stopLatch.countDown();
            }
        };

        StubMediaRoute2ProviderService service = mService;
        if (service != null) {
            service.setProxy(new StubMediaRoute2ProviderService.Proxy() {
                @Override
                public void onReleaseSession(long requestId, String sessionId) {
                    onReleaseSessionLatch.countDown();
                }
            });
        }

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route1 = routes.get(ROUTE_ID1);
        MediaRoute2Info route2 = routes.get(ROUTE_ID2);
        assertNotNull(route1);
        assertNotNull(route2);

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route1);
            assertTrue(successLatch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            mSystemRouter2ForCts.transferTo(route2);
            assertTrue(successLatch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // onTransferFailure/onStop should not be called.
            assertFalse(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
            assertFalse(stopLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));

            // Created controllers should have proper info
            assertEquals(2, createdControllers.size());
            RoutingController controller1 = createdControllers.get(0);
            RoutingController controller2 = createdControllers.get(1);

            assertNotEquals(controller1.getId(), controller2.getId());
            assertTrue(createRouteMap(controller1.getSelectedRoutes()).containsKey(
                    ROUTE_ID1));
            assertTrue(createRouteMap(controller2.getSelectedRoutes()).containsKey(
                    ROUTE_ID2));

            // Should be able to release transferred controllers.
            controller1.release();
            assertTrue(onReleaseSessionLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(createdControllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    private Map<String, MediaRoute2Info> waitAndGetRoutes(String feature) throws Exception {
        List<String> features = new ArrayList<>();
        features.add(feature);

        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(features, true).build());

        CountDownLatch latch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getFeatures().contains(feature)) {
                        latch.countDown();
                        break;
                    }
                }
            }
        };

        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        try {
            // Note: The routes can be added before registering the callback,
            // therefore no assertTrue() here.
            latch.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return createRouteMap(mSystemRouter2ForCts.getRoutes());
        } finally {
            mSystemRouter2ForCts.unregisterRouteCallback(routeCallback);
        }
    }

    // Helper for getting routes easily. Uses original ID as a key
    private static Map<String, MediaRoute2Info> createRouteMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> routeMap = new HashMap<>();
        for (MediaRoute2Info route : routes) {
            routeMap.put(route.getOriginalId(), route);
        }
        return routeMap;
    }

    private void releaseAllSessions() {
        MediaRouter2Manager manager = MediaRouter2Manager.getInstance(mContext);
        for (RoutingSessionInfo session : manager.getActiveSessions()) {
            manager.releaseSession(session);
        }
    }

    private void clearCallbacks() {
        for (RouteCallback routeCallback : mRouteCallbacks) {
            mAppRouter2.unregisterRouteCallback(routeCallback);
            mSystemRouter2ForCts.unregisterRouteCallback(routeCallback);
        }
        mRouteCallbacks.clear();

        for (TransferCallback transferCallback : mTransferCallbacks) {
            mAppRouter2.unregisterTransferCallback(transferCallback);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
        mTransferCallbacks.clear();
    }

    static void releaseControllers(List<RoutingController> controllers) {
        for (RoutingController controller : controllers) {
            controller.release();
        }
    }
}
