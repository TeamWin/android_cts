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

package android.keystore.cts.devicepolicy;

import android.keystore.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.TemporarilyIgnoreOnHeadlessSystemUserMode;
import com.android.compatibility.common.util.ApiTest;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Set of tests for use cases that apply to profile and device owner.
 * This class is the base class of MixedProfileOwnerTest, MixedDeviceOwnerTest and
 * MixedManagedProfileOwnerTest and is abstract to avoid running spurious tests.
 *
 * NOTE: Not all tests are executed in the subclasses. Sometimes, if a test is not applicable to
 * a subclass, they override it with an empty method.
 */
public abstract class DeviceAndProfileOwnerTest extends BaseDevicePolicyTest {

    public static final String DEVICE_ADMIN_PKG = "com.android.cts.keystore.deviceowner";
    public static final String DEVICE_ADMIN_APK = "CtsKeystoreDeviceOwnerApp.apk";
    protected static final String ADMIN_RECEIVER_TEST_CLASS
            = ".BaseDeviceAdminTest$BasicAdminReceiver";
    protected static final String DEVICE_ADMIN_COMPONENT_FLATTENED =
            DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS;

    // ID of the user all tests are run as. For device owner this will be the current user, for
    // profile owner it is the user id of the created profile.
    protected int mUserId;

    @Override
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_ADMIN_PKG);

        // Press the HOME key to close any alart dialog that may be shown.
        getDevice().executeShellCommand("input keyevent 3");

        super.tearDown();
    }

    @ApiTest(apis={"android.app.admin.DevicePolicyManager#generateKeyPair",
            "android.app.admin.DevicePolicyManager#ID_TYPE_IMEI",
            "android.app.admin.DevicePolicyManager#ID_TYPE_MEID",
            "android.app.admin.DevicePolicyManager#ID_TYPE_SERIAL"})
    @TemporarilyIgnoreOnHeadlessSystemUserMode(bugId = "197859595",
            reason = "Will be migrated to new test infra")
    @Test
    public void testKeyManagement() throws Exception {
        executeDeviceTestClass(".KeyManagementTest");
    }

    protected void executeDeviceTestClass(String className) throws Exception {
        executeDeviceTestMethod(className, /* testName= */ null);
    }

    protected void executeDeviceTestClass(String className, int userId) throws Exception {
        executeDeviceTestMethod(className, /* testName= */ null, userId);
    }

    protected void executeDeviceTestMethod(String className, String testName) throws Exception {
        executeDeviceTestMethod(className, testName, /* params= */ new HashMap<>());
    }

    protected void executeDeviceTestMethod(String className, String testName, int userId)
            throws Exception {
        executeDeviceTestMethod(className, testName, userId, /* params= */ new HashMap<>());
    }

    protected void executeDeviceTestMethod(String className, String testName,
            Map<String, String> params) throws Exception {
        executeDeviceTestMethod(className, testName, mUserId, params);
    }

    protected void executeDeviceTestMethod(String className, String testName, int userId,
            Map<String, String> params) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, className, testName, userId, params);
    }
}
