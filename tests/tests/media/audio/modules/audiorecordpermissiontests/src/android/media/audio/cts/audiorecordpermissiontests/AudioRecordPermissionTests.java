/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests;

import static android.media.audio.cts.audiorecordpermissiontests.common.ActionsKt.*;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
public class AudioRecordPermissionTests {
    // Keep in sync with test apps
    static final String API_34_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi34";
    // Behavior changes with targetSdk >= 34, so test both cases
    static final String API_33_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi33";
    static final String API_34_NO_CAP_PACKAGE =
            "android.media.audio.cts.CtsRecordServiceApi34NoCap";

    static final String SERVICE_NAME = ".RecordService";

    static final int FUTURE_WAIT_SECS = 15;
    static final int FALSE_NEG_SECS = 10;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    // Package name for the apk being instrumented
    private String mTestPackage = null;
    private boolean mActivityStarted = false;

    @Before
    public void setup() throws Exception {
        assumeTrue(
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
    }

    @After
    public void teardown() throws Exception {
        try {
            if (mActivityStarted) {
                stopActivity();
            }
            final Future future = getFutureForAction(ACTION_FINISHED_TEARDOWN);
            amShellCommand("startservice", mTestPackage, ACTION_TEARDOWN);
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } finally {
            // Just in case
            mInstrumentation
                    .getTargetContext()
                    .stopService(
                            new Intent().setClassName(mTestPackage, mTestPackage + SERVICE_NAME));
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToBackground_isSilenced() throws Exception {
        mTestPackage = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity();
        startRecording();
        // Prime future that the stream is silenced
        final Future future = getFutureForAction(ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity();

        // Future completes when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToSleep_isSilenced() throws Exception {
        mTestPackage = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity();
        startRecording();

        // Prime futures that the stream silences then unsilences
        final Future silenceFuture = getFutureForAction(ACTION_BEGAN_RECEIVE_SILENCE);
        final Future unsilenceFuture = getFutureForAction(ACTION_BEGAN_RECEIVE_AUDIO);

        try {
            // Move out of TOP to TOP_SLEEPING
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_SLEEP");
            // Future completes when silenced. If not, timeout and throw
            silenceFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } finally {
            // Wait for unsilence after return to TOP
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_WAKEUP");
            SystemUtil.runShellCommand(mInstrumentation, "wm dismiss-keyguard");
            unsilenceFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithMicCapabilities_isNotSilenced()
            throws Exception {
        mTestPackage = API_34_PACKAGE;
        // Start an activity, then start recording in a fgs with mic caps
        startActivity();
        goForeground();
        startRecording();

        // Prime future that the stream is silenced
        final Future future = getFutureForAction(ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity();

        // Assert that we timeout (future should not complete, since we should not be silenced)
        try {
            future.get(FALSE_NEG_SECS, TimeUnit.SECONDS);
            throw new AssertionError("Future should not complete: " + future);
        } catch (TimeoutException e) {
            // Expected
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithoutMicCapabilities_isSilenced()
            throws Exception {
        mTestPackage = API_34_NO_CAP_PACKAGE;
        // Start an activity, then start recording in a fgs WITHOUT mic caps
        startActivity();
        goForeground();
        startRecording();

        // Prime future that the stream is silenced
        final Future future = getFutureForAction(ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity();

        // Future is completes when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testIfTargetPre34_MovingFromTopToBackground_isNotSilenced() throws Exception {
        mTestPackage = API_33_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity();
        startRecording();

        // Prime future that the stream is silenced
        final Future future = getFutureForAction(ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity();

        // Assert that we timeout (future should not complete, since we should not be silenced)
        try {
            future.get(FALSE_NEG_SECS, TimeUnit.SECONDS);
            throw new AssertionError("Future should not complete: " + future);
        } catch (TimeoutException e) {
            // Expected
        }
    }

    private void startActivity() throws Exception {
        final Future future = getFutureForAction(ACTION_ACTIVITY_STARTED);
        final Intent intent =
                new Intent(Intent.ACTION_MAIN)
                        .setClassName(mTestPackage, mTestPackage + ".SimpleActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.getTargetContext().startActivity(intent);
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mActivityStarted = true;
    }

    private void stopActivity() throws Exception {
        final Future future = getFutureForAction(ACTION_ACTIVITY_FINISHED);
        mContext.sendBroadcast(
                new Intent(mTestPackage + ACTION_ACTIVITY_DO_FINISH).setPackage(mTestPackage));
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mActivityStarted = false;
    }

    private void goForeground() throws Exception {
        amShellCommand("start-foreground-service", mTestPackage, ACTION_GO_FOREGROUND);
    }

    private void startRecording() throws Exception {
        startRecording(true);
    }

    private void startRecording(boolean expectAudio) throws Exception {
        final Future recordFuture = getFutureForAction(ACTION_STARTED_RECORD);
        final Future silenceStateFuture =
                getFutureForAction(
                        expectAudio ? ACTION_BEGAN_RECEIVE_AUDIO : ACTION_BEGAN_RECEIVE_SILENCE);

        amShellCommand("startservice", mTestPackage, ACTION_START_RECORD);
        recordFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        try {
            silenceStateFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // TODO(b/297259825) we never started recording unsilenced, due to avd sometimes
            // providing only silenced mic data.
            assumeTrue("AVD mic data may be silenced, which breaks this test", false);
        }
    }

    private void stopService() throws Exception {
        final Future future = getFutureForAction(ACTION_TEARDOWN);
        amShellCommand("startservice", mTestPackage, ACTION_FINISHED_TEARDOWN);
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        // Just in case
        mInstrumentation
                .getTargetContext()
                .stopService(new Intent().setClassName(mTestPackage, mTestPackage + SERVICE_NAME));
    }

    private Future getFutureForAction(String action) {
        return getFutureForIntent(mContext, mTestPackage + action);
    }

    private void amShellCommand(String command, String packageName, String action)
            throws Exception {
        amShellCommand(command, packageName, action, "");
    }

    private void amShellCommand(String command, String packageName, String action, String extra)
            throws Exception {
        SystemUtil.runShellCommand(
                mInstrumentation,
                "am "
                        + command
                        + " -n "
                        + ComponentName.createRelative(packageName, SERVICE_NAME)
                                .flattenToShortString()
                        + " -a "
                        + packageName
                        + action
                        + extra);
    }

    /**
     * Return a future for an intent delivered by a broadcast receiver which matches an action and
     * predicate.
     *
     * @param context - Context to register the receiver with
     * @param action - String representing action to register receiver for
     * @param pred - Predicate which sets the future if evaluates to true, otherwise, leaves the
     *     future unset. If the predicate throws, the future is set exceptionally
     * @return - The future representing intent delivery matching predicate.
     */
    private static ListenableFuture<Intent> getFutureForIntent(
            Context context, String action, Predicate<Intent> pred) {
        // These are evaluated async
        Objects.requireNonNull(action);
        Objects.requireNonNull(pred);
        return getFutureForListener(
                (recv) ->
                        context.registerReceiver(
                                recv, new IntentFilter(action), Context.RECEIVER_EXPORTED),
                (recv) -> {
                    try {
                        context.unregisterReceiver(recv);
                    } catch (IllegalArgumentException e) {
                        // Thrown when receiver is already unregistered, nothing to do
                    }
                },
                (completer) ->
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                try {
                                    if (action.equals(intent.getAction()) && pred.test(intent)) {
                                        completer.set(intent);
                                    }
                                } catch (Exception e) {
                                    completer.setException(e);
                                }
                            }
                        },
                "Intent receiver future for action: " + action);
    }

    /** Same as previous, but with no predicate. */
    private static ListenableFuture<Intent> getFutureForIntent(Context context, String action) {
        return getFutureForIntent(context, action, i -> true);
    }

    /**
     * Return a future for a callback registered to a listener interface.
     *
     * @param registerFunc - Function which consumes the callback object for registration
     * @param unregisterFunc - Function which consumes the callback object for unregistration This
     *     function is called when the future is completed or cancelled
     * @param instantiateCallback - Factory function for the callback object, provided a completer
     *     object (see {@code CallbackToFutureAdapter.Completer<T>}), which is a logical reference
     *     to the future returned by this function
     * @param debug - Debug string contained in future {@code toString} representation.
     */
    private static <T, V> ListenableFuture<T> getFutureForListener(
            Consumer<V> registerFunc,
            Consumer<V> unregisterFunc,
            Function<CallbackToFutureAdapter.Completer<T>, V> instantiateCallback,
            String debug) {
        // Doesn't need to be thread safe since the resolver is called inline
        final WeakReference<V> wrapper[] = new WeakReference[1];
        ListenableFuture<T> future =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            final V cb = instantiateCallback.apply(completer);
                            wrapper[0] = new WeakReference(cb);
                            registerFunc.accept(cb);
                            return debug;
                        });
        if (wrapper[0] == null) {
            throw new AssertionError("Resolver should be called inline");
        }
        final WeakReference<V> weakref = wrapper[0];
        future.addListener(
                () -> {
                    V cb = weakref.get();
                    // If there is no reference left, the receiver has already been unregistered
                    if (cb != null) {
                        unregisterFunc.accept(cb);
                        return;
                    }
                },
                MoreExecutors.directExecutor()); // Direct executor is fine since lightweight
        return future;
    }
}
