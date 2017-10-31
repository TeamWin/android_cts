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
package android.server.am;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Display tests that require a keyguard.
 *
 * <p>Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.ActivityManagerDisplayKeyguardTests
 */
public class ActivityManagerDisplayKeyguardTests extends ActivityManagerDisplayTestBase {
    private static final String DISMISS_KEYGUARD_ACTIVITY = "DismissKeyguardActivity";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setLockDisabled(false);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tearDownLockCredentials();
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity on a secondary display is visible (for an
     * insecure keyguard).
     */
    @Test
    public void testDismissKeyguardActivity_secondaryDisplay() throws Exception {
        if (!supportsMultiDisplay() || !isHandheld()) {
            return;
        }

        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        gotoKeyguard();
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        launchActivityOnDisplay(DISMISS_KEYGUARD_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        mAmWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
    }
}
