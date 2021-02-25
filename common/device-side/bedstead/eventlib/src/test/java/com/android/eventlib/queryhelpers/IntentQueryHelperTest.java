/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.eventlib.queryhelpers;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import com.android.eventlib.events.CustomEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IntentQueryHelperTest {

    private final CustomEvent.CustomEventQuery mQuery =
            CustomEvent.queryPackage("testPackage"); // package is not used
    private static final String STRING_VALUE = "String";
    private static final String DIFFERENT_STRING_VALUE = "String2";

    @Test
    public void matches_noRestrictions_returnsTrue() {
        Intent intent = new Intent();
        IntentQueryHelper<CustomEvent.CustomEventQuery> intentQueryHelper =
                new IntentQueryHelper<>(mQuery);

        assertThat(intentQueryHelper.matches(intent)).isTrue();
    }

    @Test
    public void matches_action_meetsRestriction_returnsTrue() {
        Intent intent = new Intent();
        intent.setAction(STRING_VALUE);
        IntentQueryHelper<CustomEvent.CustomEventQuery> intentQueryHelper =
                new IntentQueryHelper<>(mQuery);

        intentQueryHelper.action().isEqualTo(STRING_VALUE);

        assertThat(intentQueryHelper.matches(intent)).isTrue();
    }

    @Test
    public void matches_action_doesNotMeetRestriction_returnsFalse() {
        Intent intent = new Intent();
        intent.setAction(STRING_VALUE);
        IntentQueryHelper<CustomEvent.CustomEventQuery> intentQueryHelper =
                new IntentQueryHelper<>(mQuery);

        intentQueryHelper.action().isEqualTo(DIFFERENT_STRING_VALUE);

        assertThat(intentQueryHelper.matches(intent)).isFalse();
    }

    @Test
    public void matches_extras_meetsRestriction_returnsTrue() {
        Intent intent = new Intent();
        intent.putExtra(/* key= */ STRING_VALUE, /* value= */ STRING_VALUE);
        IntentQueryHelper<CustomEvent.CustomEventQuery> intentQueryHelper =
                new IntentQueryHelper<>(mQuery);

        intentQueryHelper.extras().key(STRING_VALUE).stringValue().isEqualTo(STRING_VALUE);

        assertThat(intentQueryHelper.matches(intent)).isTrue();
    }

    @Test
    public void matches_extras_doesNotMeetRestriction_returnsFalse() {
        Intent intent = new Intent();
        intent.putExtra(/* key= */ STRING_VALUE, /* value= */ STRING_VALUE);
        IntentQueryHelper<CustomEvent.CustomEventQuery> intentQueryHelper =
                new IntentQueryHelper<>(mQuery);

        intentQueryHelper.extras().key(STRING_VALUE).stringValue()
                .isEqualTo(DIFFERENT_STRING_VALUE);

        assertThat(intentQueryHelper.matches(intent)).isFalse();
    }
}
