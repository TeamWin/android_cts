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

package android.autofillservice.cts.testcore;

import static android.autofillservice.cts.testcore.CannedFillResponse.ResponseType.FAILURE;
import static android.autofillservice.cts.testcore.CannedFillResponse.ResponseType.NULL;
import static android.autofillservice.cts.testcore.CannedFillResponse.ResponseType.TIMEOUT;
import static android.autofillservice.cts.testcore.Timeouts.CONNECTION_TIMEOUT;
import static android.autofillservice.cts.testcore.Timeouts.FILL_TIMEOUT;
import static android.autofillservice.cts.testcore.Timeouts.RESPONSE_DELAY_MS;

import static com.google.common.truth.Truth.assertThat;

import android.content.IntentSender;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.SystemClock;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillRequestCallback;
import android.view.inputmethod.InlineSuggestionsRequest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.RetryableException;
import com.android.compatibility.common.util.TestNameUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Implements an {@link AutofillRequestCallback} for testing client suggestions behavior.
 */
public class ClientAutofillRequestCallback implements AutofillRequestCallback {
    private static final String TAG = "ClientAutofillRequestCallback";
    private final Handler mHandler;
    private final @NonNull Function<String, AutofillId> mIdResolver;
    private final Replier mReplier;

    public ClientAutofillRequestCallback(@NonNull  Handler handler,
            @NonNull Function<String, AutofillId> idResolver) {
        mHandler = handler;
        mIdResolver = idResolver;
        mReplier = new Replier(mIdResolver);
    }

    @Override
    public void onFillRequest(InlineSuggestionsRequest inlineSuggestionsRequest,
            CancellationSignal cancellationSignal, FillCallback callback) {

        if (!TestNameUtils.isRunningTest()) {
            Log.e(TAG, "onFillRequest(client) called after tests finished");
            return;
        }

        mHandler.post(
                () -> mReplier.onFillRequest(
                        cancellationSignal, callback, inlineSuggestionsRequest));
    }

    public Replier getReplier() {
        return mReplier;
    }

    /**
     * POJO representation of the contents of a
     * {@link ClientAutofillRequestCallback#onFillRequest(InlineSuggestionsRequest,
     * CancellationSignal, FillCallback)} that can be asserted at the end of a test case.
     */
    public static final class FillRequest {

        public final CancellationSignal cancellationSignal;
        public final FillCallback callback;
        public final InlineSuggestionsRequest inlineRequest;

        private FillRequest(CancellationSignal cancellationSignal, FillCallback callback,
                InlineSuggestionsRequest inlineRequest) {
            this.cancellationSignal = cancellationSignal;
            this.callback = callback;
            this.inlineRequest = inlineRequest;
        }

        @Override
        public String toString() {
            return "FillRequest[has inlineRequest=" + (inlineRequest != null) + "]";
        }
    }

    /**
     * Object used to answer a
     * {@link AutofillRequestCallback#onFillRequest(InlineSuggestionsRequest, CancellationSignal,
     * FillCallback)}
     * on behalf of a unit test method.
     */
    public static final class Replier {
        // TODO: refactor with InstrumentedAutoFillService$Replier

        private final BlockingQueue<CannedFillResponse> mResponses = new LinkedBlockingQueue<>();
        private final BlockingQueue<FillRequest> mFillRequests =
                new LinkedBlockingQueue<>();

        private List<Throwable> mExceptions;
        private IntentSender mOnSaveIntentSender;
        private String mAcceptedPackageName;

        private Handler mHandler;

        private boolean mReportUnhandledFillRequest = true;
        private boolean mReportUnhandledSaveRequest = true;
        private final @NonNull Function<String, AutofillId> mIdResolver;

        private Replier(@NonNull Function<String, AutofillId> idResolver) {
            mIdResolver = idResolver;
        }

        public void acceptRequestsFromPackage(String packageName) {
            mAcceptedPackageName = packageName;
        }

        /**
         * Gets the exceptions thrown asynchronously, if any.
         */
        @Nullable
        public List<Throwable> getExceptions() {
            return mExceptions;
        }

        private void addException(@Nullable Throwable e) {
            if (e == null) return;

            if (mExceptions == null) {
                mExceptions = new ArrayList<>();
            }
            mExceptions.add(e);
        }

        /**
         * Sets the expectation for the next {@code onFillRequest} as {@link FillResponse} with
         * just one {@link Dataset}.
         */
        public Replier addResponse(
                CannedFillResponse.CannedDataset dataset) {
            return addResponse(new CannedFillResponse.Builder()
                    .addDataset(dataset)
                    .build());
        }

        /**
         * Sets the expectation for the next {@code onFillRequest}.
         */
        public Replier addResponse(CannedFillResponse response) {
            if (response == null) {
                throw new IllegalArgumentException("Cannot be null - use NO_RESPONSE instead");
            }
            mResponses.add(response);
            return this;
        }

        /**
         * Sets the {@link IntentSender} that is passed to
         * {@link SaveCallback#onSuccess(IntentSender)}.
         */
        public Replier setOnSave(IntentSender intentSender) {
            mOnSaveIntentSender = intentSender;
            return this;
        }

