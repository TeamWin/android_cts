/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.bedstead.harrier.UserType.INITIAL_USER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_WIPE_DATA;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class WipeDataTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @RequireRunOnInitialUser
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#wipeData")
    public void wipeData_po_removeWorkProfile() {
        sDeviceState.dpc().devicePolicyManager().wipeData(/* flags= */ 0);

        assertWithMessage("Work profile should have been removed")
                .that(sDeviceState.workProfile(sDeviceState.initialUser()).exists())
                .isFalse();
    }

    @Postsubmit(reason = "new test")
    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#wipeData")
    public void wipeData_notAuthorized_throwsException() {
        assertThrows("No license - no wiping",
                IllegalStateException.class,
                () -> sDeviceState.dpc().devicePolicyManager().wipeDevice(/* flags= */ 0));
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoAdditionalUser
    @RequireRunOnInitialUser
    @RequireHeadlessSystemUserMode(reason = "tests headless user behaviour")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#wipeData")
    public void wipeData_noAdditionalUsers_throwsException() {
        assertThrows("Should prevent the removal of last full user",
                IllegalStateException.class,
                () -> sDeviceState.dpc().devicePolicyManager().wipeData(/* flags= */ 0));
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasProfileOwner(onUser = INITIAL_USER, isPrimary = true)
    // TODO(b/242189747) Verify that it behaves properly
    @RequireRunOnAdditionalUser
    @RequireHeadlessSystemUserMode(reason = "tests headless user behaviour")
    @Ignore("b/242189747")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#wipeData")
    public void wipeData_additionalUsers_removeUser() {
        sDeviceState.dpc().devicePolicyManager().wipeData(/* flags= */ 0);

        assertWithMessage(
                "User should have been removed,"
                        + " since there are other users on the device")
                .that(sDeviceState.initialUser().exists()).isFalse();
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasDeviceOwner
    @EnsureHasAdditionalUser
    @RequireRunOnSystemUser
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#wipeData")
    public void wipeData_systemUser_throwsSecurityException() {
        assertThrows("System user should not be removed",
                SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().wipeData(/* flags= */ 0));
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasProfileOwner(onUser = INITIAL_USER)
    @EnsureHasNoAdditionalUser
    @RequireRunOnInitialUser
    @RequireHeadlessSystemUserMode(reason = "tests headless user behaviour")
    public void wipeData_headless_lastUser_throwsIllegalStateException() {
        assertThrows("Last full user should not be removed",
                IllegalStateException.class,
                () -> sDeviceState.dpc().devicePolicyManager().wipeData(/* flags= */ 0));
    }
}
