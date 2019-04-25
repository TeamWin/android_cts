/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * See the License for the specific language governing permissions andf
 * limitations under the License.
 */

package android.voiceinteraction.service;

import android.app.DirectAction;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.RemoteCallback;
import android.service.voice.VoiceInteractionSession;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Sessions for testing direct action related functionality
 */
public class DirectActionsSession extends VoiceInteractionSession {
    private @Nullable ActivityId mActivityId;

    private @NonNull Handler mHandler;

    private static final int OPERATION_TIMEOUT_MS = 5000;

    private Runnable mPendingWork;
    private Runnable mPendingCancel;

    public DirectActionsSession(@NonNull Context context) {
        super(context);
        mHandler = new Handler(context.getMainLooper());
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        final RemoteCallback callback = args.getParcelable(Utils.DIRECT_ACTIONS_KEY_CALLBACK);

        final RemoteCallback control = new RemoteCallback((cmdArgs) -> {
            final String command = cmdArgs.getString(Utils.DIRECT_ACTIONS_KEY_COMMAND);
            final RemoteCallback commandCallback = cmdArgs.getParcelable(
                    Utils.DIRECT_ACTIONS_KEY_CALLBACK);
            switch (command) {
                case Utils.DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION: {
                    executeWithAssist((result) -> performDirectAction(cmdArgs, result),
                            commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION_CANCEL: {
                    executeWithAssist((result) -> performDirectActionAndCancel(cmdArgs, result),
                            commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_SESSION_CMD_GET_ACTIONS: {
                    executeWithAssist(this::getDirectActions, commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_SESSION_CMD_FINISH: {
                    executeWithAssist(this::performHide, commandCallback);
                } break;
            }
        });

        final Bundle result = new Bundle();
        result.putParcelable(Utils.DIRECT_ACTIONS_KEY_CONTROL, control);
        callback.sendResult(result);
    }

    @Override
    public void onHandleAssist(AssistState state) {
        if (state.getIndex() == 0) {
            mActivityId = state.getActivityId();
            performPendingCommand();
        }
    }

    private void executeWithAssist(@Nullable Consumer<Bundle> command,
            @NonNull RemoteCallback callback) {
        mPendingWork = () -> {
            final Bundle result = new Bundle();
            command.accept(result);
            callback.sendResult(result);
        };

        if (mActivityId != null) {
            mPendingWork.run();
            mPendingWork = null;
        } else {
            mPendingCancel = () -> callback.sendResult(new Bundle());
            mHandler.postDelayed(mPendingCancel, OPERATION_TIMEOUT_MS);
        }
    }

    private void performPendingCommand() {
        if (mPendingWork == null) {
            return;
        }
        if (mActivityId != null && mHandler.hasCallbacks(mPendingCancel)) {
            mPendingWork.run();
            mPendingWork = null;
        }
        mHandler.removeCallbacks(mPendingCancel);
        mPendingCancel = null;
    }

    private void getDirectActions(@NonNull Bundle outResult) {
        final ArrayList<DirectAction> actions = new ArrayList<>();

        final CountDownLatch latch = new CountDownLatch(1);
        requestDirectActions(mActivityId, AsyncTask.THREAD_POOL_EXECUTOR, (b) -> {
            actions.addAll(b);
            latch.countDown();
        });
        try {
            latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            /* ignore */
        }

        outResult.putParcelableArrayList(Utils.DIRECT_ACTIONS_KEY_RESULT, actions);
    }

    private void performDirectAction(@NonNull Bundle args, @NonNull Bundle outResult) {
        final DirectAction action = args.getParcelable(Utils.DIRECT_ACTIONS_KEY_ACTION);
        final Bundle arguments = args.getBundle(Utils.DIRECT_ACTIONS_KEY_ARGUMENTS);

        final Bundle result = new Bundle();
        final CountDownLatch latch = new CountDownLatch(1);
        performDirectAction(action, arguments, null, AsyncTask.THREAD_POOL_EXECUTOR, (b) -> {
            result.putAll(b);
            latch.countDown();
        });
        try {
            latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            /* ignore */
        }

        outResult.putBundle(Utils.DIRECT_ACTIONS_KEY_RESULT, result);
    }

    private void performDirectActionAndCancel(@NonNull Bundle args, @NonNull Bundle outResult) {
        final DirectAction action = args.getParcelable(Utils.DIRECT_ACTIONS_KEY_ACTION);
        final Bundle arguments = args.getBundle(Utils.DIRECT_ACTIONS_KEY_ARGUMENTS);
        final CancellationSignal cancellationSignal = new CancellationSignal();

        final Bundle result  = new Bundle();
        final CountDownLatch latch = new CountDownLatch(1);
        performDirectAction(action, arguments, cancellationSignal,
                AsyncTask.THREAD_POOL_EXECUTOR, (b) -> {
            result.putAll(b);
            latch.countDown();
        });
        cancellationSignal.cancel();
        try {
            latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            /* ignore */
        }

        outResult.putBundle(Utils.DIRECT_ACTIONS_KEY_RESULT, result);
    }

    private void performHide(@NonNull Bundle outResult) {
        finish();
        outResult.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
    }
}
