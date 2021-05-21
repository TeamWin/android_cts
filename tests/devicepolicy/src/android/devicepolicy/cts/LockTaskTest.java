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

import static com.android.bedstead.nene.permissions.Permissions.MANAGE_DEVICE_ADMINS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.NegativePolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PositivePolicyTest;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.LockTaskPackages;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

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
    public void setLockTaskPackages_empty_lockTaskPackagesIsSet() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages();

        sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{});

        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().getLockTaskPackages()).asList()
                    .isEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(originalLockTaskPackages);
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    // TODO(scottjonathan): This omits the metrics test
    @PositivePolicyTest(policy = LockTaskPackages.class)
    @EnsureHasPermission(MANAGE_DEVICE_ADMINS) // Used for getPolicyExemptApps
    public void setLockTaskPackages_includesPolicyExemptApp_lockTaskPackagesIsSet() {
        Set<String> policyExemptApps = mDevicePolicyManager.getPolicyExemptApps();
        assumeFalse("OEM does not define any policy-exempt apps",
                policyExemptApps.isEmpty());
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages();
        String policyExemptApp = policyExemptApps.iterator().next();

        sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{policyExemptApp});

        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().getLockTaskPackages()).asList()
                    .containsExactly(policyExemptApp);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(originalLockTaskPackages);
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = LockTaskPackages.class)
    public void setLockTaskPackages_policyIsNotAllowedToBeSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{}));
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

    @Test
    @Postsubmit(reason = "New test")
    @PositivePolicyTest(policy = LockTaskPackages.class)
    @EnsureHasPermission(MANAGE_DEVICE_ADMINS) // Used for getPolicyExemptApps
    public void isLockTaskPermitted_includesPolicyExemptApps() {
        Set<String> policyExemptApps = mDevicePolicyManager.getPolicyExemptApps();
        // TODO(b/188035301): Add a unit test which ensures this actually gets tested
        assumeFalse("OEM does not define any policy-exempt apps",
                policyExemptApps.isEmpty());
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages();

        try {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(new String[]{});

            for (String app : policyExemptApps) {
                assertWithMessage("isLockTaskPermitted(%s)", app)
                        .that(mDevicePolicyManager.isLockTaskPermitted(app)).isTrue();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(originalLockTaskPackages);
        }
    }
}
