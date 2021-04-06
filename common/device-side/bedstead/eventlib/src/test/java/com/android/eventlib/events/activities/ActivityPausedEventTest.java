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

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ActivityPausedEventTest {

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();

    private static final String DEFAULT_ACTIVITY_CLASS_NAME = ActivityContext.class.getName();
    private static final String CUSTOM_ACTIVITY_CLASS_NAME = "customActivityName";
    private static final String DIFFERENT_CUSTOM_ACTIVITY_CLASS_NAME = "customActivityName2";

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereActivity_customValueOnLogger_works() throws Exception {
        ActivityContext.runWithContext((activity) ->
                ActivityPausedEvent.logger(activity)
                        .setActivity(CUSTOM_ACTIVITY_CLASS_NAME)
                        .log());

        EventLogs<ActivityPausedEvent> eventLogs =
                ActivityPausedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().className().isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);
    }

    @Test
    public void whereActivity_customValueOnLogger_skipsNonMatching() throws Exception {
        ActivityContext.runWithContext((activity) -> {
            ActivityPausedEvent.logger(activity)
                    .setActivity(DIFFERENT_CUSTOM_ACTIVITY_CLASS_NAME)
                    .log();
            ActivityPausedEvent.logger(activity)
                    .setActivity(CUSTOM_ACTIVITY_CLASS_NAME)
                    .log();
        });

        EventLogs<ActivityPausedEvent> eventLogs =
                ActivityPausedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().className().isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(CUSTOM_ACTIVITY_CLASS_NAME);
    }

    @Test
    public void whereActivity_defaultValue_works() throws Exception {
        ActivityContext.runWithContext((activity) ->
                ActivityPausedEvent.logger(activity)
                        .log());

        EventLogs<ActivityPausedEvent> eventLogs =
                ActivityPausedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().className().isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);
    }

    @Test
    public void whereActivity_defaultValue_skipsNonMatching() throws Exception {
        ActivityContext.runWithContext((activity) -> {
            ActivityPausedEvent.logger(activity)
                    .setActivity(CUSTOM_ACTIVITY_CLASS_NAME)
                    .log();
            ActivityPausedEvent.logger(activity)
                    .log();
        });

        EventLogs<ActivityPausedEvent> eventLogs =
                ActivityPausedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().className().isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.get().activity().className()).isEqualTo(DEFAULT_ACTIVITY_CLASS_NAME);
    }

}
