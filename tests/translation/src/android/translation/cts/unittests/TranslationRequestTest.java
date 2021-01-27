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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationRequest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranslationRequestTest {

    private final AutofillId mAutofillId = new AutofillId(17);
    private final String mTranslationText = "sample text";

    @Test
    public void testBuilder_validRequest() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .setAutofillId(mAutofillId)
                .setTranslationText(mTranslationText)
                .build();

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getTranslationText()).isEqualTo("sample text");
    }

    @Test
    public void testParceledRequest() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .setAutofillId(mAutofillId)
                .setTranslationText(mTranslationText)
                .build();

        final Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationRequest parceledRequest =
                TranslationRequest.CREATOR.createFromParcel(parcel);

        assertThat(request.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request.getTranslationText()).isEqualTo("sample text");
    }
}