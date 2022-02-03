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

package android.safetycenter.cts

import android.Manifest.permission.MANAGE_SAFETY_CENTER
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetySourceData
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import java.util.concurrent.Executor

/**
 * Call {@link SafetyCenterManager#sendSafetyCenterUpdate} adopting Shell's
 * {@link SEND_SAFETY_CENTER_UPDATE} permission.
 */
fun SafetyCenterManager.sendUpdateWithPermission(safetySourceData: SafetySourceData) =
    runWithShellPermissionIdentity({
        sendSafetyCenterUpdate(safetySourceData)
    }, SEND_SAFETY_CENTER_UPDATE)

/**
 * Call {@link SafetyCenterManager#getLastSafetyCenterUpdate} adopting Shell's
 * {@link SEND_SAFETY_CENTER_UPDATE} permission.
 */
fun SafetyCenterManager.getLastUpdateWithPermission(id: String): SafetySourceData? =
    callWithShellPermissionIdentity({
        getLastSafetyCenterUpdate(id)
    }, SEND_SAFETY_CENTER_UPDATE)

/**
 * Call {@link SafetyCenterManager#isSafetyCenterEnabled} adopting Shell's
 * {@link SEND_SAFETY_CENTER_UPDATE} permission.
 */
fun SafetyCenterManager.isSafetyCenterEnabledWithPermission(): Boolean =
    callWithShellPermissionIdentity({
        isSafetyCenterEnabled
    }, SEND_SAFETY_CENTER_UPDATE)

/**
 * Call {@link SafetyCenterManager#refreshSafetySources} adopting Shell's
 * {@link MANAGE_SAFETY_CENTER} permission (required for
 * {@link SafetyCenterManager#refreshSafetySources}).
 */
fun SafetyCenterManager.refreshSafetySourcesWithPermission(refreshReason: Int) =
    runWithShellPermissionIdentity({
        refreshSafetySources(refreshReason)
    }, MANAGE_SAFETY_CENTER)

/**
 * Call {@link SafetyCenterManager#clearSafetyCenterData} adopting Shell's
 * {@link MANAGE_SAFETY_CENTER} permission.
 */
fun SafetyCenterManager.clearDataWithPermission() =
    runWithShellPermissionIdentity({
        clearSafetyCenterData()
    }, MANAGE_SAFETY_CENTER)

fun SafetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
    executor: Executor,
    listener: OnSafetyCenterDataChangedListener
) =
        runWithShellPermissionIdentity({
            addOnSafetyCenterDataChangedListener(executor, listener)
        }, MANAGE_SAFETY_CENTER)
