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
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSchema.PropertyConfig;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.util.ConcurrentUtils;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class AppSearchManagerTest {
    private final Executor mExecutor = ConcurrentUtils.DIRECT_EXECUTOR;
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final AppSearchManager mAppSearch = mContext.getSystemService(AppSearchManager.class);

    @Test
    public void testGetService() {
        assertThat(mContext.getSystemService(Context.APP_SEARCH_SERVICE)).isNotNull();
        assertThat(mContext.getSystemService(AppSearchManager.class)).isNotNull();
        assertThat(mAppSearch).isNotNull();
    }

    @Test
    public void testSetSchema() {
        AppSearchSchema emailSchema = AppSearchSchema.newBuilder("Email")
                .addProperty(AppSearchSchema.newPropertyBuilder("subject")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).addProperty(AppSearchSchema.newPropertyBuilder("body")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();
        mAppSearch.setSchema(emailSchema);
    }

    @Test
    public void testPutDocuments() throws Exception {
        // Schema registration
        mAppSearch.setSchema(AppSearchEmail.SCHEMA);

        // Index a document
        AppSearchEmail email = new AppSearchEmail.Builder("uri1")
                .setFrom("from@example.com")
                .setTo("to1@example.com", "to2@example.com")
                .setSubject("testPut example")
                .setBody("This is the body of the testPut email")
                .build();

        AppSearchBatchResult result = mAppSearch.putDocuments(ImmutableList.of(email));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResults()).containsExactly("uri1", null);
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    public void testGetDocuments() throws Exception {
        // Schema registration
        mAppSearch.setSchema(AppSearch.Email.SCHEMA);

        // Index a document
        AppSearch.Email inEmail =
                AppSearch.Email.newBuilder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        assertThat(mAppSearch.putDocuments(ImmutableList.of(inEmail)).isSuccess()).isTrue();

        // Get the document
        CompletableFuture<List<AppSearch.Document>> getFuture = new CompletableFuture<>();
        mAppSearch.getDocuments(ImmutableList.of("uri1"), mExecutor, (list, err) -> {
            if (list != null) {
                getFuture.complete(list);
            } else {
                getFuture.completeExceptionally(err);
            }
        });
        List<AppSearch.Document> outDocuments = getFuture.get();
        assertThat(outDocuments).hasSize(1);
        AppSearch.Email outEmail = new AppSearch.Email(outDocuments.get(0));
        assertThat(outEmail).isEqualTo(inEmail);
    }
}
