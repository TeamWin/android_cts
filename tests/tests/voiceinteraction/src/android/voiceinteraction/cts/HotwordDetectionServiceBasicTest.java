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

package android.voiceinteraction.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.platform.test.annotations.AppModeFull;
import android.voiceinteraction.common.Utils;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for using the VoiceInteractionService that included a basic HotwordDetectionService.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public final class HotwordDetectionServiceBasicTest
        extends AbstractVoiceInteractionBasicTestCase {
    static final String TAG = "HotwordDetectionServiceBasicTest";

    private static final int TIMEOUT_MS = 5 * 1000;

    @Rule
    public final ActivityScenarioRule<TestVoiceInteractionServiceActivity> mActivityTestRule =
            new ActivityScenarioRule<>(TestVoiceInteractionServiceActivity.class);

    @Test
    public void testHotwordDetectionService_validHotwordDetectionComponentName_triggerSuccess()
            throws Throwable {
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(mContext,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT);
        receiver.register();

        mActivityTestRule.getScenario().onActivity(activity -> {
            activity.triggerHotwordDetectionServiceTest(Utils.HOTWORD_DETECTION_SERVICE_BASIC);
        });

        final Intent intent = receiver.awaitForBroadcast(TIMEOUT_MS);
        assertThat(intent).isNotNull();
        assertThat(intent.getIntExtra(Utils.KEY_TEST_RESULT, -1)).isEqualTo(
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);

        receiver.unregisterQuietly();
    }
}
