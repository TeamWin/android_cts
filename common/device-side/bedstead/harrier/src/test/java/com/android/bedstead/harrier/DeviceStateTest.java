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

package com.android.bedstead.harrier;

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireUserSupported;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DeviceStateTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final String TV_PROFILE_TYPE_NAME = "com.android.tv.profile";

    private static final String TEST_PERMISSION_1 = INTERACT_ACROSS_PROFILES;
    private static final String TEST_PERMISSION_2 = INTERACT_ACROSS_USERS_FULL;

    @Test
    @EnsureHasWorkProfile
    public void workProfile_workProfileProvided_returnsWorkProfile() {
        assertThat(sDeviceState.workProfile()).isNotNull();
    }

    @Test
    @EnsureHasNoWorkProfile
    public void workProfile_noWorkProfile_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::workProfile);
    }

    @Test
    @EnsureHasNoWorkProfile
    @EnsureHasNoDeviceOwner
    public void workProfile_createdWorkProfile_throwsException() {
        try (UserReference workProfile = sTestApis.users().createUser()
                .parent(sTestApis.users().instrumented())
                .type(sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::workProfile);
        }
    }

    @Test
    @EnsureHasWorkProfile
    public void ensureHasWorkProfileAnnotation_workProfileExists() {
        assertThat(sTestApis.users().findProfileOfType(
                sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME),
                sTestApis.users().instrumented())
        ).isNotNull();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): When supported, test the forUser argument

    @Test
    @EnsureHasNoWorkProfile
    public void ensureHasNoWorkProfileAnnotation_workProfileDoesNotExist() {
        assertThat(sTestApis.users().findProfileOfType(
                sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME),
                sTestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @EnsureHasTvProfile
    public void tvProfile_tvProfileProvided_returnsTvProfile() {
        assertThat(sDeviceState.tvProfile()).isNotNull();
    }

    @Test
    @EnsureHasNoTvProfile
    public void tvProfile_noTvProfile_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::tvProfile);
    }

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    public void tvProfile_createdTvProfile_throwsException() {
        try (UserReference tvProfile = sTestApis.users().createUser()
                .parent(sTestApis.users().instrumented())
                .type(sTestApis.users().supportedType(TV_PROFILE_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::tvProfile);
        }
    }

    @Test
    @EnsureHasTvProfile
    public void ensureHasTvProfileAnnotation_tvProfileExists() {
        assertThat(sTestApis.users().findProfileOfType(
                sTestApis.users().supportedType(TV_PROFILE_TYPE_NAME),
                sTestApis.users().instrumented())
        ).isNotNull();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): When supported, test the forUser argument

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    public void ensureHasNoTvProfileAnnotation_tvProfileDoesNotExist() {
        assertThat(sTestApis.users().findProfileOfType(
                sTestApis.users().supportedType(TV_PROFILE_TYPE_NAME),
                sTestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @EnsureHasSecondaryUser
    public void secondaryUser_secondaryUserProvided_returnsSecondaryUser() {
        assertThat(sDeviceState.secondaryUser()).isNotNull();
    }

    @Test
    @EnsureHasNoSecondaryUser
    public void secondaryUser_noSecondaryUser_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::secondaryUser);
    }

    @Test
    @EnsureHasNoSecondaryUser
    public void secondaryUser_createdSecondaryUser_throwsException() {
        try (UserReference secondaryUser = sTestApis.users().createUser()
                .type(sTestApis.users().supportedType(UserType.SECONDARY_USER_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::secondaryUser);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    public void ensureHasSecondaryUserAnnotation_secondaryUserExists() {
        assertThat(sTestApis.users().findUserOfType(
                sTestApis.users().supportedType(UserType.SECONDARY_USER_TYPE_NAME))
        ).isNotNull();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): Test the forUser argument

    @Test
    @EnsureHasNoSecondaryUser
    public void ensureHasNoSecondaryUserAnnotation_secondaryUserDoesNotExist() {
        assertThat(sTestApis.users().findUserOfType(
                sTestApis.users().supportedType(UserType.SECONDARY_USER_TYPE_NAME))
        ).isNull();
    }

    @Test
    @EnsureHasPermission(TEST_PERMISSION_1)
    public void ensureHasPermission_permissionIsGranted() {
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    @EnsureHasPermission({TEST_PERMISSION_1, TEST_PERMISSION_2})
    public void ensureHasPermission_multiplePermissions_permissionsAreGranted() {
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_GRANTED);
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_2)).isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    @EnsureDoesNotHavePermission(TEST_PERMISSION_1)
    public void ensureDoesNotHavePermission_permissionIsDenied() {
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    @EnsureDoesNotHavePermission({TEST_PERMISSION_1, TEST_PERMISSION_2})
    public void ensureDoesNotHavePermission_multiplePermissions_permissionsAreDenied() {
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_DENIED);
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_2)).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    @EnsureHasPermission(TEST_PERMISSION_1)
    @EnsureDoesNotHavePermission(TEST_PERMISSION_2)
    public void ensureHasPermissionAndDoesNotHavePermission_permissionsAreCorrect() {
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_GRANTED);
        assertThat(sTestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_2)).isEqualTo(PERMISSION_DENIED);
    }

    @EnsureHasDeviceOwner
    public void ensureHasDeviceOwnerAnnotation_deviceOwnerIsSet() {
        assertThat(sTestApis.devicePolicy().getDeviceOwner()).isNotNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    public void ensureHasNoDeviceOwnerAnnotation_deviceOwnerIsNotSet() {
        assertThat(sTestApis.devicePolicy().getDeviceOwner()).isNull();
    }

    @Test
    @EnsureHasDeviceOwner
    public void deviceOwner_deviceOwnerIsSet_returnsDeviceOwner() {
        assertThat(sDeviceState.deviceOwner()).isNotNull();
    }
}
