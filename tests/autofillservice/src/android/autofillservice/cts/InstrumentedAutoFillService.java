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

import static android.autofillservice.cts.Helper.findNodeByResourceId;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link AutoFillService} used in the tests.
 */
public class InstrumentedAutoFillService extends AutoFillService {

    private static final String TAG = "InstrumentedAutoFillService";

    private static final AtomicReference<FillReplier> sFillReplier = new AtomicReference<>();

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
        final FillReplier replier = sFillReplier.getAndSet(null);
        assertWithMessage("FillReplier not set").that(replier).isNotNull();

        replier.onFillRequest(structure, data, cancellationSignal, callback);
    }

    @Override
    public void onSaveRequest(AssistStructure structure, Bundle data, SaveCallback callback) {
        Log.v(TAG, "onSaveRequest()");
    }

    /**
     * Sets the {@link FillReplier} for the
     * {@link #onFillRequest(AssistStructure, Bundle, CancellationSignal, FillCallback)} calls.
     */
    public static void setFillReplier(FillReplier replier) {
        final boolean ok = sFillReplier.compareAndSet(null, replier);
        if (!ok) {
            throw new IllegalStateException("already set: " + sFillReplier.get());
        }
    }

    public static void resetFillReplier() {
        sFillReplier.set(null);
    }

    /**
     * POJO representation of the contents of a
     * {@link AutoFillService#onFillRequest(android.app.assist.AssistStructure, android.os.Bundle,
     * android.os.CancellationSignal, android.service.autofill.FillCallback)}
     * that can be asserted at the end of a test case.
     */
    static final class Request {
        final AssistStructure structure;
        final Bundle data;
        final CancellationSignal cancellationSignal;
        final FillCallback callback;

        private Request(AssistStructure structure, Bundle data,
                CancellationSignal cancellationSignal, FillCallback callback) {
            this.structure = structure;
            this.data = data;
            this.cancellationSignal = cancellationSignal;
            this.callback = callback;
        }
    }

    /**
     * Object used to answer a
     * {@link AutoFillService#onFillRequest(android.app.assist.AssistStructure, android.os.Bundle,
     * android.os.CancellationSignal, android.service.autofill.FillCallback)}
     * on behalf of a unit test method.
     */
    static final class FillReplier {

        private AtomicInteger mNumberFillRequests = new AtomicInteger(0);
        private final Queue<CannedFillResponse> mResponses = new LinkedList<>();
        private final Queue<Request> mRequests = new LinkedList<>();

        /**
         * Sets the expectation for the next {@code onFillRequest} as {@link FillResponse} with just
         * one {@link Dataset}.
         */
        FillReplier addResponse(CannedDataset dataset) {
            return addResponse(new CannedFillResponse.Builder()
                    .addDataset(dataset)
                    .build());
        }

        /**
         * Sets the expectation for the next {@code onFillRequest}.
         */
        FillReplier addResponse(CannedFillResponse response) {
            mResponses.add(response);
            return this;
        }

        /**
         * Gets the next request, in the order received.
         *
         * <p>Typically called at the end of a test case, to assert the initial request.
         */
        Request getNextRequest() {
            return mRequests.remove();
        }

        /**
         * Resets the number of requests to
         * {@link #onFillRequest(AssistStructure, Bundle, CancellationSignal, FillCallback)} so it
         * can be verified by {@link #assertNumberFillRequests(int)}.
         */
        void resetNumberFillRequests() {
            mNumberFillRequests.set(0);
        }

        /**
         * Asserts the number of calls to
         * {@link #onFillRequest(AssistStructure, Bundle, CancellationSignal, FillCallback)} since
         * the last call to {@link #resetNumberFillRequests()}.
         */
        void assertNumberFillRequests(int expected) {
            final int actual = mNumberFillRequests.get();
            assertWithMessage("Invalid number of fill requests").that(actual).isEqualTo(expected);
        }

        private void onFillRequest(AssistStructure structure,
                @SuppressWarnings("unused") Bundle data, CancellationSignal cancellationSignal,
                FillCallback callback) {

            mRequests.add(new Request(structure, data, cancellationSignal, callback));

            final CannedFillResponse response = mResponses.remove();

            final int requestNumber = mNumberFillRequests.incrementAndGet();
            Log.v(TAG, "onFillRequest(#" + requestNumber + ")");

            if (response == null) {
                callback.onSuccess(null);
                return;
            }
            final FillResponse.Builder responseBuilder = new FillResponse.Builder("4815162342");
            final List<CannedDataset> datasets = response.datasets;

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
            for (Map.Entry<String, AutoFillValue> entry : fields.entrySet()) {
                final String resourceId = entry.getKey();
                final ViewNode node = findNodeByResourceId(structure, resourceId);
                assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
                final AutoFillId id = node.getAutoFillId();
                final AutoFillValue value = entry.getValue();
                Log.d(TAG, "setting '" + resourceId + "' (" + id + ") to " + value);
                datasetBuilder.setValue(id, value);
            }

            final FillResponse fillResponse = responseBuilder.addDataset(datasetBuilder.build())
                    .build();
            Log.v(TAG, "onFillRequest(): fillResponse = " + fillResponse);
            callback.onSuccess(fillResponse);
        }
    }
}
