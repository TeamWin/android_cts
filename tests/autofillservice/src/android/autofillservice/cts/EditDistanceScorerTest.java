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

import android.service.autofill.EditDistanceScorer;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillValue;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EditDistanceScorerTest {

    private final EditDistanceScorer mScorer = EditDistanceScorer.getInstance();

    @Test
    public void testGetScore_nullValue() {
        assertThat(mScorer.getScore(null, "D'OH!")).isWithin(0);
    }

    @Test
    public void testGetScore_nonTextValue() {
        assertThat(mScorer.getScore(AutofillValue.forToggle(true), "D'OH!")).isWithin(0);
    }

    @Test
    public void testGetScore_nullUserData() {
        assertThat(mScorer.getScore(AutofillValue.forText("D'OH!"), null)).isWithin(0);
    }

    @Test
    public void testGetScore_fullMatch() {
        assertThat(mScorer.getScore(AutofillValue.forText("D'OH!"), "D'OH!")).isWithin(1);
    }

    @Test
    public void testGetScore_fullMatchMixedCase() {
        assertThat(mScorer.getScore(AutofillValue.forText("D'OH!"), "D'oH!")).isWithin(1);
    }

    // TODO(b/70291841): might need to change it once it supports different sizes
    @Test
    public void testGetScore_mismatchDifferentSizes() {
        assertThat(mScorer.getScore(AutofillValue.forText("One"), "MoreThanOne")).isWithin(0);
        assertThat(mScorer.getScore(AutofillValue.forText("MoreThanOne"), "One")).isWithin(0);
    }

    @Test
    public void testGetScore_partialMatch() {
        assertThat(mScorer.getScore(AutofillValue.forText("Dude"), "Dxxx")).isWithin(0.25F);
        assertThat(mScorer.getScore(AutofillValue.forText("Dude"), "DUxx")).isWithin(0.50F);
        assertThat(mScorer.getScore(AutofillValue.forText("Dude"), "DUDx")).isWithin(0.75F);
        assertThat(mScorer.getScore(AutofillValue.forText("Dxxx"), "Dude")).isWithin(0.25F);
        assertThat(mScorer.getScore(AutofillValue.forText("DUxx"), "Dude")).isWithin(0.50F);
        assertThat(mScorer.getScore(AutofillValue.forText("DUDx"), "Dude")).isWithin(0.75F);
    }
}
