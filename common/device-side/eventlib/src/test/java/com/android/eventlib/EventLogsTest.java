/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.eventlib.events.CustomEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;

@RunWith(JUnit4.class)
public class EventLogsTest {
    private static final Context CONTEXT = InstrumentationRegistry.getInstrumentation().getContext();

    private static final String TEST_TAG1 = "TEST_TAG1";
    private static final String TEST_TAG2 = "TEST_TAG2";
    private static final String DATA_1 = "DATA_1";
    private static final String DATA_2 = "DATA_2";

    private static final Duration VERY_SHORT_POLL_WAIT = Duration.ofMillis(20);
    private static final long ONE_SECOND_DELAY_MILLIS = Duration.ofSeconds(1).toMillis();

    private boolean hasScheduledEvents = false;

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @After
    public void teardown() {
        if (hasScheduledEvents) {
            // Clear the queue
            CustomEvent.queryPackage(CONTEXT.getPackageName()).poll();
        }
    }

    @Test
    public void resetLogs_get_doesNotReturnLogs() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();

        EventLogs.resetLogs();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());
        assertThat(eventLogs.get()).isNull();
    }

    @Test
    public void resetLogs_next_doesNotReturnLogs() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();

        EventLogs.resetLogs();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());
        assertThat(eventLogs.next()).isNull();
    }

    @Test
    public void resetLogs_poll_doesNotReturnLogs() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();

        EventLogs.resetLogs();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());
        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void get_nothingLogged_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.get()).isNull();
    }

    @Test
    public void next_nothingLogged_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.next()).isNull();
    }

    @Test
    public void poll_nothingLogged_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void get_alreadyLogged_returnsEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.get()).isNotNull();
    }

    @Test
    public void next_alreadyLogged_returnsFirstEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.next().data()).isEqualTo(DATA_1);
    }

    @Test
    public void poll_alreadyLogged_returnsFirstEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_1);
    }

    @Test
    public void next_hasReturnedAllEvents_returnsNull() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);
        eventLogs.next();

        assertThat(eventLogs.next()).isNull();
    }

    @Test
    public void poll_hasReturnedAllEvents_returnsNull() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);
        eventLogs.poll();

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void next_returnsNextUnseenEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);
        eventLogs.next();

        assertThat(eventLogs.next().data()).isEqualTo(DATA_2);
    }

    @Test
    public void next_previouslyPolled_returnsNextUnseenEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);
        eventLogs.poll();

        assertThat(eventLogs.next().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_previouslyCalledNext_returnsNextUnseenEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);
        eventLogs.next();

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_returnsNextUnseenEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);
        eventLogs.poll();

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void get_loggedPreviouslyWithDifferentData_returnsCorrectEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG2)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.get().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void next_loggedPreviouslyWithDifferentData_returnsCorrectEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG2)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.next().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void poll_loggedPreviouslyWithDifferentData_returnsCorrectEvent() {
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG2)
                .log();
        CustomEvent.logger(CONTEXT)
                .setTag(TEST_TAG1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void get_multipleLoggedEvents_returnsFirstEvent() {
        CustomEvent.logger(CONTEXT)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setData(DATA_2)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());

        assertThat(eventLogs.get().data()).isEqualTo(DATA_1);
    }

    @Test
    public void get_multipleCalls_alwaysReturnsFirstEvent() {
        CustomEvent.logger(CONTEXT)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(CONTEXT)
                .setData(DATA_2)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());
        eventLogs.get();

        assertThat(eventLogs.get().data()).isEqualTo(DATA_1);
    }

    @Test
    public void get_loggedAfter_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        scheduleCustomEventInOneSecond();

        assertThat(eventLogs.get()).isNull();
    }

    @Test
    public void next_loggedAfter_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());

        scheduleCustomEventInOneSecond();

        assertThat(eventLogs.next()).isNull();
    }

    @Test
    public void poll_loggedAfter_returnsEvent() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName())
                .withTag(TEST_TAG1);

        // We don't use scheduleCustomEventInOneSecond(); because we don't need any special teardown
        // as we're blocking for the event in this method
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                        CustomEvent.logger(CONTEXT)
                                .setTag(TEST_TAG1)
                                .log(),
                ONE_SECOND_DELAY_MILLIS);

        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void next_loggedAfterPreviousCallToNext_returnsNewEvent() {
        CustomEvent.logger(CONTEXT)
                .setData(DATA_1)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());
        eventLogs.next();
        CustomEvent.logger(CONTEXT)
                .setData(DATA_2)
                .log();

        assertThat(eventLogs.next().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_loggedAfterPreviousCallToPoll_returnsNewEvent() {
        CustomEvent.logger(CONTEXT)
                .setData(DATA_1)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(CONTEXT.getPackageName());
        eventLogs.poll();
        CustomEvent.logger(CONTEXT)
                .setData(DATA_2)
                .log();

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void next_calledOnSeparateQuery_returnsFromStartsAgain() {
        CustomEvent.logger(CONTEXT)
                .setData(DATA_1)
                .log();
        EventLogs<CustomEvent> eventLogs1 = CustomEvent.queryPackage(CONTEXT.getPackageName());
        EventLogs<CustomEvent> eventLogs2 = CustomEvent.queryPackage(CONTEXT.getPackageName());

        assertThat(eventLogs1.next()).isEqualTo(eventLogs2.next());
    }

    @Test
    public void poll_calledOnSeparateQuery_returnsFromStartsAgain() {
        CustomEvent.logger(CONTEXT)
                .setData(DATA_1)
                .log();
        EventLogs<CustomEvent> eventLogs1 = CustomEvent.queryPackage(CONTEXT.getPackageName());
        EventLogs<CustomEvent> eventLogs2 = CustomEvent.queryPackage(CONTEXT.getPackageName());

        assertThat(eventLogs1.poll()).isEqualTo(eventLogs2.poll());
    }

    private void scheduleCustomEventInOneSecond() {
        hasScheduledEvents = true;
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                        CustomEvent.logger(CONTEXT)
                                .log(),
                ONE_SECOND_DELAY_MILLIS);
    }
}
