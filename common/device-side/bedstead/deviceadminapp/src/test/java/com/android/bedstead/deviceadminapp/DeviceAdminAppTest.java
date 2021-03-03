/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.deviceadminapp;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceAdminAppTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private static final TestApis sTestApis = new TestApis();

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // This test assumes that DeviceAdminApp is set as a dependency of the test

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    @RequireRunOnPrimaryUser
    public void setAsDeviceOwner_isEnabled() throws Exception {
        ShellCommand.builder("dpm set-device-owner")
                .addOperand(DeviceAdminApp.deviceAdminComponentName(mContext).flattenToString())
                .allowEmptyOutput(true)
                .execute();
        try {
            EventLogs<DeviceAdminEnabledEvent> logs =
                    DeviceAdminEnabledEvent.queryPackage(mContext.getPackageName());

            assertThat(logs.poll()).isNotNull();
        } finally {
            ShellCommand.builder("dpm remove-active-admin")
                    .addOperand(DeviceAdminApp.deviceAdminComponentName(mContext).flattenToString())
                    .allowEmptyOutput(true)
                    .execute();
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasNoWorkProfile
    public void setAsProfileOwner_isEnabled() throws Exception {
        UserReference profile = sTestApis.users().createUser()
                .parent(sTestApis.users().instrumented())
                .type(sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sTestApis.packages().find(mContext.getPackageName()).install(profile);

        try {
            ShellCommand.builder("dpm set-profile-owner")
                    .addOption("--user", profile.id())
                    .addOperand(DeviceAdminApp.deviceAdminComponentName(mContext).flattenToString())
                    .allowEmptyOutput(true)
                    .execute();

            EventLogs<DeviceAdminEnabledEvent> logs =
                    DeviceAdminEnabledEvent.queryPackage(mContext.getPackageName())
                    .onUser(profile);

            assertThat(logs.poll()).isNotNull();
        } finally {
            profile.remove();
        }
    }
}
