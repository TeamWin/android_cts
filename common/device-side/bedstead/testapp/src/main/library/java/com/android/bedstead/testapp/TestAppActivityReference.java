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
import com.android.bedstead.nene.activities.ActivityReference;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.eventlib.events.activities.ActivityCreatedEvent;

/**
 * A reference to an {@link Activity} in a {@link TestApp}.
 */
public final class TestAppActivityReference {

    private static final TestApis sTestApis = new TestApis();

    private final TestAppInstanceReference mInstance;
    private final ComponentReference mComponent;

    TestAppActivityReference(
            TestAppInstanceReference instance,
            ComponentReference component) {
        mInstance = instance;
        mComponent = component;
    }

    /**
     * Starts the activity.
     */
    public TestAppActivityReference start() {
        Intent intent = new Intent();
        intent.setComponent(mComponent.componentName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        sTestApis.context().instrumentedContext().startActivity(intent);

        ActivityCreatedEvent
                .queryPackage(mComponent.packageName().packageName())
                .whereActivity().className().isEqualTo(mComponent.className()).waitForEvent();

        return this;
    }

    /**
     * Makes calls on the remote activity.
     */
    public RemoteActivity remote() {
        return new RemoteActivityImpl(
                mComponent.className(), new TargetedRemoteActivityWrapper(mInstance.connector()));
    }

    /** Gets the {@link TestAppInstanceReference} this activity exists in. */
    public TestAppInstanceReference testAppInstance() {
        return mInstance;
    }

    /** Gets the {@link ActivityReference} for this activity. */
    public ActivityReference reference() {
        return new ActivityReference(sTestApis, mComponent);
    }
}
