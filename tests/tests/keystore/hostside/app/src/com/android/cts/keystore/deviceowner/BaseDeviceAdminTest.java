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
package com.android.cts.keystore.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.widget.Toast;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;

/**
 * Base class for device admin based tests.
 *
 * <p>This class handles making sure that the test is the device owner and that it has an
 * active admin registered, so that all tests may assume these are done.
 */
public abstract class BaseDeviceAdminTest extends InstrumentationTestCase {

    public static final class BasicAdminReceiver extends DeviceAdminReceiver {
        void showToast(Context context, CharSequence msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onEnabled(Context context, Intent intent) {
            showToast(context, "Device admin enabled");
        }
        @Override
        public void onDisabled(Context context, Intent intent) {
            showToast(context, "Device admin disabled");
        }
    }

    private static final String TAG = BaseDeviceAdminTest.class.getSimpleName();

    public static final String PACKAGE_NAME = BasicAdminReceiver.class.getPackage().getName();
    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            PACKAGE_NAME, BasicAdminReceiver.class.getName());

    protected DevicePolicyManager mDevicePolicyManager;

    protected final String mTag = getClass().getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context mContext = getInstrumentation().getContext();

        boolean isDeviceOwnerTest = "DeviceOwner"
                .equals(InstrumentationRegistry.getArguments().getString("admin_type"));

        mDevicePolicyManager = TestAppSystemServiceFactory.getDevicePolicyManager(mContext,
                BasicAdminReceiver.class, isDeviceOwnerTest);

        Log.v(TAG, "setup(): dpm for " + getClass() + " and user " + mContext.getUserId() + ": "
                + mDevicePolicyManager);
        assertWithMessage("Device policy manager should not be NULL for "
                + ADMIN_RECEIVER_COMPONENT).that(mDevicePolicyManager).isNotNull();

        boolean isActiveAdmin = mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT);
        boolean isDeviceOwner = mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME);

        Log.d(mTag, "setup() on user " + mContext.getUserId() + ": package=" + PACKAGE_NAME
                + ", adminReceiverComponent=" + ADMIN_RECEIVER_COMPONENT
                + ", isActiveAdmin=" + isActiveAdmin
                + ", isDeviceOwner=" + isDeviceOwner + ", isDeviceOwnerTest=" + isDeviceOwnerTest);

        assertWithMessage("Expected to be an active admin with component %s",
                ADMIN_RECEIVER_COMPONENT).that(isActiveAdmin).isTrue();

        assertWithMessage("device owner for %s", PACKAGE_NAME)
                .that(isDeviceOwner).isTrue();
    }
}
