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

import static org.testng.Assert.assertThrows;

import android.os.Parcel;
import android.service.translation.TranslationRequest;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationSpec;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class InternalTranslationRequestTest {

    private final TranslationSpec mSourceSpec =
            new TranslationSpec("en", TranslationSpec.DATA_FORMAT_TEXT);
    private final TranslationSpec mDestSpec =
            new TranslationSpec("zh", TranslationSpec.DATA_FORMAT_TEXT);
    private final android.view.translation.TranslationRequest mRequest =
            new android.view.translation.TranslationRequest.Builder()
            .setAutofillId(new AutofillId(17))
            .setTranslationText("sample text")
            .build();

    @Test
    public void testBuilder_nullSpecs() {
        final ArrayList<android.view.translation.TranslationRequest> requests = new ArrayList<>();
        assertThrows(NullPointerException.class, () -> {
            final TranslationRequest request =
                    new TranslationRequest.Builder(0, null, mDestSpec, requests).build();
        });
        assertThrows(NullPointerException.class, () -> {
            final TranslationRequest request =
                    new TranslationRequest.Builder(0, mSourceSpec, null, requests).build();
        });
    }

    @Test
    public void testBuilder_nullRequests() {
        assertThrows(NullPointerException.class, () -> {
            final TranslationRequest request =
                    new TranslationRequest.Builder(0, mSourceSpec, mDestSpec, null).build();
        });
    }

    @Test
    public void testBuilder_validRequests() {
        final ArrayList<android.view.translation.TranslationRequest> requests = new ArrayList<>();
        requests.add(mRequest);
        final TranslationRequest request =
                new TranslationRequest.Builder(0, mSourceSpec, mDestSpec, requests).build();

        assertThat(request.getRequestId()).isEqualTo(0);
        assertThat(request.getSourceSpec()).isEqualTo(mSourceSpec);
        assertThat(request.getDestSpec()).isEqualTo(mDestSpec);
        assertThat(request.getTranslationRequests().size()).isEqualTo(1);

        final android.view.translation.TranslationRequest request1 =
                request.getTranslationRequests().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getTranslationText()).isEqualTo("sample text");
    }

    @Test
    public void testBuilder_validAddRequests() {
        final ArrayList<android.view.translation.TranslationRequest> requests = new ArrayList<>();
        final TranslationRequest request =
                new TranslationRequest.Builder(0, mSourceSpec, mDestSpec, requests)
                .addTranslationRequests(mRequest)
                .build();

        assertThat(request.getRequestId()).isEqualTo(0);
        assertThat(request.getSourceSpec()).isEqualTo(mSourceSpec);
        assertThat(request.getDestSpec()).isEqualTo(mDestSpec);
        assertThat(request.getTranslationRequests().size()).isEqualTo(1);

        final android.view.translation.TranslationRequest request1 =
                request.getTranslationRequests().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getTranslationText()).isEqualTo("sample text");
    }

    @Test
    public void testParceledRequest() {
        final ArrayList<android.view.translation.TranslationRequest> requests = new ArrayList<>();
        requests.add(mRequest);
        final TranslationRequest request =
                new TranslationRequest.Builder(0, mSourceSpec, mDestSpec, requests).build();

        final Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final TranslationRequest parceledRequest =
                TranslationRequest.CREATOR.createFromParcel(parcel);

        assertThat(request.getRequestId()).isEqualTo(0);
        assertThat(request.getSourceSpec()).isEqualTo(mSourceSpec);
        assertThat(request.getDestSpec()).isEqualTo(mDestSpec);
        assertThat(request.getTranslationRequests().size()).isEqualTo(1);

        final android.view.translation.TranslationRequest request1 =
                parceledRequest.getTranslationRequests().get(0);
        assertThat(request1.getAutofillId()).isEqualTo(new AutofillId(17));
        assertThat(request1.getTranslationText()).isEqualTo("sample text");
    }
}