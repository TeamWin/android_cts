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
package com.android.cts.mockspellchecker

import android.content.ComponentName
import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import com.android.cts.mockspellchecker.MockSpellCheckerProto.MockSpellCheckerConfiguration
import java.io.FileDescriptor
import java.io.PrintWriter

internal inline fun <T> withLog(msg: String, block: () -> T): T {
    Log.i(TAG, msg)
    return block()
}

/** Mock Spell checker for end-to-end tests. */
class MockSpellChecker : SpellCheckerService() {

    override fun onCreate() = withLog("MockSpellChecker.onCreate") {
        super.onCreate()
    }

    override fun onDestroy() = withLog("MockSpellChecker.onDestroy") {
        super.onDestroy()
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter?, args: Array<out String>?) {
        writer?.println("MockSpellChecker")
    }

    override fun createSession(): Session = withLog("MockSpellChecker.createSession") {
        val configuration = MockSpellCheckerConfiguration.parseFrom(
                SharedPrefsProvider.get(contentResolver, KEY_CONFIGURATION))
        return MockSpellCheckerSession(configuration)
    }

    private inner class MockSpellCheckerSession(
        val configuration: MockSpellCheckerConfiguration
    ) : SpellCheckerService.Session() {

        override fun onCreate() = withLog("MockSpellCheckerSession.onCreate") {
        }

        override fun onGetSuggestions(
            textInfo: TextInfo?,
            suggestionsLimit: Int
        ): SuggestionsInfo = withLog(
            "MockSpellCheckerSession.onGetSuggestions: ${textInfo?.text}") {
            if (textInfo == null) return emptySuggestionsInfo()
            return configuration.suggestionRulesList
                    .find { it.match == textInfo.text }
                    ?.let { SuggestionsInfo(it.attributes, it.suggestionsList.toTypedArray()) }
                    ?: emptySuggestionsInfo()
        }

        private fun emptySuggestionsInfo() = SuggestionsInfo(0, arrayOf())
    }

    companion object {
        @JvmStatic
        fun getId(): String =
                ComponentName(PACKAGE, MockSpellChecker::class.java.name).flattenToShortString()
    }
}
