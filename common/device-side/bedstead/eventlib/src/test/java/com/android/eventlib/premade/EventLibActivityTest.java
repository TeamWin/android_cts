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

import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.eventlib.EventLogs;
import com.android.eventlib.events.activities.ActivityCreatedEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventLibActivityTest {

    // This must exist as an <activity> in AndroidManifest.xml
    private static final String GENERATED_ACTIVITY_CLASS_NAME
            = "com.android.generatedEventLibActivity";

    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void launchEventLibActivity_logsActivityCreatedEvent() {
        Intent intent = new Intent();
        intent.setPackage(CONTEXT.getPackageName());
        intent.setClassName(CONTEXT.getPackageName(), EventLibActivity.class.getName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        CONTEXT.startActivity(intent);

        EventLogs<ActivityCreatedEvent> eventLogs = ActivityCreatedEvent
                .queryPackage(CONTEXT.getPackageName())
                .whereActivity().isSameClassAs(EventLibActivity.class);

        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void launchEventLibActivity_withGeneratedActivityClass_logsActivityCreatedEventWithCorrectClassName() {
        Intent intent = new Intent();
        intent.setPackage(CONTEXT.getPackageName());
        intent.setClassName(CONTEXT.getPackageName(), GENERATED_ACTIVITY_CLASS_NAME);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        CONTEXT.startActivity(intent);

        EventLogs<ActivityCreatedEvent> eventLogs = ActivityCreatedEvent
                .queryPackage(CONTEXT.getPackageName())
                .whereActivity().className().isEqualTo(GENERATED_ACTIVITY_CLASS_NAME);

        assertThat(eventLogs.poll()).isNotNull();
    }
}
