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

package com.android.server.cts.device.statsdatom;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

public final class AtomTests {
    private static final String TAG = AtomTests.class.getSimpleName();

    @Test
    // Start the isolated service, which logs an AppBreadcrumbReported atom, and then exit.
    public void testIsolatedProcessService() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        Intent intent = new Intent(context, IsolatedProcessService.class);
        context.startService(intent);
        sleep(500);
        context.stopService(intent);
    }

    @Test
    // Make the app do some trivial work
    public void testSimpleCpu() throws Exception {
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            timestamp += 1;
        }
        Log.i(TAG, "The answer is " + timestamp);
    }

    // Puts the current thread to sleep
    static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Received InterruptedException while sleeping");
        }
    }
}
