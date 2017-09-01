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
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME_CONTAINER;
import static android.autofillservice.cts.SimpleSaveActivity.ID_COMMIT;
import static android.autofillservice.cts.SimpleSaveActivity.ID_INPUT;
import static android.autofillservice.cts.SimpleSaveActivity.ID_LABEL;
import static android.autofillservice.cts.SimpleSaveActivity.ID_PASSWORD;
import static android.autofillservice.cts.SimpleSaveActivity.TEXT_LABEL;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.autofillservice.cts.SimpleSaveActivity.FillExpectation;
import android.content.Intent;
import android.support.test.uiautomator.UiObject2;

import org.junit.Rule;
import org.junit.Test;

public class SimpleSaveActivityTest extends CustomDescriptionWithLinkTestCase {

    @Rule
    public final AutofillActivityTestRule<SimpleSaveActivity> mActivityRule =
            new AutofillActivityTestRule<SimpleSaveActivity>(SimpleSaveActivity.class, false);

    private SimpleSaveActivity mActivity;

    private void startActivity() {
        startActivity(false);
    }

    private void startActivity(boolean remainOnRecents) {
        final Intent intent = new Intent(mContext, SimpleSaveActivity.class);
        if (remainOnRecents) {
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
        mActivity = mActivityRule.launchActivity(intent);
    }

    @Test
    public void testAutoFillOneDatasetAndSave() throws Exception {
        startActivity();

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT, ID_PASSWORD)
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_INPUT, "id")
                        .setField(ID_PASSWORD, "pass")
                        .setPresentation(createPresentation("YO"))
                        .build())
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();

        // Select dataset.
        final FillExpectation autofillExpecation = mActivity.expectAutoFill("id", "pass");
        sUiBot.selectDataset("YO");
        autofillExpecation.assertAutoFilled();

        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("ID");
            mActivity.mPassword.setText("PASS");
            mActivity.mCommit.performClick();
        });
        final UiObject2 saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Save it...
        sUiBot.saveForAutofill(saveUi, true);

        // ... and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "ID");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PASSWORD), "PASS");
    }

    /**
     * Simple test that only uses UiAutomator to interact with the activity, so it indirectly
     * tests the integration of Autofill with Accessibility.
     */
    @Test
    public void testAutoFillOneDatasetAndSave_usingUiAutomatorOnly() throws Exception {
        startActivity();

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT, ID_PASSWORD)
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_INPUT, "id")
                        .setField(ID_PASSWORD, "pass")
                        .setPresentation(createPresentation("YO"))
                        .build())
                .build());

        // Trigger autofill.
        sUiBot.assertShownByRelativeId(ID_INPUT).click();
        sReplier.getNextFillRequest();

        // Select dataset...
        sUiBot.selectDataset("YO");

        // ...and assert autofilled values.
        final UiObject2 input = sUiBot.assertShownByRelativeId(ID_INPUT);
        final UiObject2 password = sUiBot.assertShownByRelativeId(ID_PASSWORD);

        assertWithMessage("wrong value for 'input'").that(input.getText()).isEqualTo("id");
        // TODO: password field is shown as **** ; ideally we should assert it's a password
        // field, but UiAutomator does not exposes that info.
        final String visiblePassword = password.getText();
        assertWithMessage("'password' should not be visible").that(visiblePassword)
            .isNotEqualTo("pass");
        assertWithMessage("wrong value for 'password'").that(visiblePassword).hasLength(4);

        // Trigger save...
        input.setText("ID");
        password.setText("PASS");
        sUiBot.assertShownByRelativeId(ID_COMMIT).click();
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);

        // ... and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "ID");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PASSWORD), "PASS");
    }

    @Test
    public void testSave() throws Exception {
        saveTest(false);
    }

    @Test
    public void testSave_afterRotation() throws Exception {
        sUiBot.setScreenOrientation(UiBot.PORTRAIT);
        try {
            saveTest(true);
        } finally {
            sUiBot.setScreenOrientation(UiBot.PORTRAIT);
        }
    }

    private void saveTest(boolean rotate) throws Exception {
        startActivity();

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("108");
            mActivity.mCommit.performClick();
        });
        UiObject2 saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        if (rotate) {
            sUiBot.setScreenOrientation(UiBot.LANDSCAPE);
            saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);
        }

        // Save it...
        sUiBot.saveForAutofill(saveUi, true);

        // ... and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "108");
    }

    @Test
    public void testSaveThenStartNewSessionRightAway() throws Exception {
        startActivity();

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save and start a new session right away.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("108");
            mActivity.mCommit.performClick();
            mActivity.getAutofillManager().requestAutofill(mActivity.mInput);
        });

        // Make sure Save UI for 1st session was canceled....
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);

        //... and 2nd session canceled as well.
        Helper.assertNoDanglingSessions();
    }

    @Test
    public void testCancelPreventsSaveUiFromShowing() throws Exception {
        startActivity();

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Cancel session.
        mActivity.getAutofillManager().cancel();
        Helper.assertNoDanglingSessions();

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("108");
            mActivity.mCommit.performClick();
        });

        // Assert it's not showing.
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);
    }

    @Test
    public void testDismissSave_byTappingBack() throws Exception {
        startActivity();
        dismissSaveTest(DismissType.BACK_BUTTON);
    }

    @Test
    public void testDismissSave_byTappingHome() throws Exception {
        startActivity();
        dismissSaveTest(DismissType.HOME_BUTTON);
    }

    @Test
    public void testDismissSave_byTouchingOutside() throws Exception {
        startActivity();
        dismissSaveTest(DismissType.TOUCH_OUTSIDE);
    }

    @Test
    public void testDismissSave_byFocusingOutside() throws Exception {
        startActivity();
        dismissSaveTest(DismissType.FOCUS_OUTSIDE);
    }

    @Test
    public void testDismissSave_byTappingRecents() throws Exception {
        // Launches a different activity first.
        startWelcomeActivityOnNewTask();

        // Then launches the main activity.
        startActivity(true);
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // And finally test it..
        dismissSaveTest(DismissType.RECENTS_BUTTON);
    }

    private void dismissSaveTest(DismissType dismissType) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("108");
            mActivity.mCommit.performClick();
        });
        sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Then make sure it goes away when user doesn't want it..
        switch (dismissType) {
            case BACK_BUTTON:
                sUiBot.pressBack();
                break;
            case HOME_BUTTON:
                sUiBot.pressHome();
                break;
            case TOUCH_OUTSIDE:
                sUiBot.assertShownByText(TEXT_LABEL).click();
                break;
            case FOCUS_OUTSIDE:
                mActivity.syncRunOnUiThread(() -> mActivity.mLabel.requestFocus());
                sUiBot.assertShownByText(TEXT_LABEL).click();
                break;
            case RECENTS_BUTTON:
                sUiBot.switchAppsUsingRecents();
                WelcomeActivity.assertShowingDefaultMessage(sUiBot);
                break;
            default:
                throw new IllegalArgumentException("invalid dismiss type: " + dismissType);
        }
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);
    }

    private void startWelcomeActivityOnNewTask() throws Exception {
        final Intent intent = new Intent(mContext, WelcomeActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        mContext.startActivity(intent);
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);
    }

    @Override
    protected void saveUiRestoredAfterTappingLinkTest(PostSaveLinkTappedAction type)
            throws Exception {
        startActivity();
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setCustomDescription(newCustomDescription(WelcomeActivity.class))
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("108");
            mActivity.mCommit.performClick();
        });
        final UiObject2 saveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_GENERIC);

        // Tap the link.
        tapSaveUiLink(saveUi);

        // Make sure new activity is shown...
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);

        // .. then do something to return to previous activity...
        switch (type) {
            case ROTATE_THEN_TAP_BACK_BUTTON:
                sUiBot.setScreenOrientation(UiBot.LANDSCAPE);
                // not breaking on purpose
            case TAP_BACK_BUTTON:
                // ..then go back and save it.
                sUiBot.pressBack();
                break;
            case FINISH_ACTIVITY:
                // ..then finishes it.
                WelcomeActivity.finishIt();
                break;
            default:
                throw new IllegalArgumentException("invalid type: " + type);
        }
        // Make sure previous activity is back...
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // ... and tap save.
        final UiObject2 newSaveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_GENERIC);
        sUiBot.saveForAutofill(newSaveUi, true);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "108");
    }

    @Override
    protected void tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction action,
            boolean manualRequest) throws Exception {
        startActivity();
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setCustomDescription(newCustomDescription(WelcomeActivity.class))
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("108");
            mActivity.mCommit.performClick();
        });
        final UiObject2 saveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_GENERIC);

        // Tap the link.
        tapSaveUiLink(saveUi);

        // Make sure new activity is shown.
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);

        // Tap back to restore the Save UI...
        sUiBot.pressBack();
        // Make sure previous activity is back...
        sUiBot.assertShownByRelativeId(ID_LABEL);

        // ...but don't tap it...
        final UiObject2 saveUi2 = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // ...instead, do something to dismiss it:
        switch (action) {
            case TOUCH_OUTSIDE:
                sUiBot.assertShownByRelativeId(ID_LABEL).longClick();
                break;
            case TAP_NO_ON_SAVE_UI:
                sUiBot.saveForAutofill(saveUi2, false);
                break;
            case TAP_YES_ON_SAVE_UI:
                sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);
                final SaveRequest saveRequest = sReplier.getNextSaveRequest();
                assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "108");
                Helper.assertNoDanglingSessions();
                break;
            default:
                throw new IllegalArgumentException("invalid action: " + action);
        }
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);

        // Make sure previous session was finished.
        Helper.assertNoDanglingSessions();

        // Now triggers a new session and do business as usual...
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        if (manualRequest) {
            mActivity.getAutofillManager().requestAutofill(mActivity.mInput);
        } else {
            mActivity.syncRunOnUiThread(() -> mActivity.mPassword.requestFocus());
        }

        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("42");
            mActivity.mCommit.performClick();
        });

        // Save it...
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);

        // ... and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "42");
    }

    @Override
    protected void saveUiCancelledAfterTappingLinkTest(PostSaveLinkTappedAction type)
            throws Exception {
        startActivity(type == PostSaveLinkTappedAction.TAP_RECENTS);
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setCustomDescription(newCustomDescription(WelcomeActivity.class))
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("108");
            mActivity.mCommit.performClick();
        });
        final UiObject2 saveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_GENERIC);

        // Tap the link.
        tapSaveUiLink(saveUi);
        // Make sure new activity is shown...
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);

        switch (type) {
            case TAP_RECENTS:
                sUiBot.switchAppsUsingRecents();
                break;
            case LAUNCH_PREVIOUS_ACTIVITY:
                startActivity(SimpleSaveActivity.class);
                break;
            case LAUNCH_NEW_ACTIVITY:
                // Launch a 3rd activity...
                startActivity(LoginActivity.class);
                sUiBot.assertShownByRelativeId(ID_USERNAME_CONTAINER);
                // ...then go back
                sUiBot.pressBack();
                break;
            default:
                throw new IllegalArgumentException("invalid type: " + type);
        }
        // Make sure right activity is showing
        sUiBot.assertShownByRelativeId(ID_INPUT);

        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);
    }

    @Test
    public void testSelectedDatasetsAreSentOnSaveRequest() throws Exception {
        startActivity();

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT, ID_PASSWORD)
                // Added on reversed order on purpose
                .addDataset(new CannedDataset.Builder()
                        .setId("D2")
                        .setField(ID_INPUT, "id again")
                        .setField(ID_PASSWORD, "pass")
                        .setPresentation(createPresentation("D2"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setId("D1")
                        .setField(ID_INPUT, "id")
                        .setPresentation(createPresentation("D1"))
                        .build())
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();

        // Select 1st dataset.
        final FillExpectation autofillExpecation1 = mActivity.expectAutoFill("id");
        final UiObject2 picker1 = sUiBot.assertDatasets("D2", "D1");
        sUiBot.selectDataset(picker1, "D1");
        autofillExpecation1.assertAutoFilled();

        // Select 2nd dataset.
        mActivity.syncRunOnUiThread(() -> mActivity.mPassword.requestFocus());
        final FillExpectation autofillExpecation2 = mActivity.expectAutoFill("id again", "pass");
        final UiObject2 picker2 = sUiBot.assertDatasets("D2");
        sUiBot.selectDataset(picker2, "D2");
        autofillExpecation2.assertAutoFilled();

        mActivity.syncRunOnUiThread(() -> {
            mActivity.mInput.setText("ID");
            mActivity.mPassword.setText("PASS");
            mActivity.mCommit.performClick();
        });
        final UiObject2 saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Save it...
        sUiBot.saveForAutofill(saveUi, true);

        // ... and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "ID");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PASSWORD), "PASS");
        assertThat(saveRequest.datasetIds).containsExactly("D1", "D2").inOrder();
    }
}
