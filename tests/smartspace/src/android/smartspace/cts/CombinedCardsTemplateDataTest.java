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
import android.app.smartspace.uitemplatedata.CombinedCardsTemplateData;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link CombinedCardsTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class CombinedCardsTemplateDataTest {

    private static final String TAG = "CombinedCardsTemplateDataTest";

    @Test
    public void testCreateCombinedCardsTemplateData() {
        List<BaseTemplateData> dataList = new ArrayList<>();
        dataList.add(createBaseTemplateData());
        dataList.add(createBaseTemplateData());
        CombinedCardsTemplateData combinedCardsTemplateData =
                new CombinedCardsTemplateData.Builder(dataList).build();

        assertThat(combinedCardsTemplateData.getCombinedCardDataList()).isEqualTo(dataList);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        combinedCardsTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CombinedCardsTemplateData copyData =
                CombinedCardsTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(combinedCardsTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }

    private BaseTemplateData createBaseTemplateData() {
        return new BaseTemplateData.Builder(SmartspaceTarget.UI_TEMPLATE_DEFAULT)
                .setTitleText(new Text.Builder("title").build())
                .setTitleIcon(SmartspaceTestUtils.createSmartspaceIcon("title icon"))
                .setSubtitleText(new Text.Builder("subtitle").build())
                .setSubtitleIcon(SmartspaceTestUtils.createSmartspaceIcon("subtitle icon"))
                .setPrimaryTapAction(SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                        "primary action"))
                .setSupplementalSubtitleText(
                        new Text.Builder("supplemental subtitle").build())
                .setSupplementalSubtitleIcon(
                        SmartspaceTestUtils.createSmartspaceIcon("supplemental subtitle icon"))
                .setSupplementalSubtitleTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                "supplemental tap action"))
                .setSupplementalAlarmText(new Text.Builder(
                        "supplemental alarm text").build()).build();
    }
}
