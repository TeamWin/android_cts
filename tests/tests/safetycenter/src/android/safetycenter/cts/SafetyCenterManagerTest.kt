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

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_CRITICAL_WARNING
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val somePendingIntent = PendingIntent.getActivity(
        context, 0 /* requestCode */,
        Intent(ACTION_SAFETY_CENTER).addFlags(FLAG_ACTIVITY_NEW_TASK),
        FLAG_IMMUTABLE
    )

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

    @Test
    fun sendSafetyCenterUpdate_withSameId_replacesValue() {
        val id = "some_known_id"
        val firstSafetyCenterUpdate = SafetySourceData.Builder(id).build()
        runWithShellPermissionIdentity {
            safetyCenterManager.sendSafetyCenterUpdate(firstSafetyCenterUpdate)
        }
        val secondSafetyCenterUpdate = SafetySourceData.Builder(id).setStatus(
            SafetySourceStatus.Builder(
                "Status title", "Summary of the status", STATUS_LEVEL_CRITICAL_WARNING,
                somePendingIntent
            ).build()
        ).addIssue(
            SafetySourceIssue.Builder(
                "Issue title", "Summary of the issue",
                SEVERITY_LEVEL_CRITICAL_WARNING
            ).addAction(
                SafetySourceIssue.Action.Builder(
                    "Solve issue",
                    somePendingIntent
                ).build()
            ).build()
        ).build()
        runWithShellPermissionIdentity {
            safetyCenterManager.sendSafetyCenterUpdate(secondSafetyCenterUpdate)
        }

        val lastSafetyCenterUpdate = callWithShellPermissionIdentity {
            safetyCenterManager.getLastSafetyCenterUpdate(id)
        }

        assertThat(lastSafetyCenterUpdate).isEqualTo(secondSafetyCenterUpdate)
    }
}
