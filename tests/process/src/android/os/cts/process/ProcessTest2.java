/*
 * Copyright 2021 The Android Open Source Project
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
package android.os.cts.process;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;
import android.os.cts.process.common.Consts;
import android.os.cts.process.common.Message;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.android.compatibility.common.util.BroadcastMessenger.Receiver;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS for {@link android.os.Process}.
 *
 * We have more test in cts/tests/tests/os too.
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class ProcessTest2 {
    protected static final Context sContext = InstrumentationRegistry.getTargetContext();

    /**
     * Test for:
     * {@link Process#getStartElapsedRealtime()}
     * {@link Process#getStartUptimeMillis()}
     * {@link Process#getStartRequestedElapsedRealtime()}
     * {@link Process#getStartRequestedUptimeMillis()}
     */
    @Test
    public void testStartTime() {
        // First, make sure the helper app is not running.
        ShellUtils.runShellCommand("am force-stop " + Consts.PACKAGE_NAME_HELPER_1);

        // Then, start the target process by sending a receiver, and get back the results
        // from the target APIs.
        try (Receiver<Message> receiver = new Receiver<>(sContext, Consts.TAG)) {
            Intent intent = new Intent(Consts.ACTION_SEND_BACK_START_TIME)
                    .setComponent(Consts.RECEIVER_HELPER_1)
                    .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            final long beforeStartElapsedRealtime = SystemClock.elapsedRealtime();
            final long beforeStartUptimeMillis = SystemClock.uptimeMillis();

            sContext.sendBroadcast(intent);

            Message m = receiver.waitForNextMessage();

            // Check the start times.
            Log.i(Consts.TAG, "beforeStartElapsedRealtime: " + beforeStartElapsedRealtime);
            Log.i(Consts.TAG, "beforeStartUptimeMillis: " + beforeStartUptimeMillis);
            Log.i(Consts.TAG, "Message: " + m);

            assertThat(m.startRequestedElapsedRealtime).isAtLeast(beforeStartElapsedRealtime);
            assertThat(m.startElapsedRealtime).isAtLeast(m.startRequestedElapsedRealtime);

            assertThat(m.startRequestedUptimeMillis).isAtLeast(beforeStartUptimeMillis);
            assertThat(m.startUptimeMillis).isAtLeast(m.startRequestedUptimeMillis);

            // Check the process name.
            assertThat(m.processName).isEqualTo(Consts.PACKAGE_NAME_HELPER_1 + ":sub");


            receiver.ensureNoMoreMessages();
        }
    }

    // TODO Test with a secodary process.
}
