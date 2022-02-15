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

package android.companion.cts.common

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.util.Log

class RecordingCallback
private constructor(container: InvocationContainer<CallbackInvocation>) :
    CompanionDeviceManager.Callback(),
    InvocationTracker<RecordingCallback.CallbackInvocation> by container {

    constructor() : this(InvocationContainer())

    override fun onDeviceFound(intentSender: IntentSender) =
        logAndRecordInvocation(OnDeviceFound(intentSender))

    override fun onAssociationPending(intentSender: IntentSender) =
        logAndRecordInvocation(OnAssociationPending(intentSender))

    override fun onAssociationCreated(associationInfo: AssociationInfo) =
        logAndRecordInvocation(OnAssociationCreated(associationInfo))

    override fun onFailure(error: CharSequence?) = logAndRecordInvocation(OnFailure(error))

    private fun logAndRecordInvocation(invocation: CallbackInvocation) {
        Log.d(TAG, "Callback: $invocation")
        recordInvocation(invocation)
    }

    sealed interface CallbackInvocation
    data class OnDeviceFound(val intentSender: IntentSender) : CallbackInvocation
    data class OnAssociationPending(val intentSender: IntentSender) : CallbackInvocation
    data class OnAssociationCreated(val associationInfo: AssociationInfo) : CallbackInvocation
    data class OnFailure(val error: CharSequence?) : CallbackInvocation
}