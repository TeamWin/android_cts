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

package android.companion.cts

import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.companion.CompanionDeviceManager
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

/**
 * Test CDM APIs for listening for changes to [android.companion.AssociationInfo].
 *
 * Run: atest CtsCompanionDevicesTestCases:AssociationsChangedListenerTest
 *
 * @see android.companion.CompanionDeviceManager.OnAssociationsChangedListener
 * @see android.companion.CompanionDeviceManager.addOnAssociationsChangedListener
 * @see android.companion.CompanionDeviceManager.removeOnAssociationsChangedListener
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociationsChangedListenerTest : TestBase() {

    @Test
    fun test_addOnAssociationsChangedListener_requiresPermission() {
        /**
         * Attempts to add a listener without [MANAGE_COMPANION_DEVICES] permission should
         * throw a [SecurityException] and should not change the existing associations.
         */
        assertFailsWith(SecurityException::class) {
            cdm.addOnAssociationsChangedListener(SIMPLE_EXECUTOR, NO_OP_LISTENER)
        }

        /** Re-running with [MANAGE_COMPANION_DEVICES] permissions: now should succeed */
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.addOnAssociationsChangedListener(SIMPLE_EXECUTOR, NO_OP_LISTENER)

            /** Succeeded, now remove. */
            cdm.removeOnAssociationsChangedListener(NO_OP_LISTENER)
        }
    }

    companion object {
        val NO_OP_LISTENER = CompanionDeviceManager.OnAssociationsChangedListener { }
    }
}