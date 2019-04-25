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

package android.voiceinteraction.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.DirectAction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests for the direction action related functions.
 */
public class DirectActionsTest {
    private static final int OPERATION_TIMEOUT_MS = 5000;

    private final @NonNull SessionControl mSessionControl = new SessionControl();
    private final @NonNull ActivityControl mActivityControl = new ActivityControl();

    @Test
    public void testPerformDirectAction() throws Exception {
        mActivityControl.startActivity();
        mSessionControl.startVoiceInteractionSession();
        try {
            // Get the actions.
            final List<DirectAction> actions = mSessionControl.getDirectActions();

            // Only the expected action should be reported
            final DirectAction action = getExpectedDirectActionAssertively(actions);

            // Tell the activity how to execute next action
            mActivityControl.executeNextActionSuccessfully();

            // Perform the expected action.
            final Bundle result = mSessionControl.performDirectAction(action,
                    createActionArguments());

            // Assert the action completed successfully.
            assertActionSucceeded(result);
        } finally {
            mSessionControl.stopVoiceInteractionSession();
            mActivityControl.finishActivity();
        }
    }

    @Test
    public void testCancelPerformedDirectAction() throws Exception {
        mActivityControl.startActivity();
        mSessionControl.startVoiceInteractionSession();
        try {
            // Get the actions.
            final List<DirectAction> actions = mSessionControl.getDirectActions();

            // Only the expected action should be reported
            final DirectAction action = getExpectedDirectActionAssertively(actions);

            // Tell the activity how to execute next action
            mActivityControl.doNotExecuteNextActionWaitForCancellation();

            // Perform the expected action.
            final Bundle result = mSessionControl.performDirectActionAndCancel(action,
                    createActionArguments());

            // Assert the action was cancelled.
            assertActionCancelled(result);
        } finally {
            mSessionControl.stopVoiceInteractionSession();
            mActivityControl.finishActivity();
        }
    }

    private class SessionControl {
        private @Nullable RemoteCallback mControl;

        private void startVoiceInteractionSession() throws TimeoutException {
            final CountDownLatch latch = new CountDownLatch(1);

            final RemoteCallback callback = new RemoteCallback((result) -> {
                mControl = result.getParcelable(Utils.DIRECT_ACTIONS_KEY_CONTROL);
                latch.countDown();
            });

            final Intent intent = new Intent();
            intent.putExtra(Utils.DIRECT_ACTIONS_KEY_CLASS,
                    "android.voiceinteraction.service.DirectActionsSession");
            intent.setClassName("android.voiceinteraction.service",
                    "android.voiceinteraction.service.VoiceInteractionMain");
            intent.putExtra(Utils.DIRECT_ACTIONS_KEY_CALLBACK, callback);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            getContext().startActivity(intent);

            try {
                if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                /* cannot happen */
            }
        }

        private void stopVoiceInteractionSession() throws TimeoutException {
            final CountDownLatch latch = new CountDownLatch(1);

            final RemoteCallback callback = new RemoteCallback((b) ->
                latch.countDown()
            );

            final Bundle command = new Bundle();
            command.putString(Utils.DIRECT_ACTIONS_KEY_COMMAND,
                    Utils.DIRECT_ACTIONS_SESSION_CMD_FINISH);
            command.putParcelable(Utils.DIRECT_ACTIONS_KEY_CALLBACK, callback);

            mControl.sendResult(command);

            try {
                if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                /* cannot happen */
            }
        }

        @Nullable List<DirectAction> getDirectActions() throws TimeoutException {
            final CountDownLatch latch = new CountDownLatch(1);

            final ArrayList<DirectAction> actions = new ArrayList<>();

            final RemoteCallback callback = new RemoteCallback((result) -> {
                actions.addAll(result.getParcelableArrayList(Utils.DIRECT_ACTIONS_KEY_RESULT));
                latch.countDown();
            });

            final Bundle command = new Bundle();
            command.putString(Utils.DIRECT_ACTIONS_KEY_COMMAND,
                    Utils.DIRECT_ACTIONS_SESSION_CMD_GET_ACTIONS);
            command.putParcelable(Utils.DIRECT_ACTIONS_KEY_CALLBACK, callback);

            mControl.sendResult(command);

            try {
                if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                /* cannot happen */
            }

            return actions;
        }

        @Nullable Bundle performDirectAction(@NonNull DirectAction action,
                @NonNull Bundle arguments) throws TimeoutException {
            return performDirectAction(action, arguments, false);
        }

        @Nullable Bundle performDirectActionAndCancel(@NonNull DirectAction action,
                @NonNull Bundle arguments) throws TimeoutException {
            return performDirectAction(action, arguments, true);
        }

        @Nullable Bundle performDirectAction(@NonNull DirectAction action,
                @NonNull Bundle arguments, boolean cancel) throws TimeoutException {
            final CountDownLatch latch = new CountDownLatch(1);

            final Bundle result = new Bundle();

            final RemoteCallback callback = new RemoteCallback((b) -> {
                result.putAll(b);
                latch.countDown();
            });

            final Bundle command = new Bundle();
            command.putString(Utils.DIRECT_ACTIONS_KEY_COMMAND, cancel
                    ?  Utils.DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION_CANCEL
                    : Utils.DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION);
            command.putParcelable(Utils.DIRECT_ACTIONS_KEY_ACTION, action);
            command.putBundle(Utils.DIRECT_ACTIONS_KEY_ARGUMENTS, arguments);
            command.putParcelable(Utils.DIRECT_ACTIONS_KEY_CALLBACK, callback);

            mControl.sendResult(command);

            try {
                if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                /* cannot happen */
            }

            return result;
        }
    }

