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

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.os.PersistableBundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class TransferOwnershipTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final TestApp sTargetDeviceAdminTestApp = sDeviceState.testApps().query()
            .whereIsDeviceAdmin().isTrue().get();
    private static final String KEY = "VALUE";
    private static final PersistableBundle sBundle = new PersistableBundle();

    static {
        sBundle.putBoolean(KEY, true);
    }

    private static final ComponentName sTargetAdmin =
            new ComponentName(sTargetDeviceAdminTestApp.packageName(),
                    sTargetDeviceAdminTestApp.packageName() + ".DeviceAdminReceiver");

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership",
            "android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"})
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @Test
    public void transferOwnership_deviceOwner_getTransferOwnershipBundle_bundleReceivedByTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(testApp.devicePolicyManager().getTransferOwnershipBundle()
                        .getBoolean(KEY)).isTrue();
            } finally {
                TestApis.devicePolicy().getDeviceOwner().remove();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @Test
    public void transferOwnership_deviceOwner_ownershipTransferredToTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(TestApis.devicePolicy().getDeviceOwner()
                        .componentName()).isEqualTo(sTargetAdmin);
            } finally {
                TestApis.devicePolicy().getDeviceOwner().remove();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership",
            "android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"})
    @Postsubmit(reason = "new test")
    @EnsureHasProfileOwner
    @Test
    public void transferOwnership_profileOwner_getTransferOwnershipBundle_bundleReceivedByTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(testApp.devicePolicyManager().getTransferOwnershipBundle()
                        .getBoolean(KEY)).isTrue();
            } finally {
                TestApis.devicePolicy().getProfileOwner().remove();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @EnsureHasProfileOwner
    @Test
    public void transferOwnership_profileOwner_ownershipTransferredToTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(TestApis.devicePolicy().getProfileOwner()
                        .componentName()).isEqualTo(sTargetAdmin);
            } finally {
                TestApis.devicePolicy().getProfileOwner().remove();
            }
        }
    }

}
