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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.voiceinteraction.testapp;

import android.app.Activity;
import android.app.DirectAction;
import android.app.VoiceInteractor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.RemoteCallback;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Activity to test direct action behaviors.
 */
public final class DirectActionsActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        final Bundle args = getIntent().getExtras();
        final RemoteCallback callback = args.getParcelable(Utils.DIRECT_ACTIONS_KEY_CALLBACK);

        final RemoteCallback control = new RemoteCallback((cmdArgs) -> {
            final String command = cmdArgs.getString(Utils.DIRECT_ACTIONS_KEY_COMMAND);
            switch (command) {
                case Utils.DIRECT_ACTIONS_ACTIVITY_CMD_DESTROYED_INTERACTOR: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.DIRECT_ACTIONS_KEY_CALLBACK);
                    detectDestroyedInteractor(commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_ACTIVITY_CMD_FINISH: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.DIRECT_ACTIONS_KEY_CALLBACK);
                    doFinish(commandCallback);
                } break;
                case Utils.DIRECT_ACTIONS_ACTIVITY_CMD_INVALIDATE_ACTIONS: {
                    final RemoteCallback commandCallback = cmdArgs.getParcelable(
                            Utils.DIRECT_ACTIONS_KEY_CALLBACK);
                    invalidateDirectActions(commandCallback);
                } break;
            }
        });

        final Bundle result = new Bundle();
        result.putParcelable(Utils.DIRECT_ACTIONS_KEY_CONTROL, control);
        callback.sendResult(result);
    }

    @Override
    public void onGetDirectActions(@NonNull CancellationSignal cancellationSignal,
            @NonNull Consumer<List<DirectAction>> callback) {
        if (getVoiceInteractor() == null) {
            callback.accept(Collections.emptyList());
            return;
        }
        final DirectAction action = new DirectAction.Builder(Utils.DIRECT_ACTIONS_ACTION_ID)
                .setExtras(Utils.DIRECT_ACTIONS_ACTION_EXTRAS)
                .setLocusId(Utils.DIRECT_ACTIONS_LOCUS_ID)
                .build();

        final ArrayList<DirectAction> actions = new ArrayList<>();
        actions.add(action);
        callback.accept(actions);
    }

    @Override
    public void onPerformDirectAction(String actionId, Bundle arguments,
            CancellationSignal cancellationSignal, Consumer<Bundle> callback) {
        if (arguments == null || !arguments.getString(Utils.DIRECT_ACTIONS_KEY_ARGUMENTS)
                .equals(Utils.DIRECT_ACTIONS_KEY_ARGUMENTS)) {
            reportActionFailed(callback);
            return;
        }
        final RemoteCallback cancelCallback = arguments.getParcelable(
                Utils.DIRECT_ACTIONS_KEY_CANCEL_CALLBACK);
        if (cancelCallback != null) {
            cancellationSignal.setOnCancelListener(() -> reportActionCancelled(
                    cancelCallback::sendResult));
            reportActionExecuting(callback);
        } else {
            reportActionPerformed(callback);
        }
    }

    private void detectDestroyedInteractor(@NonNull RemoteCallback callback) {
        final Bundle result = new Bundle();
        final CountDownLatch latch = new CountDownLatch(1);

        final VoiceInteractor interactor = getVoiceInteractor();
        interactor.registerOnDestroyedCallback(AsyncTask.THREAD_POOL_EXECUTOR, () -> {
            if (interactor.isDestroyed() && getVoiceInteractor() == null) {
                result.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
            }
            latch.countDown();
        });

        try {
            latch.await(Utils.OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            /* ignore */
        }

        callback.sendResult(result);
    }

    private void invalidateDirectActions(@NonNull RemoteCallback callback) {
        getVoiceInteractor().notifyDirectActionsChanged();
        final Bundle result = new Bundle();
        result.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
        callback.sendResult(result);
    }

    private void doFinish(@NonNull RemoteCallback callback) {
        finish();
        final Bundle result = new Bundle();
        result.putBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT, true);
        callback.sendResult(result);
    }

    private static void reportActionPerformed(Consumer<Bundle> callback) {
        final Bundle result = new Bundle();
        result.putString(Utils.DIRECT_ACTIONS_KEY_RESULT,
                Utils.DIRECT_ACTIONS_RESULT_PERFORMED);
        callback.accept(result);
    }

    private static void reportActionCancelled(Consumer<Bundle> callback) {
        final Bundle result = new Bundle();
        result.putString(Utils.DIRECT_ACTIONS_KEY_RESULT,
                Utils.DIRECT_ACTIONS_RESULT_CANCELLED);
        callback.accept(result);
    }

    private static void reportActionExecuting(Consumer<Bundle> callback) {
        final Bundle result = new Bundle();
        result.putString(Utils.DIRECT_ACTIONS_KEY_RESULT,
                Utils.DIRECT_ACTIONS_RESULT_EXECUTING);
        callback.accept(result);
    }

    private static void reportActionFailed(Consumer<Bundle> callback) {
        callback.accept( new Bundle());
    }
}
