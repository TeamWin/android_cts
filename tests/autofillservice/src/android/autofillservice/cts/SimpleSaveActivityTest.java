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
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.SimpleSaveActivity.ID_COMMIT;
import static android.autofillservice.cts.SimpleSaveActivity.ID_INPUT;
import static android.autofillservice.cts.SimpleSaveActivity.ID_PASSWORD;
import static android.autofillservice.cts.SimpleSaveActivity.TEXT_LABEL;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.autofillservice.cts.SimpleSaveActivity.FillExpectation;
import android.content.Context;
import android.content.Intent;
import android.support.test.uiautomator.UiObject2;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SimpleSaveActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<SimpleSaveActivity> mActivityRule =
            new AutofillActivityTestRule<SimpleSaveActivity>(SimpleSaveActivity.class, false);

    private SimpleSaveActivity mActivity;

    // TODO: move mContext and mPackageName to superclass
    private Context mContext;
    private String mPackageName;

    @Before
    public void setFixtures() {
        mContext = getContext();
        mPackageName = mContext.getPackageName();
    }

    private void startActivity() {
        mActivity =
                mActivityRule.launchActivity(new Intent(getContext(), SimpleSaveActivity.class));
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
            // TODO(b/64309238): since the session is gone, a new one is created when rotated;
            // it might make sense to change the code to avoid that, but we need to re-evaluate
            // after the CustomDescription pending intent changes.
            sReplier.addResponse(CannedFillResponse.NO_RESPONSE);

            sUiBot.setScreenOrientation(UiBot.LANDSCAPE);
            saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

            sReplier.getNextFillRequest();
        }

        // Save it...
        sUiBot.saveForAutofill(saveUi, true);

        // ... and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "108");
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
}
