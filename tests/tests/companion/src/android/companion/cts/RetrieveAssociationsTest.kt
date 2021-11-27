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
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Test CDM APIs for retrieving the list of existing associations.
 *
 * Run: atest CtsCompanionDevicesTestCases:RetrieveAssociationsTest
 *
 * @see android.companion.CompanionDeviceManager.getAllAssociations
 * @see android.companion.CompanionDeviceManager.getMyAssociations
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class RetrieveAssociationsTest : TestBase() {

    @Test
    fun test_getMyAssociations_singleAssociation() = with(targetApp) {
        assertEmpty(cdm.myAssociations)

        associate(MAC_ADDRESS_A)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_A
                ))

        disassociate(MAC_ADDRESS_A)
        assertEmpty(cdm.myAssociations)
    }

    @Test
    fun test_getMyAssociations_multipleAssociations() = with(targetApp) {
        assertEmpty(cdm.myAssociations)

        associate(MAC_ADDRESS_A)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_A
                ))

        associate(MAC_ADDRESS_B)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_A,
                        packageName to MAC_ADDRESS_B
                ))

        associate(MAC_ADDRESS_C)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_A,
                        packageName to MAC_ADDRESS_B,
                        packageName to MAC_ADDRESS_C
                ))

        disassociate(MAC_ADDRESS_A)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_B,
                        packageName to MAC_ADDRESS_C
                ))

        disassociate(MAC_ADDRESS_B)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(
                        packageName to MAC_ADDRESS_C
                ))

        disassociate(MAC_ADDRESS_C)
        assertEmpty(cdm.myAssociations)
    }

    @Test
    fun test_getMyAssociations_otherPackages_NotIncluded() {
        testApp.associate(MAC_ADDRESS_A)
        assertEmpty(cdm.myAssociations)

        targetApp.associate(MAC_ADDRESS_B)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(targetApp.packageName to MAC_ADDRESS_B))

        testApp.associate(MAC_ADDRESS_C)
        assertAssociations(
                actual = cdm.myAssociations,
                expected = setOf(targetApp.packageName to MAC_ADDRESS_B))

        targetApp.disassociate(MAC_ADDRESS_B)
        assertEmpty(cdm.myAssociations)
    }

    @Test
    fun test_getAllAssociations_requiresPermission() {
        /**
         * Attempts to get the list of all associations for the (current) user without
         * [MANAGE_COMPANION_DEVICES] permission should throw a [SecurityException].
         */
        assertFailsWith(SecurityException::class) {
            cdm.allAssociations
        }

        /**
         * Re-running with [MANAGE_COMPANION_DEVICES] permissions: now should succeed and return a
         * non-null list.
         */
        assertNotNull(
                withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
                    cdm.allAssociations
                })
    }

    @Test
    fun test_getAllAssociations_sameApp() = with(targetApp) {
        associate(MAC_ADDRESS_A)
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        packageName to MAC_ADDRESS_A
                ))

        disassociate(MAC_ADDRESS_A)
        assertEmpty(withShellPermissionIdentity { cdm.allAssociations })
    }

    @Test
    fun test_getAllAssociations_otherApps() = with(testApp) {
        associate(MAC_ADDRESS_A)
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        packageName to MAC_ADDRESS_A
                ))

        disassociate(MAC_ADDRESS_A)
        assertEmpty(withShellPermissionIdentity { cdm.allAssociations })
    }

    @Test
    fun test_getAllAssociations_sameAndOtherApps() {
        targetApp.associate(MAC_ADDRESS_A)
        testApp.associate(MAC_ADDRESS_B)

        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        targetApp.packageName to MAC_ADDRESS_A,
                        testApp.packageName to MAC_ADDRESS_B
                ))
    }
}