/*
 * Copyright 2022 The Android Open Source Project
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

import java.util.List;

public class MediaRouterTestConstants {
    public static final String MEDIA_ROUTER_PROVIDER_1_PACKAGE =
            "android.media.router.cts.provider1";
    public static final String MEDIA_ROUTER_PROVIDER_1_APK = "CtsMediaRouterProviderApp1.apk";
    public static final String MEDIA_ROUTER_PROVIDER_2_PACKAGE =
            "android.media.router.cts.provider2";
    public static final String MEDIA_ROUTER_PROVIDER_2_APK = "CtsMediaRouterProviderApp2.apk";
    public static final String MEDIA_ROUTER_PROVIDER_3_PACKAGE =
            "android.media.router.cts.provider3";
    public static final String MEDIA_ROUTER_PROVIDER_3_APK = "CtsMediaRouterProviderApp3.apk";
    public static final String MEDIA_ROUTER_TEST_PACKAGE =
            "android.media.router.cts";

    public static final String DEVICE_SIDE_TEST_CLASS =
            "android.media.router.cts.MediaRouter2Test";

    public static final String MEDIA_ROUTER_TEST_APK = "CtsMediaRouterTest.apk";

    public static final String ROUTE_ID_1_1 = "media_route_1-1";
    public static final String ROUTE_ID_1_2 = "media_route_1-2";
    public static final String ROUTE_ID_1_3 = "media_route_1-3";

    public static final String ROUTE_ID_2_1 = "media_route_2-1";
    public static final String ROUTE_ID_2_2 = "media_route_2-2";
    public static final String ROUTE_ID_2_3 = "media_route_2-3";

    public static final String ROUTE_ID_3_1 = "media_route_3-1";
    public static final String ROUTE_ID_3_2 = "media_route_3-2";
    public static final String ROUTE_ID_3_3 = "media_route_3-3";

    public static final String ROUTE_NAME_1 = "route 1";
    public static final String ROUTE_NAME_2 = "route 2";
    public static final String ROUTE_NAME_3 = "route 3";

    public static final String SHARED_ROUTE_ID_1 = "media_route_shared_1";
    public static final String SHARED_ROUTE_ID_2 = "media_route_shared_2";
    public static final String SHARED_ROUTE_ID_3 = "media_route_shared_3";

    public static final String FEATURE_SAMPLE = "android.media.cts.FEATURE_SAMPLE";

    public static final List<String> FEATURES_ALL = List.of(FEATURE_SAMPLE);

}
