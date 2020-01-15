/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import static com.android.cts.deviceandprofileowner.BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserManager;
import android.test.InstrumentationTestCase;

import org.mockito.internal.util.collections.Sets;

import java.util.Set;

public class OrgOwnedProfileOwnerParentTest extends InstrumentationTestCase {

    protected Context mContext;
    private DevicePolicyManager mParentDevicePolicyManager;
    private DevicePolicyManager mDevicePolicyManager;

    private CameraManager mCameraManager;

    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mParentDevicePolicyManager =
                mDevicePolicyManager.getParentProfileInstance(ADMIN_RECEIVER_COMPONENT);
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        assertNotNull(mDevicePolicyManager);
        assertNotNull(mParentDevicePolicyManager);
        assertNotNull(mCameraManager);

        assertTrue(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT));
        assertTrue(
                mDevicePolicyManager.isProfileOwnerApp(ADMIN_RECEIVER_COMPONENT.getPackageName()));
        assertTrue(mDevicePolicyManager.isManagedProfile(ADMIN_RECEIVER_COMPONENT));
        startBackgroundThread();
    }

    @Override
    protected void tearDown() throws Exception {
        stopBackgroundThread();
        super.tearDown();
    }

    public void testSetAndGetCameraDisabled_onParent() throws Exception {
        mParentDevicePolicyManager.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, true);
        boolean actualDisabled =
                mParentDevicePolicyManager.getCameraDisabled(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualDisabled).isTrue();
        checkCanOpenCamera(false);

        mParentDevicePolicyManager.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, false);
        actualDisabled = mParentDevicePolicyManager.getCameraDisabled(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualDisabled).isFalse();
        checkCanOpenCamera(true);
    }

    private static final Set<String> PROFILE_OWNER_ORGANIZATION_OWNED_GLOBAL_RESTRICTIONS =
            Sets.newSet(
                    UserManager.DISALLOW_CONFIG_DATE_TIME,
                    UserManager.DISALLOW_ADD_USER,
                    UserManager.DISALLOW_BLUETOOTH,
                    UserManager.DISALLOW_BLUETOOTH_SHARING,
                    UserManager.DISALLOW_CONFIG_BLUETOOTH,
                    UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
                    UserManager.DISALLOW_CONFIG_LOCATION,
                    UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
                    UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
                    UserManager.DISALLOW_CONFIG_TETHERING,
                    UserManager.DISALLOW_CONFIG_WIFI,
                    UserManager.DISALLOW_CONTENT_CAPTURE,
                    UserManager.DISALLOW_CONTENT_SUGGESTIONS,
                    UserManager.DISALLOW_DATA_ROAMING,
                    UserManager.DISALLOW_DEBUGGING_FEATURES,
                    UserManager.DISALLOW_SAFE_BOOT,
                    UserManager.DISALLOW_SHARE_LOCATION,
                    UserManager.DISALLOW_SMS,
                    UserManager.DISALLOW_USB_FILE_TRANSFER
            );

    public void testAddGetAndClearUserRestriction_onParent() {
        for (String restriction : PROFILE_OWNER_ORGANIZATION_OWNED_GLOBAL_RESTRICTIONS) {
            testAddGetAndClearUserRestriction_onParent(restriction);
        }
    }

    private void testAddGetAndClearUserRestriction_onParent(String restriction) {
        mParentDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);

        Bundle restrictions = mParentDevicePolicyManager.getUserRestrictions(
                ADMIN_RECEIVER_COMPONENT);
        assertThat(restrictions.get(restriction)).isNotNull();

        mParentDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);

        restrictions = mParentDevicePolicyManager.getUserRestrictions(ADMIN_RECEIVER_COMPONENT);
        assertThat(restrictions.get(restriction)).isNull();
    }

    private void checkCanOpenCamera(boolean canOpen) throws Exception {
        // If the device does not support a camera it will return an empty camera ID list.
        if (mCameraManager.getCameraIdList() == null
                || mCameraManager.getCameraIdList().length == 0) {
            return;
        }
        int retries = 10;
        boolean successToOpen = !canOpen;
        while (successToOpen != canOpen && retries > 0) {
            retries--;
            Thread.sleep(500);
            successToOpen = CameraUtils
                    .blockUntilOpenCamera(mCameraManager, mBackgroundHandler);
        }
        assertEquals(String.format("Timed out waiting the value to change to %b (actual=%b)",
                canOpen, successToOpen), canOpen, successToOpen);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}