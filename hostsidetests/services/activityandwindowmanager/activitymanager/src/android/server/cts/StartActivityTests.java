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

package android.server.cts;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.StartActivityTests
 */
public class StartActivityTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY_NAME = "TestActivity";

    /**
     * Tests that starting an activity from a non-activity context will cause an exception if the
     * new task flag is not used.
     * @throws Exception
     */
    public void testStartActivityContexts() throws Exception {
        launchActivity(LAUNCHING_ACTIVITY);
        getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME)
                .setUseApplicationContext(true)
                .execute();
        mAmWmState.assertNotResumedActivity("The test activity should not have started",
                TEST_ACTIVITY_NAME);
        getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME)
                .execute();
        mAmWmState.assertResumedActivity("The test activity should have started",
                TEST_ACTIVITY_NAME);
    }
}
