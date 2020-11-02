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

package com.android.cts.appsearch;

import android.app.ActivityThread;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchEmail;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSession;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SetSchemaRequest;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class AppSearchSessionTest {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final AppSearchManager mAppSearch = mContext.getSystemService(AppSearchManager.class);
    private final Executor mExecutor = ActivityThread.currentActivityThread().getExecutor();
    private AppSearchSession mSearchSession;

    @Before
    public void setUp() throws Exception {
        // Remove all documents from any instances that may have been created in the tests.
        Objects.requireNonNull(mAppSearch);
        AppSearchManager.SearchContext searchContext = new AppSearchManager.SearchContext.Builder()
                .setDatabaseName("testDb").build();
        CompletableFuture<AppSearchResult<AppSearchSession>> future = new CompletableFuture<>();
        mAppSearch.createSearchSession(searchContext, mExecutor, future::complete);
        AppSearchResult<AppSearchSession> result = future.get();
        checkIsSuccess(result);
        mSearchSession = result.getResultValue();

        CompletableFuture<AppSearchResult<Void>> schemaFuture = new CompletableFuture<>();
        mSearchSession.setSchema(
                new SetSchemaRequest.Builder().setForceOverride(true).build(), mExecutor,
                schemaFuture::complete);
        checkIsSuccess(schemaFuture.get());
    }

    @Test
    public void testGetDocuments() throws Exception {
        // Schema registration
        CompletableFuture<AppSearchResult<Void>> schemaFuture = new CompletableFuture<>();
        mSearchSession.setSchema(
                new SetSchemaRequest.Builder().addSchema(AppSearchEmail.SCHEMA).build(), mExecutor,
                schemaFuture::complete);
        checkIsSuccess(schemaFuture.get());

        // Index a document
        AppSearchEmail inEmail =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();

        CompletableFuture<AppSearchBatchResult<String, Void>> putDocumentsFuture =
                new CompletableFuture<>();
        mSearchSession.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(inEmail).build(),
                mExecutor, putDocumentsFuture::complete);
        checkIsSuccess(putDocumentsFuture.get());
    }

    private void checkIsSuccess(AppSearchResult<?> result) {
        if (!result.isSuccess()) {
            throw new AssertionFailedError("AppSearchResult not successful: " + result);
        }
    }

    private void checkIsSuccess(AppSearchBatchResult<?,?> result) {
        if (!result.isSuccess()) {
            throw new AssertionFailedError(
                    "AppSearchBatchResult not successful: " + result.getFailures());
        }
    }
}
