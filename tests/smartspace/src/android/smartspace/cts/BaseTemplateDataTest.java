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
import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemLoggingInfo;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link BaseTemplateDataTest}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class BaseTemplateDataTest {

    private static final String TAG = "BaseTemplateDataTest";

    @Test
    public void testCreateBaseTemplateData() {
        BaseTemplateData baseTemplateData =
                new BaseTemplateData.Builder(SmartspaceTarget.UI_TEMPLATE_DEFAULT)
                        .setTitleText(new Text.Builder("title").build())
                        .setTitleIcon(SmartspaceTestUtils.createSmartspaceIcon("title icon"))
                        .setSubtitleText(new Text.Builder("subtitle").build())
                        .setSubtitleIcon(SmartspaceTestUtils.createSmartspaceIcon("subtitle icon"))
                        .setPrimaryTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "primary action"))
                        .setPrimaryLoggingInfo(new SubItemLoggingInfo.Builder(0, 0).build())
                        .setSupplementalSubtitleText(
                                new Text.Builder("supplemental subtitle text").build())
                        .setSupplementalSubtitleIcon(
                                SmartspaceTestUtils.createSmartspaceIcon(
                                        "supplemental subtitle icon"))
                        .setSupplementalSubtitleTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "supplemental subtitle tap action"))
                        .setSupplementalSubtitleLoggingInfo(
                                new SubItemLoggingInfo.Builder(1, 1).build())
                        .setSupplementalText(new Text.Builder("supplemental text").build())
                        .setSupplementalIcon(
                                SmartspaceTestUtils.createSmartspaceIcon("supplemental icon"))
                        .setSupplementalTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "supplemental tap action"))
                        .setSupplementalLoggingInfo(new SubItemLoggingInfo.Builder(2, 2).build())
                        .setSupplementalAlarmText(new Text.Builder(
                                "supplemental alarm text").build())
                        .setLayoutWeight(1)
                        .build();

        assertThat(baseTemplateData.getTitleText()).isEqualTo(new Text.Builder("title").build());
        assertThat(baseTemplateData.getTitleIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("title icon"));
        assertThat(baseTemplateData.getSubtitleText()).isEqualTo(
                new Text.Builder("subtitle").build());
        assertThat(baseTemplateData.getSubtitleIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("subtitle icon"));
        assertThat(baseTemplateData.getPrimaryTapAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "primary action"));
        assertThat(baseTemplateData.getPrimaryLoggingInfo()).isEqualTo(
                new SubItemLoggingInfo.Builder(0, 0).build());
        assertThat(baseTemplateData.getSupplementalSubtitleText()).isEqualTo(
                new Text.Builder("supplemental subtitle text").build());
        assertThat(baseTemplateData.getSupplementalSubtitleIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("supplemental subtitle icon"));
        assertThat(baseTemplateData.getSupplementalSubtitleTapAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                        "supplemental subtitle tap action"));
        assertThat(baseTemplateData.getSupplementalSubtitleLoggingInfo()).isEqualTo(
                new SubItemLoggingInfo.Builder(1, 1).build());
        assertThat(baseTemplateData.getSupplementalText()).isEqualTo(
                new Text.Builder("supplemental text").build());
        assertThat(baseTemplateData.getSupplementalIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("supplemental icon"));
        assertThat(baseTemplateData.getSupplementalTapAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                        "supplemental tap action"));
        assertThat(baseTemplateData.getSupplementalLoggingInfo()).isEqualTo(
                new SubItemLoggingInfo.Builder(2, 2).build());
        assertThat(baseTemplateData.getSupplementalAlarmText()).isEqualTo(
                new Text.Builder("supplemental alarm text").build());
        assertThat(baseTemplateData.getLayoutWeight()).isEqualTo(1);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        baseTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BaseTemplateData copyData = BaseTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(baseTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }
}
