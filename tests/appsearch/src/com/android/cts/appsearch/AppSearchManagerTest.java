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

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSchema.IndexingConfig;
import android.app.appsearch.AppSearchSchema.PropertyConfig;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.util.ConcurrentUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

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
    public void testSetSchema() throws Exception {
        AppSearchSchema schema = AppSearchSchema.newBuilder()
                .addType(AppSearchSchema.newSchemaTypeBuilder("Email")
                        .addProperty(AppSearchSchema.newPropertyBuilder("subject")
                                .setDataType(PropertyConfig.DATA_TYPE_STRING)
                                .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                                .setIndexingConfig(
                                        AppSearchSchema.newIndexingConfigBuilder()
                                                .setTokenizerType(
                                                        IndexingConfig.TOKENIZER_TYPE_PLAIN)
                                                .setTermMatchType(
                                                        IndexingConfig.TERM_MATCH_TYPE_PREFIX)
                                                .build()
                                ).build()
                        ).addProperty(AppSearchSchema.newPropertyBuilder("body")
                                .setDataType(PropertyConfig.DATA_TYPE_STRING)
                                .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                                .setIndexingConfig(
                                        AppSearchSchema.newIndexingConfigBuilder()
                                                .setTokenizerType(
                                                        IndexingConfig.TOKENIZER_TYPE_PLAIN)
                                                .setTermMatchType(
                                                        IndexingConfig.TERM_MATCH_TYPE_PREFIX)
                                                .build()
                                ).build()
                        ).build()
                ).build();
        CompletableFuture<Throwable> result = new CompletableFuture<>();
        mAppSearch.setSchema(schema, mExecutor, result::complete);

        // TODO(b/142567528): Once setSchema is implemented, this result should be 'null'.
        Throwable ex = result.get();
        assertThat(ex).isInstanceOf(UnsupportedOperationException.class);
    }
}
