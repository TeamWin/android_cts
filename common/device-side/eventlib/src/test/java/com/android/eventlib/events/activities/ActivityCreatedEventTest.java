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

package com.android.eventlib.events.activities;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ActivityCreatedEventTest {

    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();

    // TODO(scottjonathan): Replace mock with ContextActivity when ready
    private final Activity mActivity = mock(Activity.class);
    private final Bundle mSavedInstanceState = new Bundle();
    private final PersistableBundle mPersistentState = new PersistableBundle();

    // These values come from the mock
    private final String DEFAULT_ACTIVITY_NAME = mActivity.getClass().getName();
    private final String DEFAULT_ACTIVITY_SIMPLE_NAME = mActivity.getClass().getSimpleName();

    private static final String CUSTOM_ACTIVITY_NAME = "customActivityName";

    @Before
    public void setUp() {
        when(mActivity.getApplicationContext()).thenReturn(CONTEXT);
        EventLogs.resetLogs();
    }

    @Test
    public void querySavedInstanceState_works() {
        ActivityCreatedEvent.logger(mActivity, mSavedInstanceState).log();

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                        .withSavedInstanceState(mSavedInstanceState);

        assertThat(eventLogs.get().savedInstanceState()).isEqualTo(mSavedInstanceState);
    }

    @Test
    public void queryPersistentState_works() {
        ActivityCreatedEvent.logger(mActivity, mSavedInstanceState)
                .setPersistentState(mPersistentState)
                .log();

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                        .withPersistentState(mPersistentState);

        assertThat(eventLogs.get().persistentState()).isEqualTo(mPersistentState);
    }

    @Test
    public void queryName_customValueOnLogger_works() {
        ActivityCreatedEvent.logger(mActivity, mSavedInstanceState)
                .setName(CUSTOM_ACTIVITY_NAME)
                .log();

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                .withActivityName(CUSTOM_ACTIVITY_NAME);

        assertThat(eventLogs.get().name()).isEqualTo(CUSTOM_ACTIVITY_NAME);
    }

    @Test
    public void querySimpleName_customValueOnLogger_works() {
        ActivityCreatedEvent.logger(mActivity, mSavedInstanceState)
                .setSimpleName(CUSTOM_ACTIVITY_NAME)
                .log();

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                        .withActivitySimpleName(CUSTOM_ACTIVITY_NAME);

        assertThat(eventLogs.get().simpleName()).isEqualTo(CUSTOM_ACTIVITY_NAME);
    }

    @Test
    public void queryName_defaultValue_works() {
        ActivityCreatedEvent.logger(mActivity, mSavedInstanceState)
                .log();

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                        .withActivityName(DEFAULT_ACTIVITY_NAME);

        assertThat(eventLogs.get().name()).isEqualTo(DEFAULT_ACTIVITY_NAME);
    }

    @Test
    public void querySimpleName_defaultValue_works() {
        ActivityCreatedEvent.logger(mActivity, mSavedInstanceState)
                .log();

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                        .withActivitySimpleName(DEFAULT_ACTIVITY_SIMPLE_NAME);

        assertThat(eventLogs.get().simpleName()).isEqualTo(DEFAULT_ACTIVITY_SIMPLE_NAME);
    }

    @Test
    public void queryActivityClass_works() {
        ActivityCreatedEvent.logger(mActivity, mSavedInstanceState)
                .log();

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(CONTEXT.getPackageName())
                        .withActivityClass(mActivity.getClass());

        assertThat(eventLogs.get()).isNotNull();
    }

}
