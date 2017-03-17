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

import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.ID_USERNAME_LABEL;
import static android.autofillservice.cts.Helper.SAVE_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.assertNumberOfChildren;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertTextOnly;
import static android.autofillservice.cts.Helper.eventually;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.LoginActivity.AUTHENTICATION_MESSAGE;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME_CONTAINER;
import static android.autofillservice.cts.LoginActivity.getWelcomeMessage;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.text.InputType.TYPE_NULL;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.view.View.AUTOFILL_MODE_MANUAL;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.autofill.AutofillManager.FLAG_MANUAL_REQUEST;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.support.test.uiautomator.UiObject2;
import android.view.View;
import android.view.autofill.AutofillValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This is the test case covering most scenarios - other test cases will cover characteristics
 * specific to that test's activity (for example, custom views).
 *
 * TODO(b/33197203, b/33802548): test other scenarios like:
 *
 * Fill
 *  - partitioned datasets (i.e., multiple fields)
 *
 *  Save
 *  - test cases where only non-savable-ids are changed
 *  - test case where 'no thanks' is tapped (similar to testSaveSnackBarGoesAway())
 *
 *  Other assertions
 *  - illegal state thrown on callback calls
 *  - system server state after calls (for example, no pending callback)
 *  - make sure there is no dangling session using 'cmd autofill list sessions'
 */
