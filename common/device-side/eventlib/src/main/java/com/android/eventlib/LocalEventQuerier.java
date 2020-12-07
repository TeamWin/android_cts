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

import android.util.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;


/**
 * Implementation of {@link EventQuerier} which queries data about the current package.
 */
public class LocalEventQuerier<E extends Event> implements EventQuerier<E>, Events.EventListener {
    private final EventLogs<E> mEventLogs;
    private final BlockingDeque<Event> mEvents;

    LocalEventQuerier(EventLogs<E> eventLogs) {
        mEventLogs = eventLogs;
        mEvents = new LinkedBlockingDeque<>(Events.EVENTS.getEvents());
        Events.EVENTS.registerEventListener(this);
    }

    @Override
    public E get(Instant earliestLogTime) {
        for (Event event : Events.EVENTS.getEvents()) {
            if (mEventLogs.eventClass().isInstance(event)) {
                if (event.mTimestamp.isBefore(earliestLogTime)) {
                    continue;
                }

                E typedEvent = (E) event;
                if (mEventLogs.filter(typedEvent)) {
                    return typedEvent;
                }
            }
        }
        return null;
    }

    @Override
    public E next(Instant earliestLogTime) {
        while (!mEvents.isEmpty()) {
            Event event = mEvents.removeFirst();

            if (mEventLogs.eventClass().isInstance(event)) {
                if (event.mTimestamp.isBefore(earliestLogTime)) {
                    continue;
                }

                E typedEvent = (E) event;
                if (mEventLogs.filter(typedEvent)) {
                    return typedEvent;
                }
            }
        }
        return null;
    }

    @Override
    public E poll(Instant earliestLogTime, Duration timeout) {
        Instant endTime = Instant.now().plus(timeout);
        while (true) {
            Event event = null;
            try {
                Duration remainingTimeout = Duration.between(Instant.now(), endTime);
                event = mEvents.pollFirst(remainingTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e("LocalInternalEventQuerier", "Interrupted waiting for event", e);
                return null;
            }

            if (event == null) {
                // Timed out waiting for event
                return null;
            }

            if (mEventLogs.eventClass().isInstance(event)) {
                if (event.mTimestamp.isBefore(earliestLogTime)) {
                    continue;
                }

                E typedEvent = (E) event;
                if (mEventLogs.filter(typedEvent)) {
                    return typedEvent;
                }
            }
        }
    }

    @Override
    public void onNewEvent(Event event) {
        mEvents.addLast(event);
    }
}
