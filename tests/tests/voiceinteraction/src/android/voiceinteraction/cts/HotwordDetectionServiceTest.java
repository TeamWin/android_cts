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
import android.service.voice.VoiceInteractionService;
import android.voiceinteraction.common.Utils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public final class HotwordDetectionServiceTest extends AbstractVoiceInteractionTestCase {
    static final String TAG = "HotwordDetectionServiceTest";

    private static final int TIMEOUT_MS = 10 * 1000;

    private TestVoiceInteractionServiceActivity mTestActivity;

    @Rule
    public final ActivityTestRule<TestVoiceInteractionServiceActivity> mActivityTestRule =
            new ActivityTestRule<>(TestVoiceInteractionServiceActivity.class);

    @Before
    public void setup() throws Exception {
        mTestActivity = mActivityTestRule.getActivity();
    }

    @Test
    public void testSetHotwordDetectionConfig_noHotwordDetectionComponentName_returnFailure()
            throws Throwable {
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(mContext,
                Utils.BROADCAST_CONFIG_RESULT_INTENT);
        receiver.register();

        mTestActivity.hotwordDetectionConfigTest(Utils.HOTWORD_DETECTION_SERVICE_NONE);

        final Intent intent = receiver.awaitForBroadcast(TIMEOUT_MS);
        assertThat(intent).isNotNull();
        assertThat(intent.getIntExtra(Utils.KEY_CONFIG_RESULT, -1)).isEqualTo(
                VoiceInteractionService.HOTWORD_CONFIG_FAILURE);

        receiver.unregisterQuietly();
    }
}
