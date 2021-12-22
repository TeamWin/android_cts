package android.companion.cts.uiautomation

import android.platform.test.annotations.AppModeFull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests the Association Flow end-to-end.
 *
 * Build/Install/Run:
 * atest CtsCompanionDeviceManagerUiAutomationTestCases:AssociationEndToEndSingleDeviceTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(Parameterized::class)
class AssociationEndToEndSingleDeviceTest(
    profile: String?,
    profilePermission: String?,
    profileName: String // Used only by the Parameterized test runner for tagging.
) : UiAutomationTestBase(profile, profilePermission) {

    @Test
    @Ignore("b/211722613")
    fun test_userRejected() =
            super.test_userRejected(singleDevice = true, selfManaged = false, displayName = null)

    @Test
    @Ignore("b/211722613")
    fun test_userDismissed() =
            super.test_userDismissed(singleDevice = true, selfManaged = false, displayName = null)

    @Test
    @Ignore("b/211722613")
    fun test_timeout() = super.test_timeout(singleDevice = true)

    @Test
    @Ignore("b/211722613")
    fun test_userConfirmed() = super.test_userConfirmed_foundDevice(singleDevice = true) {
        // Wait until a device is found, which should activate the "positive" button, and click on
        // the button.
        confirmationUi.waitUntilPositiveButtonIsEnabledAndClick()
    }

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         * Each test will be suffixed with "[profile=<NAME>]", e.g.: "[profile=WATCH]".
         */
        @Parameterized.Parameters(name = "profile={2}")
        @JvmStatic
        fun parameters() = supportedProfilesAndNull()
    }
}