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
package android.autofillservice.cts.client;

import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.autofillservice.cts.commontests.ClientSuggestionsCommonTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.ClientAutofillRequestCallback;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import com.android.cts.mockime.MockImeSession;

import org.junit.Test;

/**
 * Tests client suggestions behaviors for the inline mode.
 */
public class ClientSuggestionsInlineTest extends ClientSuggestionsCommonTestCase {

    public ClientSuggestionsInlineTest() {
        super(getInlineUiBot());
    }

    @Override
    protected boolean isInlineMode() {
        return true;
    }

    // TODO(b/230853028) do not use sticky broadcasts, so that this test can run in instant mode
    @Test
    @AppModeFull(reason = "BROADCAST_STICKY permission cannot be granted to instant apps")
    public void testImeDisableClientSuggestions_showDropdownUi() throws Exception {
        // Set service.
        enableService();
        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        // Disable inline suggestions for the client.
        final Bundle bundle = new Bundle();
        bundle.putBoolean("ClientSuggestions", false);
        mockImeSession.callSetInlineSuggestionsExtras(bundle);

        // Set expectations.
        mClientReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // Check that no inline requests are sent to the client.
        final ClientAutofillRequestCallback.FillRequest clientRequest =
                mClientReplier.getNextFillRequest();
        assertThat(clientRequest.inlineRequest).isNull();

        // Check dropdown UI shown.
        getDropdownUiBot().assertDatasets("The Dude");
    }

    // TODO(b/230853028) do not use sticky broadcasts, so that this test can run in instant mode
    @Test
    @AppModeFull(reason = "BROADCAST_STICKY permission cannot be granted to instant apps")
    public void testImeDisableClientSuggestions_fallbackThenShowInline() throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        // Disable inline suggestions for the client.
        final Bundle bundle = new Bundle();
        bundle.putBoolean("ClientSuggestions", false);
        mockImeSession.callSetInlineSuggestionsExtras(bundle);

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        // Check that no inline requests are sent to the client.
        final ClientAutofillRequestCallback.FillRequest clientRequest =
                mClientReplier.getNextFillRequest();
        assertThat(clientRequest.inlineRequest).isNull();

        // Check that the inline request is sent to the service.
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.inlineRequest).isNotNull();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    // TODO(b/230853028) do not use sticky broadcasts, so that this test can run in instant mode
    @Test
    @AppModeFull(reason = "BROADCAST_STICKY permission cannot be granted to instant apps")
    public void testImeDisableServiceSuggestions_fallbackThenShowDropdownUi() throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        // Disable inline suggestions for the default service.
        final Bundle bundle = new Bundle();
        bundle.putBoolean("ServiceSuggestions", false);
        mockImeSession.callSetInlineSuggestionsExtras(bundle);

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        // Check that the inline request is sent to the client.
        final ClientAutofillRequestCallback.FillRequest clientRequest =
                mClientReplier.getNextFillRequest();
        assertThat(clientRequest.inlineRequest).isNotNull();

        // Check that no inline requests are sent to the service.
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.inlineRequest).isNull();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset on the dropdown UI.
        getDropdownUiBot().selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }
}
