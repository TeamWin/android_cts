/*
 * Copyright 2023 The Android Open Source Project
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

import android.app.AppOpsManager
import android.content.pm.PackageInstaller
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.AppOpsUtils
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
class RestrictedSettingsTest : PackageInstallerTestBase() {
    companion object {
        private const val APP_OP_STR = "android:access_restricted_settings"
    }

    @Test
    fun installViaSessionByLocalFile() {
        installViaSession(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
        Truth.assertThat(
                AppOpsUtils.getOpMode(TEST_APK_PACKAGE_NAME, APP_OP_STR)
        )
                .isEqualTo(AppOpsManager.MODE_ERRORED)
    }

    @Test
    fun installViaSessionByDownloadedFile() {
        installViaSession(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        Truth.assertThat(
                AppOpsUtils.getOpMode(TEST_APK_PACKAGE_NAME, APP_OP_STR)
        )
                .isEqualTo(AppOpsManager.MODE_ERRORED)
    }

    @Test
    fun installViaSessionByOther() {
        installViaSession(PackageInstaller.PACKAGE_SOURCE_OTHER)
        Truth.assertThat(
                AppOpsUtils.getOpMode(TEST_APK_PACKAGE_NAME, APP_OP_STR)
        )
                .isEqualTo(AppOpsManager.MODE_ALLOWED)
    }

    @Test
    fun installViaSessionByUnspecified() {
        installViaSession(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED)
        Truth.assertThat(
                AppOpsUtils.getOpMode(TEST_APK_PACKAGE_NAME, APP_OP_STR)
        )
                .isEqualTo(AppOpsManager.MODE_ALLOWED)
    }

    @Test
    fun installViaSessionByStore() {
        installViaSession(PackageInstaller.PACKAGE_SOURCE_STORE)
        Truth.assertThat(
                AppOpsUtils.getOpMode(TEST_APK_PACKAGE_NAME, APP_OP_STR)
        )
                .isEqualTo(AppOpsManager.MODE_ALLOWED)
    }

    private fun installViaSession(packageSource: Int) {
        startInstallationViaSessionWithPackageSource(packageSource)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        Truth.assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
        val info = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        Truth.assertThat(info.packageSource).isEqualTo(packageSource)
    }
}
