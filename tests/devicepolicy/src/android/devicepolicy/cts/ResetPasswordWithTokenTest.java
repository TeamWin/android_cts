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

import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PositivePolicyTest;
import com.android.bedstead.harrier.policies.ResetPasswordWithToken;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class ResetPasswordWithTokenTest {

    private static final String NOT_COMPLEX_PASSWORD = "1234";
    private static final String VALID_PASSWORD = NOT_COMPLEX_PASSWORD;
    private static final String COMPLEX_PASSWORD_WITH_1_SYMBOL = "abc123.";
    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();
    private static final byte[] BAD_TOKEN = "abcdefghijklmnopqrstuvwxyz012345678*".getBytes();

    private static final String RESET_PASSWORD_TOKEN_DISABLED =
            "Cannot reset password token as it is disabled for the primary user";

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private final KeyguardManager sLocalKeyguardManager =
            sContext.getSystemService(KeyguardManager.class);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void setResetPasswordToken_validToken_passwordTokenSet() {
        try {
            boolean possible = canSetResetPasswordToken(TOKEN);

            assertThat(sDeviceState.dpc().devicePolicyManager().isResetPasswordTokenActive(
                    DPC_COMPONENT_NAME) || !possible).isTrue();
        } finally {
            // Remove password token
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(DPC_COMPONENT_NAME);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, VALID_PASSWORD, TOKEN, /* flags = */ 0)).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_badToken_failure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, VALID_PASSWORD, BAD_TOKEN, /* flags = */ 0)).isFalse();
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_noPassword_deviceIsNotSecure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, /* password = */ null, TOKEN, /* flags = */ 0);

        // Device is not secure when no password is set
        assertThat(sLocalKeyguardManager.isDeviceSecure()).isFalse();
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_password_deviceIsSecure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, VALID_PASSWORD, TOKEN, /* flags = */ 0);

            // Device is secure when a password is set
            assertThat(sLocalKeyguardManager.isDeviceSecure()).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_passwordDoesNotSatisfyRestriction_failure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                    DPC_COMPONENT_NAME, 6);

            // Password cannot be set as it does not satisfy the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, NOT_COMPLEX_PASSWORD, TOKEN, /* flags = */ 0)).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_passwordSatisfiesRestriction_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                    DPC_COMPONENT_NAME, 6);

            // Password can be set as it satisfies the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, COMPLEX_PASSWORD_WITH_1_SYMBOL, TOKEN,
                    /* flags = */ 0)).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_logged() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, VALID_PASSWORD, TOKEN, /* flags = */ 0);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.RESET_PASSWORD_WITH_TOKEN_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().componentName().getPackageName())).wasLogged();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void isActivePasswordSufficient_passwordDoesNotSatisfyRestriction_false() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, NOT_COMPLEX_PASSWORD, TOKEN, /* flags = */ 0);
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                    DPC_COMPONENT_NAME, 6);

            // Password is insufficient because it does not satisfy the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void isActivePasswordSufficient_passwordSatisfiesRestriction_true() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, COMPLEX_PASSWORD_WITH_1_SYMBOL, TOKEN, /* flags = */ 0);
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                    DPC_COMPONENT_NAME, 6);

            // Password is sufficient because it satisfies the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @PositivePolicyTest(policy = ResetPasswordWithToken.class)
    public void isActivePasswordSufficient_passwordNoLongerSatisfiesRestriction_false() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(DPC_COMPONENT_NAME,
                    PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                    DPC_COMPONENT_NAME, 1);
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, COMPLEX_PASSWORD_WITH_1_SYMBOL, TOKEN, /* flags = */ 0);
            // Set a slightly stronger password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                    DPC_COMPONENT_NAME, 2);

            // Password is no longer sufficient because it does not satisfy the new restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    private void removeAllPasswordRestrictions() {
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(DPC_COMPONENT_NAME,
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLength(DPC_COMPONENT_NAME, 0);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(DPC_COMPONENT_NAME, 0);
    }

    private void removePasswordAndToken(byte[] token) {
        sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, /* password = */ null, token, /* flags = */ 0);
        sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(DPC_COMPONENT_NAME);
    }


    // If ResetPasswordWithTokenTest for managed profile is executed before device owner and
    // primary user profile owner tests, password reset token would have been disabled for the
    // primary user, so executing ResetPasswordWithTokenTest on user 0 would fail. We allow this
    // and do not fail the test in this case.
    private boolean canSetResetPasswordToken(byte[] token) {
        try {
            sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                    DPC_COMPONENT_NAME, token);
            return true;
        } catch (SecurityException e) {
            if (allowFailure(e)) {
                return false;
            } else {
                throw e;
            }
        }
    }

    // Password token is disabled for the primary user, allow failure.
    private static boolean allowFailure(SecurityException e) {
        return !sDeviceState.dpc().devicePolicyManager().isManagedProfile(DPC_COMPONENT_NAME)
                && e.getMessage().equals("Escrow token is disabled on the current user");
    }
}
