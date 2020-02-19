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
import static android.autofillservice.cts.Timeouts.MOCK_IME_TIMEOUT_MS;
import static android.autofillservice.cts.inline.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.autofillservice.cts.AbstractLoginActivityTestCase;
import android.autofillservice.cts.CannedFillResponse;
import android.autofillservice.cts.Helper;
import android.autofillservice.cts.InstrumentedAutoFillService;
import android.os.Process;
import android.service.autofill.FillContext;
import android.support.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.RetryableException;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;

import java.util.concurrent.TimeoutException;

public class InlineLoginActivityTest extends AbstractLoginActivityTestCase {

    private static final String TAG = "InlineLoginActivityTest";

    @Override
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
    }

    @Test
    public void testAutofill_oneDataset() throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        // Set expectations.
        String expectedHeader = null, expectedFooter = null;

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .setInlinePresentation(createInlinePresentation("The Dude"))
                        .build());

        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude", "sweet");

        // Dynamically set password to make sure it's sanitized.
        mActivity.onPassword((v) -> v.setText("I AM GROOT"));

        final ImeEventStream stream = mockImeSession.openEventStream();
        mockImeSession.callRequestShowSelf(0);

        // Wait until the MockIme gets bound to the TestActivity.
        expectBindInput(stream, Process.myPid(), MOCK_IME_TIMEOUT_MS);

        // Trigger auto-fill.
        requestFocusOnUsername();
        expectEvent(stream, editorMatcher("onStartInput", mActivity.getUsername().getId()),
                MOCK_IME_TIMEOUT_MS);

        //TODO: extServices bug cause test to fail first time, retry if suggestion strip missing.
        try {
            expectEvent(stream, event -> "onSuggestionViewUpdated".equals(event.getEventName()),
                    MOCK_IME_TIMEOUT_MS);
        } catch (TimeoutException e) {
            sReplier.getNextFillRequest();
            throw new RetryableException("Retry inline test");
        }

        final UiObject2 suggestionStrip = mUiBot.assertSuggestionStrip(1);
        mUiBot.selectSuggestion(0);

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.

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
    public void testAutofill_twoDatasets() throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        // Set expectations.
        String expectedHeader = null, expectedFooter = null;

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

        // Dynamically set password to make sure it's sanitized.
        mActivity.onPassword((v) -> v.setText("I AM GROOT"));

        final ImeEventStream stream = mockImeSession.openEventStream();
        mockImeSession.callRequestShowSelf(0);

        // Wait until the MockIme gets bound to the TestActivity.
        expectBindInput(stream, Process.myPid(), MOCK_IME_TIMEOUT_MS);

        // Trigger auto-fill.
        requestFocusOnUsername();
        expectEvent(stream, editorMatcher("onStartInput", mActivity.getUsername().getId()),
                MOCK_IME_TIMEOUT_MS);

        //TODO: extServices bug cause test to fail first time, retry if suggestion strip missing.
        try {
            expectEvent(stream, event -> "onSuggestionViewUpdated".equals(event.getEventName()),
                    MOCK_IME_TIMEOUT_MS);
        } catch (TimeoutException e) {
            sReplier.getNextFillRequest();
            throw new RetryableException("Retry inline test");
        }

        mUiBot.assertSuggestionStrip(2);
        mUiBot.selectSuggestion(1);

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.

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
}
