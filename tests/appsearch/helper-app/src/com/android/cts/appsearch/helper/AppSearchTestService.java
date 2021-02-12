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
package com.android.cts.appsearch.helper;

import static com.android.server.appsearch.testing.AppSearchTestUtils.convertSearchResultsToDocuments;

import android.app.Service;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.android.cts.appsearch.ICommandReceiver;
import com.android.server.appsearch.testing.GlobalSearchSessionShimImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppSearchTestService extends Service {

    private static final String TAG = "AppSearchTestService";
    private GlobalSearchSessionShim mGlobalSearchSession;

    @Override
    public void onCreate() {
        try {
            // We call this here so we can pass in a context. If we try to create the session in the
            // stub, it'll try to grab the context from ApplicationProvider. But that will fail
            // since this isn't instrumented.
            mGlobalSearchSession =
                    GlobalSearchSessionShimImpl.createGlobalSearchSession(this).get();
        } catch (Exception e) {
            Log.wtf(TAG, "Error starting service.", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new CommandReceiver();
    }

    private class CommandReceiver extends ICommandReceiver.Stub {

        @Override
        public List<String> globalSearch(String queryExpression) {
            try {
                final SearchSpec searchSpec =
                        new SearchSpec.Builder()
                                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                .build();
                SearchResultsShim searchResults =
                        mGlobalSearchSession.search(queryExpression, searchSpec);
                List<GenericDocument> results = convertSearchResultsToDocuments(searchResults);

                List<String> resultStrings = new ArrayList<>();
                for (GenericDocument doc : results) {
                    resultStrings.add(doc.toString());
                }

                return resultStrings;
            } catch (Exception e) {
                Log.wtf(TAG, "Error issuing global search.", e);
                return Collections.emptyList();
            }
        }
    }
}
