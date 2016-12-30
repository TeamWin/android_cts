/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.platform.test.annotations.RequiresDevice;

/**
 * Set of tests for pure (non-managed) profile owner use cases that also apply to device owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTest.
 */
public class MixedProfileOwnerTest extends DeviceAndProfileOwnerTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (mHasFeature) {
            mUserId = mPrimaryUserId;

            installAppAsUser(DEVICE_ADMIN_APK, mUserId);
            if (!setProfileOwner(
                    DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId,
                    /*expectFailure*/ false)) {
                removeAdmin(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
                getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
                fail("Failed to set profile owner");
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove profile owner.",
                    removeAdmin(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId));
        }
        super.tearDown();
    }

    /**
     * Require a device for tests that use the network stack. Headless Android setups running in
     * data centres may need their network rules un-tampered-with in order to keep the ADB / VNC
     * connection alive.
     *
     * This is only a problem on device owner / profile owner running on USER_SYSTEM, because
     * network rules for this user will affect UID 0.
     */

    @Override @RequiresDevice
    public void testAlwaysOnVpn() throws Exception {
        super.testAlwaysOnVpn();
    }

    @Override @RequiresDevice
    public void testAlwaysOnVpnLockDown() throws Exception {
        super.testAlwaysOnVpnLockDown();
    }

    @Override @RequiresDevice
    public void testAlwaysOnVpnPackageUninstalled() throws Exception {
        super.testAlwaysOnVpnPackageUninstalled();
    }
}
