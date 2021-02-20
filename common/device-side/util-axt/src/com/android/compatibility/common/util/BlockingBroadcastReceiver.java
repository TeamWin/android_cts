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
package com.android.compatibility.common.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A receiver that allows caller to wait for the broadcast synchronously. Notice that you should not
 * reuse the instance. Usage is typically like this:
 * <pre>
 *     BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(context, "action");
 *     try {
 *         receiver.register();
 *         Intent intent = receiver.awaitForBroadcast();
 *         // assert the intent
 *     } finally {
 *         receiver.unregisterQuietly();
 *     }
 * </pre>
 */
public class BlockingBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BlockingBroadcast";

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final BlockingQueue<Intent> mBlockingQueue;
    private final List<String> mExpectedActions;
    private final Context mContext;
    @Nullable
    private final Function<Intent, Boolean> mChecker;

    public BlockingBroadcastReceiver(Context context, String expectedAction) {
        this(context, expectedAction, /* checker= */ null);
    }

    public BlockingBroadcastReceiver(Context context, String expectedAction,
            Function<Intent, Boolean> checker) {
        this(context, List.of(expectedAction), checker);
    }

    public BlockingBroadcastReceiver(Context context, List<String> expectedActions) {
        this(context, expectedActions, /* checker= */ null);
    }

    public BlockingBroadcastReceiver(
            Context context, List<String> expectedActions, Function<Intent, Boolean> checker) {
        mContext = context;
        mExpectedActions = expectedActions;
        mBlockingQueue = new ArrayBlockingQueue<>(1);
        mChecker = checker;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mExpectedActions.contains(intent.getAction())) {
            if (mChecker == null || mChecker.apply(intent)) {
                mBlockingQueue.add(intent);
            }
        }
    }

    public void register() {
        for (String expectedAction : mExpectedActions) {
            mContext.registerReceiver(this, new IntentFilter(expectedAction));
        }
    }

    /**
     * Wait until the broadcast.
     *
     * <p>If no matching broadcasts is received within 60 seconds an {@link AssertionError} will
     * be thrown.
     */
    public void awaitForBroadcastOrFail() {
        awaitForBroadcastOrFail(DEFAULT_TIMEOUT_SECONDS * 1000);
    }

    /**
     * Wait until the broadcast and return the received broadcast intent. {@code null} is returned
     * if no broadcast with expected action is received within 60 seconds.
     */
    public @Nullable Intent awaitForBroadcast() {
        return awaitForBroadcast(DEFAULT_TIMEOUT_SECONDS * 1000);
    }

    /**
     * Wait until the broadcast.
     *
     * <p>If no matching broadcasts is received within the given timeout an {@link AssertionError}
     * will be thrown.
     */
    public void awaitForBroadcastOrFail(long timeoutMillis) {
        if (awaitForBroadcast(timeoutMillis) == null) {
            throw new AssertionError("Did not receive matching broadcast");
        }
    }

    /**
     * Wait until the broadcast and return the received broadcast intent. {@code null} is returned
     * if no broadcast with expected action is received within the given timeout.
     */
    public @Nullable Intent awaitForBroadcast(long timeoutMillis) {
        try {
            return mBlockingQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForBroadcast get interrupted: ", e);
        }
        return null;
    }

    public void unregisterQuietly() {
        try {
            mContext.unregisterReceiver(this);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to unregister BlockingBroadcastReceiver: ", ex);
        }
    }
}