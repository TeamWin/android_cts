/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm;

import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.jetpack.second.Components.SECOND_UNTRUSTED_EMBEDDING_ACTIVITY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.IBinder;
import android.window.TaskFragmentInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * Tests that verifies the behaviors of embedding activities in different trusted modes.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:TaskFragmentTrustedModeTest
 */
public class TaskFragmentTrustedModeTest extends TaskFragmentOrganizerTestBase {

    private final ComponentName mTranslucentActivity = new ComponentName(mContext,
            TranslucentActivity.class);

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when there is an overlay over the task fragment.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_overlayTaskFragment() {
        // Create a task fragment with activity in untrusted mode.
        final TaskFragmentInfo tf = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Start a translucent activity over the TaskFragment.
        createTaskFragment(mTranslucentActivity, partialOverlayBounds(tf));
        mWmState.waitForActivityState(mTranslucentActivity, STATE_RESUMED);

        // The task fragment must be made invisible when there is an overlay activity in it.
        mWmState.waitForActivityState(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, STATE_STOPPED);
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        assertTrue(overlayMessage, mWmState.hasActivityState(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                STATE_STOPPED));
        assertFalse(overlayMessage, mWmState.isActivityVisible(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY));
        assertFalse(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());
        // The activity that appeared on top would stay resumed
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                mTranslucentActivity).isVisible());
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when an activity from another process is started on top.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_startActivityInTaskFragment() {
        // Create a task fragment with activity in untrusted mode.
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Start an activity with a different UID in the TaskFragment.
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .startActivityInTaskFragment(taskFragmentInfo.getFragmentToken(), mOwnerToken,
                        new Intent().setComponent(mTranslucentActivity),
                        null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(wct);
        mWmState.waitForActivityState(mTranslucentActivity, STATE_RESUMED);

        // Some activities in the task fragment must be made invisible when there is an overlay.
        mWmState.waitForActivityState(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, STATE_STOPPED);
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        assertTrue(overlayMessage, mWmState.hasActivityState(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                STATE_STOPPED));
        assertFalse(overlayMessage, mWmState.isActivityVisible(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY));
        // The activity that appeared on top would stay resumed, and the task fragment is still
        // visible.
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when an activity from another process is reparented on top.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_reparentActivityInTaskFragment() {
        final Activity translucentActivity = startActivity(TranslucentActivity.class);

        // Create a task fragment with activity in untrusted mode.
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Reparent a translucent activity with a different UID to the TaskFragment.
        final IBinder embeddedActivityToken = getActivityToken(translucentActivity);
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .reparentActivityToTaskFragment(taskFragmentInfo.getFragmentToken(),
                        embeddedActivityToken);
        mTaskFragmentOrganizer.applyTransaction(wct);
        mWmState.waitForActivityState(mTranslucentActivity, STATE_RESUMED);

        // Some activities in the task fragment must be made invisible when there is an overlay.
        mWmState.waitForActivityState(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, STATE_STOPPED);
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        assertTrue(overlayMessage, mWmState.hasActivityState(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                STATE_STOPPED));
        assertFalse(overlayMessage, mWmState.isActivityVisible(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY));
        // The activity that appeared on top would stay resumed, and the task fragment is still
        // visible
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());

        // Finishing the overlay activity must make TaskFragment visible again.
        translucentActivity.finish();
        waitAndAssertResumedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                "Activity must be resumed without overlays");
        assertTrue("Activity must be visible without overlays",
                mWmState.isActivityVisible(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY));
    }

    /**
     * Creates bounds for a container that would appear on top and partially occlude the provided
     * one.
     */
    @NonNull
    private Rect partialOverlayBounds(@NonNull TaskFragmentInfo info) {
        final Rect baseBounds = info.getConfiguration().windowConfiguration.getBounds();
        final Rect result = new Rect(baseBounds);
        result.inset(50 /* left */, 50 /* top */, 50 /* right */, 50 /* bottom */);
        return result;
    }

    public static class TranslucentActivity extends FocusableActivity {}
}
