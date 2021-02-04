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

package com.android.eventlib.premade;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.content.Intent;
import android.util.Log;

/**
 * An {@link AppComponentFactory} which redirects invalid class names to premade EventLib classes.
 */
public class EventLibAppComponentFactory extends AppComponentFactory {

    private static final String LOG_TAG = "EventLibACF";

    @Override
    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {

        try {
            return super.instantiateActivity(cl, className, intent);
        } catch (ClassNotFoundException e) {
            Log.d(LOG_TAG,
                    "Activity class (" + className + ") not found, routing to EventLibActivity");
            EventLibActivity activity =
                    (EventLibActivity) super.instantiateActivity(
                            cl, EventLibActivity.class.getName(), intent);
            activity.setOverrideActivityClassName(className);
            return activity;
        }
    }
}
