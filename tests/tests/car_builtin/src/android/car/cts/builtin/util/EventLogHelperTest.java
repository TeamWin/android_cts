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

    private static final int TIMEOUT_MS = 60_000;

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

        assertLogMessage("I car_service_vhal_died: 1");
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

    private void assertLogMessage(String match) {
        LogcatHelper.assertLogcatMessage(match, TIMEOUT_MS);
    }
}
