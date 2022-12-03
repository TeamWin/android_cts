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

import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_2_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_2_ROUTE_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_2_ROUTE_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_3;

import android.Manifest;
import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2Manager;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.os.ConditionVariable;
import android.os.PowerManager;
import android.platform.test.annotations.LargeTest;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Device-side test for {@link MediaRouter2} functionality. */
@LargeTest
public class MediaRouter2DeviceTest {
    /**
     * The maximum amount of time to wait for an expected {@link
     * MediaRouter2.RouteCallback#onRoutesUpdated} call, in milliseconds.
     */
    private static final int ROUTE_UPDATE_MAX_WAIT_MS = 10_000;

    private MediaRouter2 mRouter2;
    private MediaRouter2Manager mRouter2Manager;
    private PowerManager.WakeLock mWakeLock;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mRouter2 = MediaRouter2.getInstance(mContext);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);
        mRouter2Manager = MediaRouter2Manager.getInstance(mContext);
        PowerManager powerManager = mContext.getSystemService(PowerManager.class);
        // MediaRouter does not perform scanning when the screen is off, so we need to turn on the
        // screen, and keep it on, to run this test. We use the deprecated FULL_WAKE_LOCK because
        // the test does not use activities, which would enable the use of the recommended approach.
        mWakeLock =
                powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "MediaRouter2DeviceTest");
        mWakeLock.setReferenceCounted(false);
        mWakeLock.acquire();
    }

    @After
    public void tearDown() {
        mWakeLock.release();
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @Test
    public void deduplicationIds_propagateAcrossApps() {
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        // Each app exposes all its routes in a single operation, so if we find one route per app,
        // we know the update should contain all the routes from each helper app.
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        preference,
                        Set.of(
                                ROUTE_ID_APP_1_ROUTE_1,
                                ROUTE_ID_APP_2_ROUTE_1,
                                ROUTE_ID_APP_3_ROUTE_1));

        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_1).getDeduplicationIds()).isEmpty();
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_2).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_1));
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_3).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_2));
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_1).getDeduplicationIds()).isEmpty();
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_2).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_1));
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_3).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_3));
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_1).getDeduplicationIds()).isEmpty();
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_2).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_2));
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_3).getDeduplicationIds())
                .isEqualTo(
                        Set.of(
                                ROUTE_DEDUPLICATION_ID_1,
                                ROUTE_DEDUPLICATION_ID_2,
                                ROUTE_DEDUPLICATION_ID_3));
    }

    @ApiTest(apis = {"android.media.RouteListingPreference, android.media.MediaRouter2"})
    @Test
    public void setRouteListingPreference_propagatesToManager() {
        List<RouteListingPreference.Item> items =
                List.of(
                        new RouteListingPreference.Item(
                                ROUTE_ID_APP_1_ROUTE_1,
                                /* flags= */ RouteListingPreference.Item.FLAG_ONGOING_SESSION,
                                /* disableReason= */ RouteListingPreference.Item
                                        .DISABLE_REASON_NONE),
                        new RouteListingPreference.Item(
                                ROUTE_ID_APP_2_ROUTE_2,
                                /* flags= */ 0,
                                RouteListingPreference.Item.DISABLE_REASON_NONE),
                        new RouteListingPreference.Item(
                                ROUTE_ID_APP_3_ROUTE_3,
                                /* flags= */ 0,
                                RouteListingPreference.Item.DISABLE_REASON_SUBSCRIPTION_REQUIRED));
        RouteListingPreference routeListingPreference = new RouteListingPreference(items);
        MediaRouter2ManagerCallbackImpl mediaRouter2ManagerCallback =
                new MediaRouter2ManagerCallbackImpl();
        mRouter2Manager.registerCallback(Runnable::run, mediaRouter2ManagerCallback);
        mRouter2.setRouteListingPreference(routeListingPreference);
        mediaRouter2ManagerCallback.mConditionVariable.block();
        RouteListingPreference receivedRouteListingPreference =
                mediaRouter2ManagerCallback.mRouteListingPreference;
        Truth.assertThat(receivedRouteListingPreference).isEqualTo(routeListingPreference);
        Truth.assertThat(receivedRouteListingPreference)
                .isNotSameInstanceAs(routeListingPreference);
        Truth.assertThat(receivedRouteListingPreference)
                .isSameInstanceAs(
                        mRouter2Manager.getRouteListingPreference(mContext.getPackageName()));
        Truth.assertThat(receivedRouteListingPreference.getItems().get(0).getFlags())
                .isEqualTo(RouteListingPreference.Item.FLAG_ONGOING_SESSION);
        Truth.assertThat(receivedRouteListingPreference.getItems().get(1).getDisableReason())
                .isEqualTo(RouteListingPreference.Item.DISABLE_REASON_NONE);
        Truth.assertThat(receivedRouteListingPreference.getItems().get(2).getDisableReason())
                .isEqualTo(RouteListingPreference.Item.DISABLE_REASON_SUBSCRIPTION_REQUIRED);
    }

    /**
     * Returns the next route list received via {@link MediaRouter2.RouteCallback#onRoutesUpdated}
     * that includes all the given {@code expectedRouteIds}.
     *
     * <p>Will only wait for up to {@link #ROUTE_UPDATE_MAX_WAIT_MS}.
     */
    private Map<String, MediaRoute2Info> waitForAndGetRoutes(
            RouteDiscoveryPreference preference, Set<String> expectedRouteIds) {
        ConditionVariable condition = new ConditionVariable();
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(List<MediaRoute2Info> routes) {
                        Set<String> receivedRouteIds =
                                routes.stream()
                                        .map(MediaRoute2Info::getOriginalId)
                                        .collect(Collectors.toSet());
                        if (receivedRouteIds.containsAll(expectedRouteIds)) {
                            condition.open();
                        }
                    }
                };

        mRouter2.registerRouteCallback(
                Executors.newSingleThreadExecutor(), routeCallback, preference);
        try {
            Truth.assertThat(condition.block(ROUTE_UPDATE_MAX_WAIT_MS)).isTrue();
            return mRouter2.getRoutes().stream()
                    .collect(Collectors.toMap(MediaRoute2Info::getOriginalId, Function.identity()));
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    private class MediaRouter2ManagerCallbackImpl implements MediaRouter2Manager.Callback {

        private ConditionVariable mConditionVariable = new ConditionVariable();
        private RouteListingPreference mRouteListingPreference;

        @Override
        public void onRouteListingPreferenceUpdated(
                String packageName, RouteListingPreference routeListingPreference) {
            if (packageName.equals(mContext.getPackageName()) && routeListingPreference != null) {
                mRouteListingPreference = routeListingPreference;
                mConditionVariable.open();
            }
        }
    }
}
