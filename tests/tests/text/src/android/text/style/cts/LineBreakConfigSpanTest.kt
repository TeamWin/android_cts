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

package android.text.style.cts

import android.graphics.Paint
import android.graphics.text.LineBreakConfig
import android.graphics.text.MeasuredText
import android.text.MeasuredParagraph
import android.text.SpannableString
import android.text.Spanned
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.LineBreakConfigSpan
import android.text.style.LocaleSpan
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

private const val TEST_STRING = "hello, world."

@RunWith(AndroidJUnit4ClassRunner::class)
class LineBreakConfigSpanTest {

    private data class Run(val length: Int, val lineBreakConfig: LineBreakConfig?, val paint: Paint)

    private val paint = TextPaint().apply {
        textSize = 10f
        textLocale = Locale.US
    }

    private fun buildMeasuredText(
        paint: TextPaint,
        lineBreakConfig: LineBreakConfig?,
        text: CharSequence
    ): List<Run> {
        val res = mutableListOf<Run>()
        MeasuredParagraph.buildForStaticLayoutTest(
                paint, lineBreakConfig, text, 0, text.length, TextDirectionHeuristics.LTR,
                MeasuredText.Builder.HYPHENATION_MODE_NONE,
                false /* computeLayout */, object : MeasuredParagraph.StyleRunCallback {
            override fun onAppendStyleRun(
                    paint: Paint,
                    lineBreakConfig: LineBreakConfig?,
                    length: Int,
                    isRtl: Boolean
            ) {
                res.add(Run(length, lineBreakConfig, Paint(paint)))
            }

            override fun onAppendReplacementRun(paint: Paint, length: Int, width: Float) {
                // Ignore
            }
        })
        return res
    }

    @Test
    fun testLineBreakConfigSpan() {
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(LineBreakConfigSpan(config), 0, TEST_STRING.length,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 1)
        assertEquals(TEST_STRING.length, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
    }

    @Test
    fun testLineBreakConfigSpan_override() {
        // Span overview of this test case
        // |------------------------------------------------|: Test String
        //
        // Spans
        // |------------------------------------------------|: LineBreakSpan(config)
        // |------------------------------------------------|: LineBreakSpan(overrideConfig)
        //
        // Result
        // |------------------------------------------------|:
        //              runs[0] = overridden
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(LineBreakConfigSpan(config), 0, TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            setSpan(LineBreakConfigSpan(overrideConfig), 0, TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 1)
        assertEquals(TEST_STRING.length, runs[0].length)
        assertEquals(overridden, runs[0].lineBreakConfig)
    }

    @Test
    fun testLineBreakConfigSpan_partialOverride() {
        // |------------------------------------------------|: Test String
        //
        // Spans
        // |------------------------------------------------|: LineBreakSpan(config)
        // |-------------------------|                       : LineBreakSpan(overrideConfig)
        //
        // Result
        // |-------------------------|----------------------|:
        //  runs[0] = overridden      run[1] = config
        val middleTextPos = 5
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(LineBreakConfigSpan(config), 0, TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            setSpan(LineBreakConfigSpan(overrideConfig), 0, middleTextPos,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 2)
        assertEquals(middleTextPos, runs[0].length)
        assertEquals(overridden, runs[0].lineBreakConfig)
        assertEquals(TEST_STRING.length - middleTextPos, runs[1].length)
        assertEquals(config, runs[1].lineBreakConfig)
    }

    @Test
    fun testLineBreakConfigSpan_partialOverride2() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |--------------------------------------------------------|: LineBreakSpan(config)
        //                   |--------------------|                  : LineBreakSpan(overrideConfig)
        //
        // Result
        // |-----------------|--------------------|-----------------|:
        //  runs[0] = config  run[1] = overridden  run[2] = config
        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(LineBreakConfigSpan(config), 0, TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            setSpan(LineBreakConfigSpan(overrideConfig), textPos1, textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(overridden, runs[1].lineBreakConfig)
        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(config, runs[2].lineBreakConfig)
    }

    @Test
    fun testLineBreakConfigSpan_partialOverride3() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |--------------------------------------|                  : LineBreakSpan(config)
        //                   |--------------------------------------|: LineBreakSpan(overrideConfig)
        //
        // Result
        // |-----------------|--------------------|-----------------|:
        //  runs[0] = config  run[1] = overridden  run[2] = overrideConfig
        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val overrideConfig = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(LineBreakConfigSpan(config), 0, textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            setSpan(LineBreakConfigSpan(overrideConfig), textPos1, TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val overridden = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)

        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(overridden, runs[1].lineBreakConfig)

        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(overrideConfig, runs[2].lineBreakConfig)
    }

    @Test
    fun testLineBreakConfigSpan_MetricAffectingSpanMixture() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |---------------------------------------------------------|: LineBreakSpan(config)
        //                   |--------------------|                   : LocaleSpan("ja-JP")
        //
        // Result
        // |-----------------|--------------------|------------------|:
        //  runs[0] = config  run[1] = config      run[2] = config
        //  runs[0] = en-US   run[1] = ja-JP       run[2] = en-US

        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(LineBreakConfigSpan(config), 0, TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            setSpan(LocaleSpan(Locale.JAPAN), textPos1, textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
        assertEquals(Locale.US, runs[0].paint.textLocale)

        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(config, runs[1].lineBreakConfig)
        assertEquals(Locale.JAPAN, runs[1].paint.textLocale)

        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(config, runs[2].lineBreakConfig)
        assertEquals(Locale.US, runs[2].paint.textLocale)
    }

    @Test
    fun testLineBreakConfigSpan_MetricAffectingSpanMixture2() {
        // |--------------------------------------------------------|: Test String
        //
        // Spans
        // |--------------------------------------|                   : LineBreakSpan(config)
        //                   |---------------------------------------|: LocaleSpan("ja-JP")
        //
        // Result
        // |-----------------|--------------------|------------------|:
        //  runs[0] = config  run[1] = config      run[2] = none
        //  runs[0] = en-US   run[1] = ja-JP       run[2] = ja-JP

        val textPos1 = 4
        val textPos2 = 7
        val config = LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE)
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                .build()
        val text = SpannableString(TEST_STRING).apply {
            setSpan(LineBreakConfigSpan(config), 0, textPos2,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            setSpan(LocaleSpan(Locale.JAPAN), textPos1, TEST_STRING.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        val runs = buildMeasuredText(paint, null, text)
        assertEquals(runs.size, 3)
        assertEquals(textPos1, runs[0].length)
        assertEquals(config, runs[0].lineBreakConfig)
        assertEquals(Locale.US, runs[0].paint.textLocale)

        assertEquals(textPos2 - textPos1, runs[1].length)
        assertEquals(config, runs[1].lineBreakConfig)
        assertEquals(Locale.JAPAN, runs[1].paint.textLocale)

        assertEquals(TEST_STRING.length - textPos2, runs[2].length)
        assertEquals(LineBreakConfig.Builder().build(), runs[2].lineBreakConfig)
        assertEquals(Locale.JAPAN, runs[2].paint.textLocale)
    }
}
