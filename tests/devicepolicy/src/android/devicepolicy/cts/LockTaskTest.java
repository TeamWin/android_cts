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

import android.app.admin.DevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.enterprise.NegativePolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PositivePolicyTest;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.LockTaskPackages;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class LockTaskTest {

    private static final String PACKAGE_NAME = "com.android.package.test";

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();

    private static final DevicePolicyManager mDevicePolicyManager =
            sTestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @Test
    @Postsubmit(reason = "New test")
    // TODO(scottjonathan): This omits the metrics test
    @PositivePolicyTest(policy = LockTaskPackages.class)
    public void setLockTaskPackages_lockTaskPackagesIsSet() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages();

        sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{PACKAGE_NAME});

        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().getLockTaskPackages()).asList()
                    .containsExactly(PACKAGE_NAME);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(originalLockTaskPackages);
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    // TODO(scottjonathan): This omits the metrics test
    @PositivePolicyTest(policy = LockTaskPackages.class)
    public void isLockTaskPermitted_lockTaskPackageIsSet_returnsTrue() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages();

        sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{PACKAGE_NAME});

        try {
            assertThat(mDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(originalLockTaskPackages);
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    @NegativePolicyTest(policy = LockTaskPackages.class)
    // TODO(scottjonathan): Confirm expected behaviour here
    public void isLockTaskPermitted_lockTaskPackageIsSet_policyDoesntApply_returnsFalse() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages();

        sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{PACKAGE_NAME});

        try {
            assertThat(mDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(originalLockTaskPackages);
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    // TODO(scottjonathan): This omits the metrics test
    @PositivePolicyTest(policy = LockTaskPackages.class)
    public void isLockTaskPermitted_lockTaskPackageIsNotSet_returnsFalse() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages();

        sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{});

        try {
            assertThat(mDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(originalLockTaskPackages);
        }
    }
}
