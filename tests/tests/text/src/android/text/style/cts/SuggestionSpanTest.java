/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.text.style.cts;

import junit.framework.TestCase;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.style.SuggestionSpan;

import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;

public class SuggestionSpanTest extends TestCase {

    @SmallTest
    public void testGetSuggestionSpans() {
        final String[] suggestions = new String[]{"suggestion1", "suggestion2"};
        final SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        assertArrayEquals("Should return the correct suggestions array",
                suggestions, span.getSuggestions());

        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertArrayEquals("Should (de)serialize suggestions",
                suggestions, clonedSpan.getSuggestions());
    }

    @SmallTest
    public void testGetSuggestionSpans_emptySuggestions() {
        final String[] suggestions = new String[0];
        final SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        assertArrayEquals("Span should return empty suggestion array",
                suggestions, span.getSuggestions());

        // also test parceling
        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertArrayEquals("Should (de)serialize empty suggestions array",
                suggestions, clonedSpan.getSuggestions());
    }

    @SmallTest
    public void testGetSuggestionSpans_suggestionsWithNullValue() {
        final String[] suggestions = new String[]{"suggestion", null};
        final SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        assertArrayEquals("Should accept and return null suggestions",
                suggestions, span.getSuggestions());

        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertArrayEquals("Should (de)serialize null in suggestions array",
                suggestions, clonedSpan.getSuggestions());
    }

    @SmallTest
    public void testGetFlags() {
        final String[] anySuggestions = new String[0];
        final int flag = SuggestionSpan.FLAG_AUTO_CORRECTION;
        SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), anySuggestions, flag);

        assertEquals("Should return the flag passed in constructor",
                flag, span.getFlags());

        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertEquals("Should (de)serialize flags", flag, clonedSpan.getFlags());
    }

    @SmallTest
    public void testEquals_returnsTrueForDeserializedInstances() {
        final SuggestionSpan span1 = new SuggestionSpan(null, Locale.forLanguageTag("en"),
                new String[0], SuggestionSpan.FLAG_AUTO_CORRECTION, SuggestionSpan.class);
        final SuggestionSpan span2 = cloneViaParcel(span1);

        assertTrue("(De)serialized instances should be equal", span1.equals(span2));
    }

    @SmallTest
    public void testEquals_returnsTrueIfTheFlagsAreDifferent() {
        final SuggestionSpan span1 = new SuggestionSpan(null, Locale.forLanguageTag("en"),
                new String[0], SuggestionSpan.FLAG_AUTO_CORRECTION, SuggestionSpan.class);
        final SuggestionSpan span2 = cloneViaParcel(span1);
        span2.setFlags(SuggestionSpan.FLAG_EASY_CORRECT);

        assertEquals("Should return the flag passed in set function",
                SuggestionSpan.FLAG_EASY_CORRECT, span2.getFlags());

        assertTrue("Instances with different flags should be equal", span1.equals(span2));
    }

    @SmallTest
    public void testEquals_returnsFalseIfCreationTimeIsNotSame() {
        final Locale anyLocale = Locale.forLanguageTag("en");
        final String[] anySuggestions = new String[0];
        final int anyFlags = SuggestionSpan.FLAG_AUTO_CORRECTION;
        final Class anyClass = SuggestionSpan.class;

        final SuggestionSpan span1 = new SuggestionSpan(null, anyLocale, anySuggestions, anyFlags,
                anyClass);
        try {
            // let some time pass before constructing the other span
            Thread.sleep(2);
        } catch (InterruptedException e) {
            // ignore
        }
        final SuggestionSpan span2 = new SuggestionSpan(null, anyLocale, anySuggestions, anyFlags,
                anyClass);

        assertFalse("Instances created at different time should not be equal", span2.equals(span1));
    }

    /**
     * @param locale a {@link Locale} object.
     * @return A well-formed BCP 47 language tag representation.
     */
    @Nullable
    private Locale toWellFormedLocale(@Nullable final Locale locale) {
        if (locale == null) {
            return null;
        }
        // Drop all the malformed data.
        return Locale.forLanguageTag(locale.toLanguageTag());
    }

    @NonNull
    private String getNonNullLocaleString(@Nullable final Locale original) {
        if (original == null) {
            return "";
        }
        return original.toString();
    }

    private void checkGetLocaleObject(final Locale locale) {
        final SuggestionSpan span = new SuggestionSpan(locale, new String[0],
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        // In the context of SuggestionSpan#getLocaleObject(), we do care only about subtags that
        // can be interpreted as LanguageTag.
        assertEquals(toWellFormedLocale(locale), span.getLocaleObject());
        assertEquals(getNonNullLocaleString(locale), span.getLocale());

        final SuggestionSpan cloned = cloneViaParcel(span);
        assertEquals(span, cloned);
        assertEquals(toWellFormedLocale(locale), cloned.getLocaleObject());
        assertEquals(getNonNullLocaleString(locale), cloned.getLocale());
    }

    @SmallTest
    public void testGetLocaleObject() {
        checkGetLocaleObject(Locale.forLanguageTag("en"));
        checkGetLocaleObject(Locale.forLanguageTag("en-GB"));
        checkGetLocaleObject(Locale.forLanguageTag("EN-GB"));
        checkGetLocaleObject(Locale.forLanguageTag("en-gb"));
        checkGetLocaleObject(Locale.forLanguageTag("En-gB"));
        checkGetLocaleObject(Locale.forLanguageTag("und"));
        checkGetLocaleObject(Locale.forLanguageTag("de-DE-u-co-phonebk"));
        checkGetLocaleObject(Locale.forLanguageTag(""));
        checkGetLocaleObject(null);
        checkGetLocaleObject(new Locale(" an  ", " i n v a l i d ", "data"));
    }

    @NonNull
    SuggestionSpan cloneViaParcel(@NonNull final SuggestionSpan original) {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return new SuggestionSpan(parcel);
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
    }
}
