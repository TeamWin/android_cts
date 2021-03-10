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

import android.app.Instrumentation
import android.content.Context
import android.provider.Settings
import android.text.style.SuggestionSpan
import android.text.style.SuggestionSpan.FLAG_GRAMMAR_ERROR
import android.text.style.SuggestionSpan.FLAG_MISSPELLED
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.cts.util.EndToEndImeTestBase
import android.view.inputmethod.cts.util.InputMethodVisibilityVerifier
import android.view.inputmethod.cts.util.TestActivity
import android.view.inputmethod.cts.util.TestUtils.runOnMainSync
import android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil
import android.view.inputmethod.cts.util.UnlockScreenRule
import android.view.textservice.SpellCheckerSubtype
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
import android.view.textservice.TextServicesManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.UiThread
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.CtsTouchUtils
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand
import com.android.cts.mockime.MockImeSession
import com.android.cts.mockspellchecker.MockSpellChecker
import com.android.cts.mockspellchecker.MockSpellCheckerClient
import com.android.cts.mockspellchecker.MockSpellCheckerProto
import com.android.cts.mockspellchecker.MockSpellCheckerProto.MockSpellCheckerConfiguration
import com.google.common.truth.Truth.assertThat
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

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.getTargetContext()
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)

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
    fun misspelled_easyCorrect() {
        val uniqueSuggestion = "s618397" // "s" + a random number
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions(uniqueSuggestion)
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use {
                val (_, editText) = startTestActivity()
                CtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) != null
                }, TIMEOUT)
                // Tap inside 'match'.
                emulateTapAtOffset(editText, 2)
                // Wait until the cursor moves inside 'match'.
                waitOnMainUntil({ isCursorInside(editText, 1, 4) }, TIMEOUT)
                // Wait for the suggestion to come up, and click it.
                uiDevice.wait(Until.findObject(By.text(uniqueSuggestion)), TIMEOUT).also {
                    assertThat(it).isNotNull()
                }.click()
                // Verify that the text ('match') is replaced with the suggestion.
                waitOnMainUntil({ "$uniqueSuggestion " == editText.text.toString() }, TIMEOUT)
                // The SuggestionSpan should be removed.
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) == null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun misspelled_noEasyCorrect() {
        val uniqueSuggestion = "s974355" // "s" + a random number
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions(uniqueSuggestion)
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO
                                        or RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use {
                val (_, editText) = startTestActivity()
                CtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) != null
                }, TIMEOUT)
                // Tap inside 'match'.
                emulateTapAtOffset(editText, 2)
                // Wait until the cursor moves inside 'match'.
                waitOnMainUntil({ isCursorInside(editText, 1, 4) }, TIMEOUT)
                // Verify that the suggestion is not shown.
                assertThat(uiDevice.wait(Until.gone(By.text(uniqueSuggestion)), TIMEOUT)).isTrue()
            }
        }
    }

    @Test
    fun grammarError() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR)
        ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use {
                val (_, editText) = startTestActivity()
                CtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_GRAMMAR_ERROR) != null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun performSpellCheck() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use { client ->
                val stream = session.openEventStream()
                val (_, editText) = startTestActivity()
                CtsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) != null
                }, TIMEOUT)
                // The word is now in dictionary. The next spell check should remove the misspelled
                // SuggestionSpan.
                client.updateConfiguration(MockSpellCheckerConfiguration.newBuilder()
                        .addSuggestionRules(
                                MockSpellCheckerProto.SuggestionRule.newBuilder()
                                        .setMatch("match")
                                        .setAttributes(RESULT_ATTR_IN_THE_DICTIONARY)
                        ).build())
                val command = session.callPerformSpellCheck()
                expectCommand(stream, command, TIMEOUT)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) == null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun textServicesManagerApi() {
        val tsm = context.getSystemService(TextServicesManager::class.java)!!
        assertThat(tsm).isNotNull()
        assertThat(tsm!!.isSpellCheckerEnabled()).isTrue()
        val spellCheckerInfo = tsm.getCurrentSpellCheckerInfo()
        assertThat(spellCheckerInfo).isNotNull()
        assertThat(spellCheckerInfo!!.getPackageName()).isEqualTo(
            "com.android.cts.mockspellchecker")
        assertThat(spellCheckerInfo!!.getSubtypeCount()).isEqualTo(1)
        assertThat(tsm.getEnabledSpellCheckerInfos()!!.size).isAtLeast(1)
        assertThat(tsm.getEnabledSpellCheckerInfos()!!.map { it.getPackageName() })
                        .contains("com.android.cts.mockspellchecker")
    }

    private fun findSuggestionSpanWithFlags(editText: EditText, flags: Int): SuggestionSpan? =
            getSuggestionSpans(editText).find { (it.flags and flags) == flags }

    private fun getSuggestionSpans(editText: EditText): Array<SuggestionSpan> {
        val editable = editText.text
        val spans = editable.getSpans(0, editable.length, SuggestionSpan::class.java)
        return spans
    }

    private fun emulateTapAtOffset(editText: EditText, offset: Int) {
        var x = 0
        var y = 0
        runOnMainSync {
            x = editText.layout.getPrimaryHorizontal(offset).toInt()
            val line = editText.layout.getLineForOffset(offset)
            y = (editText.layout.getLineTop(line) + editText.layout.getLineBottom(line)) / 2
        }
        CtsTouchUtils.emulateTapOnView(instrumentation, null, editText, x, y)
    }

    @UiThread
    private fun isCursorInside(editText: EditText, start: Int, end: Int): Boolean =
            start <= editText.selectionStart && editText.selectionEnd <= end

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