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
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.findAutofillIdByResourceId;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.inline.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.autofillservice.cts.CannedFillResponse;
import android.autofillservice.cts.DummyActivity;
import android.autofillservice.cts.Helper;
import android.autofillservice.cts.InstrumentedAutoFillService;
import android.autofillservice.cts.LoginActivityCommonTestCase;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.service.autofill.FillContext;

import com.android.cts.mockime.MockImeSession;

import org.junit.Test;

public class InlineLoginActivityTest extends LoginActivityCommonTestCase {

    private static final String TAG = "InlineLoginActivityTest";

    @Override
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
    }

    public InlineLoginActivityTest() {
        super(getInlineUiBot());
    }

    @Override
    protected boolean isInlineMode() {
        return true;
    }

    @Test
    public void testAutofill_disjointDatasets() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "lollipop")
                        .setPresentation(createPresentation("The Password2"))
                        .setInlinePresentation(createInlinePresentation("The Password2"))
                        .build());

        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Username");

        // Switch focus to password
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Password", "The Password2");

        // Switch focus back to username
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Username");
        mUiBot.selectDataset("The Username");
        mUiBot.waitForIdleSync();

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure input was sanitized.
        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);
        final FillContext fillContext = request.contexts.get(request.contexts.size() - 1);
        assertThat(fillContext.getFocusedId())
                .isEqualTo(findAutofillIdByResourceId(fillContext, ID_USERNAME));

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();
    }

    @Test
    public void testAutofill_selectDatasetThenHideInlineSuggestion() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build());

        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Username");

        mUiBot.selectDataset("The Username");
        mUiBot.waitForIdleSync();

        mUiBot.assertNoDatasets();

        // Make sure input was sanitized.
        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);
        final FillContext fillContext = request.contexts.get(request.contexts.size() - 1);
        assertThat(fillContext.getFocusedId())
                .isEqualTo(findAutofillIdByResourceId(fillContext, ID_USERNAME));

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();
    }

    @Test
    public void testLongClickAttribution() throws Exception {
        // Set service.
        enableService();

        Intent intent = new Intent(mContext, DummyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(
                                createInlinePresentation("The Username", pendingIntent))
                        .build());

        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Username");

        // Long click on suggestion
        mUiBot.longPressSuggestion("The Username");
        mUiBot.waitForIdleSync();

        // Make sure the attribution showed worked
        mUiBot.selectByText("foo");

        // Go back to the filled app.
        mUiBot.pressBack();

        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();
    }

    @Test
    public void testAutofill_noInvalid() throws Exception {
        final String keyInvalid = "invalid";
        final String keyValid = "valid";
        final String message = "Passes valid message to the remote service";
        final Bundle bundle = new Bundle();
        bundle.putBinder(keyInvalid, new Binder());
        bundle.putString(keyValid, message);

        // Set service.
        enableService();
        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        mockImeSession.callSetInlineSuggestionsExtras(bundle);

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build());

        sReplier.addResponse(builder.build());

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Username");

        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        final Bundle extras = request.inlineRequest.getExtras();
        assertThat(extras.get(keyInvalid)).isNull();
        assertThat(extras.getString(keyValid)).isEqualTo(message);

        final Bundle style = request.inlineRequest.getInlinePresentationSpecs().get(0).getStyle();
        assertThat(style.get(keyInvalid)).isNull();
        assertThat(style.getString(keyValid)).isEqualTo(message);

        final Bundle style2 = request.inlineRequest.getInlinePresentationSpecs().get(1).getStyle();
        assertThat(style2.get(keyInvalid)).isNull();
        assertThat(style2.getString(keyValid)).isEqualTo(message);
    }
}
