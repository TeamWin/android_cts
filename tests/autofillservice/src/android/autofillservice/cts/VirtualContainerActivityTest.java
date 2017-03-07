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
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.VirtualContainerView.*;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.support.test.rule.ActivityTestRule;
import android.view.autofill.AutofillValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for an activity containing virtual children.
 *
 * TODO(b/33197203, b/33802548): test other scenarios like:
 *
 * - save
 * - async populating structure
 * - move around different views
 */
public class VirtualContainerActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<VirtualContainerActivity> mActivityRule =
            new ActivityTestRule<VirtualContainerActivity>(VirtualContainerActivity.class);

    private VirtualContainerActivity mActivity;

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
    public void testAutofillSync() throws Exception {
        autofillTest(true);
    }

    @Test
    public void testAutofillAsync() throws Exception {
        autofillTest(false);
    }

    /**
     * Tests autofillingh the web, using the sync / async version of ViewStructure.addChild
     */
    private void autofillTest(boolean sync) throws Exception {
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
        mActivity.setSync(sync);

        // Trigger auto-fill.
        mActivity.onUsername((v) -> { v.changeFocus(true); });
        waitUntilConnected();

        // Make sure input was sanitized.
        final FillRequest request = replier.getNextFillRequest();
        final ViewNode usernameLabel = findNodeByResourceId(request.structure, ID_USERNAME_LABEL);
        final ViewNode username = findNodeByResourceId(request.structure, ID_USERNAME);
        final ViewNode passwordLabel = findNodeByResourceId(request.structure, ID_PASSWORD_LABEL);
        final ViewNode password = findNodeByResourceId(request.structure, ID_PASSWORD);

        assertTextIsSanitized(username);
        assertTextIsSanitized(password);
        assertTextAndValue(usernameLabel, "Username");
        assertTextAndValue(passwordLabel, "Password");

        assertThat(usernameLabel.getClassName()).isEqualTo(LABEL_CLASS);
        assertThat(username.getClassName()).isEqualTo(TEXT_CLASS);
        assertThat(passwordLabel.getClassName()).isEqualTo(LABEL_CLASS);
        assertThat(password.getClassName()).isEqualTo(TEXT_CLASS);

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(username.isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(password.isFocused()).isFalse();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.
        waitUntilDisconnected();
    }
}