public class LoginActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<LoginActivity> mActivityRule =
            new ActivityTestRule<LoginActivity>(LoginActivity.class);

    private LoginActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
        destroyAllSessions();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testAutoFillNoDatasets() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse((CannedFillResponse) null);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.assertNoDatasets();

        // Sanity checks.
        replier.assertNumberUnhandledFillRequests(1);
        replier.assertNumberUnhandledSaveRequests(0);

        // Other sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutofillValue.forText("dude"))
                .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Dynamically set password to make sure it's sanitized.
        mActivity.onPassword((v) -> v.setText("I AM GROOT"));

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.

        // Make sure input was sanitized.
        final FillRequest request = replier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();

        // Other sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndMoveFocusAround() throws Exception {
        // Set service.
        enableService();
        // TODO(b/35948916): callback assertions are redundant here because they're tested by
        // testAutofillCallbacks(), but that test would not detect the extra call.
        final MyAutofillCallback callback = mActivity.registerCallback();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutofillValue.forText("dude"))
                .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();
        final FillRequest fillRequest = replier.getNextFillRequest();
        final View username = mActivity.getUsername();
        final View password = mActivity.getPassword();

        callback.assertUiShownEvent(username);

        // Make sure tapping on other fields from the dataset does not trigger it again
        mActivity.onPassword((v) -> v.requestFocus());
        replier.assertNumberUnhandledFillRequests(0);

        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);

        mActivity.onUsername((v) -> v.requestFocus());
        replier.assertNumberUnhandledFillRequests(0);

        callback.assertUiHiddenEvent(password);
        callback.assertUiShownEvent(username);

        // TODO(b/35948916): temporary hack since UI is sending 2 events instead of 1
        callback.assertUiShownEvent(username);

        callback.assertNumberUnhandledEvents(0);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mActivity.onPassword((v) -> v.requestFocus());
        mActivity.onUsername((v) -> v.requestFocus());

        // Sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testAutofillCallbacks() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutofillValue.forText("dude"))
                .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();
        final FillRequest fillRequest = replier.getNextFillRequest();
        final View username = mActivity.getUsername();
        final View password = mActivity.getPassword();

        callback.assertUiShownEvent(username);

        mActivity.onPassword((v) -> v.requestFocus());
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);

        mActivity.onUsername((v) -> v.requestFocus());
        mActivity.unregisterCallback();
        callback.assertNumberUnhandledEvents(0);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.
        callback.assertNumberUnhandledEvents(0);
        waitUntilDisconnected();
    }

    @Test
    public void testAutofillCallbackDisabled() throws Exception {
        // Set service.
        disableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());

        // Assert callback was called
        final View username = mActivity.getUsername();
        callback.assertUiUnavailableEvent(username);
    }

    @Test
    public void testAutofillCallbackNoDatasets() throws Exception {
        callbackUnavailableTest(null);
    }

    @Test
    public void testAutofillCallbackNoDatasetsButSaveInfo() throws Exception {
        callbackUnavailableTest(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());
    }

    private void callbackUnavailableTest(CannedFillResponse response) throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(response);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();
        final FillRequest fillRequest = replier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.assertNoDatasets();

        // Assert callback was called
        final View username = mActivity.getUsername();
        callback.assertUiUnavailableEvent(username);

        // Sanity checks.
        replier.assertNumberUnhandledFillRequests(0);
        replier.assertNumberUnhandledSaveRequests(0);

        // Other sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndSave() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");

        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("dude"))
                        .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setExtras(extras)
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Try to login, it will fail.
        final String loginMessage = mActivity.tapLogin();

        assertWithMessage("Wrong login msg").that(loginMessage).isEqualTo(AUTHENTICATION_MESSAGE);

        // Set right password...
        mActivity.onPassword((v) -> v.setText("dude"));

        // ... and try again
        final String expectedMessage = getWelcomeMessage("dude");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()
        sUiBot.saveForAutofill(SAVE_DATA_TYPE_PASSWORD, true);

        final SaveRequest saveRequest = replier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, "dude");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(password, "dude");

        // Make sure extras were passed back on onSave()
        assertThat(saveRequest.data).isNotNull();
        final String extraValue = saveRequest.data.getString("numbers");
        assertWithMessage("extras not passed on save").that(extraValue).isEqualTo("4815162342");

        // Sanity check: make sure service was called just once.
        replier.assertNumberUnhandledFillRequests(1);
        replier.assertNumberUnhandledSaveRequests(0);

        // Other sanity checks.
        waitUntilDisconnected();

        // Sanity check: once saved, the session should be finished.
        assertNoDanglingSessions();
    }

    @Test
    public void testAutoFillMultipleDatasetsPickFirst() throws Exception {
        multipleDatasetsTest(1);
    }

    @Test
    public void testAutoFillMultipleDatasetsPickSecond() throws Exception {
        multipleDatasetsTest(2);
    }

    @Test
    public void testAutoFillMultipleDatasetsPickThird() throws Exception {
        multipleDatasetsTest(3);
    }

    private void multipleDatasetsTest(int number) throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("mr_plow"))
                        .setField(ID_PASSWORD, AutofillValue.forText("D'OH!"))
                        .setPresentation(createPresentation("Mr Plow"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("el barto"))
                        .setField(ID_PASSWORD, AutofillValue.forText("aycaramba!"))
                        .setPresentation(createPresentation("El Barto"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("mr sparkle"))
                        .setField(ID_PASSWORD, AutofillValue.forText("Aw3someP0wer"))
                        .setPresentation(createPresentation("Mr Sparkle"))
                        .build())
                .build());
        final String name;

        switch (number) {
            case 1:
                name = "Mr Plow";
                mActivity.expectAutoFill("mr_plow", "D'OH!");
                break;
            case 2:
                name = "El Barto";
                mActivity.expectAutoFill("el barto", "aycaramba!");
                break;
            case 3:
                name = "Mr Sparkle";
                mActivity.expectAutoFill("mr sparkle", "Aw3someP0wer");
                break;
            default:
                throw new IllegalArgumentException("invalid dataset number: " + number);
        }

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Make sure all datasets are shown.
        sUiBot.assertDatasets("Mr Plow", "El Barto", "Mr Sparkle");

        // Auto-fill it.
        sUiBot.selectDataset(name);

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void filterText() throws Exception {
        final String AA = "Two A's";
        final String AB = "A and B";
        final String B = "Only B";

        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("aa"))
                        .setPresentation(createPresentation(AA))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("ab"))
                        .setPresentation(createPresentation(AB))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("b"))
                        .setPresentation(createPresentation(B))
                        .build())
                .build());

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());

        waitUntilConnected();

        // With no filter text all datasets should be shown
        eventually(() -> {
            assertThat(sUiBot.hasViewWithText(AA)).isTrue();
            assertThat(sUiBot.hasViewWithText(AB)).isTrue();
            assertThat(sUiBot.hasViewWithText(B)).isTrue();
        });

        runShellCommand("input keyevent KEYCODE_A");

        // Only two datasets start with 'a'
        eventually(() -> {
            assertThat(sUiBot.hasViewWithText(AA)).isTrue();
            assertThat(sUiBot.hasViewWithText(AB)).isTrue();
            assertThat(sUiBot.hasViewWithText(B)).isFalse();
        });

        runShellCommand("input keyevent KEYCODE_A");

        // Only one datasets start with 'aa'
        eventually(() -> {
            assertThat(sUiBot.hasViewWithText(AA)).isTrue();
            assertThat(sUiBot.hasViewWithText(AB)).isFalse();
            assertThat(sUiBot.hasViewWithText(B)).isFalse();
        });

        runShellCommand("input keyevent KEYCODE_DEL");

        // Only two datasets start with 'a'
        eventually(() -> {
            assertThat(sUiBot.hasViewWithText(AA)).isTrue();
            assertThat(sUiBot.hasViewWithText(AB)).isTrue();
            assertThat(sUiBot.hasViewWithText(B)).isFalse();
        });

        runShellCommand("input keyevent KEYCODE_DEL");

        // With no filter text all datasets should be shown
        eventually(() -> {
            assertThat(sUiBot.hasViewWithText(AA)).isTrue();
            assertThat(sUiBot.hasViewWithText(AB)).isTrue();
            assertThat(sUiBot.hasViewWithText(B)).isTrue();
        });

        runShellCommand("input keyevent KEYCODE_A");
        runShellCommand("input keyevent KEYCODE_A");
        runShellCommand("input keyevent KEYCODE_A");

        // No dataset start with 'aaa'
        eventually(() -> {
            assertThat(sUiBot.hasViewWithText(AA)).isFalse();
            assertThat(sUiBot.hasViewWithText(AB)).isFalse();
            assertThat(sUiBot.hasViewWithText(B)).isFalse();
        });
    }

    @Test
    public void testSaveOnly() throws Exception {
        enableService();

        // Set service.
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        replier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(SAVE_DATA_TYPE_PASSWORD, true);

        final SaveRequest saveRequest = replier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, "malkovich");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
        assertTextAndValue(password, "malkovich");

        // Sanity check: make sure service was called just once.
        replier.assertNumberUnhandledFillRequests(0);
        replier.assertNumberUnhandledSaveRequests(0);

        // Other sanity checks.
        waitUntilDisconnected();

        // Sanity check: once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    private void setSnackBarLifetimeMs(long timeout) {
        runShellCommand("cmd autofill set save_timeout %s", timeout);
    }

    @Test
    public void testSaveSnackBarGoesAway() throws Exception {
        enableService();
        final int timeout = 1000;
        setSnackBarLifetimeMs(timeout);

        try {
            // Set service.
            final Replier replier = new Replier();
            InstrumentedAutoFillService.setReplier(replier);

            // Set expectations.
            replier.addResponse(new CannedFillResponse.Builder()
                    .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                    .build());

            // Trigger auto-fill.
            mActivity.onUsername((v) -> v.requestFocus());
            waitUntilConnected();

            // Sanity check.
            sUiBot.assertNoDatasets();

            // Wait for onFill() before proceeding, otherwise the fields might be changed before
            // the session started
            replier.getNextFillRequest();

            // Set credentials...
            mActivity.onUsername((v) -> v.setText("malkovich"));
            mActivity.onPassword((v) -> v.setText("malkovich"));

            // ...and login
            final String expectedMessage = getWelcomeMessage("malkovich");
            final String actualMessage = mActivity.tapLogin();
            assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

            InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()

            // Assert the snack bar is shown.
            sUiBot.assertSaveShowing(SAVE_DATA_TYPE_PASSWORD);
            SystemClock.sleep(timeout);
            sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

            // Sanity check: once timed out, session should be finsihed.
            assertNoDanglingSessions();

            // Other sanity checks.
            waitUntilDisconnected();
        } finally {
            setSnackBarLifetimeMs(5000);
        }
    }

    @Test
    public void testGenericSave() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_GENERIC);
    }

    @Test
    public void testCustomizedSavePassword() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_PASSWORD);
    }

    @Test
    public void testCustomizedSaveAddress() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_ADDRESS);
    }

    @Test
    public void testCustomizedSaveCreditCard() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_CREDIT_CARD);
    }

    private void customizedSaveTest(int type) throws Exception {
        enableService();

        // Set service.
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        final String saveDescription = "Your data will be saved with love and care...";
        replier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(type, ID_USERNAME, ID_PASSWORD)
                .setSaveDescription(saveDescription)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        replier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()

        // Assert the snack bar is shown and tap "Save".
        final UiObject2 saveSnackBar = sUiBot.assertSaveShowing(type, saveDescription);
        sUiBot.saveForAutofill(saveSnackBar, true);

        replier.getNextSaveRequest();

        // Other sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndSaveWhenFlagSecure() throws Exception {
        mActivity.setFlags(FLAG_SECURE);
        testAutoFillOneDatasetAndSave();
    }

    @Test
    public void testAutoFillOneDatasetWhenFlagSecure() throws Exception {
        mActivity.setFlags(FLAG_SECURE);
        testAutoFillOneDataset();
    }

    @Test
    public void testFillResponseAuth() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Prepare the authenticated response
        AuthenticationActivity.setResponse(
                new CannedFillResponse.Builder()
            .addDataset(new CannedDataset.Builder()
                    .setField(ID_USERNAME, AutofillValue.forText("dude"))
                    .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                    .setPresentation(createPresentation("Dataset"))
                    .build())
            .build());

        // Create the authentication intent
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        replier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication)
                .setPresentation(createPresentation("Auth"))
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait for onFill() before proceeding.
        replier.getNextFillRequest();

        // Authenticate
        sUiBot.selectByText("Auth");

        // Select the dataset
        sUiBot.selectDataset("Dataset");

        // Check the results.
        mActivity.assertAutoFilled();

        // Other sanity checks
        waitUntilDisconnected();
    }

    @Test
    public void testDatasetAuth() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Prepare the authenticated response
        AuthenticationActivity.setDataset(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutofillValue.forText("dude"))
                .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                .setPresentation(createPresentation("Dataset"))
                .build());

        // Create the authentication intent
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("dude"))
                        .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                        .setPresentation(createPresentation("Auth"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait for onFill() before proceeding.
        replier.getNextFillRequest();

        // Authenticate
        sUiBot.selectByText("Auth");

        // Select the dataset
        sUiBot.selectDataset("Dataset");

        // Check the results.
        mActivity.assertAutoFilled();

        // Other sanity checks
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
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Change view contents.
        mActivity.onUsernameLabel((v) -> v.setText("DA USER"));
        mActivity.onPasswordLabel((v) -> v.setText(R.string.new_password_label));

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Assert sanitization on fill request:
        final FillRequest fillRequest = replier.getNextFillRequest();

        // ...dynamic text should be sanitized.
        assertTextIsSanitized(fillRequest.structure, ID_USERNAME_LABEL);

        // ...password label should be ok because it was set from other resource id
        assertTextOnly(findNodeByResourceId(fillRequest.structure, ID_PASSWORD_LABEL),
                "DA PASSWORD");

        // ...password should be ok because it came from a resource id.
        assertTextAndValue(findNodeByResourceId(fillRequest.structure, ID_PASSWORD), "TopSecret");

        // Trigger save
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        mActivity.tapLogin();
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(SAVE_DATA_TYPE_PASSWORD, true);
        final SaveRequest saveRequest = replier.getNextSaveRequest();

        // Assert sanitization on save: everything should be available!
        assertTextOnly(findNodeByResourceId(saveRequest.structure, ID_USERNAME_LABEL), "DA USER");
        assertTextOnly(findNodeByResourceId(saveRequest.structure, ID_PASSWORD_LABEL),
                "DA PASSWORD");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_USERNAME), "malkovich");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PASSWORD), "malkovich");

        // Sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testDisableSelfWhenConnected() throws Exception {
        enableService();

        // Ensure enabled.
        assertServiceEnabled();

        // Set no-op behavior.
        final Replier replier = new Replier();
        replier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());
        InstrumentedAutoFillService.setReplier(replier);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Can disable while connected.
        mActivity.runOnUiThread(() ->
                InstrumentedAutoFillService.peekInstance().disableSelf());

        // Ensure disabled.
        assertServiceDisabled();
    }

    @Test
    public void testDisableSelfWhenDisconnected() throws Exception {
        enableService();

        // Ensure enabled.
        assertServiceEnabled();

        // Set no-op behavior.
        final Replier replier = new Replier();
        replier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());
        InstrumentedAutoFillService.setReplier(replier);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait until we timeout and disconnect.
        waitUntilDisconnected();

        // Cannot disable while disconnected.
        mActivity.runOnUiThread(() ->
                InstrumentedAutoFillService.peekInstance().disableSelf());

        // Ensure enabled.
        assertServiceEnabled();
    }

    @Test
    public void testCustomNegativeSaveButton() throws Exception {
        enableService();

        // Set service behavior.
        final Replier replier = new Replier();

        final String intentAction = "android.autofillservice.cts.CUSTOM_ACTION";

        // Configure the save UI.
        final IntentSender listener = PendingIntent.getBroadcast(
                getContext(), 0, new Intent(intentAction), 0).getIntentSender();

        replier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setNegativeAction("Foo", listener)
                .build());
        InstrumentedAutoFillService.setReplier(replier);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait for onFill() before proceeding.
        replier.getNextFillRequest();

        // Trigger save.
        mActivity.onUsername((v) -> v.setText("foo"));
        mActivity.onPassword((v) -> v.setText("foo"));
        mActivity.tapLogin();

        // Start watching for the negative intent
        final CountDownLatch latch = new CountDownLatch(1);
        final IntentFilter intentFilter = new IntentFilter(intentAction);
        getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                getContext().unregisterReceiver(this);
                latch.countDown();
            }
        }, intentFilter);

        // Trigger the negative button.
        sUiBot.saveForAutofill(SAVE_DATA_TYPE_PASSWORD, false);

        // Wait for the custom action.
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testGetTextInputType() throws Exception {
        enableService();

        // Set service.
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse((CannedFillResponse) null);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());

        // Assert input text on fill request:
        final FillRequest fillRequest = replier.getNextFillRequest();

        final ViewNode label = findNodeByResourceId(fillRequest.structure, ID_PASSWORD_LABEL);
        assertThat(label.getInputType()).isEqualTo(TYPE_NULL);
        final ViewNode password = findNodeByResourceId(fillRequest.structure, ID_PASSWORD);
        assertWithMessage("No TYPE_TEXT_VARIATION_PASSWORD on %s", password.getInputType())
                .that(password.getInputType() & TYPE_TEXT_VARIATION_PASSWORD)
                .isEqualTo(TYPE_TEXT_VARIATION_PASSWORD);
    }

    @Test
    public void testNoContainers() throws Exception {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse((CannedFillResponse) null);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();
        sUiBot.assertNoDatasets();

        final FillRequest fillRequest = replier.getNextFillRequest();

        // Assert it only has 1 root view with 9 "leaf" nodes:
        // 1.text view for app title
        // 2.username text label
        // 3.username text field
        // 4.password text label
        // 5.password text field
        // 6.output text field
        // 7.clear button
        // 8.save button
        // 9.login button
        //
        // But it also has an intermediate container (for username) that should be included because
        // it has a resource id.

        assertNumberOfChildren(fillRequest.structure, 11);

        // Make sure container with a resource id was included:
        final ViewNode usernameContainer =
                findNodeByResourceId(fillRequest.structure, ID_USERNAME_CONTAINER);
        assertThat(usernameContainer).isNotNull();
        assertThat(usernameContainer.getChildCount()).isEqualTo(2);

        // Sanity checks.
        replier.assertNumberUnhandledFillRequests(0);
        replier.assertNumberUnhandledSaveRequests(0);

        // Other sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testManualAutofill() throws Exception {
        // Set service..
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // And activity.
        mActivity.onUsername((v) -> {
            v.setAutofillMode(AUTOFILL_MODE_MANUAL);
            // TODO(b/33197203, b/33802548): setting an empty text, otherwise longPress() does not
            // display the AUTOFILL context menu. Need to fix it, but it's a test case issue...
            v.setText("");
        });

        // Set expectations.
        replier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutofillValue.forText("dude"))
                .setField(ID_PASSWORD, AutofillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Long-press field to trigger AUTOFILL menu.
        sUiBot.getAutofillMenuOption(ID_USERNAME).click();

        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.
        // Assert flag was properly set.

        final FillRequest request = replier.getNextFillRequest();
        assertThat(request.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        // Other sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testCommitMultipleTimes() throws Throwable {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        final CannedFillResponse response = new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build();

        for (int i = 1; i <= 3; i++) {
            final String username = "user-" + i;
            final String password = "pass-" + i;
            try {
                InstrumentedAutoFillService.setReplier(replier);

                // Set expectations.
                replier.addResponse(response);

                // Trigger auto-fill.
                mActivity.onUsername((v) -> v.requestFocus());
                if (i == 1) {
                    waitUntilConnected();
                }

                // Sanity check.
                sUiBot.assertNoDatasets();

                // Wait for onFill() before proceeding, otherwise the fields might be changed before
                // the session started
                replier.getNextFillRequest();

                InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()

                // Set credentials...
                mActivity.onUsername((v) -> v.setText(username));
                mActivity.onPassword((v) -> v.setText(password));

                // ...and save them
                mActivity.tapSave();

                // Assert the snack bar is shown and tap "Save".
                sUiBot.saveForAutofill(SAVE_DATA_TYPE_PASSWORD, true);

                final SaveRequest saveRequest = replier.getNextSaveRequest();

                // Assert value of expected fields - should not be sanitized.
                final ViewNode usernameNode = findNodeByResourceId(saveRequest.structure,
                        ID_USERNAME);
                assertTextAndValue(usernameNode, username);
                final ViewNode passwordNode = findNodeByResourceId(saveRequest.structure,
                        ID_PASSWORD);
                assertTextAndValue(passwordNode, password);

                assertNoDanglingSessions();

                // Change focus to prepare for next step.
                mActivity.onPassword((v) -> v.requestFocus());
            } catch (Throwable t) {
                throw new Throwable("Error on step " + i, t);
            }
        }

        // Sanity checks
        waitUntilDisconnected();
        replier.assertNumberUnhandledFillRequests(0);
        replier.assertNumberUnhandledSaveRequests(0);
    }

    @Test
    public void testCancelMultipleTimes() throws Throwable {
        // Set service.
        enableService();
        final Replier replier = new Replier();
        for (int i = 1; i <= 3; i++) {
            final String username = "user-" + i;
            final String password = "pass-" + i;
            replier.addResponse(new CannedDataset.Builder()
                    .setField(ID_USERNAME, AutofillValue.forText(username))
                    .setField(ID_PASSWORD, AutofillValue.forText(password))
                    .setPresentation(createPresentation("The Dude"))
                    .build());
            mActivity.expectAutoFill(username, password);
            try {
                InstrumentedAutoFillService.setReplier(replier);

                // Trigger auto-fill.
                mActivity.onUsername((v) -> v.requestFocus());

                waitUntilConnected();
                replier.getNextFillRequest();

                // Auto-fill it.
                sUiBot.selectDataset("The Dude");

                // Check the results.
                mActivity.assertAutoFilled();

                // Rinse and repeat...
                mActivity.tapClear();

                waitUntilDisconnected();
                assertNoDanglingSessions();

                // Change focus to prepare for next step.
                mActivity.onPassword((v) -> v.requestFocus());
            } catch (Throwable t) {
                throw new Throwable("Error on step " + i, t);
            }
        }

        // Sanity checks
        replier.assertNumberUnhandledFillRequests(0);
        replier.assertNumberUnhandledSaveRequests(0);
    }
}
