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

import static android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.testng.Assert.assertThrows;

import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.LocalPresubmit;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.PasswordComplexity;
import com.android.bedstead.harrier.policies.RequiredPasswordComplexity;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.IgnoreExceptions;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.steps.settings.password.IsItPossibleToSetNoScreenLockOrPasswordStep;
import com.android.interactive.steps.settings.password.IsItPossibleToSetPin1591Step;
import com.android.interactive.steps.settings.password.IsItPossibleToSetPin4444Step;
import com.android.interactive.steps.settings.password.SetNoScreenLockOrPasswordStep;
import com.android.interactive.steps.settings.password.SetPin15911591Step;
import com.android.interactive.steps.settings.password.SetPin1591Step;
import com.android.interactive.steps.settings.password.SetPin4444Step;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PasswordComplexityTest { // Skipped checking on headless because of known password bugs

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final int PASSWORD_COMPLEXITY = PASSWORD_COMPLEXITY_HIGH;

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class) // TODO: Remove
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getRequiredPasswordComplexity")
    public void getRequiredPasswordComplexity_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().getRequiredPasswordComplexity();
    }

    @CannotSetPolicyTest(policy = RequiredPasswordComplexity.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getRequiredPasswordComplexity")
    public void getRequiredPasswordComplexity_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().getRequiredPasswordComplexity());
    }

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class) // TODO: Remove
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    @LocalPresubmit
    public void setRequiredPasswordComplexity_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager()
                .setRequiredPasswordComplexity(PASSWORD_COMPLEXITY);
    }

    @CannotSetPolicyTest(policy = RequiredPasswordComplexity.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setRequiredPasswordComplexity(PASSWORD_COMPLEXITY));
    }

    // Because shell doesn't currently hold the MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS permission
    // we can't test the local receiver so we can only use a cansetpolicy test - once we add the
    // permission to shell we can create policy applies + policy does not apply tests.
    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_requiredComplexityIsSet() {
        int originalRequiredPasswordComplexity = sDeviceState.dpc().devicePolicyManager()
                .getRequiredPasswordComplexity();
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setRequiredPasswordComplexity(PASSWORD_COMPLEXITY);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getRequiredPasswordComplexity()).isEqualTo(PASSWORD_COMPLEXITY);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    originalRequiredPasswordComplexity);
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class) // TODO: Remove
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_doesNotThrowSecurityException() {
        sDeviceState.dpc().devicePolicyManager().getPasswordComplexity();
    }

    @CannotSetPolicyTest(policy = PasswordComplexity.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_noPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().getPasswordComplexity());
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_noPassword_returnsPasswordComplexityNone() {
        assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                .isEqualTo(PASSWORD_COMPLEXITY_NONE);
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_patternLength4_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPattern("1234");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPattern();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_patternLength8_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPattern("13246587");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPattern();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinOrderedLength4_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPin("1234");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinOrderedLength8_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPin("12345678");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinNotOrderedLength4_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPin("1238");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinNotOrderedLength7_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPin("1238964");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphabeticLength4_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("c!qw");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphabeticLength5_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("bc!qw");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphanumericLength4_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("c!23");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphanumericLength5_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("bc!23");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinNotOrderedLength8_returnsPasswordComplexityHigh() {
        try {
            sDeviceState.dpc().user().setPin("12389647");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphabeticLength6_returnsPasswordComplexityHigh() {
        try {
            sDeviceState.dpc().user().setPassword("abc!qw");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphanumericLength6_returnsPasswordComplexityHigh() {
        try {
            sDeviceState.dpc().user().setPassword("abc!23");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    private static final Intent sSetPasswordIntent =
            new Intent(ACTION_SET_NEW_PASSWORD)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);


    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_none_canSetNone() throws Exception {
        assumeFalse("NONE is not supported on profiles",
                TestApis.users().instrumented().isProfile());
        sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                PASSWORD_COMPLEXITY_NONE);

        // We trampoline via a dpc activity as some DPCs cannot start activities from
        // the background
        // TODO(scottjonathan): Consider here if we can use lock task to stop the tester from
        // just closing the activity
        sDeviceState.dpc().activities().any().start().startActivity(sSetPasswordIntent);

        Step.execute(SetNoScreenLockOrPasswordStep.class);
        assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                .isEqualTo(PASSWORD_COMPLEXITY_NONE);
    }

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_low_cannotSetNone() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_LOW);

            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            sDeviceState.dpc().activities().any().start().startActivity(sSetPasswordIntent);

            assertThat(Step.execute(IsItPossibleToSetNoScreenLockOrPasswordStep.class)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
        }
    }

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_low_canSetLowComplexity() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_LOW);
            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            sDeviceState.dpc().activities().any().start().startActivity(sSetPasswordIntent);

            Step.execute(SetPin4444Step.class);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                sDeviceState.dpc().user().clearPin("4444");
            });
        }
    }

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_medium_cannotSetLowComplexity() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            sDeviceState.dpc().activities().any().start().startActivity(sSetPasswordIntent);

            assertThat(Step.execute(IsItPossibleToSetPin4444Step.class)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                sDeviceState.dpc().user().clearPin("4444");
            });
        }
    }

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_medium_canSetMediumComplexity() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);
            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            sDeviceState.dpc().activities().any().start().startActivity(sSetPasswordIntent);

            Step.execute(SetPin1591Step.class);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                sDeviceState.dpc().user().clearPin("1591");
            });
        }
    }

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_high_cannotSetMediumComplexity() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_HIGH);

            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            sDeviceState.dpc().activities().any().start().startActivity(sSetPasswordIntent);

            assertThat(Step.execute(IsItPossibleToSetPin1591Step.class)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                sDeviceState.dpc().user().clearPin("1591");
            });
        }
    }

    @CanSetPolicyTest(policy = RequiredPasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_high_canSetHighComplexity() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_HIGH);
            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            sDeviceState.dpc().activities().any().start().startActivity(sSetPasswordIntent);

            Step.execute(SetPin15911591Step.class);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                sDeviceState.dpc().user().clearPin("15911591");
            });
        }
    }
}
