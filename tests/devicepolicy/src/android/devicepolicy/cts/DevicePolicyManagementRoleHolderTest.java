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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION;
import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;

import static com.android.bedstead.nene.permissions.CommonPermissions.BYPASS_ROLE_QUALIFICATION;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import com.android.bedstead.deviceadminapp.DeviceAdminApp;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.BlockingCallback;
import com.android.queryable.queries.ActivityQuery;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@RunWith(BedsteadJUnit4.class)
public class DevicePolicyManagementRoleHolderTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME =
            DeviceAdminApp.deviceAdminComponentName(sContext);
    private static final String PROFILE_OWNER_NAME = "testDeviceAdmin";
    private static final ManagedProfileProvisioningParams MANAGED_PROFILE_PROVISIONING_PARAMS =
            createManagedProfileProvisioningParamsBuilder().build();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final ActivityQuery<?> sQueryForRoleHolderTrustedSourceAction =
            activity().intentFilters().contains(
                intentFilter().actions().contains(
                        ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE));
    private static final ActivityQuery<?> sQueryForRoleHolderManagedProfileAction =
            activity().intentFilters().contains(
                intentFilter().actions().contains(
                        ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE));
    private static final ActivityQuery<?> sQueryForRoleHolderFinalizationAction =
            activity().intentFilters().contains(
                intentFilter().actions().contains(
                        ACTION_ROLE_HOLDER_PROVISION_FINALIZATION));
    private static final TestApp sRoleHolderApp = sDeviceState.testApps()
            .query()
            .whereActivities()
            .contains(
                    sQueryForRoleHolderTrustedSourceAction,
                    sQueryForRoleHolderManagedProfileAction,
                    sQueryForRoleHolderFinalizationAction)
            .get();
    private static final String MANAGED_USER_NAME = "managed user name";

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @Test
    public void createAndProvisionManagedProfile_roleHolderIsInWorkProfile()
            throws ProvisioningException, InterruptedException {
        UserHandle profile = null;
        String roleHolderPackageName = null;
        try (TestAppInstance roleHolderApp = sRoleHolderApp.install()) {
            roleHolderPackageName = roleHolderApp.packageName();
            TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(roleHolderPackageName);

            profile = sDevicePolicyManager.createAndProvisionManagedProfile(
                    MANAGED_PROFILE_PROVISIONING_PARAMS);

            assertThat(TestApis.packages().installedForUser(UserReference.of(profile)))
                    .contains(Package.of(roleHolderApp.packageName()));
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
            if (roleHolderPackageName != null) {
                TestApis.devicePolicy()
                        .unsetDevicePolicyManagementRoleHolder(roleHolderPackageName);
            }
        }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasDeviceOwner
    @RequireRunOnPrimaryUser
    @Test
    public void createAndManageUser_roleHolderIsInManagedUser() throws InterruptedException {
        UserHandle managedUser = null;
        String roleHolderPackageName = null;
        try (TestAppInstance roleHolderApp = sRoleHolderApp.install()) {
            roleHolderPackageName = roleHolderApp.packageName();
            TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(roleHolderPackageName);

            managedUser = sDeviceState.dpc().devicePolicyManager().createAndManageUser(
                    RemoteDpc.DPC_COMPONENT_NAME,
                    MANAGED_USER_NAME,
                    RemoteDpc.DPC_COMPONENT_NAME,
                    /* adminExtras= */ null,
                    /* flags= */ 0);

            assertThat(TestApis.packages().installedForUser(UserReference.of(managedUser)))
                    .contains(Package.of(roleHolderApp.packageName()));
        } finally {
            if (managedUser != null) {
                TestApis.users().find(managedUser).remove();
            }
            if (roleHolderPackageName != null) {
                TestApis.devicePolicy()
                        .unsetDevicePolicyManagementRoleHolder(roleHolderPackageName);
            }
        }
    }

    private static ManagedProfileProvisioningParams.Builder
            createManagedProfileProvisioningParamsBuilder() {
        return new ManagedProfileProvisioningParams.Builder(
                DEVICE_ADMIN_COMPONENT_NAME,
                PROFILE_OWNER_NAME);
    }
}
