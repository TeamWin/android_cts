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

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Interface to provide additional restrictions on an {@link Event} query.
 */
public abstract class EventLogsQuery<E extends Event, F extends EventLogsQuery>
        extends EventLogs<E> {

    // We can do this as we expect to only query when running inside instrumentation
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();

    /**
     * Default implementation of {@link EventLogsQuery} used when there are no additional query
     * options to add.
     */
    public static class Default<E extends Event> extends EventLogsQuery<E, Default> {
        public Default(Class<E> eventClass, String packageName) {
            super(eventClass, packageName);
        }

        @Override
        protected boolean filter(E event) {
            return getPackageName().equals(event.packageName());
        }
    }

    private final Class<E> mEventClass;
    private final String mPackageName;

    protected EventLogsQuery(Class<E> eventClass, String packageName) {
        if (eventClass == null || packageName == null) {
            throw new NullPointerException();
        }
        if (!packageName.equals(CONTEXT.getPackageName())) {
            throw new IllegalArgumentException("Only events in the current package can be queried");
        }
        mEventClass = eventClass;
        mPackageName = packageName;
    }

    /** Get the package name being filtered for. */
    protected String getPackageName() {
        return mPackageName;
    }

    @Override
    protected Class<E> eventClass() {
        return mEventClass;
    }

    // Currently we only support local events - this will need to be replaced when we support more
    private final EventQuerier<E> mQuerier = new LocalEventQuerier<>(this);

    @Override
    protected EventQuerier<E> getQuerier() {
        return mQuerier;
    }
}
