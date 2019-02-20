/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.autofillservice.cts.augmented;

import static android.autofillservice.cts.augmented.AugmentedHelper.assertBasicRequestInfo;
import static android.autofillservice.cts.augmented.CannedAugmentedFillResponse.NO_AUGMENTED_RESPONSE;

import android.autofillservice.cts.AutofillActivityTestRule;
import android.autofillservice.cts.LoginNotImportantForAutofillActivity;
import android.autofillservice.cts.augmented.CtsAugmentedAutofillService.AugmentedFillRequest;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiObject2;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.EditText;

import org.junit.Test;

@AppModeFull(reason = "AugmentedLoginActivityTest is enough")
public class AugmentedLoginNotImportantForAutofillActivityTest
        extends AugmentedAutofillAutoActivityLaunchTestCase<LoginNotImportantForAutofillActivity> {

    protected LoginNotImportantForAutofillActivity mActivity;

    @Override
    protected AutofillActivityTestRule<LoginNotImportantForAutofillActivity> getActivityRule() {
        return new AutofillActivityTestRule<LoginNotImportantForAutofillActivity>(
                LoginNotImportantForAutofillActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    public void testAutofill_none() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillValue expectedFocusedValue = username.getAutofillValue();
        final AutofillId expectedFocusedId = username.getAutofillId();
        sAugmentedReplier.addResponse(NO_AUGMENTED_RESPONSE);

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        final AugmentedFillRequest request = sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request, mActivity, expectedFocusedId, expectedFocusedValue);

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is not shown.
        mAugmentedUiBot.assertUiNeverShown();
    }

    @Test
    public void testAutofill_oneField() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillValue expectedFocusedValue = username.getAutofillValue();
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude")
                        .build(), usernameId)
                .build());
        mActivity.expectAutoFill("dude");

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        final AugmentedFillRequest request = sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request, mActivity, usernameId, expectedFocusedValue);

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is shown.
        final UiObject2 ui = mAugmentedUiBot.assertUiShown(usernameId, "Augment Me");

        // Autofill
        ui.click();
        mActivity.assertAutoFilled();
        mAugmentedUiBot.assertUiGone();
    }

    @Test
    public void testAutofill_twoFields() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillValue expectedFocusedValue = username.getAutofillValue();
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude")
                        .setField(mActivity.getPassword().getAutofillId(), "sweet")
                        .build(), usernameId)
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        final AugmentedFillRequest request = sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request, mActivity, usernameId, expectedFocusedValue);

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is shown.
        final UiObject2 ui = mAugmentedUiBot.assertUiShown(usernameId, "Augment Me");

        // Autofill
        ui.click();
        mActivity.assertAutoFilled();
        mAugmentedUiBot.assertUiGone();
    }
}
