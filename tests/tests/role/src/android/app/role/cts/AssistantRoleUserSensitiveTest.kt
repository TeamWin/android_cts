/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.role.cts

import android.Manifest
import android.app.Instrumentation
import android.app.role.RoleManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Tests the User Sensitive behavior of the Assistant role
 */
@RunWith(AndroidJUnit4::class)
class AssistantRoleUserSensitiveTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.context
    private val packageManager = context.packageManager
    private val roleManager = context.getSystemService(RoleManager::class.java)

    companion object {
        private const val ROLE_NAME = RoleManager.ROLE_ASSISTANT
        private const val APP_APK_PATH = "/data/local/tmp/cts/role/CtsRoleTestApp.apk"
        private const val APP_PACKAGE_NAME = "android.app.role.cts.app"
        private const val ASSISTANT_USER_SENSITIVE_SETTING =
            "assistant_record_audio_is_user_sensitive_key"
        private const val ALWAYS_USER_SENSITIVE = (1 shl 8) or (1 shl 9)
        private const val TIMEOUT_MILLIS = 15 * 1000L
    }

    private var originalRoleHolder: String? = null
    private var originalShowAssistantSetting: Int = 0

    @Before
    fun installApp() {
        runShellCommand("pm install -r --user ${Process.myUserHandle().identifier} $APP_APK_PATH")
        eventually {
            assertThat(micPermIsUserSensitive()).isTrue()
        }
    }

    @Before
    fun saveRoleHolderAndShowAssistantSetting() {
        originalShowAssistantSetting =
            Settings.Secure.getInt(context.contentResolver, ASSISTANT_USER_SENSITIVE_SETTING, 0)

        val currentHolders = getRoleHolders()
        if (currentHolders.isNotEmpty()) {
            originalRoleHolder = currentHolders[0]
            removeRoleHolder(ROLE_NAME, currentHolders[0])
        }
    }

    @After
    fun restoreRoleHolderAndShowAssistantSetting() {
        setShowAssistantMic(originalShowAssistantSetting == 1)

        val roleHolder = originalRoleHolder
        if (roleHolder != null) {
            addRoleHolder(ROLE_NAME, roleHolder)
        } else {
            removeRoleHolder()
        }
    }

    @After
    fun uninstallApp() {
        runShellCommand("pm uninstall --user ${Process.myUserHandle().identifier} " +
            APP_PACKAGE_NAME)
    }

    @Test
    fun appBecomesNonSensitiveWhenBecomingAssistant() {
        assertThat(micPermIsUserSensitive()).isTrue()
        addRoleHolder()
        eventually {
            assertThat(micPermIsUserSensitive()).isFalse()
        }
    }

    @Test
    fun appBecomesSensitiveWhenRemovedFromAssistant() {
        addRoleHolder()
        eventually {
            assertThat(micPermIsUserSensitive()).isFalse()
        }
        removeRoleHolder()
        eventually {
            assertThat(micPermIsUserSensitive()).isTrue()
        }
    }

    @Test
    fun appIsStillSensitiveIfShowAssistantEnabled() {
        setShowAssistantMic(true)
        addRoleHolder()
        Thread.sleep(500)
        assertThat(micPermIsUserSensitive()).isTrue()
    }

    @Test
    fun appIsNotSensitiveIfShowAssistantDisabled() {
        setShowAssistantMic(true)
        addRoleHolder()
        Thread.sleep(500)
        assertThat(micPermIsUserSensitive()).isTrue()
        setShowAssistantMic(false)

        // Required to have flags updated, the UI switch manually calls for an update
        removeRoleHolder()
        addRoleHolder()

        eventually {
            assertThat(micPermIsUserSensitive()).isFalse()
        }
    }

    private fun micPermIsUserSensitive(packageName: String = APP_PACKAGE_NAME): Boolean {
        return callWithShellPermissionIdentity {
            (packageManager.getPermissionFlags(Manifest.permission.RECORD_AUDIO,
                packageName, Process.myUserHandle()) and ALWAYS_USER_SENSITIVE) != 0
        }
    }

    private fun setShowAssistantMic(show: Boolean) {
        runWithShellPermissionIdentity {
            val value = if (show) {
                1
            } else {
                0
            }
            Settings.Secure.putInt(context.contentResolver, ASSISTANT_USER_SENSITIVE_SETTING, value)
        }
    }

    @Throws(Exception::class)
    private fun addRoleHolder(
        roleName: String = ROLE_NAME,
        packageName: String = APP_PACKAGE_NAME,
        expectSuccess: Boolean = true
    ) {
        val future = CallbackFuture()
        runWithShellPermissionIdentity {
            roleManager.addRoleHolderAsUser(roleName,
                packageName, 0, Process.myUserHandle(), context.getMainExecutor(), future)
        }
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(expectSuccess)
    }

    @Throws(Exception::class)
    private fun removeRoleHolder(
        roleName: String = ROLE_NAME,
        packageName: String = APP_PACKAGE_NAME,
        expectSuccess: Boolean = true
    ) {
        val future = CallbackFuture()
        runWithShellPermissionIdentity {
            roleManager.removeRoleHolderAsUser(roleName,
                packageName, 0, Process.myUserHandle(), context.getMainExecutor(), future)
        }
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(expectSuccess)
    }

    @Throws(Exception::class)
    private fun getRoleHolders(roleName: String = ROLE_NAME): List<String> {
        return callWithShellPermissionIdentity { roleManager.getRoleHolders(roleName) }
    }

    private inner class CallbackFuture : CompletableFuture<Boolean>(), Consumer<Boolean> {

        override fun accept(successful: Boolean) {
            complete(successful)
        }
    }
}
