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

package android.sensorprivacy.cts

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.OnSensorPrivacyChangedListener
import android.os.PowerManager
import android.support.test.uiautomator.By
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UiAutomatorUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

abstract class SensorPrivacyBaseTest(
    val sensor: Int,
    vararg val extras: String
) {

    companion object {
        const val MIC_CAM_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.USE_MIC_CAM"
        const val FINISH_MIC_CAM_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.FINISH_USE_MIC_CAM"
        const val USE_MIC_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.USE_MICROPHONE"
        const val USE_CAM_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.USE_CAMERA"
        const val DELAYED_ACTIVITY_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.DELAYED_ACTIVITY"
        const val DELAYED_ACTIVITY_NEW_TASK_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.DELAYED_ACTIVITY_NEW_TASK"
    }

    protected val instrumentation = InstrumentationRegistry.getInstrumentation()!!
    protected val uiAutomation = instrumentation.uiAutomation!!
    protected val uiDevice = UiDevice.getInstance(instrumentation)!!
    protected val context = instrumentation.targetContext!!
    protected val spm = context.getSystemService(SensorPrivacyManager::class.java)!!
    protected val packageManager = context.packageManager!!

    @Before
    fun init() {
        Assume.assumeTrue(spm.supportsSensorToggle(sensor))
        uiDevice.wakeUp()
        runShellCommandOrThrow("wm dismiss-keyguard")
        uiDevice.waitForIdle()
    }

    @Test
    fun testSetSensor() {
        setSensor(true)
        assertTrue(isSensorPrivacyEnabled())

        setSensor(false)
        assertFalse(isSensorPrivacyEnabled())
    }

    @Test
    fun testDialog() {
        testDialog(delayedActivity = false, delayedActivityNewTask = false)
    }

    @Test
    fun testDialog_remainsOnTop() {
        testDialog(delayedActivity = true, delayedActivityNewTask = false)
    }

    @Test
    fun testDialog_remainsOnTop_newTask() {
        testDialog(delayedActivity = true, delayedActivityNewTask = true)
    }

    fun testDialog(delayedActivity: Boolean = false, delayedActivityNewTask: Boolean = false) {
        try {
            setSensor(true)
            val intent = Intent(MIC_CAM_ACTIVITY_ACTION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
            for (extra in extras) {
                intent.putExtra(extra, true)
            }
            intent.putExtra(DELAYED_ACTIVITY_EXTRA, delayedActivity)
            intent.putExtra(DELAYED_ACTIVITY_NEW_TASK_EXTRA, delayedActivityNewTask)
            context.startActivity(intent)
            if (delayedActivity || delayedActivityNewTask) {
                Thread.sleep(3000)
            }
            unblockSensorWithDialogAndAssert()
        } finally {
            runShellCommandOrThrow("am broadcast" +
                    " --user ${context.userId}" +
                    " -a $FINISH_MIC_CAM_ACTIVITY_ACTION" +
                    " -f ${Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS}")
        }
    }

    @Test
    fun testListener() {
        val executor = Executors.newSingleThreadExecutor()
        setSensor(false)
        val latchEnabled = CountDownLatch(1)
        var listener =
                OnSensorPrivacyChangedListener { _, enabled: Boolean ->
                    if (enabled) {
                        latchEnabled.countDown()
                    }
                }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(sensor, executor, listener)
        }
        setSensor(true)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(sensor, listener)
        }

        val latchDisabled = CountDownLatch(1)
        listener = OnSensorPrivacyChangedListener { _, enabled: Boolean ->
            if (!enabled) {
                latchDisabled.countDown()
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(sensor, executor, listener)
        }
        setSensor(false)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(sensor, listener)
        }
    }

    @Test
    fun testCantChangeWhenLocked() {
        setSensor(false)
        assertFalse(isSensorPrivacyEnabled())
        runWhileLocked {
            setSensor(true)
            assertFalse("State was changed while device is locked",
                    isSensorPrivacyEnabled())
        }

        setSensor(true)
        assertTrue(isSensorPrivacyEnabled())
        runWhileLocked {
            setSensor(false)
            assertTrue("State was changed while device is locked",
                    isSensorPrivacyEnabled())
        }
    }

    fun unblockSensorWithDialogAndAssert() {
        UiAutomatorUtils.waitFindObject(By.text(
                Pattern.compile("Unblock", Pattern.CASE_INSENSITIVE))).click()
        eventually {
            assertFalse(isSensorPrivacyEnabled())
        }
    }

    fun setSensor(enable: Boolean) {
        runWithShellPermissionIdentity {
            spm.setSensorPrivacy(sensor, enable)
        }
    }

    fun isSensorPrivacyEnabled(): Boolean {
        return runWithShellPermissionIdentity(ThrowingSupplier {
            spm.isSensorPrivacyEnabled(sensor)
        })
    }

    fun runWhileLocked(r: () -> Unit) {
        val km = context.getSystemService(KeyguardManager::class.java)!!
        val pm = context.getSystemService(PowerManager::class.java)!!
        val password = byteArrayOf(1, 2, 3, 4)
        try {
            runWithShellPermissionIdentity {
                km!!.setLock(KeyguardManager.PIN, password, KeyguardManager.PIN, null)
            }
            eventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP)
                assertFalse("Device never slept.", pm.isInteractive)
            }
            eventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
                assertTrue("Device never woke up.", pm.isInteractive)
            }
            eventually {
                assertTrue("Device isn't locked", km.isDeviceLocked)
            }

            r.invoke()
        } finally {
            runWithShellPermissionIdentity {
                km!!.setLock(KeyguardManager.PIN, null, KeyguardManager.PIN, password)
            }

            // Recycle the screen power in case the keyguard is stuck open
            eventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP)
                assertFalse("Device never slept.", pm.isInteractive)
            }
            eventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
                assertTrue("Device never woke up.", pm.isInteractive)
            }

            eventually {
                assertFalse("Device isn't unlocked", km.isDeviceLocked)
            }
        }
    }
}
