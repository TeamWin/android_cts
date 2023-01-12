/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.permission3.cts

import android.os.PersistableBundle

/** Helper methods for creating test app metadata [PersistableBundle] */
object AppMetadata {

    /** Returns valid App Metadata [PersistableBundle] representation */
    fun createDefaultAppMetadata(): PersistableBundle {
        val approximateLocationBundle = PersistableBundle().apply {
            putIntArray(
                "purposes",
                (1..7).toList().toIntArray())
        }

        val locationBundle = PersistableBundle().apply {
            putPersistableBundle(
                "APPROX_LOCATION",
                approximateLocationBundle)
        }

        val dataSharedBundle = PersistableBundle().apply {
            putPersistableBundle("location", locationBundle)
        }

        val dataLabelBundle = PersistableBundle().apply {
            putPersistableBundle("data_shared", dataSharedBundle)
        }

        val safetyLabelBundle = PersistableBundle().apply {
            putPersistableBundle("data_labels", dataLabelBundle)
        }

        return PersistableBundle().apply {
            putPersistableBundle("safety_labels", safetyLabelBundle)
        }
    }

    /**
     * Returns invalid App Metadata [PersistableBundle] representation. Invalidity due to invalid
     * label name usage
     */
    fun createInvalidAppMetadata(): PersistableBundle {
        val validAppMetaData = createDefaultAppMetadata()
        val validSafetyLabel = validAppMetaData.getPersistableBundle("safety_labels")

        return PersistableBundle().apply {
            putPersistableBundle("invalid_safety_labels", validSafetyLabel)
        }
    }
}
