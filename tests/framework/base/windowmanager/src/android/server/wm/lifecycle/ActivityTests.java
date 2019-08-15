/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.wm.lifecycle;

import static android.server.wm.lifecycle.LifecycleLog.ActivityCallback.ON_DESTROY;
import static android.server.wm.lifecycle.LifecycleLog.ActivityCallback.ON_RESUME;
import static android.server.wm.lifecycle.LifecycleLog.ActivityCallback.ON_STOP;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;

import org.junit.Test;

/**
 * Tests for {@link Activity} class APIs.
 *
 * Build/Install/Run:
 *      atest CtsWindowManagerDeviceTestCases:ActivityTests
 */
@Presubmit
@MediumTest
@FlakyTest(bugId=137329632)
public class ActivityTests extends ActivityLifecycleClientTestBase {
    @Test
    public void testReleaseActivityInstance_visible() {
        final Activity activity = launchActivity(FirstActivity.class);
        waitAndAssertActivityStates(state(activity, ON_RESUME));

        getLifecycleLog().clear();
        assertFalse("Launched and visible activity must be released", activity.releaseInstance());
        LifecycleVerifier.assertEmptySequence(FirstActivity.class, getLifecycleLog(),
                "tryReleaseInstance");
    }

    @Test
    public void testReleaseActivityInstance_invisible() {
        // Launch two activities - second one to cover the first one and make it invisible.
        final Activity firstActivity = launchActivity(FirstActivity.class);
        final Activity secondActivity = launchActivity(SecondActivity.class);
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME),
                state(firstActivity, ON_STOP));
        // Wait for activity to report saved state to the server.
        getInstrumentation().waitForIdleSync();

        // Release the instance of the non-visible activity below.
        getLifecycleLog().clear();
        assertTrue("It must be possible to release an instance of an invisible activity",
                firstActivity.releaseInstance());
        waitAndAssertActivityStates(state(firstActivity, ON_DESTROY));
        LifecycleVerifier.assertEmptySequence(SecondActivity.class, getLifecycleLog(),
                "releaseInstance");

        // Finish the top activity to navigate back to the first one and re-create it.
        getLifecycleLog().clear();
        secondActivity.finish();
        waitAndAssertActivityStates(state(secondActivity, ON_DESTROY));
        LifecycleVerifier.assertLaunchSequence(FirstActivity.class, getLifecycleLog());
    }
}