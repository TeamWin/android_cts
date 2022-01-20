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

package android.car.cts.builtin.util;

import android.car.builtin.util.EventLogHelper;

import org.junit.Before;
import org.junit.Test;

public final class EventLogHelperTest {

    private static final int TIMEOUT_MS = 10_000;

    @Before
    public void setup() {
        LogcatHelper.clearLog();
    }

    @Test
    public void testWriteCarHelperStart() {
        EventLogHelper.writeCarHelperStart();

        assertLogMessage("I car_helper_start:");
    }

    @Test
    public void testWriteCarHelperBootPhase() {
        EventLogHelper.writeCarHelperBootPhase(1);

        assertLogMessage("I car_helper_boot_phase: 1");
    }

    @Test
    public void testWriteCarHelperUserStarting() {
        EventLogHelper.writeCarHelperUserStarting(100);

        assertLogMessage("I car_helper_user_starting: 100");
    }

    @Test
    public void testWriteCarHelperUserSwitching() {
        EventLogHelper.writeCarHelperUserSwitching(100, 101);

        assertLogMessage("I car_helper_user_switching: [100,101]");
    }

    @Test
    public void testWriteCarHelperUserUnlocking() {
        EventLogHelper.writeCarHelperUserUnlocking(100);

        assertLogMessage("I car_helper_user_unlocking: 100");
    }

    @Test
    public void testWriteCarHelperUserUnlocked() {
        EventLogHelper.writeCarHelperUserUnlocked(100);

        assertLogMessage("I car_helper_user_unlocked: 100");
    }

    @Test
    public void testWriteCarHelperUserStopping() {
        EventLogHelper.writeCarHelperUserStopping(100);

        assertLogMessage("I car_helper_user_stopping: 100");
    }

    @Test
    public void testWriteCarHelperUserStopped() {
        EventLogHelper.writeCarHelperUserStopped(100);

        assertLogMessage("I car_helper_user_stopped: 100");
    }

    @Test
    public void testWriteCarHelperServiceConnected() {
        EventLogHelper.writeCarHelperServiceConnected();

        assertLogMessage("I car_helper_svc_connected");
    }

    @Test
    public void testWriteCarServiceInit() {
        EventLogHelper.writeCarServiceInit(101);

        assertLogMessage("I car_service_init: 101");
    }

    @Test
    public void testWriteCarServiceVhalReconnected() {
        EventLogHelper.writeCarServiceVhalReconnected(101);

        assertLogMessage("I car_service_vhal_reconnected: 101");
    }

    @Test
    public void testWriteCarServiceSetCarServiceHelper() {
        EventLogHelper.writeCarServiceSetCarServiceHelper(101);

        assertLogMessage("I car_service_set_car_service_helper: 101");
    }

    @Test
    public void tesWriteCarServiceOnUserLifecycle() {
        EventLogHelper.writeCarServiceOnUserLifecycle(1, 2, 3);

        assertLogMessage("I car_service_on_user_lifecycle: [1,2,3]");
    }

    @Test
    public void testWriteCarServiceCreate() {
        EventLogHelper.writeCarServiceCreate(true);

        assertLogMessage("I car_service_create: 1");
    }

    @Test
    public void testWriteCarServiceConnected() {
        EventLogHelper.writeCarServiceConnected("testString");

        assertLogMessage("I car_service_connected: testString");
    }

    @Test
    public void testWriteCarServiceDestroy() {
        EventLogHelper.writeCarServiceDestroy(true);

        assertLogMessage("I car_service_destroy: 1");
    }

    @Test
    public void testWriteCarServiceVhalDied() {
        EventLogHelper.writeCarServiceVhalDied(101);

        assertLogMessage("I car_service_vhal_died: 101");
    }

    @Test
    public void testWriteCarServiceInitBootUser() {
        EventLogHelper.writeCarServiceInitBootUser();

        assertLogMessage("I car_service_init_boot_user");
    }

    @Test
    public void testWriteCarServiceOnUserRemoved() {
        EventLogHelper.writeCarServiceOnUserRemoved(101);

        assertLogMessage("I car_service_on_user_removed: 101");
    }

    @Test
    public void testWriteCarUserServiceInitialUserInfoReq() {
        EventLogHelper.writeCarUserServiceInitialUserInfoReq(1, 2, 3, 4, 5);

        assertLogMessage("I car_user_svc_initial_user_info_req: [1,2,3,4,5]");
    }

