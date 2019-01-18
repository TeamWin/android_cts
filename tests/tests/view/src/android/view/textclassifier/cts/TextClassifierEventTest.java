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
    private static final float TOLERANCE = 0.000001f;

    @Test
    public void testMinimumEvent() {
        final TextClassifierEvent event = createMinimalTextClassifierEvent();

        assertMinimalTextClassifierEvent(event);
    }

    @Test
    public void testParcelUnparcel_minimumEvent() {
        final TextClassifierEvent event = createMinimalTextClassifierEvent();

        final Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, event.describeContents());
        parcel.setDataPosition(0);
        final TextClassifierEvent result = TextClassifierEvent.CREATOR.createFromParcel(parcel);

        assertMinimalTextClassifierEvent(result);
    }

    @Test
    public void testFullEvent() {
        final long eventTime = System.currentTimeMillis();
        final TextClassifierEvent event = createFullTextClassifierEvent(eventTime);

        assertFullEvent(event, eventTime);
    }

    @Test
    public void testParcelUnparcel_fullEvent() {
        final long eventTime = System.currentTimeMillis();
        final TextClassifierEvent event = createFullTextClassifierEvent(eventTime);

        final Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, event.describeContents());
        parcel.setDataPosition(0);
        final TextClassifierEvent result = TextClassifierEvent.CREATOR.createFromParcel(parcel);

        assertFullEvent(result, eventTime);
    }

    private TextClassifierEvent createFullTextClassifierEvent(long eventTime) {
        final Bundle extra = new Bundle();
        extra.putString("key", "value");
        return new TextClassifierEvent.Builder(
                TextClassifierEvent.CATEGORY_LINKIFY,
                TextClassifierEvent.TYPE_LINK_CLICKED)
                .setEventIndex(2)
                .setEventTime(eventTime)
                .setEntityTypes(TextClassifier.TYPE_ADDRESS)
                .setRelativeWordStartIndex(1)
                .setRelativeWordEndIndex(2)
                .setRelativeSuggestedWordStartIndex(-1)
                .setRelativeSuggestedWordEndIndex(3)
                .setLanguage("en")
                .setResultId("androidtc-en-v606-1234")
                .setActionIndices(1, 2, 5)
                .setExtras(extra)
                .setEventContext(new TextClassificationContext.Builder(
                        "pkg", TextClassifier.WIDGET_TYPE_TEXTVIEW)
                        .setWidgetVersion(TextView.class.getName())
                        .build())
                .setScore(0.5f)
                .setEntityTypes(TextClassifier.TYPE_ADDRESS, TextClassifier.TYPE_DATE)
                .build();
    }

    private void assertFullEvent(TextClassifierEvent event, long expectedEventTime) {
        assertThat(event.getEventCategory()).isEqualTo(TextClassifierEvent.CATEGORY_LINKIFY);
        assertThat(event.getEventType()).isEqualTo(TextClassifierEvent.TYPE_LINK_CLICKED);
        assertThat(event.getEventIndex()).isEqualTo(2);
        assertThat(event.getEventTime()).isEqualTo(expectedEventTime);
        assertThat(event.getEntityTypes()).asList()
                .containsExactly(TextClassifier.TYPE_ADDRESS, TextClassifier.TYPE_DATE);
        assertThat(event.getRelativeWordStartIndex()).isEqualTo(1);
        assertThat(event.getRelativeWordEndIndex()).isEqualTo(2);
        assertThat(event.getRelativeSuggestedWordStartIndex()).isEqualTo(-1);
        assertThat(event.getRelativeSuggestedWordEndIndex()).isEqualTo(3);
        assertThat(event.getLanguage()).isEqualTo("en");
        assertThat(event.getResultId()).isEqualTo("androidtc-en-v606-1234");
        assertThat(event.getActionIndices()).asList().containsExactly(1, 2, 5);
        assertThat(event.getExtras().get("key")).isEqualTo("value");
        assertThat(event.getEventContext().getPackageName()).isEqualTo("pkg");
        assertThat(event.getEventContext().getWidgetType())
                .isEqualTo(TextClassifier.WIDGET_TYPE_TEXTVIEW);
        assertThat(event.getEventContext().getWidgetVersion()).isEqualTo(TextView.class.getName());
        assertThat(event.getScore()).isWithin(TOLERANCE).of(0.5f);
    }

    private TextClassifierEvent createMinimalTextClassifierEvent() {
        return new TextClassifierEvent.Builder(
                TextClassifierEvent.CATEGORY_UNDEFINED, TextClassifierEvent.TYPE_UNDEFINED)
                .build();
    }

    private void assertMinimalTextClassifierEvent(TextClassifierEvent event) {
        assertThat(event.getEventCategory()).isEqualTo(TextClassifierEvent.CATEGORY_UNDEFINED);
        assertThat(event.getEventType()).isEqualTo(TextClassifierEvent.TYPE_UNDEFINED);
        assertThat(event.getEventIndex()).isEqualTo(0);
        assertThat(event.getEventTime()).isEqualTo(0);
        assertThat(event.getEntityTypes()).isEmpty();
        assertThat(event.getRelativeWordStartIndex()).isEqualTo(0);
        assertThat(event.getRelativeWordEndIndex()).isEqualTo(0);
        assertThat(event.getRelativeSuggestedWordStartIndex()).isEqualTo(0);
        assertThat(event.getRelativeSuggestedWordEndIndex()).isEqualTo(0);
        assertThat(event.getLanguage()).isNull();
        assertThat(event.getResultId()).isNull();
        assertThat(event.getActionIndices()).isEmpty();
        assertThat(event.getExtras().size()).isEqualTo(0);
        assertThat(event.getEventContext()).isNull();
        assertThat(event.getEntityTypes()).isEmpty();
    }
}
