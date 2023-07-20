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

import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.EnsureCanAddUser;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class DeviceOwnerTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final TestApp TEST_ONLY_DPC = sDeviceState.testApps()
            .query().whereIsDeviceAdmin().isTrue()
            .whereTestOnly().isTrue()
            .get();

    // TODO: We should be able to query for the receiver rather than hardcoding
    private static final ComponentReference TEST_ONLY_DPC_COMPONENT =
            TEST_ONLY_DPC.pkg().component(
                    TEST_ONLY_DPC.packageName() + ".DeviceAdminReceiver");

    private static final TestApp NOT_TEST_ONLY_DPC = sDeviceState.testApps()
            .query().whereIsDeviceAdmin().isTrue()
            .whereTestOnly().isFalse()
            .get();

    private static final TestApp sRemoteDpcTestApp = sDeviceState.testApps().query()
            .wherePackageName().isEqualTo(RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
            .get();

    private static final ComponentReference NOT_TEST_ONLY_DPC_COMPONENT =
            NOT_TEST_ONLY_DPC.pkg().component(
                    NOT_TEST_ONLY_DPC.packageName() + ".DeviceAdminReceiver");

    private static final String SET_DEVICE_OWNER_COMMAND = "dpm set-device-owner";

    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final String FEATURE_ALLOW =
            "android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED";
    private static final String FEATURE_DISALLOW =
            "android.account.DEVICE_OR_PROFILE_OWNER_DISALLOWED";

    private static final String SET_DEVICE_OWNER_EXCEPTION_MSG =
            "Not allowed to set the device owner because there are already several users on the "
                    + "device.";

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @RequireRunOnSystemUser
    public void setDeviceOwner_setsDeviceOwner() {
        assertThat(sDevicePolicyManager.isAdminActive(sDeviceState.dpc().componentName()))
                .isTrue();
        assertThat(sDevicePolicyManager.isDeviceOwnerApp(sDeviceState.dpc().packageName()))
                .isTrue();
        assertThat(sDevicePolicyManager.getDeviceOwner())
                .isEqualTo(sDeviceState.dpc().packageName());
    }

    @UserTest({UserType.SYSTEM_USER, UserType.SECONDARY_USER})
    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "new test")
    public void getDeviceOwnerNameOnAnyUser_returnsDeviceOwnerName() {
        assertThat(sDevicePolicyManager.getDeviceOwnerNameOnAnyUser())
                .isEqualTo(sDeviceState.dpc().testApp().label());
    }

    @UserTest({UserType.SYSTEM_USER, UserType.SECONDARY_USER})
    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "new test")
    public void getDeviceOwnerComponentOnAnyUser_returnsDeviceOwnerComponent() {
        assertThat(sDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .isEqualTo(sDeviceState.dpc().componentName());
    }

    // All via adb methods use an additional user as we assume if it works cross user it'll work
    // same user

    // We use ensureHasNoAccounts and then create the account during the test because we want
    // to ensure there is only one account (and bedstead currently doesn't support this)

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @Test
    public void setDeviceOwnerViaAdb_noAccounts_testOnly_sets() throws Exception {
        try (TestAppInstance dpcApp = TEST_ONLY_DPC.install(TestApis.users().system())) {
            try {
                ShellCommand
                        .builder(SET_DEVICE_OWNER_COMMAND)
                        .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                        .validate(ShellCommandUtils::startsWithSuccess)
                        .execute();

                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @RequireNotHeadlessSystemUserMode(reason = "No non-testonly dpc which supports headless")
    @Test
    public void setDeviceOwnerViaAdb_noAccounts_notTestOnly_sets() throws Exception {
        try (TestAppInstance dpcApp = NOT_TEST_ONLY_DPC.install(TestApis.users().system())) {
            try {
                ShellCommand
                        .builder(SET_DEVICE_OWNER_COMMAND)
                        .addOperand(NOT_TEST_ONLY_DPC_COMPONENT.flattenToString())
                        .validate(ShellCommandUtils::startsWithSuccess)
                        .execute();

                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = {}, onUser = ADDITIONAL_USER)
    @Test
    public void setDeviceOwnerViaAdb_accountExistsWithNoFeatures_doesNotSet() {
        try (TestAppInstance dpcApp = TEST_ONLY_DPC.install(TestApis.users().system())) {
            try {
                assertThrows(AdbException.class, () ->
                        ShellCommand
                                .builder(SET_DEVICE_OWNER_COMMAND)
                                .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                                .execute());
                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        } finally {
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue("Active admins",
                            () -> TestApis.devicePolicy().getActiveAdmins(
                                    TestApis.users().system()))
                    .toMeet(i -> !i.contains(TEST_ONLY_DPC_COMPONENT))
                    .errorOnFail("Expected active admins to not contain DPC")
                    .await();
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = FEATURE_DISALLOW, onUser = ADDITIONAL_USER)
    @Test
    public void setDeviceOwnerViaAdb_accountExistsWithDisallowFeature_doesNotSet() {
        try (TestAppInstance dpcApp = TEST_ONLY_DPC.install(TestApis.users().system())) {
            try {
                assertThrows(AdbException.class, () ->
                        ShellCommand
                                .builder(SET_DEVICE_OWNER_COMMAND)
                                .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                                .execute());
                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        } finally {
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue("Active admins",
                            () -> TestApis.devicePolicy().getActiveAdmins(
                                    TestApis.users().system()))
                    .toMeet(i -> !i.contains(TEST_ONLY_DPC_COMPONENT))
                    .errorOnFail("Expected active admins to not contain DPC")
                    .await();
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = {FEATURE_ALLOW, FEATURE_DISALLOW}, onUser = ADDITIONAL_USER)
    @Test
    public void setDeviceOwnerViaAdb_accountExistsWithDisallowAndAllowFeatures_doesNotSet() {
        try (TestAppInstance dpcApp = TEST_ONLY_DPC.install(TestApis.users().system())) {
            try {
                assertThrows(AdbException.class, () ->
                        ShellCommand
                                .builder(SET_DEVICE_OWNER_COMMAND)
                                .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                                .execute());
                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        } finally {
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue("Active admins",
                            () -> TestApis.devicePolicy().getActiveAdmins(
                                    TestApis.users().system()))
                    .toMeet(i -> !i.contains(TEST_ONLY_DPC_COMPONENT))
                    .errorOnFail("Expected active admins to not contain DPC")
                    .await();
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = FEATURE_ALLOW, onUser = ADDITIONAL_USER)
    @Test
    public void setDeviceOwnerViaAdb_accountExistsWithAllowFeature_testOnly_sets()
            throws Exception {
        try (TestAppInstance dpcApp = TEST_ONLY_DPC.install(TestApis.users().system())) {
            try {
                ShellCommand
                        .builder(SET_DEVICE_OWNER_COMMAND)
                        .addOperand(TEST_ONLY_DPC_COMPONENT.flattenToString())
                        .validate(ShellCommandUtils::startsWithSuccess)
                        .execute();

                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        }
    }

    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = UserType.ANY)
    @EnsureHasAccount(features = FEATURE_ALLOW, onUser = ADDITIONAL_USER)
    @Test
    public void setDeviceOwnerViaAdb_accountExistsWithAllowFeature_notTestOnly_doesNotSet() {
        try (TestAppInstance dpcApp = NOT_TEST_ONLY_DPC.install(TestApis.users().system())) {
            try {
                assertThrows(AdbException.class, () ->
                        ShellCommand
                                .builder(SET_DEVICE_OWNER_COMMAND)
                                .addOperand(NOT_TEST_ONLY_DPC_COMPONENT.flattenToString())
                                .validate(ShellCommandUtils::startsWithSuccess)
                                .execute());
                assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        } finally {
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue("Active admins",
                            () -> TestApis.devicePolicy().getActiveAdmins(
                                    TestApis.users().system()))
                    .toMeet(i -> !i.contains(NOT_TEST_ONLY_DPC_COMPONENT))
                    .errorOnFail("Expected active admins to not contain DPC")
                    .await();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED")
    @EnsureHasNoDpc
    @Postsubmit(reason = "new test")
    @Test
    public void setDeviceOwner_receivesOwnerChangedBroadcast() {
        try (BlockingBroadcastReceiver broadcastReceiver =
                     sDeviceState.registerBroadcastReceiver(
                             DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED)) {
            RemoteDpc.setAsDeviceOwner();

            broadcastReceiver.awaitForBroadcastOrFail();
        } finally {
            DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
            if (deviceOwner != null) {
                deviceOwner.remove();
            }
        }
    }

    @EnsureHasNoDpc
    @EnsureCanAddUser
    @Postsubmit(reason = "new test")
    @Test
    public void setDeviceOwner_hasSecondaryUser_throwsException() {
        try (UserReference secondaryUser =
                     TestApis.users().createUser().forTesting(false).create()) {
            try {
                assertThrows("Error setting device owner.",
                        NeneException.class, () -> RemoteDpc.setAsDeviceOwner());
            } finally {
                DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
                if (deviceOwner != null) {
                    deviceOwner.remove();
                }
            }
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#isProvisioningAllowed")
    @Postsubmit(reason = "new test")
    @Test
    public void isProvisioningAllowed_forManagedDevice_setupWizardIsComplete_returnsFalse() {
        TestApis.users().instrumented().setSetupComplete(true);
        try (TestAppInstance testApp = sRemoteDpcTestApp.install()) {
            assertThat(sDevicePolicyManager.isProvisioningAllowed(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)).isFalse();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @EnsureHasDeviceOwner
    @Test
    public void clearDeviceOwnerApp_escrowTokenExists_success() {
        RemoteDevicePolicyManager dpm = sDeviceState.dpc().devicePolicyManager();
        assertThat(dpm.setResetPasswordToken(sDeviceState.dpc().componentName(), new byte[32]))
                .isTrue();
        // clearDeviceOwnerApp should not throw, and isDeviceOwnerApp should return false afterwards
        dpm.clearDeviceOwnerApp(sDeviceState.dpc().packageName());
        assertThat(dpm.isDeviceOwnerApp(sDeviceState.dpc().packageName())).isFalse();
    }

    @EnsureHasDeviceOwner
    @Test
    @Postsubmit(reason = "new test")
    public void disableComponentInDeviceOwnerViaAdb_throwsException() {
        String component = sDeviceState.dpc().packageName()+ "/" +
                sDeviceState.dpc().testApp().activities().query().get().className();

        try {
            Package.of(component).disable();
            fail("AdbException should be thrown here.");
        } catch (NeneException e) {
            assertThat(e.getCause() instanceof AdbException).isTrue();
            assertThat(((AdbException) e.getCause()).error()).contains(
                    "Cannot disable a protected package: " + sDeviceState.dpc().packageName());
        }
    }

    @EnsureHasDeviceOwner
    @Test
    @Postsubmit(reason = "new test")
    public void disableDeviceOwnerViaAdb_throwsException() {
        try {
            sDeviceState.dpc().pkg().disable();
            fail("AdbException should be thrown here.");
        } catch (NeneException e) {
            assertThat(e.getCause() instanceof AdbException).isTrue();
            assertThat(((AdbException) e.getCause()).error()).contains(
                    "Cannot disable a protected package: " + sDeviceState.dpc().packageName());
        }
    }
}
