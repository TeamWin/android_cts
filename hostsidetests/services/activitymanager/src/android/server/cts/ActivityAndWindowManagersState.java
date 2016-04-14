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

import android.server.cts.ActivityManagerState.ActivityStack;
import android.server.cts.ActivityManagerState.ActivityTask;
import android.server.cts.WindowManagerState.WindowStack;
import android.server.cts.WindowManagerState.WindowTask;

import java.awt.Rectangle;
import java.util.Objects;

import static android.server.cts.ActivityManagerTestBase.FREEFORM_WORKSPACE_STACK_ID;
import static android.server.cts.StateLogger.log;

/** Combined state of the activity manager and window manager. */
class ActivityAndWindowManagersState extends Assert {

    private ActivityManagerState mAmState = new ActivityManagerState();
    private WindowManagerState mWmState = new WindowManagerState();

    void computeState(ITestDevice device, String[] waitForActivitiesVisible) throws Exception {
        computeState(device, waitForActivitiesVisible, true);
    }

    void computeState(ITestDevice device, String[] waitForActivitiesVisible,
                      boolean compareTaskAndStackBounds) throws Exception {
        computeState(device, true, waitForActivitiesVisible, compareTaskAndStackBounds);
    }

    void computeState(ITestDevice device, boolean visibleOnly, String[] waitForActivitiesVisible)
            throws Exception {
        computeState(device, visibleOnly, waitForActivitiesVisible, true);
    }

