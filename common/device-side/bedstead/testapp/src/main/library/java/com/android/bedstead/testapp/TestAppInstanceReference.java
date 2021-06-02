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

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;

import com.google.android.enterprise.connectedapps.CrossProfileConnector;

/**
 * A reference to a specific instance of a {@link TestApp} on a given user.
 *
 * <p>The user may not exist, or the test app may not be installed on the user.
 */
public final class TestAppInstanceReference implements AutoCloseable {

    private static final TestApis sTestApis = new TestApis();

    private final TestApp mTestApp;
    private final UserReference mUser;
    private final CrossProfileConnector mConnector;

    TestAppInstanceReference(TestApp testApp, UserReference user) {
        mTestApp = testApp;
        mUser = user;
        mConnector = CrossProfileConnector.builder(sTestApis.context().instrumentedContext())
                .setBinder(new TestAppBinder(this))
                .build();
    }

    CrossProfileConnector connector() {
        return mConnector;
    }

    /**
     * Access activities on the test app.
     */
    public TestAppActivities activities() {
        return new TestAppActivities(this);
    }

    /**
     * The {@link TestApp} this instance refers to.
     */
    public TestApp testApp() {
        return mTestApp;
    }

    /**
     * The {@link UserReference} this instance refers to.
     */
    public UserReference user() {
        return mUser;
    }

    /**
     * Uninstall the {@link TestApp} from the user referenced by
     * this {@link TestAppInstanceReference}.
     */
    public void uninstall() {
        mTestApp.uninstall(mUser);
    }

    @Override
    public void close() {
        uninstall();
    }
}
