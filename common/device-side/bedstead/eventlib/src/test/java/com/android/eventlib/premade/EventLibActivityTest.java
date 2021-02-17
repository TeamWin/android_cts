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

package com.android.eventlib.premade;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.eventlib.EventLogs;
import com.android.eventlib.events.activities.ActivityCreatedEvent;
import com.android.eventlib.events.activities.ActivityDestroyedEvent;
import com.android.eventlib.events.activities.ActivityStartedEvent;
import com.android.eventlib.events.activities.ActivityStoppedEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventLibActivityTest {

    // This must exist as an <activity> in AndroidManifest.xml
    private static final String GENERATED_ACTIVITY_CLASS_NAME
            = "com.android.generatedEventLibActivity";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final Context sContext = sInstrumentation.getContext();

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void launchEventLibActivity_logsActivityCreatedEvent() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), EventLibActivity.class.getName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        sContext.startActivity(intent);

        EventLogs<ActivityCreatedEvent> eventLogs = ActivityCreatedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().isSameClassAs(EventLibActivity.class);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void launchEventLibActivity_withGeneratedActivityClass_logsActivityCreatedEventWithCorrectClassName() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), GENERATED_ACTIVITY_CLASS_NAME);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        sContext.startActivity(intent);

        EventLogs<ActivityCreatedEvent> eventLogs = ActivityCreatedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().className().isEqualTo(GENERATED_ACTIVITY_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void startEventLibActivity_logsActivityStartedEvent() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), EventLibActivity.class.getName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = sInstrumentation.startActivitySync(intent);
        EventLogs.resetLogs();

        sInstrumentation.callActivityOnStart(activity);

        EventLogs<ActivityStartedEvent> eventLogs = ActivityStartedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().isSameClassAs(EventLibActivity.class);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void startEventLibActivity_withGeneratedActivityClass_logsActivityStartedEventWithCorrectClassName() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), GENERATED_ACTIVITY_CLASS_NAME);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = sInstrumentation.startActivitySync(intent);
        EventLogs.resetLogs();

        sInstrumentation.callActivityOnStart(activity);

        EventLogs<ActivityStartedEvent> eventLogs = ActivityStartedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().className().isEqualTo(GENERATED_ACTIVITY_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void stopEventLibActivity_logsActivityStoppedEvent() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), EventLibActivity.class.getName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = sInstrumentation.startActivitySync(intent);
        EventLogs.resetLogs();

        sInstrumentation.callActivityOnStop(activity);

        EventLogs<ActivityStoppedEvent> eventLogs = ActivityStoppedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().isSameClassAs(EventLibActivity.class);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void stopEventLibActivity_withGeneratedActivityClass_logsActivityStoppedEventWithCorrectClassName() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), GENERATED_ACTIVITY_CLASS_NAME);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = sInstrumentation.startActivitySync(intent);
        EventLogs.resetLogs();

        sInstrumentation.callActivityOnStop(activity);

        EventLogs<ActivityStoppedEvent> eventLogs = ActivityStoppedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().className().isEqualTo(GENERATED_ACTIVITY_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void destroyEventLibActivity_logsActivityDestroyedEvent() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), EventLibActivity.class.getName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = sInstrumentation.startActivitySync(intent);
        EventLogs.resetLogs();

        activity.finish();

        EventLogs<ActivityDestroyedEvent> eventLogs = ActivityDestroyedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().isSameClassAs(EventLibActivity.class);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void destroyEventLibActivity_withGeneratedActivityClass_logsActivityDestroyedEventWithCorrectClassName() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), GENERATED_ACTIVITY_CLASS_NAME);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = sInstrumentation.startActivitySync(intent);
        EventLogs.resetLogs();

        activity.finish();

        EventLogs<ActivityDestroyedEvent> eventLogs = ActivityDestroyedEvent
                .queryPackage(sContext.getPackageName())
                .whereActivity().className().isEqualTo(GENERATED_ACTIVITY_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();
    }
}