        /**
         * Gets the next fill request, in the order received.
         */
        public FillRequest getNextFillRequest() {
            FillRequest request;
            try {
                request = mFillRequests.poll(FILL_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted", e);
            }
            if (request == null) {
                throw new RetryableException(FILL_TIMEOUT, "onFillRequest() not called");
            }
            return request;
        }

        /**
         * Assets the client had received fill request.
         */
        public void assertReceivedRequest() {
            getNextFillRequest();
        }

        /**
         * Asserts that {@link AutofillRequestCallback#onFillRequest(InlineSuggestionsRequest,
         * CancellationSignal, FillCallback)} was not called.
         *
         * <p>Should only be called in cases where it's not expected to be called, as it will
         * sleep for a few ms.
         */
        public void assertOnFillRequestNotCalled() {
            SystemClock.sleep(FILL_TIMEOUT.getMaxValue());
            assertThat(mFillRequests).isEmpty();
        }

        /**
         * Asserts all {@link AutofillService#onFillRequest(
         * android.service.autofill.FillRequest,  CancellationSignal, FillCallback) fill requests}
         * received by the service were properly {@link #getNextFillRequest() handled} by the test
         * case.
         */
        public void assertNoUnhandledFillRequests() {
            if (mFillRequests.isEmpty()) return; // Good job, test case!

            if (!mReportUnhandledFillRequest) {
                // Just log, so it's not thrown again on @After if already thrown on main body
                Log.d(TAG, "assertNoUnhandledFillRequests(): already reported, "
                        + "but logging just in case: " + mFillRequests);
                return;
            }

            mReportUnhandledFillRequest = false;
            throw new AssertionError(mFillRequests.size()
                    + " unhandled fill requests: " + mFillRequests);
        }

        /**
         * Gets the current number of unhandled requests.
         */
        public int getNumberUnhandledFillRequests() {
            return mFillRequests.size();
        }

        public void setHandler(Handler handler) {
            mHandler = handler;
        }

        /**
         * Resets its internal state.
         */
        public void reset() {
            mResponses.clear();
            mFillRequests.clear();
            mExceptions = null;
            mOnSaveIntentSender = null;
            mAcceptedPackageName = null;
            mReportUnhandledFillRequest = true;
            mReportUnhandledSaveRequest = true;
        }

        public void onFillRequest(CancellationSignal cancellationSignal, FillCallback callback,
                InlineSuggestionsRequest inlineSuggestionsRequest) {
            try {
                CannedFillResponse response = null;
                try {
                    response = mResponses.poll(CONNECTION_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted getting CannedResponse: " + e);
                    Thread.currentThread().interrupt();
                    addException(e);
                    return;
                }
                if (response == null) {
                    Log.d(TAG, "response is null");
                    return;
                }
                if (response.getResponseType() == NULL) {
                    Log.d(TAG, "onFillRequest(): replying with null");
                    callback.onSuccess(null);
                    return;
                }

                if (response.getResponseType() == TIMEOUT) {
                    Log.d(TAG, "onFillRequest(): not replying at all");
                    return;
                }

                if (response.getResponseType() == FAILURE) {
                    Log.d(TAG, "onFillRequest(): replying with failure");
                    callback.onFailure("D'OH!");
                    return;
                }

                if (response.getResponseType() == CannedFillResponse.ResponseType.NO_MORE) {
                    Log.w(TAG, "onFillRequest(): replying with null when not expecting more");
                    addException(new IllegalStateException("got unexpected request"));
                    callback.onSuccess(null);
                    return;
                }

                final String failureMessage = response.getFailureMessage();
                if (failureMessage != null) {
                    Log.v(TAG, "onFillRequest(): failureMessage = " + failureMessage);
                    callback.onFailure(failureMessage);
                    return;
                }

                final FillResponse fillResponse;
                fillResponse = response.asFillResponseWithAutofillId(null, mIdResolver);

                if (response.getResponseType() == CannedFillResponse.ResponseType.DELAY) {
                    mHandler.postDelayed(() -> {
                        Log.v(TAG,
                                "onFillRequest(): fillResponse = " + fillResponse);
                        callback.onSuccess(fillResponse);
                        // Add a fill request to let test case know response was sent.
                        Helper.offer(
                                mFillRequests,
                                new FillRequest(cancellationSignal, callback,
                                        inlineSuggestionsRequest),
                                CONNECTION_TIMEOUT.ms());
                    }, RESPONSE_DELAY_MS);
                } else {
                    Log.v(TAG, "onFillRequest(): fillResponse = " + fillResponse);
                    callback.onSuccess(fillResponse);
                }
            } catch (Throwable t) {
                Log.d(TAG, "onFillRequest(): catch a Throwable: " + t);
                addException(t);
            } finally {
                Helper.offer(
                        mFillRequests,
                        new FillRequest(cancellationSignal, callback,
                                inlineSuggestionsRequest),
                        CONNECTION_TIMEOUT.ms());
            }
        }

        private void onSaveRequest(List<FillContext> contexts, Bundle data, SaveCallback callback,
                List<String> datasetIds) {
            Log.d(TAG, "onSaveRequest(): sender=" + mOnSaveIntentSender);

            try {
                if (mOnSaveIntentSender != null) {
                    callback.onSuccess(mOnSaveIntentSender);
                } else {
                    callback.onSuccess();
                }
            } finally {
                //TODO
            }
        }

        private void dump(PrintWriter pw) {
            pw.print("mResponses: "); pw.println(mResponses);
            pw.print("mFillRequests: "); pw.println(mFillRequests);
            pw.print("mExceptions: "); pw.println(mExceptions);
            pw.print("mOnSaveIntentSender: "); pw.println(mOnSaveIntentSender);
            pw.print("mAcceptedPackageName: "); pw.println(mAcceptedPackageName);
            pw.print("mAcceptedPackageName: "); pw.println(mAcceptedPackageName);
            pw.print("mReportUnhandledFillRequest: "); pw.println(mReportUnhandledSaveRequest);
        }
    }
}
