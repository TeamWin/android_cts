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

import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.augmented.AugmentedHelper.assertBasicRequestInfo;
import static android.autofillservice.cts.augmented.AugmentedTimeouts.AUGMENTED_FILL_TIMEOUT;
import static android.autofillservice.cts.augmented.CannedAugmentedFillResponse.DO_NOT_REPLY_AUGMENTED_RESPONSE;
import static android.autofillservice.cts.augmented.CannedAugmentedFillResponse.NO_AUGMENTED_RESPONSE;

import static org.testng.Assert.assertThrows;

import android.autofillservice.cts.AutofillActivityTestRule;
import android.autofillservice.cts.LoginActivity;
import android.autofillservice.cts.OneTimeCancellationSignalListener;
import android.autofillservice.cts.augmented.CtsAugmentedAutofillService.AugmentedFillRequest;
import android.content.ComponentName;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiObject2;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.EditText;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Set;

public class AugmentedLoginActivityTest
        extends AugmentedAutofillAutoActivityLaunchTestCase<LoginActivity> {

    protected LoginActivity mActivity;

    @Override
    protected AutofillActivityTestRule<LoginActivity> getActivityRule() {
        return new AutofillActivityTestRule<LoginActivity>(
                LoginActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    public void testServiceLifecycle() throws Exception {
        enableService();
        CtsAugmentedAutofillService augmentedService = enableAugmentedService();

        AugmentedHelper.resetAugmentedService();
        augmentedService.waitUntilDisconnected();
    }

    @Test
    @AppModeFull(reason = "testAutoFill_mainServiceReturnedNull_augmentedAutofillOneField enough")
    public void testAutoFill_neitherServiceCanAutofill() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillValue expectedFocusedValue = username.getAutofillValue();
        final AutofillId expectedFocusedId = username.getAutofillId();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(NO_AUGMENTED_RESPONSE);

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        final AugmentedFillRequest request = sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request, mActivity, expectedFocusedId, expectedFocusedValue);

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is not shown.
        mAugmentedUiBot.assertUiNeverShown();
    }

    @Test
    public void testAutoFill_mainServiceReturnedNull_augmentedAutofillOneField() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillValue expectedFocusedValue = username.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude")
                        .build(), usernameId)
                .build());
        mActivity.expectAutoFill("dude");

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
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
    @AppModeFull(reason = "testAutoFill_mainServiceReturnedNull_augmentedAutofillOneField enough")
    public void testAutoFill_mainServiceReturnedNull_augmentedAutofillTwoFields() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillValue expectedFocusedValue = username.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude")
                        .setField(mActivity.getPassword().getAutofillId(), "sweet")
                        .build(), usernameId)
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
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

    @Ignore("blocked on b/122728762")
    @Test
    @AppModeFull(reason = "testAutoFill_mainServiceReturnedNull_augmentedAutofillOneField enough")
    public void testCancellationSignalCalledAfterTimeout() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(DO_NOT_REPLY_AUGMENTED_RESPONSE);
        final OneTimeCancellationSignalListener listener =
                new OneTimeCancellationSignalListener(AUGMENTED_FILL_TIMEOUT.ms() + 2000);

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // TODO(b/124456706): might need to wait until connected
        sAugmentedReplier.getNextFillRequest().cancellationSignal.setOnCancelListener(listener);

        // Assert results
        listener.assertOnCancelCalled();
    }

    @Test
    public void testSetAugmentedAutofillWhitelist_noStandardServiceSet() throws Exception {
        final AutofillManager mgr = mActivity.getAutofillManager();
        assertThrows(SecurityException.class,
                () -> mgr.setAugmentedAutofillWhitelist((Set<String>) null,
                        (Set<ComponentName>) null));
    }

    @Test
    public void testSetAugmentedAutofillWhitelist_notAugmentedService() throws Exception {
        enableService();
        final AutofillManager mgr = mActivity.getAutofillManager();
        assertThrows(SecurityException.class,
                () -> mgr.setAugmentedAutofillWhitelist((Set<String>) null,
                        (Set<ComponentName>) null));
    }

    /*
     * TODO(b/123542344) - add moarrrr tests:
     *
     * - Augmented service returned null
     * - Focus back and forth between username and passwod
     *   - When Augmented service shows UI on one field (like username) but not other.
     *   - When Augmented service shows UI on one field (like username) on both.
     * - Tap back
     * - Tap home (then bring activity back)
     * - Acitivy is killed (and restored)
     * - Main service returns non-null response that doesn't show UI (for example, has just
     *   SaveInfo)
     *   - Augmented autofill show UI, user fills, Save UI is shown
     *   - etc ...
     * - No augmented autofill calls when the main service is not set.
     * - etc...
     */
}
