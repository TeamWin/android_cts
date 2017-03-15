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

import static android.autofillservice.cts.Helper.CONNECTION_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.SAVE_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.IDLE_UNBIND_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.dumpAutofillService;
import static android.autofillservice.cts.Helper.dumpStructure;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link AutofillService} used in the tests.
 */
public class InstrumentedAutoFillService extends AutofillService {

    private static final String TAG = "InstrumentedAutoFillService";

    private static final boolean DUMP_FILL_REQUESTS = false;
    private static final boolean DUMP_SAVE_REQUESTS = false;

    private static final String STATE_CONNECTED = "CONNECTED";
    private static final String STATE_DISCONNECTED = "DISCONNECTED";

    private static final AtomicReference<InstrumentedAutoFillService> sInstance =
            new AtomicReference<>();
    private static final AtomicReference<Replier> sReplier = new AtomicReference<>();
    private static final BlockingQueue<String> sConnectionStates = new LinkedBlockingQueue<>();

    public InstrumentedAutoFillService() {
        sInstance.set(this);
    }

    public static AutofillService peekInstance() {
        return sInstance.get();
    }

    // TODO(b/33197203, b/33802548): add tests for onConnected() / onDisconnected() and/or remove
    // overriden methods below that are only logging their calls.
    @Override
    public void onConnected() {
        Log.v(TAG, "onConnected(): " + sConnectionStates);
        sConnectionStates.offer(STATE_CONNECTED);
    }

    @Override
    public void onDisconnected() {
        Log.v(TAG, "onDisconnected(): " + sConnectionStates);
        sConnectionStates.offer(STATE_DISCONNECTED);
    }

    @Override
    public void onFillRequest(AssistStructure structure, Bundle data,
            int flags, CancellationSignal cancellationSignal, FillCallback callback) {
        if (DUMP_FILL_REQUESTS) dumpStructure("onFillRequest()", structure);

        final Replier replier = sReplier.getAndSet(null);
        assertWithMessage("Replier not set").that(replier).isNotNull();
        replier.onFillRequest(structure, data, cancellationSignal, callback, flags);
    }

    @Override
    public void onSaveRequest(AssistStructure structure, Bundle data, SaveCallback callback) {
        if (DUMP_SAVE_REQUESTS) dumpStructure("onSaveRequest()", structure);

        final Replier replier = sReplier.getAndSet(null);
        assertWithMessage("Replier not set").that(replier).isNotNull();
        replier.onSaveRequest(structure, data, callback);
    }

