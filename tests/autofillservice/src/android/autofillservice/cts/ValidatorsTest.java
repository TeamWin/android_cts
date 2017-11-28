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

import static android.service.autofill.Validators.not;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.service.autofill.InternalValidator;
import android.service.autofill.Validator;
import android.service.autofill.ValueFinder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorsTest extends AutoFillServiceTestCase {

    @Mock private Validator mInvalidValidator;
    @Mock private InternalValidator mValidValidator;
    @Mock private ValueFinder mValueFinder;

    @Test
    public void testNot_null() {
        assertThrows(IllegalArgumentException.class, () -> not(null));
    }

    @Test
    public void testNot_invalidClass() {
        assertThrows(IllegalArgumentException.class, () -> not(mInvalidValidator));
    }

    @Test
    public void testNot_falseToTrue() {
        when(mValidValidator.isValid(mValueFinder)).thenReturn(false);
        final InternalValidator notValidator = (InternalValidator) not(mValidValidator);
        assertThat(notValidator.isValid(mValueFinder)).isTrue();
    }

    @Test
    public void testNot_trueToFalse() {
        when(mValidValidator.isValid(mValueFinder)).thenReturn(true);
        final InternalValidator notValidator = (InternalValidator) not(mValidValidator);
        assertThat(notValidator.isValid(mValueFinder)).isFalse();
    }
}
