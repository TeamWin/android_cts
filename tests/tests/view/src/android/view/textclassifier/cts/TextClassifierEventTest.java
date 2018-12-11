/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.view.textclassifier.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.Parcel;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.textclassifier.TextClassificationContext;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextClassifierEvent;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassifierEventTest {

    @Test
    public void testMinimumEvent() {
        final TextClassifierEvent event = new TextClassifierEvent.Builder(
                TextClassifierEvent.CATEGORY_UNDEFINED, TextClassifierEvent.TYPE_UNDEFINED)
                .build();

        assertThat(event.getEventCategory()).isEqualTo(TextClassifierEvent.CATEGORY_UNDEFINED);
        assertThat(event.getEventType()).isEqualTo(TextClassifierEvent.TYPE_UNDEFINED);
        assertThat(event.getEventIndex()).isEqualTo(0);
        assertThat(event.getEventTime()).isEqualTo(0);
        assertThat(event.getEntityType()).isNull();
        assertThat(event.getRelativeWordStartIndex()).isEqualTo(0);
        assertThat(event.getRelativeWordEndIndex()).isEqualTo(0);
        assertThat(event.getRelativeSuggestedWordStartIndex()).isEqualTo(0);
        assertThat(event.getRelativeSuggestedWordEndIndex()).isEqualTo(0);
        assertThat(event.getLanguage()).isNull();
        assertThat(event.getResultId()).isNull();
        assertThat(event.getActionIndices()).isEmpty();
        assertThat(event.getExtras()).isEqualTo(Bundle.EMPTY);
        assertThat(event.getEventContext()).isNull();
    }

    @Test
    public void testFullEvent() {
        final Bundle extra = new Bundle();
        extra.putString("key", "value");
        final long now = System.currentTimeMillis();
        final String resultId = "androidtc-en-v606-1234";
        final TextClassifierEvent event = new TextClassifierEvent.Builder(
                TextClassifierEvent.CATEGORY_LINKIFY,
                TextClassifierEvent.TYPE_LINK_CLICKED)
                .setEventIndex(2)
                .setEventTime(now)
                .setEntityType(TextClassifier.TYPE_ADDRESS)
                .setRelativeWordStartIndex(1)
                .setRelativeWordEndIndex(2)
                .setRelativeSuggestedWordStartIndex(-1)
                .setRelativeSuggestedWordEndIndex(3)
                .setLanguage("en")
                .setResultId(resultId)
                .setActionIndices(1, 2, 5)
                .setExtras(extra)
                .setEventContext(new TextClassificationContext.Builder(
                        "pkg", TextClassifier.WIDGET_TYPE_TEXTVIEW)
                        .setWidgetVersion(TextView.class.getName())
                        .build())
                .build();

        assertThat(event.getEventCategory()).isEqualTo(TextClassifierEvent.CATEGORY_LINKIFY);
        assertThat(event.getEventType()).isEqualTo(TextClassifierEvent.TYPE_LINK_CLICKED);
        assertThat(event.getEventIndex()).isEqualTo(2);
        assertThat(event.getEventTime()).isEqualTo(now);
        assertThat(event.getEntityType()).isEqualTo(TextClassifier.TYPE_ADDRESS);
        assertThat(event.getRelativeWordStartIndex()).isEqualTo(1);
        assertThat(event.getRelativeWordEndIndex()).isEqualTo(2);
        assertThat(event.getRelativeSuggestedWordStartIndex()).isEqualTo(-1);
        assertThat(event.getRelativeSuggestedWordEndIndex()).isEqualTo(3);
        assertThat(event.getLanguage()).isEqualTo("en");
        assertThat(event.getResultId()).isEqualTo(resultId);
        assertThat(event.getActionIndices()).asList().containsExactly(1, 2, 5);
        assertThat(event.getExtras().get("key")).isEqualTo("value");
        assertThat(event.getEventContext().getPackageName()).isEqualTo("pkg");
        assertThat(event.getEventContext().getWidgetType())
                .isEqualTo(TextClassifier.WIDGET_TYPE_TEXTVIEW);
        assertThat(event.getEventContext().getWidgetVersion()).isEqualTo(TextView.class.getName());
    }

    @Test
    public void testParcelUnparcel() {
        final Bundle extra = new Bundle();
        extra.putString("k", "v");
        final TextClassifierEvent event = new TextClassifierEvent.Builder(
                TextClassifierEvent.CATEGORY_SELECTION,
                TextClassifierEvent.TYPE_SELECTION_MODIFIED)
                .setEventIndex(1)
                .setEventTime(1000)
                .setEntityType(TextClassifier.TYPE_DATE)
                .setRelativeWordStartIndex(4)
                .setRelativeWordEndIndex(3)
                .setRelativeSuggestedWordStartIndex(2)
                .setRelativeSuggestedWordEndIndex(1)
                .setLanguage("de")
                .setResultId("id")
                .setActionIndices(3)
                .setExtras(extra)
                .setEventContext(new TextClassificationContext.Builder(
                        InstrumentationRegistry.getTargetContext().getPackageName(),
                        TextClassifier.WIDGET_TYPE_UNSELECTABLE_TEXTVIEW)
                        .setWidgetVersion("notification")
                        .build())
                .build();

        final Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, event.describeContents());
        parcel.setDataPosition(0);
        final TextClassifierEvent result = TextClassifierEvent.CREATOR.createFromParcel(parcel);

        assertThat(result.getEventCategory()).isEqualTo(TextClassifierEvent.CATEGORY_SELECTION);
        assertThat(result.getEventType()).isEqualTo(TextClassifierEvent.TYPE_SELECTION_MODIFIED);
        assertThat(result.getEventIndex()).isEqualTo(1);
        assertThat(result.getEventTime()).isEqualTo(1000);
        assertThat(result.getEntityType()).isEqualTo(TextClassifier.TYPE_DATE);
        assertThat(result.getRelativeWordStartIndex()).isEqualTo(4);
        assertThat(result.getRelativeWordEndIndex()).isEqualTo(3);
        assertThat(result.getRelativeSuggestedWordStartIndex()).isEqualTo(2);
        assertThat(result.getRelativeSuggestedWordEndIndex()).isEqualTo(1);
        assertThat(result.getLanguage()).isEqualTo("de");
        assertThat(result.getResultId()).isEqualTo("id");
        assertThat(result.getActionIndices()).asList().containsExactly(3);
        assertThat(result.getExtras().get("k")).isEqualTo("v");
        assertThat(result.getEventContext().getPackageName())
                .isEqualTo(InstrumentationRegistry.getTargetContext().getPackageName());
        assertThat(result.getEventContext().getWidgetType())
                .isEqualTo(TextClassifier.WIDGET_TYPE_UNSELECTABLE_TEXTVIEW);
        assertThat(result.getEventContext().getWidgetVersion()).isEqualTo("notification");
    }
}
