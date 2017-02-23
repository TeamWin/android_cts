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

import static android.autofillservice.cts.Helper.dumpStructure;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.content.IntentSender;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.view.autofill.AutoFillId;
import android.view.autofill.AutoFillValue;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class used to produce a {@link FillResponse} based on expected fields that should be
 * present in the {@link AssistStructure}.
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * InstrumentedAutoFillService.setFillResponse(new CannedFillResponse.Builder()
 *               .addDataset(new CannedDataset.Builder("dataset_name")
 *                   .setField("resource_id1", AutoFillValue.forText("value1"))
 *                   .setField("resource_id2", AutoFillValue.forText("value2"))
 *                   .build())
 *               .build());
 * </pre class="prettyprint">
 */
final class CannedFillResponse {

    final List<CannedDataset> datasets;
    final String[] savableIds;
    final Bundle extras;
    final RemoteViews presentation;
    final IntentSender authentication;

    private CannedFillResponse(Builder builder) {
        datasets = builder.mDatasets;
        savableIds = builder.mSavableIds;
        extras = builder.mExtras;
        presentation = builder.mPresentation;
        authentication = builder.mAuthentication;
    }

    /**
     * Creates a new response, replacing the dataset field ids by the real ids from the assist
     * structure.
     */
    FillResponse asFillResponse(AssistStructure structure) {
        final FillResponse.Builder builder = new FillResponse.Builder();
        if (datasets != null) {
            for (CannedFillResponse.CannedDataset cannedDataset : datasets) {
                final Dataset dataset = cannedDataset.asDataset(structure);
                assertWithMessage("Cannot create datase").that(dataset).isNotNull();
                builder.addDataset(dataset);
            }
        }
        if (savableIds != null) {
            for (String resourceId : savableIds) {
                final ViewNode node = findNodeByResourceId(structure, resourceId);
                if (node == null) {
                    dumpStructure("onFillRequest()", structure);
                    throw new AssertionError("No node with savable resourceId " + resourceId);
                }
                final AutoFillId id = node.getAutoFillId();
                builder.addSavableFields(id);
            }
        }
        builder.setExtras(extras);
        builder.setAuthentication(authentication, presentation);
        return builder.build();
    }

    @Override
    public String toString() {
        return "CannedFillResponse: [datasets=" + datasets
                + ", savableIds=" + Arrays.toString(savableIds)
                + ", hasPresentation=" + (presentation != null)
                + ", hasPAuthentication=" + (authentication != null)
                + "]";
    }

    static class Builder {
        private final List<CannedDataset> mDatasets = new ArrayList<>();
        private String[] mSavableIds;
        private Bundle mExtras;
        private RemoteViews mPresentation;
        private IntentSender mAuthentication;

        public Builder addDataset(CannedDataset dataset) {
            mDatasets.add(dataset);
            return this;
        }

        /**
         * Sets the savable ids based on they {@code resourceId}.
         */
        public Builder setSavableIds(String... ids) {
            mSavableIds = ids;
            return this;
        }

        /**
         * Sets the extra passed to {@link
         * android.service.autofill.FillResponse.Builder#setExtras(Bundle)}.
         */
        public Builder setExtras(Bundle data) {
            mExtras = data;
            return this;
        }

        /**
         * Sets the view to present the response in the UI.
         */
        public Builder setPresentation(RemoteViews presentation) {
            mPresentation = presentation;
            return this;
        }

        /**
         * Sets the authentication intent.
         */
        public Builder setAuthentication(IntentSender authentication) {
            mAuthentication = authentication;
            return this;
        }

        public CannedFillResponse build() {
            return new CannedFillResponse(this);
        }
    }

    /**
     * Helper class used to produce a {@link Dataset} based on expected fields that should be
     * present in the {@link AssistStructure}.
     *
     * <p>Typical usage:
     *
     * <pre class="prettyprint">
     * InstrumentedAutoFillService.setFillResponse(new CannedFillResponse.Builder()
     *               .addDataset(new CannedDataset.Builder("dataset_name")
     *                   .setField("resource_id1", AutoFillValue.forText("value1"))
     *                   .setField("resource_id2", AutoFillValue.forText("value2"))
     *                   .build())
     *               .build());
     * </pre class="prettyprint">
     */
    static class CannedDataset {
        final Map<String, AutoFillValue> fields;
        final RemoteViews presentation;
        final IntentSender authentication;

        private CannedDataset(Builder builder) {
            fields = builder.mFields;
            presentation = builder.mPresentation;
            authentication = builder.mAuthentication;
        }

        /**
         * Creates a new dataset, replacing the field ids by the real ids from the assist structure.
         */
        Dataset asDataset(AssistStructure structure) {
            final Dataset.Builder builder = new Dataset.Builder(presentation);
            if (fields != null) {
                for (Map.Entry<String, AutoFillValue> entry : fields.entrySet()) {
                    final String resourceId = entry.getKey();
                    final ViewNode node = findNodeByResourceId(structure, resourceId);
                    assertWithMessage("Cannot find node:" + resourceId).that(node).isNotNull();
                    final AutoFillId id = node.getAutoFillId();
                    final AutoFillValue value = entry.getValue();
                    builder.setValue(id, value);
                }
            }
            builder.setAuthentication(authentication);
            return builder.build();
        }

        @Override
        public String toString() {
            return "CannedDataset: [hasPresentation=" + (presentation != null)
                    + ", hasAuthentication=" + (authentication != null)
                    + ", fields=" + fields + "]";
        }

        static class Builder {
            private final Map<String, AutoFillValue> mFields = new HashMap<>();
            private RemoteViews mPresentation;
            private IntentSender mAuthentication;

            /**
             * Sets the canned value of a field based on its {@code resourceId}.
             */
            public Builder setField(String resourceId, AutoFillValue value) {
                mFields.put(resourceId, value);
                return this;
            }

            /**
             * Sets the view to present the response in the UI.
             */
            public Builder setPresentation(RemoteViews presentation) {
                mPresentation = presentation;
                return this;
            }

            /**
             * Sets the authentication intent.
             */
            public Builder setAuthentication(IntentSender authentication) {
                mAuthentication = authentication;
                return this;
            }

            public CannedDataset build() {
                return new CannedDataset(this);
            }
        }
    }
}
