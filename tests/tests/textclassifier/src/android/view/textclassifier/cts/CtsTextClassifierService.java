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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link TextClassifierService} used in the tests.
 */
public final class CtsTextClassifierService extends TextClassifierService {

    private static final String TAG = "CtsTextClassifierService";
    public static final String MY_PACKAGE = "android.view.textclassifier.cts";

    private final ArrayList<TextClassificationSessionId> mRequestSessions = new ArrayList<>();
    private final CountDownLatch mRequestLatch = new CountDownLatch(1);

    /**
     * Returns the TestWatcher that was used for the testing.
     */
    @NonNull
    public static TextClassifierTestWatcher getTestWatcher() {
        return new TextClassifierTestWatcher();
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
        TextClassifierTestWatcher.ServiceWatcher.onConnected(/* service */ this);
    }

    @Override
    public void onDisconnected() {
        TextClassifierTestWatcher.ServiceWatcher.onDisconnected();
    }
}
