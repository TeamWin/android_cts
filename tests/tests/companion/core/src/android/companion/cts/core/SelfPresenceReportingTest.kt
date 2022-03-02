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
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MissingIntentFilterActionCompanionService
import android.companion.cts.common.MissingPermissionCompanionService
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.SecondaryCompanionService
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.waitFor
import android.os.SystemClock.sleep
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
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
    fun test_selfReporting_singleDevice_multipleServices() =
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        cdm.notifyDeviceAppeared(associationId)

        assertTrue("Both valid CompanionDeviceServices - Primary and Secondary - should be bound " +
                "now") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound && SecondaryCompanionService.isBound
            }
        }
        assertFalse("CompanionDeviceServices that do not require " +
                "BIND_COMPANION_DEVICE_SERVICE permission or do not declare an intent-filter for " +
                "\"android.companion.CompanionDeviceService\" action should not be bound") {
            MissingPermissionCompanionService.isBound ||
                    MissingIntentFilterActionCompanionService.isBound
        }

        // Check that only the primary CompanionDeviceService has received the onDeviceAppeared()
        // callback...
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        assertContentEquals(
                actual = PrimaryCompanionService.associationIdsForConnectedDevices,
                expected = setOf(associationId)
        )
        // ... while neither the non-primary nor incorrectly defined CompanionDeviceServices -
        // have NOT. (Give it 1 more second.)
        sleep(1000)
        assertEmpty(SecondaryCompanionService.connectedDevices)
        assertEmpty(MissingPermissionCompanionService.connectedDevices)
        assertEmpty(MissingIntentFilterActionCompanionService.connectedDevices)

        assertFalse("Both valid CompanionDeviceServices - Primary and Secondary - should stay " +
                "bound ") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
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
    fun test_selfReporting_multipleDevices_multipleServices() {
        val idA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        val idB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B)

        cdm.notifyDeviceAppeared(idA)

        assertTrue("Both valid CompanionDeviceServices - Primary and Secondary - should be bound " +
                "now") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound && SecondaryCompanionService.isBound
            }
        }
        assertFalse("CompanionDeviceServices that do not require " +
                "BIND_COMPANION_DEVICE_SERVICE permission or do not declare an intent-filter for " +
                "\"android.companion.CompanionDeviceService\" action should not be bound") {
            MissingPermissionCompanionService.isBound ||
                    MissingIntentFilterActionCompanionService.isBound
        }

        // Check that only the primary services has received the onDeviceAppeared() callback...
        PrimaryCompanionService.waitAssociationToAppear(idA)
        assertContentEquals(
            actual = PrimaryCompanionService.associationIdsForConnectedDevices,
            expected = setOf(idA)
        )
        // ... while neither the non-primary nor incorrectly defined CompanionDeviceServices -
        // have NOT. (Give it 1 more second.)
        sleep(1000)
        assertEmpty(SecondaryCompanionService.connectedDevices)
        assertEmpty(MissingPermissionCompanionService.connectedDevices)
        assertEmpty(MissingIntentFilterActionCompanionService.connectedDevices)

        cdm.notifyDeviceAppeared(idB)

        // Check that only the primary services has received the onDeviceAppeared() callback.
        PrimaryCompanionService.waitAssociationToAppear(idB)
        assertContentEquals(
            actual = PrimaryCompanionService.associationIdsForConnectedDevices,
            expected = setOf(idA, idB)
        )

        // Make sure both valid services stay bound.
        assertFalse("Both valid CompanionDeviceServices - Primary and Secondary - should stay " +
                "bound ") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                !PrimaryCompanionService.isBound || !SecondaryCompanionService.isBound
            }
        }

        // "Disconnect" first device (A).
        cdm.notifyDeviceDisappeared(idA)

        PrimaryCompanionService.waitAssociationToDisappear(idA)
        // Both valid services should stay bound for as long as there is at least one connected
        // device - device B in this case.
        assertFalse("Both valid CompanionDeviceServices - Primary and Secondary - should stay " +
                "bound ") {
            waitFor(timeout = 3.seconds, interval = 1.milliseconds) {
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

    @Test
    fun test_notifyAppearAndDisappear_invalidId() {
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceAppeared(-1) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceAppeared(0) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceAppeared(1) }

        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceDisappeared(-1) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceDisappeared(0) }
        assertFailsWith(IllegalArgumentException::class) { cdm.notifyDeviceDisappeared(1) }
    }

    @Test
    fun test_notifyAppears_requires_selfManagedAssociation() {
        // Create NOT "self-managed" association
        targetApp.associate(MAC_ADDRESS_A)

        val id = cdm.myAssociations[0].id

        // notifyDeviceAppeared can only be called for self-managed associations.
        assertFailsWith(IllegalArgumentException::class) {
            cdm.notifyDeviceAppeared(id)
        }
    }
}