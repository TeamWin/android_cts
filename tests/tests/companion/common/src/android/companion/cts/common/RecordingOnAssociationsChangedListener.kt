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

class RecordingOnAssociationsChangedListener
    : CompanionDeviceManager.OnAssociationsChangedListener {
    private val _invocations: MutableList<List<AssociationInfo>> = mutableListOf()
    val invocations: List<List<AssociationInfo>>
        get() = _invocations

    override fun onAssociationsChanged(associations: List<AssociationInfo>) {
        _invocations.add(associations)
    }

    fun waitForInvocation(timeout: Long = 1_000) {
        if (!waitFor(timeout = timeout, interval = 100) { invocations.isNotEmpty() })
            throw AssertionError("Callback hasn't been invoked")
    }

    fun clearRecordedInvocations() = _invocations.clear()
}