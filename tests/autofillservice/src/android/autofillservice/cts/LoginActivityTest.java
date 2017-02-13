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

import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.LoginActivity.ID_PASSWORD;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillReplier;
import android.autofillservice.cts.InstrumentedAutoFillService.Request;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.view.autofill.AutoFillValue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class LoginActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<LoginActivity> mActivityRule =
        new ActivityTestRule<LoginActivity>(LoginActivity.class);

    private LoginActivity mLoginActivity;

    @Before
    public void setActivity() {
        mLoginActivity = mActivityRule.getActivity();
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        // Set service.
        enableService();
        final FillReplier fillReplier = new FillReplier();
        InstrumentedAutoFillService.setFillReplier(fillReplier);

        // Set expectation.
        fillReplier.addResponse(new CannedDataset.Builder("4815162342", "The Dude")
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet")).build());

        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Sanity check: make sure input was sanitized.
        final Request request = fillReplier.getNextRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);
    }

    @Test
    public void testAutoFillOneDatasetAndMoveFocusAround() throws Exception {
        // Set service.
        enableService();
        final FillReplier fillReplier = new FillReplier();
        InstrumentedAutoFillService.setFillReplier(fillReplier);

        // Set expectation.
        fillReplier.addResponse(new CannedDataset.Builder("4815162342", "The Dude")
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet")).build());

        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });

        // Make sure tapping on other fields from the dataset does not trigger it again
        mLoginActivity.onPassword((v) -> { v.requestFocus(); });
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mLoginActivity.onPassword((v) -> { v.requestFocus(); });
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });

        // Sanity check: make sure service was called just once.
        fillReplier.assertNumberFillRequests(1);
    }

    /*
     * TODO(b/33197203, b/33802548): test other scenarios
     *
     *  - no dataset
     *  - multiple datasets
     *  - partitioned datasets (i.e., multiple fields)
     *  - response-level authentication (custom and fingerprint)
     *  - dataset-level authentication (custom and fingerprint)
     *
     *  Save:
     *  - when no dataset is returned initially
     *  - when a dataset is returned initially
     *  - make sure password is set
     *  - test cases where non-savable-ids only are changed
     *
     *  Other assertions:
     *  - illegal state thrown on callback calls
     *  - system server state after calls (for example, no pending callback)
     */
}
