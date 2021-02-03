/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.translation.cts.unittests;

import static android.view.translation.TranslationResponseValue.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.view.translation.TranslationRequestValue;
import android.view.translation.TranslationResponseValue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranslationValueTest {

    @Test
    public void testTranslationRequestValue_forText() {
        final TranslationRequestValue value = TranslationRequestValue.forText("sample text");

        assertThat(value.getText()).isEqualTo("sample text");
    }

    @Test
    public void testTranslationResponseValue_validBuilder() {
        final TranslationResponseValue value = new TranslationResponseValue.Builder(STATUS_SUCCESS)
                .setText("sample text")
                .build();

        assertThat(value.getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(value.getText()).isEqualTo("sample text");
    }

    @Test
    public void testTranslationResponseValue_forError() {
        final TranslationResponseValue value = TranslationResponseValue.forError();

        assertThat(value.getStatusCode()).isEqualTo(TranslationResponseValue.STATUS_ERROR);
        assertThat(value.getText()).isNull();
    }
}
