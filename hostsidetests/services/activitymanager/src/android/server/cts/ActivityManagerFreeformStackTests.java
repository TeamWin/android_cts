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

import java.awt.Rectangle;

public class ActivityManagerFreeformStackTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY = "TestActivity";
    private static final int TEST_ACTIVITY_SIZE = 500;
    // NOTE: Launching the FreeformActivity will automatically launch the TestActivity
    // with bounds (0, 0, 500, 500)
    private static final String FREEFORM_ACTIVITY = "FreeformActivity";

    public void testFreeformWindowManagementSupport() throws Exception {

        launchActivityInStack(FREEFORM_ACTIVITY, FREEFORM_WORKSPACE_STACK_ID);

        mAmWmState.computeState(mDevice, new String[] {FREEFORM_ACTIVITY});
        mAmWmState.assertSanity();
        mAmWmState.assertValidBounds();

        if (supportsFreeform()) {
            mAmWmState.assertFrontStack(
                    "Freeform stack must be the front stack.", FREEFORM_WORKSPACE_STACK_ID);
            mAmWmState.assertVisibility(FREEFORM_ACTIVITY, true);
            mAmWmState.assertVisibility(TEST_ACTIVITY, true);
            mAmWmState.assertFocusedActivity(
                    TEST_ACTIVITY + " must be focused Activity", TEST_ACTIVITY);
            assertEquals(new Rectangle(0, 0, TEST_ACTIVITY_SIZE, TEST_ACTIVITY_SIZE),
                    mAmWmState.getAmState().getTaskByActivityName(TEST_ACTIVITY).getBounds());
        } else {
            mAmWmState.assertDoesNotContainsStack(
                    "Must not contain freeform stack.", FREEFORM_WORKSPACE_STACK_ID);
        }
    }
}