    void computeState(ITestDevice device, boolean visibleOnly, String[] waitForActivitiesVisible,
                      boolean compareTaskAndStackBounds) throws Exception {
        int retriesLeft = 5;
        boolean retry = waitForActivitiesVisible != null && waitForActivitiesVisible.length > 0;
        do {
            mAmState.computeState(device);
            mWmState.computeState(device, visibleOnly);
            if (retry && shouldRetry(waitForActivitiesVisible)) {
                log("***Waiting for Activities to be visible...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            } else {
                retry = false;
            }
        } while (retry && retriesLeft-- > 0);

        assertSanity();
        assertValidBounds(compareTaskAndStackBounds);
    }

    private boolean shouldRetry(String[] waitForActivitiesVisible) {
        if (!stackInAMAndWMAreEqual()) {
            // We want to wait a little for the stacks in AM and WM to have equal values as there
            // might be a transition animation ongoing when we got the states from WM AM separately.
            log("***stackInAMAndWMAreEqual=false");
            return true;
        }
        // If the caller is interested in us waiting for some particular activity windows to be
        // visible before compute the state. Check for the visibility of those activity windows.
        boolean allActivityWindowsVisible = true;
        for (String activityName : waitForActivitiesVisible) {
            final String windowName =
                    ActivityManagerTestBase.getWindowName(activityName);
            allActivityWindowsVisible &= mWmState.isWindowVisible(windowName);
        }
        log("***allActivityWindowsVisible=" + allActivityWindowsVisible);
        return !allActivityWindowsVisible;
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

        for (ActivityStack aStack : mAmState.getStacks()) {
            final int stackId = aStack.mStackId;
            for (ActivityTask aTask : aStack.getTasks()) {
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

    void assertDoesNotContainStack(String msg, int stackId) throws Exception {
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
        final String componentName = ActivityManagerTestBase.getActivityComponentName(activityName);
        assertEquals(msg, componentName, mAmState.getFocusedActivity());
        assertEquals(msg, componentName, mWmState.getFocusedApp());
    }

    void assertNotFocusedActivity(String msg, String activityName) throws Exception {
        final String componentName = ActivityManagerTestBase.getActivityComponentName(activityName);
        if (mAmState.getFocusedActivity().equals(componentName)) {
            failNotEquals(msg, mAmState.getFocusedActivity(), componentName);
        }
        if (mWmState.getFocusedApp().equals(componentName)) {
            failNotEquals(msg, mWmState.getFocusedApp(), componentName);
        }
    }

    void assertResumedActivity(String msg, String activityName) throws Exception {
        final String componentName = ActivityManagerTestBase.getActivityComponentName(activityName);
        assertEquals(msg, componentName, mAmState.getResumedActivity());
    }

    void assertNotResumedActivity(String msg, String activityName) throws Exception {
        final String componentName = ActivityManagerTestBase.getActivityComponentName(activityName);
        if (mAmState.getResumedActivity().equals(componentName)) {
            failNotEquals(msg, mAmState.getResumedActivity(), componentName);
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

    void assertHomeActivityVisible(boolean visible) {
        final boolean activityVisible = mAmState.isHomeActivityVisible();

        if (visible) {
            assertTrue("Home activity must be visible.", activityVisible);
        } else {
            assertFalse("Home activity must NOT be visible.", activityVisible);
        }
    }

    boolean stackInAMAndWMAreEqual() {
        for (ActivityStack aStack : mAmState.getStacks()) {
            final int stackId = aStack.mStackId;
            final WindowStack wStack = mWmState.getStack(stackId);
            if (wStack == null || aStack.isFullscreen() != wStack.isFullscreen()) {
                return false;
            }

            final Rectangle aStackBounds = aStack.getBounds();
            final Rectangle wStackBounds = wStack.getBounds();

            if ((aStack.isFullscreen() && aStackBounds != null)
                    || !Objects.equals(aStackBounds, wStackBounds)) {
                return false;
            }
        }

        return true;
    }

    void assertValidBounds(boolean compareTaskAndStackBounds) {
        for (ActivityStack aStack : mAmState.getStacks()) {
            final int stackId = aStack.mStackId;
            final WindowStack wStack = mWmState.getStack(stackId);
            assertNotNull("stackId=" + stackId + " in AM but not in WM?", wStack);

            assertEquals("Stack fullscreen state in AM and WM must be equal stackId=" + stackId,
                    aStack.isFullscreen(), wStack.isFullscreen());

            final Rectangle aStackBounds = aStack.getBounds();
            final Rectangle wStackBounds = wStack.getBounds();

            if (aStack.isFullscreen()) {
                assertNull("Stack bounds in AM must be null stackId=" + stackId, aStackBounds);
            } else {
                assertEquals("Stack bounds in AM and WM must be equal stackId=" + stackId,
                        aStackBounds, wStackBounds);
            }

            for (ActivityTask aTask : aStack.getTasks()) {
                final int taskId = aTask.mTaskId;
                final WindowTask wTask = wStack.getTask(taskId);
                assertNotNull(
                        "taskId=" + taskId + " in AM but not in WM? stackId=" + stackId, wTask);

                final boolean aTaskIsFullscreen = aTask.isFullscreen();
                final boolean wTaskIsFullscreen = wTask.isFullscreen();
                assertEquals("Task fullscreen state in AM and WM must be equal taskId=" + taskId
                        + ", stackId=" + stackId, aTaskIsFullscreen, wTaskIsFullscreen);

                final Rectangle aTaskBounds = aTask.getBounds();
                final Rectangle wTaskBounds = wTask.getBounds();
                final int aTaskMinWidth = aTask.getMinimalWidth();
                final int aTaskMinHeight = aTask.getMinimalHeight();

                if (aTaskIsFullscreen) {
                    assertNull("Task bounds in AM must be null for fullscreen taskId=" + taskId,
                            aTaskBounds);
                } else {
                    assertEquals("Task bounds in AM and WM must be equal taskId=" + taskId
                            + ", stackId=" + stackId, aTaskBounds, wTaskBounds);

                    if (compareTaskAndStackBounds && stackId != FREEFORM_WORKSPACE_STACK_ID) {
                        if (aTaskMinWidth == -1 && aTaskMinHeight == -1) {
                            // Minimal size not set for task - checking for equality of bounds with
                            // stack.
                            assertEquals("Task bounds must be equal to stack bounds taskId="
                                    + taskId + ", stackId=" + stackId, aStackBounds, wTaskBounds);
                        } else {
                            // Minimal width and/or height set for task - bounds of task and stack
                            // can be different.
                            int targetWidth = aTaskMinWidth == -1
                                    ? (int) aStackBounds.getWidth()
                                    : (int) Math.max(aTaskMinWidth, aStackBounds.getWidth());
                            int targetHeight = aTaskMinHeight == -1
                                    ? (int) aStackBounds.getHeight()
                                    : (int) Math.max(aTaskMinHeight, aStackBounds.getHeight());
                            final Rectangle targetBounds = new Rectangle((int) aStackBounds.getX(),
                                    (int) aStackBounds.getY(), targetWidth, targetHeight);
                            assertEquals("Task bounds must be set according to minimal size taskId="
                                    + taskId + ", stackId=" + stackId, targetBounds, wTaskBounds);
                        }
                    }
                }
            }
        }
    }
}
