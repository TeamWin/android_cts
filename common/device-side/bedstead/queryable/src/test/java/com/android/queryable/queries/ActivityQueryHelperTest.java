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

package com.android.queryable.queries;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;

import com.android.queryable.Queryable;
import com.android.queryable.info.ActivityInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ActivityQueryHelperTest {

    private final Queryable mQuery = null;

    private static final Class<? extends Activity> CLASS_1 = Activity.class;
    private static final ActivityInfo CLASS_1_ACTIVITY_INFO = ActivityInfo.builder().activityClass(CLASS_1).build();
    private static final String CLASS_1_CLASS_NAME = CLASS_1.getName();
    private static final String CLASS_1_SIMPLE_NAME = CLASS_1.getSimpleName();
    private static final ActivityInfo CLASS_2_ACTIVITY_INFO =
            ActivityInfo.builder().activityClass("differentClassName").build();

    @Test
    public void matches_noRestrictions_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        assertThat(activityQueryHelper.matches(CLASS_1_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_isSameClassAs_doesMatch_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.isSameClassAs(CLASS_1);

        assertThat(activityQueryHelper.matches(CLASS_1_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_isSameClassAs_doesNotMatch_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.isSameClassAs(CLASS_1);

        assertThat(activityQueryHelper.matches(CLASS_2_ACTIVITY_INFO)).isFalse();
    }

    @Test
    public void matches_className_doesMatch_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.className().isEqualTo(CLASS_1_CLASS_NAME);

        assertThat(activityQueryHelper.matches(CLASS_1_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_className_doesNotMatch_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.className().isEqualTo(CLASS_1_CLASS_NAME);

        assertThat(activityQueryHelper.matches(CLASS_2_ACTIVITY_INFO)).isFalse();
    }

    @Test
    public void matches_simpleName_doesMatch_returnsTrue() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.simpleName().isEqualTo(CLASS_1_SIMPLE_NAME);

        assertThat(activityQueryHelper.matches(CLASS_1_ACTIVITY_INFO)).isTrue();
    }

    @Test
    public void matches_simpleName_doesNotMatch_returnsFalse() {
        ActivityQueryHelper<Queryable> activityQueryHelper =
                new ActivityQueryHelper<>(mQuery);

        activityQueryHelper.simpleName().isEqualTo(CLASS_1_SIMPLE_NAME);

        assertThat(activityQueryHelper.matches(CLASS_2_ACTIVITY_INFO)).isFalse();
    }

}
