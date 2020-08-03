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

package android.systemui.cts.tv.pip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.systemui.tv.cts.KeyboardActivity.ACTION_HIDE_KEYBOARD
import android.systemui.tv.cts.KeyboardActivity.ACTION_SHOW_KEYBOARD
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** A trivial activity with a single text input field. */
class KeyboardActivity : Activity() {
    private lateinit var inputMethodManager: InputMethodManager
    private lateinit var textInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.keyboard_layout)

        textInput = findViewById(R.id.plain_text_input)
            ?: error("Could not find the plain_text_input element!")

        inputMethodManager = applicationContext.getSystemService(InputMethodManager::class.java)
            ?: error("Could not get an InputMethodManager")

        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) = handle(intent)

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_KEYBOARD -> {
                inputMethodManager.showSoftInput(textInput, 0)
            }
            ACTION_HIDE_KEYBOARD -> {
                inputMethodManager.hideSoftInputFromWindow(textInput.windowToken, 0)
            }
        }
    }
}