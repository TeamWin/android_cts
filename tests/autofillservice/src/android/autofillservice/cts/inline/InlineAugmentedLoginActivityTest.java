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

import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.augmented.AugmentedHelper.assertBasicRequestInfo;

import android.autofillservice.cts.AutofillActivityTestRule;
import android.autofillservice.cts.augmented.AugmentedAutofillAutoActivityLaunchTestCase;
import android.autofillservice.cts.augmented.AugmentedLoginActivity;
import android.autofillservice.cts.augmented.CannedAugmentedFillResponse;
import android.autofillservice.cts.augmented.CtsAugmentedAutofillService.AugmentedFillRequest;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.EditText;

import org.junit.Test;

public class InlineAugmentedLoginActivityTest
        extends AugmentedAutofillAutoActivityLaunchTestCase<AugmentedLoginActivity> {

    protected AugmentedLoginActivity mActivity;

    @Override
    protected AutofillActivityTestRule<AugmentedLoginActivity> getActivityRule() {
        return new AutofillActivityTestRule<AugmentedLoginActivity>(
                AugmentedLoginActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    public void testAugmentedAutoFill_oneDatasetThenFilled() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final EditText password = mActivity.getPassword();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillId passwordId = password.getAutofillId();
        final AutofillValue usernameValue = username.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude", createInlinePresentation("dude"))
                        .setField(passwordId, "sweet", createInlinePresentation("sweet"))
                        .build())
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("req1")
                        .build(), usernameId)
                .build());

        // Trigger auto-fill
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        final AugmentedFillRequest request1 = sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request1, mActivity, usernameId, usernameValue);

        // Confirm one suggestion
        mUiBot.assertSuggestionStrip(1);

        mActivity.expectAutoFill("dude", "sweet");

        // Select suggestion
        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdle();

        mActivity.assertAutoFilled();
    }

    @Test
    public void testAugmentedAutoFill_twoDatasetThenFilledSecond() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final EditText password = mActivity.getPassword();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillId passwordId = password.getAutofillId();
        final AutofillValue usernameValue = username.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude", createInlinePresentation("dude"))
                        .setField(passwordId, "sweet", createInlinePresentation("sweet"))
                        .build())
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me2")
                        .setField(usernameId, "DUDE", createInlinePresentation("DUDE"))
                        .setField(passwordId, "SWEET", createInlinePresentation("SWEET"))
                        .build())
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("req1")
                        .build(), usernameId)
                .build());

        // Trigger auto-fill
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        final AugmentedFillRequest request1 = sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request1, mActivity, usernameId, usernameValue);

        // Confirm one suggestion
        mUiBot.assertSuggestionStrip(2);

        mActivity.expectAutoFill("DUDE", "SWEET");

        // Select suggestion
        mUiBot.selectSuggestion(1);
        mUiBot.waitForIdle();

        mActivity.assertAutoFilled();
    }

    @Test
    public void testAugmentedAutoFill_selectDatasetThenHideInlineSuggestion() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final EditText password = mActivity.getPassword();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillId passwordId = password.getAutofillId();
        final AutofillValue usernameValue = username.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude", createInlinePresentation("dude"))
                        .setField(passwordId, "sweet", createInlinePresentation("sweet"))
                        .build())
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("req1")
                        .build(), usernameId)
                .build());

        // Trigger auto-fill
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        final AugmentedFillRequest request1 = sAugmentedReplier.getNextFillRequest();

        mUiBot.assertSuggestionStrip(1);

        mActivity.expectAutoFill("dude", "sweet");

        mUiBot.selectSuggestion(0);
        mUiBot.waitForIdle();

        mUiBot.assertNoSuggestionStripEver();
    }
}
