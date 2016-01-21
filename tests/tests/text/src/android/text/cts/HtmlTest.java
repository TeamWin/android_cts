/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.text.cts;

import android.graphics.Typeface;
import android.test.AndroidTestCase;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Html.ImageGetter;
import android.text.Html.TagHandler;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

public class HtmlTest extends AndroidTestCase {
    private final static int SPAN_EXCLUSIVE_INCLUSIVE = Spannable.SPAN_EXCLUSIVE_INCLUSIVE;

    public void testSingleTagOnWhileString() {
        final String source = "<b>hello</b>";

        Spanned spanned = Html.fromHtml(source);
        assertSingleTagOnWhileString(spanned);
        spanned = Html.fromHtml(source, null, null);
        assertSingleTagOnWhileString(spanned);
    }

    private void assertSingleTagOnWhileString(Spanned spanned) {
        final int expectStart = 0;
        final int expectEnd = 5;
        final int expectLen = 1;
        final int start = -1;
        final int end = 100;

        Object[] spans = spanned.getSpans(start, end, Object.class);
        assertEquals(expectLen, spans.length);
        assertEquals(expectStart, spanned.getSpanStart(spans[0]));
        assertEquals(expectEnd, spanned.getSpanEnd(spans[0]));
    }

    public void testBadHtml() {
        final String source = "Hello <b>b<i>bi</b>i</i>";

        Spanned spanned = Html.fromHtml(source);
        assertBadHtml(spanned);
        spanned = Html.fromHtml(source, null, null);
        assertBadHtml(spanned);
    }

    private void assertBadHtml(Spanned spanned) {
        final int start = 0;
        final int end = 100;
        final int spansLen = 3;

        Object[] spans = spanned.getSpans(start, end, Object.class);
        assertEquals(spansLen, spans.length);
    }

    public void testSymbols() {
        final String source = "&copy; &gt; &lt";
        final String expected = "\u00a9 > <";

        String spanned = Html.fromHtml(source).toString();
        assertEquals(expected, spanned);
        spanned = Html.fromHtml(source, null, null).toString();
        assertEquals(expected, spanned);
    }

    public void testColor() throws Exception {
        final int start = 0;

        Class<ForegroundColorSpan> type = ForegroundColorSpan.class;
        ForegroundColorSpan[] colors;
        Spanned s = Html.fromHtml("<font color=\"#00FF00\">something</font>");
        int end = s.length();
        colors = s.getSpans(start, end, type);
        int expectedColor = 0xFF00FF00;
        assertEquals(expectedColor, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"navy\">something</font>");
        end = s.length();
        colors = s.getSpans(start, end, type);
        expectedColor = 0xFF000080;
        assertEquals(0xFF000080, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"gibberish\">something</font>");
        end = s.length();
        colors = s.getSpans(start, end, type);
        assertEquals(0, colors.length);
    }

    public void testParagraphs() throws Exception {
        SpannableString s = new SpannableString("Hello world");
        assertEquals("<p dir=\"ltr\">Hello world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello world\nor something");
        assertEquals("<p dir=\"ltr\">Hello world<br>\nor something</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello world\n\nor something");
        assertEquals("<p dir=\"ltr\">Hello world</p>\n<p dir=\"ltr\">or something</p>\n",
                Html.toHtml(s));

        s = new SpannableString("Hello world\n\n\nor something");
        assertEquals("<p dir=\"ltr\">Hello world<br></p>\n<p dir=\"ltr\">or something</p>\n",
                Html.toHtml(s));
    }

    public void testBlockquote() throws Exception {
        final int start = 0;

        SpannableString s = new SpannableString("Hello world");
        int end = s.length();
        s.setSpan(new QuoteSpan(), start, end, Spannable.SPAN_PARAGRAPH);
        assertEquals("<blockquote><p dir=\"ltr\">Hello world</p>\n</blockquote>\n", Html.toHtml(s));

        s = new SpannableString("Hello\n\nworld");
        end = 7;
        s.setSpan(new QuoteSpan(), start, end, Spannable.SPAN_PARAGRAPH);
        assertEquals("<blockquote><p dir=\"ltr\">Hello</p>\n</blockquote>\n" +
        		"<p dir=\"ltr\">world</p>\n", Html.toHtml(s));
    }

    public void testEntities() throws Exception {
        SpannableString s = new SpannableString("Hello <&> world");
        assertEquals("<p dir=\"ltr\">Hello &lt;&amp;&gt; world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello \u03D5 world");
        assertEquals("<p dir=\"ltr\">Hello &#981; world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello  world");
        assertEquals("<p dir=\"ltr\">Hello&nbsp; world</p>\n", Html.toHtml(s));
    }

