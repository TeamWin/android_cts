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

import static android.autofillservice.cts.CannedFillResponse.DO_NOT_REPLY_RESPONSE;
import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.NULL_DATASET_ID;
import static android.autofillservice.cts.Helper.assertFillEventForDatasetAuthenticationSelected;
import static android.autofillservice.cts.Helper.assertFillEventForDatasetSelected;
import static android.autofillservice.cts.Helper.assertFillEventForDatasetShown;
import static android.autofillservice.cts.Helper.assertFillEventForSaveShown;
import static android.autofillservice.cts.Helper.assertNoDeprecatedClientState;
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.inline.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import android.autofillservice.cts.AbstractLoginActivityTestCase;
import android.autofillservice.cts.AuthenticationActivity;
import android.autofillservice.cts.CannedFillResponse;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.Helper;
import android.autofillservice.cts.InstrumentedAutoFillService;
import android.autofillservice.cts.LoginActivity;
import android.content.IntentSender;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.service.autofill.FillEventHistory;
import android.service.autofill.FillEventHistory.Event;
import android.support.test.uiautomator.UiObject2;
import android.view.View;

import org.junit.Test;

import java.util.List;

/**
 * Test that uses {@link LoginActivity} to test {@link FillEventHistory}.
 */
@AppModeFull(reason = "Service-specific test")
public class InlineFillEventHistoryTest extends AbstractLoginActivityTestCase {

    @Override
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
    }

    @Test
    public void testNoDatasetAndSave() throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_USERNAME)
                .build());

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        sReplier.getNextFillRequest();

        // Suggestion strip was never shown.
        mUiBot.assertNoSuggestionStripEver();

        // Change username
        mActivity.syncRunOnUiThread(() ->  mActivity.onUsername((v) -> v.setText("ID")));
        mUiBot.waitForIdle();

        // Trigger save UI.
        mActivity.tapSave();
        mUiBot.waitForIdle();

        // Confirm the save UI shown
        final UiObject2 saveUi = mUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Save it...
        mUiBot.saveForAutofill(saveUi, true);
        mUiBot.waitForIdle();
        sReplier.getNextSaveRequest();

        // Verify save event
        final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(1);
        assertNoDeprecatedClientState(selection);
        final List<Event> events = selection.getEvents();
        assertFillEventForSaveShown(events.get(0), NULL_DATASET_ID);
    }

    @Test
    public void testOneDatasetAndSave() throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_USERNAME)
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "id")
                        .setField(ID_PASSWORD, "pass")
                        .setPresentation(createPresentation("Dataset"))
                        .setInlinePresentation(createInlinePresentation("Dataset"))
                        .build())
                .build());

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();

        // Suggestion strip was shown.
        mUiBot.assertSuggestionStrip(1);
        mUiBot.waitForIdle();

        mUiBot.selectSuggestion(0);

        // Change username and password
        mActivity.syncRunOnUiThread(() ->  mActivity.onUsername((v) -> v.setText("ID")));
        mActivity.syncRunOnUiThread(() ->  mActivity.onPassword((v) -> v.setText("PASS")));
        mUiBot.waitForIdle();

        // Trigger save UI.
        mActivity.tapSave();
        mUiBot.waitForIdle();

        // Confirm the save UI shown
        final UiObject2 saveUi = mUiBot.assertUpdateShowing(SAVE_DATA_TYPE_GENERIC);

        // Save it...
        mUiBot.saveForAutofill(saveUi, true);
        mUiBot.waitForIdle();
        sReplier.getNextSaveRequest();

        // Verify events history
        final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(4);
        assertNoDeprecatedClientState(selection);
        final List<Event> events = selection.getEvents();
        assertFillEventForDatasetShown(events.get(0));
        assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID);
        assertFillEventForDatasetShown(events.get(0));
        assertFillEventForSaveShown(events.get(3), NULL_DATASET_ID);
    }

    @Test
    public void testDatasetAuthenticationSelected() throws Exception {
        enableService();

        // Set up FillResponse with dataset authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dataset"))
                        .setInlinePresentation(createInlinePresentation("Dataset"))
                        .build());

        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setId("name")
                        .setPresentation(createPresentation("authentication"))
                        .setInlinePresentation(createInlinePresentation("authentication"))
                        .setAuthentication(authentication)
                        .build())
                .setExtras(clientState).build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        // ...
        sReplier.getNextFillRequest();
        mUiBot.assertSuggestionStrip(1);

        // Authenticate
        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        // Verify fill selection
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(2);
        assertFillEventForDatasetShown(events.get(0), "clientStateKey", "clientStateValue");
        assertFillEventForDatasetAuthenticationSelected(events.get(1), "name",
                "clientStateKey", "clientStateValue");
    }

    @Test
    public void testNoEvents_whenServiceReturnsNullResponse() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .setInlinePresentation(createInlinePresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
            assertNoDeprecatedClientState(selection);
            final List<Event> events = selection.getEvents();
            assertFillEventForDatasetShown(events.get(0));
            assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID);
        }

        // Second request
        sReplier.addResponse(NO_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        mUiBot.assertNoSuggestionStripEver();
        waitUntilDisconnected();

        InstrumentedAutoFillService.assertNoFillEventHistory();
    }

    @Test
    public void testNoEvents_whenServiceReturnsFailure() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .setInlinePresentation(createInlinePresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
            assertNoDeprecatedClientState(selection);
            final List<Event> events = selection.getEvents();
            assertFillEventForDatasetShown(events.get(0));
            assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID);
        }

        // Second request
        sReplier.addResponse(new CannedFillResponse.Builder().returnFailure("D'OH!").build());
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        mUiBot.assertNoSuggestionStripEver();
        waitUntilDisconnected();

        InstrumentedAutoFillService.assertNoFillEventHistory();
    }

    @Test
    public void testNoEvents_whenServiceTimesout() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .setInlinePresentation(createInlinePresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
            assertNoDeprecatedClientState(selection);
            final List<Event> events = selection.getEvents();
            assertFillEventForDatasetShown(events.get(0));
            assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID);
        }

        // Second request
        sReplier.addResponse(DO_NOT_REPLY_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        waitUntilDisconnected();

        InstrumentedAutoFillService.assertNoFillEventHistory();
    }
}
