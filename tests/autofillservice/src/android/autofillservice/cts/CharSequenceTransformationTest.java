/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.ValueFinder;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;

import java.util.regex.PatternSyntaxException;

@RunWith(AndroidJUnit4.class)
public class CharSequenceTransformationTest {
    @Test(expected = NullPointerException.class)
    public void testAllNullBuilder() {
        new CharSequenceTransformation.Builder(null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullAutofillIdBuilder() {
        new CharSequenceTransformation.Builder(null, "", "");
    }

    @Test(expected = NullPointerException.class)
    public void testNullRegexBuilder() {
        new CharSequenceTransformation.Builder(new AutofillId(1), null, "");
    }

    @Test(expected = NullPointerException.class)
    public void testNullSubstBuilder() {
        new CharSequenceTransformation.Builder(new AutofillId(1), "", null);
    }

    @Test(expected = PatternSyntaxException.class)
    public void testBadRegexBuilder() {
        new CharSequenceTransformation.Builder(new AutofillId(1), "(", "");
    }

    @Test
    public void testBadSubst() {
        AutofillId id1 = new AutofillId(1);
        AutofillId id2 = new AutofillId(2);
        AutofillId id3 = new AutofillId(3);
        AutofillId id4 = new AutofillId(4);

        CharSequenceTransformation.Builder b = new CharSequenceTransformation.Builder(id1, "(.)",
                "1=$1");

        // bad subst: The regex has no capture groups
        b.addField(id2, ".", "2=$1");

        // bad subst: The regex does not have enough capture groups
        b.addField(id3, "(.)", "3=$2");

        b.addField(id4, "(.)", "4=$1");

        CharSequenceTransformation trans = b.build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id1)).thenReturn("a");
        when(finder.findByAutofillId(id2)).thenReturn("b");
        when(finder.findByAutofillId(id3)).thenReturn("c");
        when(finder.findByAutofillId(id4)).thenReturn("d");

        trans.apply(finder, template, 0);

        // bad subst are ignored
        verify(template).setCharSequence(eq(0), any(), argThat(new CharSequenceMatcher("1=a4=d")));
    }

    @Test
    public void testUnknownField() {
        AutofillId id1 = new AutofillId(1);
        AutofillId id2 = new AutofillId(2);
        AutofillId unknownId = new AutofillId(42);

        CharSequenceTransformation.Builder b = new CharSequenceTransformation.Builder(id1, ".*",
                "1");

        // bad subst: The field will not be found
        b.addField(unknownId, ".*", "unknown");

        b.addField(id2, ".*", "2");

        CharSequenceTransformation trans = b.build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id1)).thenReturn("1");
        when(finder.findByAutofillId(id2)).thenReturn("2");
        when(finder.findByAutofillId(unknownId)).thenReturn(null);

        trans.apply(finder, template, 0);

        // if a view cannot be found, nothing is not, not even partial results
        verify(template, never()).setCharSequence(eq(0), any(), any());
    }

    @Test
    public void testCreditCardObfuscator() {
        AutofillId creditCardFieldId = new AutofillId(1);
        CharSequenceTransformation trans = new CharSequenceTransformation.Builder(creditCardFieldId,
                "^\\s*\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?(\\d{4})\\s*$", "...$1").build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(creditCardFieldId)).thenReturn("1234 5678 9012 3456");

        trans.apply(finder, template, 0);

        verify(template).setCharSequence(eq(0), any(), argThat(new CharSequenceMatcher("...3456")));
    }

    @Test
    public void userNameObfuscator() {
        AutofillId userNameFieldId = new AutofillId(1);
        AutofillId passwordFieldId = new AutofillId(2);
        CharSequenceTransformation trans = new CharSequenceTransformation.Builder(userNameFieldId,
                "(.*)", "$1").addField(passwordFieldId, ".*(..)$", "/..$1").build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(userNameFieldId)).thenReturn("myUserName");
        when(finder.findByAutofillId(passwordFieldId)).thenReturn("myPassword");

        trans.apply(finder, template, 0);

        verify(template).setCharSequence(eq(0), any(),
                argThat(new CharSequenceMatcher("myUserName/..rd")));
    }

    static class CharSequenceMatcher implements ArgumentMatcher<CharSequence> {
        private final CharSequence mExpected;

        public CharSequenceMatcher(CharSequence expected) {
            mExpected = expected;
        }

        @Override
        public boolean matches(CharSequence actual) {
            return actual.toString().equals(mExpected.toString());
        }
    }
}
