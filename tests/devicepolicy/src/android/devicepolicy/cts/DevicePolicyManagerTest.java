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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static junit.framework.Assert.fail;

import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.app.admin.FullyManagedDeviceProvisioningParams;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.compatibility.common.util.SystemUtil;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireFeatures;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public final class DevicePolicyManagerTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final SharedPreferences sSharedPreferences =
            sContext.getSharedPreferences("required-apps.txt", Context.MODE_PRIVATE);

    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME = new ComponentName(
            sContext, CtsDeviceAdminReceiver.class);

    private static final String PROFILE_OWNER_NAME = "testDeviceAdmin";
    private static final String DEVICE_OWNER_NAME = "testDeviceAdmin";

    private static final String KEY_PRE_PROVISIONING_SYSTEM_APPS = "pre_provisioning_system_apps";
    private static final String KEY_PRE_PROVISIONING_NON_SYSTEM_APPS =
            "pre_provisioning_non_system_apps";

    private static final String SET_DEVICE_OWNER_ACTIVE_ADMIN_COMMAND =
            "dpm set-active-admin --user cur " + DEVICE_ADMIN_COMPONENT_NAME.flattenToString();
    private static final String SET_DEVICE_OWNER_COMMAND =
            "dpm set-device-owner --user cur " + DEVICE_ADMIN_COMPONENT_NAME.flattenToString();
    private static final String REMOVE_ACTIVE_ADMIN_COMMAND =
            "dpm remove-active-admin --user cur " + DEVICE_ADMIN_COMPONENT_NAME.flattenToString();
    private static final String SET_USER_SETUP_COMPLETE_COMMAND =
            "settings put secure --user 0 user_setup_complete 1";
    private static final String CLEAR_USER_SETUP_COMPLETE_COMMAND =
            "settings put secure --user 0 user_setup_complete 0";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @RequireRunOnPrimaryUser
    @EnsureHasNoWorkProfile
    @RequireFeatures({
            PackageManager.FEATURE_DEVICE_ADMIN,
            PackageManager.FEATURE_MANAGED_USERS
    })
    @Test
    @Postsubmit(reason="b/181207615 flaky")
    public void testCreateAndProvisionManagedProfile_setsProfileOwner() throws Exception {
        UserHandle profile = null;
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            ManagedProfileProvisioningParams params = createManagedProfileProvisioningParams();

            profile = sDevicePolicyManager.createAndProvisionManagedProfile(params);

            final DevicePolicyManager profileDpm = getDpmForProfile(profile);
            assertTrue("Profile owner not set", profileDpm.isProfileOwnerApp(
                                    sContext.getPackageName()));
        } finally {
            if (profile != null) {
                sContext.getSystemService(UserManager.class).removeUser(profile);
            }
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoWorkProfile
    @RequireFeatures({
            PackageManager.FEATURE_DEVICE_ADMIN,
            PackageManager.FEATURE_MANAGED_USERS
    })
    @Test
    @Postsubmit(reason="b/181207615 flaky")
    public void testCreateAndProvisionManagedProfile_createsProfile() throws Exception {
        UserHandle profile = null;
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            final ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParams();

            profile = sDevicePolicyManager.createAndProvisionManagedProfile(params);

            assertNotNull(profile);
        } finally {
            if (profile != null) {
                sContext.getSystemService(UserManager.class).removeUser(profile);
            }
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    private ManagedProfileProvisioningParams createManagedProfileProvisioningParams() {
        return new ManagedProfileProvisioningParams.Builder(
                        DEVICE_ADMIN_COMPONENT_NAME,
                        PROFILE_OWNER_NAME)
                        .build();
    }

    private DevicePolicyManager getDpmForProfile(UserHandle profile) {
        return sContext.createContextAsUser(profile, /* flags= */ 0).getSystemService(
                DevicePolicyManager.class);
    }

    @RequireRunOnPrimaryUser
    @RequireFeatures(PackageManager.FEATURE_DEVICE_ADMIN)
    @Test
    public void testProvisionFullyManagedDevice_setsDeviceOwner() throws Exception {
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            resetUserSetupCompletedFlag();
            FullyManagedDeviceProvisioningParams params = createManagedDeviceProvisioningParams();

            sDevicePolicyManager.provisionFullyManagedDevice(params);

            assertTrue(
                    "Device owner not set",
                    sDevicePolicyManager.isDeviceOwnerApp(sContext.getPackageName()));
        } finally {
            SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
            setUserSetupCompletedFlag();
            setUserSetupCompletedFlag();
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    @RequireRunOnPrimaryUser
    @RequireFeatures(PackageManager.FEATURE_DEVICE_ADMIN)
    @Test
    public void testProvisionFullyManagedDevice_doesNotThrowException() {
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            resetUserSetupCompletedFlag();
            FullyManagedDeviceProvisioningParams params = createManagedDeviceProvisioningParams();

            try {
                sDevicePolicyManager.provisionFullyManagedDevice(params);

            } catch (ProvisioningException e) {
                fail("Should not throw exception: " + e);
            }
        } finally {
            SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
            setUserSetupCompletedFlag();
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    @RequireRunOnPrimaryUser
    @Test
    public void provisionFullyManagedDevice_canControlSensorPermissionGrantsByDefault()
            throws ProvisioningException {
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            resetUserSetupCompletedFlag();

            FullyManagedDeviceProvisioningParams params = createManagedDeviceProvisioningParams();
            sDevicePolicyManager.provisionFullyManagedDevice(params);

            assertThat(sDevicePolicyManager.canAdminGrantSensorsPermissions()).isTrue();
        } finally {
            SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
            setUserSetupCompletedFlag();
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    @RequireRunOnPrimaryUser
    @Test
    public void provisionFullyManagedDevice_canOptOutOfControllingSensorPermissionGrants()
            throws ProvisioningException {
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            resetUserSetupCompletedFlag();

            FullyManagedDeviceProvisioningParams params = createManagedDeviceProvisioningParams(
                    /* canControlPermissionGrant= */ false);
            sDevicePolicyManager.provisionFullyManagedDevice(params);

            assertThat(sDevicePolicyManager.canAdminGrantSensorsPermissions()).isFalse();
        } finally {
            SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
            setUserSetupCompletedFlag();
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    FullyManagedDeviceProvisioningParams.Builder
            createDefaultManagedDeviceProvisioningParamsBuilder() {
        return new FullyManagedDeviceProvisioningParams.Builder(
                DEVICE_ADMIN_COMPONENT_NAME,
                DEVICE_OWNER_NAME)
                // Don't remove system apps during provisioning until the testing
                // infrastructure supports restoring uninstalled apps.
                .setLeaveAllSystemAppsEnabled(true);
    }

    FullyManagedDeviceProvisioningParams createManagedDeviceProvisioningParams() {
        return createDefaultManagedDeviceProvisioningParamsBuilder().build();
    }

    FullyManagedDeviceProvisioningParams createManagedDeviceProvisioningParams(
            boolean canControlPermissionGrants) {
        return createDefaultManagedDeviceProvisioningParamsBuilder()
                .setDeviceOwnerCanGrantSensorsPermissions(canControlPermissionGrants)
                .build();
    }

    private void resetUserSetupCompletedFlag() {
        SystemUtil.runShellCommand(CLEAR_USER_SETUP_COMPLETE_COMMAND);
        sDevicePolicyManager.forceUpdateUserSetupComplete();
    }

    private void setUserSetupCompletedFlag() {
        SystemUtil.runShellCommand(SET_USER_SETUP_COMPLETE_COMMAND);
        sDevicePolicyManager.forceUpdateUserSetupComplete();
    }


    // TODO(b/175380793): Add remaining cts test for DPM#provisionManagedDevice and
    //  DPM#createAndProvisionManagedProfile.
    //  Currently the following methods are not used.
    /**
     * Allows {@link #restorePreProvisioningApps} to be called to restore the pre-provisioning apps
     * that were uninstalled during provisioning.
     */
    private void persistPreProvisioningApps() {
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_ACTIVE_ADMIN_COMMAND);
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_COMMAND);

        Set<String> systemApps = findSystemApps();
        sSharedPreferences.edit()
                .putStringSet(KEY_PRE_PROVISIONING_SYSTEM_APPS, systemApps)
                .commit();
        Set<String> nonSystemApps = findNonSystemApps(systemApps);
        sSharedPreferences.edit()
                .putStringSet(KEY_PRE_PROVISIONING_NON_SYSTEM_APPS, nonSystemApps)
                .commit();
        sDevicePolicyManager.setKeepUninstalledPackages(
                DEVICE_ADMIN_COMPONENT_NAME, new ArrayList<>(nonSystemApps));

        SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
    }

    /**
     * Restores apps that were uninstalled prior to provisioning. No-op if {@link
     * #persistPreProvisioningApps()} was not called prior to provisioning. Subsequent
     * calls will need another prior call to {@link #persistPreProvisioningApps()} to avoid being a
     * no-op.
     */
    public void restorePreProvisioningApps() {
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_ACTIVE_ADMIN_COMMAND);
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_COMMAND);

        Set<String> postProvisioningSystemApps = findSystemApps();
        restorePreProvisioningSystemApps(postProvisioningSystemApps);
        restorePreProvisioningNonSystemApps(postProvisioningSystemApps);
        sSharedPreferences.edit().clear().commit();
        sDevicePolicyManager.setKeepUninstalledPackages(
                DEVICE_ADMIN_COMPONENT_NAME, new ArrayList<>());

        SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
    }

    private void restorePreProvisioningSystemApps(Set<String> postProvisioningSystemApps) {
        Set<String> preProvisioningSystemApps = sSharedPreferences.getStringSet(
                KEY_PRE_PROVISIONING_SYSTEM_APPS, Collections.emptySet());
        for (String preProvisioningSystemApp : preProvisioningSystemApps) {
            if (postProvisioningSystemApps.contains(preProvisioningSystemApp)) {
                continue;
            }
            sDevicePolicyManager.enableSystemApp(
                    DEVICE_ADMIN_COMPONENT_NAME, preProvisioningSystemApp);
        }
    }

    private void restorePreProvisioningNonSystemApps(Set<String> postProvisioningSystemApps) {
        Set<String> preProvisioningNonSystemApps = sSharedPreferences.getStringSet(
                KEY_PRE_PROVISIONING_NON_SYSTEM_APPS, Collections.emptySet());
        Set<String> postProvisioningNonSystemApps = findNonSystemApps(postProvisioningSystemApps);
        for (String preProvisioningNonSystemApp : preProvisioningNonSystemApps) {
            if (postProvisioningNonSystemApps.contains(preProvisioningNonSystemApp)) {
                continue;
            }
            sDevicePolicyManager.installExistingPackage(
                    DEVICE_ADMIN_COMPONENT_NAME, preProvisioningNonSystemApp);
        }
    }

    private Set<String> findSystemApps() {
        return sPackageManager.getInstalledApplications(PackageManager.MATCH_SYSTEM_ONLY)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toSet());
    }

    private Set<String> findNonSystemApps(Set<String> systemApps) {
        return sPackageManager.getInstalledApplications(PackageManager.MATCH_ALL)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .filter(packageName -> !systemApps.contains(packageName))
                .collect(Collectors.toSet());
    }
}
