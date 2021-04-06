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

import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ActivityCreatedEventTest {

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final String STRING_KEY = "Key";
    private static final String STRING_VALUE = "Value";
    private static final String DIFFERENT_STRING_VALUE = "Value2";

    private final Bundle mSavedInstanceState = new Bundle();
    private final PersistableBundle mPersistentState = new PersistableBundle();

    private static final String DEFAULT_ACTIVITY_CLASS_NAME = ActivityContext.class.getName();
    private static final String CUSTOM_ACTIVITY_CLASS_NAME = "customActivityName";
    private static final String DIFFERENT_CUSTOM_ACTIVITY_CLASS_NAME = "customActivityName2";

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereSavedInstanceState_works() throws Exception {
        mSavedInstanceState.putString(STRING_KEY, STRING_VALUE);
        ActivityContext.runWithContext((activity) ->
                ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                        .log());

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereSavedInstanceState()
                            .key(STRING_KEY).stringValue().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.get().savedInstanceState()).isEqualTo(mSavedInstanceState);
    }

    @Test
    public void whereSavedInstanceState_skipsNonMatching() throws Exception {
        Bundle differentInstanceState = new Bundle();
        differentInstanceState.putString(STRING_KEY, DIFFERENT_STRING_VALUE);
        mSavedInstanceState.putString(STRING_KEY, STRING_VALUE);

        ActivityContext.runWithContext((activity) -> {
            ActivityCreatedEvent.logger(activity, differentInstanceState)
                    .log();
            ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                    .log();
        });

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereSavedInstanceState()
                            .key(STRING_KEY).stringValue().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.get().savedInstanceState()).isEqualTo(mSavedInstanceState);
    }

    @Test
    public void wherePersistentState_works() throws Exception {
        mPersistentState.putString(STRING_KEY, STRING_VALUE);
        ActivityContext.runWithContext((activity) ->
                ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                        .setPersistentState(mPersistentState)
                        .log());

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .wherePersistentState()
                            .key(STRING_KEY).stringValue().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.get().persistentState()).isEqualTo(mPersistentState);
    }

    @Test
    public void wherePersistentState_skipsNonMatching() throws Exception {
        PersistableBundle differentPersistentState = new PersistableBundle();
        differentPersistentState.putString(STRING_KEY, DIFFERENT_STRING_VALUE);
        mPersistentState.putString(STRING_KEY, STRING_VALUE);
        ActivityContext.runWithContext((activity) -> {
            ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                    .setPersistentState(differentPersistentState)
                    .log();
            ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                    .setPersistentState(mPersistentState)
                    .log();
        });

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .wherePersistentState()
                            .key(STRING_KEY).stringValue().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.get().persistentState()).isEqualTo(mPersistentState);
    }

    @Test
    public void whereActivity_customValueOnLogger_works() throws Exception {
        ActivityContext.runWithContext((activity) ->
                ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                        .setActivity(CUSTOM_ACTIVITY_CLASS_NAME)
                        .log());

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                .whereActivity().className().isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);
    }

    @Test
    public void whereActivity_customValueOnLogger_skipsNonMatching() throws Exception {
        ActivityContext.runWithContext((activity) -> {
            ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                    .setActivity(DIFFERENT_CUSTOM_ACTIVITY_CLASS_NAME)
                    .log();
            ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                    .setActivity(CUSTOM_ACTIVITY_CLASS_NAME)
                    .log();
        });

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().className().isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);
    }

    @Test
    public void whereActivity_defaultValue_works() throws Exception {
        ActivityContext.runWithContext((activity) ->
                ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                        .log());

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().className().isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);
    }

    @Test
    public void whereActivity_defaultValue_skipsNonMatching() throws Exception {
        ActivityContext.runWithContext((activity) -> {
            ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                    .setActivity(CUSTOM_ACTIVITY_CLASS_NAME)
                    .log();
            ActivityCreatedEvent.logger(activity, mSavedInstanceState)
                    .log();
        });

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().className().isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);
    }

}
