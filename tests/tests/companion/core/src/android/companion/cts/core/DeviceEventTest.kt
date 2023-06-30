package android.companion.cts.core

import android.Manifest
import android.companion.CompanionDeviceService.DEVICE_EVENT_BLE_APPEARED
import android.companion.CompanionDeviceService.DEVICE_EVENT_BLE_DISAPPEARED
import android.companion.CompanionDeviceService.DEVICE_EVENT_BT_CONNECTED
import android.companion.CompanionDeviceService.DEVICE_EVENT_BT_DISCONNECTED
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.toUpperCaseString
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for device events.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DeviceEventTest
 *
 * @see android.companion.CompanionDeviceService.onDeviceEvent
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class DeviceEventTest : CoreTestBase() {
    @Test
    fun test_ble_device_event() {
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        simulateDeviceEvent(associationId, DEVICE_EVENT_BLE_APPEARED)
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        assertEquals(
                expected = DEVICE_EVENT_BLE_APPEARED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        simulateDeviceEvent(associationId, DEVICE_EVENT_BLE_DISAPPEARED)
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        assertEquals(
                expected = DEVICE_EVENT_BLE_DISAPPEARED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        PrimaryCompanionService.forgetDevicePresence(associationId)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }

    @Test
    fun test_classic_bt_device_event() {
        targetApp.associate(MAC_ADDRESS_A)
        val idA = cdm.myAssociations[0].id

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        simulateDeviceEvent(idA, DEVICE_EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitAssociationToBtConnect(idA)
        assertEquals(
                expected = DEVICE_EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        simulateDeviceEvent(idA, DEVICE_EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitAssociationToBtDisconnect(idA)
        PrimaryCompanionService.waitAssociationToDisappear(idA)
        assertEquals(
                expected = DEVICE_EVENT_BT_DISCONNECTED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        PrimaryCompanionService.forgetDevicePresence(idA)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }

    @Test
    fun test_both_bt_ble_device_event() {
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        simulateDeviceEvent(associationId, DEVICE_EVENT_BLE_APPEARED)
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        assertEquals(
                expected = DEVICE_EVENT_BLE_APPEARED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        simulateDeviceEvent(associationId, DEVICE_EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitAssociationToBtConnect(associationId)
        assertEquals(
                expected = DEVICE_EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        simulateDeviceEvent(associationId, DEVICE_EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitAssociationToBtDisconnect(associationId)
        assertEquals(
                expected = DEVICE_EVENT_BT_DISCONNECTED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        simulateDeviceEvent(associationId, DEVICE_EVENT_BLE_DISAPPEARED)
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        assertEquals(
                expected = DEVICE_EVENT_BLE_DISAPPEARED,
                actual = PrimaryCompanionService.getCurrentState()
        )

        PrimaryCompanionService.forgetDevicePresence(associationId)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }
}
