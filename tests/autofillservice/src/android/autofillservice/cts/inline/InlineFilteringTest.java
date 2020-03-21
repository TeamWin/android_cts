/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.autofillservice.cts.inline;

import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.inline.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;

import android.autofillservice.cts.AbstractLoginActivityTestCase;
import android.autofillservice.cts.CannedFillResponse;
import android.autofillservice.cts.Helper;

import org.junit.Test;

/**
 * Tests for inline suggestion filtering. Tests for filtering datasets that need authentication are
 * in {@link InlineAuthenticationTest}.
 */
public class InlineFilteringTest extends AbstractLoginActivityTestCase {

    private static final String TAG = "InlineLoginActivityTest";

    @Override
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
    }

    @Test
    public void testFiltering_filtersByPrefix() throws Exception {
        enableService();

        // Set expectations.
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .setInlinePresentation(createInlinePresentation("The Dude"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "test")
                        .setField(ID_PASSWORD, "tweet")
                        .setPresentation(createPresentation("Second Dude"))
                        .setInlinePresentation(createInlinePresentation("Second Dude"))
                        .build());
        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("test", "tweet");

        // Trigger autofill, then make sure it's showing initially.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        mUiBot.assertSuggestionStrip(2);
        sReplier.getNextFillRequest();

        // Filter out one of the datasets.
        mActivity.onUsername((v) -> v.setText("t"));
        mUiBot.waitForIdleSync();
        mUiBot.assertSuggestionStrip(1);

        // Filter out both datasets.
        mActivity.onUsername((v) -> v.setText("ta"));
        mUiBot.waitForIdleSync();
        mUiBot.assertNoSuggestionStripEver();

        // Backspace to bring back one dataset.
        mActivity.onUsername((v) -> v.setText("t"));
        mUiBot.waitForIdleSync();
        mUiBot.assertSuggestionStrip(1);

        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdleSync();
        mActivity.assertAutoFilled();
    }
}