    /**
     * Waits until {@link #onConnected()} is called, or fails if it times out.
     */
    static void waitUntilConnected() throws InterruptedException {
        final String state = sConnectionStates.poll(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (state == null) {
            dumpAutofillService();
            throw new AssertionError("not connected in " + CONNECTION_TIMEOUT_MS + " ms");
        }
        assertWithMessage("Invalid connection state").that(state).isEqualTo(STATE_CONNECTED);
    }

    /**
     * Waits until {@link #onDisconnected()} is called, or fails if it times out.
     */
    static void waitUntilDisconnected() throws InterruptedException {
        final String state = sConnectionStates.poll(2 * IDLE_UNBIND_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        if (state == null) {
            throw new AssertionError("not disconnected in " + IDLE_UNBIND_TIMEOUT_MS + " ms");
        }
        assertWithMessage("Invalid connection state").that(state).isEqualTo(STATE_DISCONNECTED);
    }

    /**
     * Sets the {@link Replier} for the
     * {@link #onFillRequest(AssistStructure, Bundle, int, CancellationSignal, FillCallback)} and
     * {@link #onSaveRequest(AssistStructure, Bundle, SaveCallback)} calls.
     */
    static void setReplier(Replier replier) {
        final boolean ok = sReplier.compareAndSet(null, replier);
        if (!ok) {
            throw new IllegalStateException("already set: " + sReplier.get());
        }
    }

    static void resetStaticState() {
        sReplier.set(null);
        sConnectionStates.clear();
    }

    /**
     * POJO representation of the contents of a
     * {@link AutofillService#onFillRequest(android.app.assist.AssistStructure, android.os.Bundle,
     * android.os.CancellationSignal, android.service.autofill.FillCallback)}
     * that can be asserted at the end of a test case.
     */
    static final class FillRequest {
        final AssistStructure structure;
        final Bundle data;
        final CancellationSignal cancellationSignal;
        final FillCallback callback;
        final int flags;

        private FillRequest(AssistStructure structure, Bundle data,
                CancellationSignal cancellationSignal, FillCallback callback, int flags) {
            this.structure = structure;
            this.data = data;
            this.cancellationSignal = cancellationSignal;
            this.callback = callback;
            this.flags = flags;
        }

    }

    /**
     * POJO representation of the contents of a
     * {@link AutofillService#onSaveRequest(AssistStructure, Bundle, SaveCallback)}
     * that can be asserted at the end of a test case.
     */
    static final class SaveRequest {
        final AssistStructure structure;
        final Bundle data;
        final SaveCallback callback;

        private SaveRequest(AssistStructure structure, Bundle data, SaveCallback callback) {
            this.structure = structure;
            this.data = data;
            this.callback = callback;
        }
    }

    /**
     * Object used to answer a
     * {@link AutofillService#onFillRequest(android.app.assist.AssistStructure, android.os.Bundle,
     * int, android.os.CancellationSignal, android.service.autofill.FillCallback)}
     * on behalf of a unit test method.
     */
    static final class Replier {

        private final Queue<CannedFillResponse> mResponses = new LinkedList<>();
        private final BlockingQueue<FillRequest> mFillRequests = new LinkedBlockingQueue<>();
        private final BlockingQueue<SaveRequest> mSaveRequests = new LinkedBlockingQueue<>();

        /**
         * Sets the expectation for the next {@code onFillRequest} as {@link FillResponse} with just
         * one {@link Dataset}.
         */
        Replier addResponse(CannedDataset dataset) {
            return addResponse(new CannedFillResponse.Builder()
                    .addDataset(dataset)
                    .build());
        }

        /**
         * Sets the expectation for the next {@code onFillRequest}.
         */
        Replier addResponse(CannedFillResponse response) {
            mResponses.add(response);
            return this;
        }

        /**
         * Gets the next fill request, in the order received.
         *
         * <p>Typically called at the end of a test case, to assert the initial request.
         */
        FillRequest getNextFillRequest() throws InterruptedException {
            final FillRequest request = mFillRequests.poll(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (request == null) {
                throw new AssertionError("onFill() not called in " + FILL_TIMEOUT_MS + " ms");
            }
            return request;
        }

        /**
         * Asserts the total number of {@link AutofillService#onFillRequest(AssistStructure, Bundle,
         * int, CancellationSignal, FillCallback)}, minus those returned by
         * {@link #getNextFillRequest()}.
         */
        void assertNumberUnhandledFillRequests(int expected) {
            assertWithMessage("Invalid number of fill requests").that(mFillRequests.size())
                    .isEqualTo(expected);
        }

        /**
         * Gets the next save request, in the order received.
         *
         * <p>Typically called at the end of a test case, to assert the initial request.
         */
        SaveRequest getNextSaveRequest() throws InterruptedException {
            final SaveRequest request = mSaveRequests.poll(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (request == null) {
                throw new AssertionError("onSave() not called in " + SAVE_TIMEOUT_MS + " ms");
            }
            return request;
        }

        /**
         * Asserts the total number of {@link AutofillService#onSaveRequest(AssistStructure,
         * Bundle, SaveCallback)} minus those returned by {@link #getNextSaveRequest()}.
         */
        void assertNumberUnhandledSaveRequests(int expected) {
            assertWithMessage("Invalid number of save requests").that(mSaveRequests.size())
                    .isEqualTo(expected);
        }

        private void onFillRequest(AssistStructure structure, Bundle data,
                CancellationSignal cancellationSignal, FillCallback callback, int flags) {
            try {
                final CannedFillResponse response = mResponses.remove();
                if (response == null) {
                    callback.onSuccess(null);
                    return;
                }

                final FillResponse fillResponse = response.asFillResponse(structure);

                Log.v(TAG, "onFillRequest(): fillResponse = " + fillResponse);
                callback.onSuccess(fillResponse);
            } finally {
                mFillRequests.offer(new FillRequest(structure, data, cancellationSignal, callback,
                        flags));
            }
        }

        private void onSaveRequest(AssistStructure structure, Bundle data, SaveCallback callback) {
            Log.d(TAG, "onSaveRequest()");
            mSaveRequests.offer(new SaveRequest(structure, data, callback));
            callback.onSuccess();
        }
    }
}
