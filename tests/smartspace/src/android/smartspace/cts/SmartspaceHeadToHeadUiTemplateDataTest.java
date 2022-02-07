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

import android.app.smartspace.uitemplatedata.SmartspaceHeadToHeadUiTemplateData;
import android.app.smartspace.uitemplatedata.SmartspaceText;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SmartspaceHeadToHeadUiTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceHeadToHeadUiTemplateDataTest {

    private static final String TAG = "SmartspaceHeadToHeadUiTemplateDataTest";

    @Test
    public void testCreateSmartspaceHeadToHeadUiTemplateData() {
        SmartspaceHeadToHeadUiTemplateData headToHeadUiTemplateData =
                new SmartspaceHeadToHeadUiTemplateData.Builder()
                        .setHeadToHeadTitle(new SmartspaceText.Builder("title").build())
                        .setHeadToHeadFirstCompetitorIcon(
                                SmartspaceTestUtils.createSmartspaceIcon("icon1"))
                        .setHeadToHeadSecondCompetitorIcon(
                                SmartspaceTestUtils.createSmartspaceIcon("icon2"))
                        .setHeadToHeadFirstCompetitorText(
                                new SmartspaceText.Builder("text1").build())
                        .setHeadToHeadSecondCompetitorText(
                                new SmartspaceText.Builder("text1").build())
                        .setHeadToHeadAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"))
                        .build();

        assertThat(headToHeadUiTemplateData.getHeadToHeadTitle()).isEqualTo(
                new SmartspaceText.Builder("title").build());
        assertThat(headToHeadUiTemplateData.getHeadToHeadFirstCompetitorIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("icon1"));
        assertThat(headToHeadUiTemplateData.getHeadToHeadSecondCompetitorIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("icon2"));
        assertThat(headToHeadUiTemplateData.getHeadToHeadFirstCompetitorText()).isEqualTo(
                new SmartspaceText.Builder("text1").build());
        assertThat(headToHeadUiTemplateData.getHeadToHeadSecondCompetitorText()).isEqualTo(
                new SmartspaceText.Builder("text1").build());
        assertThat(headToHeadUiTemplateData.getHeadToHeadAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"));

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        headToHeadUiTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceHeadToHeadUiTemplateData copyData =
                SmartspaceHeadToHeadUiTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(headToHeadUiTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }
}
