package android.companion.cts.core

import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.PrimaryCompanionService
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test CDM APIs for system communication.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:SystemCommunicationTest
 *
 * @see android.companion.CompanionDeviceManager.dispatchMessage
 * @see android.companion.CompanionDeviceService.onMessageDispatchedFromSystem
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class SystemCommunicationTest : CoreTestBase() {

    @Test
    fun test_CompanionDeviceService_onMessageDispatchedFromSystem_invoked() {
        // Register a self-managed companion device.
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        // Report device as connected.
        cdm.notifyDeviceAppeared(associationId)

        // Wait for CDM to bind the primary CompanionDeviceService
        PrimaryCompanionService.waitForBind()
        // Wait for CDM to trigger CompanionDeviceService.onDeviceAppeared()
        PrimaryCompanionService.waitAssociationToAppear(associationId)

        val dispatchSystemMessageRequestTracker =
                PrimaryCompanionService.dispatchSystemMessageRequestTracker
                        ?: error("PrimaryCompanionService.dispatchSystemMessageRequestTracker is " +
                                "absent. (PrimaryCompanionService.instance is null?)")

        // Now let's make CDM to send a secure message to the companion device that we just
        // registered (created an association with).
        // We'll do that using the shell following command:
        // adb shell cmd companiondevice send-secure-message <association-id> <message>"
        val firstMessage = "Hello, fellow CDM!"
        dispatchSystemMessageRequestTracker.assertInvokedByActions {
            runShellCommand("""
            cmd companiondevice send-secure-message $associationId $firstMessage
            """.trimIndent())
        }

        // Make sure we received exactly one request to transfer system message (via
        // CompanionDeviceService.onMessageDispatchedFromSystem())...
        assertEquals(actual = dispatchSystemMessageRequestTracker.invocations.size, expected = 1)
        dispatchSystemMessageRequestTracker.invocations.first().let {
            // ... and it was "addressed" to the right companion device (association), ...
            assertEquals(actual = it.associationId, expected = associationId)
            // ... and it contains some data.
            assertNotNull(it.message)

            // TODO(b/202926196): when the encryption is implemented, we'll no longer be able to
            //  see "inside" the message, so the following check will have to be changed to
            //  assert_Not_Equals (or removed altogether)
            assertEquals(actual = it.utf8StringMessage, expected = firstMessage)
        }

        // Let's send one more message...
        dispatchSystemMessageRequestTracker.clearRecordedInvocations()

        val secondMessage = "ping-ping"
        dispatchSystemMessageRequestTracker.assertInvokedByActions {
            runShellCommand("""
            cmd companiondevice send-secure-message $associationId $secondMessage
            """.trimIndent())
        }
        // ... and do the same checks
        assertEquals(1, dispatchSystemMessageRequestTracker.invocations.size)
        dispatchSystemMessageRequestTracker.invocations.first().let() {
            assertEquals(actual = it.associationId, expected = associationId)
            assertNotNull(it.message)

            // TODO(b/202926196): when the encryption is implemented, we'll no longer be able to
            //  see "inside" the message, so the following check will have to be changed to
            //  assert_Not_Equals (or removed altogether)
            assertEquals(actual = it.utf8StringMessage, expected = secondMessage)
        }

        // NOTE:
        // while our CTS test finishes here, a regular companion application at this stage would be
        // expected to transfer these bytes to another device (usually over BT, but any other means
        // work as well - CDM really does not care), where a companion application (could be the
        // same application) receives the data, and passes it back to CDM using:
        // CompanionDeviceManager.dispatchMessage(int messageId, int associationId, byte[] message)
        // or
        // CompanionDeviceService.dispatchMessageToSystem(int messageId, int associationId, byte[] message)
        // (which really is the same thing).

        // Report device as disconnected.
        cdm.notifyDeviceDisappeared(associationId)

        // Wait for CDM to trigger CompanionDeviceService.onDeviceDisappeared()
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        // Wait for CDM to unbind the primary CompanionDeviceService
        PrimaryCompanionService.waitForUnbind()
    }
}