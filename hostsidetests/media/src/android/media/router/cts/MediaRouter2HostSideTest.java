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

package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_CLASS;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_PACKAGE;

import android.media.cts.BaseMediaHostSideTest;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;

import com.android.compatibility.common.util.ApiTest;

/** Installs route provider apps and runs tests in {@link MediaRouter2DeviceTest}. */
public class MediaRouter2HostSideTest extends BaseMediaHostSideTest {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        installApp(MEDIA_ROUTER_PROVIDER_1_APK);
        installApp(MEDIA_ROUTER_PROVIDER_2_APK);
        installApp(MEDIA_ROUTER_PROVIDER_3_APK);
        installApp(MEDIA_ROUTER_TEST_APK);
    }

    @Override
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(MEDIA_ROUTER_PROVIDER_1_PACKAGE);
        getDevice().uninstallPackage(MEDIA_ROUTER_PROVIDER_2_PACKAGE);
        getDevice().uninstallPackage(MEDIA_ROUTER_PROVIDER_3_PACKAGE);
        getDevice().uninstallPackage(MEDIA_ROUTER_TEST_PACKAGE);
        super.tearDown();
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    public void testDeduplicationIds_propagateAcrossApps() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "deduplicationIds_propagateAcrossApps");
    }

    @ApiTest(apis = {"android.media.RouteListingPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    public void testSetRouteListingPreference_propagatesToManager() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "setRouteListingPreference_propagatesToManager");
    }
}
