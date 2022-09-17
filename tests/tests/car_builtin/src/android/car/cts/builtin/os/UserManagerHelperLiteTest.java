/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin.os;

import static android.Manifest.permission.CREATE_USERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.builtin.os.UserManagerHelper;
import android.car.cts.builtin.AbstractCarBuiltinTestCase;
import android.os.Binder;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireMultipleUsersOnMultipleDisplays;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotMultipleUsersOnMultipleDisplays;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * This class contains simple {@link UserManagerHelper} tests, i.e., they don't create user or do
 * any other heavy stuff - for that, please use {@link UserManagerHelperHeavyTest} instead.
 */
public final class UserManagerHelperLiteTest extends AbstractCarBuiltinTestCase {

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    private final UserManager mUserManager = mContext.getSystemService(UserManager.class);

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isFullUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isPreCreatedUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForCurrentUser() {
        // Current user should not be ephemeral or guest because test runs as secondary user.
        UserHandle currentUser = TestApis.users().current().userHandle();
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, currentUser)).isFalse();
        assertThat(UserManagerHelper.isFullUser(mUserManager, currentUser)).isTrue();
        assertThat(UserManagerHelper.isGuestUser(mUserManager, currentUser)).isFalse();

        // Current user should be enabled.
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, currentUser)).isTrue();

        // Current user should not be preCreated
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, currentUser)).isFalse();

        // Current should be initialized, otherwise test would be running
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, currentUser)).isTrue();

        // Current should be part of getUserHandles
        assertGetUserHandlesHasUser(currentUser);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @RequireNotHeadlessSystemUserMode
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isFullUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isPreCreatedUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForFullSystemUser() {
        UserHandle systemUser = UserHandle.SYSTEM;

        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, systemUser)).isFalse();
        assertThat(UserManagerHelper.isFullUser(mUserManager, systemUser)).isTrue();
        assertThat(UserManagerHelper.isGuestUser(mUserManager, systemUser)).isFalse();
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, systemUser)).isTrue();
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, systemUser)).isFalse();
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, systemUser)).isTrue();
        assertGetUserHandlesHasUser(systemUser);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @RequireHeadlessSystemUserMode
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isFullUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isPreCreatedUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForHeadlessSystemUser() {
        UserHandle systemUser = UserHandle.SYSTEM;

        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, systemUser)).isFalse();
        assertThat(UserManagerHelper.isFullUser(mUserManager, systemUser)).isFalse();
        assertThat(UserManagerHelper.isGuestUser(mUserManager, systemUser)).isFalse();
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, systemUser)).isTrue();
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, systemUser)).isFalse();
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, systemUser)).isTrue();
        assertGetUserHandlesHasUser(systemUser);
    }

    @Test
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#getDefaultUserTypeForUserInfoFlags(int)"
    })
    public void testGetDefaultUserTypeForUserInfoFlags() {
        // Simple example.
        assertThat(UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(UserManagerHelper.FLAG_MANAGED_PROFILE))
                        .isEqualTo(UserManager.USER_TYPE_PROFILE_MANAGED);

        // Type plus a non-type flag.
        assertThat(UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_GUEST | UserManagerHelper.FLAG_EPHEMERAL))
                                .isEqualTo(UserManager.USER_TYPE_FULL_GUEST);

        // Two types, which is illegal.
        assertThrows(IllegalArgumentException.class, () -> UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_MANAGED_PROFILE | UserManagerHelper.FLAG_GUEST));

        // No type, which defaults to {@link UserManager#USER_TYPE_FULL_SECONDARY}.
        assertThat(UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(UserManagerHelper.FLAG_EPHEMERAL))
                        .isEqualTo(UserManager.USER_TYPE_FULL_SECONDARY);
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#getDefaultUserName(Context)"})
    public void testGetDefaultUserName() {
        assertThat(UserManagerHelper.getDefaultUserName(mContext)).isNotNull();
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#getMaxRunningUsers(Context)"})
    public void testGetMaxRunningUsers() {
        assertThat(UserManagerHelper.getMaxRunningUsers(mContext)).isGreaterThan(0);
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#getUserId(int)"})
    public void testGetUserId() {
        assertThat(UserManagerHelper.getUserId(Binder.getCallingUid()))
                .isEqualTo(Binder.getCallingUserHandle().getIdentifier());
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#"
            + "isUsersOnSecondaryDisplaysSupported(UserManager)"})
    @RequireMultipleUsersOnMultipleDisplays(reason = "Because test is testing exactly that")
    public void test_isUsersOnSecondaryDisplaysSupported() {
        assertThat(UserManagerHelper.isUsersOnSecondaryDisplaysSupported(mUserManager)).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#"
            + "isUsersOnSecondaryDisplaysSupported(UserManager)"})
    @RequireNotMultipleUsersOnMultipleDisplays(reason = "Because test is testing exactly that")
    public void test_isUsersOnSecondaryDisplaysSupported_not() {
        assertThat(UserManagerHelper.isUsersOnSecondaryDisplaysSupported(mUserManager)).isFalse();
    }

    private void assertGetUserHandlesHasUser(UserHandle user) {
        List<UserHandle> allUsersHandles = UserManagerHelper.getUserHandles(mUserManager,
                /* excludePartial= */ false, /* excludeDying= */ false,
                /* excludePreCreated= */ false);
        assertThat(allUsersHandles).contains(user);
    }
}
