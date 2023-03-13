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

import static android.Manifest.permission.READ_CALENDAR;
import static android.app.admin.DevicePolicyIdentifiers.APPLICATION_RESTRICTIONS_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.AUTO_TIMEZONE_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.LOCK_TASK_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PACKAGE_UNINSTALL_BLOCKED_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERMISSION_GRANT_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERSISTENT_PREFERRED_ACTIVITY_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.RESET_PASSWORD_TOKEN_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.USER_CONTROL_DISABLED_PACKAGES_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.getIdentifierForUserRestriction;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.app.admin.PolicyUpdateReceiver.ACTION_DEVICE_POLICY_SET_RESULT;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_BUNDLE_KEY;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_KEY;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_TARGET_USER_ID;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_UPDATE_RESULT_KEY;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;
import static android.os.UserManager.DISALLOW_WIFI_DIRECT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.admin.DevicePolicyState;
import android.app.admin.DpcAuthority;
import android.app.admin.EnforcingAdmin;
import android.app.admin.IntentFilterPolicyKey;
import android.app.admin.LockTaskPolicy;
import android.app.admin.MostRestrictive;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PackagePermissionPolicyKey;
import android.app.admin.PackagePolicyKey;
import android.app.admin.PolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateReceiver;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.RoleAuthority;
import android.app.admin.StringSetUnion;
import android.app.admin.TopPriority;
import android.app.admin.UserRestrictionPolicyKey;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.devicepolicy.cts.utils.BundleUtils;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CoexistenceFlagsOn;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
@CoexistenceFlagsOn
public final class DeviceManagementCoexistenceTests {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String PACKAGE_NAME = "com.android.package.test";

    private static final String GRANTABLE_PERMISSION = READ_CALENDAR;

    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();

    private static String FINANCED_DEVICE_CONTROLLER_ROLE =
            "android.app.role.SYSTEM_FINANCED_DEVICE_CONTROLLER";

    private static final List<Boolean> TRUE_MORE_RESTRICTIVE = List.of(true, false);

    private static final String LOCAL_USER_RESTRICTION = DISALLOW_MODIFY_ACCOUNTS;

    private static final String GLOBAL_USER_RESTRICTION = DISALLOW_WIFI_DIRECT;

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static TestAppInstance sTestAppInstance = sTestApp.install();

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_autoTimezoneSet_returnsPolicy() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_permissionGrantStateSet_returnsPolicy() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_lockTaskPolicySet_returnsPolicy() {
        String[] originalLockTaskPackages = sDeviceState.dpc().devicePolicyManager()
                .getLockTaskPackages(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});

