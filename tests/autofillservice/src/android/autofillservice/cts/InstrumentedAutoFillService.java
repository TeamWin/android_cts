/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.autofillservice.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.app.assist.AssistStructure.WindowNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.service.autofill.AutoFillService;
import android.service.autofill.FillCallback;
import android.service.autofill.SaveCallback;
import android.util.Log;
import android.view.autofill.AutoFillId;
import android.view.autofill.AutoFillValue;
import android.view.autofill.Dataset;
import android.view.autofill.FillResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link AutoFillService} used in the tests.
 */
public class InstrumentedAutoFillService extends AutoFillService {

    private static final String TAG = "InstrumentedAutoFillService";

    private static final AtomicReference<CannedFillResponse> sCannedFillResponse =
            new AtomicReference<>();

    private static AtomicInteger sNumberFillRequests = new AtomicInteger(0);

    // TODO(b/33197203, b/33802548): add tests for onConnected() / onDisconnected() and/or remove
    // overriden methods below that are only logging their calls.

    @Override
    public void onConnected() {
        Log.v(TAG, "onConnected()");
    }

    @Override
    public void onDisconnected() {
        Log.v(TAG, "onDisconnected()");
    }

    @Override
    public void onFillRequest(AssistStructure structure, Bundle data,
            CancellationSignal cancellationSignal, FillCallback callback) {
        final int requestNumber = sNumberFillRequests.incrementAndGet();
        final CannedFillResponse cannedResponse = sCannedFillResponse.getAndSet(null);
        Log.v(TAG, "onFillRequest(#" + requestNumber + "): cannedResponse = " + cannedResponse);

        assertWithMessage("CancelationSignal is null").that(cancellationSignal).isNotNull();

        if (cannedResponse == null) {
            callback.onSuccess(null);
            return;
        }
        final FillResponse.Builder responseBuilder = new FillResponse.Builder("4815162342");
        final List<CannedDataset> datasets = cannedResponse.datasets;

        if (datasets.isEmpty()) {
            callback.onSuccess(responseBuilder.build());
            return;
        }

        assertWithMessage("multiple datasets not supported yet").that(datasets).hasSize(1);

        final CannedDataset dataset = datasets.get(0);

        final Map<String, AutoFillValue> fields = dataset.fields;
        if (fields.isEmpty()) {
            callback.onSuccess(responseBuilder.build());
            return;
        }

        final Dataset.Builder datasetBuilder = new Dataset.Builder(dataset.id, dataset.name);

        Log.v(TAG, "Parsing request for activity " + structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode node = structure.getWindowNodeAt(i);
            final ViewNode view = node.getRootViewNode();
            fill(datasetBuilder, fields, view);
        }

        final FillResponse fillResponse =
                responseBuilder.addDataset(datasetBuilder.build()).build();
        Log.v(TAG, "onFillRequest(): fillResponse = " + fillResponse);
        callback.onSuccess(fillResponse);
    }

    @Override
    public void onSaveRequest(AssistStructure structure, Bundle data, SaveCallback callback) {
        Log.v(TAG, "onSaveRequest()");
    }

    /**
     * Sets the response returned by the service in the next
     * {@link #onFillRequest(AssistStructure, Bundle, CancellationSignal, FillCallback)} call.
     */
    static void setFillResponse(CannedFillResponse response) {
        final boolean ok = sCannedFillResponse.compareAndSet(null, response);
        if (!ok) {
            throw new IllegalStateException("already set: " + sCannedFillResponse.get());
        }
    }

    /**
     * Resets the number of requests to {@link #onFillRequest(AssistStructure, Bundle,
     * CancellationSignal, FillCallback)} so it can be verified by
     * {@link #assertNumberFillRequests(int)}.
     */
    static void resetNumberFillRequests() {
        sNumberFillRequests.set(0);
    }

    /**
     * Asserts the number of calls to {@link #onFillRequest(AssistStructure, Bundle,
     * CancellationSignal, FillCallback)} since the last call to {@link #resetNumberFillRequests()}.
     */
    static void assertNumberFillRequests(int expected) {
        final int actual = sNumberFillRequests.get();
        assertWithMessage("Invalid number of fill requests").that(actual).isEqualTo(expected);
    }

    private void fill(Dataset.Builder builder, Map<String, AutoFillValue> fields,
            ViewNode view) {
        final String resourceId = view.getIdEntry();

        final AutoFillValue value = fields.get(resourceId);
        if (value != null) {
            final AutoFillId id = view.getAutoFillId();
            Log.d(TAG, "setting '" + resourceId + "' (" + id + ") to " + value);
            builder.setValue(id, value);
        }

        final int childrenSize = view.getChildCount();
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                fill(builder, fields, view.getChildAt(i));
            }
        }
    }
}
