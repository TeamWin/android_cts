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

import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_AUTOMOTIVE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.UnsafeStateException;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsurePolicyOperationUnsafe;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.policies.LockNow;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.CommonDevicePolicy;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class DevicePolicySafetyCheckerTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    private static final int OPERATION_SAFETY_REASON_DRIVING_DISTRACTION =
            CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION.getValue();

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    @EnsurePolicyOperationUnsafe(
            operation = CommonDevicePolicy.DevicePolicyOperation.OPERATION_LOCK_NOW,
            reason =
                    CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION)
    @CanSetPolicyTest(policy = LockNow.class)
    @Postsubmit(reason = "new test")
    public void lockNow_isUnsafe_throwsUnsafeStateException() {
        assertThrows(UnsafeStateException.class, () ->
                sDeviceState.dpc().devicePolicyManager().lockNow());
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#isSafeOperation")
    @EnsurePolicyOperationUnsafe(
            operation = CommonDevicePolicy.DevicePolicyOperation.OPERATION_LOCK_NOW,
            reason =
                    CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION)
    @Test
    @Postsubmit(reason = "new test")
    public void isSafeOperation_isNotSafe_returnsFalse() {
        assertThat(sDevicePolicyManager.isSafeOperation(
                OPERATION_SAFETY_REASON_DRIVING_DISTRACTION)).isFalse();
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#isSafeOperation")
    @Test
    @Postsubmit(reason = "new test")
    public void isSafeOperation_isSafe_returnsTrue() {
            assertThat(sDevicePolicyManager.isSafeOperation(
                    OPERATION_SAFETY_REASON_DRIVING_DISTRACTION)).isTrue();
    }
}
