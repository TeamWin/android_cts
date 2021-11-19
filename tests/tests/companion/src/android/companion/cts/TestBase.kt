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

import android.app.Instrumentation
import android.app.UiAutomation
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.MacAddress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import org.junit.Before
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A base class for CompanionDeviceManager [Tests][org.junit.Test] to extend.
 */
abstract class TestBase {
    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val uiAutomation: UiAutomation = instrumentation.uiAutomation

    protected val context: Context = instrumentation.context
    private val userId = context.userId

    protected val targetApp =
            AppHelper(userId, instrumentation.targetContext.packageName, instrumentation)
    protected val testApp = AppHelper(userId, TEST_APP_PACKAGE_NAME, instrumentation)

    protected val pm: PackageManager by lazy { context.packageManager!! }
    private val hasCompanionDeviceSetupFeature by lazy {
        pm.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    }

    protected val cdm: CompanionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)!!
    }

    @Before
    fun base_setUp() {
        assumeTrue(hasCompanionDeviceSetupFeature)

        // Remove all existing associations (for the user).
        assertEmpty(withShellPermissionIdentity {
            cdm.disassociateAll()
            cdm.allAssociations
        })

        setUp()
    }

    @After
    fun base_tearDown() {
        if (!hasCompanionDeviceSetupFeature) return

        tearDown()

        // Remove all existing associations (for the user).
        withShellPermissionIdentity { cdm.disassociateAll() }
    }

    protected open fun setUp() {}
    protected open fun tearDown() {}

    protected fun <T> withShellPermissionIdentity(
        vararg permissions: String,
        block: () -> T
    ): T {
        if (permissions.isNotEmpty()) {
            uiAutomation.adoptShellPermissionIdentity(*permissions)
        } else {
            uiAutomation.adoptShellPermissionIdentity()
        }

        try {
            return block()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    companion object {
        val MAC_ADDRESS_A = MacAddress.fromString("00:00:00:00:00:AA")
        val MAC_ADDRESS_B = MacAddress.fromString("00:00:00:00:00:BB")
        val MAC_ADDRESS_C = MacAddress.fromString("00:00:00:00:00:CC")

        val SIMPLE_EXECUTOR: Executor = Executor { it.run() }
    }

    private fun CompanionDeviceManager.disassociateAll() =
            allAssociations.forEach { disassociate(it.id) }
}

const val TAG = "CtsCompanionDevicesTestCases"
private const val TEST_APP_PACKAGE_NAME = "android.os.cts.companiontestapp"

fun <T> assumeThat(message: String, obj: T, assumption: (T) -> Boolean) {
    if (!assumption(obj)) throw AssumptionViolatedException(message)
}

fun <T> assertEmpty(list: Collection<T>) = assertTrue("Collection is not empty") { list.isEmpty() }

fun assertAssociations(
    actual: List<AssociationInfo>,
    expected: Set<Pair<String, MacAddress?>>
) = assertEquals(actual.map { it.packageName to it.deviceMacAddress }.toSet(), expected)
