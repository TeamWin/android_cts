/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.smartspace.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.smartspace.uitemplatedata.SmartspaceText;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SmartspaceText}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceTextTest {

    private static final String TAG = "SmartspaceTextTest";

    @Test
    public void testCreateSmartspaceText_defaultBuilder() {
        SmartspaceText smartspaceText = new SmartspaceText.Builder("test").build();

        assertThat(smartspaceText.getText()).isEqualTo("test");
        assertThat(smartspaceText.getTruncateAtType()).isEqualTo(TextUtils.TruncateAt.END);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        smartspaceText.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceText copyText = SmartspaceText.CREATOR.createFromParcel(parcel);
        assertThat(smartspaceText).isEqualTo(copyText);
        parcel.recycle();
    }

    @Test
    public void testCreateSmartspaceText_builderWithMiddleTrunctAtType() {
        SmartspaceText smartspaceText = new SmartspaceText.Builder("test",
                TextUtils.TruncateAt.MIDDLE).build();

        assertThat(smartspaceText.getText()).isEqualTo("test");
        assertThat(smartspaceText.getTruncateAtType()).isEqualTo(TextUtils.TruncateAt.MIDDLE);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        smartspaceText.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceText copyText = SmartspaceText.CREATOR.createFromParcel(parcel);
        assertThat(smartspaceText).isEqualTo(copyText);
        parcel.recycle();
    }
}
