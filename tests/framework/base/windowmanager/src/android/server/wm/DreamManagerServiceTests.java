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
 * limitations under the License
 */

package android.server.wm;

import static android.server.wm.app.Components.TEST_DREAM_SERVICE;

import android.app.DreamManager;
import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

@Presubmit
@FlakyTest(detail = "Promote once confirmed non-flaky")
public class DreamManagerServiceTests extends ActivityManagerTestBase {

    private static final ComponentName DREAM_ACTIVITY_COMPONENT_NAME =
            new ComponentName(TEST_DREAM_SERVICE.getPackageName(),
                              "android.service.dreams.DreamActivity");

    private void startDream(ComponentName name) {
        DreamManager dreamer = mContext.getSystemService(DreamManager.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            dreamer.startDream(name);
        });
    }

    private void stopDream() {
        DreamManager dreamer = mContext.getSystemService(DreamManager.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            dreamer.stopDream();
        });
    }

    private void setActiveDream(ComponentName dream) {
        DreamManager dreamer = mContext.getSystemService(DreamManager.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            dreamer.setActiveDream(dream);
        });
    }

    @Test
    public void testStartAndStopDream() throws Exception {
        setActiveDream(TEST_DREAM_SERVICE);

        startDream(TEST_DREAM_SERVICE);
        mWmState.waitForValidState(DREAM_ACTIVITY_COMPONENT_NAME);
        mWmState.assertVisibility(DREAM_ACTIVITY_COMPONENT_NAME, true);
        mWmState.assertHomeActivityVisible(false);

        stopDream();
        mWmState.waitAndAssertActivityRemoved(DREAM_ACTIVITY_COMPONENT_NAME);

        mWmState.assertHomeActivityVisible(true);
    }
}
