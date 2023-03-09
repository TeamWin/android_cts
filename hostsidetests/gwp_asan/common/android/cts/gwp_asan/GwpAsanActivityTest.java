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

package android.cts.gwp_asan;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class GwpAsanActivityTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Rule
    public ActivityTestRule<TestActivityLauncher> mTestActivityRule =
            new ActivityTestRule<>(
                    TestActivityLauncher.class,
                    false /*initialTouchMode*/,
                    false /*launchActivity*/);

    @Test
    public void testEnablement() throws Exception {
        TestActivityLauncher activity = mTestActivityRule.launchActivity(null);
        activity.callActivity(GwpAsanEnabledActivity.class);
        activity.callActivity(GwpAsanDefaultActivity.class);
        activity.callActivity(GwpAsanDisabledActivity.class);
    }
}
