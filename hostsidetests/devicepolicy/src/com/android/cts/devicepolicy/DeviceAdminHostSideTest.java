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

import com.android.tradefed.device.DeviceNotAvailableException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * DeviceAdmin host side tests.
 *
 * TODO Add tests for device admin targeting API 23.
 */
public class DeviceAdminHostSideTest extends BaseDevicePolicyTest {
    private static final String DEVICE_ADMIN_APK = "CtsDeviceAdminApp.apk";

    private static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceadmin";

    private static final String ADMIN_RECEIVER_TEST_CLASS = "DeviceAdminTest$AdminReceiver";

    private static final String ADMIN_RECEIVER_COMPONENT =
            DEVICE_ADMIN_PKG + "/." + ADMIN_RECEIVER_TEST_CLASS;

    private static final String UNPROTECTED_ADMIN_RECEIVER_TEST_CLASS =
            "DeviceAdminReceiverWithNoProtection";

    private int mUserId;

    private boolean mDeactivateInTearDown;
    private boolean mClearPasswordInTearDown;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUserId = USER_OWNER;

        mDeactivateInTearDown = false;
        mClearPasswordInTearDown = false;
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            // If a password has been set, we need to register the DA as DO to clear it.
            if (mClearPasswordInTearDown) {
                setDeviceOwner(ADMIN_RECEIVER_COMPONENT);

                assertTrue("Failed to clear password",
                        runTests(DEVICE_ADMIN_PKG, "DeviceAdminTest", "testClearPassword_success"));

                assertTrue("Failed to clear device owner",
                        runTests(DEVICE_ADMIN_PKG, "ClearDeviceOwnerTest"));
            }

            if (mDeactivateInTearDown) {
                assertTrue("Failed to remove device admin", runTests(
                        DEVICE_ADMIN_PKG, "ClearDeviceAdminTest"));
            }
        }

        super.tearDown();
    }

    /** DA can only set a password when there's none, and can't clear it. */
    public void testResetPassword() throws Exception {
        if (!mHasFeature) {
            return;
        }

        mDeactivateInTearDown = true;
        mClearPasswordInTearDown = true;

        installApp(DEVICE_ADMIN_APK);
        setDeviceAdmin(ADMIN_RECEIVER_COMPONENT, mUserId);

        // Can't clear the password, even if there's no passsword set currently.
        assertTrue(runTests(DEVICE_ADMIN_PKG, "DeviceAdminTest", "testClearPassword_failure"));

        // No password -> setting one is okay.
        assertTrue(runTests(DEVICE_ADMIN_PKG, "DeviceAdminTest", "testSetPassword_success"));

        // But once set, DA can't change the password.
        assertTrue(runTests(DEVICE_ADMIN_PKG, "DeviceAdminTest", "testSetPassword_failure"));
    }

    /** Device admin must be protected with BIND_DEVICE_ADMIN */
    public void testAdminWithNoProtection() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installApp(DEVICE_ADMIN_APK);
        setDeviceAdminExpectingFailure(DEVICE_ADMIN_PKG + "/." +
                UNPROTECTED_ADMIN_RECEIVER_TEST_CLASS, mUserId,
                "must be protected with android.permission.BIND_DEVICE_ADMIN");
    }

    private boolean runTests(@Nonnull String apk, @Nonnull String className,
            @Nullable String method) throws DeviceNotAvailableException {
        return runDeviceTestsAsUser(apk, "." + className, method, mUserId);
    }

    private boolean runTests(@Nonnull String apk, @Nonnull String className)
            throws DeviceNotAvailableException {
        return runTests(apk, className, null);
    }
}
