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

package android.server.wm.jetpack;

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.DEFAULT_SPLIT_RATIO;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assertValidSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitForFinishing;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitForResumed;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.server.wm.jetpack.utils.ActivityEmbeddingTestBase;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitPlaceholderRule;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the placeholders functionality within Activity Embedding. An activity
 * can provide a {@link SplitPlaceholderRule} to the {@link ActivityEmbeddingComponent} which will
 * enable the activity to launch directly into a split with the placeholder activity it is
 * configured to launch with.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingPlaceholderTests
 */
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingPlaceholderTests extends ActivityEmbeddingTestBase {

    /**
     * Tests that an activity with a matching {@link SplitPlaceholderRule} is successfully able to
     * launch into a split with its placeholder.
     */
    @Test
    public void testPlaceholderLaunchesWithPrimaryActivity() {
        // Set embedding rules
        final String primaryActivityId = "primaryActivity";
        final String placeholderActivityId = "placeholderActivity";
        final SplitPlaceholderRule splitPlaceholderRule = createSplitPlaceholderRule(
                primaryActivityId, placeholderActivityId);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPlaceholderRule));

        // Launch activity with placeholder
        final Pair<Activity, Activity> activityPair = launchActivityWithPlaceholderAndVerifySplit(
                primaryActivityId, placeholderActivityId, splitPlaceholderRule);
        final Activity primaryActivity = activityPair.first;
        final Activity placeholderActivity = activityPair.second;

        // Finishing the primary activity and verify that the placeholder activity is also finishing
        primaryActivity.finish();
        assertTrue(waitForFinishing(placeholderActivity));
    }

    /**
     * Creates a SplitPlaceholderRule that launches a placeholder with the target primary activity.
     */
    @NonNull
    private SplitPlaceholderRule createSplitPlaceholderRule(
            @NonNull String primaryActivityId, @NonNull String placeholderActivityId) {
        // Create placeholder activity intent
        Intent placeholderIntent = new Intent(mContext, TestActivityWithId.class);
        placeholderIntent.putExtra(ACTIVITY_ID_LABEL, placeholderActivityId);

        // Create {@link SplitPlaceholderRule} that launches the placeholder in a split with the
        // target primary activity.
        SplitPlaceholderRule splitPlaceholderRule = new SplitPlaceholderRule.Builder(
                placeholderIntent,
                activity -> primaryActivityId.equals(
                        ((TestActivityWithId) activity).getId()) /* activityPredicate */,
                intent -> primaryActivityId.equals(
                        intent.getStringExtra(ACTIVITY_ID_LABEL)) /* intentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(DEFAULT_SPLIT_RATIO).build();

        return splitPlaceholderRule;
    }

    /**
     * Launches an activity that has a placeholder and verifies that the placeholder launches to
     * the side of the activity.
     */
    @NonNull
    private Pair<Activity, Activity> launchActivityWithPlaceholderAndVerifySplit(
            @NonNull String primaryActivityId, @NonNull String placeholderActivityId,
            @NonNull SplitPlaceholderRule splitPlaceholderRule) {
        // Launch the primary activity
        startActivityNewTask(TestActivityWithId.class, primaryActivityId);
        // Get primary activity
        assertTrue(waitForResumed(primaryActivityId));
        Activity primaryActivity = getResumedActivityById(primaryActivityId);
        // Get placeholder activity
        assertTrue(waitForResumed(placeholderActivityId));
        Activity placeholderActivity = getResumedActivityById(placeholderActivityId);
        // Verify they are correctly split
        assertValidSplit(primaryActivity, placeholderActivity, splitPlaceholderRule);
        return new Pair<>(primaryActivity, placeholderActivity);
    }
}
