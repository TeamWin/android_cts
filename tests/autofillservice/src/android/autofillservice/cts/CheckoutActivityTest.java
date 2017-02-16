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

import static android.autofillservice.cts.CheckoutActivity.ID_ADDRESS;
import static android.autofillservice.cts.CheckoutActivity.ID_CC_EXPIRATION;
import static android.autofillservice.cts.CheckoutActivity.ID_CC_NUMBER;
import static android.autofillservice.cts.CheckoutActivity.ID_SAVE_CC;
import static android.autofillservice.cts.Helper.assertListValue;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertToggleValue;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.view.autofill.AutoFillValue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for an activity containing non-TextField views.
 *
 * TODO(b/33197203, b/33802548): use spinner for credit card expiration once supported
 *
 */
@SmallTest
public class CheckoutActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<CheckoutActivity> mActivityRule =
        new ActivityTestRule<CheckoutActivity>(CheckoutActivity.class);

    private CheckoutActivity mCheckoutActivity;

    @Before
    public void setActivity() {
        mCheckoutActivity = mActivityRule.getActivity();
    }

    @Test
    public void testAutoFill() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedDataset.Builder()
                .setPresentation(createPresentation("ACME CC"))
                .setField(ID_CC_NUMBER, AutoFillValue.forText("4815162342"))
                .setField(ID_CC_EXPIRATION, AutoFillValue.forText("never"))
                .setField(ID_ADDRESS, AutoFillValue.forList(1))
                .setField(ID_SAVE_CC, AutoFillValue.forToggle(true))
                .build());
        mCheckoutActivity.expectAutoFill("4815162342", "never", R.id.work_address, true);

        // TODO(b/33197203, b/33802548): once it users Spinner, change spinner value statically
        // and assert it was properly sanitized.

        // Trigger auto-fill.
        mCheckoutActivity.onCcNumber((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectByText("ACME CC");

        // Check the results.
        mCheckoutActivity.assertAutoFilled();

        // Sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testSanitization() throws Exception {
        enableService();

        // Set service.
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedFillResponse.Builder()
                .setSavableIds(ID_CC_NUMBER, ID_CC_EXPIRATION, ID_ADDRESS, ID_SAVE_CC)
                .build());

        // Change view contents.
        mCheckoutActivity.onCcNumber((v) -> { v.setText("108"); });
        mCheckoutActivity.onCcExpiration((v) -> { v.setText("NEVER"); });

        // Trigger auto-fill.
        mCheckoutActivity.onCcNumber((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Assert sanitization on fill request:
        final FillRequest fillRequest = replier.getNextFillRequest();

        assertTextIsSanitized(fillRequest.structure, ID_CC_NUMBER);
        assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);

        // Trigger save
        mCheckoutActivity.onCcNumber((v) -> { v.setText("4815162342"); });
        mCheckoutActivity.onCcExpiration((v) -> { v.setText("4EVER"); });
        mCheckoutActivity.onAddress((v) -> { v.check(R.id.work_address); });
        mCheckoutActivity.onSaveCc((v) -> { v.setChecked(true); });
        mCheckoutActivity.tapBuy();
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()
        sUiBot.saveForAutofill(true);
        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert sanitization on save: everything should be available!
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_CC_NUMBER), "4815162342");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_CC_EXPIRATION), "4EVER");
        assertListValue(findNodeByResourceId(saveRequest.structure, ID_ADDRESS), R.id.work_address);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_SAVE_CC), true);
    }
}
