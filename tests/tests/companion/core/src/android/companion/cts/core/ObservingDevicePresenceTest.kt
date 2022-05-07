package android.companion.cts.core

import android.Manifest
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MissingIntentFilterActionCompanionService
import android.companion.cts.common.MissingPermissionCompanionService
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.SecondaryCompanionService
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.sleepFor
import android.companion.cts.common.toUpperCaseString
import android.companion.cts.common.waitFor
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
 * Test CDM APIs for observing device presence.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:ObservingDevicePresenceTest
 *
 * @see android.companion.CompanionDeviceManager.startObservingDevicePresence
 * @see android.companion.CompanionDeviceManager.stopObservingDevicePresence
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class ObservingDevicePresenceTest : CoreTestBase() {

    @Test
    fun test_observingDevicePresence_isOffByDefault() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        simulateDeviceAppeared(associationId)

        // Make sure CDM does not bind CompanionDeviceServices
        assertFalse("CompanionDeviceServices should not be bound when observing device presence " +
                "is off") {
            waitFor(timeout = 3.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound || SecondaryCompanionService.isBound
            }
        }
        // ... and does not trigger onDeviceAppeared ()
        assertEmpty(PrimaryCompanionService.connectedDevices)
        assertEmpty(SecondaryCompanionService.connectedDevices)

        simulateDeviceDisappeared(associationId)
    }

    @Test
    fun test_startObservingDevicePresence_requiresPermission() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)

        // Attempts to call startObservingDevicePresence without the
        // REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE  permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        // Same call with the REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }

    @Test
    fun test_startObservingDevicePresence_singleDevice() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        // Simulate device appeared.
        simulateDeviceAppeared(associationId)

        // Make sure CDM binds valid CompanionDeviceServices...
        assertTrue("Both valid CompanionDeviceServices - Primary and Secondary - should be bound " +
                "now") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound && SecondaryCompanionService.isBound
            }
        }
        // ... and does not bind incorrectly defined CompanionDeviceServices.
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
        // ... while the non-primary and incorrectly defined CompanionDeviceServices - have NOT.
        // (Give it 1 more second.)
        sleepFor(1.seconds)
        assertEmpty(SecondaryCompanionService.connectedDevices)
        assertEmpty(MissingPermissionCompanionService.connectedDevices)
        assertEmpty(MissingIntentFilterActionCompanionService.connectedDevices)

        assertFalse("Both valid CompanionDeviceServices - Primary and Secondary - should stay " +
                "bound ") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                !PrimaryCompanionService.isBound || !SecondaryCompanionService.isBound
            }
        }

        simulateDeviceDisappeared(associationId)

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
    fun test_stopObservingDevicePresence() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        // Start and stop observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        // Simulate device disappeared.
        simulateDeviceAppeared(associationId)

        // Make sure CDM does not bind CompanionDeviceServices
        assertFalse("CompanionDeviceServices should not be bound when observing device presence " +
                "is off") {
            waitFor(timeout = 3.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound || SecondaryCompanionService.isBound
            }
        }
        // ... and does not trigger onDeviceAppeared ()
        assertEmpty(PrimaryCompanionService.connectedDevices)
        assertEmpty(SecondaryCompanionService.connectedDevices)

        // Simulate device disappeared.
        simulateDeviceDisappeared(associationId)
    }

    private fun simulateDeviceAppeared(associationId: Int) = runShellCommand(
            "cmd companiondevice simulate-device-appeared $associationId")

    private fun simulateDeviceDisappeared(associationId: Int) = runShellCommand(
            "cmd companiondevice simulate-device-disappeared $associationId")
}