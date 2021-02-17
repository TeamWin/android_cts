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

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.eventlib.info.ActivityInfo;
import com.android.eventlib.queryhelpers.ActivityQuery;
import com.android.eventlib.queryhelpers.ActivityQueryHelper;

/**
 * Event logged when {@link Activity#onStop()} is called.
 */
public final class ActivityStoppedEvent extends Event {

    /** Begin a query for {@link ActivityStoppedEvent} events. */
    public static ActivityStoppedEventQuery queryPackage(String packageName) {
        return new ActivityStoppedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ActivityStoppedEvent}. */
    public static final class ActivityStoppedEventQuery
            extends EventLogsQuery<ActivityStoppedEvent, ActivityStoppedEventQuery> {
        ActivityQueryHelper<ActivityStoppedEventQuery> mActivity = new ActivityQueryHelper<>(this);

        private ActivityStoppedEventQuery(String packageName) {
            super(ActivityStoppedEvent.class, packageName);
        }

        /** Query {@link Activity}. */
        @CheckResult
        public ActivityQuery<ActivityStoppedEventQuery> whereActivity() {
            return mActivity;
        }

        @Override
        protected boolean filter(ActivityStoppedEvent event) {
            if (!mActivity.matches(event.mActivity)) {
                return false;
            }
            return true;
        }
    }

    /** Begin logging a {@link ActivityStoppedEvent}. */
    public static ActivityStoppedEventLogger logger(Activity activity) {
        return new ActivityStoppedEventLogger(activity);
    }

    /** {@link EventLogger} for {@link ActivityStoppedEvent}. */
    public static final class ActivityStoppedEventLogger extends EventLogger<ActivityStoppedEvent> {
        private ActivityStoppedEventLogger(Activity activity) {
            super(activity, new ActivityStoppedEvent());
            setActivity(activity);
        }

        /** Set the {@link Activity} being stopped. */
        public ActivityStoppedEventLogger setActivity(Activity activity) {
            mEvent.mActivity = new ActivityInfo(activity);
            return this;
        }

        /** Set the {@link Activity} class being stopped. */
        public ActivityStoppedEventLogger setActivity(Class<? extends Activity> activityClass) {
            mEvent.mActivity = new ActivityInfo(activityClass);
            return this;
        }

        /** Set the {@link Activity} class name being stopped. */
        public ActivityStoppedEventLogger setActivity(String activityClassName) {
            mEvent.mActivity = new ActivityInfo(activityClassName);
            return this;
        }
    }

    protected ActivityInfo mActivity;

    /** Information about the {@link Activity} stopped. */
    public ActivityInfo activity() {
        return mActivity;
    }

    @Override
    public String toString() {
        return "ActivityStoppedEvent{"
                + ", activity=" + mActivity
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}
