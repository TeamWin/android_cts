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
import static android.autofillservice.cts.UiBot.LANDSCAPE;
import static android.autofillservice.cts.UiBot.PORTRAIT;
import static android.autofillservice.cts.augmented.AugmentedHelper.assertBasicRequestInfo;
import static android.autofillservice.cts.augmented.AugmentedTimeouts.AUGMENTED_FILL_TIMEOUT;
import static android.autofillservice.cts.augmented.CannedAugmentedFillResponse.DO_NOT_REPLY_AUGMENTED_RESPONSE;
import static android.autofillservice.cts.augmented.CannedAugmentedFillResponse.NO_AUGMENTED_RESPONSE;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.autofillservice.cts.AutofillActivityTestRule;
import android.autofillservice.cts.Helper;
import android.autofillservice.cts.LoginActivity;
import android.autofillservice.cts.OneTimeCancellationSignalListener;
import android.autofillservice.cts.augmented.CtsAugmentedAutofillService.AugmentedFillRequest;
import android.content.ComponentName;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiObject2;
import android.util.ArraySet;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.EditText;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Set;

public class AugmentedLoginActivityTest
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
    public void testAugmentedAutoFill_multipleRequests() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillValue usernameValue = username.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("req1")
                        .build(), usernameId)
                .build());

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        final AugmentedFillRequest request1 = sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request1, mActivity, usernameId, usernameValue);

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is shown.
        mAugmentedUiBot.assertUiShown(usernameId, "req1");

        // Move focus away to make sure Augmented Autofill UI is gone.
        mActivity.onLogin(View::requestFocus);
        mAugmentedUiBot.assertUiGone();

        // Tap on password field
        final EditText password = mActivity.getPassword();
        final AutofillId passwordId = password.getAutofillId();
        final AutofillValue passwordValue = password.getAutofillValue();
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("req2")
                        .build(), passwordId)
                .build());
        mActivity.onPassword(View::requestFocus);
        mUiBot.assertNoDatasetsEver();
        final AugmentedFillRequest request2 = sAugmentedReplier.getNextFillRequest();
        assertBasicRequestInfo(request2, mActivity, passwordId, passwordValue);

        mAugmentedUiBot.assertUiShown(passwordId, "req2");

        // Tap on username again...
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me")
                        .setField(usernameId, "dude")
                        .setField(passwordId, "sweet")
                        .build(), usernameId)
                .build());

        mActivity.onUsername(View::requestFocus);
        final AugmentedFillRequest request3 = sAugmentedReplier.getNextFillRequest();
        assertBasicRequestInfo(request3, mActivity, usernameId, usernameValue);
        final UiObject2 ui = mAugmentedUiBot.assertUiShown(usernameId, "Augment Me");

        // ...and autofill this time
        mActivity.expectAutoFill("dude", "sweet");
        ui.click();
        mActivity.assertAutoFilled();
        mAugmentedUiBot.assertUiGone();
    }

    @Test
    public void testAugmentedAutoFill_rotateDevice() throws Exception {
        assumeTrue("Rotation is supported", Helper.isRotationSupported(mContext));

        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText username = mActivity.getUsername();
        final AutofillId usernameId = username.getAutofillId();
        final AutofillValue usernameValue = username.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me 1")
                        .setField(usernameId, "dude1")
                        .build(), usernameId)
                .build());

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        final AugmentedFillRequest request1 = sAugmentedReplier.getNextFillRequest();

        AugmentedLoginActivity currentActivity = mActivity;

        // Assert request
        assertBasicRequestInfo(request1, currentActivity, usernameId, usernameValue);

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is shown.
        mAugmentedUiBot.assertUiShown(usernameId, "Augment Me 1");

        // 1st landscape rotation
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me 2")
                        .setField(usernameId, "dude2")
                        .build(), usernameId)
                .build());
        mUiBot.setScreenOrientation(LANDSCAPE);
        mUiBot.assertNoDatasetsEver();

        // Must update currentActivity after each rotation because it generates a new instance
        currentActivity = LoginActivity.getCurrentActivity();

        final AugmentedFillRequest request2 = sAugmentedReplier.getNextFillRequest();
        assertBasicRequestInfo(request2, currentActivity, usernameId, usernameValue);
        mAugmentedUiBot.assertUiShown(usernameId, "Augment Me 2");

        // Rotate back
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me 3")
                        .setField(usernameId, "dude3")
                        .build(), usernameId)
                .build());
        mUiBot.setScreenOrientation(PORTRAIT);
        mUiBot.assertNoDatasetsEver();
        currentActivity = LoginActivity.getCurrentActivity();

        final AugmentedFillRequest request3 = sAugmentedReplier.getNextFillRequest();
        assertBasicRequestInfo(request3, currentActivity, usernameId, usernameValue);
        mAugmentedUiBot.assertUiShown(usernameId, "Augment Me 3");

        // 2nd landscape rotation
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me 4")
                        .setField(usernameId, "dude4")
                        .build(), usernameId)
                .build());
        mUiBot.setScreenOrientation(LANDSCAPE);
        mUiBot.assertNoDatasetsEver();
        currentActivity = LoginActivity.getCurrentActivity();

        final AugmentedFillRequest request4 = sAugmentedReplier.getNextFillRequest();
        assertBasicRequestInfo(request4, currentActivity, usernameId, usernameValue);
        mAugmentedUiBot.assertUiShown(usernameId, "Augment Me 4");

        // Final rotation - should be enough....
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("Augment Me 5")
                        .setField(usernameId, "dude5")
                        .build(), usernameId)
                .build());
        mUiBot.setScreenOrientation(PORTRAIT);
        mUiBot.assertNoDatasetsEver();
        currentActivity = LoginActivity.getCurrentActivity();

        final AugmentedFillRequest request5 = sAugmentedReplier.getNextFillRequest();
        assertBasicRequestInfo(request5, mActivity, usernameId, usernameValue);
        final UiObject2 ui = mAugmentedUiBot.assertUiShown(usernameId, "Augment Me 5");

        // ..then autofill

        // Must get the latest activity because each rotation creates a new object.
        currentActivity.expectAutoFill("dude5");
        ui.click();
        mAugmentedUiBot.assertUiGone();
        currentActivity.assertAutoFilled();
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

    @Test
    public void testAugmentedAutofill_packageNotWhitelisted() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        final AutofillManager mgr = mActivity.getAutofillManager();
        mgr.setAugmentedAutofillWhitelist((Set) null, (Set) null);

        // Set expectations
        sReplier.addResponse(NO_RESPONSE);

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Assert no fill requests
        sAugmentedReplier.assertNoUnhandledFillRequests();

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is not shown.
        mAugmentedUiBot.assertUiNeverShown();
    }

    @Test
    public void testAugmentedAutofill_activityNotWhitelisted() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        final AutofillManager mgr = mActivity.getAutofillManager();
        final ArraySet<ComponentName> components = new ArraySet<>();
        components.add(new ComponentName(Helper.MY_PACKAGE, "some.activity"));
        mgr.setAugmentedAutofillWhitelist(null, components);

        // Set expectations
        sReplier.addResponse(NO_RESPONSE);

        // Trigger autofill
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Assert no fill requests
        sAugmentedReplier.assertNoUnhandledFillRequests();

        // Make sure standard Autofill UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Make sure Augmented Autofill UI is not shown.
        mAugmentedUiBot.assertUiNeverShown();
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
