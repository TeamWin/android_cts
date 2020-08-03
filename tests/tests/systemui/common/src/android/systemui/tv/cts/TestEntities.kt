/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.systemui.tv.cts

import android.content.ComponentName

private const val pkg = "android.systemui.cts.tv.pip"

object Components {

    @JvmStatic
    fun ComponentName.activityName(): String = flattenToShortString()

    @JvmStatic
    fun ComponentName.windowName(): String = flattenToString()

    @JvmField
    val PIP_ACTIVITY: ComponentName = ComponentName.createRelative(pkg, ".PipTestActivity")

    @JvmField
    val PIP_MENU_ACTIVITY: ComponentName = ComponentName.createRelative(
        ResourceNames.SYSTEM_UI_PACKAGE,
            ".pip.tv.PipMenuActivity"
    )

    @JvmField
    val KEYBOARD_ACTIVITY: ComponentName = ComponentName.createRelative(pkg, ".KeyboardActivity")
}

object PipActivity {
    /** Instruct the app to go into pip mode */
    const val ACTION_ENTER_PIP = "$pkg.PipTestActivity.enter_pip"

    /** Instruct the app to go into pip mode when set to true */
    const val EXTRA_ENTER_PIP = "enter_pip"

    /** Provide a rect hint for entering pip in the form "left top right bottom" */
    const val EXTRA_SOURCE_RECT_HINT = "source_rect_hint"

    /**
     * Boolean.
     * Make sure the app will turn on the screen (waking up the device) upon start.
     * This is accomplished by means of
     * https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean)
     */
    const val EXTRA_TURN_ON_SCREEN = "turn_on_screen"

    const val EXTRA_ASPECT_RATIO_DENOMINATOR = "aspect_ratio_denominator"
    const val EXTRA_ASPECT_RATIO_NUMERATOR = "aspect_ratio_numerator"
}

object KeyboardActivity {
    const val ACTION_SHOW_KEYBOARD = "$pkg.KeyboardActivity.show_keyboard"
    const val ACTION_HIDE_KEYBOARD = "$pkg.KeyboardActivity.hide_keyboard"
}

object PipMenu {
    const val ACTION_MENU = "PipNotification.menu"
    const val ACTION_CLOSE = "PipNotification.close"
}

object ResourceNames {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    const val STRING_PIP_MENU_BOUNDS = "pip_menu_bounds"

    const val ID_PIP_MENU_CLOSE_BUTTON = "$SYSTEM_UI_PACKAGE:id/close_button"
    const val ID_PIP_MENU_FULLSCREEN_BUTTON = "$SYSTEM_UI_PACKAGE:id/full_button"
}