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

package android.appsearch.app;

import static com.android.server.appsearch.testing.AppSearchTestUtils.checkIsBatchResultSuccess;
import static com.android.server.appsearch.testing.AppSearchTestUtils.doGet;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SetSchemaRequest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.appsearch.testing.AppSearchSessionShimImpl;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UserDataTest {

    private static final String DB_NAME = "";
    private static final String NAMESPACE = "namespace";
    private static final String ID = "id";
    private static final AppSearchSchema SCHEMA = new AppSearchSchema.Builder("testSchema")
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            .build();
    private static final GenericDocument DOCUMENT =
            new GenericDocument.Builder<>(NAMESPACE, ID, SCHEMA.getSchemaType())
                    .setPropertyString("subject", "testPut example1")
                    .setCreationTimestampMillis(12345L)
                    .build();

    private AppSearchSessionShim mDb;

    @Before
    public void setUp() throws Exception {
        mDb = AppSearchSessionShimImpl.createSearchSession(
                new AppSearchManager.SearchContext.Builder(DB_NAME).build()).get();
    }

    @Test
    public void testPutDocuments() throws Exception {
        // Schema registration
        mDb.setSchema(new SetSchemaRequest.Builder().addSchemas(SCHEMA).build())
                .get();

        // Index a document
        AppSearchBatchResult<String, Void> result = checkIsBatchResultSuccess(
                mDb.put(new PutDocumentsRequest.Builder().addGenericDocuments(DOCUMENT).build()));
        assertThat(result.getSuccesses()).containsExactly(ID, /*v0=*/null);
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    public void testGetDocuments_exist() throws Exception {
        List<GenericDocument> outDocuments = doGet(mDb, NAMESPACE, ID);
        assertThat(outDocuments).containsExactly(DOCUMENT);
    }

    @Test
    public void testGetDocuments_nonexist() throws Exception {
        AppSearchBatchResult<String, GenericDocument> getResult = mDb.getByDocumentId(
                new GetByDocumentIdRequest.Builder(NAMESPACE).addIds(ID).build()).get();
        assertThat(getResult.getFailures().get(ID).getResultCode())
                .isEqualTo(AppSearchResult.RESULT_NOT_FOUND);
    }

    /**
     * Clear generated data during the test.
     *
     * <p>Device side tests will be a part of host side test. We should clear the test data in the
     * host side tearDown only. Otherwise, it will wipe the data in the middle of a host side test.
     */
    @Test
    public void clearTestData() throws Exception {
        mDb.setSchema(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
    }
}
