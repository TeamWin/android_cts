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

import android.app.admin.DevicePolicyManager;
import android.os.UserManager;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class PrivateDnsPolicyTest extends BaseDeviceOwnerTest {
    private UserManager mUserManager;

    @Override
    protected void setUp() {
        mUserManager = mContext.getSystemService(UserManager.class);
        assertNotNull(mUserManager);
    }

    @Override
    protected void tearDown() {
        setUserRestriction(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false);
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
}
