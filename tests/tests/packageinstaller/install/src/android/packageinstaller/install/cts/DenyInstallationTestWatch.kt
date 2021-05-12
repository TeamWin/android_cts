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
package android.packageinstaller.install.cts

import android.app.Activity
import android.os.Build
import android.platform.test.annotations.AppModeFull
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.Until
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

private const val ERROR_MESSAGE_ID = "android:id/message"
private const val OK_BUTTON_ID = "button1"

@AppModeFull(reason = "Instant apps cannot create installer sessions")
@RunWith(AndroidJUnit4::class)
class DenyInstallationTestWatch : PackageInstallerTestBase() {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiDevice = UiDevice.getInstance(instrumentation)

    /**
     * Check that apps cannot be installed on watches via a package-installer session
     */
    @Test
    fun confirmSessionInstallationFails() {
        assumeWatch()

        val installation = startInstallationViaSession()
        assertWearErrorDialogShown("Install blocking dialog not shown for install via session")

        // Install confirm dialog returns 'canceled' after getting dismissed
        assertEquals(Activity.RESULT_CANCELED, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))

        // Make sure app is not installed
        assertNotInstalled()
    }

    /**
     * Check that apps cannot be installed on watches via a package-installer intent
     */
    @Test
    fun confirmIntentInstallationFails() {
        assumeWatch()

        val installation = startInstallationViaIntent()
        assertWearErrorDialogShown("Install blocking dialog not shown for install via intent")

        // Install confirm dialog returns 'canceled' after getting dismissed
        assertEquals(Activity.RESULT_CANCELED, installation.get(TIMEOUT, TimeUnit.MILLISECONDS))

        // Make sure app is not installed
        assertNotInstalled()
    }

    /**
     * Test suite will need to be updated for S, this test checks this fact.
     */
    @Test
    fun confirmPlatformVersionLessThanS() {
        assumeWatch()

        assertTrue(
            "Must revisit method for Apk blocking for watches using Android S+, see b/187944296",
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
        )
    }

    /**
     * Click the Ok button in the alert dialog blocking installation
     */
    private fun clickOkButton() {
        uiDevice.wait(Until.findObject(By.res(SYSTEM_PACKAGE_NAME, OK_BUTTON_ID)), TIMEOUT)
            .click()
    }

    private fun assertUiObject(errorMessage: String, selector: BySelector) {
        Assert.assertNotNull(
            "$errorMessage after $TIMEOUT ms",
            uiDevice.wait(Until.findObject(selector), TIMEOUT)
        )
    }

    private fun assertWearErrorDialogShown(errorMessage: String) {
        assertUiObject(errorMessage, By.res(ERROR_MESSAGE_ID))
        clickOkButton()
    }
}
