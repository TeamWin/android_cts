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

import static android.app.Activity.RESULT_OK;
import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.UNUSED_AUTOFILL_VALUE;
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.Timeouts.MOCK_IME_TIMEOUT_MS;
import static android.autofillservice.cts.inline.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;

import static org.junit.Assume.assumeTrue;

import android.autofillservice.cts.AbstractLoginActivityTestCase;
import android.autofillservice.cts.AuthenticationActivity;
import android.autofillservice.cts.CannedFillResponse;
import android.autofillservice.cts.Helper;
import android.content.IntentSender;
import android.os.Process;

import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;

public class InlineAuthenticationTest extends AbstractLoginActivityTestCase {

    private static final String TAG = "InlineAuthenticationTest";

    @Override
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
    }

    @Test
    public void testDatasetAuthTwoFields() throws Exception {
        datasetAuthTwoFields(/* cancelFirstAttempt */ false);
    }

    private void datasetAuthTwoFields(boolean cancelFirstAttempt) throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .build());
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, UNUSED_AUTOFILL_VALUE)
                        .setField(ID_PASSWORD, UNUSED_AUTOFILL_VALUE)
                        .setPresentation(createPresentation("auth"))
                        .setInlinePresentation(createInlinePresentation("auth"))
                        .setAuthentication(authentication)
                        .build());
        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude", "sweet");

        final ImeEventStream stream = mockImeSession.openEventStream();
        mockImeSession.callRequestShowSelf(0);

        // Wait until the MockIme gets bound to the TestActivity.
        expectBindInput(stream, Process.myPid(), MOCK_IME_TIMEOUT_MS);

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mUiBot.assertSuggestionStrip(1);

        // Make sure UI is show on 2nd field as well
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdle();
        mUiBot.assertSuggestionStrip(1);

        // Now tap on 1st field to show it again...
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        mUiBot.assertSuggestionStrip(1);

        // TODO(b/149891961): add logic for cancelFirstAttempt

        // ...and select it this time
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdle();

        // Check the results.
        mActivity.assertAutoFilled();
    }
}
