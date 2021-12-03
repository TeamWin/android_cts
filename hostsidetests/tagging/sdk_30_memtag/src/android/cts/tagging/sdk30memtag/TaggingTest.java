/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.tagging.sdk30memtag;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.runner.RunWith;
import org.junit.Rule;
import org.junit.Test;

import com.android.compatibility.common.util.DropBoxReceiver;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaggingTest {
    @Rule
    public ActivityTestRule<ServiceRunnerActivity> mTestActivityRule = new ActivityTestRule<>(
        ServiceRunnerActivity.class, false /*initialTouchMode*/, true /*launchActivity*/);

    @Test
    public void testAppZygoteMemtagSyncService() throws Exception {
      ServiceRunnerActivity activity = mTestActivityRule.getActivity();
      activity.runService(CrashAppZygoteService.class);
      assertEquals(ServiceRunnerActivity.RESULT_TEST_CRASHED, activity.getResult());
    }
}
