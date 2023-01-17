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

import android.app.Instrumentation
import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import com.android.cts.input.UinputDevice
import java.io.BufferedReader
import java.io.Closeable
import org.json.JSONArray
import org.json.JSONObject

class UinputKeyboard(instrumentation: Instrumentation) : Closeable {
    private val uinputDevice: UinputDevice
    lateinit var keylayout: Map<Int, Int>

    init {
        uinputDevice = createDevice(instrumentation)
    }

    fun injectKey(keyCode: Int, isDown: Boolean) {
        val scanCode: Int = keylayout[keyCode]!!
        if (isDown) {
            val evdevEventsDown = intArrayOf(EV_KEY, scanCode, EV_KEY_DOWN, EV_SYN, SYN_REPORT, 0)
            uinputDevice.injectEvents(evdevEventsDown.joinToString(prefix = "[", postfix = "]",
                    separator = ","))
        } else {
            val evdevEventsUp = intArrayOf(EV_KEY, scanCode, EV_KEY_UP, EV_SYN, SYN_REPORT, 0)
            uinputDevice.injectEvents(evdevEventsUp.joinToString(prefix = "[", postfix = "]",
                    separator = ","))
        }
    }

    private fun readRawResource(context: Context, id: Int): BufferedReader {
        return context.resources
                .openRawResource(id)
                .bufferedReader()
    }

    private fun createDevice(instrumentation: Instrumentation): UinputDevice {
        val reader = readRawResource(instrumentation.targetContext, R.raw.test_keyboard_register)
        val json = JSONObject(reader.readText())

        val resourceDeviceId: Int = json.getInt("id")
        val vendorId = json.getInt("vid")
        val productId = json.getInt("pid")
        keylayout = loadKeyLayout(instrumentation)

        val configuration: JSONArray = json.getJSONArray("configuration")

        val arraySetEvBit = JSONArray()
        arraySetEvBit.put(EV_KEY)

        // Configure device have all keys from key layout map.
        val arraySetKeyBit = JSONArray()
        for (key in keylayout.values) {
            arraySetKeyBit.put(key)
        }

        for (i in 0 until configuration.length()) {
            val item = configuration.getJSONObject(i)
            if (item.get("type") == UI_SET_EVBIT) {
                item.put("data", arraySetEvBit)
            }
            if (item.get("type") == UI_SET_KEYBIT) {
                item.put("data", arraySetKeyBit)
            }
        }

        val registerCommand = json.toString()
        return UinputDevice(instrumentation, resourceDeviceId,
                vendorId, productId, InputDevice.SOURCE_KEYBOARD, registerCommand)
    }

    private fun loadKeyLayout(instrumentation: Instrumentation): Map<Int, Int> {
        val reader = readRawResource(instrumentation.targetContext, R.raw.test_keyboard)
        val keyLayout = emptyMap<Int, Int>().toMutableMap()
        for (line in reader.lines()) {
            // space separator
            val items: List<String> = line.split("\\s+".toRegex())

            // Just load [type, code, label] mappings.
            if (items.size != 3) {
                continue
            }

            // Skip non-key mappings.
            if (items[0] != "key") {
                continue
            }

            // Skip HID usage keys.
            if (items[1] == "usage") {
                continue
            }

            // Skip non-keycode value.
            val keyCode = KeyEvent.keyCodeFromString(items[2])
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                continue
            }

            keyLayout[keyCode] = items[1].toInt()
        }

        reader.close()
        return keyLayout
    }

    override fun close() {
        uinputDevice.close()
    }

    companion object {
        private const val EV_SYN = 0
        private const val SYN_REPORT = 0
        private const val EV_KEY = 1
        private const val EV_KEY_DOWN = 1
        private const val EV_KEY_UP = 0
        private const val UI_SET_EVBIT = 100
        private const val UI_SET_KEYBIT = 101
    }
}
