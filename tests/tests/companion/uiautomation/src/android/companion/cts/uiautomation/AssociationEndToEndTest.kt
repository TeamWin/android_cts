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
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationCreated
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationPending
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnFailure
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.TestBase
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.setSystemProp
import android.content.Intent
import android.net.MacAddress
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests the Association Flow end-to-end.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerUiAutomationTestCases:AssociationEndToEndTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociationEndToEndTest : TestBase() {
    private val uiDevice by lazy { UiDevice.getInstance(instrumentation) }
    private val confirmationUi by lazy { CompanionDeviceManagerUi(uiDevice) }
    private val callback by lazy { RecordingCallback() }

    override fun setUp() {
        super.setUp()

        assumeFalse(confirmationUi.isVisible)
        assumeTrue(CompanionActivity.waitUntilGone())
        uiDevice.waitForIdle()

        callback.clearRecordedInvocations()
    }

    override fun tearDown() {
        super.tearDown()

        CompanionActivity.safeFinish()
        confirmationUi.dismiss()

        restoreDiscoveryTimeout()
    }

    @Test
    fun test_userRejected() = test_cancelled {
        // User "rejects" the request.
        confirmationUi.clickNegativeButton()
    }

    @Test
    fun test_userDismissed() = test_cancelled {
        // User "dismisses" the request.
        uiDevice.pressBack()
    }

    private fun test_cancelled(action: () -> Unit) {
        sendRequestAndLaunchConfirmation()

        action()

        callback.waitForInvocation()
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onFailure() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnFailure)
            assertEquals(actual = it[0].error, expected = "Cancelled.")
        }

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code delivered via onActivityResult()
        val (resultCode: Int, _) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_CANCELED)

        // Make sure no Associations were created.
        assertEmpty(cdm.myAssociations)
    }

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

        // Make sure the MAC address of the device was included in onActivityResult()'s data (for
        // backwards compatibility), and it matches the MAC address from AssociationInfo.
        val macAddressFromActivityResult = data.getStringExtra(CompanionDeviceManager.EXTRA_DEVICE)
        assertNotNull(macAddressFromActivityResult)
        assertEquals(associationFromCallback.deviceMacAddress,
                MacAddress.fromString(macAddressFromActivityResult))

        // Make sure getMyAssociations() returns the same association we received via the callback
        // as well as in onActivityResult()
        assertContentEquals(actual = cdm.myAssociations, expected = listOf(associationFromCallback))
    }

    @Test
    fun test_timeout() {
        // Set discovery timeout to 1 sec.
        setDiscoveryTimeout(1_000)
        // Make sure no device will match the request
        sendRequestAndLaunchConfirmation {
            addDeviceFilter(UNMATCHABLE_BT_FILTER)
        }

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

    private fun sendRequestAndLaunchConfirmation(
        block: (AssociationRequest.Builder.() -> Unit)? = null
    ) {
        val request = AssociationRequest.Builder()
                .apply { block?.invoke(this) }
                .build()
        callback.clearRecordedInvocations()

        cdm.associate(request, SIMPLE_EXECUTOR, callback)
        callback.waitForInvocation()
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onAssociationPending() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnAssociationPending)
            assertNotNull(it[0].intentSender)
        }

        // Get intent sender and clear callback invocations.
        val pendingConfirmation = callback.invocations[0].intentSender
        callback.clearRecordedInvocations()

        // Launch CompanionActivity, and then launch confirmation UI from it.
        CompanionActivity.launchAndWait(context)
        CompanionActivity.startIntentSender(pendingConfirmation)

        confirmationUi.waitUntilVisible()
    }

    private fun setDiscoveryTimeout(timeout: Int) =
            instrumentation.setSystemProp(SYS_PROP_DEBUG_TIMEOUT, timeout.toString())

    private fun restoreDiscoveryTimeout() = setDiscoveryTimeout(0)

    companion object {
        private const val SYS_PROP_DEBUG_TIMEOUT = "debug.cdm.discovery_timeout"

        private val UNMATCHABLE_BT_FILTER = BluetoothDeviceFilter.Builder()
                .setAddress("FF:FF:FF:FF:FF:FF")
                .setNamePattern(Pattern.compile("This Device Does Not Exist"))
                .build()
    }
}