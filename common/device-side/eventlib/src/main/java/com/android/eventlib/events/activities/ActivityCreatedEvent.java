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

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.eventlib.util.SerializableParcelWrapper;

/**
 * Event logged when {@link Activity#onCreate(Bundle)} or
 * {@link Activity#onCreate(Bundle, PersistableBundle)} is called.
 */
public final class ActivityCreatedEvent extends Event {

    /** Begin a query for {@link ActivityCreatedEvent} events. */
    public static ActivityCreatedEventQuery queryPackage(String packageName) {
        return new ActivityCreatedEventQuery(packageName);
    }

    public static final class ActivityCreatedEventQuery
            extends EventLogsQuery<ActivityCreatedEvent, ActivityCreatedEventQuery> {
        String mName;
        String mSimpleName;
        SerializableParcelWrapper<Bundle> mSavedInstanceState;
        SerializableParcelWrapper<PersistableBundle> mPersistentState;

        private ActivityCreatedEventQuery(String packageName) {
            super(ActivityCreatedEvent.class, packageName);
        }

        public ActivityCreatedEventQuery withSavedInstanceState(Bundle savedInstanceState) {
            mSavedInstanceState = new SerializableParcelWrapper<>(savedInstanceState);
            return this;
        }

        public ActivityCreatedEventQuery withPersistentState(PersistableBundle persistentState) {
            mPersistentState = new SerializableParcelWrapper<>(persistentState);
            return this;
        }

        public ActivityCreatedEventQuery withActivityClass(Class<?> clazz) {
            return withActivityName(clazz.getName());
        }

        public ActivityCreatedEventQuery withActivityName(String name) {
            mName = name;
            return this;
        }

        public ActivityCreatedEventQuery withActivitySimpleName(String simpleName) {
            mSimpleName = simpleName;
            return this;
        }

        @Override
        protected boolean filter(ActivityCreatedEvent event) {
            if (mSavedInstanceState != null
                    && !mSavedInstanceState.equals(event.mSavedInstanceState)) {
                return false;
            }
            if (mPersistentState != null && !mPersistentState.equals(event.mPersistentState)) {
                return false;
            }
            if (mName != null && !mName.equals(event.mName)) {
                return false;
            }
            if (mSimpleName != null && !mSimpleName.equals(event.mSimpleName)) {
                return false;
            }
            return true;
        }
    }

    /** Begin logging a {@link ActivityCreatedEvent}. */
    public static ActivityCreatedEventLogger logger(Activity activity, Bundle savedInstanceState) {
        return new ActivityCreatedEventLogger(activity, savedInstanceState);
    }

    public static final class ActivityCreatedEventLogger extends EventLogger<ActivityCreatedEvent> {
        private ActivityCreatedEventLogger(Activity activity, Bundle savedInstanceState) {
            super(activity, new ActivityCreatedEvent());
            mEvent.mSavedInstanceState = new SerializableParcelWrapper<>(savedInstanceState);
            setName(activity.getClass().getName());
            setSimpleName(activity.getClass().getSimpleName());
            // TODO(scottjonathan): Add more information about the activity (e.g. parse the
            //  manifest)
        }

        public ActivityCreatedEventLogger setName(String name) {
            mEvent.mName = name;
            return this;
        }

        public ActivityCreatedEventLogger setSimpleName(String simpleName) {
            mEvent.mSimpleName = simpleName;
            return this;
        }

        public ActivityCreatedEventLogger setSavedInstanceState(Bundle savedInstanceState) {
            mEvent.mSavedInstanceState = new SerializableParcelWrapper<>(savedInstanceState);
            return this;
        }

        public ActivityCreatedEventLogger setPersistentState(PersistableBundle persistentState) {
            mEvent.mPersistentState = new SerializableParcelWrapper<>(persistentState);
            return this;
        }
    }

    protected SerializableParcelWrapper<Bundle> mSavedInstanceState;
    protected SerializableParcelWrapper<PersistableBundle> mPersistentState;
    protected String mName;
    protected String mSimpleName;

    public Bundle savedInstanceState() {
        if (mSavedInstanceState == null) {
            return null;
        }
        return mSavedInstanceState.get();
    }

    public PersistableBundle persistentState() {
        if (mPersistentState == null) {
            return null;
        }
        return mPersistentState.get();
    }

    public String name() {
        return mName;
    }

    public String simpleName() {
        return mSimpleName;
    }

    @Override
    public String toString() {
        return "ActivityCreatedEvent{" +
                " savedInstanceState=" + savedInstanceState() +
                ", persistentState=" + persistentState() +
                ", name=" + mName +
                ", simpleName=" + mSimpleName +
                ", mPackageName='" + mPackageName + '\'' +
                ", mTimestamp=" + mTimestamp +
                '}';
    }
}
