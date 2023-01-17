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

package android.input.cts

import android.content.res.Resources
import android.media.AudioManager
import android.provider.Settings
import android.server.wm.settings.SettingsSession
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth
import java.io.Closeable
import junit.framework.Assert
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for key combination shortcut.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class KeyCombinationShortcutTest : SystemShortcutTestBase() {
    /**
     * Tests for POWER+VOLUME_DOWN shortcut.
     */
    private val screenshotChordEnabled: Boolean
        get() {
            val resources = activity.resources
            return try {
                resources.getBoolean(resources.getIdentifier(
                        "config_enableScreenshotChord", "bool", "android"))
            } catch (e: Resources.NotFoundException) {
                // Assume there's no camera enabled.
                false
            }
        }
    @Test
    fun testScreenShotShortcut() {
        Assume.assumeTrue(screenshotChordEnabled)

        val keyCodes = intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_DOWN)
        sendKeyCombination(keyCodes, 0)

        // Check if screenshot invoked.
        Assert.assertTrue(waitForReady("ScreenshotAnimation"))
    }

    // Check if volume hush gesture enabled.
    enum class PowerVolumeUpBehaviour {
        NO_BEHAVIOR, VOLUME_HUSH, POWER_MENU
    }
    private val powerVolUpBehavior: PowerVolumeUpBehaviour
        get() {
            val resources = activity.resources
            return try {
                when (resources.getInteger(resources.getIdentifier(
                        "config_keyChordPowerVolumeUp", "integer", "android"))) {
                    1 -> PowerVolumeUpBehaviour.VOLUME_HUSH
                    2 -> PowerVolumeUpBehaviour.POWER_MENU
                    else -> PowerVolumeUpBehaviour.NO_BEHAVIOR
                }
            } catch (e: Resources.NotFoundException) {
                PowerVolumeUpBehaviour.NO_BEHAVIOR
            }
        }

    /**
     * Tests for POWER+VOLUME_UP shortcut.
     */
    @Test
    fun testVolumeHushShortcut() {
        Assume.assumeTrue(powerVolUpBehavior == PowerVolumeUpBehaviour.VOLUME_HUSH)

        // set ringer mode to normal.
        val audioManager = activity.getSystemService(AudioManager::class.java)
        audioManager!!.ringerMode = AudioManager.RINGER_MODE_NORMAL

        VolumeHushSession().use {
            val keyCodes = intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP)
            sendKeyCombination(keyCodes, ViewConfiguration.getTapTimeout().toLong())
        }

        // Check if volume mute invoked.
        PollingCheck.check("Failed to make volume hush.", 5000) {
            audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        }
    }

    @Test
    fun testPowerMenuShortcut() {
        Assume.assumeTrue(powerVolUpBehavior == PowerVolumeUpBehaviour.POWER_MENU)

        val keyCodes = intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP)
        sendKeyCombination(keyCodes, ViewConfiguration.getTapTimeout().toLong())

        // Check if global action invoked.
        Assert.assertTrue(waitForReady("ActionsDialog"))
    }

    class VolumeHushSession : Closeable {
        val enableVolumeMuteGesture = SettingsSession(
                Settings.Global.getUriFor("key_chord_power_volume_up"),
                Settings.Secure::getInt, Settings.Secure::putInt)
        init {
            SystemUtil.runWithShellPermissionIdentity { enableVolumeMuteGesture.set(1) }
        }

        override fun close() {
            SystemUtil.runWithShellPermissionIdentity { enableVolumeMuteGesture.close() }
        }
    }

    /**
     * Tests for VOLUME_UP+VOLUME_DOWN shortcut.
     */
    @Test
    fun testAcessibilityShortcut() {
        // Gives the amount of time to mock a user needs to press the relevant keys to activate the
        // accessibility shortcut, this reference the result of
        // ViewConfiguration.getAccessibilityShortcutKeyTimeout.
        val timeout = 3000L
        AccessibilitySession().use {
            val keyCodes = intArrayOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
            sendKeyCombination(keyCodes, timeout)
            Assert.assertTrue(it.enableAccessibilityService.get().isNotEmpty())
            Assert.assertEquals(1, it.enableAccessibility.get())
        }
    }

    class AccessibilitySession : Closeable {
        val enableAccessibility = SettingsSession(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                Settings.Secure::getInt, Settings.Secure::putInt)
        val enableAccessibilityService = SettingsSession(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                Settings.Secure::getString, Settings.Secure::putString)
        val enableAccessibilityShortcut = SettingsSession(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE),
                Settings.Secure::getString, Settings.Secure::putString)
        val shortcut_dialog_shown = SettingsSession(
                Settings.Secure.getUriFor("accessibility_shortcut_dialog_shown"),
                Settings.Secure::getInt, Settings.Secure::putInt)

        init {
            SystemUtil.runWithShellPermissionIdentity {
                enableAccessibility.set(0)
                enableAccessibilityService.set("")
                shortcut_dialog_shown.set(1)
                enableAccessibilityShortcut.set("1")
            }
        }

        override fun close() {
            SystemUtil.runWithShellPermissionIdentity {
                enableAccessibility.close()
                enableAccessibilityService.close()
                shortcut_dialog_shown.close()
                enableAccessibilityShortcut.close()
            }
        }
    }

    /**
     * Tests toggle Caps Lock by META+ALT shortcut.
     */
    @Test
    fun testToggleCapsLockShortcut() {
        tapKey(KeyEvent.KEYCODE_A)
        var downEvent = getKeyEvent()
        Truth.assertThat(downEvent.keyCode).isEqualTo(KeyEvent.KEYCODE_A)
        val isCapsLockOn = downEvent.isCapsLockOn
        var upEvent = getKeyEvent()
        Truth.assertThat(upEvent.keyCode).isEqualTo(KeyEvent.KEYCODE_A)

        // if CapsLock is on, make sure it would be off before testing.
        if (isCapsLockOn) {
            tapKey(KeyEvent.KEYCODE_CAPS_LOCK)
        }

        val keyCodes = intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT)
        sendKeyCombination(keyCodes, 0)
        // consume KEYCODE_ALT_LEFT event.
        getKeyEvent()

        tapKey(KeyEvent.KEYCODE_A)
        downEvent = getKeyEvent()
        Truth.assertThat(downEvent.keyCode).isEqualTo(KeyEvent.KEYCODE_A)
        Truth.assertThat(downEvent.isCapsLockOn).isTrue()
        upEvent = getKeyEvent()
        Truth.assertThat(upEvent.keyCode).isEqualTo(KeyEvent.KEYCODE_A)
        Truth.assertThat(upEvent.isCapsLockOn).isTrue()

        tapKey(KeyEvent.KEYCODE_CAPS_LOCK)
    }
}
