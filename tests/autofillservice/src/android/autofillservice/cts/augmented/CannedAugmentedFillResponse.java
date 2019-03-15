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
package android.autofillservice.cts.augmented;

import static android.autofillservice.cts.augmented.AugmentedHelper.getContentDescriptionForUi;

import android.autofillservice.cts.R;
import android.content.Context;
import android.service.autofill.augmented.FillCallback;
import android.service.autofill.augmented.FillController;
import android.service.autofill.augmented.FillRequest;
import android.service.autofill.augmented.FillResponse;
import android.service.autofill.augmented.FillWindow;
import android.service.autofill.augmented.PresentationParams;
import android.service.autofill.augmented.PresentationParams.Area;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class used to produce a {@link FillResponse}.
 */
public final class CannedAugmentedFillResponse {

    private static final String TAG = CannedAugmentedFillResponse.class.getSimpleName();

    private final AugmentedResponseType mResponseType;
    private final Map<AutofillId, Dataset> mDatasets;
    private long mDelay;

    private CannedAugmentedFillResponse(@NonNull Builder builder) {
        mResponseType = builder.mResponseType;
        mDatasets = builder.mDatasets;
        mDelay = builder.mDelay;
    }

    /**
     * Constant used to pass a {@code null} response to the
     * {@link FillCallback#onSuccess(FillResponse)} method.
     */
    public static final CannedAugmentedFillResponse NO_AUGMENTED_RESPONSE =
            new Builder(AugmentedResponseType.NULL).build();

    /**
     * Constant used to emulate a timeout by not calling any method on {@link FillCallback}.
     */
    public static final CannedAugmentedFillResponse DO_NOT_REPLY_AUGMENTED_RESPONSE =
            new Builder(AugmentedResponseType.TIMEOUT).build();

    public AugmentedResponseType getResponseType() {
        return mResponseType;
    }

    public long getDelay() {
        return mDelay;
    }

    /**
     * Creates the "real" response.
     */
    public FillResponse asFillResponse(@NonNull Context context, @NonNull FillRequest request,
            @NonNull FillController controller) {
        final AutofillId focusedId = request.getFocusedId();

        final Dataset dataset = mDatasets.get(focusedId);
        if (dataset == null) {
            Log.d(TAG, "no dataset for field " + focusedId);
            return null;
        }

        Log.d(TAG, "asFillResponse: id=" + focusedId + ", dataset=" + dataset);

        final PresentationParams presentationParams = request.getPresentationParams();
        if (presentationParams == null) {
            Log.w(TAG, "No PresentationParams");
            return null;
        }

        final Area strip = presentationParams.getSuggestionArea();
        if (strip == null) {
            Log.w(TAG, "No suggestion strip");
            return null;
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        final TextView rootView = (TextView) inflater.inflate(R.layout.augmented_autofill_ui, null);

        Log.d(TAG, "Setting autofill UI text to:" + dataset.mPresentation);
        rootView.setText(dataset.mPresentation);

        rootView.setContentDescription(getContentDescriptionForUi(focusedId));
        final FillWindow fillWindow = new FillWindow();
        rootView.setOnClickListener((v) -> {
            Log.d(TAG, "Destroying window first");
            fillWindow.destroy();
            final List<Pair<AutofillId, AutofillValue>> values = dataset.getValues();
            Log.i(TAG, "Autofilling: " + AugmentedHelper.toString(values));
            controller.autofill(values);
        });

        boolean ok = fillWindow.update(strip, rootView, 0);
        if (!ok) {
            Log.w(TAG, "FillWindow.update() failed for " + strip + " and " + rootView);
            return null;
        }

        return new FillResponse.Builder().setFillWindow(fillWindow).build();
    }

    @Override
    public String toString() {
        return "CannedAugmentedFillResponse: [type=" + mResponseType
                + ",datasets=" + mDatasets
                + "]";
    }
    public enum AugmentedResponseType {
        NORMAL,
        NULL,
        TIMEOUT,
    }

    public static final class Builder {
        private final Map<AutofillId, Dataset> mDatasets = new ArrayMap<>();
        private final AugmentedResponseType mResponseType;
        private long mDelay;

        public Builder(@NonNull AugmentedResponseType type) {
            mResponseType = type;
        }

        public Builder() {
            this(AugmentedResponseType.NORMAL);
        }

        /**
         * Sets the {@link Dataset} that will be filled when the given {@code ids} is focused and
         * the UI is tapped.
         */
        public Builder setDataset(@NonNull Dataset dataset, @NonNull AutofillId... ids) {
            for (AutofillId id : ids) {
                mDatasets.put(id, dataset);
            }
            return this;
        }

        /**
         * Sets the delay for onFillRequest().
         */
        public Builder setDelay(long delay) {
            mDelay = delay;
            return this;
        }

        public CannedAugmentedFillResponse build() {
            return new CannedAugmentedFillResponse(this);
        }
    } // CannedAugmentedFillResponse.Builder


    /**
     * Helper class used to define which fields will be autofilled when the user taps the Augmented
     * Autofill UI.
     */
    public static class Dataset {
        private final Map<AutofillId, AutofillValue> mFieldValuesById;
        private final String mPresentation;

        private Dataset(@NonNull Builder builder) {
            mFieldValuesById = builder.mFieldValuesById;
            mPresentation = builder.mPresentation;
        }

        public List<Pair<AutofillId, AutofillValue>> getValues() {
            return mFieldValuesById.entrySet().stream()
                    .map((entry) -> (new Pair<>(entry.getKey(), entry.getValue())))
                    .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return "Dataset: [presentation=" + mPresentation
                    + ", fields=" + mFieldValuesById
                    + "]";
        }

        public static class Builder {
            private final Map<AutofillId, AutofillValue> mFieldValuesById = new ArrayMap<>();

            private final String mPresentation;

            public Builder(@NonNull String presentation) {
                mPresentation = Preconditions.checkNotNull(presentation);
            }

            /**
             * Sets the value that will be autofilled on the field with {@code id}.
             */
            public Builder setField(@NonNull AutofillId id, String text) {
                mFieldValuesById.put(id, AutofillValue.forText(text));
                return this;
            }
            public Dataset build() {
                return new Dataset(this);
            }
        } // Dataset.Builder
    } // Dataset
} // CannedAugmentedFillResponse
