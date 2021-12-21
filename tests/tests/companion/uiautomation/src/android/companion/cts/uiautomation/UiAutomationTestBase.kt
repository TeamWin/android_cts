package android.companion.cts.uiautomation

import android.Manifest
import android.annotation.CallSuper
import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.DeviceFilter
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.DEVICE_PROFILES
import android.companion.cts.common.DEVICE_PROFILE_TO_NAME
import android.companion.cts.common.DEVICE_PROFILE_TO_PERMISSION
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationPending
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnFailure
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.TestBase
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.setSystemProp
import androidx.test.uiautomator.UiDevice
import org.junit.Assume
import org.junit.Assume.assumeFalse
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

open class UiAutomationTestBase(
    protected val profile: String?,
    private val profilePermission: String?
) : TestBase() {
    private val uiDevice: UiDevice by lazy { UiDevice.getInstance(instrumentation) }
    protected val confirmationUi by lazy { CompanionDeviceManagerUi(uiDevice) }
    protected val callback by lazy { RecordingCallback() }

    @CallSuper
    override fun tearDown() {
        super.tearDown()

        CompanionActivity.safeFinish()
        confirmationUi.dismiss()

        restoreDiscoveryTimeout()
    }

    @CallSuper
    override fun setUp() {
        super.setUp()

        assumeFalse(confirmationUi.isVisible)
        Assume.assumeTrue(CompanionActivity.waitUntilGone())
        uiDevice.waitForIdle()

        callback.clearRecordedInvocations()
    }

    protected fun test_userRejected(selfManaged: Boolean, displayName: String? = null) =
            test_cancelled(selfManaged, displayName) {
                // User "rejects" the request.
                confirmationUi.clickNegativeButton()
            }

    protected fun test_userDismissed(selfManaged: Boolean, displayName: String? = null) =
            test_cancelled(selfManaged, displayName) {
                // User "dismisses" the request.
                uiDevice.pressBack()
            }

    private fun test_cancelled(
        selfManaged: Boolean,
        displayName: String? = null,
        cancelAction: () -> Unit
    ) {
        sendRequestAndLaunchConfirmation(selfManaged, displayName)

        cancelAction()

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

    protected fun sendRequestAndLaunchConfirmation(
        selfManaged: Boolean = false,
        displayName: String? = null,
        deviceFilter: DeviceFilter<*>? = null
    ) {
        val request = AssociationRequest.Builder()
                .apply {
                    // Set the self-managed flag.
                    setSelfManaged(selfManaged)

                    // Set profile if not null.
                    profile?.let { setDeviceProfile(it) }

                    // Set display name if not null.
                    displayName?.let { setDisplayName(it) }

                    // Add device filter if not null.
                    deviceFilter?.let { addDeviceFilter(it) }
                }
                .build()
        callback.clearRecordedInvocations()

        // If the REQUEST_COMPANION_SELF_MANAGED and/or the profile permission is required:
        // run with these permissions as the Shell;
        // otherwise: just call associate().
        with(getRequiredPermissions(selfManaged)) {
            if (isNotEmpty()) {
                withShellPermissionIdentity(*toTypedArray()) {
                    cdm.associate(request, SIMPLE_EXECUTOR, callback)
                }
            } else {
                cdm.associate(request, SIMPLE_EXECUTOR, callback)
            }
        }

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

    private fun getRequiredPermissions(selfManaged: Boolean): List<String> =
            mutableListOf<String>().also {
                if (selfManaged) it += Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
                if (profilePermission != null) it += profilePermission
            }

    protected fun setDiscoveryTimeout(timeout: Int) =
            instrumentation.setSystemProp(SYS_PROP_DEBUG_TIMEOUT, timeout.toString())

    private fun restoreDiscoveryTimeout() = setDiscoveryTimeout(0)

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         */
        @JvmStatic
        protected fun supportedProfilesAndNull() = mutableListOf<Array<String?>>().apply {
            add(arrayOf(null, null, "null"))
            addAll(supportedProfiles())
        }

        /** List of (profile, permission, name) tuples that represent all supported profiles. */
        private fun supportedProfiles(): Collection<Array<String?>> = DEVICE_PROFILES.map {
            profile ->
            arrayOf(profile,
                    DEVICE_PROFILE_TO_PERMISSION[profile]!!,
                    DEVICE_PROFILE_TO_NAME[profile]!!)
        }

        @JvmStatic
        protected val UNMATCHABLE_BT_FILTER = BluetoothDeviceFilter.Builder()
                .setAddress("FF:FF:FF:FF:FF:FF")
                .setNamePattern(Pattern.compile("This Device Does Not Exist"))
                .build()

        private const val SYS_PROP_DEBUG_TIMEOUT = "debug.cdm.discovery_timeout"
    }
}