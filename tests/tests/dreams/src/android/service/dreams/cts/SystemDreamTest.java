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

package android.service.dreams.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;
import android.server.wm.DreamCoordinator;
import android.text.TextUtils;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@ApiTest(apis = {"com.android.server.dreams.DreamManagerService#setSystemDreamComponent"})
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SystemDreamTest extends ActivityManagerTestBase {
    private static final String USER_DREAM_COMPONENT =
            "android.app.dream.cts.app/.TestDreamService";
    private static final String SYSTEM_DREAM_COMPONENT =
            "android.app.dream.cts.app/.TestSystemDreamService";

    private final DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    private ComponentName mSystemDream;
    private ComponentName mUserDreamActivity;

    @Before
    public void setup() {
        mDreamCoordinator.setup();

        final ComponentName userDream = ComponentName.unflattenFromString(USER_DREAM_COMPONENT);
        mSystemDream = ComponentName.unflattenFromString(SYSTEM_DREAM_COMPONENT);
        mUserDreamActivity = mDreamCoordinator.setActiveDream(userDream);
    }

    @After
    public void reset()  {
        mDreamCoordinator.restoreDefaults();
    }

    @Test
    public void startDream_systemDreamNotSet_startUserDream() {
        startAndVerifyDreamActivity(mUserDreamActivity);
    }

    @Test
    public void startDream_systemDreamSet_startSystemDream() {
        final ComponentName systemDreamActivity = mDreamCoordinator.setSystemDream(mSystemDream);
        startAndVerifyDreamActivity(systemDreamActivity);
    }

    @Test
    public void switchDream_systemDreamSet_switchToSystemDream() {
        mDreamCoordinator.startDream();

        // Sets system dream.
        final ComponentName systemDreamActivity = mDreamCoordinator.setSystemDream(mSystemDream);
        try {
            // Verifies switched to system dream.
            waitAndAssertTopResumedActivity(systemDreamActivity, Display.DEFAULT_DISPLAY,
                    getDreamActivityVerificationMessage(systemDreamActivity));
        } finally {
            mDreamCoordinator.stopDream();
        }
    }

    @Test
    public void switchDream_systemDreamCleared_switchToUserDream() {
        mDreamCoordinator.setSystemDream(mSystemDream);
        mDreamCoordinator.startDream();

        // Clears system dream.
        mDreamCoordinator.setSystemDream(null);
        try {
            // Verifies switched back to user dream.
            waitAndAssertTopResumedActivity(mUserDreamActivity, Display.DEFAULT_DISPLAY,
                    getDreamActivityVerificationMessage(mUserDreamActivity));
        } finally {
            mDreamCoordinator.stopDream();
        }
    }

    @Test
    public void broadcast_startAndStopDream_broadcastStartAndStopDreamIntents() {
        final TestDreamBroadcastReceiver receiver = new TestDreamBroadcastReceiver();
        mContext.registerReceiver(receiver, getDreamIntentFilter());

        // Start dream and verify DREAMING_STARTED broadcast exactly once.
        mDreamCoordinator.startDream();
        assertThat(receiver.waitForDreamStartedCount(1)).isTrue();
        assertThat(receiver.waitForDreamStoppedCount(0)).isTrue();

        // Stop dream and verify DREAMING_STOPPED broadcast exactly once.
        mDreamCoordinator.stopDream();
        assertThat(receiver.waitForDreamStoppedCount(1)).isTrue();
        assertThat(receiver.waitForDreamStartedCount(1)).isTrue();
    }

    @Test
    public void broadcast_switchDream_noBroadcastDreamIntentsInBetweenDreams() {
        final TestDreamBroadcastReceiver receiver = new TestDreamBroadcastReceiver();
        mContext.registerReceiver(receiver, getDreamIntentFilter());

        // Start dream and switch to system dream.
        mDreamCoordinator.startDream();
        assertThat(receiver.waitForDreamStartedCount(1)).isTrue();

        final ComponentName systemDreamActivity = mDreamCoordinator.setSystemDream(mSystemDream);
        waitAndAssertTopResumedActivity(systemDreamActivity, Display.DEFAULT_DISPLAY,
                getDreamActivityVerificationMessage(systemDreamActivity));

        // Verify no extra DREAMING_STARTED or DREAMING_STOPPED is broadcast.
        assertThat(receiver.waitForDreamStartedCount(1)).isTrue();
        assertThat(receiver.waitForDreamStoppedCount(0)).isTrue();

        // Stop dream and verify DREAMING_STARTED and DREAMING_STOPPED each broadcast exactly once.
        mDreamCoordinator.stopDream();
        assertThat(receiver.waitForDreamStartedCount(1)).isTrue();
        assertThat(receiver.waitForDreamStoppedCount(1)).isTrue();
    }

    private void startAndVerifyDreamActivity(ComponentName expectedDreamActivity) {
        try {
            mDreamCoordinator.startDream();
            waitAndAssertTopResumedActivity(expectedDreamActivity, Display.DEFAULT_DISPLAY,
                    getDreamActivityVerificationMessage(expectedDreamActivity));
        } finally {
            mDreamCoordinator.stopDream();
        }
    }

    private String getDreamActivityVerificationMessage(ComponentName activity) {
        return activity.flattenToString() + " should be displayed";
    }

    private IntentFilter getDreamIntentFilter() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        return filter;
    }

    private static class TestDreamBroadcastReceiver extends BroadcastReceiver {
        private int mDreamStartedCount = 0;
        private int mDreamStoppedCount = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), Intent.ACTION_DREAMING_STARTED)) {
                mDreamStartedCount++;
            } else if (TextUtils.equals(intent.getAction(), Intent.ACTION_DREAMING_STOPPED)) {
                mDreamStoppedCount++;
            }
        }

        boolean waitForDreamStartedCount(int count) {
            return Condition.waitFor("dream started broadcast count to be " + count /*message*/,
                    () -> mDreamStartedCount == count);
        }

        boolean waitForDreamStoppedCount(int count) {
            return Condition.waitFor("dream stopped broadcast count to be " + count /*message*/,
                    () -> mDreamStoppedCount == count);
        }
    }
}
