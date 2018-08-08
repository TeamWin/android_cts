/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_USERNAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.ComponentName;
import android.service.autofill.SaveInfo;

import org.junit.Test;

/**
 * Test case for the senario where a login screen is split in multiple activities.
 */
public class MultiScreenLoginTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<UsernameOnlyActivity> {

    private UsernameOnlyActivity mActivity;

    @Override
    protected AutofillActivityTestRule<UsernameOnlyActivity> getActivityRule() {
        return new AutofillActivityTestRule<UsernameOnlyActivity>(UsernameOnlyActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    /**
     * Tests the "traditional" scenario where the service must save each field (username and
     * password) separately.
     */
    @Test
    public void testSaveEachFieldSeparately() throws Exception {
        // Set service
        enableService();

        // First handle username...

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .build());

        // Trigger autofill
        mActivity.focusOnUsername();
        sReplier.getNextFillRequest();
        mUiBot.assertNoDatasetsEver();

        // Trigger save...
        mActivity.setUsername("dude");
        mActivity.next();
        mUiBot.saveForAutofill(true, SAVE_DATA_TYPE_USERNAME);

        // ..and assert results
        final SaveRequest saveRequest1 = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest1.structure, ID_USERNAME), "dude");

        // ...now rinse and repeat for password

        // Get the activity
        final PasswordOnlyActivity activity2 = AutofillTestWatcher
                .getActivity(PasswordOnlyActivity.class);

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_PASSWORD)
                .build());

        // Trigger autofill
        activity2.focusOnPassword();
        sReplier.getNextFillRequest();
        mUiBot.assertNoDatasetsEver();

        // Trigger save...
        activity2.setPassword("sweet");
        activity2.login();
        mUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        // ..and assert results
        final SaveRequest saveRequest2 = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest2.structure, ID_PASSWORD), "sweet");
    }

    /**
     * Tests the new scenario introudced on Q where the service can set a multi-screen session.
     */
    @Test
    public void testSaveBothFieldsAtOnce() throws Exception {
        // Set service
        enableService();

        // First handle username...

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setSaveInfoFlags(SaveInfo.FLAG_DELAY_SAVE).build());

        // Trigger autofill
        mActivity.focusOnUsername();
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        final ComponentName component1 = fillRequest1.structure.getActivityComponent();
        assertThat(component1).isEqualTo(mActivity.getComponentName());
        mUiBot.assertNoDatasetsEver();

        // Trigger what would be save...
        mActivity.setUsername("dude");
        mActivity.next();
        mUiBot.assertSaveNotShowing();

        // ...now rinse and repeat for password

        // Get the activity
        final PasswordOnlyActivity passwordActivity = AutofillTestWatcher
                .getActivity(PasswordOnlyActivity.class);

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME | SAVE_DATA_TYPE_PASSWORD,
                        ID_PASSWORD)
                .build());

        // Trigger autofill
        passwordActivity.focusOnPassword();
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        final ComponentName component2 = fillRequest2.structure.getActivityComponent();
        assertThat(component2).isEqualTo(passwordActivity.getComponentName());
        mUiBot.assertNoDatasetsEver();

        // Trigger save...
        passwordActivity.setPassword("sweet");
        passwordActivity.login();
        mUiBot.saveForAutofill(true, SAVE_DATA_TYPE_USERNAME, SAVE_DATA_TYPE_PASSWORD);

        // ..and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertThat(saveRequest.contexts.size()).isEqualTo(2);

        // Username is set in the 1st context
        final AssistStructure previousStructure = saveRequest.contexts.get(0).getStructure();
        assertWithMessage("no structure for 1st activity").that(previousStructure).isNotNull();
        assertTextAndValue(findNodeByResourceId(previousStructure, ID_USERNAME), "dude");
        final ComponentName componentPrevious = previousStructure.getActivityComponent();
        assertThat(componentPrevious).isEqualTo(mActivity.getComponentName());

        // Password is set in the 2nd context
        final AssistStructure currentStructure = saveRequest.contexts.get(1).getStructure();
        assertWithMessage("no structure for 2nd activity").that(currentStructure).isNotNull();
        assertTextAndValue(findNodeByResourceId(currentStructure, ID_PASSWORD), "sweet");
        final ComponentName componentCurrent = currentStructure.getActivityComponent();
        assertThat(componentCurrent).isEqualTo(passwordActivity.getComponentName());
    }

    // TODO(b/112051762): add test cases for more scenarios such as:
    // - make sure that activity not marked with keepAlive is not sent in the 2nd request
    // - somehow verify that the first activity's session is gone
    // - test client state
    // - WebView
}
