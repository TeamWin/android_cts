/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.LocaleList;
import android.os.Parcel;
import android.util.Size;
import android.view.inline.InlinePresentationSpec;
import android.view.inputmethod.InlineSuggestionsRequest;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InlineSuggestionsRequestTest {

    @Test
    public void testNullInlinePresentationSpecsThrowsException() {
        assertThrows(NullPointerException.class,
                () -> new InlineSuggestionsRequest.Builder(/* presentationSpecs */ null).build());
    }

    @Test
    public void testInvalidPresentationSpecsCountsThrowsException() {
        ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
        presentationSpecs.add(new InlinePresentationSpec.Builder(new Size(100, 100),
                new Size(400, 100)).build());
        presentationSpecs.add(new InlinePresentationSpec.Builder(new Size(100, 100),
                new Size(400, 100)).build());

        assertThrows(IllegalStateException.class,
                () -> new InlineSuggestionsRequest.Builder(presentationSpecs)
                        .setMaxSuggestionCount(1).build());
    }

    @Test
    public void testEmptyPresentationSpecsThrowsException() {
        assertThrows(IllegalStateException.class,
                () -> new InlineSuggestionsRequest.Builder(new ArrayList<>())
                        .setMaxSuggestionCount(1).build());
    }

    @Test
    public void testZeroMaxSuggestionCountThrowsException() {
        ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
        presentationSpecs.add(new InlinePresentationSpec.Builder(new Size(100, 100),
                new Size(400, 100)).build());
        assertThrows(IllegalStateException.class,
                () -> new InlineSuggestionsRequest.Builder(presentationSpecs)
                        .setMaxSuggestionCount(0).build());
    }

    @Test
    public void testInlineSuggestionsRequestValues() {
        final int suggestionCount = 3;
        ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
        LocaleList localeList = LocaleList.forLanguageTags("fa-IR");
        InlineSuggestionsRequest request =
                new InlineSuggestionsRequest.Builder(presentationSpecs)
                        .addPresentationSpecs(
                                new InlinePresentationSpec.Builder(new Size(100, 100),
                                        new Size(400, 100)).build())
                        .setSupportedLocales(LocaleList.forLanguageTags("fa-IR"))
                        .setExtras(/* value */ null)
                        .setMaxSuggestionCount(suggestionCount).build();

        assertThat(request.getMaxSuggestionCount()).isEqualTo(suggestionCount);
        assertThat(request.getPresentationSpecs()).isNotNull();
        assertThat(request.getPresentationSpecs().size()).isEqualTo(1);
        assertThat(request.getExtras()).isNull();
        assertThat(request.getSupportedLocales()).isEqualTo(localeList);
    }

    @Test
    public void testInlineSuggestionsRequestParcelizeDeparcelize() {
        ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
        presentationSpecs.add(
                new InlinePresentationSpec.Builder(new Size(100, 100), new Size(400, 400)).build());
        InlineSuggestionsRequest request =
                new InlineSuggestionsRequest.Builder(presentationSpecs).build();
        Parcel p = Parcel.obtain();
        request.writeToParcel(p, 0);
        p.setDataPosition(0);

        InlineSuggestionsRequest targetRequest =
                InlineSuggestionsRequest.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(targetRequest).isEqualTo(request);
    }
}
