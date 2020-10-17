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
package com.android.cts.appsearch;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchEmail;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSchema.PropertyConfig;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByUriRequest;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.RemoveByUriRequest;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.ImmutableList;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppSearchManagerTest {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final AppSearchManager mAppSearch = mContext.getSystemService(AppSearchManager.class);

    @Before
    public void setUp() {
        // Remove all documents from any instances that may have been created in the tests.
        checkIsSuccess(mAppSearch.setSchema(
                new SetSchemaRequest.Builder().setForceOverride(true).build()));
    }

    @Test
    public void testGetService() {
        assertThat(mContext.getSystemService(Context.APP_SEARCH_SERVICE)).isNotNull();
        assertThat(mContext.getSystemService(AppSearchManager.class)).isNotNull();
        assertThat(mAppSearch).isNotNull();
    }

    @Test
    public void testSetSchema() {
        AppSearchSchema emailSchema = new AppSearchSchema.Builder("Email")
                .addProperty(new AppSearchSchema.PropertyConfig.Builder("subject")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).addProperty(new AppSearchSchema.PropertyConfig.Builder("body")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();
        checkIsSuccess(mAppSearch.setSchema(
                new SetSchemaRequest.Builder().addSchema(emailSchema).build()));
    }

    @Test
    public void testPutDocuments() {
        // Schema registration
        checkIsSuccess(mAppSearch.setSchema(
                new SetSchemaRequest.Builder().addSchema(AppSearchEmail.SCHEMA).build()));

        // Index a document
        AppSearchEmail email = new AppSearchEmail.Builder("uri1")
                .setFrom("from@example.com")
                .setTo("to1@example.com", "to2@example.com")
                .setSubject("testPut example")
                .setBody("This is the body of the testPut email")
                .build();

        AppSearchBatchResult result = mAppSearch.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(email).build());
        checkIsSuccess(result);
        assertThat(result.getSuccesses()).containsExactly("uri1", null);
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    public void testGetDocuments() {
        // Schema registration
        checkIsSuccess(mAppSearch.setSchema(
                new SetSchemaRequest.Builder().addSchema(AppSearchEmail.SCHEMA).build()));

        // Index a document
        AppSearchEmail inEmail =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsSuccess(mAppSearch.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(inEmail).build()));

        // Get the document
        List<GenericDocument> outDocuments = doGet("uri1");
        assertThat(outDocuments).hasSize(1);
        AppSearchEmail outEmail = new AppSearchEmail(outDocuments.get(0));
        assertThat(outEmail).isEqualTo(inEmail);
    }

    @Test
    public void testQuery() {
        // Schema registration
        checkIsSuccess(mAppSearch.setSchema(
                new SetSchemaRequest.Builder().addSchema(AppSearchEmail.SCHEMA).build()));

        // Index a document
        AppSearchEmail inEmail =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsSuccess(mAppSearch.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(inEmail).build()));

        // Query for the document
        List<GenericDocument> results = doQuery("body");
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo(inEmail);

        // Multi-term query
        results = doQuery("body email");
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo(inEmail);
    }

    @Test
    public void testQuery_TypeFilter() {
        // Schema registration
        AppSearchSchema genericSchema = new AppSearchSchema.Builder("Generic")
                .addProperty(new PropertyConfig.Builder("foo")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .build()
                ).build();
        checkIsSuccess(mAppSearch.setSchema(new SetSchemaRequest.Builder()
                .addSchema(AppSearchEmail.SCHEMA, genericSchema).build()));

        // Index a document
        AppSearchEmail inEmail =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        GenericDocument inDoc = new GenericDocument.Builder<>("uri2", "Generic")
                .setProperty("foo", "body").build();
        checkIsSuccess(mAppSearch.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(inEmail, inDoc).build()));

        // Query for the documents
        List<GenericDocument> results = doQuery("body");
        assertThat(results).hasSize(2);
        assertThat(results).containsExactly(inEmail, inDoc);

        // Query only for Document
        results = doQuery("body", "Generic");
        assertThat(results).hasSize(1);
        assertThat(results).containsExactly(inDoc);
    }

    @Test
    public void testDelete() {
        // Schema registration
        checkIsSuccess(mAppSearch.setSchema(
                new SetSchemaRequest.Builder().addSchema(AppSearchEmail.SCHEMA).build()));

        // Index documents
        AppSearchEmail email1 =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        AppSearchEmail email2 =
                new AppSearchEmail.Builder("uri2")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example 2")
                        .setBody("This is the body of the testPut second email")
                        .build();
        checkIsSuccess(mAppSearch.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(email1, email2).build()));

        // Check the presence of the documents
        assertThat(doGet("uri1")).hasSize(1);
        assertThat(doGet("uri2")).hasSize(1);

        // Delete the document
        checkIsSuccess(mAppSearch.removeByUri(
                new RemoveByUriRequest.Builder().addUris("uri1").build()));

        // Make sure it's really gone
        AppSearchBatchResult<String, GenericDocument> getResult = mAppSearch.getByUri(
                new GetByUriRequest.Builder().addUris("uri1", "uri2").build());

        assertThat(getResult.isSuccess()).isFalse();
        assertThat(getResult.getFailures().get("uri1").getResultCode())
                .isEqualTo(AppSearchResult.RESULT_NOT_FOUND);
        assertThat(getResult.getSuccesses().get("uri2")).isEqualTo(email2);
    }

    @Test
    public void testRemoveByTypes() {
        // Schema registration
        AppSearchSchema genericSchema = new AppSearchSchema.Builder("Generic").build();
        checkIsSuccess(mAppSearch.setSchema(new SetSchemaRequest.Builder()
                .addSchema(AppSearchEmail.SCHEMA, genericSchema).build()));

        // Index documents
        AppSearchEmail email1 =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        AppSearchEmail email2 =
                new AppSearchEmail.Builder("uri2")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example 2")
                        .setBody("This is the body of the testPut second email")
                        .build();
        GenericDocument document1 = new GenericDocument.Builder<>("uri3", "Generic").build();
        checkIsSuccess(mAppSearch.putDocuments(
                new PutDocumentsRequest.Builder().addGenericDocument(email1, email2, document1)
                        .build()));

        // Check the presence of the documents
        assertThat(doGet("uri1", "uri2", "uri3")).hasSize(3);

        // Delete the email type
        checkIsSuccess(mAppSearch.deleteByTypes(ImmutableList.of(AppSearchEmail.SCHEMA_TYPE)));

        // Make sure it's really gone
        AppSearchBatchResult<String, GenericDocument> getResult = mAppSearch.getByUri(
                new GetByUriRequest.Builder().addUris("uri1", "uri2", "uri3").build());
        assertThat(getResult.isSuccess()).isFalse();
        assertThat(getResult.getFailures().get("uri1").getResultCode())
                .isEqualTo(AppSearchResult.RESULT_NOT_FOUND);
        assertThat(getResult.getFailures().get("uri2").getResultCode())
                .isEqualTo(AppSearchResult.RESULT_NOT_FOUND);
        assertThat(getResult.getSuccesses().get("uri3")).isEqualTo(document1);
    }

    private List<GenericDocument> doGet(String... uris) {
        AppSearchBatchResult<String, GenericDocument> result = mAppSearch.getByUri(
                new GetByUriRequest.Builder().addUris(uris).build());
        checkIsSuccess(result);
        assertThat(result.getSuccesses()).hasSize(uris.length);
        assertThat(result.getFailures()).isEmpty();
        List<GenericDocument> list = new ArrayList<>(uris.length);
        for (String uri : uris) {
            list.add(result.getSuccesses().get(uri));
        }
        return list;
    }

    private List<GenericDocument> doQuery(String queryExpression, String... schemaTypes) {
        AppSearchResult<List<SearchResult>> result = mAppSearch.query(
                queryExpression,
                new SearchSpec.Builder()
                        .setSchemaTypes(schemaTypes)
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .build());
        checkIsSuccess(result);
        List<SearchResult> searchResults = result.getResultValue();
        List<GenericDocument> documents = new ArrayList<>(searchResults.size());
        for (SearchResult searchResult : searchResults) {
            documents.add(searchResult.getDocument());
        }
        return documents;
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
