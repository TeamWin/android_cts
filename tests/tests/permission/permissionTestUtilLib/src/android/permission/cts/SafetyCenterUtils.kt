/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permission.cts

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.provider.DeviceConfig
import android.support.test.uiautomator.By
import androidx.annotation.RequiresApi
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject

object SafetyCenterUtils {
    /** Name of the flag that determines whether SafetyCenter is enabled.  */
    const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"

    /** Returns whether the device supports Safety Center. */
    @JvmStatic
    fun deviceSupportsSafetyCenter(context: Context): Boolean {
        return context.resources.getBoolean(
            Resources.getSystem().getIdentifier("config_enableSafetyCenter", "bool", "android"))
    }

    /**
     * Enabled or disable Safety Center
     */
    @JvmStatic
    fun setSafetyCenterEnabled(enabled: Boolean) {
        setDeviceConfigPrivacyProperty(
            PROPERTY_SAFETY_CENTER_ENABLED, enabled.toString()
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @JvmStatic
    fun startSafetyCenterActivity(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_SAFETY_CENTER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    @JvmStatic
    fun assertSafetyCenterStarted() {
        // CollapsingToolbar title can't be found by text, so using description instead.
        waitFindObject(By.desc("Security & Privacy"))
    }

    private fun setDeviceConfigPrivacyProperty(propertyName: String, value: String) {
        runWithShellPermissionIdentity {
            val valueWasSet = DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY, /* name = */
                propertyName, /* value = */
                value, /* makeDefault = */
                false
            )
            check(valueWasSet) { "Could not set $propertyName to $value" }
        }
    }
}