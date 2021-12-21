package android.companion.cts.common

import android.Manifest
import android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.net.MacAddress
import java.util.concurrent.Executor

/** Set of all supported CDM Device Profiles. */
val DEVICE_PROFILES = setOf(
        DEVICE_PROFILE_WATCH,
        DEVICE_PROFILE_APP_STREAMING,
        DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
)

val DEVICE_PROFILE_TO_NAME = mapOf(
        DEVICE_PROFILE_WATCH to "WATCH",
        DEVICE_PROFILE_APP_STREAMING to "APP_STREAMING",
        DEVICE_PROFILE_AUTOMOTIVE_PROJECTION to "AUTOMOTIVE_PROJECTION"
)

val DEVICE_PROFILE_TO_PERMISSION = mapOf(
        DEVICE_PROFILE_WATCH to Manifest.permission.REQUEST_COMPANION_PROFILE_WATCH,
        DEVICE_PROFILE_APP_STREAMING to
                Manifest.permission.REQUEST_COMPANION_PROFILE_APP_STREAMING,
        DEVICE_PROFILE_AUTOMOTIVE_PROJECTION to
                Manifest.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION
)

val MAC_ADDRESS_A = MacAddress.fromString("00:00:00:00:00:AA")
val MAC_ADDRESS_B = MacAddress.fromString("00:00:00:00:00:BB")
val MAC_ADDRESS_C = MacAddress.fromString("00:00:00:00:00:CC")

const val DEVICE_DISPLAY_NAME_A = "Device A"
const val DEVICE_DISPLAY_NAME_B = "Device B"

val SIMPLE_EXECUTOR: Executor = Executor { it.run() }