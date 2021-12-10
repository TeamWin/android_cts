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

package android.companion.cts

import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.AssociationRequest
import android.os.SystemClock.sleep
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests CDM APIs for notifying the presence of status of the companion devices for self-managed
 * associations.
 *
 * Build/Install/Run: atest CtsCompanionDevicesTestCases:SelfPresenceReportingTest
 *
 * @see android.companion.CompanionDeviceManager.notifyDeviceAppeared
 * @see android.companion.CompanionDeviceManager.notifyDeviceDisappeared
 * @see android.companion.CompanionDeviceService.onDeviceAppeared
 * @see android.companion.CompanionDeviceService.onDeviceDisappeared
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class SelfPresenceReportingTest : TestBase() {

    @Test
    fun test_primaryService_isBound() =
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
        val request: AssociationRequest = AssociationRequest.Builder()
                .setSelfManaged(true)
                .setDisplayName(DEVICE_DISPLAY_NAME)
                .build()
        val callback = RecordingCallback()

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
        callback.waitForInvocation()
        val associationId = cdm.myAssociations[0].id

        cdm.notifyDeviceAppeared(associationId)

        assertTrue("Both Services - Primary and Secondary - should be unbound now") {
            waitFor(timeout = 1000, interval = 100) {
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
            waitFor(timeout = 3000, interval = 1000) {
                !PrimaryCompanionService.isBound || !SecondaryCompanionService.isBound
            }
        }

        cdm.notifyDeviceDisappeared(associationId)

        // Check that only the primary services has received the onDeviceDisappeared() callback.
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        assertEmpty(PrimaryCompanionService.connectedDevices)

        assertTrue("Both Services - Primary and Secondary - should be unbound now") {
            waitFor(timeout = 1000, interval = 100) {
                !PrimaryCompanionService.isBound && !SecondaryCompanionService.isBound
            }
        }
    }

    companion object {
        private const val DEVICE_DISPLAY_NAME = "My device"
    }
}