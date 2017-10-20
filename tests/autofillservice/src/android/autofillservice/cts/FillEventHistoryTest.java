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

import static android.autofillservice.cts.CannedFillResponse.DO_NOT_REPLY_RESPONSE;
import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.CheckoutActivity.ID_CC_NUMBER;
import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.NULL_DATASET_ID;
import static android.autofillservice.cts.Helper.assertDeprecatedClientState;
import static android.autofillservice.cts.Helper.assertFillEventForAuthenticationSelected;
import static android.autofillservice.cts.Helper.assertFillEventForDatasetAuthenticationSelected;
import static android.autofillservice.cts.Helper.assertFillEventForDatasetSelected;
import static android.autofillservice.cts.Helper.assertFillEventForSaveShown;
import static android.autofillservice.cts.Helper.assertNoDeprecatedClientState;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.LoginActivity.getWelcomeMessage;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.service.autofill.FillEventHistory;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test that uses {@link LoginActivity} to test {@link FillEventHistory}.
 */
public class FillEventHistoryTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<LoginActivity> mActivityRule =
            new AutofillActivityTestRule<LoginActivity>(LoginActivity.class);

    private LoginActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void checkFillSelectionAfterSelectingDatasetAuthentication() throws Exception {
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
                        .build());

        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setId("name")
                        .setPresentation(createPresentation("authentication"))
                        .setAuthentication(authentication)
                        .build())
                .setExtras(clientState).build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Authenticate
        sUiBot.selectDataset("authentication");
        sReplier.getNextFillRequest();
        mActivity.assertAutoFilled();

        // Verify fill selection
        FillEventHistory selection =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        assertDeprecatedClientState(selection, "clientStateKey", "clientStateValue");
        assertThat(selection.getEvents().size()).isEqualTo(1);
        assertFillEventForDatasetAuthenticationSelected(selection.getEvents().get(0), "name",
                "clientStateKey", "clientStateValue");
    }

    @Test
    public void checkFillSelectionAfterSelectingAuthentication() throws Exception {
        enableService();

        // Set up FillResponse with response wide authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "username")
                                .setId("name")
                                .setPresentation(createPresentation("dataset"))
                                .build())
                        .setExtras(clientState).build());

        sReplier.addResponse(new CannedFillResponse.Builder().setExtras(clientState)
                .setPresentation(createPresentation("authentication"))
                .setAuthentication(authentication, ID_USERNAME)
                .build());

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Authenticate
        sUiBot.selectDataset("authentication");
        sReplier.getNextFillRequest();
        sUiBot.assertDatasets("dataset");

        // Verify fill selection
        FillEventHistory selection =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        assertDeprecatedClientState(selection, "clientStateKey", "clientStateValue");
        assertThat(selection.getEvents().size()).isEqualTo(1);
        assertFillEventForAuthenticationSelected(selection.getEvents().get(0), NULL_DATASET_ID,
                "clientStateKey", "clientStateValue");
    }

    @Test
    public void checkFillSelectionAfterSelectingTwoDatasets() throws Exception {
        enableService();

        // Set up first partition with an anonymous dataset
        Bundle clientState1 = new Bundle();
        clientState1.putCharSequence("clientStateKey", "Value1");

        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .setExtras(clientState1)
                .build());
        mActivity.expectAutoFill("username");

        // Trigger autofill on username
        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sUiBot.selectDataset("dataset1");
        sReplier.getNextFillRequest();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertDeprecatedClientState(selection, "clientStateKey", "Value1");
            assertThat(selection.getEvents().size()).isEqualTo(1);
            assertFillEventForDatasetSelected(selection.getEvents().get(0), NULL_DATASET_ID,
                    "clientStateKey", "Value1");
        }

        // Set up second partition with a named dataset
        Bundle clientState2 = new Bundle();
        clientState2.putCharSequence("clientStateKey", "Value2");

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password2")
                                .setPresentation(createPresentation("dataset2"))
                                .setId("name2")
                                .build())
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password3")
                                .setPresentation(createPresentation("dataset3"))
                                .setId("name3")
                                .build())
                .setExtras(clientState2)
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_PASSWORD).build());
        mActivity.expectPasswordAutoFill("password3");

        // Trigger autofill on password
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("dataset3");
        sReplier.getNextFillRequest();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertDeprecatedClientState(selection, "clientStateKey", "Value2");
            assertFillEventForDatasetSelected(selection.getEvents().get(0), "name3",
                    "clientStateKey", "Value2");
        }

        mActivity.onPassword((v) -> v.setText("new password"));
        mActivity.syncRunOnUiThread(() -> mActivity.finish());
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertDeprecatedClientState(selection, "clientStateKey", "Value2");

            assertThat(selection.getEvents().size()).isEqualTo(2);
            assertFillEventForDatasetSelected(selection.getEvents().get(0), "name3",
                    "clientStateKey", "Value2");
            assertFillEventForSaveShown(selection.getEvents().get(1), NULL_DATASET_ID,
                    "clientStateKey", "Value2");
        }
    }

    @Test
    public void checkFillSelectionIsResetAfterReturningNull() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertNoDeprecatedClientState(selection);
            assertThat(selection.getEvents().size()).isEqualTo(1);
            assertFillEventForDatasetSelected(selection.getEvents().get(0), NULL_DATASET_ID);
        }

        // Second request
        sReplier.addResponse(NO_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        sUiBot.assertNoDatasets();
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection).isNull();
        }
    }

    @Test
    public void checkFillSelectionIsResetAfterReturningError() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertNoDeprecatedClientState(selection);
            assertThat(selection.getEvents().size()).isEqualTo(1);
            assertFillEventForDatasetSelected(selection.getEvents().get(0), NULL_DATASET_ID);
        }

        // Second request
        sReplier.addResponse(new CannedFillResponse.Builder().returnFailure("D'OH!").build());
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        sUiBot.assertNoDatasets();
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection).isNull();
        }
    }

    @Test
    public void checkFillSelectionIsResetAfterTimeout() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertNoDeprecatedClientState(selection);
            assertThat(selection.getEvents().size()).isEqualTo(1);
            assertFillEventForDatasetSelected(selection.getEvents().get(0), NULL_DATASET_ID);
        }

        // Second request
        sReplier.addResponse(DO_NOT_REPLY_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection).isNull();
        }
    }

    private Bundle getBundle(String key, String value) {
        final Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    /**
     * Tests the following scenario:
     *
     * <ol>
     *    <li>Activity A is launched.
     *    <li>Activity A triggers autofill.
     *    <li>Activity B is launched.
     *    <li>Activity B triggers autofill.
     *    <li>User goes back to Activity A.
     *    <li>User triggers save on Activity A - at this point, service should have stats of
     *        activity B, and stats for activity A should have beeen discarded.
     * </ol>
     */
    @Test
    public void checkFillSelectionFromPreviousSessionIsDiscarded() throws Exception {
        enableService();

        // Launch activity A
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(getBundle("activity", "A"))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger autofill on activity A
        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();

        // Verify fill selection for Activity A
        FillEventHistory selectionA = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertDeprecatedClientState(selectionA, "activity", "A");
        assertThat(selectionA.getEvents()).isNull();

        // Launch activity B
        mContext.startActivity(new Intent(mContext, CheckoutActivity.class));

        // Trigger autofill on activity B
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(getBundle("activity", "B"))
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_CC_NUMBER, "4815162342")
                        .setPresentation(createPresentation("datasetB"))
                        .build())
                .build());
        sUiBot.focusByRelativeId(ID_CC_NUMBER);
        sReplier.getNextFillRequest();

        // Verify fill selection for Activity B
        final FillEventHistory selectionB = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertDeprecatedClientState(selectionB, "activity", "B");
        assertThat(selectionB.getEvents()).isNull();

        // Now switch back to A...
        sUiBot.pressBack(); // dismiss keyboard
        sUiBot.pressBack(); // dismiss task
        sUiBot.assertShownByRelativeId(ID_USERNAME);
        // ...and trigger save
        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        sReplier.getNextSaveRequest();

        // Finally, make sure history is right
        final FillEventHistory finalSelection = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertDeprecatedClientState(finalSelection, "activity", "B");
        assertThat(finalSelection.getEvents()).isNull();
    }
}
