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
package com.android.cts.managedprofile;

import static com.google.common.truth.Truth.assertThat;

import java.util.Set;

/**
 * This class contains tests for cross profile calendar related features. Most of the tests in
 * this class will need different setups, so the tests will be run separately.
 */
public class CrossProfileCalendarTest extends BaseManagedProfileTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Make sure we are running in a managed profile.
        assertThat(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT)).isTrue();
        assertThat(mDevicePolicyManager.isProfileOwnerApp(
                ADMIN_RECEIVER_COMPONENT.getPackageName())).isTrue();
    }

    public void testCrossPrfileCalendarPackage() throws Exception {
        final String TEST_PACKAGE_NAME = "test.calendar.package.name";
        Set<String> whitelist = mDevicePolicyManager.getCrossProfileCalendarPackages(
                ADMIN_RECEIVER_COMPONENT);
        assertThat(whitelist).isEmpty();

        mDevicePolicyManager.addCrossProfileCalendarPackage(
                ADMIN_RECEIVER_COMPONENT, TEST_PACKAGE_NAME);
        whitelist = mDevicePolicyManager.getCrossProfileCalendarPackages(
                ADMIN_RECEIVER_COMPONENT);
        assertThat(whitelist.size()).isEqualTo(1);
        assertThat(whitelist.contains(TEST_PACKAGE_NAME)).isTrue();

        assertThat(mDevicePolicyManager.removeCrossProfileCalendarPackage(
                ADMIN_RECEIVER_COMPONENT, TEST_PACKAGE_NAME)).isTrue();
        whitelist = mDevicePolicyManager.getCrossProfileCalendarPackages(
                ADMIN_RECEIVER_COMPONENT);
        assertThat(whitelist).isEmpty();
    }
}
