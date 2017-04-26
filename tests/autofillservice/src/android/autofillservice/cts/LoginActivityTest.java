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

import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertNumberOfChildren;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.eventually;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.autofillservice.cts.Helper.setUserRestrictionForAutofill;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.LoginActivity.AUTHENTICATION_MESSAGE;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME_CONTAINER;
import static android.autofillservice.cts.LoginActivity.getWelcomeMessage;
import static android.service.autofill.FillEventHistory.Event.TYPE_AUTHENTICATION_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_DATASET_AUTHENTICATION_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_DATASET_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_SAVE_SHOWN;
import static android.service.autofill.FillRequest.FLAG_MANUAL_REQUEST;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_USERNAME;
import static android.text.InputType.TYPE_NULL;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.service.autofill.FillEventHistory;
import android.service.autofill.SaveInfo;
import android.support.test.rule.ActivityTestRule;
import android.support.test.uiautomator.UiObject2;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.autofill.AutofillManager;
import android.widget.RemoteViews;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This is the test case covering most scenarios - other test cases will cover characteristics
 * specific to that test's activity (for example, custom views).
 */
public class LoginActivityTest extends AutoFillServiceTestCase {

    // TODO(b/37424539): remove when fixed
    private static final boolean SUPPORTS_PARTITIONED_AUTH = false;

    @Rule
    public final ActivityTestRule<LoginActivity> mActivityRule = new ActivityTestRule<LoginActivity>(
            LoginActivity.class);

    private LoginActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testAutoFillNoDatasets() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Test connection lifecycle.
        waitUntilConnected();
        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.assertNoDatasets();

        // Test connection lifecycle.
        waitUntilDisconnected();
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Dynamically set password to make sure it's sanitized.
        mActivity.onPassword((v) -> v.setText("I AM GROOT"));

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.

