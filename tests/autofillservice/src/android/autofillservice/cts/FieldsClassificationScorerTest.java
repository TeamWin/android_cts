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

import android.service.autofill.FieldsClassificationScorer;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillValue;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FieldsClassificationScorerTest {

    @Test
    public void testGetScore_nullValue() {
        assertThat(FieldsClassificationScorer.getScore(null, "D'OH!")).isEqualTo(0);
    }

    @Test
    public void testGetScore_nonTextValue() {
        assertThat(FieldsClassificationScorer.getScore(AutofillValue.forToggle(true), "D'OH!"))
                .isEqualTo(0);
    }

    @Test
    public void testGetScore_nullUserData() {
        assertThat(FieldsClassificationScorer.getScore(AutofillValue.forText("D'OH!"), null))
                .isEqualTo(0);
    }

    @Test
    public void testGetScore_fullMatch() {
        assertThat(FieldsClassificationScorer.getScore(AutofillValue.forText("d'oh!"), "d'oh!"))
                .isEqualTo(100_0000);
    }

    @Test
    public void testGetScore_fullMatchMixedCase() {
        assertThat(FieldsClassificationScorer.getScore(AutofillValue.forText("D'OH!"), "D'oH!"))
                .isEqualTo(100_0000);
    }
}
