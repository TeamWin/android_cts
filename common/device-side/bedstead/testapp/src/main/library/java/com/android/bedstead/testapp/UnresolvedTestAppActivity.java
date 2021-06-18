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

package com.android.bedstead.testapp;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.eventlib.events.activities.ActivityCreatedEvent;

/**
 * A reference to an {@link Activity} in a {@link TestApp}.
 */
public final class UnresolvedTestAppActivity extends TestAppActivityReference {

    private static final TestApis sTestApis = new TestApis();

    UnresolvedTestAppActivity(TestAppInstanceReference instance,
            ComponentReference component) {
        super(instance, component);
    }
}
