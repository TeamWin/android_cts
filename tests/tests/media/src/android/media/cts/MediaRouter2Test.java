/*
 * Copyright 2019 The Android Open Source Project
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

import static android.content.Context.AUDIO_SERVICE;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE;
import static android.media.cts.SampleMediaRoute2ProviderService.FEATURES_SPECIAL;
import static android.media.cts.SampleMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.cts.SampleMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.cts.SampleMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.cts.SampleMediaRoute2ProviderService.ROUTE_ID3_SESSION_CREATION_FAILED;
import static android.media.cts.SampleMediaRoute2ProviderService.ROUTE_ID4_TO_SELECT_AND_DESELECT;
import static android.media.cts.SampleMediaRoute2ProviderService.ROUTE_ID5_TO_TRANSFER_TO;
import static android.media.cts.SampleMediaRoute2ProviderService.ROUTE_ID_SPECIAL_FEATURE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertThrows;

import android.annotation.NonNull;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.OnGetControllerHintsListener;
import android.media.MediaRouter2.RouteCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.MediaRouter2.RoutingControllerCallback;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.LargeTest;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "The system should be able to bind to SampleMediaRoute2ProviderService")
@LargeTest
public class MediaRouter2Test {
    private static final String TAG = "MR2Test";
    Context mContext;
    private MediaRouter2 mRouter2;
    private Executor mExecutor;
    private AudioManager mAudioManager;
    private SampleMediaRoute2ProviderService mServiceInstance;

    private static final int TIMEOUT_MS = 5000;
    private static final int WAIT_MS = 2000;

    private static final String TEST_KEY = "test_key";
    private static final String TEST_VALUE = "test_value";
    private static final RouteDiscoveryPreference EMPTY_DISCOVERY_PREFERENCE =
            new RouteDiscoveryPreference.Builder(Collections.emptyList(), false).build();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mRouter2 = MediaRouter2.getInstance(mContext);
        mExecutor = Executors.newSingleThreadExecutor();
        mAudioManager = (AudioManager) mContext.getSystemService(AUDIO_SERVICE);

        mServiceInstance = SampleMediaRoute2ProviderService.getInstance();
        if (mServiceInstance != null) {
            mServiceInstance.initializeRoutes();
            mServiceInstance.publishRoutes();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mServiceInstance != null) {
            mServiceInstance.clear();
            mServiceInstance = null;
        }
    }

    /**
     * Tests if we get proper routes for application that has special route type.
     */
    @Test
    public void testGetRoutes() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURES_SPECIAL);

        int systemRouteCount = 0;
        int remoteRouteCount = 0;
        for (MediaRoute2Info route : routes.values()) {
            if (route.isSystemRoute()) {
                systemRouteCount++;
            } else {
                remoteRouteCount++;
            }
        }

        // Can be greater than 1 if BT devices are connected.
        assertTrue(systemRouteCount > 0);
        assertEquals(1, remoteRouteCount);
        assertNotNull(routes.get(ROUTE_ID_SPECIAL_FEATURE));
    }

    @Test
    public void testRegisterControllerCallbackWithInvalidArguments() {
        Executor executor = mExecutor;
        RoutingControllerCallback callback = new RoutingControllerCallback();

        // Tests null executor
        assertThrows(NullPointerException.class,
                () -> mRouter2.registerControllerCallback(null, callback));

        // Tests null callback
        assertThrows(NullPointerException.class,
                () -> mRouter2.registerControllerCallback(executor, null));
    }

    @Test
    public void testUnregisterControllerCallbackWithNullCallback() {
        // Tests null callback
        assertThrows(NullPointerException.class,
                () -> mRouter2.unregisterControllerCallback(null));
    }

    @Test
    public void testRequestCreateControllerWithNullRoute() {
        assertThrows(NullPointerException.class,
                () -> mRouter2.requestCreateController(null));
    }

    @Test
    public void testRequestCreateControllerWithSystemRoute() {
        List<MediaRoute2Info> systemRoutes =
            mRouter2.getRoutes().stream().filter(r -> r.isSystemRoute())
                    .collect(Collectors.toList());

        assertFalse(systemRoutes.isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> mRouter2.requestCreateController(systemRoutes.get(0)));
    }

    @Test
    public void testRequestCreateControllerSuccess() throws Exception {
        final List<String> sampleRouteFeature = new ArrayList<>();
        sampleRouteFeature.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteFeature);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertNotNull(route);

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                assertNotNull(controller);
                assertTrue(createRouteMap(controller.getSelectedRoutes()).containsKey(
                        ROUTE_ID1));
                controllers.add(controller);
                successLatch.countDown();
            }

            @Override
            public void onControllerCreationFailed(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(route);
            assertTrue(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // onSessionCreationFailed should not be called.
            assertFalse(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    public void testRequestCreateControllerFailure() throws Exception {
        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info route = routes.get(ROUTE_ID3_SESSION_CREATION_FAILED);
        assertNotNull(route);

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                controllers.add(controller);
                successLatch.countDown();
            }

            @Override
            public void onControllerCreationFailed(MediaRoute2Info requestedRoute) {
                assertEquals(route, requestedRoute);
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(route);
            assertTrue(failureLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // onSessionCreated should not be called.
            assertFalse(successLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    public void testRequestCreateControllerMultipleSessions() throws Exception {
        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        final CountDownLatch successLatch = new CountDownLatch(2);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> createdControllers = new ArrayList<>();

        // Create session with this route
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                createdControllers.add(controller);
                successLatch.countDown();
            }

            @Override
            public void onControllerCreationFailed(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info route1 = routes.get(ROUTE_ID1);
        MediaRoute2Info route2 = routes.get(ROUTE_ID2);
        assertNotNull(route1);
        assertNotNull(route2);

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(route1);
            mRouter2.requestCreateController(route2);
            assertTrue(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // onSessionCreationFailed should not be called.
            assertFalse(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));

            // Created controllers should have proper info
            assertEquals(2, createdControllers.size());
            RoutingController controller1 = createdControllers.get(0);
            RoutingController controller2 = createdControllers.get(1);

            assertNotEquals(controller1.getId(), controller2.getId());
            assertTrue(createRouteMap(controller1.getSelectedRoutes()).containsKey(
                    ROUTE_ID1));
            assertTrue(createRouteMap(controller2.getSelectedRoutes()).containsKey(
                    ROUTE_ID2));

        } finally {
            releaseControllers(createdControllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    public void testSetOnGetControllerHintsListener() throws Exception {
        final List<String> sampleRouteFeature = new ArrayList<>();
        sampleRouteFeature.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteFeature);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertNotNull(route);

        final Bundle createSessionHints = new Bundle();
        createSessionHints.putString(TEST_KEY, TEST_VALUE);
        final OnGetControllerHintsListener listener = new OnGetControllerHintsListener() {
            @Override
            public Bundle onGetControllerHints(MediaRoute2Info route) {
                return createSessionHints;
            }
        };

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                assertNotNull(controller);
                assertTrue(createRouteMap(controller.getSelectedRoutes()).containsKey(
                        ROUTE_ID1));

                // The SampleMediaRoute2ProviderService supposed to set control hints
                // with the given creationSessionHints.
                Bundle controlHints = controller.getControlHints();
                assertNotNull(controlHints);
                assertTrue(controlHints.containsKey(TEST_KEY));
                assertEquals(TEST_VALUE, controlHints.getString(TEST_KEY));

                controllers.add(controller);
                successLatch.countDown();
            }

            @Override
            public void onControllerCreationFailed(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);

            // The SampleMediaRoute2ProviderService supposed to set control hints
            // with the given creationSessionHints.
            mRouter2.setOnGetControllerHintsListener(listener);
            mRouter2.requestCreateController(route);
            assertTrue(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // onSessionCreationFailed should not be called.
            assertFalse(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    public void testSetSessionVolume() throws Exception {
        List<String> sampleRouteFeature = new ArrayList<>();
        sampleRouteFeature.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteFeature);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertNotNull(route);

        CountDownLatch successLatch = new CountDownLatch(1);
        CountDownLatch volumeChangedLatch = new CountDownLatch(1);

        List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                controllers.add(controller);
                successLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();

        try {
            mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(route);

            assertTrue(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            mRouter2.unregisterControllerCallback(controllerCallback);
            mRouter2.unregisterRouteCallback(routeCallback);
        }

        assertEquals(1, controllers.size());
        // test requestSetSessionVolume

        RoutingController targetController = controllers.get(0);
        assertEquals(PLAYBACK_VOLUME_VARIABLE, targetController.getVolumeHandling());
        int currentVolume = targetController.getVolume();
        int maxVolume = targetController.getVolumeMax();
        int targetVolume = (currentVolume == maxVolume) ? currentVolume - 1 : (currentVolume + 1);

        RoutingControllerCallback routingControllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerUpdated(MediaRouter2.RoutingController controller) {
                if (!TextUtils.equals(targetController.getId(), controller.getId())) {
                    return;
                }
                if (controller.getVolume() == targetVolume) {
                    volumeChangedLatch.countDown();
                }
            }
        };

        try {
            mRouter2.registerControllerCallback(mExecutor, routingControllerCallback);
            mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);
            targetController.setVolume(targetVolume);
            assertTrue(volumeChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(routingControllerCallback);
        }
    }

    @Test
    public void testRoutingControllerCallbackIsNotCalledAfterUnregistered() throws Exception {
        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertNotNull(route);

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                controllers.add(controller);
                successLatch.countDown();
            }

            @Override
            public void onControllerCreationFailed(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(route);

            // Unregisters session callback
            mRouter2.unregisterControllerCallback(controllerCallback);

            // No session callback methods should be called.
            assertFalse(successLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
            assertFalse(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    // TODO: Add tests for illegal inputs if needed (e.g. selecting already selected route)
    @Test
    public void testRoutingControllerSelectAndDeselectRoute() throws Exception {
        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info routeToCreateSessionWith = routes.get(ROUTE_ID1);
        assertNotNull(routeToCreateSessionWith);

        final CountDownLatch onControllerCreatedLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatchForSelect = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatchForDeselect = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with ROUTE_ID1
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                assertNotNull(controller);
                assertTrue(getOriginalRouteIds(controller.getSelectedRoutes()).contains(
                        ROUTE_ID1));
                controllers.add(controller);
                onControllerCreatedLatch.countDown();
            }

            @Override
            public void onControllerUpdated(RoutingController controller) {
                if (onControllerCreatedLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }

                if (onControllerUpdatedLatchForSelect.getCount() != 0) {
                    assertEquals(2, controller.getSelectedRoutes().size());
                    assertTrue(getOriginalRouteIds(controller.getSelectedRoutes())
                            .contains(ROUTE_ID1));
                    assertTrue(getOriginalRouteIds(controller.getSelectedRoutes())
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT));
                    assertFalse(getOriginalRouteIds(controller.getSelectableRoutes())
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT));
                    assertTrue(getOriginalRouteIds(controller.getDeselectableRoutes())
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT));

                    onControllerUpdatedLatchForSelect.countDown();
                } else {
                    assertEquals(1, controller.getSelectedRoutes().size());
                    assertTrue(getOriginalRouteIds(controller.getSelectedRoutes())
                            .contains(ROUTE_ID1));
                    assertFalse(getOriginalRouteIds(controller.getSelectedRoutes())
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT));
                    assertTrue(getOriginalRouteIds(controller.getSelectableRoutes())
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT));
                    assertFalse(getOriginalRouteIds(controller.getDeselectableRoutes())
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT));

                    onControllerUpdatedLatchForDeselect.countDown();
                }
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(routeToCreateSessionWith);
            assertTrue(onControllerCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            assertEquals(1, controllers.size());
            RoutingController controller = controllers.get(0);
            assertTrue(getOriginalRouteIds(controller.getSelectableRoutes())
                    .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT));

            // Select ROUTE_ID4_TO_SELECT_AND_DESELECT
            MediaRoute2Info routeToSelectAndDeselect = routes.get(
                    ROUTE_ID4_TO_SELECT_AND_DESELECT);
            assertNotNull(routeToSelectAndDeselect);

            controller.selectRoute(routeToSelectAndDeselect);
            assertTrue(onControllerUpdatedLatchForSelect.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            controller.deselectRoute(routeToSelectAndDeselect);
            assertTrue(onControllerUpdatedLatchForDeselect.await(
                    TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    public void testRoutingControllerTransferToRoute() throws Exception {
        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info routeToCreateSessionWith = routes.get(ROUTE_ID1);
        assertNotNull(routeToCreateSessionWith);

        final CountDownLatch onControllerCreatedLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with ROUTE_ID1
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                assertNotNull(controller);
                assertTrue(getOriginalRouteIds(controller.getSelectedRoutes()).contains(
                        ROUTE_ID1));
                controllers.add(controller);
                onControllerCreatedLatch.countDown();
            }

            @Override
            public void onControllerUpdated(RoutingController controller) {
                if (onControllerCreatedLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }
                assertEquals(1, controller.getSelectedRoutes().size());
                assertFalse(getOriginalRouteIds(controller.getSelectedRoutes()).contains(
                        ROUTE_ID1));
                assertTrue(getOriginalRouteIds(controller.getSelectedRoutes())
                        .contains(ROUTE_ID5_TO_TRANSFER_TO));
                assertFalse(getOriginalRouteIds(controller.getTransferrableRoutes())
                        .contains(ROUTE_ID5_TO_TRANSFER_TO));

                onControllerUpdatedLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(routeToCreateSessionWith);
            assertTrue(onControllerCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            assertEquals(1, controllers.size());
            RoutingController controller = controllers.get(0);
            assertTrue(getOriginalRouteIds(controller.getTransferrableRoutes())
                    .contains(ROUTE_ID5_TO_TRANSFER_TO));

            // Transfer to ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToTransferTo = routes.get(ROUTE_ID5_TO_TRANSFER_TO);
            assertNotNull(routeToTransferTo);

            controller.transferToRoute(routeToTransferTo);
            assertTrue(onControllerUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    // TODO: Add tests for onSessionReleased() when provider releases the session.

    @Test
    public void testRoutingControllerRelease() throws Exception {
        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info routeToCreateSessionWith = routes.get(ROUTE_ID1);
        assertNotNull(routeToCreateSessionWith);

        final CountDownLatch onControllerCreatedLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        final CountDownLatch onControllerReleasedLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with ROUTE_ID1
        RoutingControllerCallback controllerCallback = new RoutingControllerCallback() {
            @Override
            public void onControllerCreated(RoutingController controller) {
                assertNotNull(controller);
                assertTrue(getOriginalRouteIds(controller.getSelectedRoutes()).contains(
                        ROUTE_ID1));
                controllers.add(controller);
                onControllerCreatedLatch.countDown();
            }

            @Override
            public void onControllerUpdated(RoutingController controller) {
                if (onControllerCreatedLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }
                onControllerUpdatedLatch.countDown();
            }

            @Override
            public void onControllerReleased(RoutingController controller) {
                if (onControllerCreatedLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }
                onControllerReleasedLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback();
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.requestCreateController(routeToCreateSessionWith);
            assertTrue(onControllerCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            assertEquals(1, controllers.size());
            RoutingController controller = controllers.get(0);
            assertTrue(getOriginalRouteIds(controller.getTransferrableRoutes())
                    .contains(ROUTE_ID5_TO_TRANSFER_TO));

            // Release controller. Future calls should be ignored.
            controller.release();

            // Transfer to ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToTransferTo = routes.get(ROUTE_ID5_TO_TRANSFER_TO);
            assertNotNull(routeToTransferTo);

            // This call should be ignored.
            // The onSessionInfoChanged() shouldn't be called.
            controller.transferToRoute(routeToTransferTo);
            assertFalse(onControllerUpdatedLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));

            // onControllerReleased should be called.
            assertTrue(onControllerReleasedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    // TODO: Consider adding tests with bluetooth connection/disconnection.
    @Test
    public void testGetSystemController() {
        final RoutingController systemController = mRouter2.getSystemController();
        assertNotNull(systemController);
        assertFalse(systemController.isReleased());

        for (MediaRoute2Info route : systemController.getSelectedRoutes()) {
            assertTrue(route.isSystemRoute());
        }
    }

    @Test
    public void testGetControllers() {
        List<RoutingController> controllers = mRouter2.getControllers();
        assertNotNull(controllers);
        assertFalse(controllers.isEmpty());
        assertSame(mRouter2.getSystemController(), controllers.get(0));
    }

    @Test
    public void testVolumeHandlingWhenVolumeFixed() {
        if (!mAudioManager.isVolumeFixed()) {
            return;
        }
        MediaRoute2Info selectedSystemRoute =
                mRouter2.getSystemController().getSelectedRoutes().get(0);
        assertEquals(MediaRoute2Info.PLAYBACK_VOLUME_FIXED,
                selectedSystemRoute.getVolumeHandling());
    }

    @Test
    public void testCallbacksAreCalledWhenVolumeChanged() throws Exception {
        if (mAudioManager.isVolumeFixed()) {
            return;
        }

        final int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int minVolume = mAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        MediaRoute2Info selectedSystemRoute =
                mRouter2.getSystemController().getSelectedRoutes().get(0);

        assertEquals(maxVolume, selectedSystemRoute.getVolumeMax());
        assertEquals(originalVolume, selectedSystemRoute.getVolume());
        assertEquals(PLAYBACK_VOLUME_VARIABLE,
                selectedSystemRoute.getVolumeHandling());

        final int targetVolume = originalVolume == minVolume
                ? originalVolume + 1 : originalVolume - 1;
        final CountDownLatch latch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesChanged(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getId().equals(selectedSystemRoute.getId())
                            && route.getVolume() == targetVolume) {
                        latch.countDown();
                        break;
                    }
                }
            }
        };

        mRouter2.registerRouteCallback(mExecutor, routeCallback,
                new RouteDiscoveryPreference.Builder(new ArrayList<>(), true).build());

        try {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        }
    }

    @Test
    public void markCallbacksAsTested() {
        // Due to CTS coverage tool's bug, it doesn't count the callback methods as tested even if
        // we have tests for them. This method just directly calls those methods so that the tool
        // can recognize the callback methods as tested.

        MediaRouter2.RouteCallback routeCallback = new MediaRouter2.RouteCallback();
        routeCallback.onRoutesAdded(null);
        routeCallback.onRoutesChanged(null);
        routeCallback.onRoutesRemoved(null);

        MediaRouter2.RoutingControllerCallback controllerCallback =
                new MediaRouter2.RoutingControllerCallback();
        controllerCallback.onControllerCreated(null);
        controllerCallback.onControllerCreationFailed(null);
        controllerCallback.onControllerUpdated(null);
        controllerCallback.onControllerReleased(null);

        OnGetControllerHintsListener listener = route -> null;
        listener.onGetControllerHints(null);
    }

    // Helper for getting routes easily. Uses original ID as a key
    private static Map<String, MediaRoute2Info> createRouteMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> routeMap = new HashMap<>();
        for (MediaRoute2Info route : routes) {
            routeMap.put(route.getOriginalId(), route);
        }
        return routeMap;
    }

    private Map<String, MediaRoute2Info> waitAndGetRoutes(List<String> routeTypes)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (!route.isSystemRoute()) {
                        latch.countDown();
                    }
                }
            }
        };

        mRouter2.registerRouteCallback(mExecutor, routeCallback,
                new RouteDiscoveryPreference.Builder(routeTypes, true).build());
        try {
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return createRouteMap(mRouter2.getRoutes());
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    static void releaseControllers(@NonNull List<RoutingController> controllers) {
        for (RoutingController controller : controllers) {
            controller.release();
        }
    }

    /**
     * Returns a list of original route IDs of the given route list.
     */
    private List<String> getOriginalRouteIds(@NonNull List<MediaRoute2Info> routes) {
        List<String> result = new ArrayList<>();
        for (MediaRoute2Info route : routes) {
            result.add(route.getOriginalId());
        }
        return result;
    }
}
