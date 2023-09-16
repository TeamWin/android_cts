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

import static com.android.queryable.queries.BundleQuery.bundle;
import static com.android.queryable.queries.ReceiverQuery.receiver;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.PersistableBundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.harrier.policies.TransferOwnership;
import com.android.bedstead.harrier.policies.TransferOwnershipForDeviceOwner;
import com.android.bedstead.harrier.policies.TransferOwnershipForProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.queryable.queries.BundleQueryHelper;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/298202673: Add tests for behavior for all policies)
@RunWith(BedsteadJUnit4.class)
public final class TransferOwnershipTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static BundleQueryHelper.BundleQueryBase sTransferOwnershipMetadataQuery =
            bundle().where().key(
                    "supports-transfer-ownership").stringValue().isEqualTo("true");

    private static final TestApp sTargetDeviceAdminTestAppSupportsTransferOwnership =
            sDeviceState.testApps().query()
                    .whereIsDeviceAdmin().isTrue()
                    .whereReceivers().contains(
                            receiver().where().metadata().contains(
                                    sTransferOwnershipMetadataQuery)).get();

    private static final TestApp sTargetDeviceAdminTestAppDoesNotSupportTransferOwnership =
            sDeviceState.testApps().query()
                    .whereIsDeviceAdmin().isTrue()
                    .whereReceivers().contains(
                            receiver().where().metadata().doesNotContain(
                                    sTransferOwnershipMetadataQuery)).get();

    private static final String KEY = "VALUE";
    private static final String ACTION_DEVICE_OWNER_CHANGED
            = "android.app.action.DEVICE_OWNER_CHANGED";
    private static final String ACTION_PROFILE_OWNER_CHANGED
            = "android.app.action.PROFILE_OWNER_CHANGED";
    private static final PersistableBundle sBundle = new PersistableBundle();

    static {
        sBundle.putBoolean(KEY, true);
    }

    private static final PersistableBundle sEmptyBundle = new PersistableBundle();

    private static final ComponentName sTargetAdmin =
            new ComponentName(sTargetDeviceAdminTestAppSupportsTransferOwnership.packageName(),
                    sTargetDeviceAdminTestAppSupportsTransferOwnership.packageName()
                            + ".DeviceAdminReceiver");

    private static final ComponentName sInvalidComponentName =
            new ComponentName("invalid", "invalid");

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership",
            "android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnership.class)
    public void transferOwnership_getTransferOwnershipBundle_bundleReceivedByTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(testApp.devicePolicyManager().getTransferOwnershipBundle()
                        .getBoolean(KEY)).isTrue();
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = TransferOwnership.class, includeNonDeviceAdminStates = false)
    public void transferOwnership_cannotSet_throwsException() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
                assertThrows(SecurityException.class, () ->
                        sDeviceState.dpc().devicePolicyManager().transferOwnership(
                                sDeviceState.dpc().componentName(), sTargetAdmin, sBundle)
                );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership",
            "android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnership.class)
    public void transferOwnership_nullBundleTransferred_getTransferOwnershipBundle_emptyBundleReceivedByTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, /* bundle= */ null);

                assertThat(testApp.devicePolicyManager().getTransferOwnershipBundle()
                        .isEmpty()).isTrue();
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnershipForDeviceOwner.class)
    public void transferOwnership_deviceOwner_ownershipTransferredToTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(TestApis.devicePolicy().getDeviceOwner().componentName())
                        .isEqualTo(sTargetAdmin);
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnershipForProfileOwner.class)
    public void transferOwnership_profileOwner_ownershipTransferredToTargetAdmin() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(TestApis.devicePolicy().getProfileOwner().componentName())
                        .isEqualTo(sTargetAdmin);
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnership.class)
    public void getTransferOwnershipBundle_nonDpc_throwsException() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThrows(SecurityException.class,
                        () -> sLocalDevicePolicyManager.getTransferOwnershipBundle());
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnership.class)
    public void transferOwnership_invalidTarget_throwsException() {
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sDeviceState.dpc().devicePolicyManager().transferOwnership(
                            sDeviceState.dpc().componentName(), sInvalidComponentName, sBundle));
        } finally {
            removeDeviceAdmin();
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @EnsureHasProfileOwner
    @Test
    public void transferOwnership_disableCamera_policyRetainedAfterTransfer() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                        sDeviceState.dpc().componentName(), true);

                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(testApp.devicePolicyManager().getCameraDisabled(sTargetAdmin)).isTrue();
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @EnsureHasProfileOwner
    @Test
    public void transferOwnership_profileOwner_sendsOwnerChangedBroadcast() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try (BlockingBroadcastReceiver receiver =
                     sDeviceState.registerBroadcastReceiver(ACTION_PROFILE_OWNER_CHANGED)) {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(receiver.awaitForBroadcast().getAction()).isEqualTo(
                        ACTION_PROFILE_OWNER_CHANGED);
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @Test
    public void transferOwnership_deviceOwner_sendsOwnerChangedBroadcast() {
        try (TestAppInstance testApp = sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            try (BlockingBroadcastReceiver receiver =
                         sDeviceState.registerBroadcastReceiver(ACTION_DEVICE_OWNER_CHANGED)) {
                sDeviceState.dpc().devicePolicyManager().transferOwnership(
                        sDeviceState.dpc().componentName(), sTargetAdmin, sBundle);

                assertThat(receiver.awaitForBroadcast().getAction()).isEqualTo(
                        ACTION_DEVICE_OWNER_CHANGED);
            } finally {
                removeDeviceAdmin();
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnership.class)
    public void transferOwnership_noMetadata_throwsException() {
        try (TestAppInstance testApp =
                     sTargetDeviceAdminTestAppDoesNotSupportTransferOwnership.install()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sDeviceState.dpc().devicePolicyManager().transferOwnership(
                            sDeviceState.dpc().componentName(), sTargetAdmin, sEmptyBundle));
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#transferOwnership"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = TransferOwnership.class)
    public void transferOwnership_sameAdmin_throwsException() {
        try (TestAppInstance testApp =
                     sTargetDeviceAdminTestAppSupportsTransferOwnership.install()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sDeviceState.dpc().devicePolicyManager().transferOwnership(
                            sDeviceState.dpc().componentName(), sDeviceState.dpc().componentName(),
                            sEmptyBundle));
        }
    }

    /**
     * Remove whichever device admin (device owner or profile owner) the test is running for.
     */
    private void removeDeviceAdmin() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        if (deviceOwner != null) {
            // if the test ran for the device owner
            deviceOwner.remove();
        }

        ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner();
        if (profileOwner != null) {
            // if the test ran for the profile owner
            profileOwner.remove();
        }
    }

}
