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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.service.autofill.SimpleRegexValidator;
import android.service.autofill.ValueFinder;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.PatternSyntaxException;

@RunWith(AndroidJUnit4.class)
public class SimpleRegexValidatorTest {
    @Test(expected = NullPointerException.class)
    public void allNullConstructor() {
        new SimpleRegexValidator(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullRegexConstructor() {
        new SimpleRegexValidator(new AutofillId(1), null);
    }

    @Test(expected = NullPointerException.class)
    public void nullAutofillIdConstructor() {
        new SimpleRegexValidator(null, ".");
    }

    @Test(expected = PatternSyntaxException.class)
    public void badRegexBuilder() {
        new SimpleRegexValidator(new AutofillId(1), "(");
    }

    @Test
    public void unknownField() {
        AutofillId unknownId = new AutofillId(42);

        SimpleRegexValidator validator = new SimpleRegexValidator(unknownId, ".*");

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(unknownId)).thenReturn(null);
        assertThat(validator.isValid(finder)).isFalse();
    }

    @Test
    public void singleFieldValid() {
        AutofillId creditCardFieldId = new AutofillId(1);
        SimpleRegexValidator validator = new SimpleRegexValidator(creditCardFieldId,
                "^\\s*\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?(\\d{4})\\s*$");

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(creditCardFieldId)).thenReturn("1234 5678 9012 3456");
        assertThat(validator.isValid(finder)).isTrue();

        when(finder.findByAutofillId(creditCardFieldId)).thenReturn("invalid");
        assertThat(validator.isValid(finder)).isFalse();
    }

    @Test
    public void singleFieldInvalid() {
        AutofillId id = new AutofillId(1);
        SimpleRegexValidator validator = new SimpleRegexValidator(id, "\\d*");

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(id)).thenReturn("123a456");

        // Regex has to match the whole value
        assertThat(validator.isValid(finder)).isFalse();
    }
}
