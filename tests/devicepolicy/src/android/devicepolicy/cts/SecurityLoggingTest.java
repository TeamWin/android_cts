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

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoCloneProfile;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.LocalPresubmit;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.SecurityLogging;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SecurityLoggingTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = SecurityLogging.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled")
    public void setSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                        sDeviceState.dpc().componentName(), true));
    }

    @CanSetPolicyTest(policy = SecurityLogging.class) // TODO: Remove
    @LocalPresubmit
    public void setSecurityLoggingEnabled_doesNotThrowSecurityException() {
        sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true);
    }

    @CanSetPolicyTest(policy = SecurityLogging.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
            "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"})
    @LocalPresubmit
    public void setSecurityLoggingEnabled_true_securityLoggingIsEnabled() {
        boolean originalSecurityLoggingEnabled = sDeviceState.dpc()
                .devicePolicyManager().isSecurityLoggingEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled);
        }
    }

    @CanSetPolicyTest(policy = SecurityLogging.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
            "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"})
    @LocalPresubmit
    public void setSecurityLoggingEnabled_false_securityLoggingIsNotEnabled() {
        boolean originalSecurityLoggingEnabled = sDeviceState.dpc()
                .devicePolicyManager().isSecurityLoggingEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(sDeviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled);
        }
    }

    @CanSetPolicyTest(policy = SecurityLogging.class) // TODO: Remove
    @LocalPresubmit
    public void isSecurityLoggingEnabled_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                sDeviceState.dpc().componentName());
    }

    @CannotSetPolicyTest(policy = SecurityLogging.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled")
    public void isSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                        sDeviceState.dpc().componentName()));
    }

    @CanSetPolicyTest(policy = SecurityLogging.class) // TODO: Remove
    @LocalPresubmit
    // We need no additional users incase one is unaffiliated
    @EnsureHasNoWorkProfile
    @EnsureHasNoAdditionalUser
    @EnsureHasNoCloneProfile
    public void retrieveSecurityLogs_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                sDeviceState.dpc().componentName());
    }

    @CannotSetPolicyTest(policy = SecurityLogging.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#retrieveSecurityLogs")
    public void retrieveSecurityLogs_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                        sDeviceState.dpc().componentName()));
    }

    @CanSetPolicyTest(policy = SecurityLogging.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#retrieveSecurityLogs")
    // We need no additional users incase one is unaffiliated
    @EnsureHasNoWorkProfile
    @EnsureHasNoAdditionalUser
    @EnsureHasNoCloneProfile
    public void retrieveSecurityLogs_returnsSecurityLogs() {
        boolean originalSecurityLoggingEnabled =
                sDeviceState.dpc().devicePolicyManager()
                        .isSecurityLoggingEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), true);

            // TODO: Generate some security logs and assert on them

            sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                    sDeviceState.dpc().componentName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled);
        }
    }

    // TODO: Add test that requires all users are affiliated for device owner
    // TODO: Add test that logs are filtered for non-device-owner
    // TODO: Add test for rate limiting
    // TODO: Add test for onSecurityLogsAvailable

    @CanSetPolicyTest(policy = SecurityLogging.class) // TODO: Remove
    @LocalPresubmit
    // We need no additional users incase one is unaffiliated
    @EnsureHasNoWorkProfile
    @EnsureHasNoAdditionalUser
    @EnsureHasNoCloneProfile
    public void retrievePreRebootSecurityLogs_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                sDeviceState.dpc().componentName());
    }

    @CannotSetPolicyTest(policy = SecurityLogging.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs")
    public void retrievePreRebootSecurityLogs_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                        sDeviceState.dpc().componentName()));
    }

    @CanSetPolicyTest(policy = SecurityLogging.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs")
    // We need no additional users incase one is unaffiliated
    @EnsureHasNoWorkProfile
    @EnsureHasNoAdditionalUser
    @EnsureHasNoCloneProfile
    @LocalPresubmit
    public void retrievePreRebootSecurityLogs_doesNotThrow() {
        boolean originalSecurityLoggingEnabled =
                sDeviceState.dpc().devicePolicyManager()
                        .isSecurityLoggingEnabled(sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), true);

            // Nothing to assert as this can be null on some devices
            sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                    sDeviceState.dpc().componentName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled);
        }
    }
}
