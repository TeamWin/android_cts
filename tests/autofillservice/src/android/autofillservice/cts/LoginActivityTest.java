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

import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertTextOnly;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.LoginActivity.AUTHENTICATION_MESSAGE;
import static android.autofillservice.cts.LoginActivity.ID_PASSWORD;
import static android.autofillservice.cts.LoginActivity.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME_LABEL;
import static android.autofillservice.cts.LoginActivity.getWelcomeMessage;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.uiautomator.UiObject2;
import android.view.autofill.AutoFillValue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

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
 *  - test case where 'no thanks' is tapped
 *  - make sure snack bar times out (will require a shell cmd to change timeout)
 *
 *  Other assertions
 *  - illegal state thrown on callback calls
 *  - system server state after calls (for example, no pending callback)
 *  - make sure there is no dangling session using 'cmd autofill list sessions'
 */
@SmallTest
public class LoginActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<LoginActivity> mActivityRule =
            new ActivityTestRule<LoginActivity>(LoginActivity.class);

    private LoginActivity mLoginActivity;

    @Before
    public void setActivity() {
        mLoginActivity = mActivityRule.getActivity();
        destroyAllSessions();
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
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
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
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());
        mLoginActivity.expectAutoFill("dude", "sweet");

        // Dynamically set password to make sure it's sanitized.
        mLoginActivity.onPassword((v) -> { v.setText("I AM GROOT"); });

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mLoginActivity.assertAutoFilled();

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
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                .setPresentation(createPresentation("The Dude"))
                .build());
        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

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

        // Sanity checks.
        replier.assertNumberUnhandledFillRequests(1);
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndSave() throws Exception {
        enableService();

        // Set service.
        enableService();
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");

        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                        .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .setExtras(extras)
                .build());
        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Try to login, it will fail.
        final String loginMessage = mLoginActivity.tapLogin();

        assertWithMessage("Wrong login msg").that(loginMessage).isEqualTo(AUTHENTICATION_MESSAGE);

        // Set right password...
        mLoginActivity.onPassword((v) -> { v.setText("dude"); });

        // ... and try again
        final String expectedMessage = getWelcomeMessage("dude");
        final String actualMessage = mLoginActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()
        sUiBot.saveForAutofill(true);

        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

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
                        .setField(ID_USERNAME, AutoFillValue.forText("mr_plow"))
                        .setField(ID_PASSWORD, AutoFillValue.forText("D'OH!"))
                        .setPresentation(createPresentation("Mr Plow"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutoFillValue.forText("el barto"))
                        .setField(ID_PASSWORD, AutoFillValue.forText("aycaramba!"))
                        .setPresentation(createPresentation("El Barto"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutoFillValue.forText("mr sparkle"))
                        .setField(ID_PASSWORD, AutoFillValue.forText("Aw3someP0wer"))
                        .setPresentation(createPresentation("Mr Sparkle"))
                        .build())
                .build());
        final String name;

        switch (number) {
            case 1:
                name = "Mr Plow";
                mLoginActivity.expectAutoFill("mr_plow", "D'OH!");
                break;
            case 2:
                name = "El Barto";
                mLoginActivity.expectAutoFill("el barto", "aycaramba!");
                break;
            case 3:
                name = "Mr Sparkle";
                mLoginActivity.expectAutoFill("mr sparkle", "Aw3someP0wer");
                break;
            default:
                throw new IllegalArgumentException("invalid dataset number: " + number);
        }

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Make sure all datasets are shown.
        sUiBot.assertDatasets("Mr Plow", "El Barto", "Mr Sparkle");

        // Auto-fill it.
        sUiBot.selectDataset(name);

        // Check the results.
        mLoginActivity.assertAutoFilled();

        // Sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testSaveOnly() throws Exception {
        enableService();

        // Set service.
        final Replier replier = new Replier();
        InstrumentedAutoFillService.setReplier(replier);

        // Set expectations.
        replier.addResponse(new CannedFillResponse.Builder()
                .setSavableIds(ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        replier.getNextFillRequest();

        // Set credentials...
        mLoginActivity.onUsername((v) -> { v.setText("malkovich"); });
        mLoginActivity.onPassword((v) -> { v.setText("malkovich"); });

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mLoginActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true);

        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

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
                .setSavableIds(ID_USERNAME, ID_PASSWORD)
                .setSaveDescription(saveDescription)
                .setSaveType(type)
                .build());

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
        waitUntilConnected();

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        replier.getNextFillRequest();

        // Set credentials...
        mLoginActivity.onUsername((v) -> { v.setText("malkovich"); });
        mLoginActivity.onPassword((v) -> { v.setText("malkovich"); });

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mLoginActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()

        // Assert the snack bar is shown and tap "Save".
        final UiObject2 saveSnackBar = sUiBot.assertSaveShowing(type, saveDescription);
        sUiBot.saveForAutofill(saveSnackBar, true);

        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Other sanity checks.
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDatasetAndSaveWhenFlagSecure() throws Exception {
        mLoginActivity.setFlags(FLAG_SECURE);
        testAutoFillOneDatasetAndSave();
    }

    @Test
    public void testAutoFillOneDatasetWhenFlagSecure() throws Exception {
        mLoginActivity.setFlags(FLAG_SECURE);
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
                    .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                    .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
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
        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait for onFill() before proceeding.
        replier.getNextFillRequest();

        // Authenticate
        sUiBot.selectByText("Auth");

        // Select the dataset
        sUiBot.selectDataset("Dataset");

        // Check the results.
        mLoginActivity.assertAutoFilled();

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
                .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                .setPresentation(createPresentation("Dataset"))
                .build());

        // Create the authentication intent
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        replier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, AutoFillValue.forText("dude"))
                        .setField(ID_PASSWORD, AutoFillValue.forText("sweet"))
                        .setPresentation(createPresentation("Auth"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mLoginActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait for onFill() before proceeding.
        replier.getNextFillRequest();

        // Authenticate
        sUiBot.selectByText("Auth");

        // Select the dataset
        sUiBot.selectDataset("Dataset");

        // Check the results.
        mLoginActivity.assertAutoFilled();

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
                .setSavableIds(ID_USERNAME, ID_PASSWORD)
                .build());

        // Change view contents.
        mLoginActivity.onUsernameLabel((v) -> { v.setText("DA USER"); });
        mLoginActivity.onPasswordLabel((v) -> { v.setText(R.string.new_password_label); });

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> { v.requestFocus(); });
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
        mLoginActivity.onUsername((v) -> { v.setText("malkovich"); });
        mLoginActivity.onPassword((v) -> { v.setText("malkovich"); });
        mLoginActivity.tapLogin();
        InstrumentedAutoFillService.setReplier(replier); // Replier was reset onFill()
        sUiBot.saveForAutofill(true);
        final SaveRequest saveRequest = replier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

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
                .setSavableIds(ID_USERNAME, ID_PASSWORD)
                .build());
        InstrumentedAutoFillService.setReplier(replier);

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Can disable while connected.
        mLoginActivity.runOnUiThread(() ->
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
                .setSavableIds(ID_USERNAME, ID_PASSWORD)
                .build());
        InstrumentedAutoFillService.setReplier(replier);

        // Trigger auto-fill.
        mLoginActivity.onUsername((v) -> v.requestFocus());
        waitUntilConnected();

        // Wait until we timeout and disconnect.
        waitUntilDisconnected();

        // Cannot disable while disconnected.
        mLoginActivity.runOnUiThread(() ->
                InstrumentedAutoFillService.peekInstance().disableSelf());

        // Ensure enabled.
        assertServiceEnabled();
    }
}
