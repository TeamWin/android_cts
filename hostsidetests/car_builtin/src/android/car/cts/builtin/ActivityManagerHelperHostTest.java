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

package android.car.cts.builtin;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class ActivityManagerHelperHostTest extends CarBuiltinApiHostCtsBase {

    @Test
    public void testStartUser() throws Exception {
        // TODO (b/201005730): implement the test case to test the startUserInBackground()
        // and startUserInForeground() APIs
    }

    @Test
    public void testStopUserWithDelayedLocking() throws Exception {
        // TODO (b/201005730): implement the test case to test
        // the stopUserWithDelayedLocking() API.
    }

    @Test
    public void testUnlockUser() throws Exception {
        // TODO (b/201005730): implement the test case to test the unlockUser() API.
    }

    @Test
    public void testStopAllTaskForUser() throws Exception {
        // TODO (b/201005730): implement the test case to test the stopAllTasksForUser() API.
    }
}
