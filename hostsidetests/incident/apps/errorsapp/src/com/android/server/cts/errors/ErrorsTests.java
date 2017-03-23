/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.errors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.DropBoxManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Used by ErrorTest. Spawns misbehaving activities so reports will appear in Dropbox.
 */
@RunWith(AndroidJUnit4.class)
public class ErrorsTests {
    private static final String TAG = "ErrorsTests";

    private CountDownLatch mResultsReceivedSignal;
    private DropBoxManager mDropbox;
    private long mStartMs;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDropbox = (DropBoxManager) mContext.getSystemService(Context.DROPBOX_SERVICE);
        mResultsReceivedSignal = new CountDownLatch(1);
        registerReceiver(mContext, mResultsReceivedSignal);
        mStartMs = System.currentTimeMillis();
    }

    @Test
    public void testException() throws Exception {
        Log.i(TAG, "testException");

        Intent intent = new Intent();
        intent.setClass(mContext, ExceptionActivity.class);
        mContext.startActivity(intent);

        mResultsReceivedSignal.await(10, TimeUnit.SECONDS);
        assertDropboxContains("data_app_crash", mContext.getPackageName() + ":TestProcess",
                "java.lang.RuntimeException: This is a test exception");
    }

    @Test
    public void testANR() throws Exception {
        Log.i(TAG, "testANR");

        Intent intent = new Intent();
        intent.setClass(mContext, ANRActivity.class);
        mContext.startActivity(intent);

        mResultsReceivedSignal.await(60, TimeUnit.SECONDS);
        assertDropboxContains("data_app_anr", mContext.getPackageName() + ":TestProcess",
                "Subject: Broadcast of Intent { act=android.intent.action.SCREEN_ON");
    }

    @Test
    public void testNativeCrash() throws Exception {
        Log.i(TAG, "testNativeCrash");

        Intent intent = new Intent();
        intent.setClass(mContext, NativeActivity.class);
        mContext.startActivity(intent);

        mResultsReceivedSignal.await(10, TimeUnit.SECONDS);
        assertDropboxContains("SYSTEM_TOMBSTONE", mContext.getPackageName() + ":TestProcess",
                "backtrace:");
    }

    static void registerReceiver(Context ctx, CountDownLatch onReceiveLatch) {
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveLatch.countDown();
            }
        }, new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED));
    }

    private void assertDropboxContains(String tag, String... wantInStackTrace) throws Exception {
        DropBoxManager.Entry entry = mDropbox.getNextEntry(tag, mStartMs);
        assertTrue("No entry found with tag: " + tag, entry != null);

        assertEquals("Tag", tag, entry.getTag());

        String stackTrace = entry.getText(10000); // Only need to check a few lines.
        for (String line : wantInStackTrace) {
            assertTrue(tag + ": Stack trace did not contain: " + line, stackTrace.contains(line));
        }
        entry.close();
    }
}
