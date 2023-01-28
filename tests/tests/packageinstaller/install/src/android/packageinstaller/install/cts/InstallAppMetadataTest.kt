/*
 * Copyright 2022 The Android Open Source Project
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

import android.app.PendingIntent
import android.app.UiAutomation
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager.NameNotFoundException
import android.os.PersistableBundle
import android.platform.test.annotations.AppModeFull
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
class InstallAppMetadataTest : PackageInstallerTestBase() {

    private val TEST_FIELD = "testField"

    private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation()

    @Test
    fun installViaSession() {
        startInstallationViaSession()
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
        assertThat(result.preapproval).isFalse()

        uiAutomation.adoptShellPermissionIdentity()
        val data2 = pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
        uiAutomation.dropShellPermissionIdentity()
        assertThat(data2).isNotNull()
        assertThat(data2.isEmpty()).isTrue()
    }

    @Test
    fun installViaSessionWithAppMetadata() {
        val data = createAppMetadata()

        val (sessionId, session) = createSession(0, false, null)
        writeSession(session, TEST_APK_NAME)
        setAppMetadata(session, data)
        assertAppMetadata(data.getString(TEST_FIELD), session.getAppMetadata())
        commitSession(session)

        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
        assertThat(result.preapproval).isFalse()

        uiAutomation.adoptShellPermissionIdentity()
        assertAppMetadata(data.getString(TEST_FIELD), pm.getAppMetadata(TEST_APK_PACKAGE_NAME))
        uiAutomation.dropShellPermissionIdentity()
    }

    @Test(expected = SecurityException::class)
    fun getAppMetadataWithNoPermission() {
        startInstallationViaSession(createAppMetadata())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
        assertThat(result.preapproval).isFalse()

        pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
    }

    @Test(expected = IllegalArgumentException::class)
    fun installViaSessionWithBadAppMetadata() {
        val data = createAppMetadataExceedSizeLimit()
        startInstallationViaSession(data)
    }

    @Test(expected = NameNotFoundException::class)
    fun noInstallGetAppMetadata() {
        uiAutomation.adoptShellPermissionIdentity()
        try {
            val data = pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
        } catch (e: Exception) {
            throw e
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun installViaSessionWithOnlyAppMetadata() {
        val data = createAppMetadata()
        val (sessionId, session) = createSession(0, false, null)
        setAppMetadata(session, data)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0 /* requestCode */,
            Intent(INSTALL_ACTION_CB), PendingIntent.FLAG_UPDATE_CURRENT
                    or PendingIntent.FLAG_MUTABLE
                    or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT)
        session.commit(pendingIntent.intentSender)
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_FAILURE_INVALID)
    }

    private fun setAppMetadata(session: PackageInstaller.Session, data: PersistableBundle) {
        try {
            session.setAppMetadata(data)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    private fun assertAppMetadata(testVal: String?, data: PersistableBundle) {
        assertThat(data).isNotNull()
        assertEquals(data.size(), 1)
        assertThat(data.containsKey(TEST_FIELD)).isTrue()
        assertEquals(data.getString(TEST_FIELD), testVal)
    }

    private fun createAppMetadata(): PersistableBundle {
        val bundle = PersistableBundle()
        bundle.putString(TEST_FIELD, "testValue")
        return bundle
    }

    private fun createAppMetadataExceedSizeLimit(): PersistableBundle {
        val bundle = PersistableBundle()
        // create a bundle that is greater than default size limit of 32KB.
        bundle.putString(TEST_FIELD, "a".repeat(32000))
        return bundle
    }
}
