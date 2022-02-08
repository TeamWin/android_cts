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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.NearbyNotificationStreamingPolicy;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class NearbyNotificationStreamingPolicyTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private RemoteDevicePolicyManager mDevicePolicyManager;

    @Before
    public void setUp() {
        mDevicePolicyManager = sDeviceState.dpc().devicePolicyManager();
    }

    @Test
    @PolicyAppliesTest(policy = NearbyNotificationStreamingPolicy.class)
    @Postsubmit(reason = "new test")
    public void getNearbyNotificationStreamingPolicy_defaultToSameManagedAccountOnly() {
        assertThat(mDevicePolicyManager.getNearbyNotificationStreamingPolicy())
                .isEqualTo(DevicePolicyManager.NEARBY_STREAMING_SAME_MANAGED_ACCOUNT_ONLY);
    }

    @Test
    @PolicyAppliesTest(policy = NearbyNotificationStreamingPolicy.class)
    @Postsubmit(reason = "new test")
    public void setNearbyNotificationStreamingPolicy_policyApplied_works() {
        int originalPolicy = mDevicePolicyManager.getNearbyNotificationStreamingPolicy();

        mDevicePolicyManager.setNearbyNotificationStreamingPolicy(
                DevicePolicyManager.NEARBY_STREAMING_DISABLED);

        try {
            assertThat(mDevicePolicyManager.getNearbyNotificationStreamingPolicy())
                    .isEqualTo(DevicePolicyManager.NEARBY_STREAMING_DISABLED);
        } finally {
            mDevicePolicyManager.setNearbyAppStreamingPolicy(originalPolicy);
        }
    }

    @Test
    @CannotSetPolicyTest(policy = NearbyNotificationStreamingPolicy.class)
    @Postsubmit(reason = "new test")
    public void setNearbyAppStreamingPolicy_policyIsNotAllowedToBeSet_throwsException() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.setNearbyNotificationStreamingPolicy(
                        DevicePolicyManager.NEARBY_STREAMING_DISABLED));
    }
}
