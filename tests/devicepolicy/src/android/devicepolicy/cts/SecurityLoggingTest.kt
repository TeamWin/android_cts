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

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner
import com.android.bedstead.harrier.policies.GlobalSecurityLogging
import com.android.bedstead.harrier.policies.SecurityLogging
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class SecurityLoggingTest {
    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled"])
    fun setSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled", "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"])
    fun setSecurityLoggingEnabled_true_securityLoggingIsEnabled() {
        val originalSecurityLoggingEnabled = sDeviceState.dpc()
            .devicePolicyManager().isSecurityLoggingEnabled(sDeviceState.dpc().componentName())
        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
            Truth.assertThat(
                sDeviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName()
                )
            ).isTrue()
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled", "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"])
    fun setSecurityLoggingEnabled_false_securityLoggingIsNotEnabled() {
        val originalSecurityLoggingEnabled = sDeviceState.dpc()
            .devicePolicyManager().isSecurityLoggingEnabled(sDeviceState.dpc().componentName())
        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
            Truth.assertThat(
                sDeviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    sDeviceState.dpc().componentName()
                )
            ).isFalse()
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled
            )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"])
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    fun isSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            sDeviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                sDeviceState.dpc().componentName()
            )
        }
    }

    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_notPermitted_throwsException() {
        ensureNoAdditionalFullUsers()
        assertThrows(SecurityException::class.java) {
            sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onSecurityLogsAvailable"])
    fun retrieveSecurityLogs_onSecurityLogsAvailableCallbackIsCalled() {
        ensureNoAdditionalFullUsers()

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )

            // TODO: Generate some security logs then assert on the callback
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_returnsSecurityLogs() {
        ensureNoAdditionalFullUsers()
        val originalSecurityLoggingEnabled = sDeviceState.dpc().devicePolicyManager()
            .isSecurityLoggingEnabled(sDeviceState.dpc().componentName())
        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )

            // TODO: Generate some security logs and assert on them
            sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled
            )
        }
    }

    // TODO: Add test that logs are filtered for non-device-owner
    // TODO: Add test for rate limiting

    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_notPermitted_throwsException() {
        ensureNoAdditionalFullUsers()

        assertThrows(SecurityException::class.java) {
            sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_doesNotThrow() {
        ensureNoAdditionalFullUsers()
        val originalSecurityLoggingEnabled = sDeviceState.dpc().devicePolicyManager()
            .isSecurityLoggingEnabled(sDeviceState.dpc().componentName())
        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )

            // Nothing to assert as this can be null on some devices
            sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), originalSecurityLoggingEnabled
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class], singleTestOnly = true)
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
            assertThrows(SecurityException::class.java) {
                sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                    sDeviceState.dpc().componentName()
                )
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = UserType.ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented()
                        && u != TestApis.users().system()
                        && u != sDeviceState.additionalUser()
                        && u != TestApis.users().current()
                        && !u.isMain)
            }
            .forEach { obj: UserReference -> obj.remove() }
        val affiliationIds: MutableSet<String> = HashSet(
            sDeviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(sDeviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        sDeviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            sDeviceState.dpc().componentName(),
            affiliationIds
        )

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
            sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_noAdditionalUser_doesNotThrowException() {
        ensureNoAdditionalFullUsers()

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
            sDeviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class])
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
            assertThrows(SecurityException::class.java) {
                sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                    sDeviceState.dpc().componentName()
                )
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = UserType.ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented()
                        && u != TestApis.users().system()
                        && u != sDeviceState.additionalUser()
                        && u != TestApis.users().current())
            }
            .forEach { obj: UserReference -> obj.remove() }
        val affiliationIds: MutableSet<String> = HashSet(
            sDeviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(sDeviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        sDeviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            sDeviceState.dpc().componentName(),
            affiliationIds
        )

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
            sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_noAdditionalUser_doesNotThrowException() {
        ensureNoAdditionalFullUsers()

        try {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), true
            )
            sDeviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                sDeviceState.dpc().componentName()
            )
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                sDeviceState.dpc().componentName(), false
            )
        }
    }

    private fun ensureNoAdditionalFullUsers() {
        // TODO(273474964): Move into infra
        try {
            TestApis.users().all().stream()
                .filter { u: UserReference ->
                    (u != TestApis.users().instrumented()
                            && u != TestApis.users().system()
                            && u != TestApis.users()
                        .current() // We can't remove the profile of the instrumented user for the
                            // run on parent profile tests. But the profiles of other users
                            // will be removed when the full-user is removed anyway.
                            && !u.isProfile)
                }
                .forEach { obj: UserReference -> obj.remove() }
        } catch (e: NeneException) {
            // Happens when we can't remove a user
            throw NeneException(
                "Error when removing user. Instrumented user is "
                        + TestApis.users().instrumented() + ", current user is "
                        + TestApis.users().current() + ", system user is "
                        + TestApis.users().system(), e
            )
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState = DeviceState()
    }
}
