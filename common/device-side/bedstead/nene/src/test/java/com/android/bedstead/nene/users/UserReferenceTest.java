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

package com.android.bedstead.nene.users;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.Build.VERSION_CODES.Q;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.activities.ActivityCreatedEvent;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class UserReferenceTest {
    private static final int NON_EXISTING_USER_ID = 10000;
    private static final int USER_ID = NON_EXISTING_USER_ID;
    private static final String USER_NAME = "userName";
    private static final String TEST_ACTIVITY_NAME = "com.android.bedstead.nene.test.Activity";

    private static final Context sContext = TestApis.context().instrumentedContext();

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    public void id_returnsId() {
        assertThat(TestApis.users().find(USER_ID).id()).isEqualTo(USER_ID);
    }

    @Test
    public void userHandle_referencesId() {
        assertThat(TestApis.users().find(USER_ID).userHandle().getIdentifier()).isEqualTo(USER_ID);
    }

    @Test
    public void resolve_doesNotExist_returnsNull() {
        assertThat(TestApis.users().find(NON_EXISTING_USER_ID).resolve()).isNull();
    }

    @Test
    @EnsureHasSecondaryUser
    public void resolve_doesExist_returnsUser() {
        assertThat(sDeviceState.secondaryUser().resolve()).isNotNull();
    }

    @Test
    // TODO(scottjonathan): We should specify that we can create a new user
    @EnsureHasNoSecondaryUser
    @EnsureHasNoWorkProfile
    public void resolve_doesExist_userHasCorrectDetails() {
        UserReference userReference = TestApis.users().createUser().name(USER_NAME).create();

        try {
            User user = userReference.resolve();
            assertThat(user.name()).isEqualTo(USER_NAME);
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void remove_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.users().find(USER_ID).remove());
    }

    @Test
    public void remove_userExists_removesUser() {
        UserReference user = TestApis.users().createUser().create();

        user.remove();

        assertThat(TestApis.users().all()).doesNotContain(user);
    }

    @Test
    public void start_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.users().find(NON_EXISTING_USER_ID).start());
    }

    @Test
    public void start_userNotStarted_userIsStarted() {
        UserReference user = TestApis.users().createUser().create().stop();

        user.start();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.RUNNING_UNLOCKED);
        } finally {
            user.remove();
        }
    }

    @Test
    @EnsureHasSecondaryUser
    public void start_userAlreadyStarted_doesNothing() {
        sDeviceState.secondaryUser().start();

        sDeviceState.secondaryUser().start();

        assertThat(sDeviceState.secondaryUser().resolve().state())
                .isEqualTo(User.UserState.RUNNING_UNLOCKED);
    }

    @Test
    public void stop_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.users().find(NON_EXISTING_USER_ID).stop());
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunOnPrimaryUser // TODO(201319776): Use @RequireRunNotOnSecondaryUser
    public void stop_userStarted_userIsStopped() {
        sDeviceState.secondaryUser().stop();

        assertThat(sDeviceState.secondaryUser().resolve().state())
                .isEqualTo(User.UserState.NOT_RUNNING);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunOnPrimaryUser // TODO(201319776): Use @RequireRunNotOnSecondaryUser
    public void stop_userNotStarted_doesNothing() {
        sDeviceState.secondaryUser().stop();

        sDeviceState.secondaryUser().stop();

        assertThat(sDeviceState.secondaryUser().resolve().state())
                .isEqualTo(User.UserState.NOT_RUNNING);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunOnPrimaryUser // TODO(201319776): Use @RequireRunNotOnSecondaryUser
    @RequireSdkVersion(min = Q)
    public void switchTo_userIsSwitched() {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {

            TestApis.packages().find(sContext.getPackageName())
                    .install(sDeviceState.secondaryUser());
            sDeviceState.secondaryUser().switchTo();

            Intent intent = new Intent();
            intent.setPackage(sContext.getPackageName());
            intent.setClassName(sContext.getPackageName(), TEST_ACTIVITY_NAME);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
            sContext.startActivityAsUser(intent, sDeviceState.secondaryUser().userHandle());

            EventLogs<ActivityCreatedEvent> logs =
                    ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                            .whereActivity().activityClass()
                                .className().isEqualTo(TEST_ACTIVITY_NAME)
                            .onUser(sDeviceState.secondaryUser());
            assertThat(logs.poll()).isNotNull();
        } finally {
            TestApis.users().system().switchTo();
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void stop_isWorkProfileOfCurrentUser_stops() {
        sDeviceState.workProfile().stop();

        assertThat(sDeviceState.workProfile().resolve().state())
                .isEqualTo(User.UserState.NOT_RUNNING);
    }
}
