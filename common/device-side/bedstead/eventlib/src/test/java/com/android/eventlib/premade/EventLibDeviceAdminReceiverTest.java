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

package com.android.eventlib.premade;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.users.UserReference;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventLibDeviceAdminReceiverTest {

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final ComponentName DEVICE_ADMIN_COMPONENT =
            new ComponentName(
                    sContext.getPackageName(), EventLibDeviceAdminReceiver.class.getName());
    private static final TestApis sTestApis = new TestApis();
    private static final UserReference sUser = sTestApis.users().instrumented();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void enableDeviceOwner_logsEnabledEvent() {
        DeviceOwner deviceOwner =
                sTestApis.devicePolicy().setDeviceOwner(sUser, DEVICE_ADMIN_COMPONENT);

        try {
            EventLogs<DeviceAdminEnabledEvent> eventLogs =
                    DeviceAdminEnabledEvent.queryPackage(sContext.getPackageName());

            assertThat(eventLogs.poll()).isNotNull();
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    public void enableProfileOwner_logsEnabledEvent() {
        ProfileOwner profileOwner =
                sTestApis.devicePolicy().setProfileOwner(sUser, DEVICE_ADMIN_COMPONENT);

        try {
            EventLogs<DeviceAdminEnabledEvent> eventLogs =
                    DeviceAdminEnabledEvent.queryPackage(sContext.getPackageName());

            assertThat(eventLogs.poll()).isNotNull();
        } finally {
            profileOwner.remove();
        }
    }

    @Test
    @Ignore("It is not possible to trigger this through tests")
    public void disableProfileOwner_logsDisableRequestedEvent() {
        ProfileOwner profileOwner =
                sTestApis.devicePolicy().setProfileOwner(sUser, DEVICE_ADMIN_COMPONENT);

        try {
            // TODO: Trigger disable device admin

            EventLogs<DeviceAdminDisableRequestedEvent> eventLogs =
                    DeviceAdminDisableRequestedEvent.queryPackage(sContext.getPackageName());

            assertThat(eventLogs.poll()).isNotNull();
        } finally {
            profileOwner.remove();
        }
    }

    @Test
    @Ignore("It is not possible to trigger this through tests")
    public void disableProfileOwner_logsDisabledEvent() {
        ProfileOwner profileOwner =
                sTestApis.devicePolicy().setProfileOwner(sUser, DEVICE_ADMIN_COMPONENT);

        try {
            // TODO: Trigger disable device admin

            EventLogs<DeviceAdminDisabledEvent> eventLogs =
                    DeviceAdminDisabledEvent.queryPackage(sContext.getPackageName());

            assertThat(eventLogs.poll()).isNotNull();
        } finally {
            profileOwner.remove();
        }
    }

}
