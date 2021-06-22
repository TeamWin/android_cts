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

package com.android.queryable.info;

import android.app.Activity;

/**
 * Wrapper for information about an {@link Activity}.
 *
 * <p>This is used instead of {@link Activity} so that it can be easily serialized.
 */
public class ActivityInfo extends ClassInfo {

    public ActivityInfo(Activity activity) {
        this(activity.getClass());
    }

    public ActivityInfo(Class<? extends Activity> activityClass) {
        this(activityClass.getName());
    }

    public ActivityInfo(String activityClassName) {
        super(activityClassName);
        // TODO(scottjonathan): Add more information about the activity (e.g. parse the
        //  manifest)
    }

    @Override
    public String toString() {
        return "Activity{"
                + "class=" + super.toString()
                + "}";
    }
}
