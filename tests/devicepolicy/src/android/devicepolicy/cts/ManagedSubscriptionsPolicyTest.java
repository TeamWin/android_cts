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

import static android.provider.DeviceConfig.NAMESPACE_DEVICE_POLICY_MANAGER;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedSubscriptionsPolicy;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureFeatureFlagEnabled;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.ManagedSubscriptions;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class ManagedSubscriptionsPolicyTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

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
}
