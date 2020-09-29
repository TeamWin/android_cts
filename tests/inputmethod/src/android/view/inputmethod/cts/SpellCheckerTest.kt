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
package android.view.inputmethod.cts

import android.content.Context
import android.provider.Settings
import android.text.style.SuggestionSpan
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.cts.util.EndToEndImeTestBase
import android.view.inputmethod.cts.util.InputMethodVisibilityVerifier
import android.view.inputmethod.cts.util.TestActivity
import android.view.inputmethod.cts.util.TestUtils
import android.view.inputmethod.cts.util.UnlockScreenRule
import android.view.textservice.SpellCheckerSubtype
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextServicesManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.CtsTouchUtils
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.android.cts.mockime.MockImeSession
import com.android.cts.mockspellchecker.MockSpellChecker
import com.android.cts.mockspellchecker.MockSpellCheckerClient
import com.android.cts.mockspellchecker.MockSpellCheckerProto
import com.android.cts.mockspellchecker.MockSpellCheckerProto.MockSpellCheckerConfiguration
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

private val TIMEOUT = TimeUnit.SECONDS.toMillis(5)

@MediumTest
@RunWith(AndroidJUnit4::class)
class SpellCheckerTest : EndToEndImeTestBase() {

    private val context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()

    @Rule
    fun unlockScreenRule() = UnlockScreenRule()

    @Rule
    fun spellCheckerSettingsRule() = SettingsStateChangerRule(
            context, Settings.Secure.SELECTED_SPELL_CHECKER, MockSpellChecker.getId())

    @Rule
    fun spellCheckerSubtypeSettingsRule() = SettingsStateChangerRule(
            context, Settings.Secure.SELECTED_SPELL_CHECKER_SUBTYPE,
            SpellCheckerSubtype.SUBTYPE_ID_NONE.toString())

    @Before
    fun setUp() {
        val tsm = context.getSystemService(TextServicesManager::class.java)!!
        // Skip if spell checker is not enabled by default.
        Assume.assumeNotNull(tsm)
        Assume.assumeTrue(tsm.isSpellCheckerEnabled)
    }

    @Test
    fun test() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use {
                val (_, editText) = startTestActivity()
                CtsTouchUtils.emulateTapOnViewCenter(
                        InstrumentationRegistry.getInstrumentation(), null, editText)
                TestUtils.waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                TestUtils.waitOnMainUntil({ hasSuggestionSpan(editText) }, TIMEOUT)
            }
        }
    }

    private fun hasSuggestionSpan(editText: EditText): Boolean {
        val editable = editText.text
        val spans = editable.getSpans(0, editable.length, SuggestionSpan::class.java)
        return spans != null && spans.isNotEmpty()
    }

    private fun startTestActivity(): Pair<TestActivity, EditText> {
        var editText: EditText? = null
        val activity = TestActivity.startSync { activity: TestActivity? ->
            val layout = LinearLayout(activity)
            editText = EditText(activity)
            layout.addView(editText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            layout
        }
        return Pair(activity, editText!!)
    }
}