            PolicyState<LockTaskPolicy> policyState = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy().getPackages())
                    .containsExactly(PACKAGE_NAME);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), originalLockTaskPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_userControlDisabledPackagesSet_returnsPolicy() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).containsExactly(
                    sTestApp.packageName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_uninstallBlockedSet_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_persistentPreferredActivitySet_returnsPolicy() {
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());

            PolicyState<ComponentName> policyState = getComponentNamePolicyState(
                    new IntentFilterPolicyKey(
                            PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                            intentFilter),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    sDeviceState.dpc().componentName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_appRestrictionsSet_returnsPolicy() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getDevicePolicyState_appRestrictionsSet_returnsPolicy");
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            PolicyState<Bundle> policyState = getBundlePolicyState(
                    new PackagePolicyKey(
                            APPLICATION_RESTRICTIONS_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            // app restrictions is a non-coexistable policy, so should not have a resolved policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Bundle returnedBundle = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(returnedBundle).isNotNull();
            BundleUtils.assertEqualToBundle(
                    "getDevicePolicyState_appRestrictionsSet_returnsPolicy",
                    returnedBundle);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    // If ResetPasswordWithTokenTest for managed profile is executed before device owner and
    // primary user profile owner tests, password reset token would have been disabled for the
    // primary user, disabling this test until this gets fixed.
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_resetPasswordTokenSet_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                    sDeviceState.dpc().componentName(), TOKEN);

            PolicyState<Long> policyState = getLongPolicyState(
                    new NoArgsPolicyKey(RESET_PASSWORD_TOKEN_POLICY),
                    sDeviceState.dpc().user().userHandle());

            // reset password token is a non-coexistable policy, so should not have a resolved
            // policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Long token = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(token).isNotNull();
            assertThat(token).isNotEqualTo(0);
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(sDeviceState.dpc().componentName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_addUserRestriction_returnsPolicy() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_addUserRestrictionGlobally_returnsPolicy() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(GLOBAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                            GLOBAL_USER_RESTRICTION),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_autoTimezone_returnsCorrectResolutionMechanism() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);

        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_permissionGrantState_returnsCorrectResolutionMechanism() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRestrictiveIntegerMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(
                    List.of(PERMISSION_GRANT_STATE_DENIED,
                            PERMISSION_GRANT_STATE_GRANTED,
                            PERMISSION_GRANT_STATE_DEFAULT));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_lockTaskPolicy_returnsCorrectResolutionMechanism() {
        String[] originalLockTaskPackages = sDeviceState.dpc().devicePolicyManager()
                .getLockTaskPackages(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});

            PolicyState<LockTaskPolicy> policyState = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getTopPriorityMechanism(policyState)
                    .getHighestToLowestPriorityAuthorities()).isEqualTo(
                    List.of(
                            new RoleAuthority(Set.of(FINANCED_DEVICE_CONTROLLER_ROLE)),
                            DpcAuthority.DPC_AUTHORITY));
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), originalLockTaskPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_userControlDisabledPackages_returnsCorrectResolutionMechanism() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getResolutionMechanism()).isEqualTo(
                    StringSetUnion.STRING_SET_UNION);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_uninstallBlocked_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_persistentPreferredActivity_returnsCorrectResolutionMechanism() {
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());

            PolicyState<ComponentName> policyState = getComponentNamePolicyState(
                    new IntentFilterPolicyKey(
                            PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                            intentFilter),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getTopPriorityMechanism(policyState)
                    .getHighestToLowestPriorityAuthorities()).isEqualTo(
                    List.of(
                            new RoleAuthority(Set.of(FINANCED_DEVICE_CONTROLLER_ROLE)),
                            DpcAuthority.DPC_AUTHORITY));
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_addUserRestriction_returnsCorrectResolutionMechanism() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_autoTimezoneSet_receivedPolicySetBroadcast() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertPolicySetResultReceived(AUTO_TIMEZONE_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    @Ignore("figure out why it's failing")
    public void policyUpdateReceiver_permissionGrantStateSet_receivedPolicySetBroadcast() {
        Bundle bundle = new Bundle();
        bundle.putString(PolicyUpdateReceiver.EXTRA_PACKAGE_NAME, sTestApp.packageName());
        bundle.putString(PolicyUpdateReceiver.EXTRA_PERMISSION_NAME, GRANTABLE_PERMISSION);
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            assertPolicySetResultReceived(
                    PERMISSION_GRANT_POLICY, PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID,
                    bundle);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_lockTaskPolicySet_receivedPolicySetBroadcast() {
        String[] originalLockTaskPackages = sDeviceState.dpc().devicePolicyManager()
                .getLockTaskPackages(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});

            assertPolicySetResultReceived(
                    LOCK_TASK_POLICY, PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID,
                    new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), originalLockTaskPackages);
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_userControlDisabledPackagesSet_receivedPolicySetBroadcast() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));

            assertPolicySetResultReceived(
                    USER_CONTROL_DISABLED_PACKAGES_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());

        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_uninstallBlockedSet_receivedPolicySetBroadcast() {
        Bundle bundle = new Bundle();
        bundle.putString(PolicyUpdateReceiver.EXTRA_PACKAGE_NAME, sTestApp.packageName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            assertPolicySetResultReceived(
                    PACKAGE_UNINSTALL_BLOCKED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, bundle);

        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    @Ignore("figure out why it's failing")
    public void policyUpdateReceiver_persistentPreferredActivitySet_receivedPolicySetBroadcast() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        Bundle bundle = new Bundle();
        bundle.putParcelable(PolicyUpdateReceiver.EXTRA_INTENT_FILTER, intentFilter);
        try {
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());

            assertPolicySetResultReceived(
                    PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, bundle);
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_addUserRestriction_receivedPolicySetBroadcast() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            assertPolicySetResultReceived(
                    getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_addUserRestrictionGlobally_receivedPolicySetBroadcast() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(GLOBAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            assertPolicySetResultReceived(
                    getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    private void assertPolicySetResultReceived(
            String policyIdentifier, int resultKey, int targetUser, Bundle policyExtraBundle) {

        final Intent receivedIntent = sDeviceState.dpc().events().broadcastReceived()
                .whereIntent().action()
                .isEqualTo(ACTION_DEVICE_POLICY_SET_RESULT)
                .poll(Duration.ofMinutes(1)).intent();
        assertThat(receivedIntent).isNotNull();

        assertThat(receivedIntent.getStringExtra(EXTRA_POLICY_KEY)).isEqualTo(policyIdentifier);
        assertThat(receivedIntent.getIntExtra(EXTRA_POLICY_UPDATE_RESULT_KEY, /* default= */ -100))
                .isEqualTo(resultKey);
        assertThat(receivedIntent.getIntExtra(EXTRA_POLICY_TARGET_USER_ID, /* default= */ -100))
                .isEqualTo(targetUser);

        // TODO: add checks on bundle values.
        for (String key : policyExtraBundle.keySet()) {
            assertThat(receivedIntent.getBundleExtra(EXTRA_POLICY_BUNDLE_KEY).containsKey(key))
                    .isTrue();
        }
    }

    private PolicyState<Long> getLongPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Long>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Long: " + e);
            return null;
        }
    }

    private PolicyState<Bundle> getBundlePolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Bundle>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Bundle: " + e);
            return null;
        }
    }

    private PolicyState<Boolean> getBooleanPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Boolean>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Boolean: " + e);
            return null;
        }
    }

    private PolicyState<Set<String>> getStringSetPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Set<String>>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Set<String>: " + e);
            return null;
        }
    }

    private PolicyState<LockTaskPolicy> getLockTaskPolicyState(
            PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<LockTaskPolicy>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type LockTaskPolicy: " + e);
            return null;
        }
    }

    private PolicyState<Integer> getIntegerPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Integer>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Integer: " + e);
            return null;
        }
    }

    private PolicyState<ComponentName> getComponentNamePolicyState(
            PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<ComponentName>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type ComponentName: " + e);
            return null;
        }
    }

    private MostRestrictive<Boolean> getMostRestrictiveBooleanMechanism(
            PolicyState<Boolean> policyState) {
        try {
            return (MostRestrictive<Boolean>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRestrictive<Boolean>: " + e);
            return null;
        }
    }

    private MostRestrictive<Integer> getMostRestrictiveIntegerMechanism(
            PolicyState<Integer> policyState) {
        try {
            return (MostRestrictive<Integer>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRestrictive<Integer>: " + e);
            return null;
        }
    }

    private TopPriority<?> getTopPriorityMechanism(PolicyState<?> policyState) {
        try {
            return (TopPriority<?>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type TopPriority<>: " + e);
            return null;
        }
    }
}
