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

import android.app.assist.AssistStructure;
import android.view.autofill.AutoFillValue;
import android.view.autofill.Dataset;
import android.view.autofill.FillResponse;

import java.util.ArrayList;
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

    private CannedFillResponse(Builder builder) {
        datasets = builder.mDatasets;
    }

    @Override
    public String toString() {
        return "CannedFillResponse: [datasets=" + datasets + "]";
    }

    static class Builder {
        private final List<CannedDataset> mDatasets = new ArrayList<>();

        public Builder addDataset(CannedDataset dataset) {
            mDatasets.add(dataset);
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
        final String id;
        final String name;

        private CannedDataset(Builder builder) {
            fields = builder.mFields;
            id = builder.mId;
            name = builder.mName;
        }

        @Override
        public String toString() {
            return "CannedDataset: [id=" + id + ", name=" + name + ", fields=" + fields + "]";
        }

        static class Builder {
            private final Map<String, AutoFillValue> mFields = new HashMap<>();
            private final String mId;
            private final String mName;

            public Builder(String id, String name) {
                mId = id;
                mName = name;
            }

            /**
             * Sets the canned value of a field based on its {@code resourceId}.
             */
            public Builder setField(String resourceId, AutoFillValue value) {
                mFields.put(resourceId, value);
                return this;
            }

            public CannedDataset build() {
                return new CannedDataset(this);
            }
        }
    }
}
