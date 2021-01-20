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

import com.android.eventlib.events.CustomEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StringQueryHelperTest {

    private final CustomEvent.CustomEventQuery mQuery =
            CustomEvent.queryPackage("testPackage"); // package is not used
    private static final String STRING_VALUE = "String";
    private static final String DIFFERENT_STRING_VALUE = "String2";

    @Test
    public void matches_noRestrictions_returnsTrue() {
        StringQueryHelper<CustomEvent.CustomEventQuery> stringQueryHelper =
                new StringQueryHelper<>(mQuery);

        assertThat(stringQueryHelper.matches(STRING_VALUE)).isTrue();
    }

    @Test
    public void matches_isEqualTo_meetsRestriction_returnsTrue() {
        StringQueryHelper<CustomEvent.CustomEventQuery> stringQueryHelper =
                new StringQueryHelper<>(mQuery);

        stringQueryHelper.isEqualTo(STRING_VALUE);

        assertThat(stringQueryHelper.matches(STRING_VALUE)).isTrue();
    }

    @Test
    public void matches_isEqualTo_doesNotMeetRestriction_returnsFalse() {
        StringQueryHelper<CustomEvent.CustomEventQuery> stringQueryHelper =
                new StringQueryHelper<>(mQuery);

        stringQueryHelper.isEqualTo(DIFFERENT_STRING_VALUE);

        assertThat(stringQueryHelper.matches(STRING_VALUE)).isFalse();
    }
}
