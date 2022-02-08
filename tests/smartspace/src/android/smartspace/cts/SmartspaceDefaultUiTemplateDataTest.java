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

import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.uitemplatedata.SmartspaceDefaultUiTemplateData;
import android.app.smartspace.uitemplatedata.SmartspaceText;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SmartspaceDefaultUiTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceDefaultUiTemplateDataTest {

    private static final String TAG = "SmartspaceDefaultUiTemplateDataTest";

    @Test
    public void testCreateSmartspaceDefaultUiTemplateData() {
        SmartspaceDefaultUiTemplateData defaultUiTemplateData =
                new SmartspaceDefaultUiTemplateData.Builder(SmartspaceTarget.UI_TEMPLATE_DEFAULT)
                        .setTitleText(new SmartspaceText.Builder("title").build())
                        .setTitleIcon(SmartspaceTestUtils.createSmartspaceIcon("title icon"))
                        .setSubtitleText(new SmartspaceText.Builder("subtitle").build())
                        .setSubtitleIcon(SmartspaceTestUtils.createSmartspaceIcon("subtitle icon"))
                        .setPrimaryTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "primary action"))
                        .setSupplementalSubtitleText(
                                new SmartspaceText.Builder("supplemental subtitle").build())
                        .setSupplementalSubtitleIcon(
                                SmartspaceTestUtils.createSmartspaceIcon(
                                        "supplemental subtitle icon"))
                        .setSupplementalSubtitleTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "supplemental tap action"))
                        .setSupplementalAlarmText(new SmartspaceText.Builder(
                                "supplemental alarm text").build()).build();

        assertThat(defaultUiTemplateData.getTitleText()).isEqualTo(
                new SmartspaceText.Builder("title").build());
        assertThat(defaultUiTemplateData.getTitleIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("title icon"));
        assertThat(defaultUiTemplateData.getSubtitleText()).isEqualTo(
                new SmartspaceText.Builder("subtitle").build());
        assertThat(defaultUiTemplateData.getSubtitleIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("subtitle icon"));
        assertThat(defaultUiTemplateData.getPrimaryTapAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "primary action"));
        assertThat(defaultUiTemplateData.getSupplementalSubtitleText()).isEqualTo(
                new SmartspaceText.Builder("supplemental subtitle").build());
        assertThat(defaultUiTemplateData.getSupplementalSubtitleIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("supplemental subtitle icon"));
        assertThat(defaultUiTemplateData.getSupplementalSubtitleTapAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                        "supplemental tap action"));
        assertThat(defaultUiTemplateData.getSupplementalAlarmText()).isEqualTo(
                new SmartspaceText.Builder("supplemental alarm text").build());

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        defaultUiTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceDefaultUiTemplateData copyUiTemplateData =
                SmartspaceDefaultUiTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(defaultUiTemplateData).isEqualTo(copyUiTemplateData);
        parcel.recycle();
    }
}
