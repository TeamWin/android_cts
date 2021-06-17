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

public final class TestAppActivities {

    private final TestAppInstanceReference mInstance;

    TestAppActivities(TestAppInstanceReference instance) {
        mInstance = instance;
    }

    /**
     * Return any activity included in the test app Manifest.
     *
     * <p>Currently, this will always return the same activity.
     */
    public TestAppActivityReference any() {
        // TODO(scottjonathan): Currently we only have one pattern for testapps and they all have
        //  exactly one activity - so we will return it here. In future we should expose a query
        //  interface
        return new UnresolvedTestAppActivity(
                mInstance,
                mInstance.testApp().reference().component("android.testapp.activity"));
    }
}
