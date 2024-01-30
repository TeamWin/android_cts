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
package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.os.Build
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.Defaults
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet
import com.android.bedstead.harrier.annotations.EnsurePasswordSet
import com.android.bedstead.harrier.annotations.EnsureUnlocked
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest
import com.android.bedstead.harrier.policies.DeprecatedResetPassword
import com.android.bedstead.harrier.policies.FailedPasswordAttempts
import com.android.bedstead.harrier.policies.PasswordExpirationTimeout
import com.android.bedstead.harrier.policies.PasswordSufficiency
import com.android.bedstead.harrier.policies.StrongAuthTimeout
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.android.interactive.annotations.Interactive
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class PasswordTest {

    @CannotSetPolicyTest(
        policy = [PasswordExpirationTimeout::class],
        includeNonDeviceAdminStates = false
    )
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setPasswordExpirationTimeout"])
    fun setPasswordExpirationTimeout_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
        }
    }

    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = [PasswordExpirationTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setPasswordExpirationTimeout", "android.app.admin.DevicePolicyManager#getPasswordExpirationTimeout"])
    @Ignore("b/303206765 bug with propagation to clone profiles")
    fun setPasswordExpirationTimeout_passwordExpirationTimeoutIsSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getPasswordExpirationTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
            assertThat(TestApis.devicePolicy().getPasswordExpirationTimeout()).isEqualTo(
                TIMEOUT
            )
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @PolicyDoesNotApplyTest(policy = [PasswordExpirationTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setPasswordExpirationTimeout", "android.app.admin.DevicePolicyManager#getPasswordExpirationTimeout"])
    fun setPasswordExpirationTimeout_doesNotApply_passwordExpirationTimeoutIsNotSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getPasswordExpirationTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
            Truth.assertThat(TestApis.devicePolicy().getPasswordExpirationTimeout())
                .isNotEqualTo(TIMEOUT)
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getCurrentFailedPasswordAttempts"])
    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(
        policy = [FailedPasswordAttempts::class],
        includeNonDeviceAdminStates = false
    )
    fun getCurrentFailedPasswordAttempts_notPermitted_throwsException() {
        // TODO: Test effect of PasswordExpirationTimeout
            assertThrows(SecurityException::class.java) {
                deviceState.dpc()
                    .devicePolicyManager().currentFailedPasswordAttempts
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getCurrentFailedPasswordAttempts"])
    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = [FailedPasswordAttempts::class])
    fun getCurrentFailedPasswordAttempts_permitted_doesNotThrow() {
        deviceState.dpc().devicePolicyManager().currentFailedPasswordAttempts
    }

    // TODO: Create an interactive test to test functionality of getCurrentFailedPasswordAttempts
    @CannotSetPolicyTest(policy = [StrongAuthTimeout::class], includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setRequiredStrongAuthTimeout"])
    fun setRequiredStrongAuthTimeout_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .setRequiredStrongAuthTimeout(deviceState.dpc().componentName(), TIMEOUT)
        }
    }

    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = [StrongAuthTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setRequiredStrongAuthTimeout", "android.app.admin.DevicePolicyManager#getRequiredStrongAuthTimeout"])
    fun setRequiredStrongAuthTimeout_strongAuthTimeoutIsSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getPasswordExpirationTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
            assertThat(TestApis.devicePolicy().getPasswordExpirationTimeout()).isEqualTo(
                TIMEOUT
            )
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @PolicyDoesNotApplyTest(policy = [StrongAuthTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setRequiredStrongAuthTimeout", "android.app.admin.DevicePolicyManager#getRequiredStrongAuthTimeout"])
    fun setRequiredStrongAuthTimeout_doesNotApply_strongAuthTimeoutIsNotSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getRequiredStrongAuthTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setRequiredStrongAuthTimeout(deviceState.dpc().componentName(), TIMEOUT)
            Truth.assertThat(TestApis.devicePolicy().getRequiredStrongAuthTimeout()
            ).isNotEqualTo(TIMEOUT)
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setRequiredStrongAuthTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @RequireTargetSdkVersion(max = Build.VERSION_CODES.N)
    @PolicyAppliesTest(policy = [DeprecatedResetPassword::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#resetPassword"])
    fun resetPassword_targetBeforeN_returnsFalse() {
        Truth.assertThat(
            deviceState.dpc()
                .devicePolicyManager().resetPassword(Defaults.DEFAULT_PASSWORD,  /* flags= */0)
        ).isFalse()
    }

    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    @RequireDoesNotHaveFeature(
        PackageManager.FEATURE_AUTOMOTIVE
    )
    @RequireTargetSdkVersion(min = Build.VERSION_CODES.O)
    @PolicyAppliesTest(policy = [DeprecatedResetPassword::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#resetPassword"])
    fun resetPassword_targetAfterO_throwsSecurityException() {
        assertThrows(
            SecurityException::class.java
        ) {
            deviceState.dpc().devicePolicyManager()
                .resetPassword(Defaults.DEFAULT_PASSWORD,  /* flags= */0)
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isActivePasswordSufficient"])
    @CanSetPolicyTest(policy = [PasswordSufficiency::class])
    @EnsureUnlocked
    @EnsurePasswordSet(password = PASSWORD_MEDIUM_COMPLEXITY)
    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    fun getIsActivePasswordSufficient_passwordDoesNotMeetRequirement_returnsFalse() {
            try {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH
                Truth.assertThat(
                    deviceState.dpc().devicePolicyManager().isActivePasswordSufficient
                )
                    .isFalse()
            } finally {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_NONE
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isActivePasswordSufficient"])
    @CanSetPolicyTest(policy = [PasswordSufficiency::class])
    @EnsureUnlocked
    @EnsurePasswordSet(password = PASSWORD_MEDIUM_COMPLEXITY)
    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    fun getIsActivePasswordSufficient_passwordMeetsRequirement_returnsTrue() {
            try {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM
                Truth.assertThat(
                    deviceState.dpc().devicePolicyManager().isActivePasswordSufficient
                )
                    .isTrue()
            } finally {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_NONE
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isActivePasswordSufficient"])
    @CanSetPolicyTest(policy = [PasswordSufficiency::class])
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    fun getIsActivePasswordSufficient_passwordNotSet_returnsTrue() {
        assertThat(deviceState.dpc().devicePolicyManager()
            .isActivePasswordSufficient).isTrue()
    }

    companion object {
        private const val TIMEOUT: Long = 51234
        private const val PASSWORD_MEDIUM_COMPLEXITY = "abc12"

        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
