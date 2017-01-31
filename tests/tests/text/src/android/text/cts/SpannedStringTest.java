/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.NoCopySpan;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.QuoteSpan;
import android.text.style.UnderlineSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SpannedStringTest {
    @Test
    public void testConstructor() {
        new SpannedString("test");
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNull() {
        new SpannedString(null);
    }

    @Test
    public void testValueOf() {
        String text = "test valueOf";
        SpannedString spanned = SpannedString.valueOf(text);
        assertEquals(text, spanned.toString());

        spanned = new SpannedString(text);
        assertSame(spanned, SpannedString.valueOf(spanned));
    }

    @Test(expected=NullPointerException.class)
    public void testValueOfNull() {
        SpannedString.valueOf(null);
    }

    @Test
    public void testSubSequence() {
        String text = "hello, world";
        SpannedString spanned = new SpannedString(text);

        CharSequence subSequence = spanned.subSequence(0, 2);
        assertTrue(subSequence instanceof SpannedString);
        assertEquals("he", subSequence.toString());

        subSequence = spanned.subSequence(0, text.length());
        assertTrue(subSequence instanceof SpannedString);
        assertEquals(text, subSequence.toString());

        try {
            spanned.subSequence(-1, text.length() + 1);
            fail("subSequence failed when index is out of bounds");
        } catch (StringIndexOutOfBoundsException e) {
        }

        try {
            spanned.subSequence(2, 0);
            fail("subSequence failed on invalid index");
        } catch (StringIndexOutOfBoundsException e) {
        }
    }

    @Test
    public void testCopyConstructor_doesNotCopyNoCopySpans() {
        final SpannableString first = new SpannableString("t\nest data");
        first.setSpan(new QuoteSpan(), 0, 2, Spanned.SPAN_PARAGRAPH);
        first.setSpan(new NoCopySpan.Concrete(), 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        first.setSpan(new UnderlineSpan(), 0, first.length(), Spanned.SPAN_PRIORITY);

        final SpannedString copied = new SpannedString(first);
        final Object[] spans = copied.getSpans(0, copied.length(), Object.class);
        assertNotNull(spans);
        assertEquals(2, spans.length);

        for (int i = 0; i < spans.length; i++) {
            assertFalse(spans[i] instanceof NoCopySpan);
        }
    }

}