    @Test
    public void  testWriteCarUserServiceInitialUserInfoResp() {
        EventLogHelper.writeCarUserServiceInitialUserInfoResp(1, 2, 3, 4, "string1", "string2");

        assertLogMessage("I car_user_svc_initial_user_info_resp: [1,2,3,4,string1,string2]");
    }

    @Test
    public void testWriteCarUserServiceSetInitialUser() {
        EventLogHelper.writeCarUserServiceSetInitialUser(101);

        assertLogMessage("I car_user_svc_set_initial_user: 101");
    }

    @Test
    public void testWriteCarUserServiceSetLifecycleListener() {
        EventLogHelper.writeCarUserServiceSetLifecycleListener(101, "string1");

        assertLogMessage("I car_user_svc_set_lifecycle_listener: [101,string1]");
    }

    @Test
    public void testWriteCarUserServiceResetLifecycleListener() {
        EventLogHelper.writeCarUserServiceResetLifecycleListener(101, "string1");

        assertLogMessage("I car_user_svc_reset_lifecycle_listener: [101,string1]");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserReq() {
        EventLogHelper.writeCarUserServiceSwitchUserReq(101, 102);

        assertLogMessage("I car_user_svc_switch_user_req: [101,102]");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserResp() {
        EventLogHelper.writeCarUserServiceSwitchUserResp(101, 102, "string");

        assertLogMessage("I car_user_svc_switch_user_resp: [101,102,string]");
    }

    @Test
    public void testWriteCarUserServicePostSwitchUserReq() {
        EventLogHelper.writeCarUserServicePostSwitchUserReq(101, 102);

        assertLogMessage("I car_user_svc_post_switch_user_req: [101,102]");
    }

    @Test
    public void testWriteCarUserServiceGetUserAuthReq() {
        EventLogHelper.writeCarUserServiceGetUserAuthReq(101, 102, 103);

        assertLogMessage("I car_user_svc_get_user_auth_req: [101,102,103]");
    }

    @Test
    public void testWriteCarUserServiceGetUserAuthResp() {
        EventLogHelper.writeCarUserServiceGetUserAuthResp(101);

        assertLogMessage("I car_user_svc_get_user_auth_resp: 101");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserUiReq() {
        EventLogHelper.writeCarUserServiceSwitchUserUiReq(101);

        assertLogMessage("I car_user_svc_switch_user_ui_req: 101");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserFromHalReq() {
        EventLogHelper.writeCarUserServiceSwitchUserFromHalReq(101, 102);

        assertLogMessage("I car_user_svc_switch_user_from_hal_req: [101,102]");
    }

    @Test
    public void testWriteCarUserServiceSetUserAuthReq() {
        EventLogHelper.writeCarUserServiceSetUserAuthReq(101, 102, 103);

        assertLogMessage("I car_user_svc_set_user_auth_req: [101,102,103]");
    }

    @Test
    public void testWriteCarUserServiceSetUserAuthResp() {
        EventLogHelper.writeCarUserServiceSetUserAuthResp(101, "string");

        assertLogMessage("I car_user_svc_set_user_auth_resp: [101,string]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserReq() {
        EventLogHelper.writeCarUserServiceCreateUserReq("string1", "string2", 101, 102, 103);

        assertLogMessage("I car_user_svc_create_user_req: [string1,string2,101,102,103]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserResp() {
        EventLogHelper.writeCarUserServiceCreateUserResp(101, 102, "string");

        assertLogMessage("I car_user_svc_create_user_resp: [101,102,string]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserUserCreated() {
        EventLogHelper.writeCarUserServiceCreateUserUserCreated(101, "string1", "string2", 102);

        assertLogMessage("I car_user_svc_create_user_user_created: [101,string1,string2,102]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserUserRemoved() {
        EventLogHelper.writeCarUserServiceCreateUserUserRemoved(101, "string");

        assertLogMessage("I car_user_svc_create_user_user_removed: [101,string]");
    }

    @Test
    public void testWriteCarUserServiceRemoveUserReq() {
        EventLogHelper.writeCarUserServiceRemoveUserReq(101, 102);

        assertLogMessage("I car_user_svc_remove_user_req: [101,102]");
    }

    private void assertLogMessage(String match) {
        LogcatHelper.assertLogcatMessage(match, TIMEOUT_MS);
    }
}
