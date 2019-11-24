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
package android.view.textclassifier.cts;

import android.os.CancellationSignal;
import android.service.textclassifier.TextClassifierService;
import android.util.Log;
import android.view.textclassifier.ConversationActions;
import android.view.textclassifier.SelectionEvent;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassificationContext;
import android.view.textclassifier.TextClassificationSessionId;
import android.view.textclassifier.TextClassifierEvent;
import android.view.textclassifier.TextLanguage;
import android.view.textclassifier.TextLinks;
import android.view.textclassifier.TextSelection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link TextClassifierService} used in the tests.
 */
public final class CtsTextClassifierService extends TextClassifierService {

    private static final String TAG = CtsTextClassifierService.class.getSimpleName();
    public static final String MY_PACKAGE = "android.view.cts";
    private static final ArrayList<Throwable> sExceptions = new ArrayList<>();
    private static final long GENERIC_TIMEOUT_MS = 10_000;

    private static ServiceWatcher sServiceWatcher;

    private final ArrayList<TextClassificationSessionId> mRequestSessions = new ArrayList<>();
    private final CountDownLatch mRequestLatch = new CountDownLatch(1);

    @NonNull
    static ServiceWatcher setServiceWatcher() {
        if (sServiceWatcher == null) {
            sServiceWatcher = new ServiceWatcher();
        }
        return sServiceWatcher;
    }

    static void clearServiceWatcher() {
        if (sServiceWatcher != null) {
            sServiceWatcher.mService = null;
            sServiceWatcher = null;
        }
    }

    @NonNull
    List<TextClassificationSessionId> getRequestSessions() {
        return Collections.unmodifiableList(mRequestSessions);
    }

    void awaitQuery(long timeoutMillis) {
        try {
            mRequestLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    /**
     * Gets the exceptions that were thrown while the service handled requests.
     */
    @NonNull
    public static List<Throwable> getExceptions() throws Exception {
        return Collections.unmodifiableList(sExceptions);
    }

    public static void resetStaticState() {
        sExceptions.clear();
        if (sServiceWatcher != null) {
            sServiceWatcher = null;
        }
    }

    @Override
    public void onSuggestSelection(TextClassificationSessionId sessionId,
            TextSelection.Request request, CancellationSignal cancellationSignal,
            Callback<TextSelection> callback) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onClassifyText(TextClassificationSessionId sessionId,
            TextClassification.Request request, CancellationSignal cancellationSignal,
            Callback<TextClassification> callback) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onGenerateLinks(TextClassificationSessionId sessionId, TextLinks.Request request,
            CancellationSignal cancellationSignal, Callback<TextLinks> callback) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onDetectLanguage(TextClassificationSessionId sessionId,
            TextLanguage.Request request, CancellationSignal cancellationSignal,
            Callback<TextLanguage> callback) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onSuggestConversationActions(TextClassificationSessionId sessionId,
            ConversationActions.Request request, CancellationSignal cancellationSignal,
            Callback<ConversationActions> callback) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onSelectionEvent(TextClassificationSessionId sessionId, SelectionEvent event) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onTextClassifierEvent(TextClassificationSessionId sessionId,
            TextClassifierEvent event) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onCreateTextClassificationSession(TextClassificationContext context,
            TextClassificationSessionId sessionId) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onDestroyTextClassificationSession(TextClassificationSessionId sessionId) {
        mRequestSessions.add(sessionId);
        mRequestLatch.countDown();
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "onConnected:  sServiceWatcher=" + sServiceWatcher);

        if (sServiceWatcher == null) {
            addException("onConnected() without a watcher");
            return;
        }

        if (sServiceWatcher.mService != null) {
            addException("onConnected(): already created: " + sServiceWatcher);
            return;
        }

        sServiceWatcher.mService = this;
        sServiceWatcher.mCreated.countDown();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "onDisconnected:  sServiceWatcher=" + sServiceWatcher);

        if (sServiceWatcher == null) {
            addException("onDisconnected() without a watcher");
            return;
        }

        if (sServiceWatcher.mService == null) {
            addException("onDisconnected(): no service on %s", sServiceWatcher);
            return;
        }
        sServiceWatcher.mDestroyed.countDown();
    }

    private static void addException(@NonNull String fmt, @Nullable Object...args) {
        final String msg = String.format(fmt, args);
        Log.e(TAG, msg);
        sExceptions.add(new IllegalStateException(msg));
    }

    // TODO: add a TestRule which extends TestWatcher
    public static final class ServiceWatcher {
        private final CountDownLatch mCreated = new CountDownLatch(1);
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        CtsTextClassifierService mService;

        @NonNull
        public CtsTextClassifierService waitOnConnected() throws InterruptedException {
            await(mCreated, "not created");

            if (mService == null) {
                throw new IllegalStateException("not created");
            }
            return mService;
        }

        public void waitOnDisconnected() throws InterruptedException {
            await(mDestroyed, "not destroyed");
        }

        private void await(@NonNull CountDownLatch latch, @NonNull String fmt,
                @Nullable Object... args)
                throws InterruptedException {
            final boolean called = latch.await(GENERIC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!called) {
                throw new IllegalStateException(String.format(fmt, args)
                        + " in " + GENERIC_TIMEOUT_MS + "ms");
            }
        }
    }
}
