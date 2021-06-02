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

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestAppActivityReferenceTest {

    private static final TestApis sTestApis = new TestApis();
    private static final UserReference sUser = sTestApis.users().instrumented();

    private TestAppProvider mTestAppProvider;

    @Before
    public void setup() {
        mTestAppProvider = new TestAppProvider();
    }

    @Test
    public void start_activityIsStarted() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {

            TestAppActivityReference activity = testAppInstance.activities().any().start();

            assertThat(sTestApis.activities().foregroundActivity()).isEqualTo(activity.reference());
        }
    }
}
