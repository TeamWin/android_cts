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

package android.media.router.cts.provider1;

import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_1_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_1_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_1_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_3;
import static android.media.cts.MediaRouterTestConstants.SHARED_ROUTE_ID_1;
import static android.media.cts.MediaRouterTestConstants.SHARED_ROUTE_ID_3;

import android.content.Intent;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StubMediaRoute2ProviderService1 extends MediaRoute2ProviderService {
    private static final String TAG = "SampleMR2ProviderSvc1";
    private static final Object sLock = new Object();

    Map<String, MediaRoute2Info> mRoutes = new HashMap<>();

    public void initializeRoutes() {
        MediaRoute2Info route1 =
                new MediaRoute2Info.Builder(ROUTE_ID_1_1, ROUTE_NAME_1)
                        .addFeature(FEATURE_SAMPLE)
                        .build();
        MediaRoute2Info route2 =
                new MediaRoute2Info.Builder(ROUTE_ID_1_2, ROUTE_NAME_2)
                        .addFeature(FEATURE_SAMPLE)
                        .setDeduplicationIds(Set.of(SHARED_ROUTE_ID_1))
                        .build();
        MediaRoute2Info route3 =
                new MediaRoute2Info.Builder(ROUTE_ID_1_3, ROUTE_NAME_3)
                        .addFeature(FEATURE_SAMPLE)
                        .setDeduplicationIds(Set.of(SHARED_ROUTE_ID_3))
                        .build();
        mRoutes.put(route1.getId(), route1);
        mRoutes.put(route2.getId(), route2);
        mRoutes.put(route3.getId(), route3);
    }

    @Override
    public IBinder onBind(Intent intent) {
        initializeRoutes();
        return super.onBind(intent);
    }

    // Don't implement these methods since we will only test discovery preferences
    @Override
    public void onSetRouteVolume(long requestId, String routeId, int volume) {}

    @Override
    public void onSetSessionVolume(long requestId, String sessionId, int volume) {}

    @Override
    public void onCreateSession(
            long requestId, String packageName, String routeId, Bundle sessionHints) {}

    @Override
    public void onReleaseSession(long requestId, String sessionId) {}

    @Override
    public void onSelectRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onDeselectRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onTransferToRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onDiscoveryPreferenceChanged(RouteDiscoveryPreference preference) {
        publishRoutes();
    }

    void publishRoutes() {
        notifyRoutes(new ArrayList<>(mRoutes.values()));
    }
}
