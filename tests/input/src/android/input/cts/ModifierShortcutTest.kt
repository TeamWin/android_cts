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

package android.input.cts

import android.content.Intent
import android.server.wm.UiDeviceUtils
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for modifier key shortcut.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ModifierShortcutTest : SystemShortcutTestBase() {
/*
    Typical shortcuts (defined in bookmarks.xml):
    'a': Calculator
    'b': Browser
    'c': Contacts
    'e': Email
    'l': Calendar
    'm': Maps
    'p': Music
    's': SMS
*/
    val BOOKMARKS = mapOf("a" to "android.intent.category.APP_CALCULATOR",
        "b" to "android.intent.category.APP_BROWSER",
        "c" to "android.intent.category.APP_CONTACTS",
        "e" to "android.intent.category.APP_EMAIL",
        "l" to "android.intent.category.APP_CALENDAR",
        "m" to "android.intent.category.APP_MAPS",
        "p" to "android.intent.category.APP_MUSIC",
        "s" to "android.intent.category.APP_MESSAGING")

    @Test
    fun testBookmarksShortcuts() {
        for ((key, value) in BOOKMARKS) {
            val keyCode = KeyEvent.keyCodeFromString(key.toUpperCase())
            Assert.assertNotEquals(keyCode, KeyEvent.KEYCODE_UNKNOWN)

            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, value)
            val component = intent.resolveActivity(activity.packageManager)
            val keyCodes = intArrayOf(KeyEvent.KEYCODE_META_LEFT, keyCode)
            sendKeyCombination(keyCodes, 0)
            waitForReady(component.flattenToString())
            UiDeviceUtils.pressBackButton()
        }
    }
}
