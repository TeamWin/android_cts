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

package com.android.cts.deviceowner;

import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_OFF;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

import android.app.admin.DevicePolicyManager;
import android.os.UserManager;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

public class PrivateDnsPolicyTest extends BaseDeviceOwnerTest {
    private static final String DUMMY_PRIVATE_DNS_HOST = "resolver.example.com";
    private static final String VALID_PRIVATE_DNS_HOST = "dns.google";

    private UserManager mUserManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUserManager = mContext.getSystemService(UserManager.class);
        assertNotNull(mUserManager);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        setUserRestriction(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false);
        mDevicePolicyManager.setGlobalPrivateDns(getWho(),
                PRIVATE_DNS_MODE_OPPORTUNISTIC, null);
    }

    public void testDisallowPrivateDnsConfigurationRestriction() {
        setUserRestriction(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, true);
        assertThat(mUserManager.hasUserRestriction(
                UserManager.DISALLOW_CONFIG_PRIVATE_DNS)).isTrue();
    }

    public void testClearDisallowPrivateDnsConfigurationRestriction() {
        setUserRestriction(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false);
        assertThat(mUserManager.hasUserRestriction(
                UserManager.DISALLOW_CONFIG_PRIVATE_DNS)).isFalse();
    }

    private void setUserRestriction(String restriction, boolean add) {
        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        if (add) {
            dpm.addUserRestriction(getWho(), restriction);
        } else {
            dpm.clearUserRestriction(getWho(), restriction);
        }
    }

    /**
     * Call DevicePolicyManager.setGlobalPrivateDns with the given mode, host, expecting
     * the result code expectedResult.
     */
    private void callSetGlobalPrivateDnsExpectingResult(int mode, String privateDnsHost,
            int expectedResult) {
        int resultCode = mDevicePolicyManager.setGlobalPrivateDns(getWho(), mode, privateDnsHost);

        assertEquals(
                String.format(
                        "Call to setGlobalPrivateDns with mode %d and host %s "
                                + "should have produced result %d, but was %d",
                        mode, privateDnsHost, expectedResult, resultCode),
                expectedResult, resultCode);
    }

    public void testCannotSetOffMode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mDevicePolicyManager.setGlobalPrivateDns(
                        getWho(), PRIVATE_DNS_MODE_OFF, null));

        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsMode(getWho())).isNotEqualTo(
                PRIVATE_DNS_MODE_OFF);
    }

    public void testSetOpportunisticMode() {
        callSetGlobalPrivateDnsExpectingResult(PRIVATE_DNS_MODE_OPPORTUNISTIC, null,
                DevicePolicyManager.PRIVATE_DNS_SET_SUCCESS);

        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsMode(getWho())).isEqualTo(
                PRIVATE_DNS_MODE_OPPORTUNISTIC);
        assertThat(mDevicePolicyManager.getGlobalPrivateDnsHost(getWho())).isNull();
    }

    public void testSetSpecificHostMode() {
        callSetGlobalPrivateDnsExpectingResult(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME,
                VALID_PRIVATE_DNS_HOST,
                DevicePolicyManager.PRIVATE_DNS_SET_SUCCESS);

        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsMode(getWho())).isEqualTo(
                PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);
        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsHost(getWho())).isEqualTo(
                VALID_PRIVATE_DNS_HOST);
    }

    public void testSetModeWithIncorrectHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mDevicePolicyManager.setGlobalPrivateDns(
                        getWho(), PRIVATE_DNS_MODE_PROVIDER_HOSTNAME, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> mDevicePolicyManager.setGlobalPrivateDns(
                        getWho(), PRIVATE_DNS_MODE_OPPORTUNISTIC, DUMMY_PRIVATE_DNS_HOST));

        // This host does not resolve, so would output an error.
        callSetGlobalPrivateDnsExpectingResult(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME,
                DUMMY_PRIVATE_DNS_HOST,
                DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING);
    }

    public void testCanSetModeDespiteUserRestriction() {
        // First set a specific host and assert that applied.
        callSetGlobalPrivateDnsExpectingResult(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME,
                VALID_PRIVATE_DNS_HOST,
                DevicePolicyManager.PRIVATE_DNS_SET_SUCCESS);
        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsMode(getWho())).isEqualTo(
                PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);

        // Set a user restriction
        setUserRestriction(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, true);

        // Next, set the mode to automatic and confirm that has applied.
        callSetGlobalPrivateDnsExpectingResult(PRIVATE_DNS_MODE_OPPORTUNISTIC, null,
                DevicePolicyManager.PRIVATE_DNS_SET_SUCCESS);

        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsMode(getWho())).isEqualTo(
                PRIVATE_DNS_MODE_OPPORTUNISTIC);
        assertThat(mDevicePolicyManager.getGlobalPrivateDnsHost(getWho())).isNull();
    }
}
