/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.server.wm.jetpack.utils.ExtensionUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.utils.ExtensionUtil.getWindowExtensions;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.TAG;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplit;

import static org.junit.Assume.assumeNotNull;

import android.app.Activity;
import android.util.Log;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.server.wm.jetpack.utils.TestFirstValueConsumer;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.WindowExtensions;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * launch scenarios.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingLaunchTests
 */
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingLaunchTests extends WindowManagerJetpackTestBase {

    private ActivityEmbeddingComponent mActivityEmbeddingComponent;
    private TestFirstValueConsumer<List<SplitInfo>> mSplitInfoConsumer;

    @Before
    public void setUp() {
        super.setUp();
        assumeExtensionSupportedDevice();
        WindowExtensions windowExtensions = getWindowExtensions();
        assumeNotNull(windowExtensions);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivityEmbeddingComponent = windowExtensions.getActivityEmbeddingComponent();
            }
        });
        assumeNotNull(mActivityEmbeddingComponent);
        mSplitInfoConsumer = new TestFirstValueConsumer<>();
        mActivityEmbeddingComponent.setSplitInfoCallback(mSplitInfoConsumer);
    }

    /**
     * Tests launching activities to the side from the primary activity.
     */
    @Test
    public void testPrimaryActivityLaunchToSide() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch multiple activities to the side from the primary activity and verify that they
        // all successfully split with the primary activity.
        final int numActivitiesToLaunch = 4;
        for (int i = 0; i < numActivitiesToLaunch; i++) {
            Activity secondaryActivity = startActivityAndVerifySplit(mSplitInfoConsumer,
                    primaryActivity, TestActivityWithId.class, splitPairRule, Integer.toString(i));
        }
    }
}
