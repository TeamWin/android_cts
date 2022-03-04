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

import android.Manifest
import android.annotation.CallSuper
import android.app.Instrumentation
import android.app.UiAutomation
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.MacAddress
import android.os.SystemClock.sleep
import android.os.SystemClock.uptimeMillis
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import org.junit.Before
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A base class for CompanionDeviceManager [Tests][org.junit.Test] to extend.
 */
abstract class TestBase {
    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val uiAutomation: UiAutomation = instrumentation.uiAutomation

    protected val context: Context = instrumentation.context
    protected val userId = context.userId
    protected val targetPackageName = instrumentation.targetContext.packageName

    protected val targetApp = AppHelper(instrumentation, userId, targetPackageName)

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

        // Make sure CompanionDeviceServices are not bound.
        assertFalse(PrimaryCompanionService.isBound)
        assertFalse(SecondaryCompanionService.isBound)

        setUp()
    }

    @After
    fun base_tearDown() {
        if (!hasCompanionDeviceSetupFeature) return

        tearDown()

        // Remove all existing associations (for the user).
        withShellPermissionIdentity { cdm.disassociateAll() }
    }

    @CallSuper
    protected open fun setUp() {}

    @CallSuper
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

    protected fun createSelfManagedAssociation(displayName: String): Int {
        val callback = RecordingCallback()
        val request: AssociationRequest = AssociationRequest.Builder()
                .setSelfManaged(true)
                .setDisplayName(displayName)
                .build()
        callback.assertInvokedByActions {
            withShellPermissionIdentity(Manifest.permission.REQUEST_COMPANION_SELF_MANAGED) {
                cdm.associate(request, SIMPLE_EXECUTOR, callback)
            }
        }

        val callbackInvocation = callback.invocations.first()
        assertIs<RecordingCallback.OnAssociationCreated>(callbackInvocation)
        return callbackInvocation.associationInfo.id
    }

    private fun CompanionDeviceManager.disassociateAll() =
            allAssociations.forEach { disassociate(it.id) }
}

const val TAG = "CtsCompanionDeviceManagerTestCases"

fun <T> assumeThat(message: String, obj: T, assumption: (T) -> Boolean) {
    if (!assumption(obj)) throw AssumptionViolatedException(message)
}

fun <T> assertEmpty(list: Collection<T>) = assertTrue("Collection is not empty") { list.isEmpty() }

fun assertAssociations(
    actual: List<AssociationInfo>,
    expected: Set<Pair<String, MacAddress?>>
) = assertEquals(actual = actual.map { it.packageName to it.deviceMacAddress }.toSet(),
        expected = expected)

/**
 * @return whether the condition was met before time ran out.
 */
fun waitFor(
    timeout: Duration = 10.seconds,
    interval: Duration = 1.seconds,
    condition: () -> Boolean
): Boolean {
    val startTime = uptimeMillis()
    while (!condition()) {
        if (uptimeMillis() - startTime > timeout.inWholeMilliseconds) return false
        sleep(interval.inWholeMilliseconds)
    }
    return true
}

fun <R> waitForResult(
    timeout: Duration = 10.seconds,
    interval: Duration = 1.seconds,
    block: () -> R
): R? {
    val startTime = uptimeMillis()
    while (true) {
        val result: R = block()
        if (result != null) return result
        sleep(interval.inWholeMilliseconds)
        if (uptimeMillis() - startTime > timeout.inWholeMilliseconds) return null
    }
}

fun Instrumentation.runShellCommand(cmd: String): String {
    Log.i(TAG, "Running shell command: '$cmd'")
    try {
        val out = SystemUtil.runShellCommand(this, cmd)
        Log.i(TAG, "Out:\n$out")
        return out
    } catch (e: IOException) {
        Log.e(TAG, "Error running shell command: $cmd")
        throw e
    }
}

fun Instrumentation.setSystemProp(name: String, value: String) =
        runShellCommand("setprop $name $value")