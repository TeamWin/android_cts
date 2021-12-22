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

package android.companion.cts.uiautomation

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
import android.companion.BluetoothDeviceFilterUtils
import android.companion.CompanionDeviceManager
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationCreated
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnFailure
import android.companion.cts.common.assertEmpty
import android.content.Intent
import android.net.MacAddress
import android.os.Parcelable
import android.platform.test.annotations.AppModeFull
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests the Association Flow end-to-end.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerUiAutomationTestCases:AssociationEndToEndTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(Parameterized::class)
class AssociationEndToEndTest(
    profile: String?,
    profilePermission: String?,
    profileName: String // Used only by the Parameterized test runner for tagging.
) : UiAutomationTestBase(profile, profilePermission) {

    override fun setUp() {
        super.setUp()

        // TODO(b/211590680): Add support for APP_STREAMING and AUTOMOTIVE_PROJECTION in the
        // confirmation UI (the "multiple devices" flow variant).
        assumeFalse(profile == DEVICE_PROFILE_APP_STREAMING)
        assumeFalse(profile == DEVICE_PROFILE_AUTOMOTIVE_PROJECTION)
    }

    @Test
    fun test_userRejected() = super.test_userRejected(selfManaged = false, displayName = null)

    @Test
    fun test_userDismissed() = super.test_userDismissed(selfManaged = false, displayName = null)

    @Test
    fun test_userConfirmed() {
        sendRequestAndLaunchConfirmation()

        // Wait until at least one device is found and click on it.
        confirmationUi.waitAndClickOnFirstFoundDevice()

        callback.waitForInvocation()
        // Check callback invocations: there should have been exactly 1 invocation of the
        // OnAssociationCreated() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnAssociationCreated)
            assertNotNull(it[0].associationInfo)
        }
        val associationFromCallback = callback.invocations[0].associationInfo

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code and the data delivered via onActivityResult()
        val (resultCode: Int, data: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_OK)
        assertNotNull(data)
        val associationFromActivityResult: AssociationInfo? =
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION)
        assertNotNull(associationFromActivityResult)
        // Check that the association reported back via the callback same as the association
        // delivered via onActivityResult().
        assertEquals(associationFromCallback, associationFromActivityResult)

        // Make sure "device data" was included (for backwards compatibility), and that the
        // MAC address extracted from this data matches the MAC address from AssociationInfo.
        val deviceFromActivityResult: Parcelable? =
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        assertNotNull(deviceFromActivityResult)

        val deviceMacAddress =
                BluetoothDeviceFilterUtils.getDeviceMacAddress(deviceFromActivityResult)
        assertEquals(actual = MacAddress.fromString(deviceMacAddress),
                expected = associationFromCallback.deviceMacAddress)

        // Make sure getMyAssociations() returns the same association we received via the callback
        // as well as in onActivityResult()
        assertContentEquals(actual = cdm.myAssociations, expected = listOf(associationFromCallback))
    }

    @Test
    fun test_timeout() {
        // Set discovery timeout to 1 sec.
        setDiscoveryTimeout(1_000)
        // Make sure no device will match the request
        sendRequestAndLaunchConfirmation(deviceFilter = UNMATCHABLE_BT_FILTER)

        // The discovery timeout is 1 sec, but let's give it 2.
        callback.waitForInvocation(2_000)

        // Check callback invocations: there should have been exactly 1 invocation of the
        // onFailure() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnFailure)
            assertEquals(actual = it[0].error, expected = "Timeout.")
        }

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code delivered via onActivityResult()
        val (resultCode: Int, _) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_CANCELED)

        // Make sure no Associations were created.
        assertEmpty(cdm.myAssociations)
    }

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         * Each test will be suffixed with "[profile=<NAME>]", e.g.: "[profile=WATCH]".
         */
        @Parameterized.Parameters(name = "profile={2}")
        @JvmStatic
        fun parameters() = supportedProfilesAndNull()
    }
}