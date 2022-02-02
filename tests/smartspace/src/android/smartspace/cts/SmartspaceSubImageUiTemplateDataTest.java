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

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.app.smartspace.uitemplatedata.SmartspaceIcon;
import android.app.smartspace.uitemplatedata.SmartspaceSubImageUiTemplateData;
import android.app.smartspace.uitemplatedata.SmartspaceText;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link SmartspaceSubImageUiTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceSubImageUiTemplateDataTest {

    private static final String TAG = "SmartspaceSubImageUiTemplateDataTest";

    @Test
    public void testCreateSmartspaceSubImageUiTemplateData() {
        List<SmartspaceText> texts = new ArrayList<>();
        texts.add(new SmartspaceText.Builder("text1").build());
        texts.add(new SmartspaceText.Builder("text2").build());

        List<SmartspaceIcon> images = new ArrayList<>();
        images.add(SmartspaceTestUtils.createSmartspaceIcon("icon1"));
        images.add(SmartspaceTestUtils.createSmartspaceIcon("icon2"));
        images.add(SmartspaceTestUtils.createSmartspaceIcon("icon3"));

        SmartspaceSubImageUiTemplateData subImageUiTemplateData =
                new SmartspaceSubImageUiTemplateData.Builder(texts, images)
                        .setSubImageAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"))
                        .build();

        assertThat(subImageUiTemplateData.getSubImageTexts()).isEqualTo(texts);
        assertThat(subImageUiTemplateData.getSubImages()).isEqualTo(images);
        assertThat(subImageUiTemplateData.getSubImageAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"));

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        subImageUiTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceSubImageUiTemplateData copyData =
                SmartspaceSubImageUiTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(subImageUiTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }
}
