/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tradefed.device.ITestDevice;

import junit.framework.Assert;

import java.util.List;

/** Combined state of the activity manager and window manager. */
class ActivityAndWindowManagersState extends Assert {

    private ActivityManagerState mAmState = new ActivityManagerState();
    private WindowManagerState mWmState = new WindowManagerState();

    void computeState(ITestDevice device) throws Exception {
        computeState(device, true);
    }

    void computeState(ITestDevice device, boolean visibleOnly) throws Exception {
        mAmState.computeState(device);
        mWmState.computeState(device, visibleOnly);
    }

    ActivityManagerState getAmState() {
        return mAmState;
    }

    WindowManagerState getWmState() {
        return mWmState;
    }

    void assertSanity() throws Exception {
        assertTrue("Must have stacks", mAmState.getStackCount() > 0);
        assertEquals("There should be one and only one resumed activity in the system.",
                1, mAmState.getResumedActivitiesCount());
        assertNotNull("Must have focus activity.", mAmState.getFocusedActivity());

        for (ActivityManagerState.ActivityStack aStack : mAmState.getStacks()) {
            final int stackId = aStack.mStackId;
            for (ActivityManagerState.ActivityTask aTask : aStack.getTasks()) {
                assertEquals("Stack can only contain its own tasks", stackId, aTask.mStackId);
            }
        }

        assertNotNull("Must have front window.", mWmState.getFrontWindow());
        assertNotNull("Must have focused window.", mWmState.getFocusedWindow());
        assertNotNull("Must have app.", mWmState.getFocusedApp());
    }

    void assertContainsStack(String msg, int stackId) throws Exception {
        assertTrue(msg, mAmState.containsStack(stackId));
        assertTrue(msg, mWmState.containsStack(stackId));
    }

    void assertDoesNotContainsStack(String msg, int stackId) throws Exception {
        assertFalse(msg, mAmState.containsStack(stackId));
        assertFalse(msg, mWmState.containsStack(stackId));
    }

    void assertFrontStack(String msg, int stackId) throws Exception {
        assertEquals(msg, stackId, mAmState.getFrontStackId());
        assertEquals(msg, stackId, mWmState.getFrontStackId());
    }

    void assertFocusedStack(String msg, int stackId) throws Exception {
        assertEquals(msg, stackId, mAmState.getFocusedStackId());
    }

    void assertNotFocusedStack(String msg, int stackId) throws Exception {
        if (stackId == mAmState.getFocusedStackId()) {
            failNotEquals(msg, stackId, mAmState.getFocusedStackId());
        }
    }

    void assertFocusedActivity(String msg, String activityName) throws Exception {
        assertEquals(msg, activityName, mAmState.getFocusedActivity());
        assertEquals(msg, activityName, mWmState.getFocusedApp());
    }

    void assertNotFocusedActivity(String msg, String activityName) throws Exception {
        if (mAmState.getFocusedActivity().equals(activityName)) {
            failNotEquals(msg, mAmState.getFocusedActivity(), activityName);
        }
        if (mWmState.getFocusedApp().equals(activityName)) {
            failNotEquals(msg, mWmState.getFocusedApp(), activityName);
        }
    }

    void assertResumedActivity(String msg, String activityName) throws Exception {
        assertEquals(msg, activityName, mAmState.getResumedActivity());
    }

    void assertNotResumedActivity(String msg, String activityName) throws Exception {
        if (mAmState.getResumedActivity().equals(activityName)) {
            failNotEquals(msg, mAmState.getResumedActivity(), activityName);
        }
    }

    void assertFocusedWindow(String msg, String windowName) {
        assertEquals(msg, windowName, mWmState.getFocusedWindow());
    }

    void assertNotFocusedWindow(String msg, String windowName) {
        if (mWmState.getFocusedWindow().equals(windowName)) {
            failNotEquals(msg, mWmState.getFocusedWindow(), windowName);
        }
    }

    void assertFrontWindow(String msg, String windowName) {
        assertEquals(msg, windowName, mWmState.getFrontWindow());
    }

    void assertVisibility(String activityName, boolean visible) {
        final String activityComponentName =
                ActivityManagerTestBase.getActivityComponentName(activityName);
        final String windowName =
                ActivityManagerTestBase.getWindowName(activityName);

        final boolean activityVisible = mAmState.isActivityVisible(activityComponentName);
        final boolean windowVisible = mWmState.isWindowVisible(windowName);

        if (visible) {
            assertTrue("Activity=" + activityComponentName + " must be visible.", activityVisible);
            assertTrue("Window=" + windowName + " must be visible.", windowVisible);
        } else {
            assertFalse("Activity=" + activityComponentName + " must NOT be visible.",
                    activityVisible);
            assertFalse("Window=" + windowName + " must NOT be visible.", windowVisible);
        }
    }
}
