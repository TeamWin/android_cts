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

package android.car.cts.builtin.util;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.car.builtin.util.AssistUtilsHelper;
import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class AssistUtilsHelperTest {
    private static final String TAG = AssistUtilsHelper.class.getSimpleName();
    private static final String PERMISSION_ACCESS_VOICE_INTERACTION_SERVICE =
            "android.permission.ACCESS_VOICE_INTERACTION_SERVICE";
    private static final int TIMEOUT = 20_000;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    @Test
    public void testOnShownCallback() throws Exception {
        try {
            // setup
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    PERMISSION_ACCESS_VOICE_INTERACTION_SERVICE);

            // execution
            SessionShowCallbackHelperImpl callbackHelperImpl = new SessionShowCallbackHelperImpl();
            AssistUtilsHelper.showPushToTalkSessionForActiveService(mContext, callbackHelperImpl);
            callbackHelperImpl.waitForCallbackLatch();

            // assertion
            assertTrue(callbackHelperImpl.isSessionOnShown());
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private static class SessionShowCallbackHelperImpl implements
            AssistUtilsHelper.VoiceInteractionSessionShowCallbackHelper {

        private final CountDownLatch mCallbackLatch = new CountDownLatch(1);
        private boolean mIsSessionOnShown = false;

        public void onShown() {
            mIsSessionOnShown = true;
            Log.d(TAG, "onShown is called");
            mCallbackLatch.countDown();
        }

        public void onFailed() {
            Log.d(TAG, "onFailed");
        }

        private boolean isSessionOnShown() {
            return mIsSessionOnShown;
        }

        private void waitForCallbackLatch() throws Exception {
            mCallbackLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        }
    }
}
