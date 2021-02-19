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

package com.android.cts.deviceowner;

import static com.android.bedstead.dpmwrapper.TestAppHelper.registerTestCaseReceiver;
import static com.android.bedstead.dpmwrapper.TestAppHelper.unregisterTestCaseReceiver;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class used to wait for user-related intents broadcast by {@link BasicAdminReceiver}.
 *
 */
final class UserActionCallback {

    private static final String TAG = UserActionCallback.class.getSimpleName();

    private static final int BROADCAST_TIMEOUT = 300_000;

    private final int mExpectedSize;
    private final List<String> mExpectedActions;
    private final List<String> mPendingActions;
    private final List<UserHandle> mReceivedUsers = new ArrayList<>();
    private final CountDownLatch mLatch;
    private final Context mContext;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (intent.hasExtra(BasicAdminReceiver.EXTRA_USER_HANDLE)) {
                UserHandle userHandle = intent
                        .getParcelableExtra(BasicAdminReceiver.EXTRA_USER_HANDLE);
                Log.d(TAG, "broadcast receiver received " + action + " with user " + userHandle);
                mReceivedUsers.add(userHandle);
            } else {
                Log.e(TAG, "broadcast receiver received " + intent.getAction()
                        + " WITHOUT " + BasicAdminReceiver.EXTRA_USER_HANDLE + " extra");
            }
            boolean removed = mPendingActions.remove(action);
            if (!removed) {
                Log.e(TAG, "Unexpected action " + action + "; what's left is " + mPendingActions);
                return;
            }
            Log.d(TAG, "Counting down latch (current count is " + mLatch.getCount() + ")");
            mLatch.countDown();
        }
    };

    private UserActionCallback(Context context, String... actions) {
        mContext = context;
        mExpectedSize = actions.length;
        mExpectedActions = new ArrayList<>(mExpectedSize);
        mPendingActions = new ArrayList<>(mExpectedSize);
        for (String action : actions) {
            mExpectedActions.add(action);
            mPendingActions.add(action);
        }
        mLatch = new CountDownLatch(mExpectedSize);
    }

    /**
     * Creates a new {@link UserActionCallback} and registers it to receive user broadcasts in the
     * given context.
     *
     * @param context context to register for.
     * @param actions expected actions (used on {@link #waitForBroadcasts(UserHandle...)}.
     *
     * @return a new {@link UserActionCallback}.
     */
    public static UserActionCallback register(Context context, String...actions) {
        UserActionCallback callback = new UserActionCallback(context, actions);

        IntentFilter filter = new IntentFilter();
        for (String action : actions) {
            filter.addAction(action);
        }

        registerTestCaseReceiver(context, callback.mReceiver, filter);

        return callback;
    }

    /**
     * Runs the given operation, blocking until the broadcasts are received and automatically
     * unregistering itself at the end.
     *
     * @param runnable operation to run.
     * @param expectedUsers users that are expected in the broadcasts resulting from the operation.
     *
     * @return operation result.
     */
    public <V> V callAndUnregisterSelf(Callable<V> callable, @Nullable UserHandle... expectedUsers)
            throws Exception {
        try {
            V result = callable.call();
            if (expectedUsers != null && expectedUsers.length > 0) {
                waitForBroadcasts(expectedUsers);
            }
            return result;
        } finally {
            unregisterSelf();
        }
    }

    /**
     * Runs the given operation, blocking until the broadcasts are received and automatically
     * unregistering itself at the end.
     *
     * @param runnable operation to run.
     * @param expectedUsers users that are expected in the broadcasts resulting from the operation.
     */
    public void runAndUnregisterSelf(ThrowingRunnable runnable,
            @Nullable UserHandle... expectedUsers) throws Exception {
        callAndUnregisterSelf(() -> {
            runnable.run();
            return null;
        }, expectedUsers);
    }

    /**
     * Unregister itself as a {@link BroadcastReceiver} for user events.
     */
    public void unregisterSelf() {
        unregisterTestCaseReceiver(mContext, mReceiver);
    }

    /**
     * Custom {@link Runnable} that throws an {@link Exception}.
     */
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Waits until broadcasts for the given users are received, or fails if it timeouts.
     */
    public void waitForBroadcasts(UserHandle... expectedUsers) throws Exception {
        Log.d(TAG, "Waiting up to " + BROADCAST_TIMEOUT + " to receive " + mExpectedSize
                + " broadcasts");
        Preconditions.checkArgument(expectedUsers.length == mExpectedSize,
                "Should pass exactly %d users, but passed %s", mExpectedSize,
                Arrays.toString(expectedUsers));
        boolean received = mLatch.await(BROADCAST_TIMEOUT, TimeUnit.MILLISECONDS);

        try {
            assertWithMessage("%s messages received in %s ms. Expected actions=%s, "
                + "pending=%s", mExpectedSize, BROADCAST_TIMEOUT, mExpectedActions,
                mPendingActions).that(received).isTrue();
            assertWithMessage("wrong user handles received").that(mReceivedUsers)
                    .containsExactlyElementsIn(expectedUsers);
        } catch (Exception | Error e) {
            Log.e(TAG, "waitForBroadcasts() failed: " + e);
            throw e;
        }
    }
}
