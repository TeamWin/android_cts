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

import android.content.IntentFilter;

import com.android.bedstead.nene.packages.ComponentReference;

/**
 * A reference to a broadcast receiver in a test app for which there may or may not be an instance.
 */
public abstract class TestAppBroadcastReceiverReference {

    final TestAppInstanceReference mInstance;
    final ComponentReference mComponent;
    final IntentFilter mIntentFilter;

    TestAppBroadcastReceiverReference(
            TestAppInstanceReference instance,
            ComponentReference component,
            IntentFilter intentFilter) {
        if (instance == null || component == null || intentFilter == null) {
            throw new NullPointerException();
        }
        mInstance = instance;
        mComponent = component;
        mIntentFilter = intentFilter;
    }

    /** Gets the {@link TestAppInstanceReference} this activity exists in. */
    public TestAppInstanceReference testAppInstance() {
        return mInstance;
    }

    /** Gets the {@link ComponentReference} for this activity. */
    public ComponentReference component() {
        return mComponent;
    }

    public void unregister() {
        // TODO: Communicate unregister request

        mInstance.locallyUnregisterReceiver(mIntentFilter);
    }

}
