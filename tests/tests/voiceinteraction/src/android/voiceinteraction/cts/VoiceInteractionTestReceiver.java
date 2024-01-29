/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.voiceinteraction.service.MainInteractionSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VoiceInteractionTestReceiver extends BroadcastReceiver {

    private static final String TAG = VoiceInteractionTestReceiver.class.getSimpleName();

    private static CountDownLatch sServiceStartedLatch = new CountDownLatch(1);
    private static Intent sReceivedIntent;
    private static CountDownLatch sScreenshotReceivedLatch = new CountDownLatch(1);

    private static CountDownLatch sAssistDataReceivedLatch = new CountDownLatch(1);

    public static void waitSessionStarted(long timeout, TimeUnit unit) throws InterruptedException {
        if (!sServiceStartedLatch.await(timeout, unit)) {
            fail("Timed out waiting for session to start");
        }
        String error = sReceivedIntent.getStringExtra("error");
        if (error != null) {
            fail(error);
        }
    }

    /** Waits for onHandleScreenshot being called. */
    public static boolean waitScreenshotReceived(long timeout, TimeUnit unit)
            throws InterruptedException {
        if (!sScreenshotReceivedLatch.await(timeout, unit)) {
            // Timeout. Assume this means no screenshot is sent to VoiceInteractionService.
            return false;
        }
        if (!sReceivedIntent.hasExtra(MainInteractionSession.EXTRA_RECEIVED)) {
            throw new IllegalStateException("Expect EXTRA_RECEIVED");
        }
        return sReceivedIntent.getBooleanExtra(MainInteractionSession.EXTRA_RECEIVED, false);
    }

    /** Waits for onHandleAssist being called. */
    public static boolean waitAssistDataReceived(long timeout, TimeUnit unit)
            throws InterruptedException {
        if (!sAssistDataReceivedLatch.await(timeout, unit)) {
            // Timeout. Assume this means no assist data is sent to VoiceInteractionService.
            return false;
        }
        if (!sReceivedIntent.hasExtra(MainInteractionSession.EXTRA_RECEIVED)) {
            throw new IllegalStateException("Expect EXTRA_RECEIVED");
        }
        return sReceivedIntent.getBooleanExtra(MainInteractionSession.EXTRA_RECEIVED, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Got broadcast that MainInteractionService started");
        sReceivedIntent = intent;
        switch (intent.getAction()) {
            case MainInteractionSession.ACTION_SESSION_STARTED -> sServiceStartedLatch.countDown();
            case MainInteractionSession.ACTION_SCREENSHOT_RECEIVED ->
                    sScreenshotReceivedLatch.countDown();
            case MainInteractionSession.ACTION_ASSIST_DATA_RECEIVED ->
                    sAssistDataReceivedLatch.countDown();
        }
    }

    /** Resets the states. */
    public static void reset() {
        sReceivedIntent = null;
        sServiceStartedLatch = new CountDownLatch(1);
        sScreenshotReceivedLatch = new CountDownLatch(1);
        sAssistDataReceivedLatch = new CountDownLatch(1);
    }
}