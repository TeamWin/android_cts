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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.provider.DeviceConfig.NAMESPACE_DEVICE_POLICY_MANAGER;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedSubscriptionsPolicy;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureFeatureFlagEnabled;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.ManagedSubscriptions;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.remotedpc.RemoteDpc;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BedsteadJUnit4.class)
public final class ManagedSubscriptionsPolicyTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();

    private static final String ENABLE_WORK_PROFILE_TELEPHONY_FLAG =
            "enable_work_profile_telephony";

    @EnsureFeatureFlagEnabled(namespace = NAMESPACE_DEVICE_POLICY_MANAGER, key =
            ENABLE_WORK_PROFILE_TELEPHONY_FLAG)
    @PolicyAppliesTest(policy = ManagedSubscriptions.class)
    @Postsubmit(reason = "new test")
    public void setManagedSubscriptionsPolicy_works() {
        try {
            sDeviceState
                    .dpc()
                    .devicePolicyManager()
                    .setManagedSubscriptionsPolicy(
                            new ManagedSubscriptionsPolicy(
                                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));
            DevicePolicyManager sLocalDevicePolicyManager =
                    TestApis.context()
                            .instrumentedContext()
                            .getSystemService(DevicePolicyManager.class);

            assertThat(sLocalDevicePolicyManager.getManagedSubscriptionsPolicy().getPolicyType())
                    .isEqualTo(ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS);
        } finally {
            sDeviceState
                    .dpc()
                    .devicePolicyManager()
                    .setManagedSubscriptionsPolicy(
                            new ManagedSubscriptionsPolicy(
                                    ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
        }
    }

    @EnsureFeatureFlagEnabled(namespace = NAMESPACE_DEVICE_POLICY_MANAGER, key =
            ENABLE_WORK_PROFILE_TELEPHONY_FLAG)
    @PolicyAppliesTest(policy = ManagedSubscriptions.class)
    @Ignore("TODO(263556964): Enable after we have a way to reset the policy to default for a dpc")
    @Postsubmit(reason = "new test")
    public void getManagedSubscriptionPolicy_policyNotSet_returnsDefaultPolicy() {
        assertThat(
                sDeviceState
                        .dpc()
                        .devicePolicyManager()
                        .getManagedSubscriptionsPolicy()
                        .getPolicyType())
                .isEqualTo(ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS);
    }

    @EnsureFeatureFlagEnabled(namespace = NAMESPACE_DEVICE_POLICY_MANAGER, key =
            ENABLE_WORK_PROFILE_TELEPHONY_FLAG)
    @CannotSetPolicyTest(policy = ManagedSubscriptions.class)
    @Postsubmit(reason = "new test")
    public void setManagedSubscriptionsPolicy_invalidAdmin_fails() {
        assertThrows(
                SecurityException.class, () -> sDeviceState
                        .dpc()
                        .devicePolicyManager()
                        .setManagedSubscriptionsPolicy(
                                new ManagedSubscriptionsPolicy(
                                        ManagedSubscriptionsPolicy
                                                .TYPE_ALL_MANAGED_SUBSCRIPTIONS)));
    }

    @EnsureFeatureFlagEnabled(namespace = NAMESPACE_DEVICE_POLICY_MANAGER, key =
            ENABLE_WORK_PROFILE_TELEPHONY_FLAG)
    @EnsureHasNoDpc
    @Postsubmit(reason = "new test")
    @Test
    public void setManagedSubscriptionsPolicy_policySet_oemDialerAndSmsAppInstalledInWorkProfile() {
        try (RemoteDpc dpc = RemoteDpc.createWorkProfile()) {
            ProfileOwner profileOwner = (ProfileOwner) dpc.devicePolicyController();
            profileOwner.setIsOrganizationOwned(true);
            UserHandle managedProfileUserHandle = dpc.user().userHandle();

            assertThat(TestApis.packages().oemDefaultDialerApp().installedOnUser(
                    managedProfileUserHandle)).isFalse();
            assertThat(TestApis.packages().oemDefaultSmsApp().installedOnUser(
                    managedProfileUserHandle)).isFalse();

            dpc.devicePolicyManager().setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));

            assertThat(TestApis.packages().oemDefaultDialerApp().installedOnUser(
                    managedProfileUserHandle)).isTrue();
            assertThat(TestApis.packages().oemDefaultSmsApp().installedOnUser(
                    managedProfileUserHandle)).isTrue();
        }
    }

    @EnsureFeatureFlagEnabled(namespace = NAMESPACE_DEVICE_POLICY_MANAGER, key =
            ENABLE_WORK_PROFILE_TELEPHONY_FLAG)
    @PolicyAppliesTest(policy = ManagedSubscriptions.class)
    @Postsubmit(reason = "new test")
    @Test
    public void setManagedSubscriptionsPolicy_callAndSmsIntent_resolvesToWorkProfileApps()
            throws InterruptedException {
        sDeviceState.dpc().devicePolicyManager().setManagedSubscriptionsPolicy(
                new ManagedSubscriptionsPolicy(
                        ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            List<ResolveInfo> dialIntentActivities = sPackageManager.queryIntentActivitiesAsUser(
                    new Intent(Intent.ACTION_DIAL).addCategory(
                            Intent.CATEGORY_DEFAULT).setData(
                            Uri.parse("tel:5555")), PackageManager.ResolveInfoFlags.of(0),
                    sDeviceState.workProfile().parent().id());
            List<ResolveInfo> smsIntentActivities = sPackageManager.queryIntentActivitiesAsUser(
                    new Intent(Intent.ACTION_SENDTO).addCategory(
                            Intent.CATEGORY_DEFAULT).setData(
                            Uri.parse("smsto:5555")), PackageManager.ResolveInfoFlags.of(0),
                    sDeviceState.workProfile().parent().id());

            assertThat(dialIntentActivities).hasSize(1);
            assertThat(
                    dialIntentActivities.get(0).isCrossProfileIntentForwarderActivity()).isTrue();
            assertThat(smsIntentActivities).hasSize(1);
            assertThat(smsIntentActivities.get(0).isCrossProfileIntentForwarderActivity()).isTrue();
        }
    }
}
