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

import com.android.bedstead.nene.activities.Activity;
import com.android.bedstead.nene.activities.NeneActivity;

/**
 * Quick access to event queries about activities.
 */
public class ActivityEvents {

    private final NeneActivity mActivity;

    /** Access events for activity. */
    public static ActivityEvents forActivity(NeneActivity activity) {
        return new ActivityEvents(activity);
    }

    /** Access events for activity. */
    public static ActivityEvents forActivity(Activity<? extends NeneActivity> activity) {
        return new ActivityEvents(activity.activity());
    }

    private ActivityEvents(NeneActivity activity) {
        mActivity = activity;
    }

    /**
     * Query for when this activity is created.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    public ActivityCreatedEvent.ActivityCreatedEventQuery created() {
        return ActivityCreatedEvent.queryPackage(
                mActivity.getComponentName().getPackageName())
                .whereActivity().activityClass().className().isEqualTo(
                        mActivity.getComponentName().getClassName())
                .onUser(mActivity.getUser());
    }

    /**
     * Query for when this activity is destroyed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    public ActivityDestroyedEvent.ActivityDestroyedEventQuery destroyed() {
        return ActivityDestroyedEvent.queryPackage(
                mActivity.getComponentName().getPackageName())
                .whereActivity().activityClass().className().isEqualTo(
                        mActivity.getComponentName().getClassName())
                .onUser(mActivity.getUser());
    }

    /**
     * Query for when this activity is paused.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    public ActivityPausedEvent.ActivityPausedEventQuery paused() {
        return ActivityPausedEvent.queryPackage(
                mActivity.getComponentName().getPackageName())
                .whereActivity().activityClass().className().isEqualTo(
                        mActivity.getComponentName().getClassName())
                .onUser(mActivity.getUser());
    }

    /**
     * Query for when this activity is restarted.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    public ActivityRestartedEvent.ActivityRestartedEventQuery restarted() {
        return ActivityRestartedEvent.queryPackage(
                mActivity.getComponentName().getPackageName())
                .whereActivity().activityClass().className().isEqualTo(
                        mActivity.getComponentName().getClassName())
                .onUser(mActivity.getUser());
    }

    /**
     * Query for when this activity is resumed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    public ActivityResumedEvent.ActivityResumedEventQuery resumed() {
        return ActivityResumedEvent.queryPackage(
                mActivity.getComponentName().getPackageName())
                .whereActivity().activityClass().className().isEqualTo(
                        mActivity.getComponentName().getClassName())
                .onUser(mActivity.getUser());
    }

    /**
     * Query for when this activity is started.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    public ActivityStartedEvent.ActivityStartedEventQuery started() {
        return ActivityStartedEvent.queryPackage(
                mActivity.getComponentName().getPackageName())
                .whereActivity().activityClass().className().isEqualTo(
                        mActivity.getComponentName().getClassName())
                .onUser(mActivity.getUser());
    }

    /**
     * Query for when this activity is stopped.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    public ActivityStoppedEvent.ActivityStoppedEventQuery stopped() {
        return ActivityStoppedEvent.queryPackage(
                mActivity.getComponentName().getPackageName())
                .whereActivity().activityClass().className().isEqualTo(
                        mActivity.getComponentName().getClassName())
                .onUser(mActivity.getUser());
    }

}
