/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_APP_APK;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_APP_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_APP_TEST_CLASS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import com.google.common.truth.Expect;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

@RunWith(DeviceJUnit4ClassRunner.class)
public class ProxyMediaRouter2HostSideTest extends BaseHostJUnit4Test {

    @ClassRule public static final Expect expect = Expect.create();

    @BeforeClassWithInfo
    public static void installTestApps(TestInformation testInfo)
            throws DeviceNotAvailableException, FileNotFoundException {
        installTestApp(testInfo, PROXY_MEDIA_ROUTER_APP_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_1_APK);
    }

    @AfterClassWithInfo
    public static void uninstallTestApps(TestInformation testInformation)
            throws DeviceNotAvailableException {
        ITestDevice device = testInformation.getDevice();
        expect.that(device.uninstallPackage(MEDIA_ROUTER_PROVIDER_1_PACKAGE)).isNull();
        expect.that(device.uninstallPackage(PROXY_MEDIA_ROUTER_APP_PACKAGE)).isNull();
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withMediaRoutingControl_flagEnabled_doesNotThrow()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_APP_TEST_CLASS,
                "getInstance_withMediaRoutingControl_flagEnabled_doesNotThrow");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withMediaRoutingControl_flagDisabled_throwsSecurityException()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_APP_TEST_CLASS,
                "getInstance_withMediaRoutingControl_flagDisabled_throwsSecurityException");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withoutMediaRoutingControl_throwsSecurityException()
            throws DeviceNotAvailableException, FileNotFoundException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_APP_TEST_CLASS,
                "getInstance_withoutMediaRoutingControl_throwsSecurityException");
    }

    private static void installTestApp(TestInformation testInfo, String apkName)
            throws FileNotFoundException, DeviceNotAvailableException {
        LogUtil.CLog.d("Installing app " + apkName);
        CompatibilityBuildHelper buildHelper =
                new CompatibilityBuildHelper(testInfo.getBuildInfo());
        final String result =
                testInfo.getDevice()
                        .installPackage(
                                buildHelper.getTestFile(apkName),
                                /* reinstall= */ true,
                                /* grantPermissions= */ true,
                                /*allow test apps*/ "-t");
        assertWithMessage("Failed to install " + apkName + ": " + result).that(result).isNull();
    }
}
