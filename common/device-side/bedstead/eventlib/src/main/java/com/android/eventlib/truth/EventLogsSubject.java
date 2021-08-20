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

package com.android.eventlib.truth;

import static com.google.common.truth.Truth.assertAbout;

import androidx.annotation.Nullable;

import com.android.eventlib.EventLogs;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import java.time.Duration;

public final class EventLogsSubject extends Subject {

    /**
     * Assertions about {@link EventLogs}.
     */
    public static Factory<EventLogsSubject, EventLogs<?>> eventLogs() {
        return EventLogsSubject::new;
    }

    /**
     * Assertions about {@link EventLogs}.
     */
    public static EventLogsSubject assertThat(@Nullable EventLogs<?> actual) {
        return assertAbout(eventLogs()).that(actual);
    }

    @Nullable private final EventLogs<?> mActual;

    private EventLogsSubject(FailureMetadata metadata, @Nullable EventLogs<?> actual) {
        super(metadata, actual);
        this.mActual = actual;
    }

    /**
     * Asserts that an event occurred (that {@link EventLogs#poll()} returns non-null).
     */
    public void eventOccurred() {
        if (mActual.poll() == null) {
            // TODO(b/197315353): Add non-matching events
            failWithoutActual(Fact.simpleFact("Expected event to have occurred matching: "
                    + mActual + " but it did not occur."));
        }
    }

    /**
     * Asserts that an event occurred (that {@link EventLogs#poll(Duration)} returns non-null).
     */
    public void eventOccurredWithin(Duration timeout) {
        if (mActual.poll(timeout) == null) {
            // TODO(b/197315353): Add non-matching events
            failWithoutActual(Fact.simpleFact("Expected event to have occurred matching: "
                    + mActual + " but it did not occur."));
        }
    }
}