        // Make sure input was sanitized.
        final FillRequest request = sReplier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();
    }

    @Test
    public void testAutoFillWhenViewHasChildAccessibilityNodes() throws Exception {
        mActivity.onUsername((v) -> v.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public AccessibilityNodeProvider getAccessibilityNodeProvider(View host) {
                return new AccessibilityNodeProvider() {
                    @Override
                    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
                        final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                        if (virtualViewId == View.NO_ID) {
                            info.addChild(v, 108);
                        }
                        return info;
                    }
                };
            }
        }));

        testAutoFillOneDataset();
    }

    @Test
    public void testAutoFillOneDatasetAndMoveFocusAround() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mActivity.onPassword(View::requestFocus);
        sReplier.assertNumberUnhandledFillRequests(0);

        mActivity.onUsername(View::requestFocus);
        sReplier.assertNumberUnhandledFillRequests(0);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mActivity.onPassword(View::requestFocus);
        mActivity.onUsername(View::requestFocus);
    }

    @Test
    public void testUiNotShownAfterAutofilled() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure tapping on autofilled field does not trigger it again
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertNoDatasets();

        mActivity.onUsername(View::requestFocus);
        sUiBot.assertNoDatasets();
    }

    @Test
    public void testAutofillCallbacks() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        final View password = mActivity.getPassword();

        callback.assertUiShownEvent(username);

        mActivity.onPassword(View::requestFocus);
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);

        mActivity.onUsername(View::requestFocus);
        mActivity.unregisterCallback();
        callback.assertNumberUnhandledEvents(0);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillCallbackDisabled() throws Exception {
        // Set service.
        disableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Assert callback was called
        final View username = mActivity.getUsername();
        callback.assertUiUnavailableEvent(username);
    }

    @Test
    public void testAutofillCallbackNoDatasets() throws Exception {
        callbackUnavailableTest(NO_RESPONSE);
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

        // Set expectations.
        sReplier.addResponse(response);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.assertNoDatasets();

        // Assert callback was called
        final View username = mActivity.getUsername();
        callback.assertUiUnavailableEvent(username);
    }

    @Test
    public void testAutoFillOneDatasetAndSave() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setExtras(extras)
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

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
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, "dude");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(password, "dude");

        // Make sure extras were passed back on onSave()
        assertThat(saveRequest.data).isNotNull();
        final String extraValue = saveRequest.data.getString("numbers");
        assertWithMessage("extras not passed on save").that(extraValue).isEqualTo("4815162342");

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

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "mr_plow")
                        .setField(ID_PASSWORD, "D'OH!")
                        .setPresentation(createPresentation("Mr Plow"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "el barto")
                        .setField(ID_PASSWORD, "aycaramba!")
                        .setPresentation(createPresentation("El Barto"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "mr sparkle")
                        .setField(ID_PASSWORD, "Aw3someP0wer")
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
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure all datasets are shown.
        final UiObject2 picker = sUiBot.assertDatasets("Mr Plow", "El Barto", "Mr Sparkle");

        // Auto-fill it.
        sUiBot.selectDataset(picker, name);

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password).
     */
    @Test
    public void testAutofillOneDatasetCustomPresentation() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude",
                        createPresentation("The Dude"))
                .setField(ID_PASSWORD, "sweet",
                        createPresentation("Dude's password"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("The Dude");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Dude's password");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("The Dude");

        // Auto-fill it.
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("Dude's password");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password) and the dataset itself, and each dataset has the same number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentations() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder(createPresentation("Dataset1"))
                        .setField(ID_USERNAME, "user1") // no presentation
                        .setField(ID_PASSWORD, "pass1", createPresentation("Pass1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user2", createPresentation("User2"))
                        .setField(ID_PASSWORD, "pass2") // no presentation
                        .setPresentation(createPresentation("Dataset2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user1", "pass1");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("Dataset1", "User2");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass1", "Dataset2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("Dataset1", "User2");

        // Auto-fill it.
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("Pass1");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password), and each dataset has the same number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentationSameFields() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user1", createPresentation("User1"))
                        .setField(ID_PASSWORD, "pass1", createPresentation("Pass1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user2", createPresentation("User2"))
                        .setField(ID_PASSWORD, "pass2", createPresentation("Pass2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user1", "pass1");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("User1", "User2");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass1", "Pass2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("User1", "User2");

        // Auto-fill it.
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("Pass1");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password), but each dataset has a different number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentationFirstDatasetMissingSecondField()
            throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user1", createPresentation("User1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user2", createPresentation("User2"))
                        .setField(ID_PASSWORD, "pass2", createPresentation("Pass2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user2", "pass2");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("User1", "User2");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("User1", "User2");

        // Auto-fill it.
        sUiBot.selectDataset("User2");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password), but each dataset has a different number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentationSecondDatasetMissingFirstField()
            throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user1", createPresentation("User1"))
                        .setField(ID_PASSWORD, "pass1", createPresentation("Pass1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_PASSWORD, "pass2", createPresentation("Pass2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user1", "pass1");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("User1");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass1", "Pass2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("User1");

        // Auto-fill it.
        sUiBot.selectDataset("User1");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void filterText() throws Exception {
        final String AA = "Two A's";
        final String AB = "A and B";
        final String B = "Only B";

        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "aa")
                        .setPresentation(createPresentation(AA))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "ab")
                        .setPresentation(createPresentation(AB))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "b")
                        .setPresentation(createPresentation(B))
                        .build())
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

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

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword(View::requestFocus);
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, "malkovich");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
        assertTextAndValue(password, "malkovich");

        // Sanity check: once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testSaveOnlyOptionalField() throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME)
                .setOptionalSavableIds(ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword(View::requestFocus);
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, "malkovich");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
        assertTextAndValue(password, "malkovich");

        // Sanity check: once saved, the session should be finsihed.
        assertNoDanglingSessions();
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

    @Test
    public void testCustomizedSaveUsername() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_USERNAME);
    }

    @Test
    public void testCustomizedSaveEmailAddress() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_EMAIL_ADDRESS);
    }

    private void customizedSaveTest(int type) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final String saveDescription = "Your data will be saved with love and care...";
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(type, ID_USERNAME, ID_PASSWORD)
                .setSaveDescription(saveDescription)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started.
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        final UiObject2 saveSnackBar = sUiBot.assertSaveShowing(saveDescription, type);
        sUiBot.saveForAutofill(saveSnackBar, true);

        // Assert save was called.
        sReplier.getNextSaveRequest();
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
    public void testFillResponseAuthBothFields() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");
        AuthenticationActivity.setResponse(
                new CannedFillResponse.Builder()
                        .addDataset(new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setPresentation(createPresentation("Dataset"))
                                .build())
                        .build());

        // Create the authentication intent
        final IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication)
                .setPresentation(createPresentation("Tap to auth response"))
                .setExtras(extras)
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        callback.assertUiShownEvent(username);
        sUiBot.assertShownByText("Tap to auth response");

        // Make sure UI is show on 2nd field as weell
        final View password = mActivity.getPassword();
        mActivity.onPassword(View::requestFocus);
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);
        sUiBot.assertShownByText("Tap to auth response");

        // Now tap on 1st field to show it again...
        mActivity.onUsername(View::requestFocus);
        callback.assertUiHiddenEvent(password);
        callback.assertUiShownEvent(username);
        sUiBot.selectByText("Tap to auth response");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNotShownByText("Tap to auth response");

        // ...and select it this time
        callback.assertUiShownEvent(username);
        sUiBot.selectDataset("Dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();
        sUiBot.assertNotShownByText("Tap to auth response");

        // Check the results.
        mActivity.assertAutoFilled();

        final Bundle data = AuthenticationActivity.getData();
        assertThat(data).isNotNull();
        final String extraValue = data.getString("numbers");
        assertThat(extraValue).isEqualTo("4815162342");
    }

    @Test
    public void testFillResponseAuthJustOneField() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");
        AuthenticationActivity.setResponse(
                new CannedFillResponse.Builder()
                        .addDataset(new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setPresentation(createPresentation("Dataset"))
                                .build())
                        .setAuthenticationIds(ID_USERNAME)
                        .build());

        // Create the authentication intent
        final IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication)
                .setPresentation(createPresentation("Tap to auth response"))
                .setExtras(extras)
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        callback.assertUiShownEvent(username);
        sUiBot.assertShownByText("Tap to auth response");

        if (SUPPORTS_PARTITIONED_AUTH) {
            // Make sure UI is not show on 2nd field
            final View password = mActivity.getPassword();
            mActivity.onPassword(View::requestFocus);
            callback.assertUiHiddenEvent(username);
            sUiBot.assertNotShownByText("Tap to auth response");
            // Now tap on 1st field to show it again...
            mActivity.onUsername(View::requestFocus);
            callback.assertUiShownEvent(username);
        } else {
            // Make sure UI is show on 2nd field as well
            final View password = mActivity.getPassword();
            mActivity.onPassword(View::requestFocus);

            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(password);
            sUiBot.assertShownByText("Tap to auth response");

            // Now tap on 1st field to show it again...
            mActivity.onUsername(View::requestFocus);
            callback.assertUiHiddenEvent(password);
            callback.assertUiShownEvent(username);
        }

        // ...and select it this time
        sUiBot.selectByText("Tap to auth response");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNotShownByText("Tap to auth response");

        callback.assertUiShownEvent(username);
        sUiBot.selectDataset("Dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();
        sUiBot.assertNotShownByText("Tap to auth response");

        // Check the results.
        mActivity.assertAutoFilled();

        final Bundle data = AuthenticationActivity.getData();
        assertThat(data).isNotNull();
        final String extraValue = data.getString("numbers");
        assertThat(extraValue).isEqualTo("4815162342");
    }

    @Test
    public void testDatasetAuth() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        AuthenticationActivity.setDataset(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("Dataset"))
                .build());

        // Create the authentication intent
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Authenticate
        callback.assertUiShownEvent(username);
        sUiBot.selectByText("Tap to auth dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNotShownByText("Tap to auth dataset");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testDisableSelf() throws Exception {
        enableService();

        // Can disable while connected.
        mActivity.runOnUiThread(() -> getContext().getSystemService(
                AutofillManager.class).disableOwnedAutofillServices());

        // Ensure disabled.
        assertServiceDisabled();
    }

    @Test
    public void testRejectStyleNegativeSaveButton() throws Exception {
        enableService();

        // Set service behavior.

        final String intentAction = "android.autofillservice.cts.CUSTOM_ACTION";

        // Configure the save UI.
        final IntentSender listener = PendingIntent.getBroadcast(
                getContext(), 0, new Intent(intentAction), 0).getIntentSender();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setNegativeAction(SaveInfo.NEGATIVE_BUTTON_STYLE_REJECT, listener)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

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
        sUiBot.saveForAutofill(SaveInfo.NEGATIVE_BUTTON_STYLE_REJECT,
                false, SAVE_DATA_TYPE_PASSWORD);

        // Wait for the custom action.
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertNoDanglingSessions();
    }

    @Test
    public void testCancelStyleNegativeSaveButton() throws Exception {
        enableService();

        // Set service behavior.

        final String intentAction = "android.autofillservice.cts.CUSTOM_ACTION";

        // Configure the save UI.
        final IntentSender listener = PendingIntent.getBroadcast(
                getContext(), 0, new Intent(intentAction), 0).getIntentSender();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setNegativeAction(SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, listener)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

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
        sUiBot.saveForAutofill(SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL,
                false, SAVE_DATA_TYPE_PASSWORD);

        // Wait for the custom action.
        assertThat(latch.await(500, TimeUnit.SECONDS)).isTrue();

        assertNoDanglingSessions();
    }

    @Test
    public void testGetTextInputType() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Assert input text on fill request:
        final FillRequest fillRequest = sReplier.getNextFillRequest();

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

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        sUiBot.assertNoDatasets();

        final FillRequest fillRequest = sReplier.getNextFillRequest();

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
        final ViewNode usernameContainer = findNodeByResourceId(fillRequest.structure,
                ID_USERNAME_CONTAINER);
        assertThat(usernameContainer).isNotNull();
        assertThat(usernameContainer.getChildCount()).isEqualTo(2);
    }

    private static final boolean BUG_36171235_FIXED = false;

    @Test
    public void testAutofillManuallyOneDataset() throws Exception {
        // Set service.
        enableService();

        if (BUG_36171235_FIXED)
        // And activity.
        mActivity.onUsername((v) -> {
            v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            // TODO: setting an empty text, otherwise longPress() does not
            // display the AUTOFILL context menu. Need to fix it, but it's a test case issue...
            v.setText("");
        });

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Long-press field to trigger AUTOFILL menu.
        if (BUG_36171235_FIXED) {
            sUiBot.getAutofillMenuOption(ID_USERNAME).click();
        } else {
            mActivity.onUsername((v) -> mActivity.getAutofillManager().requestAutofill(v));
        }

        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        // Should have been automatically filled.
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillManuallyTwoDatasetsPickFirst() throws Exception {
        autofillManuallyTwoDatasets(true);
    }

    @Test
    public void testAutofillManuallyTwoDatasetsPickSecond() throws Exception {
        autofillManuallyTwoDatasets(false);
    }

    private void autofillManuallyTwoDatasets(boolean pickFirst) throws Exception {
        // Set service.
        enableService();

        if (BUG_36171235_FIXED)
        // And activity.
        mActivity.onUsername((v) -> {
            v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            // TODO: setting an empty text, otherwise longPress() does not display the AUTOFILL
            // context menu. Need to fix it, but it's a test case issue...
            v.setText("");
        });

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "jenny")
                        .setField(ID_PASSWORD, "8675309")
                        .setPresentation(createPresentation("Jenny"))
                        .build())
                .build());
        if (pickFirst) {
            mActivity.expectAutoFill("dude", "sweet");
        } else {
            mActivity.expectAutoFill("jenny", "8675309");

        }

        // Long-press field to trigger AUTOFILL menu.
        if (BUG_36171235_FIXED) {
            sUiBot.getAutofillMenuOption(ID_USERNAME).click();
        } else {
            mActivity.onUsername((v) -> mActivity.getAutofillManager().requestAutofill(v));
        }

        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        // Auto-fill it.
        final UiObject2 picker = sUiBot.assertDatasets("The Dude", "Jenny");
        sUiBot.selectDataset(picker, pickFirst ? "The Dude" : "Jenny");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testCommitMultipleTimes() throws Throwable {
        // Set service.
        enableService();

        final CannedFillResponse response = new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build();

        for (int i = 1; i <= 3; i++) {
            final String username = "user-" + i;
            final String password = "pass-" + i;
            try {
                // Set expectations.
                sReplier.addResponse(response);

                // Trigger auto-fill.
                mActivity.onUsername(View::requestFocus);

                // Sanity check.
                sUiBot.assertNoDatasets();

                // Wait for onFill() before proceeding, otherwise the fields might be changed before
                // the session started
                waitUntilConnected();
                sReplier.getNextFillRequest();

                // Set credentials...
                mActivity.onUsername((v) -> v.setText(username));
                mActivity.onPassword((v) -> v.setText(password));

                // Change focus to prepare for next step - must do it before session is gone
                mActivity.onPassword(View::requestFocus);

                // ...and save them
                mActivity.tapSave();

                // Assert the snack bar is shown and tap "Save".
                sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

                final SaveRequest saveRequest = sReplier.getNextSaveRequest();

                // Assert value of expected fields - should not be sanitized.
                final ViewNode usernameNode = findNodeByResourceId(saveRequest.structure,
                        ID_USERNAME);
                assertTextAndValue(usernameNode, username);
                final ViewNode passwordNode = findNodeByResourceId(saveRequest.structure,
                        ID_PASSWORD);
                assertTextAndValue(passwordNode, password);

                waitUntilDisconnected();
                assertNoDanglingSessions();

            } catch (Throwable t) {
                throw new Throwable("Error on step " + i, t);
            }
        }
    }

    @Test
    public void testCancelMultipleTimes() throws Throwable {
        // Set service.
        enableService();

        for (int i = 1; i <= 3; i++) {
            final String username = "user-" + i;
            final String password = "pass-" + i;
            sReplier.addResponse(new CannedDataset.Builder()
                    .setField(ID_USERNAME, username)
                    .setField(ID_PASSWORD, password)
                    .setPresentation(createPresentation("The Dude"))
                    .build());
            mActivity.expectAutoFill(username, password);
            try {
                // Trigger auto-fill.
                mActivity.onUsername(View::requestFocus);

                waitUntilConnected();
                sReplier.getNextFillRequest();

                // Auto-fill it.
                sUiBot.selectDataset("The Dude");

                // Check the results.
                mActivity.assertAutoFilled();

                // Change focus to prepare for next step - must do it before session is gone
                mActivity.onPassword(View::requestFocus);

                // Rinse and repeat...
                mActivity.tapClear();

                waitUntilDisconnected();
                assertNoDanglingSessions();

            } catch (Throwable t) {
                throw new Throwable("Error on step " + i, t);
            }
        }
    }

    @Test
    public void testUserRestriction() throws Exception {
        // Set service.
        setUserRestrictionForAutofill(false);
        enableService();

        final AutofillManager afm = mActivity.getAutofillManager();
        assertThat(afm.isEnabled()).isTrue();
        assertThat(afm.isAutofillSupported()).isTrue();

        // Set expectations.
        final CannedDataset dataset = new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build();
        sReplier.addResponse(dataset);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();

        sReplier.getNextFillRequest();

        // Make sure UI is shown initially.
        sUiBot.assertDatasets("The Dude");

        // Disable it...
        setUserRestrictionForAutofill(true);
        try {
            waitUntilDisconnected();
            assertNoDanglingSessions();
            assertThat(afm.isEnabled()).isFalse();
            assertThat(afm.isAutofillSupported()).isFalse();

            // ...and then assert is not shown.
            sUiBot.assertNoDatasets();

            // Re-enable and try again.
            setUserRestrictionForAutofill(false);
            sReplier.addResponse(dataset);

            // Must reset session on app's side
            mActivity.tapClear();
            mActivity.expectAutoFill("dude", "sweet");
            mActivity.onPassword(View::requestFocus);
            sReplier.getNextFillRequest();
            sUiBot.selectDataset("The Dude");

            // Check the results.
            mActivity.assertAutoFilled();
        } finally {
            setUserRestrictionForAutofill(false);
        }
    }

    @Test
    public void testClickCustomButton() throws Exception {
        // Set service.
        enableService();

        Intent intent = new Intent(getContext(), EmptyActivity.class);
        IntentSender sender = PendingIntent.getActivity(getContext(), 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT)
                .getIntentSender();

        RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                R.layout.list_item);
        presentation.setTextViewText(R.id.text1, "Poke");
        Intent firstIntent = new Intent(getContext(), DummyActivity.class);
        presentation.setOnClickPendingIntent(R.id.text1, PendingIntent.getActivity(
                getContext(), 0, firstIntent, PendingIntent.FLAG_ONE_SHOT
                        | PendingIntent.FLAG_CANCEL_CURRENT));

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(sender)
                .setPresentation(presentation)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Click on the custom button
        sUiBot.selectByText("Poke");

        // Make sure the click worked
        sUiBot.selectByText("foo");

        // Go back to the filled app.
        sUiBot.pressBack();

        // The session should be gone
        assertNoDanglingSessions();
    }

    @Test
    public void checkFillSelectionAfterSelectingDatasetAuthentication() throws Exception {
        enableService();

        // Set up FillResponse with dataset authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        AuthenticationActivity.setDataset(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("Dataset"))
                .build());

        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setId("name")
                        .setPresentation(createPresentation("authentication"))
                        .setAuthentication(authentication)
                        .build())
                .setExtras(clientState).build());

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Authenticate
        sUiBot.selectDataset("authentication");
        sReplier.getNextFillRequest();

        eventually(() -> {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                    "clientStateValue");

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_AUTHENTICATION_SELECTED);
            assertThat(event.getDatasetId()).isEqualTo("name");
        });
    }

    @Test
    public void checkFillSelectionAfterSelectingAuthentication() throws Exception {
        enableService();

        // Set up FillResponse with response wide authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        AuthenticationActivity.setResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setId("name")
                        .setPresentation(createPresentation("dataset"))
                        .build())
                .setExtras(clientState).build());

        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), AuthenticationActivity.class), 0).getIntentSender();

        sReplier.addResponse(new CannedFillResponse.Builder().setExtras(clientState)
                .setPresentation(createPresentation("authentication"))
                .setAuthentication(authentication)
                .build());

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Authenticate
        sUiBot.selectDataset("authentication");
        sReplier.getNextFillRequest();

        eventually(() -> {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                    "clientStateValue");

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_AUTHENTICATION_SELECTED);
            assertThat(event.getDatasetId()).isNull();
        });
    }

    @Test
    public void checkFillSelectionAfterSelectingTwoDatasets() throws Exception {
        enableService();

        // Set up first partition with an anonymous dataset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());

        // Trigger autofill on username
        mActivity.onUsername(View::requestFocus);
        sUiBot.selectDataset("dataset1");
        sReplier.getNextFillRequest();

        eventually(() -> {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState()).isNull();

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event.getDatasetId()).isNull();
        });

        // Set up second partition with a named dataset
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password2")
                                .setPresentation(createPresentation("dataset2"))
                                .setId("name2")
                                .build())
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password3")
                                .setPresentation(createPresentation("dataset3"))
                                .setId("name3")
                                .build())
                .setExtras(clientState)
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_PASSWORD).build());

        // Trigger autofill on password
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("dataset3");
        sReplier.getNextFillRequest();

        eventually(() -> {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                    "clientStateValue");

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event.getDatasetId()).isEqualTo("name3");
        });

        mActivity.onPassword((v) -> v.setText("new password"));
        mActivity.syncRunOnUiThread(() -> mActivity.finish());

        eventually(() -> {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                    "clientStateValue");

            assertThat(selection.getEvents().size()).isEqualTo(2);
            FillEventHistory.Event event1 = selection.getEvents().get(0);
            assertThat(event1.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event1.getDatasetId()).isEqualTo("name3");

            FillEventHistory.Event event2 = selection.getEvents().get(1);
            assertThat(event2.getType()).isEqualTo(TYPE_SAVE_SHOWN);
            assertThat(event2.getDatasetId()).isNull();
        });
    }

    @Test
    public void testIsServiceEnabled() throws Exception {
        disableService();

        final AutofillManager afm = mActivity.getAutofillManager();
        assertThat(afm.hasEnabledAutofillServices()).isFalse();
        try {
            enableService();
            assertThat(afm.hasEnabledAutofillServices()).isTrue();
        } finally {
            disableService();
        }
    }
}
