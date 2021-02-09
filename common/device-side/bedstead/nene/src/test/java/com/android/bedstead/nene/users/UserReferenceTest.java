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

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.Build.VERSION.SDK_INT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.compatibility.common.util.SystemUtil;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.activities.ActivityCreatedEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UserReferenceTest {
    private static final int NON_EXISTING_USER_ID = 10000;
    private static final int USER_ID = NON_EXISTING_USER_ID;
    private static final String USER_NAME = "userName";
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final String TEST_ACTIVITY_NAME = "com.android.bedstead.nene.test.Activity";

    private final Users mUsers = new Users();

    @Test
    public void id_returnsId() {
        assertThat(mUsers.find(USER_ID).id()).isEqualTo(USER_ID);
    }

    @Test
    public void userHandle_referencesId() {
        assertThat(mUsers.find(USER_ID).userHandle().getIdentifier()).isEqualTo(USER_ID);
    }

    @Test
    public void resolve_doesNotExist_returnsNull() {
        assertThat(mUsers.find(NON_EXISTING_USER_ID).resolve()).isNull();
    }

    @Test
    public void resolve_doesExist_returnsUser() {
        UserReference userReference = createUser();

        try {
            assertThat(userReference.resolve()).isNotNull();
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void resolve_doesExist_userHasCorrectDetails() {
        UserReference userReference = mUsers.createUser().name(USER_NAME).create();

        try {
            User user = userReference.resolve();
            assertThat(user.name()).isEqualTo(USER_NAME);
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void remove_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> mUsers.find(USER_ID).remove());
    }

    @Test
    public void remove_userExists_removesUser() {
        UserReference user = createUser();

        user.remove();

        assertThat(mUsers.all().stream().anyMatch(u -> u.id() == user.id())).isFalse();
    }

    @Test
    public void start_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> mUsers.find(NON_EXISTING_USER_ID).start());
    }

    @Test
    public void start_userNotStarted_userIsStarted() {
        UserReference user = createUser()
                .start();

        user.start();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.RUNNING_UNLOCKED);
        } finally {
            user.remove();
        }
    }

    @Test
    public void start_userAlreadyStarted_doesNothing() {
        UserReference user = createUser()
                .start();

        user.start();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.RUNNING_UNLOCKED);
        } finally {
            user.remove();
        }
    }

    @Test
    public void stop_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> mUsers.find(NON_EXISTING_USER_ID).stop());
    }

    @Test
    public void stop_userStarted_userIsStopped() {
        UserReference user = createUser()
                .start();

        user.stop();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.NOT_RUNNING);
        } finally {
            user.remove();
        }
    }

    @Test
    public void stop_userNotStarted_doesNothing() {
        UserReference user = createUser()
                .stop();

        user.stop();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.NOT_RUNNING);
        } finally {
            user.remove();
        }
    }

    @Test
    public void switchTo_userIsSwitched() throws Exception {
        assumeTrue(
                "Install-existing only works for P+", SDK_INT >= Build.VERSION_CODES.P);
        // TODO(scottjonathan): Might be a way of faking install-existing on older
        //  versions (fetch the pkg and reinstall?)
        assumeTrue(
                "Adopting Shell Permissions only works for Q+", SDK_INT >= Build.VERSION_CODES.Q);
        // TODO(scottjonathan): In this case we can probably grant the permission through adb?

        UserReference user = createUser().start();
        try {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                // for INTERACT_ACROSS_USERS
                ShellCommand.builder("pm install-existing")
                        .addOption("--user", user.id())
                        .addOperand(sContext.getPackageName())
                        .executeAndValidateOutput(
                                (output) -> output.contains("installed for user"));
                user.switchTo();

                Intent intent = new Intent();
                intent.setPackage(sContext.getPackageName());
                intent.setClassName(sContext.getPackageName(), TEST_ACTIVITY_NAME);
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
                sContext.startActivityAsUser(intent, user.userHandle());

                EventLogs<ActivityCreatedEvent> logs =
                        ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                                .whereActivity().className().isEqualTo(TEST_ACTIVITY_NAME)
                                .onUser(user.userHandle());
                assertThat(logs.poll()).isNotNull();
            });
        } finally {
            mUsers.system().switchTo();
            user.remove();
        }
    }

    private UserReference createUser() {
        return mUsers.createUser().name(USER_NAME).create();
    }
}
