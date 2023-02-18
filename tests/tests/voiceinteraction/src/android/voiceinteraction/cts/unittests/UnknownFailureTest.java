/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.voiceinteraction.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.Parcel;
import android.service.voice.DetectorFailure;
import android.service.voice.UnknownFailure;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link UnknownFailure} APIs.
 */
@RunWith(AndroidJUnit4.class)
public class UnknownFailureTest {
    static final String TEST_ERROR_MESSAGE = "Error for UnknownFailureTest";

    @Test
    public void testUnknownFailure_nullErrorMessage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new UnknownFailure(/* errorMessage= */ null));
    }

    @Test
    public void testUnknownFailure_getErrorMessage() throws Exception {
        final UnknownFailure unknownFailure = new UnknownFailure(TEST_ERROR_MESSAGE);

        assertThat(unknownFailure.getErrorMessage()).isEqualTo(TEST_ERROR_MESSAGE);
    }

    @Test
    public void testUnknownFailure_getSuggestedAction() throws Exception {
        final UnknownFailure unknownFailure = new UnknownFailure(TEST_ERROR_MESSAGE);

        assertThat(unknownFailure.getSuggestedAction()).isEqualTo(
                DetectorFailure.SUGGESTED_ACTION_UNKNOWN);
    }

    @Test
    public void testUnknownFailureParcelizeDeparcelize() throws Exception {
        final UnknownFailure unknownFailure = new UnknownFailure(TEST_ERROR_MESSAGE);

        final Parcel p = Parcel.obtain();
        unknownFailure.writeToParcel(p, 0);
        p.setDataPosition(0);

        final UnknownFailure targetUnknownFailure = UnknownFailure.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(unknownFailure.getErrorMessage()).isEqualTo(
                targetUnknownFailure.getErrorMessage());
        assertThat(unknownFailure.getSuggestedAction()).isEqualTo(
                targetUnknownFailure.getSuggestedAction());
    }
}
