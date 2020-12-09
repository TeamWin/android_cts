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

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a single action which has been logged.
 */
public abstract class Event implements Serializable {

    // This class should contain all standard data applicable to all Events.

    protected String mPackageName;
    protected Instant mTimestamp;

    /** Get the package name this event was logged by. */
    public String packageName() {
        return mPackageName;
    }

    /** Get the time that this event was logged. */
    public Instant timestamp() {
        return mTimestamp;
    }
}