    public void testMarkup() throws Exception {
        final int start = 6;

        SpannableString s = new SpannableString("Hello bold world");
        int end = s.length() - start;
        s.setSpan(new StyleSpan(Typeface.BOLD), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello <b>bold</b> world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello italic world");
        end = s.length() - start;
        s.setSpan(new StyleSpan(Typeface.ITALIC), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello <i>italic</i> world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello monospace world");
        end = s.length() - start;
        s.setSpan(new TypefaceSpan("monospace"), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello <tt>monospace</tt> world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello superscript world");
        end = s.length() - start;
        s.setSpan(new SuperscriptSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello <sup>superscript</sup> world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello subscript world");
        end = s.length() - start;
        s.setSpan(new SubscriptSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello <sub>subscript</sub> world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello underline world");
        end = s.length() - start;
        s.setSpan(new UnderlineSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello <u>underline</u> world</p>\n", Html.toHtml(s));

        s = new SpannableString("Hello struck world");
        end = s.length() - start;
        s.setSpan(new StrikethroughSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello "
                + "<span style=\"text-decoration:line-through;\">struck</span> world</p>\n",
                Html.toHtml(s));

        s = new SpannableString("Hello linky world");
        end = s.length() - start;
        s.setSpan(new URLSpan("http://www.google.com"), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello "
                + "<a href=\"http://www.google.com\">linky</a> world</p>\n",
                Html.toHtml(s));

        s = new SpannableString("Hello foreground world");
        end = s.length() - start;
        s.setSpan(new ForegroundColorSpan(0x00FF00), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello "
                + "<span style=\"color:#00FF00;\">foreground</span> world</p>\n",
                Html.toHtml(s));

        s = new SpannableString("Hello background world");
        end = s.length() - start;
        s.setSpan(new BackgroundColorSpan(0x00FF00), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<p dir=\"ltr\">Hello "
                + "<span style=\"background-color:#00FF00;\">background</span> world</p>\n",
                Html.toHtml(s));
    }

    public void testMarkupFromHtml() throws Exception {
        final int expectedStart = 6;
        final int expectedEnd = expectedStart + 6;

        String tags[] = {"del", "s", "strike"};
        for (String tag : tags) {
            String source = String.format("Hello <%s>struck</%s> world", tag, tag);
            Spanned spanned = Html.fromHtml(source);
            Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
            assertEquals(1, spans.length);
            assertEquals(StrikethroughSpan.class, spans[0].getClass());
            assertEquals(expectedStart, spanned.getSpanStart(spans[0]));
            assertEquals(expectedEnd, spanned.getSpanEnd(spans[0]));
        }
    }

    /**
     * Tests if text alignments encoded as CSS TEXT-ALIGN property are correctly converted into
     * {@link AlignmentSpan}s. Note that the span will also cover the first newline character after
     * the text.
     */
    public void testTextAlignCssFromHtml() throws Exception {
        String tags[] = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "div", "blockquote"};

        for (String tag : tags) {
            String source = String.format("<%s style=\"text-align:start\">TEXT</%s>"
                    + "<%s style=\"text-align:center\">TEXT</%s>"
                    + "<%s style=\"text-align:end\">TEXT</%s>",
                    tag, tag, tag, tag, tag, tag);
            Spanned spanned = Html.fromHtml(source);
            AlignmentSpan[] spans = spanned.getSpans(0, spanned.length(), AlignmentSpan.class);
            assertEquals(3, spans.length);

            assertEquals(Layout.Alignment.ALIGN_NORMAL, spans[0].getAlignment());
            assertEquals(0, spanned.getSpanStart(spans[0]));
            assertEquals(5, spanned.getSpanEnd(spans[0]));

            assertEquals(Layout.Alignment.ALIGN_CENTER, spans[1].getAlignment());
            assertEquals(6, spanned.getSpanStart(spans[1]));
            assertEquals(11, spanned.getSpanEnd(spans[1]));

            assertEquals(Layout.Alignment.ALIGN_OPPOSITE, spans[2].getAlignment());
            assertEquals(12, spanned.getSpanStart(spans[2]));
            assertEquals(17, spanned.getSpanEnd(spans[2]));

            // Other valid TEXT-ALIGN property encodings
            source = String.format("<%s style=\'text-align:center\''>TEXT</%s>"
                    + "<%s style=\"text-align:center;\">TEXT</%s>"
                    + "<%s style=\"text-align  :  center  ;  \">TEXT</%s>",
                    tag, tag, tag, tag, tag, tag);
            spanned = Html.fromHtml(source);
            spans = spanned.getSpans(0, spanned.length(), AlignmentSpan.class);
            assertEquals(3, spans.length);

            // Invalid TEXT-ALIGN property encodings
            source = String.format("<%s style=\"text-align:centre\">TEXT</%s>"
                    + "<%s style=\"text-alignment:center\">TEXT</%s>"
                    + "<%s style=\"align:center\">TEXT</%s>"
                    + "<%s style=\"text-align:gibberish\">TEXT</%s>",
                    tag, tag, tag, tag, tag, tag, tag, tag);
            spanned = Html.fromHtml(source);
            spans = spanned.getSpans(0, spanned.length(), AlignmentSpan.class);
            assertEquals(0, spans.length);
        }
    }

    public void testBlockLevelElementsFromHtml() throws Exception {
        String source = "<blockquote>BLOCKQUOTE</blockquote>"
                + "<div>DIV</div>"
                + "<p>P</p>"
                + "<h1>HEADING</h1>";

        int flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE
                | Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV;
        assertEquals("BLOCKQUOTE\nDIV\n\nP\n\nHEADING\n\n",
                Html.fromHtml(source, flags, null, null).toString());

        flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV
                | Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH;
        assertEquals("BLOCKQUOTE\n\nDIV\nP\n\nHEADING\n\n",
                Html.fromHtml(source, flags, null, null).toString());

        flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
                | Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING;
        assertEquals("BLOCKQUOTE\n\nDIV\n\nP\nHEADING\n",
                Html.fromHtml(source, flags, null, null).toString());
    }

    public void testParagraphFromHtml() throws Exception {
        final int flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH;

        String source = "<p>Line 1</p><p>Line 2</p>";
        assertEquals("Line 1\nLine 2\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Line 1\n\nLine 2\n\n",
                Html.fromHtml(source).toString());

        source = "<br>Line 1<br>Line 2<br>";
        assertEquals("\nLine 1\nLine 2\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("\nLine 1\nLine 2\n",
                Html.fromHtml(source).toString());

        source = "<br><p>Line 1</p><br><p>Line 2</p><br>";
        assertEquals("\nLine 1\n\nLine 2\n\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("\n\nLine 1\n\n\nLine 2\n\n\n",
                Html.fromHtml(source).toString());

        source = "<p>Line 1<br>Line 2</p><p>Line 3</p>";
        assertEquals("Line 1\nLine 2\nLine 3\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Line 1\nLine 2\n\nLine 3\n\n",
                Html.fromHtml(source).toString());
    }

    public void testHeadingFromHtml() throws Exception {
        final int flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING;

        String source = "<h1>Heading 1</h1><h1>Heading 2</h1>";
        assertEquals("Heading 1\nHeading 2\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Heading 1\n\nHeading 2\n\n",
                Html.fromHtml(source).toString());

        source = "<br><h1>Heading 1</h1><br><h1>Heading 2</h1><br>";
        assertEquals("\nHeading 1\n\nHeading 2\n\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("\n\nHeading 1\n\n\nHeading 2\n\n\n",
                Html.fromHtml(source).toString());

        source = "<h1>Heading 1<br>Heading 2</h1><h1>Heading 3</h1>";
        assertEquals("Heading 1\nHeading 2\nHeading 3\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Heading 1\nHeading 2\n\nHeading 3\n\n",
                Html.fromHtml(source).toString());
    }

    public void testImg() throws Exception {
        Spanned s = Html.fromHtml("yes<img src=\"http://example.com/foo.gif\">no");
        assertEquals("<p dir=\"ltr\">yes<img src=\"http://example.com/foo.gif\">no</p>\n",
                Html.toHtml(s));
    }

    public void testUtf8() throws Exception {
        Spanned s = Html.fromHtml("<p>\u0124\u00eb\u0142\u0142o, world!</p>");
        assertEquals("<p dir=\"ltr\">&#292;&#235;&#322;&#322;o, world!</p>\n", Html.toHtml(s));
    }

    public void testSurrogates() throws Exception {
        Spanned s = Html.fromHtml("\ud83d\udc31");
        assertEquals("<p dir=\"ltr\">&#128049;</p>\n", Html.toHtml(s));
    }

    public void testBadSurrogates() throws Exception {
        Spanned s = Html.fromHtml("\udc31\ud83d");
        assertEquals("<p dir=\"ltr\"></p>\n", Html.toHtml(s));
    }
}
