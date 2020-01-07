/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;
import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.isStatsdEnabled;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.platform.test.annotations.FlakyTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Set of tests for device owner use cases that also apply to profile owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTest.
 */
public class MixedDeviceOwnerTest extends DeviceAndProfileOwnerTest {

    private static final String DELEGATION_NETWORK_LOGGING = "delegation-network-logging";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        if (mHasFeature) {
            mUserId = mPrimaryUserId;

            installAppAsUser(DEVICE_ADMIN_APK, mUserId);
            if (!setDeviceOwner(DEVICE_ADMIN_COMPONENT_FLATTENED, mUserId, /*expectFailure*/
                    false)) {
                removeAdmin(DEVICE_ADMIN_COMPONENT_FLATTENED, mUserId);
                getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
                fail("Failed to set device owner");
            }
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove device owner",
                    removeAdmin(DEVICE_ADMIN_COMPONENT_FLATTENED, mUserId));
        }
        super.tearDown();
    }

    @Test
    public void testLockTask_unaffiliatedUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }

        final int userId = createSecondaryUserAsProfileOwner();
        runDeviceTestsAsUser(
                DEVICE_ADMIN_PKG,
                ".AffiliationTest",
                "testLockTaskMethodsThrowExceptionIfUnaffiliated",
                userId);

        setUserAsAffiliatedUserToPrimary(userId);
        runDeviceTestsAsUser(
                DEVICE_ADMIN_PKG,
                ".AffiliationTest",
                "testSetLockTaskPackagesClearedIfUserBecomesUnaffiliated",
                userId);
    }

    @FlakyTest(bugId = 127270520)
    @Test
    public void testLockTask_affiliatedSecondaryUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }
        final int userId = createSecondaryUserAsProfileOwner();
        switchToUser(userId);
        setUserAsAffiliatedUserToPrimary(userId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockTaskTest", userId);
    }

    @Test
    public void testDelegatedCertInstallerDeviceIdAttestation() throws Exception {
        if (!mHasFeature) {
            return;
        }

        setUpDelegatedCertInstallerAndRunTests(() ->
                runDeviceTestsAsUser("com.android.cts.certinstaller",
                        ".DelegatedDeviceIdAttestationTest",
                        "testGenerateKeyPairWithDeviceIdAttestationExpectingSuccess", mUserId));
    }

    @FlakyTest
    @Override
    @Test
    public void testCaCertManagement() throws Exception {
        super.testCaCertManagement();
    }

    @FlakyTest(bugId = 141161038)
    @Override
    @Test
    public void testCannotRemoveUserIfRestrictionSet() throws Exception {
        super.testCannotRemoveUserIfRestrictionSet();
    }

    @FlakyTest
    @Override
    @Test
    public void testInstallCaCertLogged() throws Exception {
        super.testInstallCaCertLogged();
    }

    @FlakyTest(bugId = 137088260)
    @Test
    public void testWifi() throws Exception {
        if (!mHasFeature || !hasDeviceFeature("android.hardware.wifi")) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".WifiTest", "testGetWifiMacAddress", mUserId);
        if (isStatsdEnabled(getDevice())) {
            assertMetricsLogged(getDevice(), () -> {
                executeDeviceTestMethod(".WifiTest", "testGetWifiMacAddress");
            }, new DevicePolicyEventWrapper.Builder(EventId.GET_WIFI_MAC_ADDRESS_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .build());
        }
    }

    @Test
    public void testSetTime() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestMethod(".TimeManagementTest", "testSetTime");
        executeDeviceTestMethod(".TimeManagementTest", "testSetTime_failWhenAutoTimeEnabled");
    }

    @Test
    public void testSetTimeZone() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestMethod(".TimeManagementTest", "testSetTimeZone");
        executeDeviceTestMethod(".TimeManagementTest", "testSetTimeZone_failIfAutoTimeZoneEnabled");
    }

    Map<String, DevicePolicyEventWrapper[]> getAdditionalDelegationTests() {
        final Map<String, DevicePolicyEventWrapper[]> result = new HashMap<>();
        DevicePolicyEventWrapper[] expectedMetrics = new DevicePolicyEventWrapper[] {
                new DevicePolicyEventWrapper.Builder(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                        .setAdminPackageName(DELEGATE_APP_PKG)
                        .setBoolean(true)
                        .setInt(1)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.RETRIEVE_NETWORK_LOGS_VALUE)
                        .setAdminPackageName(DELEGATE_APP_PKG)
                        .setBoolean(true)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                        .setAdminPackageName(DELEGATE_APP_PKG)
                        .setBoolean(true)
                        .setInt(0)
                        .build(),
        };
        result.put(".NetworkLoggingDelegateTest", expectedMetrics);
        return result;
    }

    @Override
    List<String> getAdditionalDelegationScopes() {
        final List<String> result = new ArrayList<>();
        result.add(DELEGATION_NETWORK_LOGGING);
        return result;
    }

    @Test
    public void testLockScreenInfo() throws Exception {
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockScreenInfoTest", mUserId);

        if (isStatsdEnabled(getDevice())) {
            assertMetricsLogged(getDevice(), () -> {
                executeDeviceTestMethod(".LockScreenInfoTest", "testSetAndGetLockInfo");
            }, new DevicePolicyEventWrapper.Builder(EventId.SET_DEVICE_OWNER_LOCK_SCREEN_INFO_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .build());
        }
    }

    private int createSecondaryUserAsProfileOwner() throws Exception {
        final int userId = createUser();
        installAppAsUser(INTENT_RECEIVER_APK, userId);
        installAppAsUser(DEVICE_ADMIN_APK, userId);
        setProfileOwnerOrFail(DEVICE_ADMIN_COMPONENT_FLATTENED, userId);
        return userId;
    }

    private void switchToUser(int userId) throws Exception {
        switchUser(userId);
        waitForBroadcastIdle();
        wakeupAndDismissKeyguard();
    }

    private void setUserAsAffiliatedUserToPrimary(int userId) throws Exception {
        // Setting the same affiliation ids on both users
        runDeviceTestsAsUser(
                DEVICE_ADMIN_PKG, ".AffiliationTest", "testSetAffiliationId1", mPrimaryUserId);
        runDeviceTestsAsUser(
                DEVICE_ADMIN_PKG, ".AffiliationTest", "testSetAffiliationId1", userId);
    }
}
