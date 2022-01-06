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

package android.companion.cts.core

import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.AssociationRequest
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.SecondaryCompanionService
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.waitFor
import android.os.SystemClock.sleep
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests CDM APIs for notifying the presence of status of the companion devices for self-managed
 * associations.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerCoreTestCases:SelfPresenceReportingTest
 *
 * @see android.companion.CompanionDeviceManager.notifyDeviceAppeared
 * @see android.companion.CompanionDeviceManager.notifyDeviceDisappeared
 * @see android.companion.CompanionDeviceService.onDeviceAppeared
 * @see android.companion.CompanionDeviceService.onDeviceDisappeared
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class SelfPresenceReportingTest : CoreTestBase() {

    @Test
    fun test_primaryService_isBound() =
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        cdm.notifyDeviceAppeared(associationId)

        assertTrue("Both Services - Primary and Secondary - should be bound now") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound && SecondaryCompanionService.isBound
            }
        }

        // Check that only the primary services has received the onDeviceAppeared() callback...
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        assertContentEquals(
                actual = PrimaryCompanionService.associationIdsForConnectedDevices,
                expected = setOf(associationId)
        )
        // ... while the non-primary service - has NOT. (Give it 1 more second.)
        sleep(1000)
        assertEmpty(SecondaryCompanionService.connectedDevices)

        assertFalse("Both Services - Primary and Secondary - should stay bound") {
            waitFor(timeout = 3.seconds, interval = 1.seconds) {
                !PrimaryCompanionService.isBound || !SecondaryCompanionService.isBound
            }
        }

        cdm.notifyDeviceDisappeared(associationId)

        // Check that only the primary services has received the onDeviceDisappeared() callback.
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        assertEmpty(PrimaryCompanionService.connectedDevices)

        assertTrue("Both Services - Primary and Secondary - should be unbound now") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                !PrimaryCompanionService.isBound && !SecondaryCompanionService.isBound
            }
        }
    }

    @Test
    @Ignore("b/211398735")
    fun test_multipleDevices_sameApplication() {
        val idA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        val idB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B)

        cdm.notifyDeviceAppeared(idA)

        assertTrue("Both Services - Primary and Secondary - should be bound now") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound && SecondaryCompanionService.isBound
            }
        }

        // Check that only the primary services has received the onDeviceAppeared() callback...
        PrimaryCompanionService.waitAssociationToAppear(idA)
        assertContentEquals(
            actual = PrimaryCompanionService.associationIdsForConnectedDevices,
            expected = setOf(idA)
        )
        // ... while the non-primary service - has NOT. (Give it 1 more second.)
        sleep(1000)
        assertEmpty(SecondaryCompanionService.connectedDevices)

        cdm.notifyDeviceAppeared(idB)

        // Check that only the primary services has received the onDeviceAppeared() callback...
        PrimaryCompanionService.waitAssociationToAppear(idB)
        assertContentEquals(
            actual = PrimaryCompanionService.associationIdsForConnectedDevices,
            expected = setOf(idA, idB)
        )

        // Make sure both services stay bound.
        assertFalse("Both Services - Primary and Secondary - should stay bound") {
            waitFor(timeout = 3.seconds, interval = 1.seconds) {
                !PrimaryCompanionService.isBound || !SecondaryCompanionService.isBound
            }
        }

        // "Disconnect" first device (A).
        cdm.notifyDeviceDisappeared(idA)

        PrimaryCompanionService.waitAssociationToDisappear(idA)
        // Both services should stay bound for as long as there is at least
        // one connected device (B).
        assertFalse("Both Services - Primary and Secondary - should stay bound") {
            waitFor(timeout = 3.seconds, interval = 1.seconds) {
                !PrimaryCompanionService.isBound || !SecondaryCompanionService.isBound
            }
        }

        // "Disconnect" second device (B).
        cdm.notifyDeviceDisappeared(idB)

        PrimaryCompanionService.waitAssociationToDisappear(idB)
        assertTrue("Both Services - Primary and Secondary - should be unbound now") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                !PrimaryCompanionService.isBound && !SecondaryCompanionService.isBound
            }
        }
    }

    private fun createSelfManagedAssociation(displayName: String): Int {
        val callback = RecordingCallback()
        val request: AssociationRequest = AssociationRequest.Builder()
            .setSelfManaged(true)
            .setDisplayName(displayName)
            .build()
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
        callback.waitForInvocation()
        return callback.invocations[0].associationInfo.id
    }
}