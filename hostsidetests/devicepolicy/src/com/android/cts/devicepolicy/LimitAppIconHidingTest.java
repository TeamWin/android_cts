/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import java.util.Collections;

/**
 * Set of tests for the limit app icon hiding feature.
 */
public class LimitAppIconHidingTest extends BaseLauncherAppsTest {

    private static final String LAUNCHER_TESTS_NO_LAUNCHABLE_ACTIVITY_APK =
            "CtsNoLaunchableActivityApp.apk";

    private boolean mHasLauncherApps;
    private String mSerialNumber;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHasLauncherApps = getDevice().getApiLevel() >= 21;

        if (mHasLauncherApps) {
            mSerialNumber = Integer.toString(getUserSerialNumber(USER_SYSTEM));
            installTestApps();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasLauncherApps) {
            uninstallTestApps();
        }
        super.tearDown();
    }

    @Override
    protected void installTestApps() throws Exception {
        super.installTestApps();
        installAppAsUser(LAUNCHER_TESTS_NO_LAUNCHABLE_ACTIVITY_APK, mPrimaryUserId);
    }

    @Override
    protected void uninstallTestApps() throws Exception {
        super.uninstallTestApps();
        getDevice().uninstallPackage(LAUNCHER_TESTS_NO_LAUNCHABLE_ACTIVITY_APK);
    }

    public void testNoLaunchableActivityAppHasAppDetailsActivityInjected() throws Exception {
        if (!mHasLauncherApps) {
            return;
        }
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testNoLaunchableActivityAppHasAppDetailsActivityInjected",
                mPrimaryUserId, Collections.singletonMap(PARAM_TEST_USER, mSerialNumber));
    }

    public void testNoSystemAppHasSyntheticAppDetailsActivityInjected() throws Exception {
        if (!mHasLauncherApps) {
            return;
        }
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testNoSystemAppHasSyntheticAppDetailsActivityInjected",
                mPrimaryUserId, Collections.singletonMap(PARAM_TEST_USER, mSerialNumber));
    }
}
