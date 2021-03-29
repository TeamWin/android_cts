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
package android.autofillservice.cts.commontests;

import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.ID_EMPTY;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;

import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.ClientSuggestionsActivity;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.ClientAutofillRequestCallback;
import android.autofillservice.cts.testcore.OneTimeTextWatcher;
import android.autofillservice.cts.testcore.UiBot;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.widget.EditText;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * This is the test case covering most scenarios - other test cases will cover characteristics
 * specific to that test's activity (for example, custom views).
 */
public abstract class ClientSuggestionsCommonTestCase
        extends AutoFillServiceTestCase.AutoActivityLaunch<ClientSuggestionsActivity> {

    private static final String TAG = "ClientSuggestions";
    protected ClientSuggestionsActivity mActivity;
    protected ClientAutofillRequestCallback.Replier mClientReplier;

    protected ClientSuggestionsCommonTestCase() {}

    protected ClientSuggestionsCommonTestCase(UiBot inlineUiBot) {
        super(inlineUiBot);
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mClientReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        sReplier.assertOnFillRequestNotCalled();
        mClientReplier.assertReceivedRequest();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutoFillNoDatasets_fallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertReceivedRequest();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @AppModeFull(reason = "testAutoFillNoDatasets_fallbackDefaultService() is enough")
    public void testManualRequestAfterFallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertReceivedRequest();

        // The dataset shown.
        mUiBot.assertDatasets("The Dude");

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "DUDE")
                .setField(ID_PASSWORD, "SWEET")
                .setPresentation("THE DUDE", isInlineMode())
                .build());

        // Trigger autofill.
        mActivity.forceAutofillOnUsername();
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertNoUnhandledFillRequests();

        mActivity.expectAutoFill("DUDE", "SWEET");

        // Select the dataset.
        mUiBot.selectDataset("THE DUDE");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @AppModeFull(reason = "testAutoFillNoDatasets_fallbackDefaultService() is enough")
    public void testNewFieldAddedAfterFallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertReceivedRequest();

        // The dataset shown.
        mUiBot.assertDatasets("The Dude");

        // Try again, in a field that was added after the first request
        final EditText child = new EditText(mActivity);
        child.setId(R.id.empty);
        mActivity.addChild(child, ID_EMPTY);
        final OneTimeTextWatcher watcher = new OneTimeTextWatcher("child", child,
                "new view on the block");
        child.addTextChangedListener(watcher);
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setField(ID_EMPTY, "new view on the block")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mActivity.syncRunOnUiThread(() -> child.requestFocus());
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertNoUnhandledFillRequests();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");
        mUiBot.waitForIdle();

        // Check the results.
        mActivity.assertAutoFilled();
        watcher.assertAutoFilled();
    }

    @Test
    public void testNoDatasetsAfterFallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);
        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        mClientReplier.assertReceivedRequest();
        sReplier.getNextFillRequest();

        // Make sure UI is not shown.
        mUiBot.assertNoDatasetsEver();
    }

    @Test
    @AppModeFull(reason = "testAutoFillOneDataset() is enough")
    public void testAutoFillNoDatasets() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        setEmptyClientResponse();

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);

        mClientReplier.assertReceivedRequest();

        // Make sure UI is not shown.
        mUiBot.assertNoDatasetsEver();
    }

    @Test
    @AppModeFull(reason = "testAutoFillOneDataset() is enough")
    public void testNewFieldAddedAfterFirstRequest() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        setEmptyClientResponse();

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mClientReplier.assertReceivedRequest();

        // Make sure UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Try again, in a field that was added after the first request
        final EditText child = new EditText(mActivity);
        child.setId(R.id.empty);
        mActivity.addChild(child, ID_EMPTY);
        final OneTimeTextWatcher watcher = new OneTimeTextWatcher("child", child,
                "new view on the block");
        child.addTextChangedListener(watcher);
        mClientReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setField(ID_EMPTY, "new view on the block")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mActivity.syncRunOnUiThread(() -> child.requestFocus());

        mClientReplier.assertReceivedRequest();
        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");

        // Check the results.
        // Check username and password fields
        mActivity.assertAutoFilled();
        // Check the new added field
        watcher.assertAutoFilled();
    }

    @NonNull
    @Override
    protected AutofillActivityTestRule<ClientSuggestionsActivity> getActivityRule() {
        return new AutofillActivityTestRule<ClientSuggestionsActivity>(
                ClientSuggestionsActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
                mClientReplier = mActivity.getReplier();
            }
        };
    }

    private void setEmptyClientResponse() {
        mClientReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(new Bundle())
                .build());
    }
}
