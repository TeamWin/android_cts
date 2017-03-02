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
import static android.autofillservice.cts.CheckoutActivity.ID_HOME_ADDRESS;
import static android.autofillservice.cts.CheckoutActivity.ID_SAVE_CC;
import static android.autofillservice.cts.CheckoutActivity.ID_WORK_ADDRESS;
import static android.autofillservice.cts.CheckoutActivity.INDEX_ADDRESS_WORK;
import static android.autofillservice.cts.CheckoutActivity.INDEX_CC_EXPIRATION_NEVER;
import static android.autofillservice.cts.CheckoutActivity.INDEX_CC_EXPIRATION_TODAY;
import static android.autofillservice.cts.CheckoutActivity.INDEX_CC_EXPIRATION_TOMORROW;
import static android.autofillservice.cts.Helper.assertListValue;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertToggleIsSanitized;
import static android.autofillservice.cts.Helper.assertToggleValue;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.support.test.rule.ActivityTestRule;
import android.view.autofill.AutoFillType;
import android.view.autofill.AutoFillValue;
import android.widget.Spinner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for an activity containing non-TextField views.
 */
public class CheckoutActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<CheckoutActivity> mActivityRule =
        new ActivityTestRule<CheckoutActivity>(CheckoutActivity.class);

    private CheckoutActivity mCheckoutActivity;

    @Before
    public void setActivity() {
        mCheckoutActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
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
                .setField(ID_CC_EXPIRATION, AutoFillValue.forList(INDEX_CC_EXPIRATION_NEVER))
                .setField(ID_ADDRESS, AutoFillValue.forList(1))
                .setField(ID_SAVE_CC, AutoFillValue.forToggle(true))
                .build());
        mCheckoutActivity.expectAutoFill("4815162342", INDEX_CC_EXPIRATION_NEVER, R.id.work_address,
                true);

        // Trigger auto-fill.
        mCheckoutActivity.onCcNumber((v) -> { v.requestFocus(); });
        waitUntilConnected();

        final FillRequest fillRequest = replier.getNextFillRequest();

        // Assert properties of Spinner field.
        final ViewNode ccExpirationNode =
                assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertThat(ccExpirationNode.getClassName()).isEqualTo(Spinner.class.getName());
        assertThat(ccExpirationNode.getAutoFillType()).isEqualTo(AutoFillType.forList());
        final String[] options = ccExpirationNode.getAutoFillOptions();
        assertWithMessage("ccExpirationNode.getAutoFillOptions()").that(options).isNotNull();
        assertWithMessage("Wrong auto-fill options for spinner").that(options).asList()
                .containsExactly(
                        getContext().getResources().getStringArray(R.array.cc_expiration_values));

        // Auto-fill it.
        sUiBot.selectDataset("ACME CC");

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

        // Dynamically change view contents
        mCheckoutActivity.onCcNumber((v) -> { v.setText("108"); });
        mCheckoutActivity.onCcExpiration((v) -> {
            v.setSelection(INDEX_CC_EXPIRATION_TOMORROW, true);
        });
        mCheckoutActivity.onHomeAddress((v) -> {
            v.setChecked(true);
        });
        mCheckoutActivity.onSaveCc((v) -> {
            v.setChecked(true);
        });

        // Trigger auto-fill.
        mCheckoutActivity.onCcNumber((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Assert sanitization on fill request: everything should be sanitized!
        final FillRequest fillRequest = replier.getNextFillRequest();

        assertTextIsSanitized(fillRequest.structure, ID_CC_NUMBER);
        assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertToggleIsSanitized(fillRequest.structure, ID_HOME_ADDRESS);
        assertToggleIsSanitized(fillRequest.structure, ID_SAVE_CC);

        // Trigger save.
        mCheckoutActivity.onCcNumber((v) -> { v.setText("4815162342"); });
        mCheckoutActivity.onCcExpiration((v) -> { v.setSelection(INDEX_CC_EXPIRATION_TODAY); });
        mCheckoutActivity.onAddress((v) -> { v.check(R.id.work_address); });
        mCheckoutActivity.onSaveCc((v) -> { v.setChecked(true); });
        mCheckoutActivity.tapBuy();
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()
        sUiBot.saveForAutofill(true);
        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert sanitization on save: everything should be available!
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_CC_NUMBER), "4815162342");
        assertListValue(findNodeByResourceId(saveRequest.structure, ID_CC_EXPIRATION),
                INDEX_CC_EXPIRATION_TODAY);
        assertListValue(findNodeByResourceId(saveRequest.structure, ID_ADDRESS),
                INDEX_ADDRESS_WORK);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_HOME_ADDRESS), false);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_WORK_ADDRESS), true);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_SAVE_CC), true);
    }
}
