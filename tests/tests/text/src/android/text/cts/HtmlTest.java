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

import static org.hamcrest.MatcherAssert.assertThat;
import org.hamcrest.Description;
import org.hamcrest.BaseMatcher;

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
import android.text.style.BulletSpan;
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
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world</p>"));

        s = new SpannableString("Hello world\nor something");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world<br>\nor something</p>"));
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">Hello world</p>\n"
                + "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">or something</p>"));

        s = new SpannableString("Hello world\n\nor something");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world</p>\n<p dir=\"ltr\">or something</p>"));

        s = new SpannableString("Hello world\n\n\nor something");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world<br></p>\n<p dir=\"ltr\">or something</p>"));
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">Hello world</p>\n"
                + "<br>\n"
                + "<br>\n"
                + "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">or something</p>"));
    }

    public void testParagraphStyles() throws Exception {
        SpannableString s = new SpannableString("Hello world");
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, s.length(), Spanned.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0; text-align:center;\">"
                + "Hello world</p>"));

        // Set another AlignmentSpan of a different alignment. Only the last one should be encoded.
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                0, s.length(), Spanned.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0; text-align:end;\">"
                + "Hello world</p>"));

        // Set another AlignmentSpan without SPAN_PARAGRAPH flag.
        // This will be ignored and the previous alignment should be encoded.
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL),
                0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0; text-align:end;\">"
                + "Hello world</p>"));
    }

    public void testBulletSpan() throws Exception {
        SpannableString s = new SpannableString("Bullet1\nBullet2\nNormal paragraph");
        s.setSpan(new BulletSpan(), 0, 8, Spanned.SPAN_PARAGRAPH);
        s.setSpan(new BulletSpan(), 8, 16, Spanned.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<ul style=\"margin-top:0; margin-bottom:0;\">\n"
                + "<li dir=\"ltr\">Bullet1</li>\n"
                + "<li dir=\"ltr\">Bullet2</li>\n"
                + "</ul>\n"
                + "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">Normal paragraph</p>"));
    }

    public void testBlockquote() throws Exception {
        final int start = 0;

        SpannableString s = new SpannableString("Hello world");
        int end = s.length();
        s.setSpan(new QuoteSpan(), start, end, Spannable.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<blockquote><p dir=\"ltr\">Hello world</p>\n</blockquote>"));

        s = new SpannableString("Hello\n\nworld");
        end = 7;
        s.setSpan(new QuoteSpan(), start, end, Spannable.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<blockquote><p dir=\"ltr\">Hello</p>\n</blockquote>\n<p dir=\"ltr\">world</p>"));
    }

    public void testEntities() throws Exception {
        SpannableString s = new SpannableString("Hello <&> world");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello &lt;&amp;&gt; world</p>"));

        s = new SpannableString("Hello \u03D5 world");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello &#981; world</p>"));

        s = new SpannableString("Hello  world");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello&nbsp; world</p>"));
    }

    public void testMarkup() throws Exception {
        final int start = 6;
        SpannableString s = new SpannableString("Hello bold world");
        int end = s.length() - start;
        s.setSpan(new StyleSpan(Typeface.BOLD), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <b>bold</b> world</p>"));

        s = new SpannableString("Hello italic world");
        end = s.length() - start;
        s.setSpan(new StyleSpan(Typeface.ITALIC), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <i>italic</i> world</p>"));

        s = new SpannableString("Hello monospace world");
        end = s.length() - start;
        s.setSpan(new TypefaceSpan("monospace"), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <tt>monospace</tt> world</p>"));

        s = new SpannableString("Hello superscript world");
        end = s.length() - start;
        s.setSpan(new SuperscriptSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <sup>superscript</sup> world</p>"));

        s = new SpannableString("Hello subscript world");
        end = s.length() - start;
        s.setSpan(new SubscriptSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <sub>subscript</sub> world</p>"));

        s = new SpannableString("Hello underline world");
        end = s.length() - start;
        s.setSpan(new UnderlineSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <u>underline</u> world</p>"));

        s = new SpannableString("Hello struck world");
        end = s.length() - start;
        s.setSpan(new StrikethroughSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<span style=\"text-decoration:line-through;\">struck</span> world</p>"));

        s = new SpannableString("Hello linky world");
        end = s.length() - start;
        s.setSpan(new URLSpan("http://www.google.com"), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<a href=\"http://www.google.com\">linky</a> world</p>"));

        s = new SpannableString("Hello foreground world");
        end = s.length() - start;
        s.setSpan(new ForegroundColorSpan(0x00FF00), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<span style=\"color:#00FF00;\">foreground</span> world</p>"));

        s = new SpannableString("Hello background world");
        end = s.length() - start;
        s.setSpan(new BackgroundColorSpan(0x00FF00), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<span style=\"background-color:#00FF00;\">background</span> world</p>"));
    }

    public void testMarkupFromHtml() throws Exception {
        Spanned s;
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

    public void testImg() throws Exception {
        Spanned s = Html.fromHtml("yes<img src=\"http://example.com/foo.gif\">no");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">yes<img src=\"http://example.com/foo.gif\">no</p>"));
    }

    public void testUtf8() throws Exception {
        Spanned s = Html.fromHtml("<p>\u0124\u00eb\u0142\u0142o, world!</p>");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">&#292;&#235;&#322;&#322;o, world!</p>"));
    }

    public void testSurrogates() throws Exception {
        Spanned s = Html.fromHtml("\ud83d\udc31");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">&#128049;</p>"));
    }

    public void testBadSurrogates() throws Exception {
        Spanned s = Html.fromHtml("\udc31\ud83d");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\"></p>"));
    }

    private static StringIgnoringTrailingWhitespaceMatcher matchesIgnoringTrailingWhitespace(
            String expected) {
        return new StringIgnoringTrailingWhitespaceMatcher(expected);
    }

    private static final class StringIgnoringTrailingWhitespaceMatcher extends
            BaseMatcher<String> {
        private final String mStrippedString;

        public StringIgnoringTrailingWhitespaceMatcher(String string) {
            mStrippedString = stripTrailingWhitespace(string);
        }

        @Override
        public boolean matches(Object item) {
            final String string = (String) item;
            return mStrippedString.equals(stripTrailingWhitespace(string));
        }

        private String stripTrailingWhitespace(String text) {
            return text.replaceFirst("\\s+$", "");
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is equal to ")
                    .appendText(mStrippedString)
                    .appendText(" ignoring tailing whitespaces");
        }
    }
}
