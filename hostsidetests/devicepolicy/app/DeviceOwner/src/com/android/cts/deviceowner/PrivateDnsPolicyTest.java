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

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

public class PrivateDnsPolicyTest extends BaseDeviceOwnerTest {
    private static final String PRIVATE_DNS_HOST = "resolver.example.com";

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

    public void testCannotSetOffMode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mDevicePolicyManager.setGlobalPrivateDns(getWho(),
                        PRIVATE_DNS_MODE_OFF, null)
        );
    }

    public void testSetOpportunisticMode() {
        mDevicePolicyManager.setGlobalPrivateDns(getWho(),
                PRIVATE_DNS_MODE_OPPORTUNISTIC, null);
        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsMode(getWho())).isEqualTo(
                PRIVATE_DNS_MODE_OPPORTUNISTIC);
        assertThat(mDevicePolicyManager.getGlobalPrivateDnsHost(getWho())).isNull();
    }

    public void testSetSpecificHostMode() {
        mDevicePolicyManager.setGlobalPrivateDns(getWho(),
                PRIVATE_DNS_MODE_PROVIDER_HOSTNAME, PRIVATE_DNS_HOST);

        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsMode(getWho())).isEqualTo(
                PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);
        assertThat(
                mDevicePolicyManager.getGlobalPrivateDnsHost(getWho())).isEqualTo(
                PRIVATE_DNS_HOST);
    }
}
