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
import android.view.translation.TranslationResponse;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class TranslationResponseTest {

    private final TranslationRequest mRequest = new TranslationRequest.Builder()
            .setAutofillId(new AutofillId(17))
            .setTranslationText("text1")
            .build();
    private final TranslationRequest mRequest2 = new TranslationRequest.Builder()
            .setAutofillId(new AutofillId(42))
            .setTranslationText("text2")
            .build();

    @Test
    public void testBuilder_nullTranslation() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                .addTranslations(null)
                .build();

        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        assertThat(response.getTranslations().size()).isEqualTo(1);
        assertThat(response.getTranslations().get(0)).isNull();
    }

    @Test
    public void testBuilder_emptyTranslations() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                .build();

        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        assertThat(response.getTranslations().size()).isEqualTo(0);
    }

    @Test
    public void testBuilder_changeTranslationStatus() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                .setTranslationStatus(TranslationResponse.TRANSLATION_STATUS_UNKNOWN_ERROR)
                .build();

        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_UNKNOWN_ERROR);
    }

    @Test
    public void testBuilder_validAddRequests() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                .addTranslations(mRequest)
                .addTranslations(mRequest2)
                .build();

        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        assertThat(response.getTranslations().size()).isEqualTo(2);

        final TranslationRequest request1 = response.getTranslations().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getTranslationText()).isEqualTo("text1");

        final TranslationRequest request2 = response.getTranslations().get(1);
        assertThat(request2.getAutofillId()).isEqualTo(new AutofillId(42));
        assertThat(request2.getTranslationText()).isEqualTo("text2");
    }

    @Test
    public void testBuilder_validSetRequests() {
        final ArrayList<TranslationRequest> requests = new ArrayList<>();
        requests.add(mRequest);
        requests.add(mRequest2);
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                .setTranslations(requests)
                .build();

        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        assertThat(response.getTranslations().size()).isEqualTo(2);

        final TranslationRequest request1 = response.getTranslations().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getTranslationText()).isEqualTo("text1");

        final TranslationRequest request2 = response.getTranslations().get(1);
        assertThat(request2.getAutofillId()).isEqualTo(new AutofillId(42));
        assertThat(request2.getTranslationText()).isEqualTo("text2");
    }

    @Test
    public void testParceledResponse() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                .addTranslations(mRequest)
                .build();

        final Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationResponse parceledResponse =
                TranslationResponse.CREATOR.createFromParcel(parcel);

        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        assertThat(response.getTranslations().size()).isEqualTo(1);

        final TranslationRequest request1 = response.getTranslations().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getTranslationText()).isEqualTo("text1");
    }

}