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

package android.safetycenter.cts

import com.google.common.truth.Truth.assertThat
import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.filters.SdkSuppress
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetySourceData

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    @Test
    fun getLastSafetyCenterUpdate_noUpdate_returnsNull() {
        val lastSafetyCenterUpdate = callWithShellPermissionIdentity {
            safetyCenterManager.getLastSafetyCenterUpdate("some_unknown_id")
        }

        assertThat(lastSafetyCenterUpdate).isNull()
    }

    @Test
    fun sendSafetyCenterUpdate_getLastSafetyCenterUpdateReturnsNewValue() {
        val id = "some_known_id"
        val safetyCenterUpdate = SafetySourceData.Builder(id).build()
        runWithShellPermissionIdentity {
            safetyCenterManager.sendSafetyCenterUpdate(safetyCenterUpdate)
        }

        val lastSafetyCenterUpdate = callWithShellPermissionIdentity {
            safetyCenterManager.getLastSafetyCenterUpdate(id)
        }

        assertThat(lastSafetyCenterUpdate).isEqualTo(safetyCenterUpdate)
    }
}
