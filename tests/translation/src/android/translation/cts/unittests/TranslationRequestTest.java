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
import android.view.translation.TranslationRequestValue;
import android.view.translation.ViewTranslationRequest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranslationRequestTest {

    private final TranslationRequestValue mValue = TranslationRequestValue.forText("hello");

    private final ViewTranslationRequest mRequest = new ViewTranslationRequest
            .Builder(new AutofillId(17))
            .setValue("sample id", TranslationRequestValue.forText("sample text"))
            .build();

    @Test
    public void testBuilder_validViewTranslationRequest() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .addViewTranslationRequest(mRequest)
                .build();

        assertThat(request.getTranslationRequestValues().size()).isEqualTo(0);
        assertThat(request.getViewTranslationRequests().size()).isEqualTo(1);

        final ViewTranslationRequest viewRequest =
                request.getViewTranslationRequests().get(0);
        assertThat(viewRequest.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewRequest.getKeys().size()).isEqualTo(1);
        assertThat(viewRequest.getKeys()).containsExactly("sample id");
        assertThat(viewRequest.getValue("sample id").getText()).isEqualTo("sample text");
    }

    @Test
    public void testBuilder_validTranslationRequestValue() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .addTranslationRequestValue(mValue)
                .build();

        assertThat(request.getTranslationRequestValues().size()).isEqualTo(1);
        assertThat(request.getViewTranslationRequests().size()).isEqualTo(0);

        final TranslationRequestValue value =
                request.getTranslationRequestValues().get(0);
        assertThat(value.getText()).isEqualTo("hello");
    }

    @Test
    public void testParceledRequest_validTranslationRequestValues() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .addTranslationRequestValue(mValue)
                .addTranslationRequestValue(TranslationRequestValue.forText("world"))
                .build();

        final Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationRequest parceledRequest =
                TranslationRequest.CREATOR.createFromParcel(parcel);

        assertThat(parceledRequest.getTranslationRequestValues().size()).isEqualTo(2);
        assertThat(parceledRequest.getViewTranslationRequests().size()).isEqualTo(0);

        final TranslationRequestValue value1 =
                parceledRequest.getTranslationRequestValues().get(0);
        assertThat(value1.getText()).isEqualTo("hello");

        final TranslationRequestValue value2 =
                parceledRequest.getTranslationRequestValues().get(1);
        assertThat(value2.getText()).isEqualTo("world");
    }

    @Test
    public void testBuilder_sameAutofillIdViewTranslationRequests() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .addViewTranslationRequest(mRequest)
                .addViewTranslationRequest(
                        new ViewTranslationRequest.Builder(new AutofillId(17))
                                .setValue("id2", TranslationRequestValue.forText("text2"))
                                .build())
                .build();

        assertThat(request.getTranslationRequestValues().size()).isEqualTo(0);
        assertThat(request.getViewTranslationRequests().size()).isEqualTo(2);

        final ViewTranslationRequest viewRequest =
                request.getViewTranslationRequests().get(0);
        assertThat(viewRequest.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewRequest.getKeys().size()).isEqualTo(1);
        assertThat(viewRequest.getKeys()).containsExactly("sample id");
        assertThat(viewRequest.getValue("sample id").getText()).isEqualTo("sample text");

        final ViewTranslationRequest viewRequest2 =
                request.getViewTranslationRequests().get(1);
        assertThat(viewRequest2.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewRequest2.getKeys().size()).isEqualTo(1);
        assertThat(viewRequest2.getKeys()).containsExactly("id2");
        assertThat(viewRequest2.getValue("id2").getText()).isEqualTo("text2");
    }

    @Test
    public void testBuilder_mixingAdders() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .addViewTranslationRequest(mRequest)
                .addTranslationRequestValue(mValue)
                .build();

        assertThat(request.getTranslationRequestValues().size()).isEqualTo(1);
        assertThat(request.getViewTranslationRequests().size()).isEqualTo(1);

        final ViewTranslationRequest viewRequest =
                request.getViewTranslationRequests().get(0);
        assertThat(viewRequest.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(viewRequest.getKeys().size()).isEqualTo(1);
        assertThat(viewRequest.getKeys()).containsExactly("sample id");
        assertThat(viewRequest.getValue("sample id").getText()).isEqualTo("sample text");

        final TranslationRequestValue value =
                request.getTranslationRequestValues().get(0);
        assertThat(value.getText()).isEqualTo("hello");
    }

    @Test
    public void testParceledRequest_validViewTranslationRequests() {
        final TranslationRequest request = new TranslationRequest.Builder()
                .addViewTranslationRequest(mRequest)
                .addViewTranslationRequest(new ViewTranslationRequest.Builder(new AutofillId(42))
                        .setValue("id2", TranslationRequestValue.forText("test"))
                        .build())
                .build();

        final Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationRequest parceledRequest =
                TranslationRequest.CREATOR.createFromParcel(parcel);

        assertThat(parceledRequest.getTranslationRequestValues().size()).isEqualTo(0);
        assertThat(parceledRequest.getViewTranslationRequests().size()).isEqualTo(2);

        final ViewTranslationRequest request1 =
                parceledRequest.getViewTranslationRequests().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getKeys().size()).isEqualTo(1);
        assertThat(request1.getKeys()).containsExactly("sample id");
        assertThat(request1.getValue("sample id").getText()).isEqualTo("sample text");

        final ViewTranslationRequest request2 =
                parceledRequest.getViewTranslationRequests().get(1);
        assertThat(request2.getAutofillId()).isEqualTo(new AutofillId(42));
        assertThat(request2.getKeys().size()).isEqualTo(1);
        assertThat(request2.getKeys()).containsExactly("id2");
        assertThat(request2.getValue("id2").getText()).isEqualTo("test");
    }
}