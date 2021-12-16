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

import androidx.test.uiautomator.By
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

class CompanionDeviceManagerUi(private val ui: UiDevice) {
    val isVisible: Boolean
        get() = ui.hasObject(CONFIRMATION_UI)

    fun dismiss() {
        if (!isVisible) return
        // Pressing back button should close (cancel) confirmation UI.
        ui.pressBack()
        waitUntilGone()
    }

    fun waitUntilVisible() = ui.wait(Until.hasObject(CONFIRMATION_UI), "CDM UI has not appeared.")

    fun waitUntilGone() = ui.waitShort(Until.gone(CONFIRMATION_UI), "CDM UI has not disappeared")

    fun waitAndClickOnFirstFoundDevice() = ui.waitLongAndFind(
            Until.findObject(DEVICE_LIST_WITH_ITEMS), "Device List not found or empty")
                    .children[0].click()

    fun clickNegativeButton() = ui.waitShortAndFind(
            Until.findObject(NEGATIVE_BUTTON), "Negative button is not found")
                    .click()

    companion object {
        private const val PACKAGE_NAME = "com.android.companiondevicemanager"

        private val CONFIRMATION_UI = By.pkg(PACKAGE_NAME)
                .res(PACKAGE_NAME, "activity_confirmation")

        private val BUTTON = By.pkg(PACKAGE_NAME).clazz(".Button")
        private val POSITIVE_BUTTON = By.copy(BUTTON).res(PACKAGE_NAME, "btn_positive")
        private val NEGATIVE_BUTTON = By.copy(BUTTON).res(PACKAGE_NAME, "btn_negative")

        private val DEVICE_LIST = By.pkg(PACKAGE_NAME).clazz(".ListView")
                .res(PACKAGE_NAME, "device_list")
        private val DEVICE_LIST_ITEM = By.pkg(PACKAGE_NAME)
                .res(PACKAGE_NAME, "list_item_device")
        private val DEVICE_LIST_WITH_ITEMS = By.copy(DEVICE_LIST)
                .hasChild(DEVICE_LIST_ITEM)
    }

    private fun UiDevice.wait(
        condition: SearchCondition<Boolean>,
        message: String,
        timeout: Long = 3_000
    ) {
        if (!wait(condition, timeout)) error(message)
    }

    private fun UiDevice.waitShort(condition: SearchCondition<Boolean>, message: String) =
            wait(condition, message, 1_000)

    private fun UiDevice.waitAndFind(
        condition: SearchCondition<UiObject2>,
        message: String,
        timeout: Long = 3_000
    ): UiObject2 =
            wait(condition, timeout) ?: error(message)

    private fun UiDevice.waitShortAndFind(
        condition: SearchCondition<UiObject2>,
        message: String
    ): UiObject2 = waitAndFind(condition, message, 1_000)

    private fun UiDevice.waitLongAndFind(
        condition: SearchCondition<UiObject2>,
        message: String
    ): UiObject2 = waitAndFind(condition, message, 10_000)
}