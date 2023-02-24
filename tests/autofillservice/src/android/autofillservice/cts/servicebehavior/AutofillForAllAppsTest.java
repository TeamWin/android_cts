/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.autofillservice.cts.servicebehavior;

import static android.autofillservice.cts.testcore.Helper.ID_IMEACTION_LABEL;
import static android.autofillservice.cts.testcore.Helper.ID_IMEACTION_TEXT;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME_LABEL;

import android.autofillservice.cts.activities.ImeOptionActivity;
import android.autofillservice.cts.activities.LoginNotImportantForAutofillActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.content.Intent;
import android.provider.DeviceConfig;
import android.util.Log;
import android.view.autofill.AutofillFeatureFlags;
import android.view.inputmethod.EditorInfo;

import com.android.compatibility.common.util.DeviceConfigStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AutofillForAllAppsTest extends
        AutoFillServiceTestCase.ManualActivityLaunch {

    private DeviceConfigStateManager mDenyListStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
              AutofillFeatureFlags.DEVICE_CONFIG_PACKAGE_DENYLIST_FOR_UNIMPORTANT_VIEW);
    private DeviceConfigStateManager mImeActionFlagStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
              AutofillFeatureFlags.DEVICE_CONFIG_NON_AUTOFILLABLE_IME_ACTION_IDS);
    private DeviceConfigStateManager mIsTriggerFillRequestOnUnimportantViewEnabledStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
              AutofillFeatureFlags.DEVICE_CONFIG_TRIGGER_FILL_REQUEST_ON_UNIMPORTANT_VIEW);
    private static final String TAG = "AutofillForAllAppsTest";
    private static final String TEST_CTS_PACKAGE_NAME = "android.autofillservice.cts";
    private static final String TEST_NOT_IMPORTANT_FOR_AUTOFILL_ACTIVITY_NAME =
            "android.autofillservice.cts/.activities.LoginNotImportantForAutofillActivity";
    private static final String TEST_OTHER_ACTIVITY_NAME =
            "android.autofillservice.cts/.activities.LoginActivity";
    private final CannedFillResponse.Builder mLoginResponseBuilder =
            new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                    .setField(ID_USERNAME, "dude")
                    .setField(ID_PASSWORD, "sweet")
                    .setPresentation(createPresentation("Dropdown Presentation"))
                    .build());
    private final CannedFillResponse.Builder mImeOptionResponseBuilder =
            new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                  .setField(ID_IMEACTION_TEXT, "dude")
                  .setPresentation(createPresentation("Dropdown Presentation"))
                  .build());

    @Before
    public void setDenyListImeActionToEmptySetTriggerFillResponseOnUnimportantViewToTrue() {
        setImeActionFlagValue("");
        setDenyListFlagValue("");
        setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue(Boolean.toString(true));
    }

    @After
    public void setDenyListImeActionToEmptySetTriggerFillResponseOnUnimportantViewToFalse() {
        setImeActionFlagValue("");
        setDenyListFlagValue("");
        setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue(Boolean.toString(false));
    }

    @Test
    public void testUnimportantViewTriggerFillRequest() throws Exception {
        enableService();
        // Set response with a dataset
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start activity
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field to trigger fill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is triggered  on username
        // (a not important view since parent is set to notImportantExcludeDescends)
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testUnimportantViewNotTriggerFillRequestWhenActivityInDenylist() throws Exception {
        enableService();
        setDenyListFlagValue(
                TEST_CTS_PACKAGE_NAME + ":" + TEST_NOT_IMPORTANT_FOR_AUTOFILL_ACTIVITY_NAME + ";");
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert no fill request is triggered since the package is on deny list.
        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testUnimportantViewTriggerFillRequestWhenDenylistIsWronglyFormatted()
            throws Exception {
        enableService();
        // Left out ";" in the end to make the denylist wrongly formatted
        setDenyListFlagValue(
                TEST_CTS_PACKAGE_NAME + ":" + TEST_NOT_IMPORTANT_FOR_AUTOFILL_ACTIVITY_NAME);
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is still triggered since the activity in denylist is wrongly
        // formatted
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testUnimportantViewNotTriggerFillRequestWhenPackageInDenylist() throws Exception {
        enableService();
        setDenyListFlagValue(TEST_CTS_PACKAGE_NAME + ":;");
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is not triggered since the activity is on deny list.
        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testUnimportantViewTriggerFillRequestWhenOtherActivityOfPackageInDenylist()
            throws Exception {
        enableService();
        setDenyListFlagValue(TEST_CTS_PACKAGE_NAME + ":" + TEST_OTHER_ACTIVITY_NAME + ";");
        // Set response with a dataset
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field to trigger fill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is triggered since current activity is not in denylist
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testAddingSingleHeuristicCanStopTriggeringFillRequestOnUnimportantViews()
            throws Exception {
        enableService();
        // Test when adding IME_ACTION_GO in flag, fill request won't be triggered
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO));
        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        // Test normally, fill request would be triggered on unimportant views
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(ID_IMEACTION_TEXT);
        mUiBot.waitForIdleSync();

        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testAddingSeveralHeuristicsCanStopTriggeringFillRequestOnUnimportantViews()
            throws Exception {
        enableService();
        // Test when adding multiple ime action ids including ime_action_go in flag, fill request
        // won't be triggered
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO) + "," + String.valueOf(
                EditorInfo.IME_ACTION_SEND));
        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        // Test normally, fill request would be triggered on unimportant views
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(ID_IMEACTION_TEXT);
        mUiBot.waitForIdleSync();

        sReplier.assertOnFillRequestNotCalled();
    }

    private void setImeActionFlagValue(String value) {
        Helper.setDeviceConfig(mImeActionFlagStateManager, value);
    }

    private void setDenyListFlagValue(String value) {
        Helper.setDeviceConfig(mDenyListStateManager, value);
    }

    private void setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue(String value) {
        Helper.setDeviceConfig(mIsTriggerFillRequestOnUnimportantViewEnabledStateManager, value);
    }

    private LoginNotImportantForAutofillActivity startActivity() throws Exception {
        final Intent intent = new Intent(mContext, LoginNotImportantForAutofillActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        waitSeveralIdleSyncToAssertSpecifiedUiShown(ID_USERNAME_LABEL, 11);
        return LoginNotImportantForAutofillActivity.getCurrentActivity();
    }

    private ImeOptionActivity startImeOptionActivity() throws Exception {
        final Intent intent = new Intent(mContext, ImeOptionActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        waitSeveralIdleSyncToAssertSpecifiedUiShown(ID_IMEACTION_LABEL, 11);
        return ImeOptionActivity.getCurrentActivity();
    }

    private void waitSeveralIdleSyncToAssertSpecifiedUiShown(String labelId, int numOfRounds) {
        int count = 0;
        while (count < numOfRounds) {
            try {
                mUiBot.assertShownByRelativeId(labelId);
                return;
            } catch (Exception e) {
                mUiBot.waitForIdleSync();
                count += 1;
            }
        }
        Log.w(TAG,
                "Label " + labelId + "didn't show after "
                    + numOfRounds + " rounds of wait idle syncs");
        return;
    }
}
