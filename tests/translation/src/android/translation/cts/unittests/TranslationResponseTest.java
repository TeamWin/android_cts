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

import android.os.Parcel;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.ViewTranslationResponse;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranslationResponseTest {

    private final TranslationResponseValue mValue =
            new TranslationResponseValue.Builder(STATUS_SUCCESS)
                    .setText("hello")
                    .build();

    private final ViewTranslationResponse mResponse = new ViewTranslationResponse
            .Builder(new AutofillId(17))
            .setValue("sample id",
                    new TranslationResponseValue.Builder(STATUS_SUCCESS)
                            .setText("sample text")
                            .build())
            .build();

    @Test
    public void testBuilder_validViewTranslationResponse() {
        final TranslationResponse request =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, mResponse)
                        .build();

        assertThat(request.getTranslationResponseValues().size()).isEqualTo(0);
        assertThat(request.getViewTranslationResponses().size()).isEqualTo(1);

        final ViewTranslationResponse viewRequest =
                request.getViewTranslationResponses().get(0);
        assertThat(viewRequest.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewRequest.getKeys().size()).isEqualTo(1);
        assertThat(viewRequest.getValue("sample id").getText()).isEqualTo("sample text");
    }

    @Test
    public void testBuilder_errorViewTranslationResponse() {
        final TranslationResponse request =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, new ViewTranslationResponse
                                .Builder(new AutofillId(42))
                                .setValue("id2",
                                        TranslationResponseValue.forError())
                                .build())
                        .build();

        assertThat(request.getTranslationResponseValues().size()).isEqualTo(0);
        assertThat(request.getViewTranslationResponses().size()).isEqualTo(1);

        final ViewTranslationResponse viewRequest =
                request.getViewTranslationResponses().get(0);
        assertThat(viewRequest.getAutofillId()).isEqualTo(new AutofillId(42));
        assertThat(viewRequest.getKeys().size()).isEqualTo(1);
        assertThat(viewRequest.getValue("id2").getStatusCode())
                .isEqualTo(TranslationResponseValue.STATUS_ERROR);
    }

    @Test
    public void testBuilder_validTranslationResponseValue() {
        final TranslationResponse request =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setTranslationResponseValue(0, mValue)
                        .build();

        assertThat(request.getTranslationResponseValues().size()).isEqualTo(1);
        assertThat(request.getViewTranslationResponses().size()).isEqualTo(0);

        final TranslationResponseValue value =
                request.getTranslationResponseValues().get(0);
        assertThat(value.getText()).isEqualTo("hello");
    }

    @Test
    public void testParceledRequest_validTranslationResponseValues() {
        final TranslationResponse request =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setTranslationResponseValue(0, mValue)
                        .setTranslationResponseValue(2,
                                new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                        .setText("world")
                                        .build())
                        .build();

        final Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationResponse parceledRequest =
                TranslationResponse.CREATOR.createFromParcel(parcel);

        assertThat(parceledRequest.getTranslationResponseValues().size()).isEqualTo(2);
        assertThat(parceledRequest.getViewTranslationResponses().size()).isEqualTo(0);

        final TranslationResponseValue value1 =
                parceledRequest.getTranslationResponseValues().get(0);
        assertThat(value1.getText()).isEqualTo("hello");

        final TranslationResponseValue value2 =
                parceledRequest.getTranslationResponseValues().get(2);
        assertThat(value2.getText()).isEqualTo("world");
    }

    @Test
    public void testBuilder_mixingAdders() {
        final TranslationResponse response =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, mResponse)
                        .setTranslationResponseValue(0, mValue)
                        .build();

        assertThat(response.getTranslationResponseValues().size()).isEqualTo(1);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(1);

        final ViewTranslationResponse viewResponse =
                response.getViewTranslationResponses().get(0);
        assertThat(viewResponse.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewResponse.getKeys().size()).isEqualTo(1);
        assertThat(viewResponse.getValue("sample id").getText()).isEqualTo("sample text");

        final TranslationResponseValue value =
                response.getTranslationResponseValues().get(0);
        assertThat(value.getText()).isEqualTo("hello");
    }

    @Test
    public void testParceledRequest_validViewTranslationResponses() {
        final TranslationResponse request =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setViewTranslationResponse(0, mResponse)
                        .setViewTranslationResponse(2, new ViewTranslationResponse
                                .Builder(new AutofillId(42))
                                .setValue("id2",
                                        new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                                .setText("test")
                                                .build())
                                .build())
                        .build();

        final Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationResponse parceledRequest =
                TranslationResponse.CREATOR.createFromParcel(parcel);

        assertThat(parceledRequest.getTranslationResponseValues().size()).isEqualTo(0);
        assertThat(parceledRequest.getViewTranslationResponses().size()).isEqualTo(2);

        final ViewTranslationResponse request1 =
                parceledRequest.getViewTranslationResponses().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getKeys().size()).isEqualTo(1);
        assertThat(request1.getValue("sample id").getText()).isEqualTo("sample text");

        final ViewTranslationResponse request2 =
                parceledRequest.getViewTranslationResponses().get(2);
        assertThat(request2.getAutofillId()).isEqualTo(new AutofillId(42));
        assertThat(request2.getKeys().size()).isEqualTo(1);
        assertThat(request2.getValue("id2").getText()).isEqualTo("test");
    }

}