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

package android.companion.cts.uiautomation

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.cts.common.CompanionActivity
import android.content.Intent
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests the system data transfer.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerUiAutomationTestCases:SystemDataTransferTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class SystemDataTransferTest : UiAutomationTestBase(null, null) {

    @Test
    fun test_userConsentDialogAllowed() {
        val association1 = associate()

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        confirmationUi.clickPositiveButton()
        val (resultCode: Int, _: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(expected = RESULT_OK, actual = resultCode)

        // Second time request permission transfer should get null IntentSender
        val pendingUserConsent2 = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNull(pendingUserConsent2)

        // disassociate() should clean up the requests
        cdm.disassociate(association1.id)
        Thread.sleep(2_100)
        val association2 = associate()
        val pendingUserConsent3 = cdm.buildPermissionTransferUserConsentIntent(association2.id)
        assertNotNull(pendingUserConsent3)
    }

    @Test
    fun test_userConsentDialogDisallowed() {
        val association1 = associate()

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        confirmationUi.clickNegativeButton()
        val (resultCode: Int, _: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(expected = RESULT_CANCELED, actual = resultCode)

        // Second time request permission transfer should get null IntentSender
        val pendingUserConsent2 = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNull(pendingUserConsent2)

        // disassociate() should clean up the requests
        cdm.disassociate(association1.id)
        Thread.sleep(2_100)
        val association2 = associate()
        val pendingUserConsent3 = cdm.buildPermissionTransferUserConsentIntent(association2.id)
        assertNotNull(pendingUserConsent3)
    }

    @Test
    fun test_userConsentDialogCanceled() {
        val association1 = associate()

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        uiDevice.pressBack()

        // Second time request permission transfer should prompt a dialog
        val pendingUserConsent2 = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent2)
    }

    /**
     * Associate without checking the association data.
     */
    private fun associate(): AssociationInfo {
        sendRequestAndLaunchConfirmation()
        callback.assertInvokedByActions {
            confirmationUi.waitAndClickOnFirstFoundDevice()
        }
        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()
        // Check the result code and the data delivered via onActivityResult()
        val (_: Int, associationData: Intent?) = CompanionActivity.waitForActivityResult()
        CompanionActivity.clearResult()
        assertNotNull(associationData)
        val association: AssociationInfo? = associationData.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java)
        assertNotNull(association)

        return association
    }
}