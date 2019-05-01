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

package android.server.wm;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.CommandSession.ActivityCallback.ON_CONFIGURATION_CHANGED;
import static android.server.wm.CommandSession.ActivityCallback.ON_RESUME;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;
import android.view.WindowManager;

import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:MultiDisplayClientTests
 */
@Presubmit
public class MultiDisplayClientTests extends MultiDisplayTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsMultiDisplay());
    }

    @Test
    @FlakyTest(bugId = 130260102, detail = "Promote to presubmit once proved stable")
    public void testDisplayIdUpdateOnMove_RelaunchActivity() throws Exception {
        testDisplayIdUpdateOnMove(ClientTestActivity.class, false /* handlesConfigChange */);
    }

    @Test
    @FlakyTest(bugId = 130260102, detail = "Promote to presubmit once proved stable")
    public void testDisplayIdUpdateOnMove_NoRelaunchActivity() throws Exception {
        testDisplayIdUpdateOnMove(NoRelaunchActivity.class, true /* handlesConfigChange */);
    }

    private void testDisplayIdUpdateOnMove(Class<? extends Activity> activityClass,
            boolean handlesConfigChange) throws Exception {
        final ActivityTestRule activityTestRule = new ActivityTestRule(
                activityClass, true /* initialTouchMode */, false /* launchActivity */);

        // Launch activity display.
        separateTestJournal();
        Activity activity = activityTestRule.launchActivity(new Intent());
        final ComponentName activityName = getComponentName(activityClass);
        waitAndAssertResume(activityName);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display
            final ActivityManagerState.ActivityDisplay newDisplay =
                    virtualDisplaySession.setSimulateDisplay(true).createDisplay();

            // Move the activity to the new secondary display.
            separateTestJournal();
            final ActivityOptions launchOptions = ActivityOptions.makeBasic();
            launchOptions.setLaunchDisplayId(newDisplay.mId);
            final Intent newDisplayIntent = new Intent(mContext, activityClass);
            newDisplayIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            getInstrumentation().getTargetContext().startActivity(newDisplayIntent,
                    launchOptions.toBundle());
            waitAndAssertTopResumedActivity(activityName, newDisplay.mId,
                    "Activity moved to secondary display must be focused");

            if (handlesConfigChange) {
                // Wait for activity to receive the configuration change after move
                waitAndAssertConfigurationChange(activityName);
            } else {
                // Activity will be re-created, wait for resumed state
                waitAndAssertResume(activityName);
                activity = activityTestRule.getActivity();
            }
            final String message = "Display id must be updated";
            assertEquals(message, newDisplay.mId, activity.getDisplayId());
            assertEquals(message, newDisplay.mId, activity.getDisplay().getDisplayId());
            final WindowManager wm = activity.getWindowManager();
            assertEquals(message, newDisplay.mId, wm.getDefaultDisplay().getDisplayId());
        }
    }

    private static ComponentName getComponentName(Class<? extends Activity> activity) {
        return new ComponentName(getInstrumentation().getContext(), activity);
    }

    private void waitAndAssertConfigurationChange(ComponentName activityName) {
        mAmWmState.waitForWithAmState((state) ->
                        getCallbackCount(activityName, ON_CONFIGURATION_CHANGED) == 1,
                "waitForConfigurationChange");
        assertEquals("Must receive a single configuration change", 1,
                getCallbackCount(activityName, ON_CONFIGURATION_CHANGED));
    }

    private void waitAndAssertResume(ComponentName activityName) {
        mAmWmState.waitForWithAmState((state) ->
                getCallbackCount(activityName, ON_RESUME) == 1, "waitForResume");
        assertEquals("Must be resumed once", 1, getCallbackCount(activityName, ON_RESUME));
    }

    private int getCallbackCount(ComponentName activityName,
            CommandSession.ActivityCallback callback) {
        final ActivityLifecycleCounts lifecycles = new ActivityLifecycleCounts(activityName);
        return lifecycles.getCount(callback);
    }

    public static class ClientTestActivity extends CommandSession.BasicTestActivity { }

    public static class NoRelaunchActivity extends CommandSession.BasicTestActivity { }
}
