/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.am;

import static android.app.ActivityManager.StackId.INVALID_STACK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.content.ComponentName;
import android.support.annotation.Nullable;

public class WaitForValidActivityState {
    @Nullable
    public final String componentName;
    @Nullable
    public final String windowName;
    /** Use {@link #componentName} and  {@link #windowName}. */
    @Deprecated
    public final String activityName;
    public final int stackId;
    public final int windowingMode;
    public final int activityType;

    public WaitForValidActivityState(final ComponentName activityName) {
        this.componentName = activityName.flattenToShortString();
        this.windowName = activityName.flattenToString();
        this.activityName = activityName.getShortClassName();
        this.stackId = INVALID_STACK_ID;
        this.windowingMode = WINDOWING_MODE_UNDEFINED;
        this.activityType = ACTIVITY_TYPE_UNDEFINED;
    }

    /** Use {@link #WaitForValidActivityState(ComponentName)}. */
    @Deprecated
    public WaitForValidActivityState(String activityName) {
        this.componentName = null;
        this.windowName = null;
        this.activityName = activityName;
        this.stackId = INVALID_STACK_ID;
        this.windowingMode = WINDOWING_MODE_UNDEFINED;
        this.activityType = ACTIVITY_TYPE_UNDEFINED;
    }

    private WaitForValidActivityState(final Builder builder) {
        this.componentName = builder.mComponentName;
        this.windowName = builder.mWindowName;
        this.activityName = builder.mActivityName;
        this.stackId = builder.mStackId;
        this.windowingMode = builder.mWindowingMode;
        this.activityType = builder.mActivityType;
    }

    public static class Builder {
        @Nullable
        private String mComponentName = null;
        @Nullable
        private String mWindowName = null;
        @Nullable
        private String mActivityName = null;
        private int mStackId = INVALID_STACK_ID;
        private int mWindowingMode = WINDOWING_MODE_UNDEFINED;
        private int mActivityType = ACTIVITY_TYPE_UNDEFINED;

        public Builder() {}

        public Builder(final ComponentName activityName) {
            mComponentName = activityName.flattenToShortString();
            mWindowName = activityName.flattenToString();
            mActivityName = activityName.getShortClassName();
        }

        /** Use {@link #Builder(ComponentName)}. */
        @Deprecated
        public Builder(String activityName) {
            mActivityName = activityName;
        }

        public Builder setActivityName(final ComponentName activityName) {
            mComponentName = activityName.flattenToShortString();
            mWindowName = activityName.flattenToString();
            mActivityName = activityName.getShortClassName();
            return this;
        }

        /** Use {@link #setActivityName(ComponentName)}. */
        @Deprecated
        public Builder setActivityName(String activityName) {
            mActivityName = activityName;
            return this;
        }

        public Builder setWindowName(String windowName) {
            mWindowName = windowName;
            return this;
        }

        public Builder setStackId(int stackId) {
            mStackId = stackId;
            return this;
        }

        public Builder setWindowingMode(int windowingMode) {
            mWindowingMode = windowingMode;
            return this;
        }

        public Builder setActivityType(int activityType) {
            mActivityType = activityType;
            return this;
        }

        public WaitForValidActivityState build() {
            return new WaitForValidActivityState(this);
        }
    }
}
