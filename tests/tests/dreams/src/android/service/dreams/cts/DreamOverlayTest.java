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

package android.service.dreams.cts;

import android.Manifest;
import android.app.DreamManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ServiceManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DreamOverlayTest  {
    private static final String DREAM_OVERLAY_SERVICE_COMPONENT =
            "android.app.dream.cts.app/.DreamOverlayService";
    private static final String DREAM_SERVICE_COMPONENT =
            "android.app.dream.cts.app/.TestDreamService";
    private static final String ACTION_DREAM_OVERLAY_SHOWN =
            "android.app.dream.cts.app.action.overlay_shown";

    private static final int TIMEOUT_SECONDS = 5;

    private static final ComponentName DREAM_COMPONENT_NAME = ComponentName.unflattenFromString(
            DREAM_SERVICE_COMPONENT);

    private DreamManager mDreamManager;
    /**
     * A simple {@link BroadcastReceiver} implementation that counts down a
     * {@link CountDownLatch} when a matching message is received
     */
    static final class OverlayVisibilityReceiver extends BroadcastReceiver {
        final CountDownLatch mLatch;

        OverlayVisibilityReceiver(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mLatch.countDown();
        }
    }

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.INTERACT_ACROSS_USERS,  Manifest.permission.WRITE_DREAM_STATE);

    @Before
    public void setup() throws ServiceManager.ServiceNotFoundException {
        mDreamManager = new DreamManager(InstrumentationRegistry.getTargetContext());

        mDreamManager.setActiveDream(DREAM_COMPONENT_NAME);

        // Register the overlay service.
        mDreamManager.setDreamOverlay(ComponentName.unflattenFromString(
                DREAM_OVERLAY_SERVICE_COMPONENT));
    }

    @After
    public void teardown() {
        mDreamManager.setActiveDream(null);

        // Unregister overlay service.
        mDreamManager.setDreamOverlay(null);
    }

    @Test
    public void testDreamOverlayAppearance() throws Exception {
        // Listen for the overlay to be shown
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        InstrumentationRegistry.getTargetContext().registerReceiver(
                new OverlayVisibilityReceiver(countDownLatch),
                new IntentFilter(ACTION_DREAM_OVERLAY_SHOWN));

        mDreamManager.startDream(DREAM_COMPONENT_NAME);

        // Wait on count down latch.
        assert (countDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}