    private final class ActivityControl {
        private @Nullable RemoteCallback mControl;

        void startActivity() throws TimeoutException {
            final CountDownLatch latch = new CountDownLatch(1);

            final RemoteCallback callback = new RemoteCallback((result) -> {
                mControl = result.getParcelable(Utils.DIRECT_ACTIONS_KEY_CONTROL);
                latch.countDown();
            });

            final Intent intent = new Intent();
            intent.setClassName("android.voiceinteraction.testapp",
                    "android.voiceinteraction.testapp.DirectActionsActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Utils.DIRECT_ACTIONS_KEY_CALLBACK, callback);

            getContext().startActivity(intent);

            try {
                if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                /* cannot happen */
            }
        }

        private boolean detectInteractorDestroyed(Runnable destroyTrigger) throws TimeoutException {
            final CountDownLatch latch = new CountDownLatch(1);

            final Bundle result = new Bundle();

            final RemoteCallback callback = new RemoteCallback((b) -> {
                result.putAll(b);
                latch.countDown();
            });

            final Bundle command = new Bundle();
            command.putString(Utils.DIRECT_ACTIONS_KEY_COMMAND,
                    Utils.DIRECT_ACTIONS_ACTIVITY_CMD_DESTROYED_INTERACTOR);
            command.putParcelable(Utils.DIRECT_ACTIONS_KEY_CALLBACK, callback);

            mControl.sendResult(command);

            destroyTrigger.run();

            try {
                if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                /* cannot happen */
            }

            return result.getBoolean(Utils.DIRECT_ACTIONS_KEY_RESULT);
        }

        void finishActivity() throws TimeoutException {
            final CountDownLatch latch = new CountDownLatch(1);

            final RemoteCallback callback = new RemoteCallback((b) ->
                latch.countDown());

            final Bundle command = new Bundle();
            command.putString(Utils.DIRECT_ACTIONS_KEY_COMMAND,
                    Utils.DIRECT_ACTIONS_ACTIVITY_CMD_FINISH);
            command.putParcelable(Utils.DIRECT_ACTIONS_KEY_CALLBACK, callback);

            mControl.sendResult(command);

            try {
                if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                /* cannot happen */
            }
        }

        void executeNextActionSuccessfully() {
            final Bundle command = new Bundle();
            command.putString(Utils.DIRECT_ACTIONS_KEY_COMMAND,
                    Utils.DIRECT_ACTIONS_ACTIVITY_CMD_SET_ACTION_BEHAVIOR);
            mControl.sendResult(command);
        }

        void doNotExecuteNextActionWaitForCancellation() {
            final Bundle command = new Bundle();
            command.putString(Utils.DIRECT_ACTIONS_KEY_COMMAND,
                    Utils.DIRECT_ACTIONS_ACTIVITY_CMD_SET_ACTION_BEHAVIOR);
            command.putBoolean(Utils.DIRECT_ACTIONS_KEY_WIAT_FOR_CANCEL, true);
            mControl.sendResult(command);
        }
    }

    private @NonNull DirectAction getExpectedDirectActionAssertively(
            @Nullable List<DirectAction> actions) {
        final DirectAction action = actions.get(0);
        assertThat(action.getId()).isEqualTo(Utils.DIRECT_ACTIONS_ACTION_ID);
        assertThat(action.getExtras().getString(Utils.DIRECT_ACTION_EXTRA_KEY))
                .isEqualTo(Utils.DIRECT_ACTION_EXTRA_VALUE);
        assertThat(action.getLocusId().getId()).isEqualTo(Utils.DIRECT_ACTIONS_LOCUS_ID.getId());
        return action;
    }

    private @NonNull Bundle createActionArguments() {
        final Bundle args = new Bundle();
        args.putString(Utils.DIRECT_ACTIONS_KEY_ARGUMENTS, Utils.DIRECT_ACTIONS_KEY_ARGUMENTS);
        return args;
    }

    private void assertActionSucceeded(@NonNull Bundle result) {
        final Bundle bundle = result.getBundle(Utils.DIRECT_ACTIONS_KEY_RESULT);
        final String status = bundle.getString(Utils.DIRECT_ACTIONS_KEY_RESULT);
        assertThat(Utils.DIRECT_ACTIONS_RESULT_PERFORMED).isEqualTo(status);
    }

    private void assertActionCancelled(@NonNull Bundle result) {
        final Bundle bundle = result.getBundle(Utils.DIRECT_ACTIONS_KEY_RESULT);
        final String status = bundle.getString(Utils.DIRECT_ACTIONS_KEY_RESULT);
        assertThat(Utils.DIRECT_ACTIONS_RESULT_CANCELLED).isEqualTo(status);
    }

    private static @NonNull Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
