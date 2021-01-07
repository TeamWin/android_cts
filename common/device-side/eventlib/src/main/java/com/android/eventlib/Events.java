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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Event store for the current package. */
class Events {

    private static final String TAG = "Events";

    /** Interface used to be informed when new events are logged. */
    interface EventListener {
        void onNewEvent(Event e);
    }

    public static final Events EVENTS = new Events();

    private Events() {

    }

    /** Saves the event so it can be queried. */
    void log(Event event) {
        Log.d(TAG, event.toString());

        mEventList.add(event); // TODO: This should be made immutable before adding
        triggerEventListeners(event);
        // TODO: Serialize in case the process crashes
    }

    private final List<Event> mEventList = new ArrayList<>();
    // This is a weak set so we don't retain listeners from old tests
    private final Set<EventListener> mEventListeners
            = Collections.newSetFromMap(new WeakHashMap<>());

    /** Get all logged events. */
    public List<Event> getEvents() {
        return mEventList;
    }

    /** Register an {@link EventListener} to be called when a new {@link Event} is logged. */
    public void registerEventListener(EventListener listener) {
        synchronized (Events.class) {
            mEventListeners.add(listener);
        }
    }

    private void triggerEventListeners(Event event) {
        synchronized (Events.class) {
            for (EventListener listener : mEventListeners) {
                listener.onNewEvent(event);
            }
        }
    }

}
