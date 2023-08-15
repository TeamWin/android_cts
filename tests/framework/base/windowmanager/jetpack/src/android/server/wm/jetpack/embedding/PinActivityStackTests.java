/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.jetpack.embedding;

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRuleBuilderWithPrimaryActivityClass;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotVisible;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitPairRule;
import androidx.window.extensions.embedding.SplitPinRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests pinning the
 * top ActivityStack on a Task.
 * <p>
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:PinActivityStackTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {
        "androidx.window.extensions.embedding.ActivityEmbeddingComponent#pinTopActivityStack",
        "androidx.window.extensions.embedding.ActivityEmbeddingComponent#unpinTopActivityStack"
})
public class PinActivityStackTests extends ActivityEmbeddingTestBase {
    private Activity mPrimaryActivity;
    private Activity mPinnedActivity;
    private String mPinnedActivityId = "pinActivity";
    private SplitPairRule mWildcardSplitPairRule;
    private int mTaskId;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        mPrimaryActivity = startFullScreenActivityNewTask(TestConfigChangeHandlingActivity.class);
        mTaskId = mPrimaryActivity.getTaskId();
        mWildcardSplitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(mWildcardSplitPairRule));
    }

    /**
     * Verifies that the activity navigation are isolated after the top ActivityStack is pinned.
     */
    @Test
    public void testPinTopActivityStack() {
        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Pin the top ActivityStack
        assertTrue(pinTopActivityStack());

        // Start an Activity from the primary ActivityStack
        final String activityId1 = "Activity1";
        startActivityFromActivity(mPrimaryActivity, TestActivityWithId.class, activityId1);

        // Verifies the activity in the primary ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId1);
        waitAndAssertResumed(mPinnedActivityId);
        waitAndAssertNotVisible(mPrimaryActivity);
        final Activity activity1 = getResumedActivityById(activityId1);

        // Start an Activity from the pinned ActivityStack
        final String activityId2 = "Activity2";
        startActivityFromActivity(mPinnedActivity, TestActivityWithId.class, activityId2);

        // Verifies the activity on the pinned ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId2);
        waitAndAssertResumed(activity1);
        waitAndAssertNotVisible(mPrimaryActivity);
        waitAndAssertNotVisible(mPinnedActivity);
        final Activity activity2 = getResumedActivityById(activityId2);

        // Finishes activities on the pinned ActivityStack
        activity2.finish();
        mPinnedActivity.finish();

        // Verifies both the two activities left are split side-by-side.
        waitAndAssertResumed(activity1);
        waitAndAssertResumed(mPrimaryActivity);
    }

    /**
     * Verifies that the activity navigation are not isolated after the top ActivityStack is
     * unpinned.
     */
    @Test
    public void testUnpinTopActivityStack() {
        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Pin and unpin the top ActivityStack
        assertTrue(pinTopActivityStack());
        mActivityEmbeddingComponent.unpinTopActivityStack(mTaskId);

        // Verifies the activities still splits after unpin.
        waitAndAssertResumed(mPrimaryActivity);
        waitAndAssertResumed(mPinnedActivity);

        // Start an Activity from the primary ActivityStack
        final String activityId1 = "Activity1";
        startActivityFromActivity(mPrimaryActivity, TestActivityWithId.class, activityId1);

        // Verifies the activity in the secondary ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId1);
        waitAndAssertResumed(mPrimaryActivity);
        waitAndAssertNotVisible(mPinnedActivity);
    }

    /**
     * Verifies that the activity is expanded if no split rules after unpinned.
     */
    @Test
    public void testUnpinTopActivityStack_expands() {
        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Pin the top ActivityStack
        assertTrue(pinTopActivityStack());
        waitAndAssertResumed(mPrimaryActivity);
        waitAndAssertResumed(mPinnedActivity);

        // Start an Activity from the primary ActivityStack
        final String activityId1 = "Activity1";
        startActivityFromActivity(mPrimaryActivity, TestActivityWithId.class, activityId1);

        // Verifies the activity in the primary ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId1);
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(mPrimaryActivity);
        final Activity activity1 = getResumedActivityById(activityId1);

        // Verifies the activity is expanded and occludes other activities after unpin.
        mActivityEmbeddingComponent.unpinTopActivityStack(mTaskId);
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(mPrimaryActivity);
        waitAndAssertNotVisible(activity1);
    }

    /**
     * Verifies that top ActivityStack cannot be pinned whenever it is not allowed.
     */
    @Test
    public void testPinTopActivityStack_invalidPin() {
        // Cannot pin if there's no ActivityStack.
        assertFalse(pinTopActivityStack());

        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Cannot pin if no such task.
        assertFalse(pinTopActivityStack(mTaskId + 1));

        // Cannot pin if parent window metric not large enough.
        final SplitPinRule oversizeParentMetricsRule = new SplitPinRule.Builder(
                new SplitAttributes.Builder().build(),
                parentWindowMetrics -> false /* parentWindowMetricsPredicate */).build();
        assertFalse(pinTopActivityStack(mTaskId, oversizeParentMetricsRule));

        // Pin the top ActivityStack
        assertTrue(pinTopActivityStack());

        // Cannot pin once there's already a pinned ActivityStack.
        assertFalse(pinTopActivityStack());
    }

    private boolean pinTopActivityStack() {
        return pinTopActivityStack(mTaskId);
    }

    private boolean pinTopActivityStack(int taskId) {
        SplitPinRule splitPinRule = new SplitPinRule.Builder(new SplitAttributes.Builder().build(),
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */).build();
        return pinTopActivityStack(taskId, splitPinRule);
    }

    private boolean pinTopActivityStack(int taskId, @NonNull SplitPinRule splitPinRule) {
        return mActivityEmbeddingComponent.pinTopActivityStack(taskId, splitPinRule);
    }
}
